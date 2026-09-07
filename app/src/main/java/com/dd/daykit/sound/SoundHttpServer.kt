package com.dd.daykit.sound

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Local HTTP server for Home Assistant integration
 * Provides REST API to list and play custom alarm sounds
 * 
 * SECURITY: Only enable on trusted networks!
 * This server is designed for local network access only.
 */
class SoundHttpServer(
    private val context: Context,
    private val port: Int = 8765,
    private val apiKey: String
) : NanoHTTPD("0.0.0.0", port) {
    
    private val repository = SoundRepository.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "SoundHttpServer"
        const val DEFAULT_PORT = 8765
        
        @Volatile
        private var INSTANCE: SoundHttpServer? = null
        
        /**
         * Start the HTTP server (opt-in only)
         */
        fun start(context: Context, apiKey: String, port: Int = DEFAULT_PORT): SoundHttpServer? {
            return try {
                if (INSTANCE != null) {
                    Log.w(TAG, "Server already running")
                    return INSTANCE
                }
                
                val server = SoundHttpServer(context, port, apiKey)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                INSTANCE = server
                
                val ip = com.dd.daykit.sound.HttpServerManager.getDeviceIpAddress(context)
                Log.i(TAG, "HTTP server started on 0.0.0.0:$port (accessible via $ip:$port)")
                server
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HTTP server", e)
                null
            }
        }
        
        /**
         * Stop the HTTP server
         */
        fun stop() {
            INSTANCE?.let { server ->
                try {
                    server.stop()
                    INSTANCE = null
                    Log.i(TAG, "HTTP server stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping HTTP server", e)
                }
            }
        }
        
        /**
         * Check if server is running
         */
        fun isRunning(): Boolean = INSTANCE?.isAlive == true
        
        /**
         * Get server instance
         */
        fun getInstance(): SoundHttpServer? = INSTANCE
    }
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "Request: $method $uri")
        
        try {
            // Check API key authentication
            val providedKey = session.headers["x-api-key"] 
                ?: session.parms["api_key"]
            
            if (providedKey != apiKey) {
                Log.w(TAG, "Unauthorized access attempt")
                return newFixedLengthResponse(
                    Response.Status.UNAUTHORIZED,
                    MIME_PLAINTEXT,
                    "Unauthorized: Invalid or missing API key"
                )
            }
            
            // Route requests
            return when {
                uri == "/sounds" && method == Method.GET -> handleGetSounds()
                uri.startsWith("/sounds/") && method == Method.GET -> handleGetSound(uri)
                uri == "/ringtone" && method == Method.GET -> handleGetRingtone(session)
                uri == "/play" && method == Method.POST -> handlePlaySound(session)
                uri == "/status" && method == Method.GET -> handleStatus()
                uri == "/" && method == Method.GET -> handleRoot()
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Not Found: $uri"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Internal Server Error: ${e.message}"
            )
        }
    }
    
    /**
     * GET / - Server info
     */
    private fun handleRoot(): Response {
        val json = JSONObject().apply {
            put("name", "AgendaAlarm Sound Server")
            put("version", "1.0")
            put("endpoints", JSONArray().apply {
                put("/sounds - List all custom sounds")
                put("/sounds/{id} - Get sound file")
                put("/play - Play a sound (POST)")
                put("/status - Server status")
            })
        }
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString(2)
        )
    }
    
    /**
     * GET /sounds - List all sounds (custom + system) with detailed file info
     */
    private fun handleGetSounds(): Response {
        val sounds = repository.sounds.value
        val ip = com.dd.daykit.sound.HttpServerManager.getDeviceIpAddress(context) ?: "<unknown>"
        
        val customCount = sounds.count { !it.isSystemSound }
        val systemCount = sounds.count { it.isSystemSound }
        
        val json = JSONObject().apply {
            put("count", sounds.size)
            put("custom_count", customCount)
            put("system_count", systemCount)
            put("sounds", JSONArray().apply {
                sounds.forEach { sound ->
                    put(JSONObject().apply {
                        put("id", sound.id)
                        put("name", sound.name)
                        put("type", if (sound.isSystemSound) "system" else "custom")
                        put("duration_ms", sound.duration)
                        put("duration_formatted", repository.formatDuration(sound.duration))
                        
                        if (sound.isSystemSound) {
                            // System sound
                            put("uri", sound.uri)
                            put("url", "http://$ip:$port/sounds/${sound.id}?api_key=$apiKey")
                        } else {
                            // Custom sound
                            val file = repository.getFileForSound(sound)
                            put("file_path", sound.filePath)
                            put("file_exists", file.exists())
                            put("file_size_bytes", if (file.exists()) file.length() else 0)
                            put("file_size_formatted", if (file.exists()) repository.formatFileSize(file.length()) else "N/A")
                            put("file_extension", file.extension)
                            put("url", "http://$ip:$port/sounds/${sound.id}?api_key=$apiKey")
                        }
                    })
                }
            })
        }
        
        Log.d(TAG, "Listing ${sounds.size} sounds ($customCount custom, $systemCount system)")
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString(2)
        )
    }
    
    /**
     * GET /sounds/{id} - Get sound file (custom or system)
     */
    private fun handleGetSound(uri: String): Response {
        val idStr = uri.substringAfterLast("/")
        val id = idStr.toLongOrNull() ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            MIME_PLAINTEXT,
            "Invalid sound ID"
        )
        
        val sounds = repository.sounds.value
        val sound = sounds.find { it.id == id } ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            "Sound not found"
        )
        
        return try {
            if (sound.isSystemSound) {
                // Serve system ringtone
                val soundUri = android.net.Uri.parse(sound.uri)
                val inputStream = context.contentResolver.openInputStream(soundUri)
                
                if (inputStream == null) {
                    return newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "System ringtone not accessible"
                    )
                }
                
                val mimeType = context.contentResolver.getType(soundUri) ?: "audio/mpeg"
                
                Log.d(TAG, "Serving system sound: ${sound.name}")
                
                newChunkedResponse(
                    Response.Status.OK,
                    mimeType,
                    inputStream
                ).apply {
                    addHeader("Content-Disposition", "inline; filename=\"${sound.name}\"")
                    addHeader("Accept-Ranges", "bytes")
                }
            } else {
                // Serve custom sound file
                val file = repository.getFileForSound(sound)
                if (!file.exists()) {
                    return newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "Sound file not found"
                    )
                }
                
                val inputStream = FileInputStream(file)
                val mimeType = when (file.extension.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/wav"
                    "ogg" -> "audio/ogg"
                    "m4a" -> "audio/mp4"
                    "aac" -> "audio/aac"
                    else -> "audio/mpeg"
                }
                
                Log.d(TAG, "Serving custom sound: ${sound.name}")
                
                newChunkedResponse(
                    Response.Status.OK,
                    mimeType,
                    inputStream
                ).apply {
                    addHeader("Content-Disposition", "inline; filename=\"${sound.name}.${file.extension}\"")
                    addHeader("Accept-Ranges", "bytes")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error serving sound file", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error serving file: ${e.message}"
            )
        }
    }
    
    /**
     * GET /ringtone - Serve a system ringtone
     * Query param: uri (encoded ringtone URI)
     */
    private fun handleGetRingtone(session: IHTTPSession): Response {
        val ringtoneUri = session.parms["uri"] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            MIME_PLAINTEXT,
            "Missing uri parameter"
        )
        
        return try {
            val uri = android.net.Uri.parse(ringtoneUri)
            val inputStream = context.contentResolver.openInputStream(uri)
            
            if (inputStream == null) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Ringtone not found or not accessible"
                )
            }
            
            // Determine MIME type from URI or default to audio/mpeg
            val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
            
            Log.d(TAG, "Serving system ringtone: $ringtoneUri")
            
            newChunkedResponse(
                Response.Status.OK,
                mimeType,
                inputStream
            ).apply {
                addHeader("Content-Disposition", "inline")
                addHeader("Accept-Ranges", "bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error serving ringtone", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error serving ringtone: ${e.message}"
            )
        }
    }
    
    /**
     * POST /play - Play a sound
     * Body: {"sound_id": 123}
     */
    private fun handlePlaySound(session: IHTTPSession): Response {
        return try {
            // Parse request body
            val bodyMap = mutableMapOf<String, String>()
            session.parseBody(bodyMap)
            val body = bodyMap["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                "Missing request body"
            )
            
            val json = JSONObject(body)
            val soundId = json.optLong("sound_id", -1)
            
            if (soundId == -1L) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    MIME_PLAINTEXT,
                    "Missing or invalid sound_id"
                )
            }
            
            // Find sound
            val sounds = repository.sounds.value
            val sound = sounds.find { it.id == soundId }
            
            if (sound == null) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Sound not found"
                )
            }
            
            // Note: Actual playback would need to be triggered via broadcast/intent
            // This is a placeholder response
            val response = JSONObject().apply {
                put("status", "accepted")
                put("message", "Play request received for sound: ${sound.name}")
                put("sound_id", soundId)
                put("note", "Playback must be triggered via app notification/intent")
            }
            
            Log.i(TAG, "Play request for sound: ${sound.name} (ID: $soundId)")
            
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                response.toString(2)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling play request", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * GET /status - Server status with detailed diagnostics
     */
    private fun handleStatus(): Response {
        val sounds = repository.sounds.value
        val ip = com.dd.daykit.sound.HttpServerManager.getDeviceIpAddress(context)
        val wifiSsid = com.dd.daykit.sound.HttpServerManager.getWifiNetworkName(context)
        val isWifiConnected = com.dd.daykit.sound.HttpServerManager.isWifiConnected(context)
        
        // Get storage paths
        val customSoundsDir = File(context.filesDir, "alarm_sounds/custom")
        
        val json = JSONObject().apply {
            put("running", true)
            put("ip", ip ?: "<unknown>")
            put("port", port)
            put("bind_address", "0.0.0.0")
            put("sounds_count", sounds.size)
            put("wifi_connected", isWifiConnected)
            put("wifi_ssid", wifiSsid ?: "<unknown ssid>")
            put("storage_path", customSoundsDir.absolutePath)
            put("storage_exists", customSoundsDir.exists())
            put("storage_readable", customSoundsDir.canRead())
            put("uptime_ms", System.currentTimeMillis())
            
            // List all sounds with file status
            put("sounds", JSONArray().apply {
                sounds.forEach { sound ->
                    val file = repository.getFileForSound(sound)
                    put(JSONObject().apply {
                        put("id", sound.id)
                        put("name", sound.name)
                        put("file_exists", file.exists())
                        put("file_size", if (file.exists()) file.length() else 0)
                        put("file_path", sound.filePath)
                    })
                }
            })
        }
        
        Log.i(TAG, "Status check: $sounds.size sounds, IP: $ip, WiFi: $wifiSsid")
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString(2)
        )
    }
}
