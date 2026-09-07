package com.dd.daykit.data

import com.dd.daykit.network.HaStateResponse
import com.dd.daykit.network.HomeAssistantClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.io.IOException
import android.util.Log

class HomeAssistantRepository(
    private val client: HomeAssistantClient,
    private val settingsStorage: HomeAssistantSettingsStorage
) {

    private var lastVerifiedUrl: String? = null
    private var lastVerifiedAt: Long = 0L
    private val URL_VERIFY_INTERVAL_MS = 5 * 60 * 1000L

    /**
     * Helper functie die eerst activeBaseUrl probeert en anders automatisch alle URLs test.
     * Valideert de URL met een echte netwerkcall (gecacht voor 5 min) zodat de fallback naar
     * een andere URL (lokaal/extern) daadwerkelijk werkt als je van netwerk wisselt.
     * @return HomeAssistantApi instance of null als geen enkele URL werkt
     */
    private suspend fun getApiWithFallback(): com.dd.daykit.network.HomeAssistantApi? {
        val settings = settingsStorage.settingsFlow.firstOrNull() ?: return null
        if (settings.longLivedToken.isNullOrBlank()) return null

        val now = System.currentTimeMillis()

        // Als we recent deze URL gevalideerd hebben, vertrouw de cache
        if (!settings.activeBaseUrl.isNullOrBlank() &&
            settings.activeBaseUrl == lastVerifiedUrl &&
            (now - lastVerifiedAt) < URL_VERIFY_INTERVAL_MS
        ) {
            return client.getApi(settings)
        }

        // Probeer activeBaseUrl met een echte netwerkcall
        if (!settings.activeBaseUrl.isNullOrBlank()) {
            try {
                val api = client.getApiForUrl(settings.activeBaseUrl, settings.longLivedToken)
                val entityToTest = settings.entities.firstOrNull() ?: "sun.sun"
                api.getEntityState(entityToTest)
                lastVerifiedUrl = settings.activeBaseUrl
                lastVerifiedAt = now
                return api
            } catch (_: Exception) {
                Log.d("HomeAssistantRepo", "activeBaseUrl onbereikbaar (${settings.activeBaseUrl}), probeer andere URLs")
                lastVerifiedUrl = null
            }
        }

        // activeBaseUrl werkt niet of is leeg — probeer alle URLs
        val testResult = testConnectionOverAllUrls()
        if (testResult is HaConnectionResult.Success) {
            lastVerifiedUrl = testResult.baseUrl
            lastVerifiedAt = now
            val updatedSettings = settingsStorage.settingsFlow.firstOrNull()
            return updatedSettings?.let { client.getApi(it) }
        }

        return null
    }

    /**
     * Zelfde connectiviteits-/URL-fallback-logica als [getApiWithFallback] (hergebruikt die
     * functie voor de eigenlijke bereikbaarheidscheck), maar retourneert daarna een API-instance
     * met een veel langere read/write-timeout - zie [HomeAssistantClient.getUploadApi]. Gebruik
     * dit voor het uploaden van geluiden/weeralarm-tts-audio (base64, kan enkele MB zijn); de
     * normale 15s-timeout was hier de daadwerkelijke oorzaak van steevast mislukkende uploads
     * (timeout, niet een echte HTTP-fout), vooral richting een HA-instantie op bescheiden
     * hardware of over een matige wifi-verbinding.
     */
    private suspend fun getUploadApiWithFallback(): com.dd.daykit.network.HomeAssistantApi? {
        // Hergebruikt de bestaande, snelle connectiviteitscheck/URL-fallback - alleen de
        // uiteindelijke api-instance die voor de echte upload gebruikt wordt heeft de langere
        // timeout nodig, niet de lichte "leeft deze URL nog"-probe hierboven.
        if (getApiWithFallback() == null) return null

        val settings = settingsStorage.settingsFlow.firstOrNull() ?: return null
        val activeUrl = settings.activeBaseUrl ?: return null
        val token = settings.longLivedToken ?: return null
        return client.getUploadApiForUrl(activeUrl, token)
    }

    /**
     * Haalt de state van een specifieke entity op.
     * Gebruikt activeBaseUrl met automatische fallback naar alle URLs als dat niet werkt.
     */
    suspend fun getEntityState(entityId: String): HaStateResponse {
        Log.d("HomeAssistantRepo", "getEntityState: $entityId")
        val api = getApiWithFallback()
        if (api == null) {
            Log.e("HomeAssistantRepo", "getEntityState FAILED: No API connection available")
            throw IllegalStateException("Kan geen verbinding maken met Home Assistant - controleer URL en token")
        }
        try {
            val response = api.getEntityState(entityId)
            Log.d("HomeAssistantRepo", "getEntityState SUCCESS: $entityId -> ${response.state}")
            return response
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "getEntityState FAILED: $entityId", e)
            throw e
        }
    }
    
    /**
     * Haalt de huidige Home Assistant instellingen op
     */
    suspend fun getSettings(): HomeAssistantSettings {
        return settingsStorage.settingsFlow.firstOrNull() ?: HomeAssistantSettings()
    }
    
    /**
     * Wis de gecachte client. Moet worden aangeroepen als settings worden verwijderd/gewijzigd.
     * Dit zorgt ervoor dat de volgende API call een nieuwe client maakt met de nieuwe settings.
     */
    fun clearClientCache() {
        client.clearCache()
        lastVerifiedUrl = null
        lastVerifiedAt = 0L
    }

    /**
     * Controleert of een entity 'aan' staat (state == "on").
     */
    suspend fun isEntityOn(entityId: String): Boolean {
        return try {
            val response = getEntityState(entityId)
            response.state == "on"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Test de verbinding met Home Assistant.
     * Probeert een simpele call te doen.
     */
    suspend fun testConnection(testEntityId: String? = null): HaConnectionResult {
        val settings = settingsStorage.settingsFlow.firstOrNull()
        
        if (settings == null) {
            return HaConnectionResult.Error("Geen instellingen gevonden.")
        }
        if (settings.activeBaseUrl.isNullOrBlank() || settings.longLivedToken.isNullOrBlank()) {
             return HaConnectionResult.Error("URL of token ontbreekt.")
        }

        // Bepaal welke entity we testen. 
        // 1. De meegegeven entity
        // 2. De eerste entiteit uit de lijst
        // 3. Fallback: "sun.sun" (sun.sun is in bijna elke HA installatie aanwezig)
        val entityToTest = testEntityId 
            ?: settings.entities.firstOrNull() 
            ?: "sun.sun"

        return try {
            // Probeer data op te halen
            getEntityState(entityToTest)
            
            HaConnectionResult.Success(settings.activeBaseUrl)
        } catch (e: HttpException) {
            if (e.code() == 404) {
                // Connectie werkt wel (want we kregen antwoord van HA), maar entity niet gevonden.
                // Eigenlijk is de connectie dus Succesvol, maar de entity bestaat niet.
                // Voor een pure connectietest rekenen we dit soms goed, of we geven een specifieke melding.
                // Laten we zeggen: Connectie OK, maar entity niet gevonden.
                HaConnectionResult.Success(settings.activeBaseUrl) 
            } else if (e.code() == 401) {
                HaConnectionResult.Error("Authenticatie mislukt (401). Check je token.")
            } else {
                 HaConnectionResult.Error("HTTP Fout: ${e.code()} ${e.message()}")
            }
        } catch (e: IOException) {
            HaConnectionResult.Error("Netwerkfout: ${e.message}. Is de URL bereikbaar?")
        } catch (e: Exception) {
            HaConnectionResult.Error("Onbekende fout: ${e.message}")
        }
    }

    /**
     * Test de verbinding met Home Assistant door alle geconfigureerde URLs te proberen.
     * Probeert elke URL in volgorde totdat er één werkt.
     * Slaat de werkende URL op als activeBaseUrl in de instellingen.
     * 
     * @return HaConnectionResult.Success met de werkende baseUrl, of Error als geen enkele werkt
     */
    suspend fun testConnectionOverAllUrls(): HaConnectionResult {
        val settings = settingsStorage.settingsFlow.firstOrNull()
        
        if (settings == null) {
            return HaConnectionResult.Error("Geen instellingen gevonden.")
        }
        
        if (settings.longLivedToken.isNullOrBlank()) {
            return HaConnectionResult.Error("Token ontbreekt.")
        }
        
        val urlsToTry = settings.baseUrls.filter { it.isNotBlank() }
        
        if (urlsToTry.isEmpty()) {
            return HaConnectionResult.Error("Geen Home Assistant URLs geconfigureerd.")
        }
        
        // Bepaal welke entity we testen
        val entityToTest = settings.entities.firstOrNull() ?: "sun.sun"
        
        val errors = mutableListOf<String>()
        
        // Probeer elke URL in volgorde
        for (baseUrl in urlsToTry) {
            try {
                val api = client.getApiForUrl(baseUrl, settings.longLivedToken)
                
                // Probeer de entity state op te halen
                api.getEntityState(entityToTest)
                
                // Als we hier komen, werkt de verbinding!
                // Sla de werkende URL op als activeBaseUrl
                val updatedSettings = settings.copy(activeBaseUrl = baseUrl)
                settingsStorage.saveSettings(updatedSettings)
                
                return HaConnectionResult.Success(baseUrl)
                
            } catch (e: HttpException) {
                when (e.code()) {
                    404 -> {
                        // Entity niet gevonden, maar verbinding werkt wel
                        val updatedSettings = settings.copy(activeBaseUrl = baseUrl)
                        settingsStorage.saveSettings(updatedSettings)
                        return HaConnectionResult.Success(baseUrl)
                    }
                    401 -> {
                        errors.add("$baseUrl: Authenticatie mislukt (401)")
                    }
                    else -> {
                        errors.add("$baseUrl: HTTP ${e.code()}")
                    }
                }
            } catch (e: IOException) {
                errors.add("$baseUrl: Netwerkfout (${e.message})")
            } catch (e: Exception) {
                errors.add("$baseUrl: ${e.message ?: "Onbekende fout"}")
            }
        }
        
        // Geen enkele URL werkte
        val errorMessage = if (errors.size == 1) {
            errors.first()
        } else {
            "Geen van de URLs is bereikbaar:\n" + errors.joinToString("\n")
        }
        
        return HaConnectionResult.Error(errorMessage)
    }

    /**
     * Controleert alle ingestelde entiteiten.
     */
    suspend fun checkConfiguredSensors(): List<SensorCheckResult> {
        val settings = settingsStorage.settingsFlow.firstOrNull() ?: return emptyList()
        val api = getApiWithFallback() ?: return emptyList()
        
        // Gebruik de generieke entities lijst
        val allEntities = settings.entities
        
        if (allEntities.isEmpty()) return emptyList()

        val results = mutableListOf<SensorCheckResult>()

        for (entityId in allEntities) {
            val result = try {
                val response = api.getEntityState(entityId)
                SensorCheckResult(
                    entityId = entityId,
                    exists = true,
                    state = response.state,
                    errorMessage = null
                )
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    SensorCheckResult(entityId, false, null, "Entity niet gevonden in HA")
                } else {
                    SensorCheckResult(entityId, false, null, "HTTP ${e.code()}: ${e.message()}")
                }
            } catch (e: Exception) {
                SensorCheckResult(entityId, false, null, e.message)
            }
            results.add(result)
        }
        return results
    }
    
    /**
     * Haalt alle media_player entities op uit Home Assistant
     * @return List van HaEntity objecten met media_player entities
     */
    suspend fun fetchMediaPlayers(): List<HaEntity> {
        return try {
            val api = getApiWithFallback() ?: return emptyList()
            val allStates = api.getAllStates()
            
            // Filter op media_player entities
            allStates
                .filter { it.entity_id.startsWith("media_player.") }
                .map { response ->
                    val friendlyName = response.attributes?.get("friendly_name")?.toString()?.trim('"') 
                        ?: response.entity_id
                    HaEntity(
                        entityId = response.entity_id,
                        friendlyName = friendlyName,
                        state = response.state,
                        domain = response.entity_id.substringBefore(".")
                    )
                }
        } catch (e: HttpException) {
            Log.e("HomeAssistantRepo", "HTTP error fetching media players: ${e.code()} ${e.message()}", e)
            emptyList()
        } catch (e: IOException) {
            Log.e("HomeAssistantRepo", "Network error fetching media players", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "Error fetching media players", e)
            emptyList()
        }
    }
    
    /**
     * Haalt alle entities op uit Home Assistant (voor presence detection etc.)
     * @return List van HaEntity objecten
     */
    suspend fun fetchAllEntities(): List<HaEntity> {
        return try {
            val api = getApiWithFallback() ?: return emptyList()
            val allStates = api.getAllStates()
            
            allStates.map { response ->
                val friendlyName = response.attributes?.get("friendly_name")?.toString()?.trim('"') 
                    ?: response.entity_id
                HaEntity(
                    entityId = response.entity_id,
                    friendlyName = friendlyName,
                    state = response.state,
                    domain = response.entity_id.substringBefore(".")
                )
            }
        } catch (e: HttpException) {
            Log.e("HomeAssistantRepo", "HTTP error fetching entities: ${e.code()} ${e.message()}", e)
            emptyList()
        } catch (e: IOException) {
            Log.e("HomeAssistantRepo", "Network error fetching entities", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "Error fetching entities", e)
            emptyList()
        }
    }
    
    /**
     * Haalt alle script entities op uit Home Assistant
     * @return List van HaEntity objecten met script entities
     */
    suspend fun fetchScripts(): List<HaEntity> {
        return try {
            val api = getApiWithFallback() ?: return emptyList()
            val allStates = api.getAllStates()

            // Filter op script entities
            allStates
                .filter { it.entity_id.startsWith("script.") }
                .map { response ->
                    val friendlyName = response.attributes?.get("friendly_name")?.toString()?.trim('"')
                        ?: response.entity_id
                    HaEntity(
                        entityId = response.entity_id,
                        friendlyName = friendlyName,
                        state = response.state,
                        domain = response.entity_id.substringBefore(".")
                    )
                }
        } catch (e: HttpException) {
            Log.e("HomeAssistantRepo", "HTTP error fetching scripts: ${e.code()} ${e.message()}", e)
            emptyList()
        } catch (e: IOException) {
            Log.e("HomeAssistantRepo", "Network error fetching scripts", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "Error fetching scripts", e)
            emptyList()
        }
    }

    /**
     * Start een Home Assistant script via script.turn_on.
     * @param entityId Entity ID van het script (bijv. "script.wek_lampen_aan")
     * @return HaUpdateResult met success of error
     */
    suspend fun callScript(entityId: String): HaUpdateResult {
        Log.d("HomeAssistantRepo", "callScript: $entityId")
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.e("HomeAssistantRepo", "callScript FAILED: No API connection")
                return HaUpdateResult.Error("Kan geen verbinding maken met Home Assistant - controleer instellingen")
            }

            val payload = buildJsonObject {
                put("entity_id", entityId)
            }
            api.callJsonService("script", "turn_on", payload)

            Log.i("HomeAssistantRepo", "callScript SUCCESS: $entityId")
            HaUpdateResult.Success
        } catch (e: retrofit2.HttpException) {
            val errorMsg = when (e.code()) {
                401 -> "Authenticatie mislukt - controleer token"
                404 -> "Script niet gevonden"
                else -> "HTTP ${e.code()}: ${e.message()}"
            }
            Log.e("HomeAssistantRepo", "callScript FAILED: $errorMsg", e)
            HaUpdateResult.Error(errorMsg)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("HomeAssistantRepo", "callScript TIMEOUT", e)
            HaUpdateResult.Error("Timeout - geen reactie van Home Assistant")
        } catch (e: java.net.UnknownHostException) {
            Log.e("HomeAssistantRepo", "callScript UNREACHABLE: ${e.message}", e)
            HaUpdateResult.Error("Kan Home Assistant niet bereiken - controleer URL")
        } catch (e: java.io.IOException) {
            Log.e("HomeAssistantRepo", "callScript IO ERROR: ${e.message}", e)
            HaUpdateResult.Error("Netwerkfout: ${e.message}")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "callScript UNKNOWN ERROR: ${e.message}", e)
            HaUpdateResult.Error("Onbekende fout: ${e.message}")
        }
    }

    /**
     * Forceert een verse update van een entity via de generieke homeassistant.update_entity
     * service, i.p.v. te wachten tot de eigen (periodieke) poll-cyclus van die entity langskomt.
     *
     * Gebruikt door [com.dd.daykit.AlarmOutputDecisionEngine.isUserAtHome] als eenmalige
     * auto-fix vlak vóórdat een "niet thuis"-beslissing definitief gemaakt wordt: voor de
     * ingebouwde ping-sensor (`binary_sensor.py`'s PhoneReachableBinarySensor, die hiervoor
     * expliciet `async_update()` + `should_poll = False` heeft gekregen) forceert dit een directe
     * herping i.p.v. te wachten op de volgende PING_INTERVAL_SECONDS-tick - relevant omdat een
     * HA-reload (zie de phone_ip-persistentie-fix) of een gewoon gemiste ping anders tot wel
     * PING_INTERVAL_SECONDS kan duren voordat de sensor zichzelf herstelt. Voor entities zonder
     * eigen `async_update` (bv. person./device_tracker.*) is dit een onschadelijke no-op.
     *
     * Best-effort: een falende call mag de presence-beslissing nooit blokkeren, alleen loggen.
     */
    suspend fun forceUpdateEntity(entityId: String): HaUpdateResult {
        Log.d("HomeAssistantRepo", "forceUpdateEntity: $entityId")
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "forceUpdateEntity: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }

            val payload = buildJsonObject {
                put("entity_id", entityId)
            }
            api.callJsonService("homeassistant", "update_entity", payload)

            Log.i("HomeAssistantRepo", "forceUpdateEntity SUCCESS: $entityId")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.w("HomeAssistantRepo", "forceUpdateEntity FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij forceren van entity-update: ${e.message}")
        }
    }

    /**
     * Stelt het volume in op een externe speaker via media_player.volume_set.
     * @param entityId Entity ID van de media_player
     * @param volumePercent Volume in procenten (0-100)
     */
    suspend fun setSpeakerVolume(entityId: String, volumePercent: Int): HaPlayMediaResult {
        val clamped = volumePercent.coerceIn(0, 100)
        val volumeLevel = clamped / 100.0
        Log.d("HomeAssistantRepo", "setSpeakerVolume: $entityId -> $volumeLevel")
        return try {
            val api = getApiWithFallback()
                ?: return HaPlayMediaResult.Error("Kan geen verbinding maken met Home Assistant - controleer instellingen")
            val payload = buildJsonObject {
                put("entity_id", entityId)
                put("volume_level", volumeLevel)
            }
            api.callJsonService("media_player", "volume_set", payload)
            HaPlayMediaResult.Success
        } catch (e: retrofit2.HttpException) {
            Log.e("HomeAssistantRepo", "setSpeakerVolume FAILED: HTTP ${e.code()}", e)
            HaPlayMediaResult.Error("HTTP ${e.code()}: ${e.message()}")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "setSpeakerVolume FAILED: ${e.message}", e)
            HaPlayMediaResult.Error("Fout bij volume instellen: ${e.message}")
        }
    }

    /**
     * Speelt media af op een externe speaker
     * @param entityId Entity ID van de media_player (bijv. "media_player.woonkamer")
     * @param mediaContentId URL of pad naar het af te spelen media bestand
     * @param mediaContentType Type media (bijv. "music", "url")
     * @return HaPlayMediaResult met success of error
     */
    suspend fun playMediaOnSpeaker(
        entityId: String, 
        mediaContentId: String,
        mediaContentType: String = "music"
    ): HaPlayMediaResult {
        Log.d("HomeAssistantRepo", "playMediaOnSpeaker: $entityId")
        Log.d("HomeAssistantRepo", "  Content: $mediaContentId")
        Log.d("HomeAssistantRepo", "  Type: $mediaContentType")
        
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.e("HomeAssistantRepo", "playMediaOnSpeaker FAILED: No API connection")
                return HaPlayMediaResult.Error("Kan geen verbinding maken met Home Assistant - controleer instellingen")
            }
            
            val serviceCall = com.dd.daykit.network.HaServiceCall(
                entity_id = entityId,
                media_content_id = mediaContentId,
                media_content_type = mediaContentType
            )
            
            api.callService("media_player", "play_media", serviceCall)
            
            Log.i("HomeAssistantRepo", "playMediaOnSpeaker SUCCESS: $entityId")
            HaPlayMediaResult.Success
        } catch (e: retrofit2.HttpException) {
            val errorMsg = when (e.code()) {
                401 -> "Authenticatie mislukt - controleer token"
                404 -> "Speaker niet gevonden"
                else -> "HTTP ${e.code()}: ${e.message()}"
            }
            Log.e("HomeAssistantRepo", "playMediaOnSpeaker FAILED: $errorMsg", e)
            HaPlayMediaResult.Error(errorMsg)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("HomeAssistantRepo", "playMediaOnSpeaker TIMEOUT", e)
            HaPlayMediaResult.Error("Timeout - geen reactie van Home Assistant")
        } catch (e: java.net.UnknownHostException) {
            Log.e("HomeAssistantRepo", "playMediaOnSpeaker UNREACHABLE: ${e.message}", e)
            HaPlayMediaResult.Error("Kan Home Assistant niet bereiken - controleer URL")
        } catch (e: java.io.IOException) {
            Log.e("HomeAssistantRepo", "playMediaOnSpeaker IO ERROR: ${e.message}", e)
            HaPlayMediaResult.Error("Netwerkfout: ${e.message}")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "playMediaOnSpeaker UNKNOWN ERROR: ${e.message}", e)
            HaPlayMediaResult.Error("Onbekende fout: ${e.message}")
        }
    }

    /**
     * Stopt het afspelen op een externe speaker
     * @param entityId Entity ID van de media_player
     * @return HaPlayMediaResult met success of error
     */
    suspend fun stopMediaOnSpeaker(entityId: String): HaPlayMediaResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                return HaPlayMediaResult.Error("Kan geen verbinding maken met Home Assistant - controleer instellingen")
            }
            
            val serviceCall = com.dd.daykit.network.HaServiceCall(
                entity_id = entityId,
                media_content_id = null,
                media_content_type = null
            )
            
            // Call media_player.media_stop service
            api.callService("media_player", "media_stop", serviceCall)
            
            HaPlayMediaResult.Success
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> HaPlayMediaResult.Error("Authenticatie mislukt - controleer token")
                404 -> HaPlayMediaResult.Error("Speaker niet gevonden")
                else -> HaPlayMediaResult.Error("HTTP ${e.code()}: ${e.message()}")
            }
        } catch (e: Exception) {
            HaPlayMediaResult.Error("Fout bij stoppen: ${e.message}")
        }
    }
    
    /**
     * Update de volgende alarmtijd in Home Assistant
     * @param timestampIso ISO 8601 timestamp (bijv. "2025-11-27T06:45:00+01:00")
     * @return HaUpdateResult met success of error
     */
    suspend fun updateNextAlarm(timestampIso: String): HaUpdateResult {
        return try {
            val settings = settingsStorage.settingsFlow.firstOrNull()
            
            if (settings == null) {
                return HaUpdateResult.Error("Geen Home Assistant instellingen gevonden")
            }
            
            if (settings.activeBaseUrl.isNullOrBlank() || settings.longLivedToken.isNullOrBlank()) {
                return HaUpdateResult.Error("Home Assistant URL of token ontbreekt")
            }
            
            val api = client.getApi(settings)
            if (api == null) {
                return HaUpdateResult.Error("Kan geen verbinding maken met Home Assistant")
            }
            
            val stateUpdate = com.dd.daykit.network.HaStateUpdate(
                state = timestampIso,
                attributes = com.dd.daykit.network.HaStateAttributes(
                    friendly_name = "AgendaAlarm volgende wekker",
                    device_class = "timestamp",
                    icon = "mdi:alarm"
                )
            )
            
            api.updateEntityState("sensor.agendalarm_next_alarm", stateUpdate)
            
            HaUpdateResult.Success
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> HaUpdateResult.Error("Authenticatie mislukt - controleer token")
                404 -> HaUpdateResult.Error("Home Assistant API niet gevonden")
                else -> HaUpdateResult.Error("HTTP ${e.code()}: ${e.message()}")
            }
        } catch (e: java.net.SocketTimeoutException) {
            HaUpdateResult.Error("Timeout - geen reactie van Home Assistant")
        } catch (e: java.net.UnknownHostException) {
            HaUpdateResult.Error("Kan Home Assistant niet bereiken - controleer URL")
        } catch (e: java.io.IOException) {
            HaUpdateResult.Error("Netwerkfout: ${e.message}")
        } catch (e: Exception) {
            HaUpdateResult.Error("Onbekende fout: ${e.message}")
        }
    }

    /**
     * Stuur de volgende alarmtijd naar Home Assistant input_datetime
     * @param timeHHMM Tijd in HH:MM formaat
     * @return HaUpdateResult met success of error
     */
    suspend fun setAlarmTime(timeHHMM: String): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "setAlarmTime: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }
            
            val serviceCall = com.dd.daykit.network.HaGenericServiceCall(
                entity_id = "input_datetime.alarmtijd",
                time = timeHHMM
            )
            
            api.callGenericService("input_datetime", "set_datetime", serviceCall)
            Log.i("HomeAssistantRepo", "setAlarmTime SUCCESS: $timeHHMM -> input_datetime.alarmtijd")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "setAlarmTime FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij instellen alarmtijd: ${e.message}")
        }
    }

    /**
     * Stuur batterijverbruik per uur naar Home Assistant input_number
     * @param value Batterijverbruik percentage per uur
     * @return HaUpdateResult met success of error
     */
    suspend fun setBatteryUsagePerHour(value: Double): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "setBatteryUsagePerHour: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }
            
            val serviceCall = com.dd.daykit.network.HaGenericServiceCall(
                entity_id = "input_number.mobiel_batterij_per_uur",
                value = value
            )
            
            api.callGenericService("input_number", "set_value", serviceCall)
            Log.i("HomeAssistantRepo", "setBatteryUsagePerHour SUCCESS: $value -> input_number.mobiel_batterij_per_uur")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "setBatteryUsagePerHour FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij instellen batterijverbruik: ${e.message}")
        }
    }

    /**
     * Stuur de backup-alarmtijd (alarmtijd + marge) naar Home Assistant input_datetime.
     * Dit is de tijd waarop de HA-watchdog (FASE 4 CORE automation) mag ingrijpen als de
     * telefoon zelf niets heeft laten weten (dead-man's-switch).
     * @param timeHHMMSS Tijd in HH:MM:SS formaat
     */
    suspend fun setBackupAlarmTime(timeHHMMSS: String): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "setBackupAlarmTime: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }

            val serviceCall = com.dd.daykit.network.HaGenericServiceCall(
                entity_id = "input_datetime.backup_alarm_time",
                time = timeHHMMSS
            )

            api.callGenericService("input_datetime", "set_datetime", serviceCall)
            Log.i("HomeAssistantRepo", "setBackupAlarmTime SUCCESS: $timeHHMMSS -> input_datetime.backup_alarm_time")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "setBackupAlarmTime FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij instellen backup alarmtijd: ${e.message}")
        }
    }

    /**
     * Stuur "mobiel betrouwbaar voor alarm" naar Home Assistant input_boolean.
     * true = telefoon meldt zich zelf (geen backup nodig), false = HA-watchdog mag ingrijpen.
     */
    suspend fun setMobielBetrouwbaarVoorAlarm(betrouwbaar: Boolean): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "setMobielBetrouwbaarVoorAlarm: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }

            val serviceCall = com.dd.daykit.network.HaBooleanServiceCall(
                entity_id = "input_boolean.mobiel_betrouwbaar_voor_alarm"
            )

            val service = if (betrouwbaar) "turn_on" else "turn_off"
            api.callBooleanService("input_boolean", service, serviceCall)
            Log.i("HomeAssistantRepo", "setMobielBetrouwbaarVoorAlarm SUCCESS: $betrouwbaar")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "setMobielBetrouwbaarVoorAlarm FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij instellen mobiel betrouwbaar: ${e.message}")
        }
    }

    /**
     * Zet alarm_actief en alarm_fallback_actief expliciet uit. Wordt aangeroepen bij het
     * wapenen van een nieuw alarm (schedule time), zodat een vergeten/vastgelopen "aan"-status
     * van een vorige cyclus nooit de volgende keten blokkeert (HA state-triggers vuren alleen
     * op een echte uit->aan overgang, dus blijven-hangen-op-aan is stil-kapot zonder deze reset).
     */
    suspend fun resetBackupAlarmState(): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "resetBackupAlarmState: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }

            api.callBooleanService(
                "input_boolean", "turn_off",
                com.dd.daykit.network.HaBooleanServiceCall(entity_id = "input_boolean.alarm_actief")
            )
            api.callBooleanService(
                "input_boolean", "turn_off",
                com.dd.daykit.network.HaBooleanServiceCall(entity_id = "input_boolean.alarm_fallback_actief")
            )
            Log.i("HomeAssistantRepo", "resetBackupAlarmState SUCCESS")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "resetBackupAlarmState FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij resetten backup alarm state: ${e.message}")
        }
    }

    /**
     * Stuur thuisstatus naar Home Assistant input_boolean
     * @param isHome true = gebruiker slaapt thuis, false = niet thuis
     * @return HaUpdateResult met success of error
     */
    suspend fun setUserHomeStatus(isHome: Boolean): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "setUserHomeStatus: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }
            
            val serviceCall = com.dd.daykit.network.HaBooleanServiceCall(
                entity_id = "input_boolean.gebruiker_thuis"
            )
            
            val service = if (isHome) "turn_on" else "turn_off"
            api.callBooleanService("input_boolean", service, serviceCall)
            Log.i("HomeAssistantRepo", "setUserHomeStatus SUCCESS: $isHome -> input_boolean.gebruiker_thuis")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "setUserHomeStatus FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij instellen thuisstatus: ${e.message}")
        }
    }

    /**
     * Stuur de gekozen speaker entity naar Home Assistant input_text
     * @param speakerEntityId De volledige entity_id van de gekozen speaker (bijv. media_player.slaapkamer)
     * @return HaUpdateResult met success of error
     */
    suspend fun setAlarmSpeakerEntity(speakerEntityId: String): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "setAlarmSpeakerEntity: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }
            
            val serviceCall = com.dd.daykit.network.HaTextServiceCall(
                entity_id = "input_text.alarm_speaker_entity",
                value = speakerEntityId
            )
            
            api.callTextService("input_text", "set_value", serviceCall)
            Log.i("HomeAssistantRepo", "setAlarmSpeakerEntity SUCCESS: $speakerEntityId -> input_text.alarm_speaker_entity")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "setAlarmSpeakerEntity FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij instellen speaker entity: ${e.message}")
        }
    }

    /**
     * Trigger een webhook in Home Assistant
     * @param webhookId De ID van de webhook (zonder /api/webhook/ prefix)
     * @param payload Optionele data om mee te sturen
     */
    suspend fun triggerWebhook(webhookId: String, payload: Map<String, String> = emptyMap()): HaUpdateResult {
        return try {
            val api = getApiWithFallback() ?: return HaUpdateResult.Error("Geen verbinding met Home Assistant")

            val jsonPayload = buildJsonObject {
                payload.forEach { (key, value) ->
                    put(key, value)
                }
            }

            val response = api.triggerWebhook(webhookId, jsonPayload)
            if (response.isSuccessful) {
                HaUpdateResult.Success
            } else {
                Log.e("HomeAssistantRepo", "Error triggering webhook $webhookId: HTTP ${response.code()}")
                HaUpdateResult.Error("HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "Error triggering webhook $webhookId", e)
            HaUpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Stuur alarm START naar Home Assistant webhook
     * @param mobileReliable true als de mobiel betrouwbaar is (voldoende batterij)
     * @param fallbackSpeaker Entity ID van de fallback speaker
     * @param fallbackVolume Volume voor de fallback speaker (0-100)
     * @param fallbackSoundUrl URL van het geluid (leeg = standaard)
     * @param fallbackAlarmDuration Interval in seconden waarmee HA het alarm herhaalt (standaard 10)
     */
    suspend fun sendAlarmStartToHA(
        mobileReliable: Boolean,
        fallbackSpeaker: String,
        fallbackVolume: Int,
        fallbackSoundUrl: String,
        fallbackAlarmDuration: Int = 10
    ): HaUpdateResult {
        return try {
            val api = getApiWithFallback() 
            if (api == null) {
                Log.w("HomeAssistantRepo", "sendAlarmStartToHA: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }

            val jsonPayload = buildJsonObject {
                put("action", "start")
                put("mobiel_betrouwbaar", mobileReliable)
                put("fallback", buildJsonObject {
                    put("speaker", fallbackSpeaker)
                    put("volume", fallbackVolume)
                    put("sound_url", fallbackSoundUrl)
                    put("alarm_duration", fallbackAlarmDuration)
                })
            }

            val response = api.triggerWebhook("alarm_app_v1", jsonPayload)
            if (response.isSuccessful) {
                Log.i("HomeAssistantRepo", "sendAlarmStartToHA SUCCESS: mobileReliable=$mobileReliable, speaker=$fallbackSpeaker, repeatInterval=$fallbackAlarmDuration")
                HaUpdateResult.Success
            } else {
                Log.e("HomeAssistantRepo", "sendAlarmStartToHA FAILED: HTTP ${response.code()}")
                HaUpdateResult.Error("HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "sendAlarmStartToHA FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij sturen alarm start: ${e.message}")
        }
    }

    /**
     * Stuur alarm STOP naar Home Assistant webhook
     */
    suspend fun sendAlarmStopToHA(): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
            if (api == null) {
                Log.w("HomeAssistantRepo", "sendAlarmStopToHA: No HA connection, skipping")
                return HaUpdateResult.Error("Geen verbinding met Home Assistant")
            }

            val jsonPayload = buildJsonObject {
                put("action", "stop")
            }

            val response = api.triggerWebhook("alarm_app_v1", jsonPayload)
            if (response.isSuccessful) {
                Log.i("HomeAssistantRepo", "sendAlarmStopToHA SUCCESS")
                HaUpdateResult.Success
            } else {
                Log.e("HomeAssistantRepo", "sendAlarmStopToHA FAILED: HTTP ${response.code()}")
                HaUpdateResult.Error("HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "sendAlarmStopToHA FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij sturen alarm stop: ${e.message}")
        }
    }
    
    // --- DayKit custom integration (/api/daykit/*) ---
    // Vervangt setBackupAlarmTime + setMobielBetrouwbaarVoorAlarm + resetBackupAlarmState +
    // setUserHomeStatus (arm) en sendAlarmStartToHA/sendAlarmStopToHA (start/stop) hierboven.
    // Gebruikt dezelfde getApiWithFallback()-verbinding (baseUrl + long-lived token), dus geen
    // apart "local_only"-webhook-gat en geen los AppDaemon-poort meer nodig.

    private fun buildFallbackJson(
        speaker: String?,
        volume: Int,
        soundUrl: String?,
        interval: Int?
    ) = buildJsonObject {
        put("speaker", speaker)
        put("volume", volume)
        put("sound_url", soundUrl)
        if (interval != null) put("interval", interval)
    }

    /**
     * "Wapen" de HA-watchdog voor het aankomende alarm: één aanroep i.p.v. de losse
     * resetBackupAlarmState + setMobielBetrouwbaarVoorAlarm(false) + setBackupAlarmTime +
     * setUserHomeStatus van de handmatige opzet. De integratie zelf leest aanwezigheid
     * live uit (person/device_tracker), dus gebruiker_thuis hoeft niet meer los gesynct te worden.
     *
     * @param fireAtIso UTC ISO-8601 tijdstip (bijv. "2026-07-09T04:31:30Z") waarop de watchdog,
     *   als de telefoon zich niet meldt via [reportAlarmAlive], de fallback-speaker mag starten.
     */
    suspend fun armBackupWatchdog(
        fireAtIso: String,
        fallbackSpeaker: String?,
        fallbackVolume: Int,
        fallbackSoundUrl: String?,
        fallbackInterval: Int,
        batteryPercent: Int? = null,
        batteryUsagePerHour: Int? = null,
        phoneIp: String? = null
    ): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
                ?: return HaUpdateResult.Error("Geen verbinding met Home Assistant").also {
                    Log.w("HomeAssistantRepo", "armBackupWatchdog: No HA connection, skipping")
                }
            val payload = buildJsonObject {
                put("action", "arm")
                put("fire_at", fireAtIso)
                put("fallback", buildFallbackJson(fallbackSpeaker, fallbackVolume, fallbackSoundUrl, fallbackInterval))
                if (batteryPercent != null) put("battery_percent", batteryPercent)
                if (batteryUsagePerHour != null) put("battery_usage_per_hour", batteryUsagePerHour)
                if (!phoneIp.isNullOrBlank()) put("phone_ip", phoneIp)
            }
            Log.d("HABackup", "armBackupWatchdog: battery_percent=$batteryPercent% battery_usage_per_hour=$batteryUsagePerHour")
            api.sendAgendaAlarmEvent(payload)
            Log.i("HomeAssistantRepo", "armBackupWatchdog SUCCESS: fireAt=$fireAtIso speaker=$fallbackSpeaker")
            HaUpdateResult.Success
        } catch (e: retrofit2.HttpException) {
            Log.e("HomeAssistantRepo", "armBackupWatchdog FAILED: HTTP ${e.code()} - is de daykit-integratie geinstalleerd?", e)
            HaUpdateResult.Error("HTTP ${e.code()}: ${e.message()}")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "armBackupWatchdog FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij wapenen backup watchdog: ${e.message}")
        }
    }

    /**
     * Meldt alleen het huidige lokale IP-adres van de telefoon aan de integratie, zonder iets te
     * wapenen (actie "report_ip", zie http_views.py).
     *
     * Bestaat omdat het IP eerder uitsluitend meeliftte op [armBackupWatchdog] en
     * [reportAlarmAlive]. Daardoor had de ingebouwde ping-sensor pas iets te pingen zodra er een
     * agenda-alarm ingepland stond: bij een verse koppeling bleef die leeg, en wie even geen
     * aankomend alarm had ook. Gaf de router de telefoon een nieuw adres, dan bleef HA het oude
     * pingen tot het volgende alarm - met een onterechte "niet thuis" tot gevolg.
     *
     * Best-effort: dit is telemetrie, dus een mislukte aanroep (geen HA-verbinding, integratie nog
     * niet geïnstalleerd) mag nergens iets blokkeren en wordt alleen gelogd.
     */
    suspend fun reportPhoneIp(phoneIp: String?): HaUpdateResult {
        if (phoneIp.isNullOrBlank()) {
            return HaUpdateResult.Error("Geen lokaal IP-adres gevonden")
        }
        return try {
            val api = getApiWithFallback()
                ?: return HaUpdateResult.Error("Geen verbinding met Home Assistant").also {
                    Log.d("HomeAssistantRepo", "reportPhoneIp: No HA connection, skipping")
                }
            api.sendAgendaAlarmEvent(
                buildJsonObject {
                    put("action", "report_ip")
                    put("phone_ip", phoneIp)
                }
            )
            Log.i("HomeAssistantRepo", "reportPhoneIp SUCCESS: $phoneIp")
            HaUpdateResult.Success
        } catch (e: Exception) {
            Log.w("HomeAssistantRepo", "reportPhoneIp FAILED: ${e.message}")
            HaUpdateResult.Error("Fout bij melden telefoon-IP: ${e.message}")
        }
    }

    /**
     * Meldt bij HA dat de telefoon het alarm zelf daadwerkelijk aan het afspelen is
     * (vervangt sendAlarmStartToHA). [mobielBetrouwbaar] = false laat de watchdog alsnog
     * ingrijpen ondanks dat de telefoon zich meldt (bijv. batterij-trigger).
     */
    suspend fun reportAlarmAlive(
        mobielBetrouwbaar: Boolean,
        fallbackSpeaker: String?,
        fallbackVolume: Int,
        fallbackSoundUrl: String?,
        fallbackInterval: Int? = null,
        batteryPercent: Int? = null,
        batteryUsagePerHour: Int? = null,
        phoneIp: String? = null
    ): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
                ?: return HaUpdateResult.Error("Geen verbinding met Home Assistant").also {
                    Log.w("HomeAssistantRepo", "reportAlarmAlive: No HA connection, skipping")
                }
            val payload = buildJsonObject {
                put("action", "start")
                put("mobiel_betrouwbaar", mobielBetrouwbaar)
                put("fallback", buildFallbackJson(fallbackSpeaker, fallbackVolume, fallbackSoundUrl, fallbackInterval))
                if (batteryPercent != null) put("battery_percent", batteryPercent)
                if (batteryUsagePerHour != null) put("battery_usage_per_hour", batteryUsagePerHour)
                if (!phoneIp.isNullOrBlank()) put("phone_ip", phoneIp)
            }
            Log.d("HABackup", "reportAlarmAlive: battery_percent=$batteryPercent% battery_usage_per_hour=$batteryUsagePerHour")
            api.sendAgendaAlarmEvent(payload)
            Log.i("HomeAssistantRepo", "reportAlarmAlive SUCCESS: mobielBetrouwbaar=$mobielBetrouwbaar")
            HaUpdateResult.Success
        } catch (e: retrofit2.HttpException) {
            Log.e("HomeAssistantRepo", "reportAlarmAlive FAILED: HTTP ${e.code()}", e)
            HaUpdateResult.Error("HTTP ${e.code()}: ${e.message()}")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "reportAlarmAlive FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij melden alarm-start: ${e.message}")
        }
    }

    /**
     * Meldt bij HA dat het alarm (en eventuele fallback) gestopt is (vervangt sendAlarmStopToHA).
     */
    suspend fun reportAlarmStop(): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
                ?: return HaUpdateResult.Error("Geen verbinding met Home Assistant").also {
                    Log.w("HomeAssistantRepo", "reportAlarmStop: No HA connection, skipping")
                }
            val payload = buildJsonObject { put("action", "stop") }
            api.sendAgendaAlarmEvent(payload)
            Log.i("HomeAssistantRepo", "reportAlarmStop SUCCESS")
            HaUpdateResult.Success
        } catch (e: retrofit2.HttpException) {
            Log.e("HomeAssistantRepo", "reportAlarmStop FAILED: HTTP ${e.code()}", e)
            HaUpdateResult.Error("HTTP ${e.code()}: ${e.message()}")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "reportAlarmStop FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij melden alarm-stop: ${e.message}")
        }
    }

    /**
     * Ontwapent de HA-watchdog zonder dat er een alarm afgespeeld is (vervangt geen bestaande
     * functie) - voor het geval een gewapend alarm geannuleerd wordt zonder dat er een nieuw
     * volgend alarm gepland wordt (bv. gebruiker verwijdert het laatste agenda-item).
     */
    suspend fun disarmBackupWatchdog(): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
                ?: return HaUpdateResult.Error("Geen verbinding met Home Assistant").also {
                    Log.w("HomeAssistantRepo", "disarmBackupWatchdog: No HA connection, skipping")
                }
            val payload = buildJsonObject { put("action", "disarm") }
            api.sendAgendaAlarmEvent(payload)
            Log.i("HomeAssistantRepo", "disarmBackupWatchdog SUCCESS")
            HaUpdateResult.Success
        } catch (e: retrofit2.HttpException) {
            Log.e("HomeAssistantRepo", "disarmBackupWatchdog FAILED: HTTP ${e.code()}", e)
            HaUpdateResult.Error("HTTP ${e.code()}: ${e.message()}")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "disarmBackupWatchdog FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij ontwapenen backup watchdog: ${e.message}")
        }
    }

    /**
     * Upload een alarmgeluid naar HA's eigen opslag via de nieuwe integratie
     * (vervangt de losse AppDaemon-upload in [com.dd.daykit.homeassistant.SoundHaSync]).
     * Gebruikt dezelfde HA-verbinding/poort als al het andere - geen aparte AppDaemon-URL nodig.
     */
    suspend fun uploadSoundToHomeAssistant(filename: String, base64Data: String): HaUpdateResult {
        return try {
            // getUploadApiWithFallback() i.p.v. getApiWithFallback(): deze upload kan een
            // base64-payload van enkele MB zijn, de normale 15s-timeout was hier de daadwerkelijke
            // oorzaak van steevast mislukkende uploads (zie HomeAssistantClient.getUploadApi).
            val api = getUploadApiWithFallback()
                ?: return HaUpdateResult.Error("Geen verbinding met Home Assistant").also {
                    Log.w("HomeAssistantRepo", "uploadSoundToHomeAssistant: No HA connection, skipping")
                }
            val payload = buildJsonObject {
                put("filename", filename)
                put("data_base64", base64Data)
            }
            api.uploadAgendaAlarmSound(payload)
            Log.i("HomeAssistantRepo", "uploadSoundToHomeAssistant SUCCESS: $filename")
            HaUpdateResult.Success
        } catch (e: retrofit2.HttpException) {
            Log.e("HomeAssistantRepo", "uploadSoundToHomeAssistant FAILED: HTTP ${e.code()}", e)
            HaUpdateResult.Error("HTTP ${e.code()}: ${e.message()}")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("HomeAssistantRepo", "uploadSoundToHomeAssistant TIMEOUT", e)
            HaUpdateResult.Error("Timeout - Home Assistant reageerde niet op tijd (bestand mogelijk te groot of trage verbinding)")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "uploadSoundToHomeAssistant FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij uploaden geluid: ${e.message}")
        }
    }

    /**
     * Stuurt een (partiele) wijziging van de gedeelde AgendaAlarm-config naar Home Assistant,
     * die 'm meteen in de config-entry options van de daykit-integratie zet
     * (zichtbaar/bewerkbaar via Instellingen > Integraties > Configureren). Bedoeld om na
     * elke lokale save aan te roepen zodat beide kanten in sync blijven.
     */
    suspend fun pushAgendaAlarmConfigPatch(patch: JsonObject): HaUpdateResult {
        return try {
            val api = getApiWithFallback()
                ?: return HaUpdateResult.Error("Geen verbinding met Home Assistant").also {
                    Log.w("HomeAssistantRepo", "pushAgendaAlarmConfigPatch: No HA connection, skipping")
                }
            api.postAgendaAlarmConfig(patch)
            Log.i("HomeAssistantRepo", "pushAgendaAlarmConfigPatch SUCCESS: ${patch.keys}")
            HaUpdateResult.Success
        } catch (e: retrofit2.HttpException) {
            Log.e("HomeAssistantRepo", "pushAgendaAlarmConfigPatch FAILED: HTTP ${e.code()}", e)
            HaUpdateResult.Error("HTTP ${e.code()}: ${e.message()}")
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "pushAgendaAlarmConfigPatch FAILED: ${e.message}", e)
            HaUpdateResult.Error("Fout bij synchroniseren config: ${e.message}")
        }
    }

    /**
     * Haalt de volledige config op zoals die momenteel in HA's config-entry staat (GET
     * /api/daykit/config, zie config_sync.py's serialize_config) - niet alleen de
     * "entities"-lijst, ook alle overige velden die zowel in de app als via HA's eigen
     * "Configureren"-scherm (config_flow.py) bewerkt kunnen worden (speaker/presence/uit-bed/
     * scripts/interval). Dit is puur een leesactie zonder herlaad-bijwerking aan de HA-kant (in
     * tegenstelling tot een POST/config-sync, die de daykit-integratie herlaadt) -
     * bedoeld voor de expliciete, door de gebruiker bevestigde "HA-update overnemen?"-check
     * ([com.dd.daykit.viewmodel.HaSettingsViewModel.checkForHaEntityUpdate]), NOOIT
     * automatisch aangeroepen: de app blijft verder altijd leidend, zie
     * [pushAgendaAlarmConfigPatch] en applyHomeAssistantSettings().
     */
    suspend fun fetchAgendaAlarmConfig(): HaAgendaAlarmConfig? {
        return try {
            val api = getApiWithFallback() ?: return null
            val json = api.getAgendaAlarmConfig()
            fun str(key: String): String? = (json[key] as? JsonPrimitive)?.contentOrNull
            fun bool(key: String): Boolean? {
                val prim = json[key] as? JsonPrimitive ?: return null
                // HA's boolean-selector levert een echte JSON-bool, maar een via YAML/oudere
                // versies gezette waarde kan als "true"/"on"/"1" binnenkomen - dat is nog steeds
                // een ondubbelzinnige ja/nee, dus niet stilzwijgend weggooien.
                prim.booleanOrNull?.let { return it }
                return when (prim.contentOrNull?.lowercase()) {
                    "true", "on", "1", "yes" -> true
                    "false", "off", "0", "no" -> false
                    else -> null
                }
            }
            /**
             * HA's number-selector (slider/box) levert zijn waarde als FLOAT terug - een volume
             * van 70 komt binnen als `70.0`. `intOrNull` faalt daarop en gaf null terug, waardoor
             * elke in HA ingestelde volume-/interval-/timeout-waarde stilletjes genegeerd werd en
             * er dus nooit een "HA-update gevonden"-melding voor verscheen. Daarom hier via
             * doubleOrNull + afronden, met een string-fallback voor handmatig gezette waarden.
             */
            fun int(key: String): Int? {
                val prim = json[key] as? JsonPrimitive ?: return null
                prim.intOrNull?.let { return it }
                prim.doubleOrNull?.let { return Math.round(it).toInt() }
                return prim.contentOrNull?.trim()?.toDoubleOrNull()?.let { Math.round(it).toInt() }
            }

            /**
             * Aparte parser voor epoch-milliseconden: die passen niet in een Int (een tijdstempel
             * van nu is ruim boven Int.MAX_VALUE), dus [int] zou hier stilzwijgend overlopen.
             * Verder dezelfde tolerantie: JSON-int, JSON-float of een string met een getal.
             */
            fun long(key: String): Long? {
                val prim = json[key] as? JsonPrimitive ?: return null
                prim.longOrNull?.let { return it }
                prim.doubleOrNull?.let { return Math.round(it) }
                return prim.contentOrNull?.trim()?.toDoubleOrNull()?.let { Math.round(it) }
            }
            HaAgendaAlarmConfig(
                entities = (json["entities"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                speakerEntityId = str("speaker_entity_id"),
                speakerMode = str("speaker_mode"),
                defaultVolume = int("default_volume"),
                skipBackupVolume = bool("skip_backup_volume"),
                presenceEntityId = str("presence_entity_id"),
                presenceExpectedState = str("presence_expected_state"),
                outOfBedEntityId = str("out_of_bed_entity_id"),
                outOfBedExpectedValue = str("out_of_bed_expected_value"),
                // "out_of_bed_check_enabled", niet "out_of_bed_enabled": dat laatste bestaat aan
                // de HA-kant niet (zie const.py's CONF_OUT_OF_BED_ENABLED) en leverde dus altijd
                // null op - de uit-bed-schakelaar kwam daardoor nooit door vanuit HA, ook al werd
                // hij bij het terugpushen wél onder de juiste naam verstuurd.
                outOfBedEnabled = bool("out_of_bed_check_enabled"),
                alarmScriptEntityId = str("alarm_script_entity_id"),
                alarmScriptEnabled = bool("alarm_script_enabled"),
                alarmScriptIgnorePresence = bool("alarm_script_ignore_presence"),
                timerScriptEntityId = str("timer_script_entity_id"),
                timerScriptEnabled = bool("timer_script_enabled"),
                timerScriptIgnorePresence = bool("timer_script_ignore_presence"),
                defaultInterval = int("default_interval"),
                timerSpeakerEntityId = str("timer_speaker_entity_id"),
                timerSpeakerMode = str("timer_speaker_mode"),
                timerSpeakerVolume = int("timer_speaker_volume"),
                timerSpeakerSkipVolume = bool("timer_speaker_skip_volume"),
                timerSpeakerSoundUrl = str("timer_speaker_sound_url"),
                weatherSpeakerEntityId = str("weather_speaker_entity_id"),
                weatherSpeakerMode = str("weather_speaker_mode"),
                weatherSpeakerVolume = int("weather_speaker_volume"),
                weatherSpeakerSkipVolume = bool("weather_speaker_skip_volume"),
                weatherSpeakerSoundUrl = str("weather_speaker_sound_url"),
                weatherTtsEnabled = bool("weather_tts_enabled"),
                defaultSoundUrl = str("default_sound_url"),
                notifyService = str("notify_service"),
                safetyTimeoutSeconds = int("safety_timeout_seconds"),
                configLastModified = long("config_last_modified"),
                configLastModifiedBy = str("config_last_modified_by")
            )
        } catch (e: Exception) {
            Log.e("HomeAssistantRepo", "fetchAgendaAlarmConfig FAILED: ${e.message}", e)
            null
        }
    }

    suspend fun getLocalSounds(): List<com.dd.daykit.sound.Sound> {
        return withContext(Dispatchers.IO) {
            val repo = com.dd.daykit.sound.SoundRepository.getInstance(settingsStorage.context)
            if (repo.sounds.value.isEmpty()) {
                repo.loadSounds()
                kotlinx.coroutines.delay(500)
            }
            repo.sounds.value
        }
    }

    suspend fun uploadAndGetSoundUrl(sound: com.dd.daykit.sound.Sound): String? {
        return uploadAndGetSoundUrlOrError(sound).getOrNull()
    }

    /**
     * Zelfde als [uploadAndGetSoundUrl], maar geeft bij falen de daadwerkelijke reden terug
     * (timeout, HTTP-fout, netwerkfout, ...) i.p.v. alleen null - voor UI-plekken (zoals de
     * "Test backup-alarm"-knop) die de gebruiker willen vertellen WAAROM een upload mislukte,
     * i.p.v. een generieke "mislukt"-melding die geen enkel aanknopingspunt geeft.
     */
    suspend fun uploadAndGetSoundUrlOrError(sound: com.dd.daykit.sound.Sound): Result<String> {
        return withContext(Dispatchers.IO) {
            val result = com.dd.daykit.homeassistant.SoundHaSync.uploadSoundToHa(
                settingsStorage.context, sound
            )
            if (result.isFailure) {
                val error = result.exceptionOrNull() ?: Exception("Onbekende fout")
                Log.e("HomeAssistantRepo", "Sound upload failed: ${error.message}")
                return@withContext Result.failure(error)
            }
            val baseUrl = settingsStorage.settingsFlow.firstOrNull()?.activeBaseUrl?.trimEnd('/')
            if (baseUrl.isNullOrBlank()) {
                Result.failure(Exception("Geen actieve Home Assistant URL"))
            } else {
                val safeFilename = com.dd.daykit.homeassistant.SoundHaSync.safeUploadedFilename(sound)
                Result.success(com.dd.daykit.homeassistant.HaPaths.soundUrl(baseUrl, safeFilename))
            }
        }
    }

    /**
     * Upload van vluchtige weeralarm-tts-audio (WAV, lokaal gerenderd door Android's eigen
     * TextToSpeech - zie WeatherAlertWorker.kt) naar het aparte weather_tts_upload-endpoint.
     * Dat endpoint overschrijft steeds hetzelfde vaste bestand aan de HA-kant (zie
     * DayKitWeatherTtsUploadView in http_views.py) - dus bewust GEEN eigen bestandsnaam
     * of manifest-registratie zoals bij [uploadAndGetSoundUrl]/[uploadSoundToHomeAssistant],
     * dit hoort niet in de permanente custom-sound-bibliotheek/backup-sync thuis.
     *
     * Retourneert de afspeel-URL mét een cache-busting query-param (?t=<timestamp>): omdat de
     * URL aan de HA-kant altijd identiek blijft, kan een smart speaker anders een eerder
     * afgespeelde, inmiddels alweer overschreven versie uit zijn eigen cache blijven afspelen.
     */
    suspend fun uploadWeatherTtsAndGetUrl(audioBytes: ByteArray): String? {
        return withContext(Dispatchers.IO) {
            try {
                val api = getUploadApiWithFallback()
                if (api == null) {
                    Log.w("HomeAssistantRepo", "uploadWeatherTtsAndGetUrl: No HA connection, skipping")
                    return@withContext null
                }
                val base64Data = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
                val payload = buildJsonObject {
                    put("data_base64", base64Data)
                }
                api.uploadWeatherTts(payload)

                val baseUrl = settingsStorage.settingsFlow.firstOrNull()?.activeBaseUrl?.trimEnd('/')
                if (baseUrl.isNullOrBlank()) {
                    null
                } else {
                    com.dd.daykit.homeassistant.HaPaths.weatherTtsUrl(baseUrl)
                }
            } catch (e: Exception) {
                Log.e("HomeAssistantRepo", "uploadWeatherTtsAndGetUrl FAILED: ${e.message}", e)
                null
            }
        }
    }
}

/**
 * Getypeerde weergave van GET /api/daykit/config (zie config_sync.py's
 * CONFIG_FIELDS) - alle velden die zowel vanuit de app als vanuit HA's eigen
 * "Configureren"-scherm bewerkt kunnen worden. Elk veld is nullable: `null` betekent hier
 * "HA heeft hier niets over gezegd" (ontbrekend/onbekend in de JSON), niet per se "leeg" -
 * zie HaSettingsViewModel.buildConfigFieldDiffs() voor hoe dit onderscheid gebruikt wordt om
 * alleen ECHTE, betekenisvolle HA-wijzigingen te signaleren.
 */
data class HaAgendaAlarmConfig(
    val entities: List<String>,
    val speakerEntityId: String?,
    val speakerMode: String?,
    val defaultVolume: Int?,
    val skipBackupVolume: Boolean?,
    val presenceEntityId: String?,
    val presenceExpectedState: String?,
    val outOfBedEntityId: String?,
    val outOfBedExpectedValue: String?,
    val outOfBedEnabled: Boolean?,
    val alarmScriptEntityId: String?,
    val alarmScriptEnabled: Boolean?,
    val alarmScriptIgnorePresence: Boolean?,
    val timerScriptEntityId: String?,
    val timerScriptEnabled: Boolean?,
    val timerScriptIgnorePresence: Boolean?,
    val defaultInterval: Int?,
    // Timer-/weer-speaker: eigen naamruimte in HA (zie config_sync.py's CONF_TIMER_SPEAKER_*/
    // CONF_WEATHER_SPEAKER_*), los van de Agenda-alarm-watchdogvelden hierboven - deze voeden
    // geen watchdog, maar spiegelen HomeAssistantSettings.timerSpeaker/weatherSpeaker.
    val timerSpeakerEntityId: String?,
    val timerSpeakerMode: String?,
    val timerSpeakerVolume: Int?,
    val timerSpeakerSkipVolume: Boolean?,
    val timerSpeakerSoundUrl: String?,
    val weatherSpeakerEntityId: String?,
    val weatherSpeakerMode: String?,
    val weatherSpeakerVolume: Int?,
    val weatherSpeakerSkipVolume: Boolean?,
    val weatherSpeakerSoundUrl: String?,
    // Weeralarm uitspreken - hoort bij de Weer-speaker-sectie in HA's Configureren-scherm, wordt
    // door de app zelf uitgevoerd (lokale TextToSpeech, zie WeatherAlertWorker.kt).
    val weatherTtsEnabled: Boolean?,
    // Geluid van het agenda-alarm/de watchdog. Altijd een volledige /local/daykit_sounds/-URL;
    // de bestandsnaam daarin is de gedeelde sleutel met de lokale geluiden-bibliotheek van de app
    // (zie SoundHaSync.safeUploadedFilename en HaSettingsViewModel.soundIdForHaUrl).
    val defaultSoundUrl: String?,
    // HA-only instellingen uit de "Defaults"-sectie: de app gebruikt ze zelf niet, maar houdt ze
    // wel bij zodat een wijziging in HA zichtbaar wordt en niet bij de volgende push sneuvelt.
    val notifyService: String?,
    val safetyTimeoutSeconds: Int?,
    /**
     * Wanneer HA's config voor het laatst gewijzigd is (ms, HA-klok bij een wijziging in HA;
     * de door de app meegestuurde waarde bij een push) en door wie: "ha" of "app".
     *
     * De app vergelijkt deze stempel NIET met haar eigen klok — telefoon en server lopen zelden
     * gelijk. Ze onthoudt alleen welke waarde ze het laatst gezien heeft; is die veranderd én
     * staat er "ha", dan is er echt in HA iets gewijzigd. Zonder deze twee velden kon de app
     * alleen waardes vergelijken en bleef ze vragen of je je eigen instellingen wilde
     * "terugzetten" naar wat er nog in HA stond. Null bij een oudere integratieversie; de check
     * valt dan terug op het oude gedrag.
     */
    val configLastModified: Long?,
    val configLastModifiedBy: String?
)

sealed class HaConnectionResult {
    data class Success(val baseUrl: String) : HaConnectionResult()
    data class Error(val message: String?) : HaConnectionResult()
}

sealed class HaUpdateResult {
    data object Success : HaUpdateResult()
    data class Error(val message: String) : HaUpdateResult()
}

data class SensorCheckResult(
    val entityId: String,
    val exists: Boolean,
    val state: String?,
    val errorMessage: String?
)

data class HaEntity(
    val entityId: String,
    val friendlyName: String,
    val state: String,
    val domain: String
)

sealed class HaPlayMediaResult {
    data object Success : HaPlayMediaResult()
    data class Error(val message: String) : HaPlayMediaResult()
}
