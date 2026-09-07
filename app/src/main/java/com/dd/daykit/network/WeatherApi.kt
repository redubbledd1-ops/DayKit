package com.dd.daykit.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.dd.daykit.GeocodingResponse
import com.dd.daykit.WeatherForecast
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String,
        @Query("timezone") timezone: String,
        @Query("forecast_days") forecastDays: Int,
        @Query("models") models: String = "best_match",
        /**
         * Kwartierdata, alleen voor het verscherpen van de randen van een regenperiode. Optioneel:
         * niet meesturen levert gewoon een antwoord zonder "minutely_15"-blok op.
         */
        @Query("minutely_15") minutely15: String? = null,
        /**
         * Hoeveel kwartieren vooruit. Los van forecast_days, want kwartierdata bestaat maar zo'n
         * twee dagen vooruit; alles daarboven zou de reactie onnodig opblazen.
         */
        @Query("forecast_minutely_15") forecastMinutely15: Int? = null
    ): WeatherForecast
}

interface WeatherGeocodingApi {
    @GET("v1/search")
    suspend fun searchLocation(
        @Query("name") name: String,
        @Query("count") count: Int = 5,
        @Query("language") language: String = "nl"
    ): GeocodingResponse
}

object GeocodingClient {
    private const val BASE_URL = "https://geocoding-api.open-meteo.com/"

    val api: WeatherGeocodingApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val json = Json { ignoreUnknownKeys = true }

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WeatherGeocodingApi::class.java)
    }
}

object WeatherClient {
    private const val BASE_URL = "https://api.open-meteo.com/"

    val api: WeatherApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val json = Json { ignoreUnknownKeys = true }

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WeatherApi::class.java)
    }
}
