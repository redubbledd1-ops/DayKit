package com.dd.daykit

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.dd.daykit.data.HaUpdateResult
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays timer alarm sound/vibration when the timer fires (e.g. from [TimerAlarmReceiver]).
 * Runs as a short foreground service with a high-priority alarm notification and full-screen
 * intent to [TimerFinishedActivity] so the user can restart or stop without auto-dismiss.
 */
class TimeEngineService : Service() {

    companion object {
        private const val TAG = "TimeEngineService"

        const val ACTION_TIMER_ALARM = "com.dd.daykit.ACTION_TIMER_ALARM"
        const val ACTION_STOP = "com.dd.daykit.ACTION_STOP_TIMER_ALARM"

        @Volatile
        var isRunning = false
            private set

        fun startTimerAlarm(context: Context) {
            val intent = Intent(context, TimeEngineService::class.java).apply {
                action = ACTION_TIMER_ALARM
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimeEngineService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false
    private var externalSpeakerJob: Job? = null
    private var currentExternalSpeakerEntityId: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        isRunning = true

        if (WakeMobilePolicy.isWakeMobileEnabled(this)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AgendaWekker:TimerAlarmWakeLock",
            ).apply {
                acquire(5 * 60 * 1000L) // 5 minutes max
            }
            AlarmRingingDebug.logWakeLock("TimeEngineService.onCreate", acquired = true)
        } else {
            AlarmRingingDebug.logWakeLock("TimeEngineService.onCreate", acquired = false, reason = "full_screen_alarm_unavailable")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping timer alarm service")
                GlobalTimerManager.setAlarmPlaying(false)
                stopAlarm()
                stopExternalSpeaker()
                tearDownForeground()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TIMER_ALARM -> {
                if (GlobalTimerManager.isAlarmPlaying) {
                    Log.d(TAG, "Alarm already playing - ignoring duplicate trigger")
                    return START_NOT_STICKY
                }

                TimerSettingsStateHolder.init(this)

                // startForeground MUST run before any work that can start another FGS (e.g.
                // syncTimerPopup → TimerPopupForegroundService) or Android 12+ / API 34+ may
                // throttle or drop alarm playback.
                AlarmRingingDebug.logWakeMobile(applicationContext, "TimeEngineService.beforeForeground")
                TimerFinishedAlarmUi.ensureChannel(this)
                val notif = TimerFinishedAlarmUi.buildTimerFinishedNotification(this)
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(
                        TimerFinishedAlarmUi.NOTIFICATION_ID,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    @Suppress("DEPRECATION")
                    startForeground(TimerFinishedAlarmUi.NOTIFICATION_ID, notif)
                }
                foregroundStarted = true

                GlobalTimerManager.setAlarmPlaying(true)
                GlobalTimerManager.onTimerAlarmFired(this)

                // Explicitly launch the global overlay. setFullScreenIntent only auto-launches
                // when the app is in the background / device is locked (Android 10+); when the
                // user is currently on Stopwatch / Witgoed / Timer page, we still need the
                // overlay to appear over the running app.
                AlarmRingingDebug.logFsiPermission(applicationContext, "TimeEngineService.ACTION_TIMER_ALARM")
                FullScreenIntentPermission.logState(applicationContext, TAG)
                if (WakeMobilePolicy.isWakeMobileEnabled(applicationContext)) {
                    launchTimerFinishedOverlay()
                } else {
                    AlarmRingingDebug.logActivityLaunch(
                        "TimeEngineService.ACTION_TIMER_ALARM",
                        "TimerFinishedActivity",
                        attempted = false,
                        reason = "full_screen_alarm_unavailable",
                    )
                }

                playAlarm()

                return START_STICKY
            }
            else -> {
                // OOM restart with null intent: recover if timer was FINISHED when process was killed.
                GlobalTimerManager.init(this)
                if (GlobalTimerManager.timerState.value == GlobalTimerManager.TimerState.FINISHED
                    && !GlobalTimerManager.isAlarmPlaying
                ) {
                    Log.w(TAG, "OOM restart null intent — timer was FINISHED, recovering alarm")
                    return onStartCommand(
                        Intent(this, TimeEngineService::class.java).apply { action = ACTION_TIMER_ALARM },
                        flags,
                        startId,
                    )
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isRunning = false
        GlobalTimerManager.setAlarmPlaying(false)
        stopAlarm()
        stopExternalSpeaker()
        tearDownForeground()
        wakeLock?.release()
        wakeLock = null
    }

    private fun stopExternalSpeaker() {
        externalSpeakerJob?.cancel()
        externalSpeakerJob = null

        currentExternalSpeakerEntityId?.let { speakerId ->
            currentExternalSpeakerEntityId = null
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ExternalSpeakerHelper.stopAlarmOnSpeaker(applicationContext, speakerId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping external speaker for timer", e)
                }
            }
        }
    }

    private fun tearDownForeground() {
        if (!foregroundStarted) return
        foregroundStarted = false
        TimerFinishedAlarmUi.cancel(this)
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchTimerFinishedOverlay() {
        try {
            val intent = Intent(this, TimerFinishedActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
            Log.i(TAG, "Launched TimerFinishedActivity overlay (explicit startActivity from TimeEngineService)")
            AlarmRingingDebug.logActivityLaunch(
                "TimeEngineService.launchTimerFinishedOverlay",
                "TimerFinishedActivity",
                attempted = true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch TimerFinishedActivity overlay (possible BAL block)", e)
            AlarmRingingDebug.e("TimeEngineService launch TimerFinishedActivity failed", e)
        }
    }

    private fun playAlarm() {
        // STREAM_ALARM altijd eerst synchroniseren met de volume-slider (tenzij "mobiel/
        // systeemvolume" aan staat - syncAlarmStreamFromSlider slaat dat zelf over) - ook als DD
        // Music het lokale geluid hieronder gaat onderdrukken. Stond dit ALLEEN binnen
        // startLocalPlayback()'s niet-onderdrukte tak, dan werd het overgeslagen zodra DD Music
        // actief was, waardoor DD Music altijd op het toevallige/oude STREAM_ALARM-niveau
        // speelde i.p.v. het ingestelde timervolume - vandaar "te zacht en niet harder te zetten".
        if (!TimerSettingsStateHolder.useMobileVolume.value) {
            TimerSystemAlarmVolume.syncAlarmStreamFromSlider(this)
            // DD Music speelt altijd via de media-stream (nooit ALARM) - zorg dat die stream
            // ook op het ingestelde timervolume staat vóórdat DD Music eventueel gestart wordt.
            TimerSystemAlarmVolume.syncMediaStreamFromSlider(this)
        }

        // DD Music: zelfde keuze als GlobalTimerManager/ExtraTimerManager - lokaal alarmgeluid
        // onderdrukken bij succesvolle launch (trilling blijft gewoon), bij mislukte launch
        // valt terug op lokaal geluid. Dit is het ECHTE achtergrond-vuurpad (via
        // TimerAlarmReceiver+AlarmManager) - GlobalTimerManager.playAlarm draait alleen als de
        // app op de voorgrond de countdown zelf bijhoudt.
        val ddMusicStarted = tryPlayViaDdMusic()
        startLocalPlayback(suppressSound = ddMusicStarted)

        // Phone plays immediately above; HA speaker decision happens async (network round-trip)
        // so the alarm is never silent while waiting, same pattern as AlarmService.
        externalSpeakerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                playOnExternalSpeakerIfConfigured()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling external speaker output for timer", e)
            }
        }

        // Los van de speaker-instellingen: optioneel HA-script starten bij deze timer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runTimerScriptIfConfigured()
            } catch (e: Exception) {
                Log.e(TAG, "Error running HA script for timer", e)
            }
        }
    }

    /**
     * Start DD Music als timer-instelling dat vraagt en de app geïnstalleerd is.
     * @return true als de Intent succesvol is verstuurd (lokaal alarmgeluid mag dan weg,
     *   trilling blijft ongeacht dit resultaat).
     */
    private fun tryPlayViaDdMusic(): Boolean {
        if (!TimerSettingsStateHolder.ddMusicLinked.value) return false
        if (!DdMusicBridge.isInstalled(this)) return false
        return DdMusicBridge.launch(this, DdMusicBridge.TIMER_TRIGGER_ID)
    }

    private fun startLocalPlayback(suppressSound: Boolean = false) {
        try {
            val useMobileVolume = TimerSettingsStateHolder.useMobileVolume.value
            val volume = TimerSettingsStateHolder.volume.value
            val vibrate = TimerSettingsStateHolder.vibrationEnabled.value

            Log.d(TAG, "Playing alarm - useMobileVolume: $useMobileVolume, volume: $volume, vibrate: $vibrate")

            if (vibrate) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
                }
                Log.d(TAG, "Vibration started")
            }

            if (!suppressSound && (useMobileVolume || volume > 0)) {
                if (!useMobileVolume) {
                    TimerSystemAlarmVolume.syncAlarmStreamFromSlider(this)
                }
                val toneUriStr = TimerSettingsStateHolder.toneUri.value
                val uri = SettingsManager.resolvePlayableAlarmSoundUri(this@TimeEngineService, toneUriStr)

                mediaPlayer = try {
                    MediaPlayer().apply {
                        setDataSource(this@TimeEngineService, uri)
                        setAudioAttributes(TimerSettingsStateHolder.timerAlarmAudioAttributes())
                        isLooping = true
                        prepare()
                        start()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Alarm sound failed for uri=$uri, falling back to default", e)
                    MediaPlayer().apply {
                        setDataSource(this@TimeEngineService, Uri.parse(SettingsManager.DEFAULT_ALARM_SOUND_URI))
                        setAudioAttributes(TimerSettingsStateHolder.timerAlarmAudioAttributes())
                        isLooping = true
                        prepare()
                        start()
                    }
                }
                Log.d(TAG, "Alarm sound started useMobileVolume=$useMobileVolume")
            } else if (suppressSound) {
                Log.d(TAG, "Lokaal alarmgeluid onderdrukt - DD Music speelt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm", e)
        }
    }

    /**
     * Optioneel HA-script starten bij het aflopen van de timer. Volledig los van de
     * externe-speaker instellingen/beslissing hierboven.
     */
    private suspend fun runTimerScriptIfConfigured() {
        val settingsStorage = HomeAssistantSettingsStorage(applicationContext)
        val repository = HomeAssistantRepository(HomeAssistantClient, settingsStorage)
        val settings = repository.getSettings()
        val scriptEntityId = settings.timerScriptEntityId
        if (!settings.timerScriptEnabled || scriptEntityId.isNullOrBlank()) return

        if (!settings.timerScriptIgnorePresence &&
            !AlarmOutputDecisionEngine.isUserAtHome(repository, settings)
        ) {
            Log.d(TAG, "[TIMER SCRIPT] Skipped: user not home (alleen als thuis)")
            return
        }

        val result = repository.callScript(scriptEntityId)
        if (result is HaUpdateResult.Error) {
            Log.w(TAG, "[TIMER SCRIPT] callScript failed: ${result.message}")
        } else {
            Log.i(TAG, "[TIMER SCRIPT] Started script $scriptEntityId")
        }
    }

    /**
     * Same HA-speaker decision as AlarmService (AlarmOutputDecisionEngine + ExternalSpeakerHelper),
     * applied to the timer's own tone instead of the alarm item's sound.
     */
    private suspend fun playOnExternalSpeakerIfConfigured() {
        val settingsStorage = HomeAssistantSettingsStorage(applicationContext)
        val repository = HomeAssistantRepository(HomeAssistantClient, settingsStorage)

        // 12s (niet 5s): isUserAtHome() kan nu 1x een auto-fix-retry doen bij een "niet thuis"-
        // uitslag (forced refresh + herhaalde check). Geen risico op stilte: startLocalPlayback()
        // is al aangeroepen in playAlarm() vóórdat deze coroutine gestart is (zie regel 240-241).
        val alarmOutput = withTimeoutOrNull(12000) {
            AlarmOutputDecisionEngine.determineAlarmOutput(
                context = applicationContext,
                repository = repository,
                nextAlarmTimeMillis = System.currentTimeMillis(),
                speakerContext = com.dd.daykit.data.SpeakerContext.TIMER
            )
        } ?: return

        val speakerEntityId = when (alarmOutput) {
            is AlarmOutput.ExternalDefault -> alarmOutput.speakerEntityId
            is AlarmOutput.ExternalBackup -> alarmOutput.speakerEntityId
            is AlarmOutput.PhoneAndExternal -> alarmOutput.speakerEntityId
            AlarmOutput.PhoneOnly -> null
        } ?: return

        Log.d(TAG, "[TIMER OUTPUT] Using external speaker: $speakerEntityId")
        currentExternalSpeakerEntityId = speakerEntityId

        // DD Music (mode "song") + HA-speaker geconfigureerd: speel het gekozen nummer op de
        // speaker i.p.v. het normale timergeluid. Alleen gezet bij "Kies nummer" in DD Music -
        // playlist/favorieten/doorgaan spelen alleen lokaal op de telefoon.
        val ddMusicPlayUrl = TimerSettingsStateHolder.ddMusicPlayUrl.value?.takeIf { it.isNotBlank() }
        val toneUriStr = TimerSettingsStateHolder.toneUri.value ?: SettingsManager.DEFAULT_ALARM_SOUND_URI
        val soundUrl = ddMusicPlayUrl
            ?: ExternalSpeakerHelper.getExternalSpeakerSoundUrl(applicationContext, com.dd.daykit.data.SpeakerContext.TIMER)
            ?: ExternalSpeakerHelper.getAlarmSoundUrl(applicationContext, toneUriStr)

        // Speaker-only modes: stop local playback so alarm isn't doubled on phone + speaker.
        val speakerOnly = alarmOutput !is AlarmOutput.PhoneAndExternal
        if (speakerOnly) {
            stopAlarm()
        }

        val success = ExternalSpeakerHelper.playAlarmOnSpeaker(applicationContext, speakerEntityId, soundUrl, com.dd.daykit.data.SpeakerContext.TIMER)
        if (!success) {
            Log.e(TAG, "[TIMER OUTPUT] External speaker failed, falling back to phone")
            currentExternalSpeakerEntityId = null
            if (speakerOnly) {
                startLocalPlayback()
            }
        } else {
            Log.i(TAG, "[TIMER OUTPUT] External speaker started successfully")
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d(TAG, "MediaPlayer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        }

        try {
            vibrator?.cancel()
            vibrator = null
            Log.d(TAG, "Vibrator stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibrator", e)
        }
    }
}
