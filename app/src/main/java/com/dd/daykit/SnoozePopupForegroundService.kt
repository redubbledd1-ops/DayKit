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

/**
 * Compacte tray tijdens agenda-sluiten, zelfde layout als [TimerPopupForegroundService] /
 * [AgendaAlarmPopupForegroundService].
 */
class SnoozePopupForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            PopupNotificationFoundation.ensureCalendarAlarmChannel(this)
            Log.i(TAG, "SERVICE_LIFECYCLE onCreate popupType=snooze")
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
                syncWithSnoozeState(intent?.getStringExtra(EXTRA_REASON).orEmpty())
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
                Log.i(TAG, "SERVICE_RECREATED action=${action ?: "null"} — restoring from snooze store")
                syncWithSnoozeState("system_recreate")
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "SERVICE_LIFECYCLE onDestroy popupType=snooze")
        tickerJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "SERVICE_LIFECYCLE onTaskRemoved popupType=snooze — service continues")
        super.onTaskRemoved(rootIntent)
    }

    private fun syncWithSnoozeState(reason: String) {
        val app = applicationContext
        val end = AgendaSnoozeStore.readEndMillis(app)
        if (end <= 0L) {
            shutdown(removeNotification = true, reason = "no_snooze_state_$reason")
            return
        }
        val now = System.currentTimeMillis()
        if (end <= now) {
            shutdown(removeNotification = true, reason = "snooze_expired_$reason")
            return
        }

        LanguageManager.init(app)
        tickerJob?.cancel()
        NotificationPopupDebugLog.popup(
            source = "SnoozePopupForegroundService.syncWithSnoozeState",
            nextAlarmEpoch = end,
            displayedTime = "(initial)",
            extra = "reason=$reason",
        )
        val initial = buildNotification(end, notificationRebuilt = true)
        PopupNotificationFoundation.startForeground(this, NOTIFICATION_ID, initial)

        tickerJob = scope.launch {
            // Chronometer handles display updates automatically.
            // notify() here re-anchors base on state changes (pause/resume/reset).
            while (isActive) {
                delay(1_000L)
                val e = AgendaSnoozeStore.readEndMillis(this@SnoozePopupForegroundService)
                val n = System.currentTimeMillis()
                if (e <= 0L || e <= n) {
                    shutdown(removeNotification = true, reason = "ticker_snooze_done")
                    break
                }
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(e, notificationRebuilt = true))
            }
        }
    }

    private fun buildNotification(snoozeEndMillis: Long, notificationRebuilt: Boolean = false): android.app.Notification {
        val colors = PopupNotificationFoundation.resolveThemeColors(this)
        val json = AgendaSnoozeStore.readAlarmJson(this)
        val eventTitle = json?.let { j ->
            runCatching { Json.decodeFromString<AlarmItem>(j).label.trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
        }
        val subtitle = eventTitle ?: LanguageManager.getString("alarm_snooze")
        val title = AgendaAlarmNotificationCopy.compactCountdownTitle(this, snoozeEndMillis)
        NotificationPopupDebugLog.notification(
            source = "SnoozePopupForegroundService.buildNotification",
            nextAlarmEpoch = snoozeEndMillis,
            displayedTime = title,
            notificationRebuilt = notificationRebuilt,
            extra = "subtitle=$subtitle storeEnd=${AgendaSnoozeStore.readEndMillis(this)}",
        )

        val cancelSnoozePi = buildCancelSnoozePendingIntent()
        val remainingMs = (snoozeEndMillis - System.currentTimeMillis()).coerceAtLeast(0L)

        return runCatching {
            val spec = CompactTrayNotificationSpec(
                title = title,
                subtitle = subtitle,
                primaryActionGlyph = null,
                secondaryActionGlyph = "✕",
                primaryAction = null,
                secondaryAction = cancelSnoozePi,
                rootTapAction = null,
                chronometerRemainingMs = remainingMs,
                chronometerCountDown = true,
                chronometerStarted = true,
            )
            val rv = PopupNotificationFoundation.buildPocCompactRemoteViews(this, colors, spec)
            PopupNotificationFoundation
                .pocCompactStyleBuilder(this, CHANNEL_ID, colors.backgroundColor)
                .setCustomContentView(rv)
                .setDeleteIntent(buildDeletePendingIntent())
                .setContentTitle(title)
                .setContentText(subtitle)
                .build()
        }.getOrElse { err ->
            Log.w(TAG, "CUSTOM_RENDER_FAILED fallback reason=${err.javaClass.simpleName}")
            PopupNotificationFoundation
                .pocCompactStyleBuilder(this, CHANNEL_ID, colors.backgroundColor)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setDeleteIntent(buildDeletePendingIntent())
                .build()
        }
    }

    private fun buildCancelSnoozePendingIntent(): PendingIntent {
        val i = Intent(this, SnoozePopupCancelReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            REQ_CANCEL_SNOOZE,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDeletePendingIntent(): PendingIntent {
        val i = Intent(this, SnoozePopupDismissReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            REQ_DELETE,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun shutdown(removeNotification: Boolean, reason: String) {
        Log.i(TAG, "SERVICE_SHUTDOWN reason=$reason removeNotification=$removeNotification")
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

    companion object {
        private const val TAG = "SnoozePopupService"
        const val EXTRA_REASON = "reason"

        internal const val ACTION_SYNC = "com.dd.daykit.snooze_popup.SYNC"
        internal const val ACTION_SWIPE_TEARDOWN = "com.dd.daykit.snooze_popup.SWIPE_TEARDOWN"
        private const val ACTION_STOP_SERVICE = "com.dd.daykit.snooze_popup.STOP_SERVICE"

        private const val CHANNEL_ID = PopupNotificationFoundation.CALENDAR_ALARM_CHANNEL_ID
        private const val NOTIFICATION_ID = 90406

        private const val REQ_DELETE = 6202
        private const val REQ_CANCEL_SNOOZE = 6203

        fun startOrSync(context: Context, reason: String) {
            val app = context.applicationContext
            val end = AgendaSnoozeStore.readEndMillis(app)
            if (end <= System.currentTimeMillis() || end <= 0L) {
                Log.d(TAG, "startOrSync skipped reason=$reason (no active snooze window)")
                return
            }
            val i = Intent(app, SnoozePopupForegroundService::class.java).apply {
                action = ACTION_SYNC
                putExtra(EXTRA_REASON, reason)
            }
            runCatching { ContextCompat.startForegroundService(app, i) }
                .onFailure { Log.e(TAG, "startOrSync failed reason=$reason", it) }
        }

        fun stopPopup(context: Context, reason: String) {
            val app = context.applicationContext
            val i = Intent(app, SnoozePopupForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
                putExtra(EXTRA_REASON, reason)
            }
            runCatching { app.startService(i) }.onFailure { Log.w(TAG, "stopPopup failed", it) }
        }
    }
}
