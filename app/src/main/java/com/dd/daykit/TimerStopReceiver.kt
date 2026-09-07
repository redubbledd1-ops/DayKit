package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Stops timer alarm playback and resets timer state (e.g. explicit broadcast from UI or future tray actions). */
class TimerStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Stop the TimeEngineService (stops alarm sound/vibration)
        TimeEngineService.stop(context)
        
        // Reset the timer state
        GlobalTimerManager.resetTimer(context)
    }
}
