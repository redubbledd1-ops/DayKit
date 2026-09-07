package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class ButtonlessNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val json = intent?.getStringExtra(ButtonlessNotificationService.EXTRA_ALARM_JSON).orEmpty()
        if (json.isEmpty()) {
            Log.w(TAG, "onReceive: missing alarm json")
            return
        }
        Log.i(TAG, "BUTTONLESS_ALARM_FIRED starting foreground service")
        val app = context.applicationContext
        val i = Intent(app, ButtonlessNotificationService::class.java).apply {
            action = ButtonlessNotificationService.ACTION_SYNC
            putExtra(ButtonlessNotificationService.EXTRA_ALARM_JSON, json)
            putExtra(ButtonlessNotificationService.EXTRA_REASON, "buttonless_alarm")
        }
        runCatching { ContextCompat.startForegroundService(app, i) }
            .onFailure { Log.e(TAG, "startForegroundService failed", it) }
    }

    companion object {
        private const val TAG = "ButtonlessNotifReceiver"
    }
}
