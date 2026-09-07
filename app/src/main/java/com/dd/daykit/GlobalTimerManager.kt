package com.dd.daykit

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Global manager for timer state that persists across activity recreations AND app restarts.
 * This is the SINGLE source of truth for timer state - similar to StopwatchStateHolder.
 * All timer operations MUST go through this manager.
 * 
 * Timer persists by saving end time to SharedPreferences and scheduling an AlarmManager alarm.
 * When app reopens, it calculates remaining time from saved end time.
 */
object GlobalTimerManager {
    
    private const val TAG = "GlobalTimerManager"
    private const val PREFS_NAME = "TimerPrefs"
    private const val KEY_END_TIME = "timer_end_time"
    private const val KEY_INITIAL_TIME = "timer_initial_time"
    private const val KEY_STATE = "timer_state"
    private const val KEY_PAUSED_REMAINING = "timer_paused_remaining"
    private const val KEY_TIMER_NAME = "timer_name"
    private const val TIMER_ALARM_REQUEST_CODE = 9999
    
    enum class TimerState {
        IDLE, RUNNING, PAUSED, FINISHED
    }
    
    val timerState = mutableStateOf(TimerState.IDLE)
    val timeMillis = mutableStateOf(0L)
    val initialTimeMillis = mutableStateOf(0L)
    val timerName = mutableStateOf("")
    val showAlarmDialog = mutableStateOf(false)
    
    // Absolute end time - this is the source of truth for running timers
    // Public so GlobalInAppMessageManager can calculate remaining time directly
    var endTimeMs: Long = 0L
        private set
    
    // Single source of truth: is timer alarm currently playing?
    // This flag prevents duplicate alarm triggers from multiple entry points
    @Volatile
    var isAlarmPlaying = false
        private set
    
    /** Set alarm playing state. Called by [TimeEngineService] only. */
    fun setAlarmPlaying(playing: Boolean) {
        isAlarmPlaying = playing
        Log.d(TAG, "Alarm playing state set to: $playing")
    }
    
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var appContext: Context? = null
    private var isInitialized = false

    private fun syncTimerPopup(reason: String) {
        val ctx = appContext ?: return
        runCatching {
            TimerSettingsStateHolder.init(ctx)
            val state = timerState.value
            if (
                TimerSettingsStateHolder.popupEnabled.value &&
                (state == TimerState.RUNNING || state == TimerState.PAUSED)
            ) {
                // Recover from previous runtime suppression once user explicitly runs a timer again.
                TimerSettingsStateHolder.clearPopupRuntimeSuppression("active_timer_$reason")
            }
            Log.i(
                TAG,
                "POPUP_RESTORE_SYNC reason=$reason popupEnabled=${TimerSettingsStateHolder.popupEnabled.value} popupAllowed=${TimerSettingsStateHolder.isPopupStartupAllowed()} state=$state"
            )
            if (TimerSettingsStateHolder.isPopupStartupAllowed()) {
                TimerPopupForegroundService.syncFromTimerState(ctx, reason)
            } else {
                TimerPopupForegroundService.stopPopup(ctx, "${reason}_disabled")
            }
        }.onFailure { error ->
            Log.e(TAG, "POPUP_RESTORE_SYNC_FAILED reason=$reason", error)
            TimerSettingsStateHolder.suppressPopupRuntime("global_sync_failure_$reason", error)
            runCatching { TimerPopupForegroundService.stopPopup(ctx, "global_sync_failure_stop_$reason") }
        }
    }

    /**
     * Get remaining time calculated from absolute end time.
     * This is the correct way to get remaining time - always accurate even after app restart.
     */
    fun getRemainingTimeMs(): Long {
        return when (timerState.value) {
            TimerState.RUNNING -> maxOf(0L, endTimeMs - System.currentTimeMillis())
            TimerState.PAUSED -> timeMillis.value
            else -> 0L
        }
    }
    
