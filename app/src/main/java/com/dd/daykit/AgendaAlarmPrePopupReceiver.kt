package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Exact alarm op het begin van het "Popup laatste x"-venster: start
 * [AgendaAlarmPopupForegroundService] met compacte tray-notificatie.
 */
class AgendaAlarmPrePopupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val json = intent?.getStringExtra(AgendaAlarmPopupForegroundService.EXTRA_ALARM_JSON).orEmpty()
        if (json.isEmpty()) {
            Log.w(TAG, "onReceive: missing alarm json")
            return
        }
        Log.i(TAG, "PRE_POPUP_ALARM_FIRED starting foreground service")
        val app = context.applicationContext
        val i = Intent(app, AgendaAlarmPopupForegroundService::class.java).apply {
            action = AgendaAlarmPopupForegroundService.ACTION_SYNC
            putExtra(AgendaAlarmPopupForegroundService.EXTRA_ALARM_JSON, json)
            putExtra(AgendaAlarmPopupForegroundService.EXTRA_REASON, "pre_popup_alarm")
        }
        runCatching { ContextCompat.startForegroundService(app, i) }
            .onFailure { Log.e(TAG, "startForegroundService failed", it) }
    }

    companion object {
        private const val TAG = "AgendaPrePopupRx"
    }
}
