package com.dd.daykit

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service for stopwatch popup/notification + lockscreen visibility.
 * Same architecture as [TimerPopupForegroundService] + [AgendaAlarmPopupForegroundService].
 *
 * Actions per user spec:
 * - RUNNING: Pause (left) + Stop (right)
 * - PAUSED: Stop (left) + Resume (right)
 */
class StopwatchPopupForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            PopupNotificationFoundation.ensureStopwatchChannel(this)
            Log.i(TAG, "SERVICE_LIFECYCLE onCreate popupType=stopwatch")
        }.onFailure { error ->
            Log.e(TAG, "SERVICE_LIFECYCLE onCreate failed popupType=stopwatch", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "SERVICE_START action=$action startId=$startId")
        return runCatching {
            when (action) {
                ACTION_SYNC -> {
                    val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
                    Log.i(TAG, "STOPWATCH_NOTIFICATION_START reason=$reason")
                    syncWithStopwatchState(reason)
                    START_STICKY
                }

                ACTION_TOGGLE_PAUSE_RESUME -> {
                    StopwatchStateHolder.init(this)
                    when (StopwatchStateHolder.stopwatchState.value) {
                        StopwatchState.RUNNING -> {
                            Log.i(TAG, "PAUSE_PRESSED popupType=stopwatch")
                            StopwatchStateHolder.pause()
                        }
                        StopwatchState.PAUSED -> {
                            Log.i(TAG, "RESUME_PRESSED popupType=stopwatch")
                            StopwatchStateHolder.resume()
                        }
                        else -> Unit
                    }
                    syncWithStopwatchState("toggle_action")
                    START_STICKY
                }

                ACTION_STOP -> {
                    StopwatchStateHolder.init(this)
                    Log.i(TAG, "STOP_PRESSED popupType=stopwatch")
                    StopwatchStateHolder.stopWithoutSave()
                    shutdownEverything(removeNotification = true, reason = "stop_pressed")
                    START_NOT_STICKY
                }

                ACTION_SWIPE_TEARDOWN -> {
                    Log.i(TAG, "NOTIFICATION_REMOVED popupType=stopwatch")
                    suppressNotificationUi.set(true)
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Exception) {
                    }
                    START_STICKY
                }

                ACTION_STOP_SERVICE -> {
                    shutdownEverything(removeNotification = true, reason = "stop_service_action")
                    START_NOT_STICKY
                }

                else -> {
                    Log.i(TAG, "SERVICE_RECREATED action=${action ?: "null"} — restoring from persisted state")
                    syncWithStopwatchState("system_recreate")
                    START_STICKY
                }
            }
        }.getOrElse { error ->
            Log.e(TAG, "SAFE_MODE popup fatal stage=onStartCommand_${action ?: "null"}", error)
            runCatching { shutdownEverything(removeNotification = true, reason = "fatal_onStartCommand") }
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "SERVICE_LIFECYCLE onDestroy popupType=stopwatch")
        tickerJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "SERVICE_LIFECYCLE onTaskRemoved popupType=stopwatch — service continues")
        super.onTaskRemoved(rootIntent)
    }

    private fun syncWithStopwatchState(reason: String) {
        StopwatchStateHolder.init(this)
        val popupEnabled = StopwatchStateHolder.popupEnabled.value
        val state = StopwatchStateHolder.stopwatchState.value
        Log.i(TAG, "POPUP_SETTING_STATE reason=$reason popupEnabled=$popupEnabled state=$state")

        if (!popupEnabled || (state != StopwatchState.RUNNING && state != StopwatchState.PAUSED)) {
            Log.i(TAG, "POPUP_STARTUP_SKIPPED reason=$reason popupEnabled=$popupEnabled state=$state")
            shutdownEverything(removeNotification = true, reason = "sync_hide_${state.name.lowercase()}_$reason")
            return
        }

        suppressNotificationUi.set(false)
        tickerJob?.cancel()
        val initial = buildStopwatchNotification(StopwatchStateHolder.getElapsedTimeMs(), state)
        PopupNotificationFoundation.startForeground(this, NOTIFICATION_ID, initial)

        tickerJob = scope.launch {
            // Chronometer handles display updates automatically.
            // notify() here re-anchors base on state changes (pause/resume/reset).
            while (isActive) {
                delay(1_000L)
                StopwatchStateHolder.init(this@StopwatchPopupForegroundService)
                val liveState = StopwatchStateHolder.stopwatchState.value
                val livePopupEnabled = StopwatchStateHolder.popupEnabled.value
                if (!livePopupEnabled || (liveState != StopwatchState.RUNNING && liveState != StopwatchState.PAUSED)) {
                    shutdownEverything(removeNotification = true, reason = "ticker_end_${liveState.name.lowercase()}")
                    break
                }

                val elapsed = StopwatchStateHolder.getElapsedTimeMs()
                if (!suppressNotificationUi.get()) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildStopwatchNotification(elapsed, liveState))
                }
            }
        }
    }

    private fun buildStopwatchNotification(
        elapsedMs: Long,
        state: StopwatchState
    ): Notification {
        LanguageManager.init(applicationContext)
        val colors = PopupNotificationFoundation.resolveThemeColors(this)
        val timeText = TimerDisplayFormat.formatMillisCompact(elapsedMs)
        val subtitle = if (state == StopwatchState.PAUSED) {
            "${LanguageManager.getString("screen_stopwatch")} (${LanguageManager.getString("sw_pause")})"
        } else {
            LanguageManager.getString("screen_stopwatch")
        }

        // Actions per spec:
        // RUNNING: Pause (left) + Stop (right)
        // PAUSED: Stop (left) + Resume (right)
        val primaryGlyph: String
        val secondaryGlyph = "✕"
        val primaryPi: PendingIntent
        val secondaryPi: PendingIntent

        if (state == StopwatchState.RUNNING) {
            primaryGlyph = "II"
            primaryPi = buildServiceIntent(ACTION_TOGGLE_PAUSE_RESUME, REQ_CODE_PAUSE_RESUME)
            secondaryPi = buildServiceIntent(ACTION_STOP, REQ_CODE_STOP)
        } else {
            primaryGlyph = "▶"
            primaryPi = buildServiceIntent(ACTION_TOGGLE_PAUSE_RESUME, REQ_CODE_PAUSE_RESUME)
            secondaryPi = buildServiceIntent(ACTION_STOP, REQ_CODE_STOP)
        }

        val deletePi = buildDeletePendingIntent()
        val contentPi = buildContentTapPendingIntent()
        val fullScreenPi = buildFullScreenPendingIntent()

        return runCatching {
            val spec = CompactTrayNotificationSpec(
                title = timeText,
                subtitle = subtitle,
                primaryActionGlyph = primaryGlyph,
                secondaryActionGlyph = secondaryGlyph,
                primaryAction = primaryPi,
                secondaryAction = secondaryPi,
                rootTapAction = contentPi,
                chronometerRemainingMs = elapsedMs.coerceAtLeast(0L),
                chronometerCountDown = false,
                chronometerStarted = state == StopwatchState.RUNNING,
            )
            val compactRv = PopupNotificationFoundation.buildPocCompactRemoteViews(this, colors, spec)

            WakeMobilePolicy
                .applyFullScreenIntent(
                    this,
                    PopupNotificationFoundation
                        .alarmTrayCompactBuilder(this, LOCKSCREEN_CHANNEL_ID, colors.backgroundColor)
                        .setCustomContentView(compactRv)
                        .setContentIntent(contentPi)
                        .setDeleteIntent(deletePi),
                    fullScreenPi,
                    "StopwatchPopupForegroundService.customView",
                )
                .build()
                .also { lastNotification = it }
        }.getOrElse { err ->
            Log.w(TAG, "CUSTOM_RENDER_FAILED fallback reason=${err.javaClass.simpleName}")
            WakeMobilePolicy
                .applyFullScreenIntent(
                    this,
                    PopupNotificationFoundation
                        .alarmTrayCompactBuilder(this, LOCKSCREEN_CHANNEL_ID, colors.backgroundColor)
                        .setContentTitle(timeText)
                        .setContentText(subtitle)
                        .setContentIntent(contentPi)
                        .setDeleteIntent(deletePi),
                    fullScreenPi,
                    "StopwatchPopupForegroundService.fallback",
                )
                .build()
                .also { lastNotification = it }
        }
    }

    private fun buildServiceIntent(action: String, requestCode: Int): PendingIntent {
        val i = Intent(this, StopwatchPopupForegroundService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDeletePendingIntent(): PendingIntent {
        val i = Intent(this, StopwatchPopupDismissReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            REQ_CODE_DELETE,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildContentTapPendingIntent(): PendingIntent {
        val i = Intent(this, NewFeatureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("FROM_NAVIGATION", true)
        }
        return PendingIntent.getActivity(
            this,
            REQ_CODE_CONTENT,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildFullScreenPendingIntent(): PendingIntent {
        val i = Intent(this, StopwatchPopupLockscreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            RC_FULL_SCREEN,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun shutdownEverything(removeNotification: Boolean, reason: String) {
        Log.i(TAG, "SERVICE_LIFECYCLE shutdown popupType=stopwatch reason=$reason removeNotification=$removeNotification")
        tickerJob?.cancel()
        tickerJob = null
        if (removeNotification) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    companion object {
        private const val TAG = "StopwatchPopupService"
        private const val LOCKSCREEN_CHANNEL_ID = PopupNotificationFoundation.STOPWATCH_CHANNEL_ID
        private const val NOTIFICATION_ID = 90501

        private const val ACTION_SYNC = "com.dd.daykit.stopwatch_popup.SYNC"
        private const val ACTION_TOGGLE_PAUSE_RESUME = "com.dd.daykit.stopwatch_popup.TOGGLE_PAUSE_RESUME"
        private const val ACTION_STOP = "com.dd.daykit.stopwatch_popup.STOP"
        internal const val ACTION_SWIPE_TEARDOWN = "com.dd.daykit.stopwatch_popup.SWIPE_TEARDOWN"
        private const val ACTION_STOP_SERVICE = "com.dd.daykit.stopwatch_popup.STOP_SERVICE"

        private const val EXTRA_REASON = "reason"

        private const val REQ_CODE_PAUSE_RESUME = 7101
        private const val REQ_CODE_STOP = 7102
        private const val REQ_CODE_DELETE = 7103
        private const val REQ_CODE_CONTENT = 7104
        private const val RC_FULL_SCREEN = 7150

        private val suppressNotificationUi = AtomicBoolean(false)

        @Volatile
        var lastNotification: android.app.Notification? = null
            private set

        fun repostCachedNotification(context: Context) {
            val n = lastNotification ?: return
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                    .notify(NOTIFICATION_ID, n)
            }
        }

        fun syncFromStopwatchState(context: Context, reason: String) {
            val app = context.applicationContext
            StopwatchStateHolder.init(app)
            val popupEnabled = StopwatchStateHolder.popupEnabled.value
            val state = StopwatchStateHolder.stopwatchState.value
            val shouldRunAsForeground = popupEnabled &&
                (state == StopwatchState.RUNNING || state == StopwatchState.PAUSED)
            Log.i(
                TAG,
                "POPUP_STARTUP_REQUESTED reason=$reason popupEnabled=$popupEnabled state=$state shouldRunAsForeground=$shouldRunAsForeground"
            )

            val i = Intent(app, StopwatchPopupForegroundService::class.java).apply {
                action = ACTION_SYNC
                putExtra(EXTRA_REASON, reason)
            }
            runCatching {
                if (shouldRunAsForeground) {
                    Log.i(TAG, "FOREGROUND_SERVICE_START reason=$reason")
                    ContextCompat.startForegroundService(app, i)
                } else {
                    Log.i(TAG, "POPUP_STARTUP_SKIPPED reason=$reason foregroundCondition=false")
                    app.startService(i)
                }
            }.onFailure { error ->
                Log.e(TAG, "POPUP_RESTORE_STARTUP failed reason=$reason", error)
            }
        }

        fun stopPopup(context: Context, reason: String) {
            Log.i(TAG, "SERVICE_LIFECYCLE stopPopup reason=$reason popupType=stopwatch")
            val app = context.applicationContext
            val i = Intent(app, StopwatchPopupForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
                putExtra(EXTRA_REASON, reason)
            }
            try {
                app.startService(i)
            } catch (e: Exception) {
                Log.w(TAG, "stopPopup failed reason=$reason", e)
            }
        }
    }
}
