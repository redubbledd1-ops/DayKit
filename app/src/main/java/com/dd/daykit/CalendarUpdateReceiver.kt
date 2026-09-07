package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalendarUpdateReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_UPDATED = "com.dd.daykit.ACTION_ALARM_UPDATED"
        private const val TAG = "CalendarUpdateReceiver"

        @Volatile
        private var lastProviderChangedHandledAt: Long = 0L
        private const val PROVIDER_CHANGED_DEBOUNCE_MS = 3_000L
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            Log.w(TAG, "Context is null, cannot process calendar update")
            return
        }
        
        val action = intent?.action
        AgendaMoveDebugLog.calendarUpdateReceiver(action, intent?.data?.toString())
        Log.d(TAG, "Received broadcast with action: $action")
        
        // Check if this is a calendar change or manual sync trigger
        val isCalendarChange = action == Intent.ACTION_PROVIDER_CHANGED && 
                               intent.data.toString().contains(CalendarContract.CONTENT_URI.toString())
        val isManualSync = action == ACTION_ALARM_UPDATED
        
        if (isCalendarChange || isManualSync) {
            Log.i(
                TAG,
                "AgendaAlarmSync: calendar update — manual=$isManualSync calendarBroadcast=$isCalendarChange data=${intent?.data}"
            )

            if (isCalendarChange) {
                val now = System.currentTimeMillis()
                if (now - lastProviderChangedHandledAt < PROVIDER_CHANGED_DEBOUNCE_MS) {
                    Log.d(TAG, "Ignoring provider-changed (debounce): ${now - lastProviderChangedHandledAt}ms since last")
                    return
                }
                lastProviderChangedHandledAt = now
            }

            // Use goAsync for long-running operations
            val pendingResult = goAsync()
            
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    // Mark sync as started
                    SyncStatusManager.onSyncStarted()
                    
                    // Schedule the next alarm
                    val scheduleSource = if (isManualSync) "calendar_receiver_manual_sync" else "calendar_receiver_provider_changed"
                    val nextAlarm = AlarmScheduler.scheduleNextAlarm(context, scheduleSource)
                    AlarmScheduler.restorePendingSnoozeIfNeeded(context)

                    // Agenda-wijzigingen (nieuw/verplaatst/verwijderd item) kunnen het eerstvolgende
                    // moment voor een event-gebonden weermelding veranderen - zonder deze aanroep zou
                    // WeatherEventAlertScheduler alleen resyncen bij de eigen 15-min-poll, app-start
                    // of een instellingswijziging, wat een nieuw/verplaatst testitem zou missen.
                    WeatherEventAlertScheduler.rescheduleNext(context)

                    // Mark sync as completed
                    SyncStatusManager.onSyncCompleted(context, nextAlarm)
                    
                    val elapsed = SystemClock.elapsedRealtime() - t0
                    if (nextAlarm != null) {
                        Log.i(
                            TAG,
                            "AgendaAlarmSync: done in ${elapsed}ms — next id=${nextAlarm.id} at=${nextAlarm.epochMillis} label=${nextAlarm.label}"
                        )
                    } else {
                        Log.i(TAG, "AgendaAlarmSync: done in ${elapsed}ms — no upcoming alarms")
                    }
                    
                    // Notify the UI that the alarm has been updated (only if not already manual sync)
                    if (!isManualSync) {
                        val updateIntent = Intent(ACTION_ALARM_UPDATED).apply {
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(updateIntent)
                        Log.d(TAG, "Broadcast sent to update UI")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during calendar sync", e)
                    SyncStatusManager.onSyncFailed(context, e.message ?: "Unknown error")
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            Log.d(TAG, "Ignoring broadcast - not a calendar change or manual sync")
        }
    }
}
