package com.dd.daykit

import android.content.Context
import android.util.Log
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.data.HaPlayMediaResult
import com.dd.daykit.data.SpeakerContext
import com.dd.daykit.data.speakerFor
import com.dd.daykit.network.HomeAssistantClient
import com.dd.daykit.sound.HttpServerManager
import com.dd.daykit.sound.SoundRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Helper object voor het afspelen van alarmen op externe speakers via Home Assistant
 */
object ExternalSpeakerHelper {
    
    private const val TAG = "ExternalSpeakerHelper"
    
    /**
     * Speelt een alarm af op een externe speaker via Home Assistant
     * 
     * @param context Android context
     * @param speakerEntityId Entity ID van de media_player (bijv. "media_player.woonkamer")
     * @param alarmSoundUrl URL naar het alarm geluid bestand
     * @param speakerContext welk onderdeel dit alarm is (ALARM/TIMER/WEATHER) - bepaalt welke
     *   volume/skipVolume-instelling gebruikt wordt, sinds de speaker-splitsing.
     * @return true als succesvol, false bij fout
     */
    suspend fun playAlarmOnSpeaker(
        context: Context,
        speakerEntityId: String,
        alarmSoundUrl: String,
        speakerContext: SpeakerContext
    ): Boolean {
        return try {
            Log.d(TAG, "Playing alarm on external speaker: $speakerEntityId")
            Log.d(TAG, "Sound URL: $alarmSoundUrl")
            
            val settingsStorage = HomeAssistantSettingsStorage(context)
            val client = HomeAssistantClient // Singleton
            val repository = HomeAssistantRepository(client, settingsStorage)

            // Bestaat/leeft de speaker-entiteit? media_player.play_media geeft bij een
            // niet-bestaande entity_id gewoon HTTP 200 terug (HA valideert entity_id niet op
            // servicecall-niveau) - zonder deze check "lukt" de call altijd, ook al speelt er
            // niets af, en komt de bestaande fallback-naar-telefoon hieronder dus nooit in actie.
            // GET /api/states/<entity_id> geeft wél een 404 bij een écht niet-bestaande entiteit.
            val entityCheck = withTimeoutOrNull(3000) {
                try {
                    repository.getEntityState(speakerEntityId)
                } catch (e: Exception) {
                    Log.w(TAG, "Speaker-entiteit $speakerEntityId niet gevonden/bereikbaar: ${e.message}")
                    null
                }
            }
            if (entityCheck == null) {
                Log.e(TAG, "Speaker $speakerEntityId niet gevonden of HA niet bereikbaar - val terug op telefoon")
                return false
            }
            if (entityCheck.state.equals("unavailable", ignoreCase = true) ||
                entityCheck.state.equals("unknown", ignoreCase = true)
            ) {
                Log.e(TAG, "Speaker $speakerEntityId is offline (state=${entityCheck.state}) - val terug op telefoon")
                return false
            }

            val speakerSettings = settingsStorage.settingsFlow.firstOrNull()?.speakerFor(speakerContext)
            val configuredVolume = speakerSettings?.volume
            if (configuredVolume != null && speakerSettings?.skipVolume != true) {
                withTimeoutOrNull(3000) {
                    val volumeResult = repository.setSpeakerVolume(speakerEntityId, configuredVolume)
                    if (volumeResult is HaPlayMediaResult.Error) {
                        Log.w(TAG, "setSpeakerVolume failed: ${volumeResult.message}")
                    }
                }
            }

            // Add timeout to prevent hanging
            val result = withTimeoutOrNull(5000) {
                repository.playMediaOnSpeaker(
                    entityId = speakerEntityId,
                    mediaContentId = alarmSoundUrl,
                    mediaContentType = "music"
                )
            }
            
            if (result == null) {
                Log.e(TAG, "Timeout (5s) while trying to play on external speaker")
                return false
            }
            
            when (result) {
                is HaPlayMediaResult.Success -> {
                    Log.d(TAG, "Successfully started alarm on external speaker")
                    true
                }
                is HaPlayMediaResult.Error -> {
                    Log.e(TAG, "Failed to play alarm on external speaker: ${result.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while playing alarm on external speaker", e)
            false
        }
    }

    /**
     * URL uit HA-instellingen (GitHub of eigen URL) die de Home Assistant-server
     * kan ophalen — in tegenstelling tot een lokale telefoon-URL.
     */
    suspend fun getExternalSpeakerSoundUrl(context: Context, speakerContext: SpeakerContext): String? {
        return try {
            val settings = HomeAssistantSettingsStorage(context).settingsFlow.firstOrNull()
                ?: return null
            val soundId = settings.speakerFor(speakerContext).soundId ?: return null
            val soundRepo = com.dd.daykit.sound.SoundRepository.getInstance(context)
            val sound = soundRepo.sounds.value.find { it.id == soundId } ?: return null
            val baseUrl = settings.activeBaseUrl?.trimEnd('/') ?: return null
            val safeName = com.dd.daykit.homeassistant.SoundHaSync.safeUploadedFilename(sound)
            com.dd.daykit.homeassistant.HaPaths.soundUrl(baseUrl, safeName)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading external speaker sound URL from settings", e)
            null
        }
    }

    /**
     * Stopt het afspelen op een externe speaker via Home Assistant
     * 
     * @param context Android context
     * @param speakerEntityId Entity ID van de media_player
     * @return true als succesvol, false bij fout
     */
    suspend fun stopAlarmOnSpeaker(
        context: Context,
        speakerEntityId: String
    ): Boolean {
        return try {
            Log.d(TAG, "Stopping alarm on external speaker: $speakerEntityId")
            
            val settingsStorage = HomeAssistantSettingsStorage(context)
            val client = HomeAssistantClient // Singleton
            val repository = HomeAssistantRepository(client, settingsStorage)
            
            val result = repository.stopMediaOnSpeaker(speakerEntityId)
            
            when (result) {
                is HaPlayMediaResult.Success -> {
                    Log.d(TAG, "Successfully stopped alarm on external speaker")
                    true
                }
                is HaPlayMediaResult.Error -> {
                    Log.e(TAG, "Failed to stop alarm on external speaker: ${result.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while stopping alarm on external speaker", e)
            false
        }
    }
    
    /**
     * Bepaalt de URL van het alarm geluid dat afgespeeld moet worden
     * Ondersteunt zowel custom sounds (via HTTP server) als standaard ringtones (via URI)
     * 
     * @param context Android context
     * @param soundUriString Optional sound URI string. Can be custom sound URI or system ringtone URI.
     * @return URL naar het alarm geluid
     */
    fun getAlarmSoundUrl(context: Context, soundUriString: String? = null): String {
        try {
            // If a sound URI is provided, check if it's a custom sound or system ringtone
            if (soundUriString != null) {
                val uri = android.net.Uri.parse(soundUriString)
                
                // Check if it's a custom sound (content://com.dd.daykit...)
                if (uri.authority == "com.dd.daykit.customsounds") {
                    val soundId = uri.lastPathSegment?.toLongOrNull()
                    if (soundId != null) {
                        val customUrl = getCustomSoundUrl(context, soundId)
                        if (customUrl != null) {
                            return customUrl
                        }
                    }
                }
                
                // Check if it's a file URI (custom sound via direct file path)
                if (uri.scheme == "file") {
                    val repository = SoundRepository.getInstance(context)
                    // Note: This relies on repository being loaded, which it should be in the service
                    val sounds = repository.sounds.value
                    
                    // Match by file path
                    val matchingSound = sounds.find { sound ->
                        sound.filePath == uri.path || 
                        (uri.path != null && sound.filePath.endsWith(uri.path!!.substringAfterLast('/')))
                    }
                    
                    if (matchingSound != null) {
                        val customUrl = getCustomSoundUrl(context, matchingSound.id)
                        if (customUrl != null) return customUrl
                    } else {
                         Log.w(TAG, "Could not find custom sound in repository for path: ${uri.path}")
                    }
                }
                
                // For system ringtones, we need to serve them via HTTP server
                // First check if it's a system ringtone
                if (uri.scheme == "content" && uri.authority == "media") {
                    return getSystemRingtoneUrl(context, soundUriString)
                }
            }
            
            // Fallback: try to get first custom sound or use default
            Log.w(TAG, "Could not resolve sound URI: $soundUriString. Falling back to default.")
            return getCustomSoundUrl(context, null) ?: getFallbackUrl()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error building alarm sound URL", e)
            return getFallbackUrl()
        }
    }
    
    /**
     * Get URL for a custom sound via HTTP server
     */
    private fun getCustomSoundUrl(context: Context, soundId: Long?): String? {
        try {
            if (!HttpServerManager.isEnabled(context)) {
                Log.w(TAG, "HTTP Server not enabled for custom sounds")
                return null
            }
            
            val repository = SoundRepository.getInstance(context)
            val sounds = repository.sounds.value
            
            if (sounds.isEmpty()) {
                Log.w(TAG, "No custom sounds available")
                return null
            }
            
            val targetSound = if (soundId != null) {
                sounds.find { it.id == soundId } ?: sounds.first()
            } else {
                sounds.first()
            }
            
            val ip = HttpServerManager.getDeviceIpAddress(context)
            if (ip == null) {
                Log.w(TAG, "No device IP available")
                return null
            }
            
            val port = HttpServerManager.getPort(context)
            val apiKey = HttpServerManager.getApiKey(context)
            
            val streamingUrl = "http://$ip:$port/sounds/${targetSound.id}?api_key=$apiKey"
            
            Log.i(TAG, "Using custom sound: ${targetSound.name}")
            Log.i(TAG, "Streaming URL: $streamingUrl")
            
            return streamingUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error building custom sound URL", e)
            return null
        }
    }
    
    /**
     * Get URL for a system ringtone via HTTP server
     * System ringtones need to be served through the HTTP server to be accessible by Home Assistant
     */
    private fun getSystemRingtoneUrl(context: Context, ringtoneUri: String): String {
        try {
            if (!HttpServerManager.isEnabled(context)) {
                Log.w(TAG, "HTTP Server not enabled, cannot serve system ringtone")
                return getFallbackUrl()
            }
            
            val ip = HttpServerManager.getDeviceIpAddress(context)
            if (ip == null) {
                Log.w(TAG, "No device IP available for system ringtone")
                return getFallbackUrl()
            }
            
            val port = HttpServerManager.getPort(context)
            val apiKey = HttpServerManager.getApiKey(context)
            
            // Encode the ringtone URI for the HTTP server
            val encodedUri = java.net.URLEncoder.encode(ringtoneUri, "UTF-8")
            val streamingUrl = "http://$ip:$port/ringtone?uri=$encodedUri&api_key=$apiKey"
            
            Log.i(TAG, "Using system ringtone via HTTP server")
            Log.i(TAG, "Streaming URL: $streamingUrl")
            
            return streamingUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error building system ringtone URL", e)
            return getFallbackUrl()
        }
    }
    
    /**
     * Fallback URL when custom sounds are not available
     */
    private fun getFallbackUrl(): String {
        return "https://www.soundjay.com/buttons/sounds/beep-07a.mp3"
    }
}
