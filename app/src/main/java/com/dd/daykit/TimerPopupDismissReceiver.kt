package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TimerPopupDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("TimerPopupService", "NOTIFICATION_DISMISSED popupType=timer — reposting immediately")
        TimerPopupForegroundService.repostCachedNotification(context)
        TimerPopupForegroundService.syncFromTimerState(context, "swipe_repost")
    }
}
