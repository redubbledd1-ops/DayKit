package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AgendaAlarmPopupDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "NOTIFICATION_REMOVED popupType=agenda_pre source=swipe")
        GlobalInAppMessageManager.dismissAgendaAlarmInAppOverlayAfterExternalAction()
        val i = Intent(context.applicationContext, AgendaAlarmPopupForegroundService::class.java).apply {
            action = AgendaAlarmPopupForegroundService.ACTION_SWIPE_TEARDOWN
        }
        context.applicationContext.startService(i)
    }

    companion object {
        private const val TAG = "AgendaPopupDismissRx"
    }
}
