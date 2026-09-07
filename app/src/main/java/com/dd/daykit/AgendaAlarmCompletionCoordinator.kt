package com.dd.daykit

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ensures [AlarmScheduler.scheduleNextAlarm] runs once after an agenda [AlarmService] ring ends.
 *
 * Covers dismiss, tray stop, [AlarmService.onDestroy], and force-stop paths. Snooze uses
 * [suppressCompletionRescheduleForSnooze] so post-snooze scheduling is not doubled.
 */
object AgendaAlarmCompletionCoordinator {

    private const val TAG = "AgendaAlarmCompletion"
    private const val DEDUPE_WINDOW_MS = 3_000L

    @Volatile
    private var pendingCompletionAlarm: AlarmItem? = null

    @Volatile
    private var suppressCompletionReschedule = false

    @Volatile
    private var lastCompletionScheduleAtMs = 0L

    private val lock = Any()

    /** Call when [AlarmService] starts ringing (calendar or snooze delivery). */
    fun onAgendaAlarmRinging(alarm: AlarmItem) {
        pendingCompletionAlarm = alarm
        suppressCompletionReschedule = false
        Log.d(TAG, "onAgendaAlarmRinging id=${alarm.id} epoch=${alarm.epochMillis} trigger=${alarm.triggerId}")
    }

    /**
     * User chose snooze — [AgendaAlarmSnoozeCoordinator.launchPostSnoozeCalendarResync] will refresh planning.
     */
    fun suppressCompletionRescheduleForSnooze() {
        suppressCompletionReschedule = true
        pendingCompletionAlarm = null
        Log.d(TAG, "suppressCompletionRescheduleForSnooze")
    }

    /**
     * A snooze tap can be rejected (0 minutes or max count reached). In that case
     * the alarm should finish like a normal completion instead of staying suppressed.
     */
    fun resumeCompletionRescheduleAfterRejectedSnooze(alarm: AlarmItem?) {
        suppressCompletionReschedule = false
        pendingCompletionAlarm = alarm
        Log.d(TAG, "resumeCompletionRescheduleAfterRejectedSnooze id=${alarm?.id}")
    }

    /** Snapshot for [AlarmService.onDestroy] when [AlarmStateManager] was already cleared on the main thread. */
    fun peekPendingCompletionAlarm(): AlarmItem? = pendingCompletionAlarm

    /**
     * Schedule the next calendar alarm after a ring session ends. Safe to call from multiple paths;
     * only one [AlarmScheduler.scheduleNextAlarm] runs per [DEDUPE_WINDOW_MS].
     */
    fun scheduleNextAfterCompletion(context: Context, source: String, completedAlarm: AlarmItem? = null) {
        if (suppressCompletionReschedule) {
            suppressCompletionReschedule = false
            Log.d(TAG, "scheduleNextAfterCompletion skipped (snooze) source=$source")
            return
        }

        val alarm = completedAlarm
            ?: pendingCompletionAlarm
            ?: AlarmStateManager.restoreRingingAlarmIfNeeded(context)
        pendingCompletionAlarm = null

        if (alarm == null) {
            Log.d(TAG, "scheduleNextAfterCompletion skipped (no alarm) source=$source")
            return
        }

        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastCompletionScheduleAtMs < DEDUPE_WINDOW_MS) {
                Log.d(
                    TAG,
                    "scheduleNextAfterCompletion deduped source=$source id=${alarm.id} " +
                        "epoch=${alarm.epochMillis} (${now - lastCompletionScheduleAtMs}ms since last)"
                )
                return
            }
            lastCompletionScheduleAtMs = now
        }

        val app = context.applicationContext
        Log.i(
            TAG,
            "scheduleNextAfterCompletion source=$source id=${alarm.id} epoch=${alarm.epochMillis} " +
                "trigger=${alarm.triggerId}"
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.scheduleNextAlarm(app, "alarm_completion_$source")
                Log.i(TAG, "scheduleNextAfterCompletion done source=$source")
            } catch (e: Exception) {
                Log.e(TAG, "scheduleNextAfterCompletion failed source=$source", e)
            }
            try {
                app.sendBroadcast(
                    Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED).apply {
                        setPackage(app.packageName)
                    }
                )
            } catch (_: Exception) {
            }
        }
    }
}
