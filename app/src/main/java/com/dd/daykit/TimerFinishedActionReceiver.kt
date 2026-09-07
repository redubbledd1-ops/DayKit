package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Notification action targets for [TimerFinishedAlarmUi] (restart / stop).
 */
class TimerFinishedActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "com.dd.daykit.TIMER_FINISHED_STOP"
        const val ACTION_RESTART = "com.dd.daykit.TIMER_FINISHED_RESTART"
        private const val TAG = "TimerFinishedActionRx"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        GlobalTimerManager.init(app)
        ExtraTimerManager.init(app)
        LanguageManager.init(app)

        val slotId = intent?.getStringExtra(TimerFinishedAlarmUi.EXTRA_SLOT_ID)
        val slotIndex = intent?.getIntExtra(TimerFinishedAlarmUi.EXTRA_SLOT_INDEX, 0) ?: 0

        if (slotId != null) {
            // 2e/3e (extra) timer — zelfde acties, maar dan via ExtraTimerManager voor dit slot.
            val slot = ExtraTimerManager.findById(slotId)
            when (intent?.action) {
                ACTION_STOP -> {
                    Log.i(TAG, "action=STOP slot=$slotId")
                    if (slot != null) ExtraTimerManager.stopAlarmOnlyForSlot(app, slot)
                    TimerFinishedAlarmUi.cancel(app, slotIndex)
                }
                ACTION_RESTART -> {
                    Log.i(TAG, "action=RESTART slot=$slotId")
                    if (slot != null) ExtraTimerManager.restartTimerAfterAlarm(app, slot)
                    TimerFinishedAlarmUi.cancel(app, slotIndex)
                }
                else -> Log.w(TAG, "unknown action=${intent?.action} slot=$slotId")
            }
            return
        }

        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "action=STOP")
                GlobalTimerManager.stopAlarmOnly(app)
                TimerFinishedAlarmUi.cancel(app)
            }
            ACTION_RESTART -> {
                Log.i(TAG, "action=RESTART")
                GlobalTimerManager.restartTimerAfterAlarm(app)
                TimerFinishedAlarmUi.cancel(app)
            }
            else -> Log.w(TAG, "unknown action=${intent?.action}")
        }
    }
}
