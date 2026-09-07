package com.dd.daykit

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/** Logcat tag for agenda alarm fullscreen launch diagnostics only. */
object AlarmRingingDebug {

    const val TAG = "AlarmRingingDebug"

    fun i(message: String) = Log.i(TAG, message)

    fun w(message: String) = Log.w(TAG, message)

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }

    fun logWakeMobile(context: Context, phase: String) {
        val fullScreenEnabled = WakeMobilePolicy.isFullScreenAlarmEnabled(context)
        val wakeMobile = WakeMobilePolicy.isWakeMobileEnabled(context)
        i("wakeMobile phase=$phase fullScreenAlarmEnabled=$fullScreenEnabled wakeMobile=$wakeMobile")
        if (!wakeMobile) {
            val reason = if (fullScreenEnabled) "full_screen_permission_missing" else "full_screen_alarm_disabled"
            i("wakeFlow phase=$phase enabled=false reason=$reason")
        }
    }

    fun logFsiPermission(context: Context, phase: String) {
        logWakeMobile(context, phase)
        val canUse = FullScreenIntentPermission.canUse(context)
        i("fsiPermission phase=$phase canUseFullScreenIntent=$canUse sdk=${Build.VERSION.SDK_INT}")
        if (!canUse) {
            w("fsiPermission MISSING — RingActivity may not auto-open on lockscreen (enable full-screen notifications)")
        }
    }

    fun logFullScreenIntent(phase: String, attached: Boolean, reason: String? = null) {
        if (attached) {
            i("fullScreenIntent phase=$phase attached=true")
        } else {
            val detail = reason?.let { " reason=$it" } ?: ""
            i("fullScreenIntent phase=$phase attached=false$detail")
        }
    }

    fun logActivityLaunch(phase: String, activity: String, attempted: Boolean, reason: String? = null) {
        if (attempted) {
            i("activityLaunch phase=$phase target=$activity attempted=true")
        } else {
            val detail = reason?.let { " reason=$it" } ?: ""
            i("activityLaunch phase=$phase target=$activity attempted=false$detail")
        }
    }

    fun logWakeLock(phase: String, acquired: Boolean, reason: String? = null) {
        if (acquired) {
            i("wakeLock phase=$phase acquired=true")
        } else {
            val detail = reason?.let { " reason=$it" } ?: ""
            i("wakeLock phase=$phase acquired=false$detail")
        }
    }

    fun logNotificationChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            i("notificationChannel id=$channelId (pre-O, no channel object)")
            return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = nm.getNotificationChannel(channelId)
        if (ch == null) {
            w("notificationChannel id=$channelId NOT_FOUND")
            return
        }
        val wakeMobile = WakeMobilePolicy.isWakeMobileEnabled(context)
        i(
            "notificationChannel id=$channelId importance=${ch.importance} " +
                "wakeMobile=$wakeMobile bypassDnd=${ch.canBypassDnd()} " +
                "lockscreenVisibility=${ch.lockscreenVisibility}"
        )
        if (wakeMobile && ch.importance < NotificationManager.IMPORTANCE_HIGH) {
            w(
                "notificationChannel id=$channelId importance too low for fullScreenIntent " +
                    "(need >= IMPORTANCE_HIGH while wakeMobile=true)"
            )
        }
    }
}
