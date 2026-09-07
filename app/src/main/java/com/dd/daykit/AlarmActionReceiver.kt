package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS = "com.dd.daykit.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.dd.daykit.ACTION_SNOOZE"
        private const val TAG = "AlarmActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "AlarmActionReceiver onReceive - action: ${intent.action}")
        val pendingResult = goAsync()

        val alarmItem = intent.getStringExtra("alarm")?.let {
            try {
                Json.decodeFromString<AlarmItem>(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding alarm item in action receiver", e)
                null
            }
        }
        val effectiveAlarm = alarmItem ?: AlarmStateManager.ringingAlarm.value
        val app = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snoozeDecision = if (intent.action == ACTION_SNOOZE) {
                    AgendaAlarmSnoozeCoordinator.evaluateNextSnooze(app, effectiveAlarm)
                } else {
                    null
                }

                if (snoozeDecision?.allowed == true) {
                    AgendaAlarmCompletionCoordinator.suppressCompletionRescheduleForSnooze()
                } else if (snoozeDecision != null) {
                    Log.w(
                        TAG,
                        "Snooze rejected before stop reason=${snoozeDecision.reason} " +
                            "minutes=${snoozeDecision.minutes} count=${snoozeDecision.currentCount} max=${snoozeDecision.maxCount}"
                    )
                }

                // First, stop the ringing service to silence the alarm
                val stopServiceIntent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_STOP
                }

                try {
                    context.stopService(stopServiceIntent)
                    Log.d(TAG, "Alarm service stopped")

                    // Cancel the RingActivity pending intent to prevent it from showing again
                    val ringIntent = Intent(context, RingActivity::class.java)
                    val ringPendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        0,
                        ringIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    ringPendingIntent.cancel()
                    Log.d(TAG, "RingActivity pending intent cancelled")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping service", e)
                }

                Handler(Looper.getMainLooper()).post {
                    GlobalInAppMessageManager.dismissAgendaAlarmInAppOverlayAfterExternalAction()
                    AlarmStateManager.onAlarmStop()
                    AgendaAlarmPopupForegroundService.stopPopup(app, "alarm_tray_action")
                }

                // Perform the requested action (dismiss or snooze)
                when (intent.action) {
                    ACTION_DISMISS -> {
                        Log.d(TAG, "Dismissing alarm, scheduling next")
                        AlarmScheduler.cancelAgendaSnoozeUser(context, skipScheduleRefresh = true)
                        AgendaAlarmCompletionCoordinator.scheduleNextAfterCompletion(
                            app,
                            source = "dismiss",
                            completedAlarm = effectiveAlarm,
                        )
                        pendingResult.finish()
                    }
                    ACTION_SNOOZE -> {
                        Log.d(TAG, "Snoozing alarm effectiveId=${effectiveAlarm?.id} chain=${effectiveAlarm?.snoozeSourceTriggerId} trigger=${effectiveAlarm?.triggerId}")
                        try {
                            val snoozeEndTime = AlarmScheduler.scheduleSnooze(context, effectiveAlarm)
                            if (snoozeEndTime != null) {
                                AlarmStateManager.onSnoozeStart(snoozeEndTime)
                                SnoozePopupForegroundService.startOrSync(app, "snooze_from_action")
                                Log.i(TAG, "SNOOZE scheduled until $snoozeEndTime — refreshing main agenda selection")
                                AgendaAlarmSnoozeCoordinator.launchPostSnoozeCalendarResync(app)
                            } else {
                                Log.w(TAG, "Snooze not scheduled; completing alarm")
                                AlarmScheduler.cancelAgendaSnoozeUser(context, skipScheduleRefresh = true)
                                AgendaAlarmCompletionCoordinator.resumeCompletionRescheduleAfterRejectedSnooze(effectiveAlarm)
                                AgendaAlarmCompletionCoordinator.scheduleNextAfterCompletion(
                                    app,
                                    source = "snooze_not_scheduled",
                                    completedAlarm = effectiveAlarm,
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error scheduling snooze", e)
                            AlarmScheduler.cancelAgendaSnoozeUser(context, skipScheduleRefresh = true)
                            AgendaAlarmCompletionCoordinator.resumeCompletionRescheduleAfterRejectedSnooze(effectiveAlarm)
                            AgendaAlarmCompletionCoordinator.scheduleNextAfterCompletion(
                                app,
                                source = "snooze_exception",
                                completedAlarm = effectiveAlarm,
                            )
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unknown action: ${intent.action}")
                        pendingResult.finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onReceive", e)
                pendingResult.finish()
            }
        }
    }
}
