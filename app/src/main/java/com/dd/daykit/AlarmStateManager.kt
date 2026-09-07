package com.dd.daykit

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

object AlarmStateManager {

    private const val PREFS_RINGING = "alarm_service_ringing"
    private const val KEY_RINGING_JSON = "ringing_alarm_json"

    private val _isRinging = MutableStateFlow(false)
    val isRinging = _isRinging.asStateFlow()

    private val _ringingAlarm = MutableStateFlow<AlarmItem?>(null)
    val ringingAlarm = _ringingAlarm.asStateFlow()
    
    private val _isSnoozeActive = MutableStateFlow(false)
    val isSnoozeActive = _isSnoozeActive.asStateFlow()
    
    private val _snoozeEndTime = MutableStateFlow(0L)
    val snoozeEndTime = _snoozeEndTime.asStateFlow()

    /** Snapshot for non-Compose callers (e.g. [GlobalInAppMessageManager] update loop). */
    val isRingingNow: Boolean get() = _isRinging.value

    /** Snapshot of the alarm currently ringing, if any. */
    val ringingAlarmNow: AlarmItem? get() = _ringingAlarm.value

    fun onAlarmStart(alarm: AlarmItem) {
        _isRinging.value = true
        _ringingAlarm.value = alarm
        AgendaAlarmCompletionCoordinator.onAgendaAlarmRinging(alarm)
        // Clear snooze when alarm starts
        _isSnoozeActive.value = false
        _snoozeEndTime.value = 0L
    }

    fun onAlarmStop() {
        _isRinging.value = false
        _ringingAlarm.value = null
    }
    
    fun onSnoozeStart(endTime: Long) {
        _isSnoozeActive.value = true
        _snoozeEndTime.value = endTime
    }
    
    fun onSnoozeCancel() {
        _isSnoozeActive.value = false
        _snoozeEndTime.value = 0L
    }

    fun restoreRingingAlarmIfNeeded(context: Context): AlarmItem? {
        if (_isRinging.value) return null

        val json = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE)
                .getString(KEY_RINGING_JSON, null)
        }.getOrNull() ?: return null

        val alarm = try {
            Json.decodeFromString(AlarmItem.serializer(), json)
        } catch (_: Exception) {
            clearPersistedRingingAlarm(context)
            return null
        }

        _isRinging.value = true
        _ringingAlarm.value = alarm
        return alarm
    }

    fun clearPersistedRingingAlarm(context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_RINGING_JSON)
                .apply()
        }
    }
}
