package com.dd.daykit

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Centrale acties: lokaal activeren/deactiveren + alarm opnieuw plannen (geen calendar writes).
 * Na plannen: [CalendarUpdateReceiver.ACTION_ALARM_UPDATED] zodat hoofdscherm, cache en
 * [SyncStatusManager] (via [CalendarUpdateReceiver]) meteen meelopen.
 */
object AgendaAlarmLocalActivationCoordinator {

    private const val TAG = "AgendaLocalActivation"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun broadcastAlarmUpdated(app: Context) {
        app.sendBroadcast(
            Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED).apply {
                setPackage(app.packageName)
            }
        )
    }

    fun deactivateAndReschedule(context: Context, alarm: AlarmItem) {
        val app = context.applicationContext
        AgendaAlarmLocalActivationStore.setLocallyEnabled(app, alarm, false)
        AgendaAlarmPopupForegroundService.stopPopup(app, "local_deactivate")
        scope.launch {
            runCatching {
                AlarmScheduler.scheduleNextAlarm(app, "local_activation_deactivate")
                AlarmScheduler.restorePendingSnoozeIfNeeded(app)
                broadcastAlarmUpdated(app)
            }.onFailure {
                Log.e(TAG, "scheduleNextAlarm after deactivate failed", it)
            }
        }
    }

    fun activateAndReschedule(context: Context, alarm: AlarmItem) {
        val app = context.applicationContext
        AgendaAlarmLocalActivationStore.setLocallyEnabled(app, alarm, true)
        scope.launch {
            runCatching {
                AlarmScheduler.scheduleNextAlarm(app, "local_activation_activate")
                AlarmScheduler.restorePendingSnoozeIfNeeded(app)
                broadcastAlarmUpdated(app)
            }.onFailure {
                Log.e(TAG, "scheduleNextAlarm after activate failed", it)
            }
        }
    }
}
