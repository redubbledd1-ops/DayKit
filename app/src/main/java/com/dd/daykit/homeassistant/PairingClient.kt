package com.dd.daykit.homeassistant

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Setup-code / QR-koppeling met de DayKit HA-integratie.
 *
 * Beveiligingsmodel (bewust gekozen i.p.v. het long-lived token direct in de QR
 * te zetten): de QR-code / handmatig over te typen code bevat alleen het HA-adres
 * en een kortdurende, eenmalige 6-cijferige code. Deze client wisselt die code in
 * bij het (bewust ongeauthenticeerde) /pair-endpoint voor een echt long-lived
 * token - dat token wordt pas hierna, en alleen bij een geldige code, aangemaakt.
 *
 * Dit gebruikt bewust GEEN [com.dd.daykit.network.HomeAssistantClient],
 * want die voegt altijd een Authorization-header toe - en die hebben we hier nog
 * niet (dat is precies waar dit endpoint voor is).
 */
object PairingClient {
    private const val TAG = "PairingClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class PairingPayload(
        val base_url: String,
        val code: String
    )

    @Serializable
    private data class PairRequestBody(val code: String)

    @Serializable
    private data class PairResponseBody(
        val success: Boolean = false,
        val token: String? = null,
        val error: String? = null
    )

    sealed class PairingResult {
        data class Success(val baseUrl: String, val token: String) : PairingResult()
        data class Error(val message: String) : PairingResult()
    }

    /**
     * Parseert de tekst die uit een gescande QR-code komt. Verwacht formaat:
     * {"base_url": "http://homeassistant.local:8123", "code": "123456"}
     */
    fun parseScannedPayload(rawText: String): PairingPayload? {
        return try {
            val payload = json.decodeFromString<PairingPayload>(rawText)
            if (payload.base_url.isBlank() || payload.code.isBlank()) null else payload
        } catch (e: Exception) {
            Log.w(TAG, "Kon gescande QR-inhoud niet parsen: ${e.message}")
            null
        }
    }

    /**
     * Wissel een (gescande of handmatig ingevoerde) koppelcode in voor een long-lived
     * token. [baseUrl] moet het adres zijn waarop deze HA-instantie bereikbaar is
     * (uit de QR, of handmatig door de gebruiker ingevuld).
     */
    suspend fun exchangeCode(baseUrl: String, code: String): PairingResult = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')
            if (cleanBase.isBlank()) {
                return@withContext PairingResult.Error("Geen Home Assistant-adres opgegeven")
            }
            val cleanCode = code.trim()
            if (cleanCode.isBlank()) {
                return@withContext PairingResult.Error("Geen koppelcode opgegeven")
            }

            val url = "$cleanBase/api/daykit/pair"
            val body = json.encodeToString(PairRequestBody.serializer(), PairRequestBody(cleanCode))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                val parsed = try {
                    json.decodeFromString<PairResponseBody>(bodyStr)
                } catch (e: Exception) {
                    null
                }

                if (response.isSuccessful && parsed?.success == true && !parsed.token.isNullOrBlank()) {
                    Log.i(TAG, "Koppeling gelukt via $cleanBase")
                    PairingResult.Success(baseUrl = cleanBase, token = parsed.token)
                } else {
                    val message = parsed?.error ?: "HTTP ${response.code}"
                    Log.w(TAG, "Koppeling mislukt: $message")
                    PairingResult.Error(message)
                }
            }
        } catch (e: java.net.UnknownHostException) {
            PairingResult.Error("Kan Home Assistant niet bereiken - controleer het adres")
        } catch (e: java.net.SocketTimeoutException) {
            PairingResult.Error("Timeout - geen reactie van Home Assistant")
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij koppelen", e)
            PairingResult.Error(e.message ?: "Onbekende fout")
        }
    }
}
