package com.dd.daykit.rules

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.playedAlarmsDataStore: DataStore<Preferences> by preferencesDataStore(name = "played_alarms")

/**
 * Storage voor het bijhouden welke alarms al zijn afgespeeld (voor "één keer afspelen" regel)
 */
class PlayedAlarmsStorage(private val context: Context) {
    
    private val PLAYED_ALARMS_KEY = stringSetPreferencesKey("played_alarm_ids")
    
    /**
     * Markeer een alarm als afgespeeld
     */
    suspend fun markAsPlayed(triggerId: String) {
        context.playedAlarmsDataStore.edit { preferences ->
            val current = preferences[PLAYED_ALARMS_KEY] ?: emptySet()
            preferences[PLAYED_ALARMS_KEY] = current + triggerId
        }
    }
    
    /**
     * Check of een alarm al is afgespeeld
     */
    suspend fun hasBeenPlayed(triggerId: String): Boolean {
        val preferences = context.playedAlarmsDataStore.data.first()
        val playedAlarms = preferences[PLAYED_ALARMS_KEY] ?: emptySet()
        return triggerId in playedAlarms
    }
    
    /**
     * Reset de status voor een specifiek alarm (zodat het weer kan afspelen)
     */
    suspend fun resetAlarm(triggerId: String) {
        context.playedAlarmsDataStore.edit { preferences ->
            val current = preferences[PLAYED_ALARMS_KEY] ?: emptySet()
            preferences[PLAYED_ALARMS_KEY] = current - triggerId
        }
    }
    
    /**
     * Reset alle alarms (bijv. voor testing of manual reset)
     */
    suspend fun resetAll() {
        context.playedAlarmsDataStore.edit { preferences ->
            preferences.remove(PLAYED_ALARMS_KEY)
        }
    }
}
