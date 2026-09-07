package com.dd.daykit

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf

data class SavedTimer(val durationMillis: Long, val name: String = "")

object TimerStateHolder {
    private const val PREFS_NAME = "TimerHistoryPrefs"
    private const val KEY_HISTORY = "timer_history_v2" // Changed key to avoid conflict or detect version

    private val _savedTimers = mutableStateListOf<SavedTimer>()
    val savedTimers: List<SavedTimer> get() = _savedTimers
    
    // Backwards compatibility for reading old list of longs if needed, though v2 key handles migration implicitly by starting fresh or we can try to read old key.
    private const val KEY_HISTORY_OLD = "timer_history"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _savedTimers.clear()
        
        val historyString = prefs.getString(KEY_HISTORY, null)
        
        if (historyString != null) {
            // New format: duration:name;duration:name
            if (historyString.isNotEmpty()) {
                historyString.split(";").forEach { item ->
                    val parts = item.split(":")
                    if (parts.isNotEmpty()) {
                        val duration = parts[0].toLongOrNull()
                        if (duration != null) {
                            val name = if (parts.size > 1) parts[1] else ""
                            _savedTimers.add(SavedTimer(duration, name))
                        }
                    }
                }
            }
        } else {
            // Try loading old format and migrate
            val oldHistory = prefs.getString(KEY_HISTORY_OLD, "") ?: ""
            if (oldHistory.isNotEmpty()) {
                oldHistory.split(",").forEach { 
                    it.toLongOrNull()?.let { millis -> _savedTimers.add(SavedTimer(millis)) }
                }
                // Save immediately in new format
                saveToPrefs(context)
            }
        }
    }

    fun saveDuration(context: Context, durationMillis: Long) {
        // Avoid exact duplicate at the end
        if (_savedTimers.isEmpty() || _savedTimers.last().durationMillis != durationMillis) {
            _savedTimers.add(SavedTimer(durationMillis))
            saveToPrefs(context)
        }
    }
    
    fun updateTimerName(context: Context, index: Int, newName: String) {
        if (index in 0 until _savedTimers.size) {
            _savedTimers[index] = _savedTimers[index].copy(name = newName)
            saveToPrefs(context)
        }
    }

    fun deleteTimer(context: Context, index: Int) {
        if (index in 0 until _savedTimers.size) {
            _savedTimers.removeAt(index)
            saveToPrefs(context)
        }
    }

    fun clearHistory(context: Context) {
        _savedTimers.clear()
        saveToPrefs(context)
    }

    private fun saveToPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Format: duration:name;... (sanitize name to remove ; and :)
        val historyString = _savedTimers.joinToString(";") { 
            "${it.durationMillis}:${it.name.replace(";", "").replace(":", "")}" 
        }
        prefs.edit().putString(KEY_HISTORY, historyString).apply()
    }
}
