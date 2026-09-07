package com.dd.daykit

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class TimerPopupForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var tickerJob: Job? = null

    /** Indices ([ExtraTimerManager.timers]) waarvoor momenteel een losse notificatie getoond wordt. */
    private val shownExtraNotificationIndices = mutableSetOf<Int>()

    /** Extra slots waarvan de notificatie via een eigen FGS-host draait (voor setColorized). */
    private val extraFgsHostedIndices = mutableSetOf<Int>()

    /** Extra slots die terugvielen op gewone notify() na een mislukte FGS-start. */
    private val extraFallbackIndices = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            PopupNotificationFoundation.ensureTimerChannel(this)
            Log.i(TAG, "SERVICE_LIFECYCLE onCreate popupType=timer")
        }.onFailure { error ->
            Log.e(TAG, "SERVICE_LIFECYCLE onCreate failed popupType=timer", error)
            TimerSettingsStateHolder.suppressPopupRuntime("service_oncreate_failure", error)
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
                Log.i(TAG, "TIMER_NOTIFICATION_START popupType=timer reason=$reason")
                syncWithTimerState(reason)
                START_STICKY
            }

            ACTION_TOGGLE_PAUSE_RESUME -> {
                GlobalTimerManager.init(this)
                when (GlobalTimerManager.getCurrentState()) {
                    GlobalTimerManager.TimerState.RUNNING -> {
                        Log.i(TAG, "PAUSE_PRESSED popupType=timer")
                        GlobalTimerManager.pauseTimer()
                    }
                    GlobalTimerManager.TimerState.PAUSED -> {
                        Log.i(TAG, "RESUME_PRESSED popupType=timer")
                        GlobalTimerManager.resumeTimer(this)
                    }
                    else -> Unit
                }
                syncWithTimerState("toggle_action")
                START_STICKY
            }

            ACTION_STOP_AND_RESET -> {
                GlobalTimerManager.init(this)
                Log.i(TAG, "X_PRESSED popupType=timer")
                GlobalTimerManager.resetTimer(this)
                shutdownEverything(removeNotification = true, reason = "x_pressed")
                START_NOT_STICKY
            }

            ACTION_SWIPE_TEARDOWN -> {
                Log.i(TAG, "NOTIFICATION_REMOVED popupType=timer")
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

            ACTION_EXTRA_TOGGLE_PAUSE_RESUME -> {
                val index = intent?.getIntExtra(EXTRA_SLOT_INDEX, -1) ?: -1
                ExtraTimerManager.init(this)
                ExtraTimerManager.timers.getOrNull(index)?.let { slot ->
                    when (slot.state) {
                        GlobalTimerManager.TimerState.RUNNING -> {
                            Log.i(TAG, "EXTRA_PAUSE_PRESSED popupType=timer index=$index")
                            ExtraTimerManager.pauseTimer(this, slot)
                        }
                        GlobalTimerManager.TimerState.PAUSED -> {
                            Log.i(TAG, "EXTRA_RESUME_PRESSED popupType=timer index=$index")
                            ExtraTimerManager.resumeTimer(this, slot)
                        }
                        else -> Unit
                    }
                }
                syncWithTimerState("extra_toggle_action")
                START_STICKY
            }

            ACTION_EXTRA_STOP -> {
                val index = intent?.getIntExtra(EXTRA_SLOT_INDEX, -1) ?: -1
                ExtraTimerManager.init(this)
                ExtraTimerManager.timers.getOrNull(index)?.let { slot ->
                    Log.i(TAG, "EXTRA_STOP_PRESSED popupType=timer index=$index")
                    ExtraTimerManager.removeTimer(this, slot)
                }
                syncWithTimerState("extra_stop_action")
                START_STICKY
            }

            ACTION_EXTRA_FINISHED -> {
                val slotId = intent?.getStringExtra(TimerFinishedAlarmUi.EXTRA_SLOT_ID)
                ExtraTimerManager.init(this)
                val slot = slotId?.let { id -> ExtraTimerManager.findById(id) }
                val index = slot?.let { ExtraTimerManager.timers.indexOf(it) } ?: -1
                if (slot != null && index >= 0) {
                    Log.i(TAG, "EXTRA_FINISHED popupType=timer slot=$slotId index=$index")
                    runCatching {
                        TimerFinishedAlarmUi.ensureChannel(this, index)
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(
                            TimerFinishedAlarmUi.extraNotificationId(index),
                            TimerFinishedAlarmUi.buildTimerFinishedNotification(this, slot.id, index)
                        )
                    }.onFailure { Log.e(TAG, "EXTRA_FINISHED_NOTIFICATION_FAILED slot=$slotId", it) }
                    runCatching {
                        val activityIntent = Intent(this, TimerFinishedActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                            putExtra(TimerFinishedAlarmUi.EXTRA_SLOT_ID, slot.id)
                            putExtra(TimerFinishedAlarmUi.EXTRA_SLOT_INDEX, index)
                        }
                        startActivity(activityIntent)
                        Log.i(TAG, "EXTRA_FINISHED_ACTIVITY_LAUNCHED slot=$slotId")
                    }.onFailure { Log.w(TAG, "EXTRA_FINISHED_ACTIVITY_LAUNCH_FAILED slot=$slotId (possible BAL block)", it) }
                } else {
                    Log.w(TAG, "EXTRA_FINISHED ignored — slot not found slot=$slotId")
                }
                syncWithTimerState("extra_finished")
                START_STICKY
            }
                else -> {
                    Log.i(TAG, "SERVICE_RECREATED action=${action ?: "null"} — restoring from persisted state")
                    syncWithTimerState("system_recreate")
                    START_STICKY
                }
            }
        }.getOrElse { error ->
            handleFatalPopupFailure("onStartCommand_${action ?: "null"}", error)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "SERVICE_LIFECYCLE onDestroy popupType=timer")
        tickerJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "SERVICE_LIFECYCLE onTaskRemoved popupType=timer — service continues")
        super.onTaskRemoved(rootIntent)
    }

    private fun hasAnyActiveTimer(): Boolean {
        val primaryState = GlobalTimerManager.getCurrentState()
        val primaryActive = primaryState == GlobalTimerManager.TimerState.RUNNING || primaryState == GlobalTimerManager.TimerState.PAUSED
        return primaryActive || ExtraTimerManager.hasActiveTimers()
    }

    private fun syncWithTimerState(reason: String) {
        GlobalTimerManager.init(this)
        ExtraTimerManager.init(this)
        TimerSettingsStateHolder.init(this)
        val popupEnabled = TimerSettingsStateHolder.popupEnabled.value
        val popupAllowed = TimerSettingsStateHolder.isPopupStartupAllowed()
        val state = GlobalTimerManager.getCurrentState()
        Log.i(TAG, "POPUP_SETTING_STATE reason=$reason popupEnabled=$popupEnabled popupAllowed=$popupAllowed state=$state extraActive=${ExtraTimerManager.hasActiveTimers()}")

        if (!popupAllowed || !hasAnyActiveTimer()) {
            Log.i(TAG, "POPUP_STARTUP_SKIPPED reason=$reason popupAllowed=$popupAllowed state=$state")
            shutdownEverything(removeNotification = true, reason = "sync_hide_${state.name.lowercase()}_$reason")
            return
        }

        suppressNotificationUi.set(false)
        tickerJob?.cancel()
        val initial = buildTimerNotification(GlobalTimerManager.getRemainingTimeMs(), state)
        PopupNotificationFoundation.startForeground(this, NOTIFICATION_ID, initial)
        syncExtraTimerNotifications()

        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                GlobalTimerManager.init(this@TimerPopupForegroundService)
                ExtraTimerManager.init(this@TimerPopupForegroundService)
                val liveState = GlobalTimerManager.getCurrentState()
                val livePopupAllowed = TimerSettingsStateHolder.isPopupStartupAllowed()
                if (!livePopupAllowed || !hasAnyActiveTimer()) {
                    shutdownEverything(removeNotification = true, reason = "ticker_end_${liveState.name.lowercase()}")
                    break
                }

                val remaining = GlobalTimerManager.getRemainingTimeMs()
                Log.d(TAG, "REALTIME_UPDATE_TICK popupType=timer remainingMs=$remaining state=$liveState")
                if (!suppressNotificationUi.get()) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    Log.d(TAG, "NOTIFICATION_NOTIFY popupType=timer remainingMs=$remaining")
                    nm.notify(NOTIFICATION_ID, buildTimerNotification(remaining, liveState))
                }
                syncExtraTimerNotifications()
            }
        }
    }

    /**
     * Eigen, losse notificatie per lopende/gepauzeerde extra timer (2e/3e) — naast de primaire
     * timer-notificatie, niet erin samengevoegd. Notificatie-id en request-codes zijn afgeleid
     * van de index in [ExtraTimerManager.timers] (max [ExtraTimerManager.MAX_EXTRA_TIMERS] stuks).
     */
    private fun syncExtraTimerNotifications() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val compatNm = NotificationManagerCompat.from(this)
        val liveIndices = mutableSetOf<Int>()

        ExtraTimerManager.timers.forEachIndexed { index, slot ->
            val shouldShow = slot.state == GlobalTimerManager.TimerState.RUNNING ||
                slot.state == GlobalTimerManager.TimerState.PAUSED
            if (!shouldShow) return@forEachIndexed
            liveIndices += index
            val notification = ExtraTimerPopupNotifications.build(this, slot, index)
            val id = ExtraTimerPopupNotifications.notificationId(index)

            when {
                index in extraFgsHostedIndices -> {
                    compatNm.notify(id, notification)
                }
                index in extraFallbackIndices -> {
                    nm.notify(id, notification)
                }
                else -> {
                    try {
                        ExtraTimerForegroundServiceBase.start(this, index)
                        extraFgsHostedIndices += index
                        Log.i(TAG, "EXTRA_FGS_START index=$index notificationId=$id")
                    } catch (error: Exception) {
                        Log.w(TAG, "EXTRA_FGS_START_FAILED index=$index fallback=notify", error)
                        extraFallbackIndices += index
                        nm.notify(id, notification)
                    }
                }
            }
        }

        val previouslyVisible = (
            shownExtraNotificationIndices +
                extraFgsHostedIndices +
                extraFallbackIndices
            ).toSet()
        for (index in previouslyVisible) {
            if (index !in liveIndices) {
                if (index in extraFgsHostedIndices) {
                    ExtraTimerForegroundServiceBase.stop(this, index)
                    extraFgsHostedIndices.remove(index)
                    Log.i(TAG, "EXTRA_FGS_STOP index=$index")
                }
                extraFallbackIndices.remove(index)
                nm.cancel(ExtraTimerPopupNotifications.notificationId(index))
            }
        }
        shownExtraNotificationIndices.clear()
        shownExtraNotificationIndices.addAll(liveIndices)
    }

    private fun buildExtraServiceIntent(action: String, requestCode: Int, index: Int): PendingIntent {
        val i = Intent(this, TimerPopupForegroundService::class.java).apply {
            this.action = action
            putExtra(EXTRA_SLOT_INDEX, index)
        }
        return PendingIntent.getService(
            this,
            requestCode,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildTimerNotification(
        remainingMs: Long,
        state: GlobalTimerManager.TimerState
    ): android.app.Notification {
        Log.d(TAG, "NOTIFICATION_BUILD begin state=$state remainingMs=$remainingMs")
        LanguageManager.init(applicationContext)
        val colors = PopupNotificationFoundation.resolveThemeColors(this)
        val timeText = TimerDisplayFormat.formatMillisCompact(remainingMs)
        val name = GlobalTimerManager.timerName.value
        val subtitle = name.ifBlank { LanguageManager.getString("screen_timer") }
        val pauseGlyph = if (state == GlobalTimerManager.TimerState.RUNNING) "II" else "▶"
        val pausePi = buildServiceIntent(ACTION_TOGGLE_PAUSE_RESUME, REQ_CODE_PAUSE_RESUME)
        val stopPi = buildServiceIntent(ACTION_STOP_AND_RESET, REQ_CODE_STOP)
        val deletePi = buildDeletePendingIntent()
        val contentPi = buildContentTapPendingIntent()

        return runCatching {
            val spec = CompactTrayNotificationSpec(
                title = timeText,
                subtitle = subtitle,
                primaryActionGlyph = pauseGlyph,
                secondaryActionGlyph = "✕",
                primaryAction = pausePi,
                secondaryAction = stopPi,
                rootTapAction = contentPi,
                chronometerRemainingMs = remainingMs.coerceAtLeast(0L),
                chronometerCountDown = true,
                chronometerStarted = state == GlobalTimerManager.TimerState.RUNNING,
            )
            val compactRv = PopupNotificationFoundation.buildPocCompactRemoteViews(this, colors, spec)

            PopupNotificationFoundation
                .alarmTrayCompactBuilder(this, CHANNEL_ID, colors.backgroundColor)
                .setCustomContentView(compactRv)
                .setContentIntent(contentPi)
                .setDeleteIntent(deletePi)
                // Zie toelichting in buildExtraTimerNotification: eigen group-key voorkomt
                // dat Android/MIUI deze samen met de extra-timer-meldingen bundelt, wat een
                // grijze systeem-header boven onze zwarte achtergrond zou opleveren.
                .setGroup("timer_notif_primary")
                .build()
                .also { lastNotification = it }
        }.getOrElse { err ->
            Log.w(TAG, "CUSTOM_RENDER_FAILED fallback=system_compact reason=${err.javaClass.simpleName}")
            PopupNotificationFoundation
                .alarmTrayCompactBuilder(this, CHANNEL_ID, colors.backgroundColor)
                .setContentTitle(timeText)
                .setContentText(subtitle)
                .addAction(android.R.drawable.ic_media_pause, pauseGlyph, pausePi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "X", stopPi)
                .setContentIntent(contentPi)
                .setDeleteIntent(deletePi)
                .setGroup("timer_notif_primary")
                .build()
                .also { lastNotification = it }
        }
    }

    private fun buildServiceIntent(action: String, requestCode: Int): PendingIntent {
        val i = Intent(this, TimerPopupForegroundService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDeletePendingIntent(): PendingIntent {
        val i = Intent(this, TimerPopupDismissReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            REQ_CODE_DELETE,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildContentTapPendingIntent(): PendingIntent {
        val i = Intent(this, TimerActivity::class.java).apply {
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

    private fun shutdownEverything(removeNotification: Boolean, reason: String) {
        Log.i(TAG, "SERVICE_LIFECYCLE shutdown popupType=timer reason=$reason removeNotification=$removeNotification")
        tickerJob?.cancel()
        tickerJob = null
        if (removeNotification) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
            for (index in extraFgsHostedIndices.toList()) {
                ExtraTimerForegroundServiceBase.stop(this, index)
            }
            extraFgsHostedIndices.clear()
            extraFallbackIndices.clear()
            for (index in shownExtraNotificationIndices) {
                nm.cancel(ExtraTimerPopupNotifications.notificationId(index))
            }
            shownExtraNotificationIndices.clear()
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    companion object {
        private const val TAG = "TimerPopupService"
        private const val CHANNEL_ID = PopupNotificationFoundation.TIMER_CHANNEL_ID
        private const val NOTIFICATION_ID = 9011

        private const val ACTION_SYNC = "com.dd.daykit.timer_popup.SYNC"
        private const val ACTION_TOGGLE_PAUSE_RESUME = "com.dd.daykit.timer_popup.TOGGLE_PAUSE_RESUME"
        private const val ACTION_STOP_AND_RESET = "com.dd.daykit.timer_popup.STOP_AND_RESET"
        internal const val ACTION_SWIPE_TEARDOWN = "com.dd.daykit.timer_popup.SWIPE_TEARDOWN"
        private const val ACTION_STOP_SERVICE = "com.dd.daykit.timer_popup.STOP_SERVICE"
        internal const val ACTION_EXTRA_TOGGLE_PAUSE_RESUME =
            "com.dd.daykit.timer_popup.EXTRA_TOGGLE_PAUSE_RESUME"
        internal const val ACTION_EXTRA_STOP = "com.dd.daykit.timer_popup.EXTRA_STOP"

        /** Getriggerd door [ExtraTimerManager] zodra een 2e/3e timer afloopt. */
        const val ACTION_EXTRA_FINISHED = "com.dd.daykit.timer_popup.EXTRA_FINISHED"

        private const val EXTRA_REASON = "reason"
        internal const val EXTRA_SLOT_INDEX = "slot_index"

        private const val REQ_CODE_PAUSE_RESUME = 5101
        private const val REQ_CODE_STOP = 5102
        private const val REQ_CODE_DELETE = 5103
        private const val REQ_CODE_CONTENT = 5104
        // + index (max ExtraTimerManager.MAX_EXTRA_TIMERS slots) — blijft ver uit de buurt van de codes hierboven.
        private const val REQ_CODE_EXTRA_PAUSE_BASE = 5110
        private const val REQ_CODE_EXTRA_STOP_BASE = 5120

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

        fun syncFromTimerState(context: Context, reason: String) {
            val app = context.applicationContext
            TimerSettingsStateHolder.init(app)
            ExtraTimerManager.init(app)
            val popupEnabled = TimerSettingsStateHolder.popupEnabled.value
            val popupAllowed = TimerSettingsStateHolder.isPopupStartupAllowed()
            val timerPrefs = app.getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE)
            val state = timerPrefs.getString("timer_state", GlobalTimerManager.TimerState.IDLE.name)
            val primaryActive = state == GlobalTimerManager.TimerState.RUNNING.name || state == GlobalTimerManager.TimerState.PAUSED.name
            val shouldRunAsForeground = popupAllowed && (primaryActive || ExtraTimerManager.hasActiveTimers())
            Log.i(
                TAG,
                "POPUP_STARTUP_REQUESTED reason=$reason popupEnabled=$popupEnabled popupAllowed=$popupAllowed persistedState=$state shouldRunAsForeground=$shouldRunAsForeground"
            )

            val i = Intent(app, TimerPopupForegroundService::class.java).apply {
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
                TimerSettingsStateHolder.suppressPopupRuntime("sync_start_failure_$reason", error)
            }
        }

        fun stopPopup(context: Context, reason: String) {
            Log.i(TAG, "SERVICE_LIFECYCLE stopPopup reason=$reason popupType=timer")
            val app = context.applicationContext
            val i = Intent(app, TimerPopupForegroundService::class.java).apply {
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

    private fun handleFatalPopupFailure(stage: String, error: Throwable) {
        Log.e(TAG, "SAFE_MODE popup fatal stage=$stage", error)
        TimerSettingsStateHolder.suppressPopupRuntime("service_fatal_$stage", error)
        runCatching { shutdownEverything(removeNotification = true, reason = "fatal_$stage") }
    }
}
