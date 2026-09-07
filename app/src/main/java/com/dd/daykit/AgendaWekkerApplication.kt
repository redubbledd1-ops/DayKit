package com.dd.daykit

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log

/** Process-wide init for language, timer/stopwatch state restore, and one-shot migrations. */
class AgendaWekkerApplication : Application() {

    private companion object {
        const val TAG = "AgendaWekkerApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "APPLICATION_CREATE pid=${android.os.Process.myPid()} — process (re)started")
        // Als eerste: een procesherstart midden in de nacht is zélf een aanwijzing (OOM-kill of
        // battery optimizer). Zonder deze regel is achteraf niet te zien dát het proces omviel.
        AgendaAlarmForensics.init(this)
        AgendaAlarmForensics.log(
            AgendaAlarmForensics.Cat.PROCESS,
            "APPLICATION_CREATE pid=${android.os.Process.myPid()}"
        )
        AgendaAlarmForensics.snapshotArmedState(this, "app_create")
        AlarmScheduler.restorePendingSnoozeIfNeeded(this)
        AlarmStateManager.restoreRingingAlarmIfNeeded(this)
        LanguageManager.init(this)
        InAppNotificationsVisibility.syncFromPrefs(this)
        PopupNotificationFoundation.syncAllChannels(this)
        TimerSettingsStateHolder.init(this)
        GlobalTimerManager.init(this)
        StopwatchStateHolder.init(this)
        CountdownMilestoneMigration.runOnce(this)
        if (SettingsManager.getAutoSyncEnabled(this)) {
            CalendarSyncWorker.schedule(this, androidx.work.ExistingPeriodicWorkPolicy.KEEP)
        }
        WeatherLocationSyncWorker.syncScheduleWithSettings(this)
        WeatherAlertWorker.syncScheduleWithSettings(this)
        WeatherDailyAlertScheduler.rescheduleAll(this)
        Log.i(TAG, "APPLICATION_CREATE init complete timerState=${GlobalTimerManager.getCurrentState()} stopwatchState=${StopwatchStateHolder.stopwatchState.value}")

        registerActivityLifecycleCallbacks(TimerOverlayRelauncher())
    }
}

/**
 * Re-asserts [TimerFinishedActivity] on top whenever any other activity resumes while a
 * timer alarm is playing. Idempotent: TimerFinishedActivity uses `launchMode="singleTask"`,
 * so re-launching it just brings the existing instance to the front.
 */
private class TimerOverlayRelauncher : Application.ActivityLifecycleCallbacks {

    private companion object {
        const val TAG = "TimerOverlayRelauncher"
    }

    override fun onActivityResumed(activity: Activity) {
        // Guard: if TimeEngineService is not actually running, clear stale in-memory alarm state
        if (!TimeEngineService.isRunning && GlobalTimerManager.isAlarmPlaying) {
            Log.w(TAG, "OOM guard: isAlarmPlaying=true but TimeEngineService not running — resetting")
            GlobalTimerManager.setAlarmPlaying(false)
        }

        if (activity is TimerFinishedActivity) return

        val alarmActive = GlobalTimerManager.isAlarmPlaying ||
            GlobalTimerManager.timerState.value == GlobalTimerManager.TimerState.FINISHED

        // Zelfde re-assert gedrag geldt voor een afgelopen 2e/3e (extra) timer — anders blijft
        // die pagina onzichtbaar wanneer de gebruiker eerst naar een andere activity navigeert.
        ExtraTimerManager.init(activity)
        val finishedExtraIndex = ExtraTimerManager.timers.indexOfFirst {
            it.state == GlobalTimerManager.TimerState.FINISHED
        }
        val finishedExtraSlot = finishedExtraIndex.takeIf { it >= 0 }?.let { ExtraTimerManager.timers[it] }

        if (!alarmActive && finishedExtraSlot == null) return
        if (!WakeMobilePolicy.isWakeMobileEnabled(activity)) {
            AlarmRingingDebug.logActivityLaunch(
                "TimerOverlayRelauncher",
                "TimerFinishedActivity",
                attempted = false,
                reason = "full_screen_alarm_unavailable",
            )
            return
        }

        Log.i(TAG, "Re-asserting TimerFinishedActivity over ${activity.javaClass.simpleName} extraSlot=${finishedExtraSlot?.id}")
        try {
            val intent = Intent(activity, TimerFinishedActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                // Primaire timer heeft voorrang als beide tegelijk klaar zijn (bestaand gedrag).
                if (!alarmActive && finishedExtraSlot != null) {
                    putExtra(TimerFinishedAlarmUi.EXTRA_SLOT_ID, finishedExtraSlot.id)
                    putExtra(TimerFinishedAlarmUi.EXTRA_SLOT_INDEX, finishedExtraIndex)
                }
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to re-launch TimerFinishedActivity (possible BAL block)", e)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
