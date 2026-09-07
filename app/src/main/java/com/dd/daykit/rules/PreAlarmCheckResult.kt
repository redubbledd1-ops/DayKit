package com.dd.daykit.rules

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Result of a Phase 1 pre-alarm check.
 * Stores whether the alarm should be cancelled and the reason.
 */
@Serializable
data class PreAlarmCheckResult(
    val alarmId: Long,
    val triggerId: String?,
    val shouldCancelAlarm: Boolean,
    val reason: PreAlarmCancelReason,
    val checkTimestamp: Long = System.currentTimeMillis(),
    val outOfBedConfidencePercent: Int = 0, // 0-100, how confident we are user is out of bed
    val outOfBedDurationMs: Long = 0, // How long user has been out of bed
    val sensorValue: String? = null, // Actual sensor value at check time
    val expectedInBedValue: String? = null // What value means "in bed"
)

@Serializable
enum class PreAlarmCancelReason {
    NOT_CHECKED,           // Pre-alarm check was not performed
    CHECK_DISABLED,        // Out-of-bed check is disabled in settings
    USER_IN_BED,           // User is still in bed - alarm should fire
    USER_OUT_OF_BED,       // User is out of bed with high confidence - cancel alarm
    SENSOR_ERROR,          // Could not read sensor - fail-safe: allow alarm
    SENSOR_UNRELIABLE,     // Sensor returned unknown/unavailable - fail-safe: allow alarm
    HA_CONNECTION_ERROR,   // Home Assistant unreachable - fail-safe: allow alarm
    INSUFFICIENT_DURATION, // User out of bed but not long enough - allow alarm
    USER_NOT_HOME,         // User not at home - skip bed check, allow alarm
    PRESENCE_ERROR         // Error checking presence - fail-safe: allow alarm
}

/**
 * Storage for pre-alarm check results.
 * Uses SharedPreferences for persistence across receiver/service boundaries.
 */
class PreAlarmCheckStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "PreAlarmCheckStorage"
        private const val PREFS_NAME = "pre_alarm_check_prefs"
        private const val KEY_RESULT_PREFIX = "result_"
        private const val KEY_LAST_CHECK = "last_check_timestamp"
        
        // Result is valid for 10 minutes max (covers pre-alarm window + buffer)
        private const val RESULT_MAX_AGE_MS = 10 * 60 * 1000L
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Store a pre-alarm check result.
     */
    fun storeResult(result: PreAlarmCheckResult) {
        try {
            val key = KEY_RESULT_PREFIX + result.alarmId
            val jsonString = json.encodeToString(result)
            prefs.edit()
                .putString(key, jsonString)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Stored pre-alarm result for alarm ${result.alarmId}: cancel=${result.shouldCancelAlarm}, reason=${result.reason}")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing pre-alarm result", e)
        }
    }
    
    /**
     * Get a pre-alarm check result for a specific alarm.
     * Returns null if no valid result exists.
     * NOTE: Does NOT auto-clear old results - clearing only happens after alarm fires.
     */
    fun getResult(alarmId: Long): PreAlarmCheckResult? {
        return try {
            val key = KEY_RESULT_PREFIX + alarmId
            val jsonString = prefs.getString(key, null) ?: return null
            val result = json.decodeFromString<PreAlarmCheckResult>(jsonString)
            
            // NO auto-clearing here - result persists until explicitly cleared after T=0
            Log.d(TAG, "Retrieved pre-alarm result for $alarmId: cancel=${result.shouldCancelAlarm}, reason=${result.reason}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error reading pre-alarm result", e)
            null
        }
    }
    
    /**
     * Clear a pre-alarm check result.
     */
    fun clearResult(alarmId: Long) {
        try {
            val key = KEY_RESULT_PREFIX + alarmId
            prefs.edit().remove(key).apply()
            Log.d(TAG, "Cleared pre-alarm result for $alarmId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing pre-alarm result", e)
        }
    }
    
    /**
     * Clear all stored results (cleanup).
     */
    fun clearAll() {
        try {
            prefs.edit().clear().apply()
            Log.d(TAG, "Cleared all pre-alarm results")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all pre-alarm results", e)
        }
    }
    
    /**
     * Override a pre-alarm cancel decision - force the alarm to fire anyway.
     * This is used when user taps "Alarm toch af laten gaan" button.
     */
    fun forceAlarmToFire(alarmId: Long) {
        try {
            val existingResult = getResult(alarmId)
            if (existingResult != null && existingResult.shouldCancelAlarm) {
                // Create a new result that forces alarm to fire
                val overriddenResult = existingResult.copy(
                    shouldCancelAlarm = false,
                    reason = PreAlarmCancelReason.USER_IN_BED // Treat as if user is in bed
                )
                storeResult(overriddenResult)
                Log.d(TAG, "Overridden pre-alarm cancel for alarm $alarmId - alarm will now fire")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error overriding pre-alarm result", e)
        }
    }
    
    /**
     * Get the active pre-alarm status for UI display.
     * Returns status if pre-alarm check has run and alarm hasn't fired yet.
     * 
     * IMPORTANT: Does NOT clear result - that only happens in AlarmReceiver after T=0.
     * Banner should show anytime between T-5 and T=0 (or even slightly after if alarm delayed).
     * 
     * FIX: Added validation that the check was performed within the valid pre-alarm window
     * to prevent stale data from showing on devices with different lifecycle timing (e.g., Samsung).
     */
    fun getActivePreAlarmStatus(currentAlarmId: Long?, alarmTimeMs: Long?): PreAlarmUiStatus? {
        if (currentAlarmId == null || alarmTimeMs == null) {
            Log.d(TAG, "getActivePreAlarmStatus: null alarm id or time")
            return null
        }
        
        val result = getResult(currentAlarmId)
        if (result == null) {
            Log.d(TAG, "getActivePreAlarmStatus: no result found for alarm $currentAlarmId")
            return null
        }
        
        val now = System.currentTimeMillis()
        
        // Allow showing status up to 1 minute AFTER alarm time (in case of delays)
        // Result will be cleared by AlarmReceiver after it processes the alarm
        val bufferMs = 60 * 1000L
        if (now > alarmTimeMs + bufferMs) {
            Log.d(TAG, "getActivePreAlarmStatus: alarm time + buffer passed (now=$now, alarmTime=$alarmTimeMs)")
            // Don't clear here - let AlarmReceiver handle cleanup
            return null
        }
        
        // FIX: Validate that the check was performed within a reasonable window.
        // Pre-alarm check runs at T-5 minutes, so the checkTimestamp should be:
        // - Not more than 6 minutes before alarm time (T-6 to allow for scheduling delays)
        // - Not in the future
        // This prevents stale data from previous alarms from showing.
        val preAlarmWindowMs = 6 * 60 * 1000L // 6 minutes before alarm
        val earliestValidCheck = alarmTimeMs - preAlarmWindowMs
        
        if (result.checkTimestamp < earliestValidCheck) {
            Log.d(TAG, "getActivePreAlarmStatus: check too old (checkTime=${result.checkTimestamp}, earliest=$earliestValidCheck)")
            // Clear stale result
            clearResult(currentAlarmId)
            return null
        }
        
        if (result.checkTimestamp > now + 60000) { // Allow 1 min clock drift
            Log.d(TAG, "getActivePreAlarmStatus: check timestamp in future, ignoring")
            return null
        }
        
        Log.d(TAG, "getActivePreAlarmStatus: returning status for alarm $currentAlarmId (willFire=${!result.shouldCancelAlarm})")
        return PreAlarmUiStatus(
            alarmId = currentAlarmId,
            willAlarmFire = !result.shouldCancelAlarm,
            reason = result.reason,
            confidence = result.outOfBedConfidencePercent,
            duration = result.outOfBedDurationMs,
            validUntil = alarmTimeMs,
            sensorValue = result.sensorValue,
            checkTimestamp = result.checkTimestamp
        )
    }
    
    /**
     * Check if there's any pre-alarm status available (quick check without full parsing).
     */
    fun hasActiveStatus(alarmId: Long): Boolean {
        val key = KEY_RESULT_PREFIX + alarmId
        return prefs.contains(key)
    }
}

/**
 * UI-friendly status for pre-alarm check display.
 */
data class PreAlarmUiStatus(
    val alarmId: Long,
    val willAlarmFire: Boolean,
    val reason: PreAlarmCancelReason,
    val confidence: Int,
    val duration: Long,
    val validUntil: Long,
    val sensorValue: String?,
    val checkTimestamp: Long
)
