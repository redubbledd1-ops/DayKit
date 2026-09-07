package com.dd.daykit

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class StopwatchState {
    IDLE, RUNNING, PAUSED
}

data class TimerSession(val finalTime: Long, val laps: List<Long>, val name: String = "")

object StopwatchStateHolder {
    private const val PREFS_NAME = "StopwatchPrefs"
    private const val KEY_TIME_MILLIS = "time_millis"
    private const val KEY_STATE = "state"
    private const val KEY_LAPS = "laps"
    private const val KEY_SAVED_TIMES = "saved_times"
    private const val KEY_START_TIME_SYSTEM = "start_time_system"
    private const val KEY_POPUP_ENABLED = "popup_enabled"
    const val DEFAULT_POPUP_ENABLED = true

    var timeMillis = mutableStateOf(0L)
    var stopwatchState = mutableStateOf(StopwatchState.IDLE)
    val laps = mutableStateListOf<Long>()
    val savedTimes = mutableStateListOf<TimerSession>()
    var popupEnabled = mutableStateOf(DEFAULT_POPUP_ENABLED)
    
    // Absolute start time - source of truth for running stopwatch
    private var startTimeSystem: Long = 0L
    private var baseTimeMillis: Long = 0L // Time accumulated before current run

    private const val MAX_SESSIONS = 100
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var context: Context? = null

    private var isInitialized = false

    /**
     * Get elapsed time calculated from absolute start time.
     * This is the correct way to get elapsed time - always accurate even after app restart.
     */
    fun getElapsedTimeMs(): Long {
        return when (stopwatchState.value) {
            StopwatchState.RUNNING -> baseTimeMillis + (System.currentTimeMillis() - startTimeSystem)
            StopwatchState.PAUSED -> timeMillis.value
            else -> 0L
        }
    }
    
    fun init(ctx: Context) {
        if (isInitialized && context != null) return // Already initialized
        context = ctx.applicationContext
        isInitialized = true
        loadState()
    }

    private fun loadState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        timeMillis.value = prefs.getLong(KEY_TIME_MILLIS, 0L)
        popupEnabled.value = prefs.getBoolean(KEY_POPUP_ENABLED, DEFAULT_POPUP_ENABLED)
        val stateStr = prefs.getString(KEY_STATE, StopwatchState.IDLE.name)
        stopwatchState.value = try {
            StopwatchState.valueOf(stateStr ?: StopwatchState.IDLE.name)
        } catch (e: Exception) {
            StopwatchState.IDLE
        }

        val lapsStr = prefs.getString(KEY_LAPS, "")
        laps.clear()
        if (!lapsStr.isNullOrEmpty()) {
            lapsStr.split(",").forEach { 
                it.toLongOrNull()?.let { millis -> laps.add(millis) }
            }
        }

        val savedTimesStr = prefs.getString(KEY_SAVED_TIMES, "")
        savedTimes.clear()
        if (!savedTimesStr.isNullOrEmpty()) {
            savedTimesStr.split(";").forEach { sessionStr ->
                if (sessionStr.isNotEmpty()) {
                    val parts = sessionStr.split(":")
                    if (parts.size >= 2) {
                        val finalTime = parts[0].toLongOrNull() ?: 0L
                        val lapsList = parts[1].split(",").mapNotNull { it.toLongOrNull() }
                        val name = if (parts.size > 2) parts[2] else ""
                        savedTimes.add(TimerSession(finalTime, lapsList, name))
                    } else if (parts.size == 1) {
                         val finalTime = parts[0].toLongOrNull() ?: 0L
                         savedTimes.add(TimerSession(finalTime, listOf(finalTime)))
                    }
                }
            }
        }

