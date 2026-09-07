package com.dd.daykit

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Gedeelde build-logica voor extra-timer (2e/3e) tray-notificaties.
 * Gebruikt door [TimerPopupForegroundService] en [ExtraTimerForegroundServiceBase].
 */
internal object ExtraTimerPopupNotifications {

    const val EXTRA_NOTIFICATION_ID_BASE = 9020

    private const val REQ_CODE_EXTRA_PAUSE_BASE = 5110
    private const val REQ_CODE_EXTRA_STOP_BASE = 5120
    private const val REQ_CODE_CONTENT = 5104

    fun notificationId(slotIndex: Int): Int = EXTRA_NOTIFICATION_ID_BASE + slotIndex

    fun build(context: Context, slot: ExtraTimerData, index: Int): Notification {
        LanguageManager.init(context.applicationContext)
        val extraChannelId = PopupNotificationFoundation.extraTimerChannelId(index)
        PopupNotificationFoundation.ensureExtraTimerChannel(context, index)
        val colors = PopupNotificationFoundation.resolveThemeColors(context)
        val timeText = TimerDisplayFormat.formatMillisCompact(slot.remainingMs)
        val subtitle = slot.name.ifBlank { LanguageManager.getString("screen_timer") }
        val pauseGlyph = if (slot.state == GlobalTimerManager.TimerState.RUNNING) "II" else "▶"
        val pausePi = buildExtraServiceIntent(
            context,
            TimerPopupForegroundService.ACTION_EXTRA_TOGGLE_PAUSE_RESUME,
            REQ_CODE_EXTRA_PAUSE_BASE + index,
            index,
        )
        val stopPi = buildExtraServiceIntent(
            context,
            TimerPopupForegroundService.ACTION_EXTRA_STOP,
            REQ_CODE_EXTRA_STOP_BASE + index,
            index,
        )
        val contentPi = buildContentTapPendingIntent(context)

        return runCatching {
            val spec = CompactTrayNotificationSpec(
                title = timeText,
                subtitle = subtitle,
                primaryActionGlyph = pauseGlyph,
                secondaryActionGlyph = "✕",
                primaryAction = pausePi,
                secondaryAction = stopPi,
                rootTapAction = contentPi,
                chronometerRemainingMs = slot.remainingMs.coerceAtLeast(0L),
                chronometerCountDown = true,
                chronometerStarted = slot.state == GlobalTimerManager.TimerState.RUNNING,
            )
            val compactRv = PopupNotificationFoundation.buildPocCompactRemoteViews(context, colors, spec)
            PopupNotificationFoundation
                .alarmTrayCompactBuilder(context, extraChannelId, colors.backgroundColor)
                .setCustomContentView(compactRv)
                .setContentIntent(contentPi)
                .setGroup("timer_notif_extra_$index")
                .build()
        }.getOrElse {
            PopupNotificationFoundation
                .alarmTrayCompactBuilder(context, extraChannelId, colors.backgroundColor)
                .setContentTitle(timeText)
                .setContentText(subtitle)
                .addAction(android.R.drawable.ic_media_pause, pauseGlyph, pausePi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "X", stopPi)
                .setContentIntent(contentPi)
                .setGroup("timer_notif_extra_$index")
                .build()
        }
    }

    private fun buildExtraServiceIntent(
        context: Context,
        action: String,
        requestCode: Int,
        index: Int,
    ): PendingIntent {
        val i = Intent(context, TimerPopupForegroundService::class.java).apply {
            this.action = action
            putExtra(TimerPopupForegroundService.EXTRA_SLOT_INDEX, index)
        }
        return PendingIntent.getService(
            context,
            requestCode,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildContentTapPendingIntent(context: Context): PendingIntent {
        val i = Intent(context, TimerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("FROM_NAVIGATION", true)
        }
        return PendingIntent.getActivity(
            context,
            REQ_CODE_CONTENT,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
