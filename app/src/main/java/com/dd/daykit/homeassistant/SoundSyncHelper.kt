package com.dd.daykit.homeassistant

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.dd.daykit.sound.HttpServerManager
import com.dd.daykit.sound.SoundRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper voor het synchroniseren van geluiden naar Home Assistant
 */
object SoundSyncHelper {
    private const val TAG = "SoundSyncHelper"
    
    /**
     * Haal alle geluiden op en maak SoundInfo lijst met streaming URLs
     */
    suspend fun getAllSoundsForSync(context: Context): List<HomeAssistantSync.SoundInfo> = withContext(Dispatchers.IO) {
        val repository = SoundRepository.getInstance(context)
        val sounds = repository.sounds.value
        
        // Get best IP address (Tailscale if enabled, otherwise WiFi)
        val serverStatus = HttpServerManager.getServerStatus(context)
        val deviceIp = if (serverStatus.remoteAccessEnabled && serverStatus.tailscaleIp != null) {
            Log.i(TAG, "Using Tailscale IP for remote access: ${serverStatus.tailscaleIp}")
            serverStatus.tailscaleIp
        } else {
            serverStatus.deviceIp ?: "localhost"
        }
        
        val port = HttpServerManager.getPort(context)
        val apiKey = HttpServerManager.getApiKey(context)
        
        Log.d(TAG, "Building sound list for sync: ${sounds.size} sounds, IP: $deviceIp (remote: ${serverStatus.remoteAccessEnabled})")
        
        // Convert to SoundInfo with streaming URLs
        sounds.map { sound ->
            val streamingUrl = "http://$deviceIp:$port/sounds/${sound.id}?api_key=$apiKey"
            
            HomeAssistantSync.SoundInfo(
                id = sound.id,
                name = sound.name,
                duration = sound.duration,
                streaming_url = streamingUrl
            )
        }
    }
    
    /**
     * Get best device IP address (Tailscale if available, otherwise WiFi)
     * @deprecated Use HttpServerManager.getServerStatus(context).getPrimaryIp() instead
     */
    @Deprecated("Use HttpServerManager methods", ReplaceWith("HttpServerManager.getServerStatus(context).getPrimaryIp()"))
    fun getDeviceIpAddress(context: Context): String? {
        val status = HttpServerManager.getServerStatus(context)
        return status.getPrimaryIp()
    }
    
    /**
     * Sync alle geluiden naar Home Assistant
     */
    suspend fun syncAllSounds(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if HTTP server is enabled
            if (!HttpServerManager.isEnabled(context)) {
                return@withContext Result.failure(
                    Exception("HTTP Server moet ingeschakeld zijn voor geluid sync")
                )
            }
            
            // Check if HA sync is enabled
            if (!HomeAssistantSync.isEnabled(context)) {
                return@withContext Result.failure(
                    Exception("Home Assistant sync moet ingeschakeld zijn")
                )
            }
            
            // Get all sounds
            val sounds = getAllSoundsForSync(context)
            
            if (sounds.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Geen geluiden gevonden om te synchroniseren")
                )
            }
            
            Log.d(TAG, "Syncing ${sounds.size} sounds to Home Assistant")
            
            // Send to Home Assistant
            val result = HomeAssistantSync.syncSounds(context, sounds)
            
            return@withContext if (result.isSuccess) {
                Result.success("${sounds.size} geluiden gesynchroniseerd")
            } else {
                Result.failure(
                    result.exceptionOrNull() ?: Exception("Sync mislukt")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing sounds", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get preview van wat gesynchroniseerd zal worden
     */
    suspend fun getSyncPreview(context: Context): SyncPreview = withContext(Dispatchers.IO) {
        val sounds = getAllSoundsForSync(context)
        val serverStatus = HttpServerManager.getServerStatus(context)
        val deviceIp = serverStatus.getPrimaryIp()
        val httpServerEnabled = HttpServerManager.isEnabled(context)
        val haSyncEnabled = HomeAssistantSync.isEnabled(context)
        val haUrl = HomeAssistantSync.getBaseUrl(context)
        val remoteAccessEnabled = serverStatus.remoteAccessEnabled
        val tailscaleIp = serverStatus.tailscaleIp
        
        SyncPreview(
            soundCount = sounds.size,
            deviceIp = deviceIp,
            httpServerEnabled = httpServerEnabled,
            haSyncEnabled = haSyncEnabled,
            haUrl = haUrl,
            canSync = httpServerEnabled && haSyncEnabled && haUrl.isNotEmpty() && sounds.isNotEmpty(),
            remoteAccessEnabled = remoteAccessEnabled,
            tailscaleIp = tailscaleIp
        )
    }
    
    data class SyncPreview(
        val soundCount: Int,
        val deviceIp: String?,
        val httpServerEnabled: Boolean,
        val haSyncEnabled: Boolean,
        val haUrl: String,
        val canSync: Boolean,
        val remoteAccessEnabled: Boolean = false,
        val tailscaleIp: String? = null
    )
}
