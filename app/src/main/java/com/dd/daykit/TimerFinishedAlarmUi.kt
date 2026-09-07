package com.dd.daykit

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Foreground notification + full-screen intent for "timer finished" alarm.
 * Ongoing until the user taps Restart or Stop (or equivalent in [TimerFinishedActivity]).
 *
 * Zelfde compacte tray-layout als [TimerPopupForegroundService] via
 * [PopupNotificationFoundation.buildPocCompactRemoteViews] + [CompactTrayNotificationSpec].
 */
object TimerFinishedAlarmUi {

    val CHANNEL_ID: String get() = PopupNotificationFoundation.TIMER_CHANNEL_ID
    const val NOTIFICATION_ID = 91002

    /** Losse "timer afgelopen"-melding per extra timer (2e/3e): BASE + index in [ExtraTimerManager.timers]. */
    private const val EXTRA_NOTIFICATION_ID_BASE = 91010

    /** Intent-extra keys gedeeld door [TimerFinishedActivity] en [TimerFinishedActionReceiver]. */
    const val EXTRA_SLOT_ID = "extra_timer_slot_id"
    const val EXTRA_SLOT_INDEX = "extra_timer_slot_index"

    private const val RC_FULL_SCREEN = 9101
    private const val RC_CONTENT_TAP = 9102
    private const val RC_ACTION_RESTART = 9103
    private const val RC_ACTION_STOP = 9104

    /** Losse request-codes per extra timer, ver uit de buurt van de primaire codes hierboven. */
    private const val RC_EXTRA_BASE = 9110
    private const val RC_EXTRA_STRIDE = 10

    /** Zelfde glyph-stijl als overige tray-acties; herstart = tegen de klok in. */
    private const val GLYPH_RESTART = "↻"

    /** [slotIndex] null = primaire timer (gedeeld kanaal); anders het eigen kanaal van die extra timer. */
    fun ensureChannel(context: Context, slotIndex: Int? = null) {
        if (slotIndex == null) {
            PopupNotificationFoundation.ensureTimerChannel(context)
        } else {
            PopupNotificationFoundation.ensureExtraTimerChannel(context, slotIndex)
        }
    }

    fun extraNotificationId(slotIndex: Int): Int = EXTRA_NOTIFICATION_ID_BASE + slotIndex

