package com.dd.daykit



import android.app.Notification

import android.app.NotificationChannel

import android.app.NotificationManager

import android.app.PendingIntent

import android.app.Service

import android.content.Context

import android.content.pm.ServiceInfo

import android.graphics.Bitmap

import android.graphics.Color

import android.os.Build

import android.os.SystemClock

import android.util.Log

import android.view.View

import android.widget.RemoteViews

import androidx.core.app.NotificationCompat



data class PopupThemeColors(

    val titleColor: Int,

    val subtitleColor: Int,

    val actionButtonColor: Int,

    val actionGlyphColor: Int,

    val backgroundColor: Int

)



/**

 * Texts, optioneel één of twee ronde tray-acties, en hun [PendingIntent]s voor

 * [R.layout.notification_poc_compact]. Als [primaryAction] ontbreekt, wordt de linkercirkel verborgen.

 */

data class CompactTrayNotificationSpec(

    val title: String,

    val subtitle: String,

    val primaryActionGlyph: String?,

    val secondaryActionGlyph: String,

    val primaryAction: PendingIntent?,

    val secondaryAction: PendingIntent,

    /** Tap op lege ruimte / titelzone: custom RemoteViews blokkeert vaak [Notification.contentIntent]. */

    val rootTapAction: PendingIntent? = null,

    /**
     * Wanneer gezet: [R.id.notification_countdown] Chronometer i.p.v. statische [title].
     * Countdown: resterende ms; stopwatch: verstreken ms met [chronometerCountDown] = false.
     */
    val chronometerRemainingMs: Long? = null,

    val chronometerCountDown: Boolean = true,

    val chronometerStarted: Boolean = true,

)



object PopupNotificationFoundation {



    private const val TAG = "PopupFoundation"



    /** Agenda alarms, pre-alarm, snooze, agenda foreground tray / full-screen. */

    const val CALENDAR_ALARM_CHANNEL_ID = "calendar_alarm"



    /** Stopwatch tray and lockscreen foreground. */

    const val STOPWATCH_CHANNEL_ID = "stopwatch"



    /** Timer countdown, timer finished alarm, timer foreground. */

    const val TIMER_CHANNEL_ID = "timer"



    private val ALLOWED_CHANNEL_IDS = setOf(

        CALENDAR_ALARM_CHANNEL_ID,

        STOPWATCH_CHANNEL_ID,

        TIMER_CHANNEL_ID,

        // Melding "agenda-alarm overgeslagen" — bewust een eigen, rustig kanaal en niet het
        // alarmkanaal, want dit is een mededeling achteraf en geen alarm.
        AgendaAlarmSkipNotifier.CHANNEL_ID,

        // Waarschuwing als opgeslagen HA-instellingen niet gelezen konden worden.
        HaSettingsIntegrityNotifier.CHANNEL_ID,

    )



    /** Superseded channel IDs plus debug/FSI test channels — never recreated. */

    private val LEGACY_CHANNEL_IDS = listOf(

        "timer_fgs_bg",

        "stopwatch_fgs_bg",

        "timer_popup_channel",

        "agenda_snooze_popup_channel",

        "stopwatch_lockscreen_popup",

        "agenda_alarm_lockscreen_popup",

        "agenda_alarm_firing_tray",

        "timer_finished_alarm",

        "poc_test_countdown",

        "ALARM_CHANNEL",

        "COUNTDOWN_CHANNEL",

        "TIMER_CHANNEL",

        "STOPWATCH_CHANNEL",

        "TIME_ENGINE_CHANNEL",

        "fsi_debug",

        "fsi_debug_test",

        "fsi_test",

        "fsi_test_channel",

        "fullscreen_intent_test",

        "fullscreen_intent_debug",

        "full_screen_intent_test",

        "alarm_fsi_debug",

        "debug_fsi",

        "test_fsi_channel",

        "notification_fsi_debug",

        "fsi_debug_test_channel",

    )



    fun ensureCalendarAlarmChannel(context: Context) {

        ensureWakeCapableChannel(

            context,

            CALENDAR_ALARM_CHANNEL_ID,

            "Calendar Alarm",

            "Calendar alarms, pre-alarm, snooze, and agenda notifications",

        )

    }



    fun ensureStopwatchChannel(context: Context) {

        ensureWakeCapableChannel(

            context,

            STOPWATCH_CHANNEL_ID,

            "Stopwatch",

            "Stopwatch notifications and lockscreen popup",

        )

    }



    fun ensureTimerChannel(context: Context) {

        ensureWakeCapableChannel(

            context,

            TIMER_CHANNEL_ID,

            "Timer",

            "Timer countdown, timer finished alarm, and timer notifications",

        )

    }



