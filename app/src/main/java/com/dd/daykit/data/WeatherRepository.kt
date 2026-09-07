package com.dd.daykit.data

import android.util.Log
import com.dd.daykit.CurrentWeather
import com.dd.daykit.HourlyWeather
import com.dd.daykit.WeatherCondition
import com.dd.daykit.WeatherForecast
import com.dd.daykit.network.WeatherClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WeatherRepository {

    companion object {
        /**
         * Gedeelde instantie voor de UI (Weer-startscherm): blijft in leven zolang het app-proces
         * leeft, dus ook als [com.dd.daykit.WeatherActivity] opnieuw wordt aangemaakt (bv.
         * even weg en terug). Zo blijft de 30-minuten-cache hieronder bruikbaar i.p.v. dat elke
         * nieuwe Activity-instantie met een lege cache begint en de pagina steeds opnieuw een
         * "Loading"-status moet tonen terwijl er eigenlijk al (recente) data bekend is.
         * Achtergrondtaken (bv. [com.dd.daykit.WeatherAlertWorker]) maken bewust hun eigen,
         * losse instantie - die lopen onafhankelijk van of de UI open staat.
         */
        val shared: WeatherRepository by lazy { WeatherRepository() }
    }

    private var cachedForecast: WeatherForecast? = null
    private var cacheTimestamp: Long = 0L
    private var cachedLat: Double = Double.NaN
    private var cachedLon: Double = Double.NaN
    private var cachedModel: String = ""

    private val CACHE_MAX_AGE_MS = 30 * 60 * 1000L

    /** Aantal kwartier-stappen dat opgehaald wordt: 2 dagen (4 per uur × 24 × 2). */
    private val MINUTELY_15_STEPS = 4 * 24 * 2

    suspend fun getForecast(latitude: Double, longitude: Double, model: String = "best_match"): Result<WeatherForecast> {
        val now = System.currentTimeMillis()
        val cached = cachedForecast
        if (cached != null
            && cachedLat == latitude
            && cachedLon == longitude
            && cachedModel == model
            && (now - cacheTimestamp) < CACHE_MAX_AGE_MS
        ) {
            return Result.success(cached)
        }

        return try {
            val forecast = WeatherClient.api.getForecast(
                latitude = latitude,
                longitude = longitude,
                hourly = "temperature_2m,precipitation_probability,precipitation,weathercode,windspeed_10m,wind_gusts_10m,winddirection_10m",
                timezone = "auto",
                forecastDays = 7,
                models = model,
                // Alleen neerslag in millimeters: een neerslagkans per kwartier bestaat niet bij
                // Open-Meteo, en een weercode evenmin. Dit dient uitsluitend om de randen van een al
                // vastgestelde regenperiode scherper te krijgen - de beslissing óf er gewaarschuwd
                // wordt blijft volledig op de uurdata staan.
                minutely15 = "precipitation",
                // Twee dagen: verder vooruit bestaat er geen echte kwartierdata (de
                // hoge-resolutiemodellen lopen niet verder), dus dat zou alleen een grotere reactie
                // opleveren zonder extra informatie.
                forecastMinutely15 = MINUTELY_15_STEPS
            )
            cachedForecast = forecast
            cacheTimestamp = now
            cachedLat = latitude
            cachedLon = longitude
            cachedModel = model
            Log.d("WeatherRepository", "Fetched ${forecast.hourly.time.size} hourly entries")
            Result.success(forecast)
        } catch (e: SocketTimeoutException) {
            Log.e("WeatherRepository", "Timeout fetching weather", e)
            Result.failure(e)
        } catch (e: UnknownHostException) {
            Log.e("WeatherRepository", "No network connection", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e("WeatherRepository", "IO error fetching weather", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Error fetching weather", e)
            Result.failure(e)
        }
    }

    /**
     * Het uur dat het dichtst bij [timestamp] ligt.
     *
     * Bedoeld voor waarden die gelden óp het uurstempel zelf: temperatuur, weercode, wind. Voor
     * neerslag hoort [getPrecipitationHourForTime] gebruikt te worden - zie de uitleg daar.
     */
    suspend fun getWeatherForTime(latitude: Double, longitude: Double, timestamp: Long, model: String = "best_match"): Result<HourlyWeather> {
        return getForecast(latitude, longitude, model).mapCatching { forecast ->
            val hourlyList = toHourlyList(forecast)
            hourlyList.minByOrNull { kotlin.math.abs(it.timestamp - timestamp) }
                ?: throw NoSuchElementException("No hourly data in forecast")
        }
    }

    /**
     * Het uurblok waarin [timestamp] valt, voor neerslag-gerelateerde beslissingen.
     *
     * Open-Meteo documenteert neerslag en neerslagkans als de som/kans ván het voorafgaande uur: de
     * waarde bij 15:00 beschrijft 14:00 tot 15:00. Het uur waarin een afspraak van 14:30 valt, is
     * dus het stempel van 15:00 - niet dat van 14:00.
     *
     * Voorheen werd hiervoor [getWeatherForTime] gebruikt, dat simpelweg het dichtstbijzijnde
     * stempel pakt. Voor een afspraak om 14:20 leverde dat het stempel van 14:00 op, oftewel de
     * regen tussen 13:00 en 14:00 - het uur vóór de afspraak. Precies de soort melding die niet lijkt
     * te kloppen met wat je buiten ziet gebeuren.
     *
     * Een afspraak die exact op het hele uur begint (14:00) krijgt bewust het blok dat dán begint
     * (stempel 15:00, dus 14:00-15:00) en niet het blok dat er net op eindigde: bij een afspraak om
     * 14:00 gaat het om het weer vanaf 14:00.
     *
     * Let op: dit steunt op de documentatie van Open-Meteo, die door gebruikers wel eens anders
     * gelezen wordt. Wijst een meting uit dat de waarde tóch bij het stempel zelf hoort, dan is dit
     * de enige plek die terug moet.
     */
    suspend fun getPrecipitationHourForTime(
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        model: String = "best_match"
    ): Result<HourlyWeather> {
        return getForecast(latitude, longitude, model).mapCatching { forecast ->
            val hourlyList = toHourlyList(forecast)
            hourlyList.firstOrNull { it.timestamp > timestamp }
            // Voorbij het einde van de voorspelling: het laatste bekende uur is het beste dat er is.
                ?: hourlyList.maxByOrNull { it.timestamp }
                ?: throw NoSuchElementException("No hourly data in forecast")
        }
    }

    suspend fun getCurrentWeather(latitude: Double, longitude: Double, model: String = "best_match"): Result<CurrentWeather> {
        return getWeatherForTime(latitude, longitude, System.currentTimeMillis(), model).map { hourly ->
            CurrentWeather(
                temperature = hourly.temperature,
                precipitationProbability = hourly.precipitationProbability,
                precipitation = hourly.precipitation,
                weatherCode = hourly.weatherCode,
                windSpeed = hourly.windSpeed,
                windGusts = hourly.windGusts,
                windDirection = hourly.windDirection,
                condition = hourly.condition,
                timestamp = hourly.timestamp
            )
        }
    }

    suspend fun getTemperatureChange(
        latitude: Double,
        longitude: Double,
        todayTimestamp: Long,
        tomorrowTimestamp: Long,
        model: String = "best_match"
    ): Result<Double> {
        return getForecast(latitude, longitude, model).mapCatching { forecast ->
            val hourlyList = toHourlyList(forecast)
            val today = hourlyList.minByOrNull { kotlin.math.abs(it.timestamp - todayTimestamp) }
                ?: throw NoSuchElementException("No data for today timestamp")
            val tomorrow = hourlyList.minByOrNull { kotlin.math.abs(it.timestamp - tomorrowTimestamp) }
                ?: throw NoSuchElementException("No data for tomorrow timestamp")
            tomorrow.temperature - today.temperature
        }
    }

    fun clearCache() {
        cachedForecast = null
        cacheTimestamp = 0L
        cachedLat = Double.NaN
        cachedLon = Double.NaN
        cachedModel = ""
    }

    private fun toHourlyList(forecast: WeatherForecast): List<HourlyWeather> {
        val hourly = forecast.hourly
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val zone = ZoneId.systemDefault()
        return hourly.time.indices.map { i ->
            val epochMillis = LocalDateTime.parse(hourly.time[i], formatter)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            val code = hourly.weathercode[i]
            HourlyWeather(
                timestamp = epochMillis,
                temperature = hourly.temperature2m[i],
                precipitationProbability = hourly.precipitationProbability.getOrNull(i) ?: 0,
                precipitation = hourly.precipitation.getOrNull(i) ?: 0.0,
                weatherCode = code,
                windSpeed = hourly.windspeed10m[i],
                windGusts = hourly.windGusts10m.getOrNull(i) ?: hourly.windspeed10m[i],
                windDirection = hourly.windDirection10m.getOrNull(i),
                condition = WeatherCondition.fromWmoCode(code)
            )
        }
    }
}
