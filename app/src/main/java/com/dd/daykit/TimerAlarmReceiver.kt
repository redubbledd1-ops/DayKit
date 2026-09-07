package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that fires when the timer finishes.
 * Starts [TimeEngineService] for alarm playback when the app is not in the foreground.
 */
class TimerAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "TimerAlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Timer alarm received")

        // IDEMPOTENT GUARD: Check if alarm is already playing
        // This prevents duplicate triggers when ticker already handled the alarm
        if (GlobalTimerManager.isAlarmPlaying || TimeEngineService.isRunning) {
            Log.d(TAG, "Alarm already playing or service running - skipping")
            return
        }

        Log.d(TAG, "Starting TimeEngineService for timer alarm")
        TimeEngineService.startTimerAlarm(context)
    }
}
