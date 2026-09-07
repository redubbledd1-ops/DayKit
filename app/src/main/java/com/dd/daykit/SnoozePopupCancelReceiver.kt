package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Compacte tray: ✕ — agenda-sluiten annuleren (zelfde gedrag als sluimeren stoppen in de app). */
class SnoozePopupCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "SNOOZE_TRAY_CANCEL_TAP")
        AlarmScheduler.cancelAgendaSnoozeUser(context.applicationContext)
    }

    companion object {
        private const val TAG = "SnoozePopupCancelRx"
    }
}
