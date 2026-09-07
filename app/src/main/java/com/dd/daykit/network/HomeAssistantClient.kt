package com.dd.daykit.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.dd.daykit.data.HomeAssistantSettings
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Singleton HomeAssistantClient - zorgt ervoor dat alle code dezelfde instantie gebruikt.
 * Dit is belangrijk zodat cache clearing werkt over alle Activities/Services heen.
 */
object HomeAssistantClient {

    private var retrofit: Retrofit? = null
    private var currentSettings: HomeAssistantSettings? = null

    // Aparte, langzamere retrofit-cache voor uploads (geluiden/weeralarm-tts-audio): een
    // base64-payload van enkele MB heeft ruim meer tijd nodig dan de normale 15s-timeout
    // hieronder, die bedoeld is voor kleine state/service-call-JSON. Los gehouden van de
    // cache hierboven zodat een upload de snelle client voor gewone calls niet "vervuilt"
    // met een te lange timeout, en andersom. Zie getUploadApi/getUploadApiForUrl.
    private var uploadRetrofit: Retrofit? = null
    private var currentUploadSettings: HomeAssistantSettings? = null

    /**
     * Wis de gecachte client. Moet worden aangeroepen als settings worden verwijderd/gewijzigd.
     * Omdat dit een singleton is, werkt dit over alle Activities/Services heen.
     */
    fun clearCache() {
        android.util.Log.i("HomeAssistantClient", "clearCache() called - clearing retrofit and currentSettings")
        retrofit = null
        currentSettings = null
        uploadRetrofit = null
        currentUploadSettings = null
    }

    fun getApi(settings: HomeAssistantSettings): HomeAssistantApi? {
        val url = settings.activeBaseUrl ?: return null
        val token = settings.longLivedToken ?: return null

        // Check if we need to rebuild (settings changed)
        if (retrofit != null && currentSettings == settings) {
            return retrofit!!.create(HomeAssistantApi::class.java)
        }

        val client = buildOkHttpClient(token)
        val json = Json { ignoreUnknownKeys = true }

        // Ensure URL ends with /
        val baseUrl = if (url.endsWith("/")) url else "$url/"

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        currentSettings = settings
        return retrofit!!.create(HomeAssistantApi::class.java)
    }

    /**
     * Maakt een API instance voor een specifieke baseUrl en token.
     * Gebruikt geen caching - altijd een nieuwe instance.
     * Handig voor het testen van meerdere URLs.
     */
    fun getApiForUrl(baseUrl: String, token: String): HomeAssistantApi {
        val client = buildOkHttpClient(token)
        val json = Json { ignoreUnknownKeys = true }

        // Ensure URL ends with /
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val tempRetrofit = Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return tempRetrofit.create(HomeAssistantApi::class.java)
    }

    /**
     * Zelfde als [getApi], maar met een ruim langere read/write-timeout (90s i.p.v. 15s) -
     * nodig voor het uploaden van geluiden/weeralarm-tts-audio (base64, kan enkele MB zijn).
     * De normale 15s-timeout is prima voor gewone kleine state/service-call-JSON, maar te kort
     * voor dit soort uploads - vooral richting een HA-instantie op bescheiden hardware (bv. een
     * Raspberry Pi) of over een matige wifi-verbinding, waar de upload consequent op een
     * timeout liep i.p.v. een echte HTTP-fout.
     */
    fun getUploadApi(settings: HomeAssistantSettings): HomeAssistantApi? {
        val url = settings.activeBaseUrl ?: return null
        val token = settings.longLivedToken ?: return null

        if (uploadRetrofit != null && currentUploadSettings == settings) {
            return uploadRetrofit!!.create(HomeAssistantApi::class.java)
        }

        val client = buildOkHttpClient(token, extendedTimeouts = true)
        val json = Json { ignoreUnknownKeys = true }
        val baseUrl = if (url.endsWith("/")) url else "$url/"

        uploadRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        currentUploadSettings = settings
        return uploadRetrofit!!.create(HomeAssistantApi::class.java)
    }

    /** Zelfde als [getApiForUrl], met de langere upload-timeout - zie [getUploadApi]. */
    fun getUploadApiForUrl(baseUrl: String, token: String): HomeAssistantApi {
        val client = buildOkHttpClient(token, extendedTimeouts = true)
        val json = Json { ignoreUnknownKeys = true }
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val tempRetrofit = Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return tempRetrofit.create(HomeAssistantApi::class.java)
    }

    private fun buildOkHttpClient(token: String, extendedTimeouts: Boolean = false): OkHttpClient {
        val timeoutSeconds = if (extendedTimeouts) 90L else 15L
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }
}
