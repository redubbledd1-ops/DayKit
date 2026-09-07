package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StopwatchPopupDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("StopwatchPopupService", "NOTIFICATION_DISMISSED popupType=stopwatch — reposting immediately")
        StopwatchPopupForegroundService.repostCachedNotification(context)
        StopwatchPopupForegroundService.syncFromStopwatchState(context, "swipe_repost")
    }
}
