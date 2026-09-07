package com.dd.daykit

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Calendar

/**
 * Vooraf-melding binnen "Popup laatste x" (alleen als die instelling AAN staat).
 * Zelfde compacte tray als [TimerPopupForegroundService] via [CompactTrayNotificationSpec].
 */
class AgendaAlarmPopupForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            PopupNotificationFoundation.ensureCalendarAlarmChannel(this)
            Log.i(TAG, "SERVICE_LIFECYCLE onCreate popupType=agenda_pre")
        }.onFailure { error ->
            Log.e(TAG, "onCreate ensureChannel failed", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "SERVICE_START action=$action startId=$startId")
        return when (action) {
            ACTION_SYNC -> {
                syncWithAlarm(intent?.getStringExtra(EXTRA_ALARM_JSON).orEmpty(), intent?.getStringExtra(EXTRA_REASON).orEmpty())
                START_STICKY
            }
            ACTION_SWIPE_TEARDOWN -> {
                shutdown(removeNotification = true, reason = "swipe_dismiss")
                START_STICKY
            }
            ACTION_STOP_SERVICE -> {
                shutdown(removeNotification = true, reason = intent?.getStringExtra(EXTRA_REASON).orEmpty())
                START_NOT_STICKY
            }
            else -> {
                val restoredJson = readPersistedAlarmJson()
                if (!restoredJson.isNullOrEmpty()) {
                    Log.i(TAG, "SERVICE_RECREATED — restoring from persisted alarm JSON")
                    syncWithAlarm(restoredJson, "oom_restore")
                } else {
                    shutdown(removeNotification = true, reason = "system_recreate_no_data")
                }
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "SERVICE_LIFECYCLE onDestroy popupType=agenda_pre")
        tickerJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "SERVICE_LIFECYCLE onTaskRemoved popupType=agenda_pre — service continues")
        super.onTaskRemoved(rootIntent)
    }

    private fun persistAlarmJson(alarmJson: String) {
        runCatching {
            getSharedPreferences("agenda_popup_state", Context.MODE_PRIVATE)
                .edit().putString("popup_alarm_json", alarmJson).apply()
        }
    }

    private fun clearPersistedAlarmJson() {
        runCatching {
            getSharedPreferences("agenda_popup_state", Context.MODE_PRIVATE)
                .edit().remove("popup_alarm_json").apply()
        }
    }

    private fun readPersistedAlarmJson(): String? =
        runCatching {
            getSharedPreferences("agenda_popup_state", Context.MODE_PRIVATE)
                .getString("popup_alarm_json", null)
        }.getOrNull()

    private fun syncWithAlarm(alarmJson: String, reason: String) {
        persistAlarmJson(alarmJson)
        if (alarmJson.isEmpty()) {
            shutdown(removeNotification = true, reason = "empty_alarm_json_$reason")
            return
        }
        val alarm = runCatching { Json.decodeFromString<AlarmItem>(alarmJson) }.getOrElse {
            Log.e(TAG, "decode alarm failed", it)
            shutdown(removeNotification = true, reason = "bad_json")
            return
        }

        if (!AgendaAlarmLocalActivationStore.isLocallyEnabled(this, alarm)) {
            shutdown(removeNotification = true, reason = "locally_disabled")
            return
        }

        if (!SettingsManager.getCalendarPopupLast30Min(this)) {
            shutdown(removeNotification = true, reason = "popup_disabled")
            return
        }

        val now = System.currentTimeMillis()
        val windowMs = SettingsManager.getCalendarPopupWindowMinutes(this).coerceAtLeast(1) * 60_000L
        if (alarm.epochMillis <= now) {
            shutdown(removeNotification = true, reason = "alarm_in_past")
            return
        }
        val remainingMs = alarm.epochMillis - now
        if (remainingMs > windowMs) {
            shutdown(removeNotification = true, reason = "outside_popup_window")
            return
        }

        // Er mag maar 1 melding tegelijk zijn voor 1 alarm: deze Stop-knop melding heeft altijd
        // voorrang op de buttonless melding en mag die uitzetten.
        ButtonlessNotificationService.stopNotification(this, "superseded_by_stop_popup")

        LanguageManager.init(applicationContext)
        tickerJob?.cancel()
        NotificationPopupDebugLog.popup(
            source = "AgendaAlarmPopupForegroundService.syncWithAlarm",
            nextAlarmEpoch = alarm.epochMillis,
            displayedTime = "(initial)",
            extra = "reason=$reason alarmId=${alarm.id}",
        )
        val initial = buildNotification(alarm, notificationRebuilt = true)
        PopupNotificationFoundation.startForeground(this, NOTIFICATION_ID, initial)

        tickerJob = scope.launch {
            // Chronometer handles display updates automatically.
            // notify() here re-anchors base on state changes (pause/resume/reset).
            while (isActive) {
                delay(1_000L)
                if (!SettingsManager.getCalendarPopupLast30Min(this@AgendaAlarmPopupForegroundService)) {
                    shutdown(removeNotification = true, reason = "popup_disabled_mid_run")
                    break
                }
                if (!AgendaAlarmLocalActivationStore.isLocallyEnabled(this@AgendaAlarmPopupForegroundService, alarm)) {
                    shutdown(removeNotification = true, reason = "deactivated_elsewhere")
                    break
                }
                val n = System.currentTimeMillis()
                if (alarm.epochMillis <= n) {
                    shutdown(removeNotification = true, reason = "alarm_time_reached")
                    break
                }
                val rem = alarm.epochMillis - n
                if (rem > windowMs) {
                    shutdown(removeNotification = true, reason = "left_window")
                    break
                }
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(alarm, notificationRebuilt = true))
            }
        }
    }

    private fun buildNotification(alarm: AlarmItem, notificationRebuilt: Boolean = false): android.app.Notification {
        val colors = PopupNotificationFoundation.resolveThemeColors(this)
        val generic = LanguageManager.getString("screen_agenda_alarm")
        val eventTitle = alarm.label.trim().takeIf { it.isNotEmpty() }
        val subtitle = eventTitle ?: generic
        val title = AgendaAlarmNotificationCopy.compactCountdownTitle(this, alarm.epochMillis)
        NotificationPopupDebugLog.notification(
            source = "AgendaAlarmPopupForegroundService.buildNotification",
            nextAlarmEpoch = alarm.epochMillis,
            displayedTime = title,
            notificationRebuilt = notificationRebuilt,
            extra = "subtitle=$subtitle frozenAlarmId=${alarm.id}",
        )

        val deactivatePi = buildDeactivateBroadcastPendingIntent(alarm)
        val deletePi = buildDeletePendingIntent()
        val fullScreenPi = buildFullScreenPendingIntent(alarm)
        val remainingMs = chronometerRemainingMsForAlarm(alarm.epochMillis)

        return runCatching {
            val spec = CompactTrayNotificationSpec(
                title = title,
                subtitle = subtitle,
                primaryActionGlyph = null,
                secondaryActionGlyph = "✕",
                primaryAction = null,
                secondaryAction = deactivatePi,
                rootTapAction = null,
                chronometerRemainingMs = remainingMs,
                chronometerCountDown = true,
                chronometerStarted = true,
            )
            val rv = PopupNotificationFoundation.buildPocCompactRemoteViews(this, colors, spec)
            WakeMobilePolicy
                .applyFullScreenIntent(
                    this,
                    PopupNotificationFoundation
                        .alarmTrayCompactBuilder(this, LOCKSCREEN_CHANNEL_ID, colors.backgroundColor)
                        .setOngoing(false)
                        .setCustomContentView(rv)
                        .setDeleteIntent(deletePi)
                        .setContentTitle(title)
                        .setContentText(subtitle),
                    fullScreenPi,
                    "AgendaAlarmPopupForegroundService.customView",
                )
                .build()
        }.getOrElse { err ->
            Log.w(TAG, "CUSTOM_RENDER_FAILED fallback reason=${err.javaClass.simpleName}")
            WakeMobilePolicy
                .applyFullScreenIntent(
                    this,
                    PopupNotificationFoundation
                        .alarmTrayCompactBuilder(this, LOCKSCREEN_CHANNEL_ID, colors.backgroundColor)
                        .setOngoing(false)
                        .setContentTitle(title)
                        .setContentText(subtitle)
                        .setDeleteIntent(deletePi),
                    fullScreenPi,
                    "AgendaAlarmPopupForegroundService.fallback",
                )
                .build()
        }
    }

    private fun buildDeactivateBroadcastPendingIntent(alarm: AlarmItem): PendingIntent {
        val json = Json.encodeToString(AlarmItem.serializer(), alarm)
        val i = Intent(this, AgendaAlarmLocalDeactivateReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_JSON, json)
        }
        val req = (alarm.id xor alarm.epochMillis).toInt()
        return PendingIntent.getBroadcast(
            this,
            req,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildFullScreenPendingIntent(alarm: AlarmItem): PendingIntent {
        val json = Json.encodeToString(AlarmItem.serializer(), alarm)
        val i = Intent(this, AgendaAlarmPopupLockscreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AgendaAlarmPopupLockscreenActivity.EXTRA_ALARM_JSON, json)
        }
        return PendingIntent.getActivity(
            this,
            RC_FULL_SCREEN,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDeletePendingIntent(): PendingIntent {
        val i = Intent(this, AgendaAlarmPopupDismissReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            REQ_DELETE,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun shutdown(removeNotification: Boolean, reason: String) {
        Log.i(TAG, "SERVICE_SHUTDOWN reason=$reason removeNotification=$removeNotification")
        clearPersistedAlarmJson()
        tickerJob?.cancel()
        tickerJob = null
        if (removeNotification) {
            runCatching {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
            }
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    /** Null wanneer de alarmdag na vandaag valt ("Morgen") — dan statische titel. */
    private fun chronometerRemainingMsForAlarm(alarmEpochMillis: Long): Long? {
        val now = System.currentTimeMillis()
        val calAlarm = Calendar.getInstance().apply { timeInMillis = alarmEpochMillis }
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val laterCalendarDay =
            calAlarm.get(Calendar.YEAR) > calNow.get(Calendar.YEAR) ||
                (
                    calAlarm.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                        calAlarm.get(Calendar.DAY_OF_YEAR) > calNow.get(Calendar.DAY_OF_YEAR)
                    )
        if (laterCalendarDay) return null
        return (alarmEpochMillis - now).coerceAtLeast(0L)
    }

    companion object {
        private const val TAG = "AgendaPopupService"
        const val EXTRA_ALARM_JSON = "alarm_json"
        const val EXTRA_REASON = "reason"

        internal const val ACTION_SYNC = "com.dd.daykit.agenda_popup.SYNC"
        internal const val ACTION_SWIPE_TEARDOWN = "com.dd.daykit.agenda_popup.SWIPE_TEARDOWN"
        private const val ACTION_STOP_SERVICE = "com.dd.daykit.agenda_popup.STOP_SERVICE"

        private const val LOCKSCREEN_CHANNEL_ID = PopupNotificationFoundation.CALENDAR_ALARM_CHANNEL_ID
        private const val NOTIFICATION_ID = 90402

        private const val RC_FULL_SCREEN = 6150
        private const val REQ_DELETE = 6103

        fun startWithAlarmJson(context: Context, alarmJson: String, reason: String) {
            val app = context.applicationContext
            val i = Intent(app, AgendaAlarmPopupForegroundService::class.java).apply {
                action = ACTION_SYNC
                putExtra(EXTRA_ALARM_JSON, alarmJson)
                putExtra(EXTRA_REASON, reason)
            }
            runCatching { ContextCompat.startForegroundService(app, i) }
                .onFailure { Log.e(TAG, "startWithAlarmJson failed reason=$reason", it) }
        }

        fun stopPopup(context: Context, reason: String) {
            val app = context.applicationContext
            val i = Intent(app, AgendaAlarmPopupForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
                putExtra(EXTRA_REASON, reason)
            }
            runCatching { app.startService(i) }.onFailure { Log.w(TAG, "stopPopup failed", it) }
        }
    }
}
