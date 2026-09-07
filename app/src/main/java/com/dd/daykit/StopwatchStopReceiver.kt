package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopwatchStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Stop the stopwatch via the StateHolder
        StopwatchStateHolder.stopWithoutSave()
    }
}