    /** [slotIndex] null = primaire timer; anders de 2e/3e timer op die index in [ExtraTimerManager.timers]. */
    fun cancel(context: Context, slotIndex: Int? = null) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(if (slotIndex == null) NOTIFICATION_ID else extraNotificationId(slotIndex))
    }

    /**
     * [slotId]/[slotIndex] null = primaire timer (bestaand gedrag). Anders wordt een losse
     * "timer afgelopen"-melding gebouwd voor de 2e/3e timer, met eigen notificatie-id,
     * eigen PendingIntents (geen collisie met de primaire of andere extra timers) en een
     * eigen full-screen intent naar dezelfde [TimerFinishedActivity] — nu met de bijbehorende
     * slot-id als extra, zodat de activity weet welke timer herstart/gestopt moet worden.
     */
    fun buildTimerFinishedNotification(
        context: Context,
        slotId: String? = null,
        slotIndex: Int = 0,
    ): Notification {
        LanguageManager.init(context.applicationContext)
        val app = context.applicationContext
        // Extra timers krijgen hun eigen kanaal (zie TimerPopupForegroundService.buildExtraTimerNotification
        // voor dezelfde reden): voorkomt dat MIUI/Android deze samen met de primaire melding bundelt.
        val channelId = if (slotId != null) {
            PopupNotificationFoundation.ensureExtraTimerChannel(app, slotIndex)
            PopupNotificationFoundation.extraTimerChannelId(slotIndex)
        } else {
            CHANNEL_ID
        }
        AlarmRingingDebug.logFsiPermission(app, "buildTimerFinishedNotification")
        AlarmRingingDebug.logNotificationChannel(app, channelId)
        val colors = PopupNotificationFoundation.resolveThemeColors(app)
        val title = LanguageManager.getString("timer_finished_tray_title")
        val subtitle = if (slotId != null) {
            val extraSlot = ExtraTimerManager.findById(slotId)
            extraSlot?.name?.ifBlank { null } ?: "${LanguageManager.getString("screen_timer")} ${slotIndex + 2}"
        } else {
            GlobalTimerManager.timerName.value.ifBlank { LanguageManager.getString("screen_timer") }
        }
        val restartLabel = LanguageManager.getString("timer_alarm_restart")
        val stopLabel = LanguageManager.getString("timer_alarm_close")
        val notificationId = if (slotId != null) extraNotificationId(slotIndex) else NOTIFICATION_ID
        val rcOffset = if (slotId != null) RC_EXTRA_BASE + slotIndex * RC_EXTRA_STRIDE else 0
        val rcFullScreen = if (slotId != null) rcOffset + 1 else RC_FULL_SCREEN
        val rcContentTap = if (slotId != null) rcOffset + 2 else RC_CONTENT_TAP
        val rcRestart = if (slotId != null) rcOffset + 3 else RC_ACTION_RESTART
        val rcStop = if (slotId != null) rcOffset + 4 else RC_ACTION_STOP

        val activityIntent = Intent(app, TimerFinishedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (slotId != null) {
                putExtra(EXTRA_SLOT_ID, slotId)
                putExtra(EXTRA_SLOT_INDEX, slotIndex)
            }
        }
        val fullScreenPi = PendingIntent.getActivity(
            app,
            rcFullScreen,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentPi = PendingIntent.getActivity(
            app,
            rcContentTap,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restartIntent = Intent(app, TimerFinishedActionReceiver::class.java).apply {
            action = TimerFinishedActionReceiver.ACTION_RESTART
            if (slotId != null) {
                putExtra(EXTRA_SLOT_ID, slotId)
                putExtra(EXTRA_SLOT_INDEX, slotIndex)
            }
        }
        val restartPi = PendingIntent.getBroadcast(
            app,
            rcRestart,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(app, TimerFinishedActionReceiver::class.java).apply {
            action = TimerFinishedActionReceiver.ACTION_STOP
            if (slotId != null) {
                putExtra(EXTRA_SLOT_ID, slotId)
                putExtra(EXTRA_SLOT_INDEX, slotIndex)
            }
        }
        val stopPi = PendingIntent.getBroadcast(
            app,
            rcStop,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val spec = CompactTrayNotificationSpec(
            title = title,
            subtitle = subtitle,
            primaryActionGlyph = GLYPH_RESTART,
            secondaryActionGlyph = "✕",
            primaryAction = restartPi,
            secondaryAction = stopPi,
            rootTapAction = contentPi,
        )

        // Eigen group-key per timer (net als de tray-meldingen in TimerPopupForegroundService):
        // zonder deze bundelt Android/MIUI meerdere "timer afgelopen"-meldingen samen en krijgt
        // het gebundelde kind-item een verplichte grijze systeem-header boven onze eigen
        // zwarte achtergrond — dat is exact de grijze balk die steeds terugkwam.
        val groupKey = if (slotId != null) "timer_finished_extra_$slotIndex" else "timer_finished_primary"

        return runCatching {
            val compactRv = PopupNotificationFoundation.buildPocCompactRemoteViews(app, colors, spec)
            WakeMobilePolicy
                .applyFullScreenIntent(
                    app,
                    PopupNotificationFoundation
                        .alarmTrayCompactBuilder(app, channelId, colors.backgroundColor)
                        .setCustomContentView(compactRv)
                        .setContentTitle(title)
                        .setContentText(subtitle)
                        .setContentIntent(contentPi),
                    fullScreenPi,
                    "buildTimerFinishedNotification.customView",
                )
                .setGroup(groupKey)
                .build()
        }.getOrElse {
            WakeMobilePolicy
                .applyFullScreenIntent(
                    app,
                    PopupNotificationFoundation
                        .alarmTrayCompactBuilder(app, channelId, colors.backgroundColor)
                        .setContentTitle(title)
                        .setContentText(subtitle)
                        .setContentIntent(contentPi),
                    fullScreenPi,
                    "buildTimerFinishedNotification.fallback",
                )
                .addAction(0, restartLabel, restartPi)
                .addAction(0, stopLabel, stopPi)
                .setGroup(groupKey)
                .build()
        }.also {
            // notificationId onbenut laten zou een unused-var warning geven bij sommige compilers;
            // caller (ExtraTimerManager/TimeEngineService) gebruikt zelf extraNotificationId(...)/NOTIFICATION_ID.
            Log.d("TimerFinishedAlarmUi", "buildTimerFinishedNotification id=$notificationId slotId=$slotId")
        }
    }
}
