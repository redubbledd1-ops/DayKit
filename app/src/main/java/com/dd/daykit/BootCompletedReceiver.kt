package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "BOOT_COMPLETED — restoring alarms, timers, and popup services")
                // Een reboot wist álle AlarmManager-alarmen. Staat dit in de log tussen het wapenen
                // en het verwachte alarmtijdstip, dan is dát de verklaring voor een gemist alarm.
                AgendaAlarmForensics.init(context)
                AgendaAlarmForensics.log(
                    AgendaAlarmForensics.Cat.BOOT,
                    "BOOT_COMPLETED — alle OS-alarmen waren gewist, planning wordt opnieuw opgebouwd",
                    context
                )
                if (SettingsManager.getAutoSyncEnabled(context)) {
                    CalendarSyncWorker.schedule(context, ExistingPeriodicWorkPolicy.KEEP)
                }
                WeatherLocationSyncWorker.syncScheduleWithSettings(context)
                WeatherAlertWorker.syncScheduleWithSettings(context)
                WeatherDailyAlertScheduler.rescheduleAll(context)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AlarmScheduler.scheduleNextAlarm(context, "boot_completed")
                        AlarmScheduler.restorePendingSnoozeIfNeeded(context)
                        restoreTimer(context)
                        restorePopupServices(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "BOOT_COMPLETED restore failed", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                Log.i(TAG, "Time/timezone changed action=${intent.action} — rescheduleNextAlarm+restoreTimer")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AlarmScheduler.scheduleNextAlarm(context, "time_or_timezone_changed")
                        AlarmScheduler.restorePendingSnoozeIfNeeded(context)
                        restoreTimer(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
    
    private fun restorePopupServices(context: Context) {
        try {
            TimerSettingsStateHolder.init(context)
            GlobalTimerManager.init(context)
            val timerState = GlobalTimerManager.getCurrentState()
            if (TimerSettingsStateHolder.isPopupStartupAllowed() &&
                (timerState == GlobalTimerManager.TimerState.RUNNING || timerState == GlobalTimerManager.TimerState.PAUSED)
            ) {
                Log.i(TAG, "POPUP_RESTORE timer popup after boot state=$timerState")
                TimerPopupForegroundService.syncFromTimerState(context, "boot_completed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "POPUP_RESTORE timer failed", e)
        }
        try {
            StopwatchStateHolder.init(context)
            val swState = StopwatchStateHolder.stopwatchState.value
            if (StopwatchStateHolder.popupEnabled.value &&
                (swState == StopwatchState.RUNNING || swState == StopwatchState.PAUSED)
            ) {
                Log.i(TAG, "POPUP_RESTORE stopwatch popup after boot state=$swState")
                StopwatchPopupForegroundService.syncFromStopwatchState(context, "boot_completed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "POPUP_RESTORE stopwatch failed", e)
        }
        try {
            SnoozePopupForegroundService.startOrSync(context, "boot_completed")
        } catch (e: Exception) {
            Log.e(TAG, "POPUP_RESTORE snooze failed", e)
        }
    }

    private fun restoreTimer(context: Context) {
        try {
            // Initialize timer settings first
            TimerSettingsStateHolder.init(context)
            
            // Initialize and restore timer state
            GlobalTimerManager.init(context)
            
            // If timer was running, reschedule the AlarmManager alarm
            if (GlobalTimerManager.timerState.value == GlobalTimerManager.TimerState.RUNNING) {
                val endTimeMs = GlobalTimerManager.endTimeMs
                val now = System.currentTimeMillis()
                
                if (endTimeMs > now) {
                    // Timer still has time remaining - reschedule
                    Log.d(TAG, "Rescheduling timer alarm for $endTimeMs (${(endTimeMs - now) / 1000}s remaining)")
                    GlobalTimerManager.rescheduleTimerAlarm(context)
                } else {
                    // Timer should have fired while device was off - fire now
                    Log.d(TAG, "Timer expired during boot - firing alarm now")
                    TimeEngineService.startTimerAlarm(context)
                }
            } else {
                Log.d(TAG, "No active timer to restore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring timer", e)
        }
    }
}