    /**
     * Eigen kanaal-id per extra timer (2e/3e), i.p.v. het gedeelde [TIMER_CHANNEL_ID]. Sommige
     * OEM-schilen (o.a. MIUI) bundelen meerdere meldingen op hetzelfde kanaal van dezelfde app
     * agressief samen in de meldingenbalk, waarbij het gebundelde item een systeem-header (grijze
     * rand) krijgt die niet overschreven kan worden — een unieke [android.app.NotificationChannel]
     * per extra timer voorkomt dat ze als "dezelfde stroom" gezien worden.
     */
    fun extraTimerChannelId(slotIndex: Int): String = "timer_extra_$slotIndex"

    fun ensureExtraTimerChannel(context: Context, slotIndex: Int) {
        ensureWakeCapableChannel(
            context,
            extraTimerChannelId(slotIndex),
            "Timer ${slotIndex + 2}",
            "Countdown en alarm voor timer ${slotIndex + 2}",
        )
    }



    /** Delete superseded channels and ensure the three app channels exist. */

    fun syncAllChannels(context: Context) {

        runCatching {

            val app = context.applicationContext

            deleteLegacyChannels(app)

            ensureCalendarAlarmChannel(app)

            ensureStopwatchChannel(app)

            ensureTimerChannel(app)

            AgendaAlarmSkipNotifier.ensureChannel(app)

            HaSettingsIntegrityNotifier.ensureChannel(app)

        }.onFailure { AlarmRingingDebug.e("syncAllChannels failed", it) }

    }



    private fun deleteLegacyChannels(context: Context) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        for (legacyId in LEGACY_CHANNEL_IDS) {

            deleteChannelIfPresent(nm, legacyId)

        }

