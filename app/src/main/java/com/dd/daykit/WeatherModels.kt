package com.dd.daykit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherForecast(
    val latitude: Double,
    val longitude: Double,
    val hourly: HourlyData,
    @SerialName("minutely_15") val minutely15: Minutely15Data? = null
)

/**
 * Kwartierdata, alleen gebruikt om de randen van een regenperiode scherper te krijgen ("van half 1"
 * i.p.v. "van 1 uur"). Optioneel: niet elke modelkeuze of locatie levert het, en verder terug dan
 * ongeveer twee dagen bestaat het sowieso niet.
 *
 * Bewust alleen [precipitation]: een neerslagkans per kwartier bestaat niet bij Open-Meteo (dat is
 * een uurwaarde), en weercodes evenmin. Alles wat hier binnenkomt buiten het dekkingsgebied van de
 * hoge-resolutiemodellen is gewoon de uurwaarde uitgesmeerd over vier kwartieren - dan verandert de
 * verfijning niets, wat precies de bedoeling is.
 */
@Serializable
data class Minutely15Data(
    val time: List<String> = emptyList(),
    val precipitation: List<Double?> = emptyList()
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature2m: List<Double>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>,
    val weathercode: List<Int>,
    @SerialName("windspeed_10m") val windspeed10m: List<Double>,
    @SerialName("wind_gusts_10m") val windGusts10m: List<Double> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("winddirection_10m") val windDirection10m: List<Int?> = emptyList()
)

/**
 * Eén geconfigureerde entiteit-regel binnen een weer-widget (Weer instellingen > Widgets). [name]
 * is optioneel: leeg = toon de "friendly_name" van Home Assistant (of anders de entiteit-id) i.p.v.
 * een eigen label.
 */
data class WeatherWidgetEntityConfig(
    val entityId: String,
    val name: String = ""
)

/** Live opgehaalde waarde voor een [WeatherWidgetEntityConfig], voor weergave op de Weer-homepage. */
data class WeatherWidgetLiveValue(
    val state: String,
    val unit: String? = null,
    val friendlyName: String? = null,
    // HA's "device_class"-attribuut (bv. "carbon_dioxide", "pm25", "pm10") - bepaalt of de waarde
    // als heel getal getoond moet worden i.p.v. met decimalen, zie shouldRoundToWholeNumber().
    val deviceClass: String? = null
)

data class HourlyWeather(
    val timestamp: Long,
    val temperature: Double,
    val precipitationProbability: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val windGusts: Double,
    val windDirection: Int?,
    val condition: WeatherCondition
)

data class CurrentWeather(
    val temperature: Double,
    val precipitationProbability: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val windGusts: Double,
    val windDirection: Int?,
    val condition: WeatherCondition,
    val timestamp: Long
)

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult>? = null)

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
)

enum class WeatherCondition {
    CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, CLOUDY,
    FOG, DRIZZLE, FREEZING_DRIZZLE,
    RAIN, FREEZING_RAIN,
    SNOW, SNOW_SHOWERS,
    RAIN_SHOWERS, THUNDERSTORM, HAIL;

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> MOSTLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> FREEZING_DRIZZLE
            61, 63, 65 -> RAIN
            66, 67 -> FREEZING_RAIN
            71, 73, 75, 77 -> SNOW
            80, 81, 82 -> RAIN_SHOWERS
            85, 86 -> SNOW_SHOWERS
            95 -> THUNDERSTORM
            96, 99 -> HAIL
            else -> CLOUDY
        }
    }
}