    fun init(context: Context) {
        Log.i(TAG, "INIT_STARTUP begin isInitialized=$isInitialized")
        if (isInitialized && appContext != null) {
            // Already initialized - don't restore state again as ticker may be running
            Log.i(TAG, "INIT_STARTUP skip already initialized")
            return
        }
        appContext = context.applicationContext
        isInitialized = true
        runCatching {
            restoreState(context)
            Log.i(TAG, "INIT_STARTUP restore complete state=${timerState.value}")
        }.onFailure { error ->
            Log.e(TAG, "INIT_STARTUP restore failed; forcing safe idle", error)
            timerState.value = TimerState.IDLE
            timeMillis.value = 0L
            endTimeMs = 0L
            showAlarmDialog.value = false
            TimerSettingsStateHolder.suppressPopupRuntime("init_restore_failure", error)
            runCatching { saveState(context.applicationContext) }
            runCatching { TimerPopupForegroundService.stopPopup(context.applicationContext, "init_restore_failure") }
        }
    }
    
    private fun restoreState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedState = prefs.getString(KEY_STATE, TimerState.IDLE.name)
        val savedEndTime = prefs.getLong(KEY_END_TIME, 0L)
        val savedInitialTime = prefs.getLong(KEY_INITIAL_TIME, 0L)
        val savedPausedRemaining = prefs.getLong(KEY_PAUSED_REMAINING, 0L)

        initialTimeMillis.value = savedInitialTime
        timerName.value = prefs.getString(KEY_TIMER_NAME, "") ?: ""
        