        pruneObsoleteChannelsOnDevice(nm)

    }



    private fun deleteChannelIfPresent(nm: NotificationManager, channelId: String) {

        if (channelId in ALLOWED_CHANNEL_IDS) return

        if (nm.getNotificationChannel(channelId) == null) return

        nm.deleteNotificationChannel(channelId)

        Log.i(TAG, "CHANNEL_MIGRATION deleted channelId=$channelId")

    }



    /** Removes leftover debug/test channels still on the device (e.g. FSI debug POC). */

    private fun pruneObsoleteChannelsOnDevice(nm: NotificationManager) {

        for (channel in nm.notificationChannels) {

            val id = channel.id

            if (id in ALLOWED_CHANNEL_IDS) continue

            if (!isObsoleteChannelId(id)) continue

            nm.deleteNotificationChannel(id)

            Log.i(TAG, "CHANNEL_MIGRATION pruned obsolete channelId=$id name=${channel.name}")

        }

    }



    private fun isObsoleteChannelId(channelId: String): Boolean {

        if (channelId in LEGACY_CHANNEL_IDS) return true

        val id = channelId.lowercase()

        if (id in ALLOWED_CHANNEL_IDS) return false

        if (id.startsWith("poc_")) return true

        if (id.contains("fsi_debug") || id.contains("debug_fsi")) return true

        if (id.contains("fullscreen_intent") && (id.contains("test") || id.contains("debug"))) return true

        if (id.contains("full_screen_intent") && (id.contains("test") || id.contains("debug"))) return true

        if (id.endsWith("_fsi_test") || id == "fsi_test") return true

        if (id.contains("test_countdown")) return true

        return false

    }



    private fun ensureWakeCapableChannel(

        context: Context,

        channelId: String,

        channelName: String,

        description: String,

    ) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val app = context.applicationContext

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val importance = WakeMobilePolicy.channelImportance(app)

        recreateStaleWakeChannelIfNeeded(nm, channelId, importance)

        val channel = NotificationChannel(channelId, channelName, importance).apply {

            this.description = description

            setSound(null, null)

            enableVibration(false)

            lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            setBypassDnd(true)

        }

        nm.createNotificationChannel(channel)

        if (WakeMobilePolicy.isWakeCapableChannel(channelId)) {

            AlarmRingingDebug.logNotificationChannel(app, channelId)

        }

    }



    // TODO: Deleting and recreating a channel with the SAME id does not reset user-chosen
    // importance on Android — the OS restores the old settings. A proper importance upgrade
    // requires migrating to a new channel id (e.g. timer_v2).

    /**

     * Android ignores importance updates on existing channels. Delete stale channels so

     * [setFullScreenIntent] can work when "Wek mobiel" is on.

     */

    private fun recreateStaleWakeChannelIfNeeded(

        nm: NotificationManager,

        channelId: String,

        targetImportance: Int,

    ) {

        if (targetImportance < NotificationManager.IMPORTANCE_HIGH) return

        val existing = nm.getNotificationChannel(channelId) ?: return

        if (existing.importance >= NotificationManager.IMPORTANCE_HIGH) return

        try {

            nm.deleteNotificationChannel(channelId)

        } catch (e: Exception) {

            AlarmRingingDebug.w("notificationChannel id=$channelId recreate skipped — channel in use by foreground service")

            return

        }

        AlarmRingingDebug.w(

            "notificationChannel id=$channelId recreated=true staleImportance=${existing.importance} " +

                "targetImportance=$targetImportance wakeMobile=true"

        )

    }



    fun resolveThemeColors(context: Context): PopupThemeColors {

        val backgroundType = SettingsManager.getBackgroundType(context).lowercase()

        val gifUri = SettingsManager.getBackgroundGifUri(context).orEmpty()

        val useBlackFallback = backgroundType.contains("gif") || gifUri.isNotBlank()

        val background = if (useBlackFallback) Color.BLACK else SettingsManager.getBackgroundColor(context)

        return PopupThemeColors(

            titleColor = SettingsManager.getTextColor(context),

            subtitleColor = SettingsManager.getTextColor(context),

            actionButtonColor = SettingsManager.getButtonColor(context),

            actionGlyphColor = SettingsManager.getButtonTextColor(context),

            backgroundColor = background

        )

    }



    fun buildPocCompactRemoteViews(

        context: Context,

        colors: PopupThemeColors,

        spec: CompactTrayNotificationSpec,

    ): RemoteViews {

        return RemoteViews(context.packageName, R.layout.notification_poc_compact).apply {

            setInt(R.id.poc_root, "setBackgroundColor", colors.backgroundColor)

            val chronometerMs = spec.chronometerRemainingMs

            if (chronometerMs != null) {

                val base = if (spec.chronometerCountDown) {

                    SystemClock.elapsedRealtime() + chronometerMs

                } else {

                    SystemClock.elapsedRealtime() - chronometerMs

                }

                setChronometer(

                    R.id.notification_countdown,

                    base,

                    null,

                    spec.chronometerStarted,

                )

                setChronometerCountDown(R.id.notification_countdown, spec.chronometerCountDown)

            } else {

                setTextViewText(R.id.notification_countdown, spec.title)

            }

            setTextViewText(R.id.poc_subtitle, spec.subtitle)

            setTextColor(R.id.notification_countdown, colors.titleColor)

            setTextColor(R.id.poc_subtitle, colors.subtitleColor)

            val primary = spec.primaryAction

            val primaryGlyph = spec.primaryActionGlyph

            if (primary != null && primaryGlyph != null) {

                setViewVisibility(R.id.poc_primary_click, View.VISIBLE)

                setInt(R.id.poc_primary_circle, "setColorFilter", colors.actionButtonColor)

                setTextColor(R.id.poc_primary_glyph, colors.actionGlyphColor)

                setTextViewText(R.id.poc_primary_glyph, primaryGlyph)

                setOnClickPendingIntent(R.id.poc_primary_click, primary)

            } else {

                setViewVisibility(R.id.poc_primary_click, View.GONE)

            }

            setInt(R.id.poc_stop_circle, "setColorFilter", colors.actionButtonColor)

            setTextColor(R.id.poc_stop_glyph, colors.actionGlyphColor)

            setTextViewText(R.id.poc_stop_glyph, spec.secondaryActionGlyph)

            setOnClickPendingIntent(R.id.poc_stop_click, spec.secondaryAction)

            spec.rootTapAction?.let { setOnClickPendingIntent(R.id.poc_root, it) }

        }

    }



    fun alarmTrayCompactBuilder(

        context: Context,

        channelId: String,

        backgroundColor: Int,

    ): NotificationCompat.Builder {

        return NotificationCompat.Builder(context, channelId)

            .setSmallIcon(R.drawable.clockgood)

            .setLargeIcon(null as Bitmap?)

            .setWhen(0L)

            .setShowWhen(false)

            .setSilent(true)

            .setOnlyAlertOnce(true)

            .setOngoing(true)

            .setAutoCancel(false)

            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

            .setCategory(NotificationCompat.CATEGORY_ALARM)

            .setPriority(WakeMobilePolicy.compatPriority(context))

            .setColor(backgroundColor)

            .setColorized(true)

    }



    fun baseBuilder(

        context: Context,

        channelId: String,

        backgroundColor: Int

    ): NotificationCompat.Builder {

        return pocCompactStyleBuilder(context, channelId, backgroundColor)

    }



    fun pocCompactStyleBuilder(

        context: Context,

        channelId: String,

        backgroundColor: Int

    ): NotificationCompat.Builder {

        return NotificationCompat.Builder(context, channelId)

            .setSmallIcon(R.drawable.clockgood)

            .setLargeIcon(null as Bitmap?)

            .setWhen(0L)

            .setShowWhen(false)

            .setSilent(true)

            .setOnlyAlertOnce(true)

            .setOngoing(false)

            .setAutoCancel(false)

            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

            .setCategory(NotificationCompat.CATEGORY_SERVICE)

            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            .setColor(backgroundColor)

            .setColorized(true)

    }



    fun startForeground(service: Service, notificationId: Int, notification: Notification) {

        if (Build.VERSION.SDK_INT >= 34) {

            service.startForeground(

                notificationId,

                notification,

                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

            )

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            @Suppress("DEPRECATION")

            service.startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)

        } else {

            @Suppress("DEPRECATION")

            service.startForeground(notificationId, notification)

        }

        Log.i(TAG, "FOREGROUND_START notificationId=$notificationId service=${service.javaClass.simpleName} sdk=${Build.VERSION.SDK_INT}")

    }

}


