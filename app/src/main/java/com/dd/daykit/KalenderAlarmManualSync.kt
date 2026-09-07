package com.dd.daykit

import android.content.Context
import android.content.Intent
import android.util.Log
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handmatige agenda-/alarmsynchronisatie zoals de sync-knop op KalenderAlarm-instellingen:
 * alarm opnieuw plannen, HA speaker + alarmtijd (≤24u), backup-alarm ISO indien ingeschakeld,
 * [SyncStatusManager] bijwerken en broadcast naar de UI.
 */
object KalenderAlarmManualSync {

    private const val TAG = "KalenderAlarmManualSync"

    suspend fun run(context: Context): AlarmItem? {
        val appContext = context.applicationContext
        return try {
            SyncStatusManager.init(appContext)
            SyncStatusManager.onSyncStarted()

            // Self-healing: zie CalendarSyncWorker.doWork() - vangt hetzelfde geval op via de
            // handmatige "Sync nu"-knop, zodat de gebruiker niet per se op de eerstvolgende
            // periodieke sync hoeft te wachten.
            try {
                BackupManager.reconcilePendingCalendarTriggerRemap(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "reconcilePendingCalendarTriggerRemap failed", e)
            }

            val nextAlarm = AlarmScheduler.scheduleNextAlarm(appContext, "kalender_manual_sync")

            AlarmScheduler.restorePendingSnoozeIfNeeded(appContext)

            try {
                val haSettingsStorage = HomeAssistantSettingsStorage(appContext)
                val haRepository = HomeAssistantRepository(HomeAssistantClient, haSettingsStorage)
                val haSettings = haSettingsStorage.settingsFlow.firstOrNull()
                val speakerEntityId = haSettings?.alarmSpeaker?.entityId
                if (!speakerEntityId.isNullOrBlank()) {
                    haRepository.setAlarmSpeakerEntity(speakerEntityId)
                    Log.i(TAG, "Speaker entity synced to HA: $speakerEntityId")
                }
                nextAlarm?.let { alarm ->
                    val timeUntilAlarm = alarm.epochMillis - System.currentTimeMillis()
                    val twentyFourHoursMs = 24 * 60 * 60 * 1000L
                    if (timeUntilAlarm > 0 && timeUntilAlarm <= twentyFourHoursMs) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val alarmTimeHHMM = sdf.format(Date(alarm.epochMillis))
                        haRepository.setAlarmTime(alarmTimeHHMM)
                        Log.i(TAG, "Alarm time synced to HA: $alarmTimeHHMM")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync speaker/time to HA", e)
            }

            HomeAssistantAlarmSync.syncNextAlarmToHomeAssistant(
                appContext,
                SettingsManager.getTriggerCalendarIds(appContext)
            )

            SyncStatusManager.onSyncCompleted(appContext, nextAlarm)
            // Explicit package zodat dynamische receivers in de app de update altijd krijgen (zoals MainActivity).
            appContext.sendBroadcast(
                Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED).apply {
                    setPackage(appContext.packageName)
                }
            )
            nextAlarm
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            SyncStatusManager.onSyncFailed(appContext, e.message ?: "Sync failed")
            null
        }
    }
}
