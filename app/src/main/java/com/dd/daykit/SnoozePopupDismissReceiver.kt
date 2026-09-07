package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SnoozePopupDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "NOTIFICATION_REMOVED popupType=snooze source=swipe")
        val i = Intent(context.applicationContext, SnoozePopupForegroundService::class.java).apply {
            action = SnoozePopupForegroundService.ACTION_SWIPE_TEARDOWN
        }
        context.applicationContext.startService(i)
    }

    companion object {
        private const val TAG = "SnoozePopupDismissRx"
    }
}
