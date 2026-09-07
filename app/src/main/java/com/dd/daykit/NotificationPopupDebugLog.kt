package com.dd.daykit

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TEMP: diagnose desync between in-app next-alarm display and Android tray/popup UI.
 * Filter logcat: `NotificationDebug` or `PopupDebug`
 */
object NotificationPopupDebugLog {

    private const val TAG_NOTIFICATION = "NotificationDebug"
    private const val TAG_POPUP = "PopupDebug"

    private val wallClockFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun formatEpoch(epochMillis: Long): String =
        "$epochMillis (${wallClockFmt.format(Date(epochMillis))})"

    fun notification(
        source: String,
        nextAlarmEpoch: Long?,
        displayedTime: String,
        notificationRebuilt: Boolean,
        extra: String = "",
    ) {
        val epochPart = nextAlarmEpoch?.let { formatEpoch(it) } ?: "null"
        val extraPart = if (extra.isEmpty()) "" else " $extra"
        Log.i(
            TAG_NOTIFICATION,
            "NotificationDebug source=$source nextAlarmEpoch=$epochPart displayedTime=$displayedTime notificationRebuilt=$notificationRebuilt$extraPart",
        )
    }

    fun popup(
        source: String,
        nextAlarmEpoch: Long?,
        displayedTime: String,
        extra: String = "",
    ) {
        val epochPart = nextAlarmEpoch?.let { formatEpoch(it) } ?: "null"
        val extraPart = if (extra.isEmpty()) "" else " $extra"
        Log.i(
            TAG_POPUP,
            "PopupDebug source=$source nextAlarmEpoch=$epochPart displayedTime=$displayedTime$extraPart",
        )
    }

    /** Compare scheduler truth vs cache vs lastScheduledMainAlarm (call after scheduleNextAlarm). */
    fun consistencySnapshot(
        source: String,
        schedulerPicked: AlarmItem?,
        cacheEpoch: Long?,
        lastScheduledEpoch: Long?,
    ) {
        Log.i(
            TAG_NOTIFICATION,
            "NotificationDebug source=$source type=consistency " +
                "schedulerEpoch=${schedulerPicked?.epochMillis?.let { formatEpoch(it) } ?: "null"} " +
                "cacheEpoch=${cacheEpoch?.let { formatEpoch(it) } ?: "null"} " +
                "lastScheduledMainEpoch=${lastScheduledEpoch?.let { formatEpoch(it) } ?: "null"}",
        )
    }
}
