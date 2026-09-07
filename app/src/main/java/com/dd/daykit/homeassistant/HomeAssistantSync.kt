package com.dd.daykit.homeassistant

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Home Assistant Sync
 * Stuurt volgend alarm info naar Home Assistant zodat HA kan anticiperen
 *
 * Gebruik: Wanneer gebruiker een alarm instelt, stuurt app de details naar HA
 * HA kan dan automations maken zoals:
 * - 30 min voor alarm: lichten dimmen
 * - 15 min voor alarm: koffie zetten
 * - Bij alarm tijd: lichten aan, muziek, etc.
 */
object HomeAssistantSync {
    private const val TAG = "HASync"
    private const val PREFS_NAME = "home_assistant_sync"

    // Settings keys
    private const val KEY_ENABLED = "ha_sync_enabled"
    private const val KEY_BASE_URL = "ha_base_url"
    private const val KEY_ACCESS_TOKEN = "ha_access_token"
    private const val KEY_WEBHOOK_ID = "ha_webhook_id"

    // Default webhook ID
    // BEWUST niet meegenomen in de naamswijziging naar DayKit. Dit hoort bij de oude,
    // handmatige HA-opzet (webhook + automations in automations.yaml), niet bij de DayKit-
    // integratie. Hernoemen zou de webhook-URL breken van iedereen die die handmatige opzet
    // nog draait, zonder dat er ook maar iets zichtbaars aan verbetert.
    private const val DEFAULT_WEBHOOK_ID = "agendaalarm_next_alarm"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Get SharedPreferences
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if sync is enabled
     */
    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false)
    }

    /**
     * Enable or disable sync
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.i(TAG, "HA Sync ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Get Home Assistant base URL
     * Example: http://192.168.1.50:8123
     */
    fun getBaseUrl(context: Context): String {
        return getPrefs(context).getString(KEY_BASE_URL, "") ?: ""
    }

    /**
     * Set Home Assistant base URL
     */
    fun setBaseUrl(context: Context, url: String) {
        val cleanUrl = url.trimEnd('/')
        getPrefs(context).edit().putString(KEY_BASE_URL, cleanUrl).apply()
        Log.i(TAG, "HA base URL set: $cleanUrl")
    }

    /**
     * Get Home Assistant long-lived access token
     */
    fun getAccessToken(context: Context): String {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, "") ?: ""
    }

    /**
     * Set Home Assistant long-lived access token
     */
    fun setAccessToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_ACCESS_TOKEN, token).apply()
        Log.i(TAG, "HA access token set")
    }

    /**
     * Get webhook ID
     */
    fun getWebhookId(context: Context): String {
        val prefs = getPrefs(context)
        var webhookId = prefs.getString(KEY_WEBHOOK_ID, "") ?: ""

        if (webhookId.isEmpty()) {
            webhookId = DEFAULT_WEBHOOK_ID
            prefs.edit().putString(KEY_WEBHOOK_ID, webhookId).apply()
        }

        return webhookId
    }

    /**
     * Build webhook URL
     */
    fun getWebhookUrl(context: Context): String {
        val baseUrl = getBaseUrl(context)
        val webhookId = getWebhookId(context)

        if (baseUrl.isEmpty()) return ""

        return "$baseUrl/api/webhook/$webhookId"
    }

    /**
     * Sync next alarm to Home Assistant
     * Roep deze aan wanneer gebruiker een alarm instelt of wijzigt
     */
    suspend fun syncNextAlarm(
        context: Context,
        alarmTimeMillis: Long,
        alarmName: String,
        soundName: String? = null,
        soundUrl: String? = null,
        calendarName: String? = null,
        eventTitle: String? = null,
        enabled: Boolean = true,
        alarmSpeakerEntity: String? = null,
        // Backup alarm settings for HA fallback when phone is unavailable
        backupVolume: Int? = null,
        backupAlarmDuration: Int? = null,
        backupSoundUrl: String? = null,
        batteryUsagePerHour: Int? = null,
        speakerMode: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) {
            Log.d(TAG, "HA Sync disabled, skipping")
            return@withContext Result.success(Unit)
        }

        val webhookUrl = getWebhookUrl(context)
        if (webhookUrl.isEmpty()) {
            Log.w(TAG, "HA webhook URL not configured")
            return@withContext Result.failure(Exception("Home Assistant URL not configured"))
        }

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val alarmTimeStr = dateFormat.format(Date(alarmTimeMillis))

            val payload = NextAlarmPayload(
                alarm_time = alarmTimeStr,
                eerstvolgende_alarmtijd = alarmTimeStr, // Added for Dutch HA automation compatibility
                alarm_time_millis = alarmTimeMillis,
                alarm_name = alarmName,
                sound_name = soundName,
                sound_url = soundUrl,
                calendar_name = calendarName,
                event_title = eventTitle,
                enabled = enabled,
                timestamp = System.currentTimeMillis(),
                alarm_speaker_entity = alarmSpeakerEntity,
                // Backup alarm settings for HA fallback
                backup_volume = backupVolume,
                backup_alarm_duration = backupAlarmDuration,
                backup_sound_url = backupSoundUrl,
                battery_usage_per_hour = batteryUsagePerHour,
                speaker_mode = speakerMode
            )

            Log.d(TAG, "Syncing next alarm: $alarmName at $alarmTimeStr")

            sendWebhook(webhookUrl, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing next alarm", e)
            Result.failure(e)
        }
    }

    /**
     * Clear next alarm (when alarm is disabled or deleted)
     */
    suspend fun clearNextAlarm(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) {
            return@withContext Result.success(Unit)
        }

        val webhookUrl = getWebhookUrl(context)
        if (webhookUrl.isEmpty()) {
            return@withContext Result.failure(Exception("Home Assistant URL not configured"))
        }

        val payload = ClearAlarmPayload(
            alarm_time = null,
            alarm_name = null,
            enabled = false,
            timestamp = System.currentTimeMillis()
        )

        Log.d(TAG, "Clearing next alarm")
        sendWebhook(webhookUrl, payload)
    }

    /**
     * Test connection to Home Assistant
     */
    suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val webhookUrl = getWebhookUrl(context)
        if (webhookUrl.isEmpty()) {
            return@withContext Result.failure(Exception("Home Assistant URL not configured"))
        }

        val payload = TestPayload(
            test = true,
            message = "Test from AgendaAlarm app",
            timestamp = System.currentTimeMillis()
        )

        val result = sendWebhook(webhookUrl, payload)
        return@withContext if (result.isSuccess) {
            Result.success("Connection successful!")
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    /**
     * Generic webhook sender
     */
    private suspend inline fun <reified T> sendWebhook(url: String, payload: T): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending webhook to: $url")

            val jsonPayload = json.encodeToString(payload)
            Log.d(TAG, "Payload: $jsonPayload")

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Webhook sent successfully: ${response.code}")
                    Result.success(Unit)
                } else {
                    val error = "Webhook failed: ${response.code} ${response.message}"
                    Log.e(TAG, error)
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending webhook", e)
            Result.failure(e)
        }
    }

    // Payload data classes

    @Serializable
    data class NextAlarmPayload(
        val alarm_time: String,              // ISO format: 2024-12-10T07:00:00
        val eerstvolgende_alarmtijd: String, // Compatibility with HA automation
        val alarm_time_millis: Long,         // Unix timestamp in milliseconds
        val alarm_name: String,              // "Morning Alarm"
        val sound_name: String?,             // "Gentle Wake"
        val sound_url: String?,              // "http://phone-ip:8765/sounds/1"
        val calendar_name: String?,          // "Work Calendar"
        val event_title: String?,            // "Team Meeting"
        val enabled: Boolean,                // true/false
        val timestamp: Long,                  // When this was sent
        val alarm_speaker_entity: String?,
        // Backup alarm settings for HA fallback when phone is unavailable
        val backup_volume: Int?,             // Volume 0-100 for backup speaker
        val backup_alarm_duration: Int?,     // Duration in seconds for backup alarm
        val backup_sound_url: String?,       // URL of backup alarm sound
        val battery_usage_per_hour: Int?,    // Battery drain % per hour (for HA to calculate if phone will survive)
        val speaker_mode: String?            // "Off", "Standard", "Backup", "Both"
    )

    @Serializable
    private data class ClearAlarmPayload(
        val alarm_time: String?,
        val alarm_name: String?,
        val enabled: Boolean,
        val timestamp: Long
    )

    @Serializable
    private data class TestPayload(
        val test: Boolean,
        val message: String,
        val timestamp: Long
    )

    /**
     * Sync all sounds to Home Assistant
     * Sends metadata of all available sounds so HA knows what's available
     */
    suspend fun syncSounds(
        context: Context,
        sounds: List<SoundInfo>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) {
            Log.d(TAG, "HA Sync disabled, skipping sound sync")
            return@withContext Result.success(Unit)
        }

        val baseUrl = getBaseUrl(context)
        if (baseUrl.isEmpty()) {
            Log.w(TAG, "HA base URL not configured")
            return@withContext Result.failure(Exception("Home Assistant URL not configured"))
        }

        // Build webhook URL for sound sync
        val webhookUrl = "$baseUrl/api/webhook/agendaalarm_sound_sync"

        try {
            Log.d(TAG, "Syncing ${sounds.size} sounds to Home Assistant")

            val payload = SoundSyncPayload(
                sounds = sounds,
                total_count = sounds.size,
                timestamp = System.currentTimeMillis()
            )

            sendWebhook(webhookUrl, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing sounds", e)
            Result.failure(e)
        }
    }

    /**
     * Sound info for sync
     */
    @Serializable
    data class SoundInfo(
        val id: Long,
        val name: String,
        val duration: Long?,
        val streaming_url: String
    )

    @Serializable
    private data class SoundSyncPayload(
        val sounds: List<SoundInfo>,
        val total_count: Int,
        val timestamp: Long
    )
}