        when (savedState) {
            TimerState.RUNNING.name -> {
                val now = System.currentTimeMillis()
                if (savedEndTime > now) {
                    // Timer still running, calculate remaining time
                    endTimeMs = savedEndTime
                    timeMillis.value = savedEndTime - now
                    timerState.value = TimerState.RUNNING
                    startTicker(context)
                    TimerSettingsStateHolder.init(context.applicationContext)
                    syncTimerPopup("restore_running")
                    Log.d(TAG, "Restored running timer with ${timeMillis.value}ms remaining")
                } else if (savedEndTime > 0) {
                    // Timer finished while app was closed
                    // Update state first, then delegate alarm to service (if not already playing)
                    timeMillis.value = 0L
                    timerState.value = TimerState.FINISHED
                    showAlarmDialog.value = false
                    saveState(context)
                    
                    // Only start alarm if not already playing (idempotent)
                    if (!isAlarmPlaying && !TimeEngineService.isRunning) {
                        Log.d(TAG, "Timer finished while app was closed - starting alarm service")
                        val started = runCatching { TimeEngineService.startTimerAlarm(context) }.isSuccess
                        if (!started) {
                            Log.w(TAG, "Timer finished while app was closed - startTimerAlarm blocked (background FGS restriction)")
                            val app = context.applicationContext
                            TimerFinishedAlarmUi.ensureChannel(app)
                            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                                TimerFinishedAlarmUi.NOTIFICATION_ID,
                                TimerFinishedAlarmUi.buildTimerFinishedNotification(app)
                            )
                        }
                    } else {
                        Log.d(TAG, "Timer finished while app was closed - alarm already playing")
                    }
                    syncTimerPopup("restore_finished")
                }
            }
            TimerState.PAUSED.name -> {
                timeMillis.value = savedPausedRemaining
                timerState.value = TimerState.PAUSED
                syncTimerPopup("restore_paused")
                Log.d(TAG, "Restored paused timer with ${timeMillis.value}ms remaining")
            }
            TimerState.FINISHED.name -> {
                timeMillis.value = 0L
                timerState.value = TimerState.FINISHED
                showAlarmDialog.value = false
                syncTimerPopup("restore_finished_state")
                val app = context.applicationContext
                TimerFinishedAlarmUi.ensureChannel(app)
                (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                    TimerFinishedAlarmUi.NOTIFICATION_ID,
                    TimerFinishedAlarmUi.buildTimerFinishedNotification(app)
                )
            }
            else -> {
                timerState.value = TimerState.IDLE
                timeMillis.value = savedInitialTime
                syncTimerPopup("restore_idle")
            }
        }
    }
    
    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_STATE, timerState.value.name)
            putLong(KEY_END_TIME, endTimeMs)
            putLong(KEY_INITIAL_TIME, initialTimeMillis.value)
            putLong(KEY_PAUSED_REMAINING, if (timerState.value == TimerState.PAUSED) timeMillis.value else 0L)
            putString(KEY_TIMER_NAME, timerName.value)
            apply()
        }
    }
    
    fun startTimer(context: Context, duration: Long, name: String = "") {
        if (duration <= 0) return
        appContext = context.applicationContext
        TimerFinishedAlarmUi.cancel(context.applicationContext)

        timerName.value = name
        initialTimeMillis.value = duration
        TimerSettingsStateHolder.lastTimeMillis.value = duration
        TimerSettingsStateHolder.save(context, sync = true)
        
        // Calculate end time and save it
        endTimeMs = System.currentTimeMillis() + duration
        timeMillis.value = duration
        timerState.value = TimerState.RUNNING
        
        // Schedule system alarm for when timer finishes (works even if app is killed)
        scheduleTimerAlarm(context, endTimeMs)

        // Start ticker for UI updates
        startTicker(context)
        
        saveState(context)
        syncTimerPopup("start_timer")
        Log.d(TAG, "Started timer for ${duration}ms, end time: $endTimeMs")
    }
    
    private fun startTicker(context: Context) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (timerState.value == TimerState.RUNNING) {
                val now = System.currentTimeMillis()
                val remaining = endTimeMs - now

                if (remaining <= 0) {
                    timeMillis.value = 0L
                    timerState.value = TimerState.FINISHED
                    showAlarmDialog.value = false
                    saveState(context)

                    if (!isAlarmPlaying && !TimeEngineService.isRunning) {
                        Log.d(TAG, "Ticker finished - starting alarm service")
                        val started = runCatching { TimeEngineService.startTimerAlarm(context) }.isSuccess
                        if (started) {
                            cancelTimerAlarm(context) // backup no longer needed
                        } else {
                            Log.w(TAG, "startTimerAlarm blocked (background FGS restriction) — leaving AlarmManager backup scheduled, TimerAlarmReceiver will fire with exemption")
                        }
                    } else {
                        Log.d(TAG, "Ticker finished - alarm already playing, skipping")
                    }
                    syncTimerPopup("ticker_finished")
                    break
                }

                timeMillis.value = remaining
                delay(100L)
            }
        }
    }
    
    private fun scheduleTimerAlarm(context: Context, triggerAtMs: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                TIMER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val showIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val showPi = PendingIntent.getActivity(
                context,
                TIMER_ALARM_REQUEST_CODE,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtMs, showPi),
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMs, showPi),
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled timer alarm (setAlarmClock) for $triggerAtMs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule timer alarm", e)
        }
    }
    
    /**
     * Reschedule the timer alarm after device reboot.
     * Called by BootCompletedReceiver when a running timer is restored.
     */
    fun rescheduleTimerAlarm(context: Context) {
        if (timerState.value == TimerState.RUNNING && endTimeMs > System.currentTimeMillis()) {
            scheduleTimerAlarm(context, endTimeMs)
            TimerSettingsStateHolder.init(context)
            startTicker(context)
            Log.d(TAG, "Rescheduled timer alarm for $endTimeMs")
        }
    }
    
    private fun cancelTimerAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                TIMER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Cancelled timer alarm")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel timer alarm", e)
        }
    }
    
    fun pauseTimer() {
        val ctx = appContext ?: return
        tickerJob?.cancel()
        tickerJob = null
        cancelTimerAlarm(ctx)
        timerState.value = TimerState.PAUSED
        saveState(ctx)
        syncTimerPopup("pause_timer")
        Log.d(TAG, "Paused timer with ${timeMillis.value}ms remaining")
    }
    
    fun resumeTimer(context: Context) {
        if (timeMillis.value > 0) {
            // Recalculate end time based on remaining time
            endTimeMs = System.currentTimeMillis() + timeMillis.value
            timerState.value = TimerState.RUNNING
            scheduleTimerAlarm(context, endTimeMs)
            TimerSettingsStateHolder.init(context)
            startTicker(context)
            saveState(context)
            syncTimerPopup("resume_timer")
            Log.d(TAG, "Resumed timer with ${timeMillis.value}ms remaining")
        }
    }
    
    /**
     * Reset timer to initial state. This is the ONLY way to stop the timer.
     * Call this from ALL stop buttons (Timer screen, banners, popups).
     */
    fun resetTimer(context: Context) {
        Log.d(TAG, "Reset timer called")
        tickerJob?.cancel()
        tickerJob = null
        cancelTimerAlarm(context)

        // Clear alarm playing state and stop service
        isAlarmPlaying = false
        if (TimeEngineService.isRunning) {
            TimeEngineService.stop(context)
        }

        // Also stop any local alarm (legacy cleanup)
        stopAlarm(context)

        timerState.value = TimerState.IDLE
        timeMillis.value = initialTimeMillis.value
        endTimeMs = 0L
        showAlarmDialog.value = false
        // Naam hoort bij de nu voorbije run (bv. "Witgoed") - niet laten hangen voor de volgende,
        // nog naamloze, timer die de gebruiker hierna instelt.
        timerName.value = ""
        TimerFinishedAlarmUi.cancel(context.applicationContext)
        saveState(context)
        syncTimerPopup("reset_timer")
        Log.d(TAG, "Reset timer complete")
    }
    
    /**
     * Stop alarm sound only without resetting timer state.
     * Used when timer finishes and user dismisses alarm.
     */
    fun stopAlarmOnly(context: Context) {
        Log.d(TAG, "Stop alarm only called")
        tickerJob?.cancel()
        tickerJob = null
        cancelTimerAlarm(context)

        // Clear alarm playing state and stop service
        isAlarmPlaying = false
        if (TimeEngineService.isRunning) {
            TimeEngineService.stop(context)
        }

        // Also stop any local alarm (legacy cleanup)
        stopAlarm(context)

        timerState.value = TimerState.IDLE
        timeMillis.value = initialTimeMillis.value
        endTimeMs = 0L
        showAlarmDialog.value = false
        TimerFinishedAlarmUi.cancel(context.applicationContext)
        saveState(context)
        syncTimerPopup("stop_alarm_only")
    }
    
    /**
     * Stop timer completely - clears everything including initial time.
     * Use this for a full stop (not just pause/reset).
     */
    fun stopTimer(context: Context) {
        Log.d(TAG, "Stop timer called")
        tickerJob?.cancel()
        tickerJob = null
        cancelTimerAlarm(context)

        // Clear alarm playing state and stop service
        isAlarmPlaying = false
        if (TimeEngineService.isRunning) {
            TimeEngineService.stop(context)
        }

        // Also stop any local alarm (legacy cleanup)
        stopAlarm(context)

        timerState.value = TimerState.IDLE
        timeMillis.value = 0L
        initialTimeMillis.value = 0L
        endTimeMs = 0L
        showAlarmDialog.value = false
        // Zelfde reden als in resetTimer() - naam hoort bij de voorbije run.
        timerName.value = ""
        TimerFinishedAlarmUi.cancel(context.applicationContext)
        saveState(context)
        syncTimerPopup("stop_timer")
    }

    
    /**
     * Called by TimeEngineService when timer finishes.
     * Updates state only - alarm playback is handled by TimeEngineService.
     */
    fun onTimerAlarmFired(context: Context) {
        Log.d(TAG, "Timer alarm fired - updating state")
        timeMillis.value = 0L
        timerState.value = TimerState.FINISHED
        showAlarmDialog.value = false

        // Note: Alarm playback is now handled by TimeEngineService
        // We only update state here
        saveState(context)
        syncTimerPopup("timer_alarm_fired")
    }

    /**
     * Stop alarm playback and start the same duration again (from finished popup / notification).
     */
    fun restartTimerAfterAlarm(context: Context) {
        val app = context.applicationContext
        val duration = when {
            initialTimeMillis.value > 0L -> initialTimeMillis.value
            else -> TimerSettingsStateHolder.lastTimeMillis.value
        }.coerceAtLeast(1000L)

        Log.i(TAG, "restartTimerAfterAlarm durationMs=$duration")

        tickerJob?.cancel()
        tickerJob = null
        cancelTimerAlarm(app)

        isAlarmPlaying = false
        if (TimeEngineService.isRunning) {
            TimeEngineService.stop(app)
        }
        stopAlarm(app)

        showAlarmDialog.value = false
        Handler(Looper.getMainLooper()).postDelayed({
            startTimer(app, duration)
        }, 80L)
    }
    
    private fun stopAlarm(context: Context) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val vib = vibrator ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vib.cancel()
            vibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun playAlarmFromActivity(context: Context) {
        playAlarm(context)
    }
    
    private fun playAlarm(context: Context) {
        try {
            TimerSettingsStateHolder.init(context.applicationContext)
            val useMobileVolume = TimerSettingsStateHolder.useMobileVolume.value
            val volume = TimerSettingsStateHolder.volume.value
            val vibrate = TimerSettingsStateHolder.vibrationEnabled.value

            if (vibrate) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
                }
            }

            // STREAM_ALARM altijd eerst synchroniseren met de volume-slider (tenzij "mobiel/
            // systeemvolume" aan staat - syncAlarmStreamFromSlider slaat dat zelf over) - ook als
            // DD Music het lokale geluid hieronder gaat onderdrukken. Stond dit ALLEEN binnen de
            // lokale-MediaPlayer-tak hieronder, dan werd het overgeslagen zodra DD Music actief
            // was, waardoor DD Music altijd op het toevallige/oude STREAM_ALARM-niveau speelde
            // i.p.v. het ingestelde timervolume - vandaar "te zacht en niet harder te zetten".
            TimerSystemAlarmVolume.syncAlarmStreamFromSlider(context)
            // DD Music speelt altijd via de media-stream (nooit ALARM) - zorg dat die stream
            // ook op het ingestelde timervolume staat vóórdat DD Music eventueel gestart wordt.
            TimerSystemAlarmVolume.syncMediaStreamFromSlider(context)

            // DD Music: zelfde keuze als ExtraTimerManager.playOnExternalSpeakerIfConfiguredForSlot
            // bij speaker-only — lokale MediaPlayer onderdrukken zodat er geen dubbel geluid
            // speelt. Bij mislukte launch valt terug op lokaal alarmgeluid.
            if (tryPlayViaDdMusic(context)) return

            // If using mobile volume, always play (system controls volume)
            // If using internal volume, only play if volume > 0
            if (useMobileVolume || volume > 0) {
                val toneUriStr = TimerSettingsStateHolder.toneUri.value
                val uri = SettingsManager.resolvePlayableAlarmSoundUri(context, toneUriStr)

                mediaPlayer = try {
                    MediaPlayer().apply {
                        setDataSource(context, uri)
                        setAudioAttributes(TimerSettingsStateHolder.timerAlarmAudioAttributes())
                        isLooping = true
                        prepare()
                        start()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Alarm sound failed for uri=$uri, falling back to default", e)
                    MediaPlayer().apply {
                        setDataSource(context, Uri.parse(SettingsManager.DEFAULT_ALARM_SOUND_URI))
                        setAudioAttributes(TimerSettingsStateHolder.timerAlarmAudioAttributes())
                        isLooping = true
                        prepare()
                        start()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Start DD Music als timer-instelling dat vraagt en de app geïnstalleerd is.
     * @return true als de Intent succesvol is verstuurd (lokale MediaPlayer mag dan weg).
     */
    private fun tryPlayViaDdMusic(context: Context): Boolean {
        if (!TimerSettingsStateHolder.ddMusicLinked.value) return false
        if (!DdMusicBridge.isInstalled(context)) return false
        return DdMusicBridge.launch(context, DdMusicBridge.TIMER_TRIGGER_ID)
    }

    fun isRunning(): Boolean = timerState.value == TimerState.RUNNING
    fun isPaused(): Boolean = timerState.value == TimerState.PAUSED
    fun isFinished(): Boolean = timerState.value == TimerState.FINISHED
    fun isIdle(): Boolean = timerState.value == TimerState.IDLE
    
    // Track if a timer is actively running (for restoring state when activity reopens)
    fun hasActiveTimer(): Boolean = timerState.value == TimerState.RUNNING || timerState.value == TimerState.PAUSED || timerState.value == TimerState.FINISHED
    
    fun getCurrentTimeMillis(): Long = timeMillis.value
    fun getInitialTimeMillis(): Long = initialTimeMillis.value
    fun getCurrentState(): TimerState = timerState.value

    /**
     * Persist idle input duration after an explicit reset (e.g. 00:00:00).
     * No-op while a timer is running, paused, or in finished/alarm state.
     */
    fun persistIdleInputDuration(context: Context, durationMillis: Long) {
        if (hasActiveTimer()) return
        appContext = context.applicationContext
        initialTimeMillis.value = durationMillis
        timeMillis.value = durationMillis
        saveState(context.applicationContext)
        Log.d(TAG, "Persisted idle input duration: ${durationMillis}ms")
    }
    
}
