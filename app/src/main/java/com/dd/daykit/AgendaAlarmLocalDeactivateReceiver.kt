package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * X-knop op vooraf-popup: lokaal dit alarm uitschakelen + opnieuw plannen (zelfde pad als lijst).
 */
class AgendaAlarmLocalDeactivateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val json = intent?.getStringExtra(AgendaAlarmPopupForegroundService.EXTRA_ALARM_JSON).orEmpty()
        if (json.isEmpty()) {
            Log.w(TAG, "missing alarm json")
            return
        }
        val alarm = runCatching { Json.decodeFromString<AlarmItem>(json) }.getOrElse {
            Log.e(TAG, "decode failed", it)
            return
        }
        Log.i(TAG, "DEACTIVATE_FROM_TRAY key=${AgendaAlarmLocalActivationStore.stableKey(alarm)}")
        AgendaAlarmLocalActivationCoordinator.deactivateAndReschedule(context.applicationContext, alarm)
        GlobalInAppMessageManager.dismissAgendaAlarmInAppOverlayAfterExternalAction()
    }

    companion object {
        private const val TAG = "AgendaLocalDeactivateRx"
    }
}
