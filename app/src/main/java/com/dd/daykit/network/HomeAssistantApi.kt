package com.dd.daykit.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface HomeAssistantApi {
    @GET("api/states/{entity_id}")
    suspend fun getEntityState(@Path("entity_id") entityId: String): HaStateResponse

    @GET("api/")
    suspend fun getApiStatus(): HaApiStatus
    
    @GET("api/states")
    suspend fun getAllStates(): List<HaStateResponse>
    
    @PUT("api/states/{entity_id}")
    suspend fun updateEntityState(
        @Path("entity_id") entityId: String,
        @Body stateUpdate: HaStateUpdate
    ): HaStateResponse
    
    @POST("api/services/{domain}/{service}")
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body serviceData: HaServiceCall
    ): JsonElement

    @POST("api/services/{domain}/{service}")
    suspend fun callGenericService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body serviceData: HaGenericServiceCall
    ): JsonElement

    @POST("api/services/{domain}/{service}")
    suspend fun callBooleanService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body serviceData: HaBooleanServiceCall
    ): JsonElement

    @POST("api/services/{domain}/{service}")
    suspend fun callTextService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body serviceData: HaTextServiceCall
    ): JsonElement

    @POST("api/services/{domain}/{service}")
    suspend fun callJsonService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body serviceData: JsonObject
    ): JsonElement

    @POST("api/webhook/{webhook_id}")
    suspend fun triggerWebhook(
        @Path("webhook_id") webhookId: String,
        @Body data: JsonObject = JsonObject(emptyMap())
    ): Response<Unit>

    // --- DayKit custom integration (custom_components/daykit) ---
    // Vervangt de losse alarm_app_v1-webhook + AppDaemon-upload. Zelfde auth (long-lived
    // token via de bestaande OkHttp-interceptor) en dezelfde baseUrl/poort als de rest
    // van deze API - geen apart endpoint/poort nodig.
    @POST("api/daykit/event")
    suspend fun sendAgendaAlarmEvent(@Body payload: JsonObject): JsonElement

    @POST("api/daykit/sound_upload")
    suspend fun uploadAgendaAlarmSound(@Body payload: JsonObject): JsonElement

    // Vluchtige weeralarm-tts-audio (Android TextToSpeech -> HA-speaker via
    // media_player.play_media) - overschrijft steeds hetzelfde vaste bestand aan de
    // HA-kant, geen manifest/permanente opslag zoals sound_upload hierboven. Zie
    // DayKitWeatherTtsUploadView in http_views.py.
    @POST("api/daykit/weather_tts_upload")
    suspend fun uploadWeatherTts(@Body payload: JsonObject): JsonElement

    // Config-sync (entiteiten-lijst, speaker/presence/uit-bed/script-instellingen, enz.) -
    // de APP is hier leidend, zie config_sync.py aan de HA-kant en
    // HomeAssistantRepository.pushAgendaAlarmConfigPatch() aan deze kant. getAgendaAlarmConfig()
    // wordt NOOIT gebruikt om automatisch de lokale staat te overschrijven - alleen om te
    // vergelijken met de lokale staat vóór een terug-push (zie
    // HomeAssistantRepository.fetchAgendaAlarmConfig() en HaSettingsViewModel's
    // performHaEntityUpdateCheck()/applyHomeAssistantSettings()), zodat entiteiten of losse
    // velden (externe speaker/aanwezigheid/uit-bed/scripts/interval) die de gebruiker net via
    // HA's eigen "Configureren"-scherm wijzigde niet alweer overschreven worden vóórdat de app
    // die wijziging kon tonen en laten bevestigen. Elke verwijdering en elke waarde die de
    // gebruiker niet expliciet overneemt blijft 100% app-leidend.
    @GET("api/daykit/config")
    suspend fun getAgendaAlarmConfig(): JsonObject

    @POST("api/daykit/config")
    suspend fun postAgendaAlarmConfig(@Body payload: JsonObject): JsonObject
}

@Serializable
data class HaStateResponse(
    val entity_id: String,
    val state: String,
    val attributes: JsonObject? = null
)

@Serializable
data class HaApiStatus(
    val message: String
)

@Serializable
data class HaStateUpdate(
    val state: String,
    val attributes: HaStateAttributes? = null
)

@Serializable
data class HaStateAttributes(
    val friendly_name: String? = null,
    val device_class: String? = null,
    val icon: String? = null
)

@Serializable
data class HaServiceCall(
    val entity_id: String,
    val media_content_id: String? = null,
    val media_content_type: String? = null
)

@Serializable
data class HaGenericServiceCall(
    val entity_id: String,
    val time: String? = null,       // Voor input_datetime.set_datetime (HH:MM formaat)
    val value: Double? = null       // Voor input_number.set_value
)

@Serializable
data class HaBooleanServiceCall(
    val entity_id: String
)

@Serializable
data class HaTextServiceCall(
    val entity_id: String,
    val value: String
)