        if (stopwatchState.value == StopwatchState.RUNNING) {
            val savedStartTime = prefs.getLong(KEY_START_TIME_SYSTEM, 0L)
            if (savedStartTime > 0) {
                // Store the base time (time accumulated before this run)
                baseTimeMillis = timeMillis.value
                startTimeSystem = savedStartTime
                // Calculate current elapsed time
                val now = System.currentTimeMillis()
                val elapsed = now - savedStartTime
                timeMillis.value = baseTimeMillis + elapsed
            }
            startTicker()
            syncStopwatchPopup("restore_running")
        } else if (stopwatchState.value == StopwatchState.PAUSED) {
            startTimeSystem = 0L
            baseTimeMillis = timeMillis.value
            syncStopwatchPopup("restore_paused")
        } else {
            // Not running - reset tracking variables
            startTimeSystem = 0L
            baseTimeMillis = timeMillis.value
        }
    }
    
    private fun saveState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putLong(KEY_TIME_MILLIS, timeMillis.value)
        editor.putBoolean(KEY_POPUP_ENABLED, popupEnabled.value)
        editor.putString(KEY_STATE, stopwatchState.value.name)
        editor.putString(KEY_LAPS, laps.joinToString(","))
        
        val savedTimesStr = savedTimes.joinToString(";") { session ->
             "${session.finalTime}:${session.laps.joinToString(",")}:${session.name}"
        }
        editor.putString(KEY_SAVED_TIMES, savedTimesStr)
        
        if (stopwatchState.value == StopwatchState.RUNNING) {
             editor.putLong(KEY_START_TIME_SYSTEM, System.currentTimeMillis())
        } else {
             editor.remove(KEY_START_TIME_SYSTEM)
        }

        editor.apply()
    }

    fun start() {
        if (stopwatchState.value != StopwatchState.IDLE) return
        // Set absolute start time
        baseTimeMillis = 0L
        startTimeSystem = System.currentTimeMillis()
        stopwatchState.value = StopwatchState.RUNNING
        startTicker()
        saveState()
        syncStopwatchPopup("start")
    }

    private fun startTicker() {
        job?.cancel()
        job = scope.launch {
            while (stopwatchState.value == StopwatchState.RUNNING) {
                val elapsed = baseTimeMillis + (System.currentTimeMillis() - startTimeSystem)
                timeMillis.value = elapsed
                delay(100L)
            }
        }
    }

    fun pause() {
        if (stopwatchState.value != StopwatchState.RUNNING) return
        stopwatchState.value = StopwatchState.PAUSED
        job?.cancel()
        job = null
        saveState()
        syncStopwatchPopup("pause")
    }

    fun resume() {
        if (stopwatchState.value != StopwatchState.PAUSED) return
        // Set new start time, keeping accumulated time as base
        baseTimeMillis = timeMillis.value
        startTimeSystem = System.currentTimeMillis()
        stopwatchState.value = StopwatchState.RUNNING
        startTicker()
        saveState()
        syncStopwatchPopup("resume")
    }

    fun saveAndReset() {
        saveTime() 
        performReset()
    }

    fun stopWithoutSave() {
        performReset()
    }

    private fun performReset() {
        job?.cancel()
        job = null
        stopwatchState.value = StopwatchState.IDLE
        timeMillis.value = 0L
        laps.clear()
        saveState()
        syncStopwatchPopup("reset")
    }

    fun addLap() {
        if (stopwatchState.value == StopwatchState.RUNNING) {
            laps.add(timeMillis.value)
            saveState()
        }
    }

    private fun saveTime() {
        if (timeMillis.value > 0) {
            val lapsForSession = if (laps.isEmpty()) listOf(timeMillis.value) else laps.toList()
            val session = TimerSession(finalTime = timeMillis.value, laps = lapsForSession)

            if (savedTimes.size >= MAX_SESSIONS) {
                savedTimes.removeAt(0) 
            }
            savedTimes.add(session)
            saveState()
        }
    }
    
    fun updateSessionName(index: Int, newName: String) {
        if (index in 0 until savedTimes.size) {
            // Replace item with copy to trigger state update in Compose
            savedTimes[index] = savedTimes[index].copy(name = newName)
            saveState()
        }
    }

    fun deleteSession(index: Int) {
        if (index in 0 until savedTimes.size) {
            savedTimes.removeAt(index)
            saveState()
        }
    }

    fun clearSavedTimes() {
        savedTimes.clear()
        saveState()
    }
    
    fun setPopupEnabled(enabled: Boolean) {
        popupEnabled.value = enabled
        saveState()
        if (enabled) {
            syncStopwatchPopup("settings_toggle_on")
        } else {
            context?.let { StopwatchPopupForegroundService.stopPopup(it, "settings_toggle_off") }
        }
    }

    private fun syncStopwatchPopup(reason: String) {
        val ctx = context ?: return
        runCatching {
            if (popupEnabled.value) {
                StopwatchPopupForegroundService.syncFromStopwatchState(ctx, reason)
            } else {
                StopwatchPopupForegroundService.stopPopup(ctx, "${reason}_disabled")
            }
        }.onFailure { error ->
            android.util.Log.e("StopwatchStateHolder", "POPUP_SYNC_FAILED reason=$reason", error)
        }
    }
}
