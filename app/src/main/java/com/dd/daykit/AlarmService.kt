package com.dd.daykit

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.dd.daykit.data.HaUpdateResult
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.data.SpeakerContext
import com.dd.daykit.network.HomeAssistantClient
import com.dd.daykit.rules.TriggerBehaviorMode
import com.dd.daykit.rules.TriggerBehaviorStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var externalSpeakerJob: Job? = null
    private var currentExternalSpeakerEntityId: String? = null

    /** Vangnet dat het ingebouwde alarmgeluid alsnog start als DD Music niet hoorbaar wordt. */
    private var ddMusicWatchdogJob: Job? = null

    /** Gezet zodra de DD Music-Intent daadwerkelijk verstuurd is (fase 1 → fase 2 van de watchdog). */
    @Volatile
    private var ddMusicLaunched = false

    companion object {
        const val ACTION_STOP = "com.dd.daykit.ACTION_STOP"
        private const val TAG = "AlarmService"
        const val FIRING_TRAY_NOTIFICATION_ID = 90403
        private const val RC_FIRE_FULL_SCREEN = 9300
        private const val RC_FIRE_CONTENT = 9301
        private const val RC_FIRE_SNOOZE = 9302
        private const val RC_FIRE_DISMISS = 9303

        private const val PREFS_RINGING = "alarm_service_ringing"
        private const val KEY_RINGING_JSON = "ringing_alarm_json"

        /**
         * Hoe lang de watchdog wacht tot DD Music überhaupt gestart is. Bij SMART_ALARM zit de
         * AlarmOutputDecisionEngine (max 12s) hier nog tussen, dus dit venster mag niet te kort:
         * te kort = het ingebouwde geluid start alsnog vóór DD Music en je hoort weer die korte
         * "disco"-burst. Te lang = te lang stilte als DD Music helemaal niet komt.
         */
        private const val DD_MUSIC_LAUNCH_TIMEOUT_MS = 8_000L

        /** Hoe lang na het versturen van de Intent DD Music de tijd krijgt om écht geluid te maken. */
        private const val DD_MUSIC_AUDIO_TIMEOUT_MS = 5_000L

        /** Pollinterval van de watchdog. */
        private const val DD_MUSIC_WATCHDOG_POLL_MS = 200L

        /** Bovengrens voor het uitlezen van de DD Music-koppeling vóór er geluid mag komen. */
        private const val DD_MUSIC_CONFIG_READ_TIMEOUT_MS = 1_500L

        fun buildFiringTrayNotification(context: Context, alarm: AlarmItem): android.app.Notification {
            val serviceContext = context.applicationContext
            LanguageManager.init(serviceContext)
            PopupNotificationFoundation.ensureCalendarAlarmChannel(serviceContext)
            AlarmRingingDebug.logFsiPermission(serviceContext, "buildFiringTrayNotification")
            AlarmRingingDebug.logNotificationChannel(
                serviceContext,
                PopupNotificationFoundation.CALENDAR_ALARM_CHANNEL_ID,
            )
            val app = serviceContext
            val colors = PopupNotificationFoundation.resolveThemeColors(app)
            val generic = LanguageManager.getString("screen_agenda_alarm")
            val eventTitle = alarm.label.trim().takeIf { it.isNotEmpty() }
            val timeLine = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.epochMillis))
            val title = eventTitle ?: generic
            val subtitle = timeLine
            val alarmJson = Json.encodeToString(AlarmItem.serializer(), alarm)
            val canSnooze = kotlinx.coroutines.runBlocking {
                AgendaAlarmSnoozeCoordinator.canOfferSnooze(app, alarm)
            }

            val ringIntent = Intent(app, RingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("alarm", alarmJson)
            }
            val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val fullScreenPi = PendingIntent.getActivity(app, RC_FIRE_FULL_SCREEN, ringIntent, piFlags)
            val contentPi = PendingIntent.getActivity(app, RC_FIRE_CONTENT, ringIntent, piFlags)
            AlarmRingingDebug.i(
                "buildFiringTrayNotification pendingIntents fullScreenRc=$RC_FIRE_FULL_SCREEN " +
                    "contentRc=$RC_FIRE_CONTENT flags=UPDATE_CURRENT|IMMUTABLE target=RingActivity"
            )

            val snoozeIntent = Intent(app, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_SNOOZE
                putExtra("alarm", alarmJson)
            }
            val snoozePi = PendingIntent.getBroadcast(
                app,
                RC_FIRE_SNOOZE,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(app, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_DISMISS
                putExtra("alarm", alarmJson)
            }
            val dismissPi = PendingIntent.getBroadcast(
                app,
                RC_FIRE_DISMISS,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return runCatching {
                val spec = CompactTrayNotificationSpec(
                    title = title,
                    subtitle = subtitle,
                    primaryActionGlyph = if (canSnooze) "zzz" else null,
                    secondaryActionGlyph = "✕",
                    primaryAction = if (canSnooze) snoozePi else null,
                    secondaryAction = dismissPi,
                    rootTapAction = contentPi,
                )
                val rv = PopupNotificationFoundation.buildPocCompactRemoteViews(app, colors, spec)
                WakeMobilePolicy
                    .applyFullScreenIntent(
                        app,
                        PopupNotificationFoundation
                            .alarmTrayCompactBuilder(
                                app,
                                PopupNotificationFoundation.CALENDAR_ALARM_CHANNEL_ID,
                                colors.backgroundColor,
                            )
                            .setCustomContentView(rv)
                            .setContentIntent(contentPi)
                            .setContentTitle(title)
                            .setContentText(subtitle),
                        fullScreenPi,
                        "buildFiringTrayNotification.customView",
                    )
                    .also {
                        AlarmRingingDebug.i(
                            "notificationBuilt variant=customView category=ALARM " +
                                "channel=${PopupNotificationFoundation.CALENDAR_ALARM_CHANNEL_ID} " +
                                "wakeMobile=${WakeMobilePolicy.isWakeMobileEnabled(app)}"
                        )
                    }
                    .build()
            }.getOrElse { err ->
                Log.w(TAG, "Firing tray custom view failed, fallback: ${err.javaClass.simpleName}")
                AlarmRingingDebug.w("notificationBuilt variant=fallback reason=${err.javaClass.simpleName}")
                WakeMobilePolicy
                    .applyFullScreenIntent(
                        app,
                        PopupNotificationFoundation
                            .alarmTrayCompactBuilder(
                                app,
                                PopupNotificationFoundation.CALENDAR_ALARM_CHANNEL_ID,
                                colors.backgroundColor,
                            )
                            .setContentTitle(title)
                            .setContentText(subtitle)
                            .setContentIntent(contentPi),
                        fullScreenPi,
                        "buildFiringTrayNotification.fallback",
                    )
                    .also {
                        AlarmRingingDebug.i(
                            "notificationBuilt variant=fallback category=ALARM " +
                                "wakeMobile=${WakeMobilePolicy.isWakeMobileEnabled(app)}"
                        )
                    }
                    .apply {
                        if (canSnooze) {
                            addAction(0, LanguageManager.getString("alarm_snooze"), snoozePi)
                        }
                        addAction(0, LanguageManager.getString("alarm_dismiss"), dismissPi)
                    }
                    .build()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService onCreate")

        if (WakeMobilePolicy.isWakeMobileEnabled(this)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AgendaWekker:AlarmWakeLock",
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes max
            }
            AlarmRingingDebug.logWakeLock("AlarmService.onCreate", acquired = true)
        } else {
            AlarmRingingDebug.logWakeLock("AlarmService.onCreate", acquired = false, reason = "full_screen_alarm_unavailable")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService onStartCommand - action: ${intent?.action}")
        
        try {
            if (intent?.action == ACTION_STOP) {
                Log.d(TAG, "Stopping alarm service")
                cancelDdMusicWatchdog("action_stop")
                stopLocalAlarmPlayback()
                externalSpeakerJob?.cancel()
                externalSpeakerJob = null
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return START_NOT_STICKY
            }

            val alarmItem = intent?.getStringExtra("alarm")?.let {
                try {
                    Json.decodeFromString<AlarmItem>(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding alarm item", e)
                    null
                }
            } ?: restoreRingingAlarm()

            if (alarmItem == null) {
                Log.w(TAG, "OOM restart null intent — no persisted alarm; rescheduling next")
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        AlarmScheduler.scheduleNextAlarm(applicationContext, "alarm_service_null_intent")
                    }
                }
                stopSelf()
                return START_NOT_STICKY
            }

            if (!AgendaAlarmLocalActivationStore.isLocallyEnabled(applicationContext, alarmItem)) {
                Log.w(TAG, "Alarm locally disabled — aborting ring; rescheduling")
                CoroutineScope(Dispatchers.IO).launch {
                    AlarmScheduler.scheduleNextAlarm(applicationContext, "alarm_service_locally_disabled")
                }
                stopSelf()
                return START_NOT_STICKY
            }

            if (AlarmStateManager.isRinging.value) {
                val current = AlarmStateManager.ringingAlarm.value
                val sameInstance = current != null &&
                    current.id == alarmItem.id &&
                    current.epochMillis == alarmItem.epochMillis
                if (sameInstance) {
                    Log.w(
                        TAG,
                        "RINGING_DUP_IGNORE same id=${alarmItem.id} epoch=${alarmItem.epochMillis} startId=$startId"
                    )
                    return START_NOT_STICKY
                }
                Log.i(
                    TAG,
                    "RINGING_REPLACE prev id=${current?.id} epoch=${current?.epochMillis} → " +
                        "new id=${alarmItem.id} epoch=${alarmItem.epochMillis}"
                )
                cancelDdMusicWatchdog("ringing_replace")
                stopLocalAlarmPlayback()
                externalSpeakerJob?.cancel()
                externalSpeakerJob = null
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                AlarmStateManager.onAlarmStop()
            }

            Log.d(TAG, "Starting alarm for: ${alarmItem.label}")
            AgendaAlarmForensics.init(applicationContext)
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.SERVICE,
                "AlarmService GESTART — alarm gaat nu daadwerkelijk af. ${AgendaAlarmForensics.describe(alarmItem)}",
                applicationContext
            )

            // Sluimer-aflevering: PI + venster wissen, keten-trigger bewaren.
            // Echte agenda-alarm (triggerId gezet): volledige sluimer-state wissen — geen keten meer.
            if (!alarmItem.triggerId.isNullOrBlank()) {
                Log.i(TAG, "RING main calendar instance — full snooze store clear")
                AlarmScheduler.cancelSnoozeAlarm(applicationContext)
                AgendaSnoozeStore.clear(applicationContext)
            } else {
                AgendaAlarmSnoozeCoordinator.onSnoozeAlarmDeliveredClearGhostSchedulingOnly(applicationContext)
            }
            SnoozePopupForegroundService.stopPopup(applicationContext, "snooze_delivered_starting_ring")

            AlarmRingingDebug.i("AlarmService.onStartCommand firing label=${alarmItem.label} id=${alarmItem.id}")
            AlarmRingingDebug.logWakeMobile(applicationContext, "AlarmService.beforeForeground")
            AlarmRingingDebug.logFsiPermission(applicationContext, "AlarmService.beforeForeground")

            val trayNotification = buildFiringTrayNotification(this, alarmItem)
            runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(
                        FIRING_TRAY_NOTIFICATION_ID,
                        trayNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    @Suppress("DEPRECATION")
                    startForeground(FIRING_TRAY_NOTIFICATION_ID, trayNotification)
                }
                AlarmRingingDebug.i(
                    "AlarmService.startForeground ok notificationId=$FIRING_TRAY_NOTIFICATION_ID " +
                        "(fullScreenIntent attached in buildFiringTrayNotification)"
                )
            }.onFailure {
                Log.e(TAG, "startForeground tray failed", it)
                AlarmRingingDebug.e("AlarmService.startForeground failed", it)
            }

            AlarmStateManager.onAlarmStart(alarmItem)
            saveRingingAlarm(alarmItem)

            val alarmJson = Json.encodeToString(AlarmItem.serializer(), alarmItem)
            val ringActivityIntent = Intent(applicationContext, RingActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("alarm", alarmJson)
            }

            if (WakeMobilePolicy.isWakeMobileEnabled(applicationContext)) {
                try {
                    startActivity(ringActivityIntent)
                    AlarmRingingDebug.logActivityLaunch(
                        "AlarmService.onStartCommand",
                        "RingActivity",
                        attempted = true,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting RingActivity", e)
                    AlarmRingingDebug.e("AlarmService startActivity RingActivity failed (possible BAL block)", e)
                }
            } else {
                AlarmRingingDebug.logActivityLaunch(
                    "AlarmService.onStartCommand",
                    "RingActivity",
                    attempted = false,
                    reason = "full_screen_alarm_unavailable",
                )
            }

            val playbackTriggerId =
                AgendaAlarmSnoozeCoordinator.resolveChainSourceTriggerId(applicationContext, alarmItem)
            Log.d(
                TAG,
                "Playback settings trigger=$playbackTriggerId " +
                    "(item.triggerId=${alarmItem.triggerId} snoozeSource=${alarmItem.snoozeSourceTriggerId})",
            )

            // Determine alarm output (phone vs external speaker) — eerst telefoon, daarna HA-beslissing
            externalSpeakerJob = CoroutineScope(Dispatchers.IO).launch {
                // Is DD Music de bedoelde telefoon-output voor deze trigger? Zo ja, dan starten we
                // het ingebouwde alarmgeluid hier bewust NIET: dat gaf een korte, hoorbare burst
                // ("Standaard disco") die daarna door DD Music overgenomen werd. In plaats daarvan
                // bewaakt een watchdog of er binnen redelijke tijd écht geluid komt; zo niet, dan
                // start het ingebouwde geluid alsnog. Stilte is dus nog steeds nooit het risico.
                if (isDdMusicPhoneOutput(playbackTriggerId)) {
                    Log.i(TAG, "[DD MUSIC] Gekoppeld aan trigger=$playbackTriggerId - ingebouwd geluid wordt overgeslagen, watchdog gewapend")
                    armDdMusicWatchdog(playbackTriggerId, alarmItem)
                } else {
                    playRingtoneAndVibrate(playbackTriggerId, alarmItem)
                }
                if (!shouldUseHomeAssistantOutput(playbackTriggerId)) {
                    Log.d(TAG, "[ALARM OUTPUT] Standalone simple alarm - skipping Home Assistant output")
                    // Geen HA-koppeling voor deze trigger: telefoon is het enige output, dus DD Music
                    // (indien gekoppeld) mag hier altijd lokaal starten.
                    val ddMusicStarted = tryStartDdMusicForTrigger(playbackTriggerId, alarmItem)
                    if (!ddMusicStarted) {
                        ensurePhoneAlarmPlayback(playbackTriggerId, alarmItem)
                    }
                    return@launch
                }
                try {
                    Log.d(TAG, "=".repeat(70))
                    Log.d(TAG, "[ALARM OUTPUT] Starting decision engine (phone already primed)...")

                    val settingsStorage = HomeAssistantSettingsStorage(applicationContext)
                    val client = HomeAssistantClient // Singleton
                    val repository = HomeAssistantRepository(client, settingsStorage)

                    // Los van de speaker-beslissing hieronder: optioneel HA-script starten bij dit alarm
                    val haSettings = repository.getSettings()
                    val alarmScriptEntityId = haSettings.alarmScriptEntityId
                    if (haSettings.alarmScriptEnabled && !alarmScriptEntityId.isNullOrBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                if (!haSettings.alarmScriptIgnorePresence &&
                                    !AlarmOutputDecisionEngine.isUserAtHome(repository, haSettings)
                                ) {
                                    Log.d(TAG, "[ALARM SCRIPT] Skipped: user not home (alleen als thuis)")
                                    return@launch
                                }
                                val result = repository.callScript(alarmScriptEntityId)
                                if (result is HaUpdateResult.Error) {
                                    Log.w(TAG, "[ALARM SCRIPT] callScript failed: ${result.message}")
                                } else {
                                    Log.i(TAG, "[ALARM SCRIPT] Started script $alarmScriptEntityId")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "[ALARM SCRIPT] Exception calling script $alarmScriptEntityId", e)
                            }
                        }
                    }

                    // 12s (niet 5s): isUserAtHome() kan nu 1x een auto-fix-retry doen bij een
                    // "niet thuis"-uitslag (forced refresh + herhaalde check, zie
                    // AlarmOutputDecisionEngine.isUserAtHome), wat in het slechtste geval een
                    // paar seconden extra kost. Geen risico op stilte: óf het telefoon-alarm speelt
                    // al (playRingtoneAndVibrate hierboven), óf DD Music is de telefoon-output en
                    // dan bewaakt de watchdog dat er geluid komt. Dit bepaalt alleen of/wanneer er
                    // ook nog naar de externe speaker overgeschakeld wordt.
                    val alarmOutput = withTimeoutOrNull(12000) {
                        AlarmOutputDecisionEngine.determineAlarmOutput(
                            context = applicationContext,
                            repository = repository,
                            nextAlarmTimeMillis = alarmItem.epochMillis,
                            speakerContext = SpeakerContext.ALARM
                        )
                    }

                    if (alarmOutput == null) {
                        Log.e(TAG, "[ALARM OUTPUT] Timeout (5s) — phone already playing, ensuring local playback")
                        val ddMusicStarted = tryStartDdMusicForTrigger(playbackTriggerId, alarmItem)
                        if (!ddMusicStarted) {
                            ensurePhoneAlarmPlayback(playbackTriggerId, alarmItem)
                        }
                        Log.d(TAG, "=".repeat(70))
                        return@launch
                    }

                    val playbackSoundUri = resolvePlaybackSoundUriString(playbackTriggerId, alarmItem)

                    // DD Music (mode "song") + HA-speaker geconfigureerd voor dezelfde trigger:
                    // speel het gekozen nummer op de speaker i.p.v. het normale alarmgeluid. Alleen
                    // gezet als DD Music expliciet een play_url teruggestuurd heeft (song-mode) -
                    // playlist/favorieten/doorgaan spelen alleen lokaal op de telefoon via DD Music.
                    val externalSpeakerSoundUrl = resolveExternalSpeakerSoundUrl(playbackTriggerId, playbackSoundUri)

                    // Dead-man's-switch: tell HA the phone is alive and ringing, so its backup
                    // watchdog (daykit integration, armed at schedule time by
                    // AlarmScheduler) doesn't need to step in with the external speaker.
                    // Best-effort, never blocks playback.
                    //
                    // Unconditional on speaker settings (only gated on an HA link actually being
                    // configured): this call also feeds the always-on phone_ip/ping detection
                    // (last_known_phone_ip -> binary_sensor.py), which has nothing to do with the
                    // external-speaker feature.
                    if (!haSettings.activeBaseUrl.isNullOrBlank() && !haSettings.longLivedToken.isNullOrBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Batterij als extra trigger: ook al haalt de telefoon het alarm zelf,
                                // als de batterij naar verwachting te laag wordt, blijft de telefoon
                                // "onbetrouwbaar" en mag de HA-watchdog straks alsnog inspringen.
                                val batteryOk = AlarmOutputDecisionEngine.willBatterySurviveNextAlarm(
                                    applicationContext,
                                    alarmItem.epochMillis
                                )
                                val fallbackSoundUrl = externalSpeakerSoundUrl
                                val batteryPercent = AlarmOutputDecisionEngine.getBatteryPercent(applicationContext)
                                val batteryUsagePerHour = SettingsManager.getBatteryUsagePerHour(applicationContext)
                                val phoneIp = AlarmOutputDecisionEngine.getPhoneIpAddress(applicationContext)
                                val result = repository.reportAlarmAlive(
                                    mobielBetrouwbaar = batteryOk,
                                    fallbackSpeaker = haSettings.alarmSpeaker.entityId,
                                    fallbackVolume = haSettings.alarmSpeaker.volume,
                                    fallbackSoundUrl = fallbackSoundUrl,
                                    fallbackInterval = haSettings.backupAlarmDuration,
                                    batteryPercent = batteryPercent,
                                    batteryUsagePerHour = batteryUsagePerHour,
                                    phoneIp = phoneIp
                                )
                                if (result is HaUpdateResult.Error) {
                                    Log.w(TAG, "[HA WATCHDOG] reportAlarmAlive failed: ${result.message}")
                                } else {
                                    Log.d(TAG, "[HA WATCHDOG] reportAlarmAlive reported mobielBetrouwbaar=$batteryOk")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "[HA WATCHDOG] reportAlarmAlive failed", e)
                            }
                        }
                    }

                    when (alarmOutput) {
                        is AlarmOutput.ExternalDefault -> {
                            Log.d(TAG, "[ALARM OUTPUT] Decision: External speaker (default)")
                            Log.d(TAG, "[ALARM OUTPUT]   Speaker: ${alarmOutput.speakerEntityId}")
                            currentExternalSpeakerEntityId = alarmOutput.speakerEntityId
                            val soundUrl = externalSpeakerSoundUrl
                            Log.d(TAG, "[ALARM OUTPUT]   Sound URL: $soundUrl")

                            // HA-speaker-only: telefoon moet stil blijven, dus DD Music wordt hier
                            // bewust NIET lokaal gestart (dat zou ongewenst geluid op de telefoon
                            // geven bovenop de HA-speaker). Watchdog uit, anders zou die straks
                            // alsnog het ingebouwde geluid op de telefoon starten.
                            cancelDdMusicWatchdog("external_default")
                            stopLocalAlarmPlayback()

                            val success = ExternalSpeakerHelper.playAlarmOnSpeaker(
                                applicationContext,
                                alarmOutput.speakerEntityId,
                                soundUrl,
                                SpeakerContext.ALARM
                            )

                            if (!success) {
                                Log.e(TAG, "[ALARM OUTPUT] External speaker FAILED - falling back to phone")
                                val ddMusicStarted = tryStartDdMusicForTrigger(playbackTriggerId, alarmItem)
                                if (!ddMusicStarted) {
                                    ensurePhoneAlarmPlayback(playbackTriggerId, alarmItem)
                                }
                            } else {
                                Log.i(TAG, "[ALARM OUTPUT] External speaker started successfully")
                            }
                        }
                        is AlarmOutput.ExternalBackup -> {
                            Log.d(TAG, "[ALARM OUTPUT] Decision: External speaker (backup - low battery)")
                            Log.d(TAG, "[ALARM OUTPUT]   Speaker: ${alarmOutput.speakerEntityId}")
                            currentExternalSpeakerEntityId = alarmOutput.speakerEntityId
                            val soundUrl = externalSpeakerSoundUrl
                            Log.d(TAG, "[ALARM OUTPUT]   Sound URL: $soundUrl")

                            // Zelfde als ExternalDefault: HA-speaker-only, dus geen lokale DD Music.
                            cancelDdMusicWatchdog("external_backup")
                            stopLocalAlarmPlayback()

                            val success = ExternalSpeakerHelper.playAlarmOnSpeaker(
                                applicationContext,
                                alarmOutput.speakerEntityId,
                                soundUrl,
                                SpeakerContext.ALARM
                            )

                            if (!success) {
                                Log.e(TAG, "[ALARM OUTPUT] External speaker FAILED - falling back to phone")
                                val ddMusicStarted = tryStartDdMusicForTrigger(playbackTriggerId, alarmItem)
                                if (!ddMusicStarted) {
                                    ensurePhoneAlarmPlayback(playbackTriggerId, alarmItem)
                                }
                            } else {
                                Log.i(TAG, "[ALARM OUTPUT] External speaker started successfully")
                            }
                        }
                        is AlarmOutput.PhoneAndExternal -> {
                            Log.d(TAG, "[ALARM OUTPUT] Decision: Both phone AND external speaker")
                            Log.d(TAG, "[ALARM OUTPUT]   Speaker: ${alarmOutput.speakerEntityId}")
                            currentExternalSpeakerEntityId = alarmOutput.speakerEntityId
                            val soundUrl = externalSpeakerSoundUrl
                            Log.d(TAG, "[ALARM OUTPUT]   Sound URL: $soundUrl")

                            ExternalSpeakerHelper.playAlarmOnSpeaker(
                                applicationContext,
                                alarmOutput.speakerEntityId,
                                soundUrl,
                                SpeakerContext.ALARM
                            )

                            // Telefoon hoort hier ook mee te doen: DD Music mag lokaal starten;
                            // ensurePhoneAlarmPlayback vangt op als dat niet lukt (fallback ingebouwd geluid).
                            val ddMusicStarted = tryStartDdMusicForTrigger(playbackTriggerId, alarmItem)
                            if (!ddMusicStarted) {
                                ensurePhoneAlarmPlayback(playbackTriggerId, alarmItem)
                            }
                            Log.i(TAG, "[ALARM OUTPUT] Playing on both speakers")
                        }
                        is AlarmOutput.PhoneOnly -> {
                            Log.d(TAG, "[ALARM OUTPUT] Decision: Phone only (no external speaker)")
                            val ddMusicStarted = tryStartDdMusicForTrigger(playbackTriggerId, alarmItem)
                            if (!ddMusicStarted) {
                                ensurePhoneAlarmPlayback(playbackTriggerId, alarmItem)
                            }
                            Log.i(TAG, "[ALARM OUTPUT] Playing on phone")
                        }
                    }
                    Log.d(TAG, "=".repeat(70))
                } catch (e: Exception) {
                    Log.e(TAG, "[ALARM OUTPUT] Exception in decision engine", e)
                    Log.e(TAG, "[ALARM OUTPUT] Falling back to phone playback")
                    ensurePhoneAlarmPlayback(playbackTriggerId, alarmItem)
                    Log.d(TAG, "=".repeat(70))
                }
            }

            return START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun saveRingingAlarm(alarm: AlarmItem) {
        runCatching {
            applicationContext.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RINGING_JSON, Json.encodeToString(AlarmItem.serializer(), alarm))
                .apply()
        }
    }

    private fun clearRingingAlarm() {
        runCatching {
            applicationContext.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE)
                .edit().remove(KEY_RINGING_JSON).apply()
        }
    }

    private fun restoreRingingAlarm(): AlarmItem? {
        val json = runCatching {
            applicationContext.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE)
                .getString(KEY_RINGING_JSON, null)
        }.getOrNull() ?: return null
        return try {
            Json.decodeFromString<AlarmItem>(json).also {
                Log.w(TAG, "OOM restart — restored ringing alarm id=${it.id} label=${it.label}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OOM restart — failed to decode persisted alarm; clearing", e)
            clearRingingAlarm()
            null
        }
    }

    private fun stopLocalAlarmPlayback() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
    }

    private fun isLocalRingtonePlaying(): Boolean =
        try {
            ringtone?.isPlaying == true
        } catch (_: Exception) {
            false
        }

    private suspend fun ensurePhoneAlarmPlayback(playbackTriggerId: String?, alarmItem: AlarmItem) {
        // We nemen het ingebouwde geluid hier zelf ter hand — de watchdog hoeft niet meer in te
        // springen (en mag zeker niet later nóg een keer starten).
        cancelDdMusicWatchdog("ensurePhoneAlarmPlayback")
        if (!isLocalRingtonePlaying()) {
            playRingtoneAndVibrate(playbackTriggerId, alarmItem)
        }
    }

    /**
     * True als DD Music voor deze trigger de bedoelde telefoon-output is: gekoppeld én
     * geïnstalleerd. Alleen dan mag het ingebouwde alarmgeluid overgeslagen worden — is DD Music
     * niet geïnstalleerd, dan is het ingebouwde geluid het enige dat er nog is en starten we het
     * gewoon meteen.
     */
    private suspend fun isDdMusicPhoneOutput(playbackTriggerId: String?): Boolean {
        if (playbackTriggerId.isNullOrBlank()) return false
        // Dit staat nu vóór élk geluid op het kritieke pad, dus harde bovengrens: blijft de
        // DataStore-lezing hangen, dan behandelen we het als "geen DD Music" en start het
        // ingebouwde alarmgeluid gewoon meteen.
        val linked = withTimeoutOrNull(DD_MUSIC_CONFIG_READ_TIMEOUT_MS) {
            try {
                TriggerBehaviorStorage(this@AlarmService).getConfig(playbackTriggerId).ddMusicLinked
            } catch (e: Exception) {
                Log.e(TAG, "[DD MUSIC] Kon koppeling niet lezen voor trigger $playbackTriggerId", e)
                false
            }
        }
        if (linked == null) {
            Log.w(TAG, "[DD MUSIC] Koppeling uitlezen duurde te lang - ingebouwd geluid start direct")
            return false
        }
        if (!linked) return false
        if (!DdMusicBridge.isInstalled(applicationContext)) {
            Log.w(TAG, "[DD MUSIC] Gekoppeld maar niet geïnstalleerd - ingebouwd geluid start direct")
            return false
        }
        return true
    }

    /** Speelt er op dit moment iets op de media-stream (waar DD Music altijd op speelt)? */
    private fun isMusicStreamActive(): Boolean =
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
        } catch (_: Exception) {
            false
        }

    /**
     * Vangnet voor het geval DD Music de telefoon-output is maar niet (op tijd) hoorbaar wordt.
     *
     * Fase 1: wachten tot [tryStartDdMusicForTrigger] de Intent verstuurd heeft ([ddMusicLaunched]).
     * Bij SMART_ALARM zit de AlarmOutputDecisionEngine daar nog vóór, vandaar het ruime venster.
     * Fase 2: na het versturen nog even wachten tot er daadwerkelijk audio op de media-stream staat.
     *
     * Komt er in beide fases niets, dan start het ingebouwde alarmgeluid alsnog. De watchdog wordt
     * geannuleerd zodra een andere route de output overneemt (HA-speaker-only, of
     * [ensurePhoneAlarmPlayback]).
     */
    private fun armDdMusicWatchdog(playbackTriggerId: String?, alarmItem: AlarmItem) {
        ddMusicWatchdogJob?.cancel()
        ddMusicLaunched = false
        ddMusicWatchdogJob = CoroutineScope(Dispatchers.IO).launch {
            val launchDeadline = SystemClock.elapsedRealtime() + DD_MUSIC_LAUNCH_TIMEOUT_MS
            while (isActive && !ddMusicLaunched && SystemClock.elapsedRealtime() < launchDeadline) {
                delay(DD_MUSIC_WATCHDOG_POLL_MS)
            }
            if (!isActive) return@launch

            if (ddMusicLaunched) {
                val audioDeadline = SystemClock.elapsedRealtime() + DD_MUSIC_AUDIO_TIMEOUT_MS
                while (isActive && SystemClock.elapsedRealtime() < audioDeadline) {
                    if (isMusicStreamActive()) {
                        Log.i(TAG, "[DD MUSIC WATCHDOG] DD Music is hoorbaar - ingebouwd geluid blijft uit")
                        return@launch
                    }
                    delay(DD_MUSIC_WATCHDOG_POLL_MS)
                }
                if (!isActive) return@launch
                Log.w(TAG, "[DD MUSIC WATCHDOG] Gestart maar geen audio binnen ${DD_MUSIC_AUDIO_TIMEOUT_MS}ms - ingebouwd alarmgeluid alsnog starten")
            } else {
                Log.w(TAG, "[DD MUSIC WATCHDOG] Niet gestart binnen ${DD_MUSIC_LAUNCH_TIMEOUT_MS}ms - ingebouwd alarmgeluid alsnog starten")
            }

            if (!isLocalRingtonePlaying()) {
                playRingtoneAndVibrate(playbackTriggerId, alarmItem)
            }
        }
    }

    private fun cancelDdMusicWatchdog(reason: String) {
        if (ddMusicWatchdogJob != null) {
            Log.d(TAG, "[DD MUSIC WATCHDOG] Geannuleerd ($reason)")
        }
        ddMusicWatchdogJob?.cancel()
        ddMusicWatchdogJob = null
    }

    /**
     * Same sound resolution as [playRingtoneAndVibrate] (trigger → item snapshot → global).
     */
    private suspend fun resolvePlaybackSoundUriString(playbackTriggerId: String?, alarmItem: AlarmItem): String? {
        var soundUriString: String? = null
        if (!playbackTriggerId.isNullOrBlank()) {
            try {
                val storage = TriggerBehaviorStorage(this)
                val config = storage.getConfig(playbackTriggerId)
                soundUriString = config.alarmSoundUri
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving playback sound from trigger $playbackTriggerId", e)
            }
        }
        if (soundUriString.isNullOrBlank()) {
            soundUriString = alarmItem.soundUri
        }
        if (soundUriString.isNullOrBlank()) {
            soundUriString = SettingsManager.getAlarmSoundUri(this)
        }
        return soundUriString
    }

    /**
     * Start DD Music lokaal op de telefoon voor deze trigger als
     * [com.dd.daykit.rules.TriggerRulesConfig.ddMusicLinked] aan staat. Wordt alleen
     * aangeroepen vanuit call-sites waar de telefoon daadwerkelijk output moet geven (standalone
     * alarm zonder HA-koppeling, of AlarmOutput.PhoneOnly/PhoneAndExternal) - bij een HA-speaker-only
     * uitkomst (ExternalDefault/ExternalBackup) blijft de telefoon bewust stil en wordt dit NIET
     * aangeroepen, ook al is de trigger aan DD Music gekoppeld (zie [resolveExternalSpeakerSoundUrl]
     * voor hoe het gekozen nummer dan wél naar de HA-speaker gaat).
     *
     * Is DD Music de bedoelde telefoon-output (gekoppeld + geïnstalleerd), dan is het ingebouwde
     * alarmgeluid bewust NIET gestart - dat gaf een korte, hoorbare "disco"-burst vlak vóór DD
     * Music. Stilte is toch niet het risico: [armDdMusicWatchdog] start het ingebouwde geluid
     * alsnog als DD Music niet op tijd hoorbaar wordt. In alle andere gevallen speelt het
     * ingebouwde geluid al en stoppen we dat pas als de DD Music-Intent daadwerkelijk verstuurd is.
     */
    private suspend fun tryStartDdMusicForTrigger(playbackTriggerId: String?, alarmItem: AlarmItem?): Boolean {
        if (playbackTriggerId.isNullOrBlank()) return false
        try {
            val storage = TriggerBehaviorStorage(this)
            val config = storage.getConfig(playbackTriggerId)
            if (!config.ddMusicLinked) return false

            if (!DdMusicBridge.isInstalled(applicationContext)) {
                Log.w(TAG, "[DD MUSIC] Niet geïnstalleerd - ingebouwd geluid blijft spelen")
                return false
            }

            // DD Music speelt zelf altijd via de normale media-stream (nooit de ALARM-stream -
            // dat gaf herhaaldelijk problemen met audio-sessies die niet meer goed opstartten).
            // Om toch op het juiste (wekker-)niveau te horen te zijn, nemen we hier het
            // geconfigureerde wekker-volume over naar het media-volume, vlak vóór het starten.
            syncMediaStreamToAlarmVolume(playbackTriggerId, config.alarmVolume)

            val started = DdMusicBridge.launch(applicationContext, playbackTriggerId, alarmItem?.label)
            if (started) {
                // Let op: dit betekent alleen "Intent verstuurd", niet "er komt al geluid uit".
                // De watchdog (fase 2) controleert of DD Music daadwerkelijk hoorbaar wordt.
                ddMusicLaunched = true
                Log.i(TAG, "[DD MUSIC] Gestart voor trigger=$playbackTriggerId - stop ingebouwd geluid")
                stopLocalAlarmPlayback()
                return true
            } else {
                Log.w(TAG, "[DD MUSIC] Starten mislukt - ingebouwd geluid blijft spelen")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "[DD MUSIC] Fout bij starten voor trigger $playbackTriggerId", e)
            return false
        }
    }

    /**
     * Zet het systeem-media-volume (STREAM_MUSIC, waar DD Music altijd op speelt) op hetzelfde
     * niveau als het geconfigureerde wekker-volume voor deze trigger. [configAlarmVolume] is het
     * per-trigger volume (kan null zijn -> dan geldt het globale wekker-volume), dezelfde bron
     * als [playRingtoneAndVibrate] gebruikt voor STREAM_ALARM - zo klinkt DD Music net zo hard
     * als het ingebouwde alarmgeluid zou hebben gedaan.
     */
    private fun syncMediaStreamToAlarmVolume(playbackTriggerId: String?, configAlarmVolume: Int?) {
        try {
            val alarmVolume = configAlarmVolume ?: SettingsManager.getAlarmVolume(this)
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volumeIndex = if (alarmVolume > 0) {
                (maxVolume * (alarmVolume / 100.0f)).toInt().coerceAtLeast(1)
            } else {
                0
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeIndex.coerceAtMost(maxVolume), 0)
            Log.d(TAG, "[DD MUSIC] STREAM_MUSIC overgenomen van wekker-volume ($playbackTriggerId): $alarmVolume% → $volumeIndex/$maxVolume")
        } catch (e: Exception) {
            Log.w(TAG, "[DD MUSIC] Kon media-volume niet overnemen van wekker-volume", e)
        }
    }

    /**
     * URL voor de externe speaker: als DD Music voor deze trigger een specifiek nummer
     * teruggekoppeld heeft (mode "song" in DD Music, zie DdMusicLinkUpdateReceiver), gebruik die
     * URL zodat het gekozen nummer op de HA-speaker te horen is. Anders (geen DD Music-koppeling,
     * of playlist/favorieten/doorgaan - die spelen alleen lokaal op de telefoon) gewoon het
     * bestaande gedrag: eigen backup-geluid of het normale alarmgeluid.
     */
    private suspend fun resolveExternalSpeakerSoundUrl(playbackTriggerId: String?, fallbackSoundUri: String?): String {
        val ddMusicPlayUrl = if (playbackTriggerId.isNullOrBlank()) {
            null
        } else {
            try {
                TriggerBehaviorStorage(this).getConfig(playbackTriggerId).ddMusicPlayUrl?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w(TAG, "[DD MUSIC] Kon ddMusicPlayUrl niet lezen voor trigger $playbackTriggerId", e)
                null
            }
        }
        if (ddMusicPlayUrl != null) {
            Log.d(TAG, "[ALARM OUTPUT] DD Music-nummer wordt gebruikt voor externe speaker: $ddMusicPlayUrl")
            return ddMusicPlayUrl
        }
        return ExternalSpeakerHelper.getExternalSpeakerSoundUrl(applicationContext, SpeakerContext.ALARM)
            ?: ExternalSpeakerHelper.getAlarmSoundUrl(applicationContext, fallbackSoundUri)
    }

    private suspend fun shouldUseHomeAssistantOutput(playbackTriggerId: String?): Boolean {
        if (playbackTriggerId.isNullOrBlank()) return false
        return try {
            val storage = TriggerBehaviorStorage(this)
            val config = storage.getConfig(playbackTriggerId)
            config.mode == TriggerBehaviorMode.SMART_ALARM
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving trigger mode, using standalone phone playback", e)
            false
        }
    }

    private suspend fun playRingtoneAndVibrate(playbackTriggerId: String?, alarmItem: AlarmItem) {
        try {
            var alarmVolume = SettingsManager.getAlarmVolume(this)
            var vibrateEnabled = SettingsManager.getVibrate(this)

            if (!playbackTriggerId.isNullOrBlank()) {
                try {
                    val storage = TriggerBehaviorStorage(this)
                    val config = storage.getConfig(playbackTriggerId)

                    alarmVolume = config.alarmVolume ?: alarmVolume
                    vibrateEnabled = config.vibrate ?: vibrateEnabled

                    Log.d(TAG, "Trigger $playbackTriggerId settings:")
                    Log.d(TAG, "  - Volume: ${config.alarmVolume ?: "(using global: $alarmVolume%)"}")
                    Log.d(TAG, "  - Vibrate: ${config.vibrate ?: "(using global: $vibrateEnabled)"}")
                    Log.d(TAG, "  - Sound: ${config.alarmSoundUri ?: "(using global)"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting trigger config, using global settings", e)
                }
            }

            val soundUriString = resolvePlaybackSoundUriString(playbackTriggerId, alarmItem)
            Log.d(TAG, "Playing ringtone at volume: $alarmVolume% for trigger: $playbackTriggerId")

            if (alarmVolume > 0) {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val volumeIndex = (maxVolume * (alarmVolume / 100.0f)).toInt().coerceAtLeast(1)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volumeIndex, 0)
                Log.d(TAG, "  Volume mapping: $alarmVolume% → $volumeIndex/$maxVolume system volume")

                val soundUri = SettingsManager.resolvePlayableAlarmSoundUri(this, soundUriString)
                
                Log.d(TAG, "Final alarm sound URI: $soundUri")

                ringtone = RingtoneManager.getRingtone(this, soundUri)
                ringtone?.let {
                    it.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    it.isLooping = true
                    it.play()
                    Log.d(TAG, "Ringtone started")
                }
            }

            // Use vibrateEnabled from trigger config (already loaded above)
            if (vibrateEnabled) {
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 500, 500)
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
                }
                Log.d(TAG, "Vibration started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing ringtone or vibrating", e)
        }
    }

    override fun onDestroy() {
        val completedAlarm = AlarmStateManager.ringingAlarmNow
            ?: AgendaAlarmCompletionCoordinator.peekPendingCompletionAlarm()
            ?: restoreRingingAlarm()
        Log.d(TAG, "AlarmService onDestroy completedAlarm=${completedAlarm?.id}")

        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }

        cancelDdMusicWatchdog("on_destroy")

        try {
            ringtone?.stop()
            vibrator?.cancel()
            wakeLock?.release()

            currentExternalSpeakerEntityId?.let { speakerId ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ExternalSpeakerHelper.stopAlarmOnSpeaker(applicationContext, speakerId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping external speaker", e)
                    }
                }
            }

            // Dead-man's-switch: tell HA the alarm is over so its watchdog (alarm_actief) turns
            // off too — otherwise a backup that HA already started keeps looping. Best-effort.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settingsStorage = HomeAssistantSettingsStorage(applicationContext)
                    val repository = HomeAssistantRepository(HomeAssistantClient, settingsStorage)
                    val haSettings = repository.getSettings()
                    if (haSettings.alarmSpeaker.mode != com.dd.daykit.data.ExternalSpeakerMode.DISABLED &&
                        !haSettings.alarmSpeaker.entityId.isNullOrBlank()
                    ) {
                        val result = repository.reportAlarmStop()
                        if (result is HaUpdateResult.Error) {
                            Log.w(TAG, "[HA WATCHDOG] reportAlarmStop failed: ${result.message}")
                        } else {
                            Log.d(TAG, "[HA WATCHDOG] reportAlarmStop reported")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[HA WATCHDOG] reportAlarmStop failed", e)
                }
            }

            externalSpeakerJob?.cancel()

            AgendaAlarmCompletionCoordinator.scheduleNextAfterCompletion(
                applicationContext,
                source = "service_on_destroy",
                completedAlarm = completedAlarm,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }

        AlarmStateManager.onAlarmStop()
        clearRingingAlarm()
        super.onDestroy()
    }
}
