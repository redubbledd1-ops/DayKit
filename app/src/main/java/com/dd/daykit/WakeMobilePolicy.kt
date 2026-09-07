package com.dd.daykit

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Controls phone wake, full-screen intent, and automatic alarm-screen launch.
 *
 * Independent of tray countdown notifications (timer/sluimer); system settings control heads-up.
 */
object WakeMobilePolicy {

    const val ACTION_CHANGED = "com.dd.daykit.WAKE_MOBILE_CHANGED"

    fun isFullScreenAlarmEnabled(context: Context): Boolean =
        SettingsManager.getFullScreenAlarmEnabled(context.applicationContext)

    fun canUseFullScreenAlarm(context: Context): Boolean =
        isFullScreenAlarmEnabled(context) && FullScreenIntentPermission.canUse(context.applicationContext)

    fun isWakeMobileEnabled(context: Context): Boolean =
        canUseFullScreenAlarm(context)

    /** Minimum for reliable [android.app.Notification.Builder.setFullScreenIntent] on API 29+. */
    fun channelImportance(context: Context): Int =
        if (canUseFullScreenAlarm(context)) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }

    fun isWakeCapableChannel(channelId: String): Boolean =
        channelId == PopupNotificationFoundation.CALENDAR_ALARM_CHANNEL_ID ||
            channelId == PopupNotificationFoundation.TIMER_CHANNEL_ID ||
            channelId == PopupNotificationFoundation.STOPWATCH_CHANNEL_ID

    fun compatPriority(context: Context): Int =
        if (canUseFullScreenAlarm(context)) {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

    fun applyFullScreenIntent(
        context: Context,
        builder: NotificationCompat.Builder,
        fullScreenPi: PendingIntent,
        phase: String,
    ): NotificationCompat.Builder {
        return if (canUseFullScreenAlarm(context)) {
            AlarmRingingDebug.logFullScreenIntent(phase, attached = true)
            builder.setFullScreenIntent(fullScreenPi, true)
        } else {
            AlarmRingingDebug.logFullScreenIntent(
                phase,
                attached = false,
                reason = disabledReason(context),
            )
            builder
        }
    }

    fun notifySettingChanged(context: Context) {
        val app = context.applicationContext
        AlarmRingingDebug.logWakeMobile(app, "notifySettingChanged")
        PopupNotificationFoundation.syncAllChannels(app)
        app.sendBroadcast(Intent(ACTION_CHANGED).setPackage(app.packageName))
        refreshActiveAlarmTrayNotifications(app)
    }

    private fun refreshActiveAlarmTrayNotifications(app: Context) {
        if (GlobalTimerManager.isAlarmPlaying ||
            GlobalTimerManager.timerState.value == GlobalTimerManager.TimerState.FINISHED
        ) {
            TimerFinishedAlarmUi.ensureChannel(app)
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                TimerFinishedAlarmUi.NOTIFICATION_ID,
                TimerFinishedAlarmUi.buildTimerFinishedNotification(app),
            )
        }
        if (AlarmStateManager.isRingingNow) {
            val alarm = AlarmStateManager.ringingAlarmNow ?: return
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                AlarmService.FIRING_TRAY_NOTIFICATION_ID,
                AlarmService.buildFiringTrayNotification(app, alarm),
            )
        }
    }

    private fun disabledReason(context: Context): String {
        if (!isFullScreenAlarmEnabled(context)) return "full_screen_alarm_disabled"
        if (!FullScreenIntentPermission.canUse(context.applicationContext)) return "full_screen_permission_missing"
        return "full_screen_unavailable"
    }
}
