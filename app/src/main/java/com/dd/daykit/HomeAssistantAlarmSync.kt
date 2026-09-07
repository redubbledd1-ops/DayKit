package com.dd.daykit

import android.content.Context
import android.util.Log
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.data.HaUpdateResult
import com.dd.daykit.network.HomeAssistantClient
import kotlinx.coroutines.flow.firstOrNull

/**
 * Helper object voor het synchroniseren van alarmtijden met Home Assistant
 */
object HomeAssistantAlarmSync {
    
    private const val TAG = "HAAlarmSync"
    
    /**
     * Update de volgende alarmtijd in Home Assistant (indien backup-wekker is ingeschakeld)
     * @param context Android context
     * @param selectedCalendarIds Set van actieve calendar IDs
     */
    suspend fun syncNextAlarmToHomeAssistant(context: Context, selectedCalendarIds: Set<String>) {
        try {
            // Check of backup-wekker is ingeschakeld
            val settingsStorage = HomeAssistantSettingsStorage(context)
            val settings = settingsStorage.settingsFlow.firstOrNull()
            
            if (settings?.backupAlarmEnabled != true) {
                Log.d(TAG, "Backup-wekker niet ingeschakeld, skip sync")
                return
            }
            
            // Bereken volgende alarmtijd
            val nextAlarmIso = NextAlarmCalculator.getNextAlarmIso(context, selectedCalendarIds)
            
            Log.d(TAG, "Sync volgende alarm naar HA: $nextAlarmIso")
            
            // Update in Home Assistant
            val client = HomeAssistantClient // Singleton
            val repository = HomeAssistantRepository(client, settingsStorage)
            
            val result = repository.updateNextAlarm(nextAlarmIso)
            
            when (result) {
                is HaUpdateResult.Success -> {
                    Log.i(TAG, "Volgende alarm succesvol gesynchroniseerd naar HA: $nextAlarmIso")
                }
                is HaUpdateResult.Error -> {
                    Log.e(TAG, "Fout bij synchroniseren naar HA: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Onverwachte fout bij HA sync", e)
        }
    }
}
