package com.dd.daykit

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.homeassistant.HomeAssistantSync
import com.dd.daykit.rules.TriggerBehaviorMode
import com.dd.daykit.rules.TriggerBehaviorStorage
import com.dd.daykit.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AlarmScheduler {

    const val MAIN_ALARM_REQUEST_CODE = 0
    const val SNOOZE_ALARM_REQUEST_CODE = 1
    const val PRE_ALARM_REQUEST_CODE = 2
    /** Start van "Popup laatste x" — [AgendaAlarmPrePopupReceiver]. */
    const val PRE_POPUP_REQUEST_CODE = 4
    /** Start van "Zonder knoppen" melding — [ButtonlessNotificationReceiver]. */
    const val BUTTONLESS_NOTIFICATION_REQUEST_CODE = 5
    /**
     * Basis voor de losse metingen van de uit-bed-voorcheck (40, 41, 42). Ruim buiten de bestaande
     * codes 0–5 gekozen zodat er geen botsing kan ontstaan als er later codes bijkomen.
     */
    const val PRE_ALARM_SAMPLE_REQUEST_CODE_BASE = 40
    private const val TAG = "AlarmScheduler"

    /** Serialise reschedule cycles so overlapping syncs/UI refreshes cannot interleave cancel + schedule. */
    private val scheduleMutex = Mutex()

    /** TEMP: last main alarm written by [scheduleExactAlarm] (for [cancelMainAlarm] debug). */
    @Volatile
    var lastScheduledMainAlarm: AlarmItem? = null

    // Pre-alarm check fires 5 minutes before the main alarm
    const val PRE_ALARM_OFFSET_MS = 5 * 60 * 1000L

    // Grace period after the scheduled alarm time before HA's own watchdog (daykit
    // custom integration) is allowed to take over with the backup speaker. Gives the phone a
    // moment to fire and report in via reportAlarmAlive() before HA assumes it dropped out.
    const val BACKUP_WATCHDOG_GRACE_MS = 90 * 1000L

    /**
     * CalendarContract instances can lag briefly behind PROVIDER_CHANGED. Short retries catch
     * new events that are not visible on the first query (common for “alarm over 1 minuut” tests).
     */
    private suspend fun fetchNextSchedulableWithProviderRetry(context: Context): Pair<AlarmItem?, Int> {
        val maxAttempts = 6
        val pauseMs = 400L
        repeat(maxAttempts) { attempt ->
            val picked = withContext(Dispatchers.IO) {
                getNextSchedulableWakeUpEvent(context)
            }
            if (picked != null) {
                if (attempt > 0) {
                    Log.i(
                        TAG,
                        "AgendaAlarmSync: next schedulable alarm after ${attempt} provider retry(ies) id=${picked.id} at=${picked.epochMillis} deltaMs=${picked.epochMillis - System.currentTimeMillis()}"
                    )
                }
                return picked to (attempt + 1)
            }
            if (attempt < maxAttempts - 1) {
                Log.d(
                    TAG,
                    "AgendaAlarmSync: no schedulable alarm yet (attempt ${attempt + 1}/$maxAttempts), retry in ${pauseMs}ms"
                )
                delay(pauseMs)
            }
        }
        Log.w(TAG, "AgendaAlarmSync: no schedulable alarm after $maxAttempts provider reads")
        return null to maxAttempts
    }

    suspend fun scheduleNextAlarm(context: Context, source: String = "unknown"): AlarmItem? = scheduleMutex.withLock {
        AgendaMoveDebugLog.scheduleNextAlarmStart(source)
        return try {
            val tStart = android.os.SystemClock.elapsedRealtime()
            Log.d(TAG, "AgendaAlarmSync: scheduleNextAlarm start source=$source")
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.SCHEDULE,
                "START source=$source",
                context
            )
            val (nextAlarm, fetchAttemptsUsed) = fetchNextSchedulableWithProviderRetry(context)
            AgendaMoveDebugLog.scheduleNextAlarmAfterFetch(source, nextAlarm, fetchAttemptsUsed)
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.SCHEDULE,
                "AFTER_FETCH source=$source attempts=$fetchAttemptsUsed picked=${AgendaAlarmForensics.describe(nextAlarm)}",
                context
            )

            // Cancel any previously scheduled main alarm and pre-alarm check.
            // NB: dit ontwapent het bestaande alarm vóórdat vaststaat dat er een vervanger komt —
            // valt de planning hieronder om, dan staat er dus even niets meer. Daarom loggen.
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.SCHEDULE,
                "DISARM_BEFORE_RESCHEDULE source=$source previous=${AgendaAlarmForensics.describe(lastScheduledMainAlarm)}",
                context
            )
            cancelAlarmBroadcastOnly(context, MAIN_ALARM_REQUEST_CODE)
            cancelPreAlarmCheck(context)
            cancelAgendaPrePopupAlarm(context)
            AgendaAlarmPopupForegroundService.stopPopup(context.applicationContext, "schedule_cycle")
            cancelButtonlessNotificationAlarm(context)
            ButtonlessNotificationService.stopNotification(context.applicationContext, "schedule_cycle")

            if (nextAlarm != null) {
                val nowMs = System.currentTimeMillis()
                // Zelfde grace als [getNextSchedulableWakeUpEvent]: start net in verleden = alsnog plannen (AlarmManager vuurt dan direct).
                if (nextAlarm.epochMillis > nowMs - AGENDA_ALARM_BEGIN_PAST_GRACE_MS) {
                    Log.i(
                        TAG,
                        "AgendaAlarmSync: next alarm '${nextAlarm.label}' id=${nextAlarm.id} at=${nextAlarm.epochMillis} deltaMs=${nextAlarm.epochMillis - nowMs} (cycle ${android.os.SystemClock.elapsedRealtime() - tStart}ms)"
                    )

                    // Reset alreadyFired for ONE_TIME alarms so they can be reused
                    nextAlarm.triggerId?.let { triggerId ->
                        try {
                            val storage = TriggerBehaviorStorage(context)
                            storage.resetFiredStatus(triggerId)
                            Log.d(TAG, "Reset alreadyFired for trigger: $triggerId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error resetting fired status", e)
                        }
                    }

                    val triggerConfig = nextAlarm.triggerId?.let { triggerId ->
                        TriggerBehaviorStorage(context).getConfig(triggerId)
                    }
                    val defaultSoundUri = SettingsManager.getAlarmSoundUri(context)
                    val candidateSoundUri = triggerConfig?.alarmSoundUri ?: defaultSoundUri
                    val soundUri = SettingsManager.resolvePlayableAlarmSoundUri(context, candidateSoundUri).toString()
                    val alarmWithSound = nextAlarm.copy(soundUri = soundUri)
                    if (!scheduleExactAlarm(context, alarmWithSound, MAIN_ALARM_REQUEST_CODE)) {
                        Log.e(TAG, "Failed to schedule main alarm (exact alarm not set)")
                        AgendaMoveDebugLog.scheduleNextAlarmExit(source, "schedule_exact_failed", "picked=${AgendaMoveDebugLog.formatAlarm(alarmWithSound)}")
                        AgendaMoveDebugLog.scheduleNextAlarmResult(source, null, "schedule_exact_failed")
                        AgendaAlarmForensics.log(
                            AgendaAlarmForensics.Cat.SCHEDULE,
                            "END source=$source result=EXACT_ALARM_FAILED — NIETS GEWAPEND, alarm gaat niet af. " +
                                "picked=${AgendaAlarmForensics.describe(alarmWithSound)}",
                            context
                        )
                        return null
                    }

                    // Schedule Home Assistant pre-alarm checks only for SMART_ALARM.
                    if (triggerConfig?.mode == TriggerBehaviorMode.SMART_ALARM) {
                        schedulePreAlarmCheck(context, alarmWithSound)
                    } else {
                        cancelPreAlarmCheck(context)
                    }

                    // Sync only SMART_ALARM with Home Assistant; NORMAL is volledig standalone.
                    if (triggerConfig?.mode == TriggerBehaviorMode.SMART_ALARM) {
                        val haSettingsStorage = HomeAssistantSettingsStorage(context)
                        val haSettings = haSettingsStorage.settingsFlow.firstOrNull()
                        if (haSettings != null && !haSettings.activeBaseUrl.isNullOrBlank()) {
                        
                            // Generate Sound URL for Home Assistant
                            var soundUrl: String? = null
                            if (com.dd.daykit.sound.HttpServerManager.isEnabled(context)) {
                                try {
                                    val serverUrl = com.dd.daykit.sound.HttpServerManager.getFullServerUrl(context)
                                    val apiKey = com.dd.daykit.sound.HttpServerManager.getApiKey(context)
                                    val uriString = alarmWithSound.soundUri
                                    
                                    if (uriString != null) {
                                        val uri = android.net.Uri.parse(uriString)
                                        
                                        // Case 1: System Content URI
                                        if (uri.scheme == "content" || uri.scheme == "android.resource") {
                                            val encodedUri = java.net.URLEncoder.encode(uriString, "UTF-8")
                                            soundUrl = "$serverUrl/ringtone?uri=$encodedUri&api_key=$apiKey"
                                        } 
                                        // Case 2: Custom File
                                        else if (uri.scheme == "file" || uri.path?.contains("alarm_sounds/custom") == true) {
                                            // Try to find the sound in DB to get its ID
                                            val filename = java.io.File(uri.path ?: "").name
                                            val db = com.dd.daykit.database.AppDatabase.getDatabase(context)
                                            val sound = db.customAlarmSoundDao().getSoundByFilename(filename)
                                            
                                            if (sound != null) {
                                                soundUrl = "$serverUrl/sounds/${sound.id}?api_key=$apiKey"
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error generating sound URL for HA", e)
                                }
                            }

                            // Determine backup sound URL from selected local sound (agenda-alarm-
                            // specifieke speaker-instellingen, zie SpeakerContext.ALARM)
                            val backupSoundUrl: String? = if (haSettings.alarmSpeaker.soundId != null) {
                                val soundRepo = com.dd.daykit.sound.SoundRepository.getInstance(context)
                                val sound = soundRepo.sounds.value.find { it.id == haSettings.alarmSpeaker.soundId }
                                if (sound != null) {
                                    val baseUrl = haSettings.activeBaseUrl?.trimEnd('/')
                                    val safeName = com.dd.daykit.homeassistant.SoundHaSync.safeUploadedFilename(sound)
                                    if (!baseUrl.isNullOrBlank()) {
                                        com.dd.daykit.homeassistant.HaPaths.soundUrl(baseUrl, safeName)
                                    } else null
                                } else null
                            } else null

                            // Determine speaker mode string
                            val speakerModeStr = when (haSettings.alarmSpeaker.mode) {
                                com.dd.daykit.data.ExternalSpeakerMode.DISABLED -> "Off"
                                com.dd.daykit.data.ExternalSpeakerMode.DEFAULT -> "Standard"
                                com.dd.daykit.data.ExternalSpeakerMode.BACKUP_ONLY -> "Backup"
                                com.dd.daykit.data.ExternalSpeakerMode.BOTH -> "Both"
                            }
                            
                            HomeAssistantSync.syncNextAlarm(
                                context = context,
                                alarmTimeMillis = alarmWithSound.epochMillis,
                                alarmName = alarmWithSound.label,
                                soundUrl = soundUrl, // Pass the generated URL
                                enabled = true,
                                alarmSpeakerEntity = haSettings.alarmSpeaker.entityId,
                                // Backup alarm settings for HA fallback when phone is unavailable
                                backupVolume = haSettings.alarmSpeaker.volume,
                                backupAlarmDuration = haSettings.backupAlarmDuration,
                                backupSoundUrl = backupSoundUrl,
                                batteryUsagePerHour = SettingsManager.getBatteryUsagePerHour(context),
                                speakerMode = speakerModeStr
                            )

                            // Dead-man's-switch: wapen de HA-integratie ("daykit") voor het
                            // aankomende alarm — één aanroep i.p.v. de losse reset/mobiel_betrouwbaar/
                            // backup_alarm_time/gebruiker_thuis-calls van de handmatige opzet hierboven.
                            // De integratie zelf reset zijn eigen state bij "arm" en leest aanwezigheid
                            // live uit (person/device_tracker, indien geconfigureerd) — geen losse
                            // gebruiker_thuis-sync meer nodig. Als de telefoon het alarm echt afspeelt,
                            // meldt AlarmService dit terug via reportAlarmAlive(). Zo niet (crash/geen
                            // netwerk/killed), neemt de watchdog na fire_at het over met de fallback-speaker.
                            //
                            // Onvoorwaardelijk (geen check op alarmSpeaker.mode/alarmSpeaker.entityId):
                            // deze aanroep voedt ook de altijd-actieve phone_ip/ping-detectie
                            // (last_known_phone_ip -> binary_sensor.py), die niets met de externe-speaker-
                            // functie te maken heeft. Alleen gegate op "is er een HA-koppeling" (haSettings
                            // != null && activeBaseUrl niet leeg, al gecheckt op regel 143 hierboven).
                            try {
                                val haRepository = com.dd.daykit.data.HomeAssistantRepository(
                                    com.dd.daykit.network.HomeAssistantClient,
                                    haSettingsStorage
                                )
                                val fireAtFormat = java.text.SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault()
                                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                val fireAtIso = fireAtFormat.format(
                                    java.util.Date(alarmWithSound.epochMillis + BACKUP_WATCHDOG_GRACE_MS)
                                )
                                val batteryPercent = AlarmOutputDecisionEngine.getBatteryPercent(context)
                                val batteryUsagePerHour = SettingsManager.getBatteryUsagePerHour(context)
                                val phoneIp = AlarmOutputDecisionEngine.getPhoneIpAddress(context)
                                val result = haRepository.armBackupWatchdog(
                                    fireAtIso = fireAtIso,
                                    fallbackSpeaker = haSettings.alarmSpeaker.entityId,
                                    fallbackVolume = haSettings.alarmSpeaker.volume,
                                    fallbackSoundUrl = backupSoundUrl,
                                    fallbackInterval = haSettings.backupAlarmDuration,
                                    batteryPercent = batteryPercent,
                                    batteryUsagePerHour = batteryUsagePerHour,
                                    phoneIp = phoneIp
                                )
                                if (result is com.dd.daykit.data.HaUpdateResult.Error) {
                                    Log.w(TAG, "HA watchdog arm FAILED (integratie nog niet geinstalleerd/bereikbaar?): ${result.message}")
                                } else {
                                    Log.d(TAG, "HA watchdog armed via daykit: fire_at=$fireAtIso speaker=${haSettings.alarmSpeaker.entityId}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error arming HA backup watchdog", e)
                            }
                        }
                    } else {
                        Log.d(TAG, "HA sync skipped for standalone NORMAL alarm")
                    }
                    
                    // Cache alarm time and ID for GlobalInAppMessageManager
                    GlobalInAppMessageManager.cacheNextAlarmTime(
                        context,
                        alarmWithSound.epochMillis,
                        alarmWithSound.id,
                        alarmWithSound.label
                    )

                    scheduleOrStartAgendaPrePopup(context, alarmWithSound)
                    scheduleOrStartButtonlessNotification(context, alarmWithSound)

                    AgendaMoveDebugLog.scheduleNextAlarmExit(
                        source,
                        "schedule_success",
                        "scheduled=${AgendaMoveDebugLog.formatAlarm(alarmWithSound)} fetchAttempts=$fetchAttemptsUsed",
                    )
                    AgendaMoveDebugLog.scheduleNextAlarmResult(source, alarmWithSound, "scheduled_ok")
                    AgendaAlarmForensics.rememberExpectedAlarm(context, alarmWithSound)
                    AgendaAlarmForensics.log(
                        AgendaAlarmForensics.Cat.SCHEDULE,
                        "END source=$source result=SCHEDULED ${AgendaAlarmForensics.describe(alarmWithSound)} " +
                            "mode=${triggerConfig?.mode}",
                        context
                    )
                    AgendaAlarmForensics.snapshotArmedState(context, "after_schedule_$source")
                    NotificationPopupDebugLog.consistencySnapshot(
                        source = "scheduleNextAlarm_success_$source",
                        schedulerPicked = alarmWithSound,
                        cacheEpoch = alarmWithSound.epochMillis,
                        lastScheduledEpoch = lastScheduledMainAlarm?.epochMillis,
                    )
                    nextAlarm
                } else {
                    Log.w(
                        TAG,
                        "AgendaAlarmSync: alarm start is older than grace (${AGENDA_ALARM_BEGIN_PAST_GRACE_MS}ms), not scheduling id=${nextAlarm.id} at=${nextAlarm.epochMillis}"
                    )
                    AgendaMoveDebugLog.scheduleNextAlarmExit(
                        source,
                        "picked_older_than_grace",
                        "picked=${AgendaMoveDebugLog.formatAlarm(nextAlarm)} graceMs=$AGENDA_ALARM_BEGIN_PAST_GRACE_MS",
                    )
                    AgendaMoveDebugLog.scheduleNextAlarmResult(source, nextAlarm, "picked_older_than_grace_not_scheduled")
                    AgendaAlarmForensics.log(
                        AgendaAlarmForensics.Cat.SCHEDULE,
                        "END source=$source result=PICKED_OLDER_THAN_GRACE — NIETS GEWAPEND. " +
                            "picked=${AgendaAlarmForensics.describe(nextAlarm)} graceMs=$AGENDA_ALARM_BEGIN_PAST_GRACE_MS",
                        context
                    )
                    null
                }
            } else {
                val futureExists = hasStrictFutureSchedulableWakeUp(context.applicationContext)
                Log.w(
                    TAG,
                    "AgendaAlarmSync: no schedulable row after retries (cycle ${android.os.SystemClock.elapsedRealtime() - tStart}ms) strictFutureExists=$futureExists fetchAttempts=$fetchAttemptsUsed"
                )
                if (!futureExists) {
                    HomeAssistantSync.clearNextAlarm(context)
                    GlobalInAppMessageManager.cacheNextAlarmTime(context, null)
                    cancelAgendaPrePopupAlarm(context)
                    AgendaAlarmPopupForegroundService.stopPopup(context.applicationContext, "no_next_alarm")
                    cancelButtonlessNotificationAlarm(context)
                    ButtonlessNotificationService.stopNotification(context.applicationContext, "no_next_alarm")
                    disarmHaBackupWatchdogBestEffort(context, "no_next_alarm")
                    AgendaMoveDebugLog.scheduleNextAlarmExit(
                        source,
                        "no_events",
                        "fetchAttempts=$fetchAttemptsUsed strictFutureExists=false",
                    )
                    AgendaMoveDebugLog.scheduleNextAlarmResult(source, null, "no_schedulable_strict_future_false")
                    AgendaAlarmForensics.rememberExpectedAlarm(context, null)
                    AgendaAlarmForensics.log(
                        AgendaAlarmForensics.Cat.SCHEDULE,
                        "END source=$source result=NO_EVENTS — geen agenda-item meer, planning bewust leeggemaakt.",
                        context
                    )
                } else {
                    Log.e(
                        TAG,
                        "AgendaAlarmSync: fetch returned null but strict-future calendar events exist — skipping cache/HA clear (provider/sync race)"
                    )
                    AgendaMoveDebugLog.scheduleNextAlarmExit(
                        source,
                        "no_candidate",
                        "fetchAttempts=$fetchAttemptsUsed strictFutureExists=true provider_race",
                    )
                    AgendaMoveDebugLog.scheduleNextAlarmResult(source, null, "no_schedulable_provider_race_strict_future_true")
                    // Dit is de gevaarlijkste tak: er zíjn toekomstige agenda-items, maar de provider
                    // gaf ze even niet terug. Het oude alarm is hierboven al ontwapend en er komt nu
                    // géén vervanger — tot de volgende sync. Blijft dit staan in de log op het moment
                    // dat een alarm miste, dan is dit de oorzaak.
                    AgendaAlarmForensics.log(
                        AgendaAlarmForensics.Cat.SCHEDULE,
                        "END source=$source result=PROVIDER_RACE — NIETS GEWAPEND terwijl er wél toekomstige " +
                            "agenda-items zijn. Alarm blijft ongewapend tot de volgende sync.",
                        context
                    )
                    val staleCache = runCatching {
                        context.getSharedPreferences("alarm_cache", Context.MODE_PRIVATE)
                            .getLong("next_alarm_time", -1L).takeIf { it > 0 }
                    }.getOrNull()
                    NotificationPopupDebugLog.consistencySnapshot(
                        source = "scheduleNextAlarm_provider_race_$source",
                        schedulerPicked = null,
                        cacheEpoch = staleCache,
                        lastScheduledEpoch = lastScheduledMainAlarm?.epochMillis,
                    )
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling next alarm", e)
            AgendaMoveDebugLog.scheduleNextAlarmExit(source, "exception", e.javaClass.simpleName)
            AgendaMoveDebugLog.scheduleNextAlarmResult(source, null, "exception:${e.javaClass.simpleName}")
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.SCHEDULE,
                "END source=$source result=EXCEPTION ${e.javaClass.simpleName}: ${e.message} — NIETS GEWAPEND.",
                context
            )
            null
        }
    }

    /**
     * Schedules the snooze broadcast/clock and persists [AgendaSnoozeStore] so state survives
     * process death and can be restored after reboot.
     *
     * @param triggerAtMillis optional absolute fire time (for restore); default = now + user snooze minutes
     * @return epoch millis when the snooze will fire, or null if scheduling failed
     */
    suspend fun scheduleSnooze(
        context: Context,
        originalAlarm: AlarmItem? = null,
        triggerAtMillis: Long? = null
    ): Long? {
        return try {
            val app = context.applicationContext
            val decision = AgendaAlarmSnoozeCoordinator.evaluateNextSnooze(app, originalAlarm)
            val chainSource = decision.chainSourceTriggerId
            Log.i(
                TAG,
                "SNOOZE_SCHED begin originalId=${originalAlarm?.id} originalTrigger=${originalAlarm?.triggerId} " +
                    "snoozeSourceTrigger=${originalAlarm?.snoozeSourceTriggerId} resolvedChainSource=$chainSource " +
                    "decision=${decision.reason} count=${decision.currentCount}/${decision.maxCount ?: "unlimited"}"
            )
            if (!decision.allowed) {
                Log.w(
                    TAG,
                    "SNOOZE_SCHED blocked reason=${decision.reason} minutes=${decision.minutes} " +
                        "count=${decision.currentCount} max=${decision.maxCount}"
                )
                return null
            }
            val snoozeMinutes = decision.minutes
            val triggerAt = triggerAtMillis
                ?: (System.currentTimeMillis() + snoozeMinutes * 60 * 1000L)
            LanguageManager.init(app)
            val snoozeDisplayLabel = originalAlarm?.label?.trim()?.takeIf { it.isNotEmpty() }
                ?: LanguageManager.getString("alarm_snooze")
            // Geen triggerId: sluimer is geen agenda-instantie — voorkomt dat RuleEngine het sluimer-alarm blokkeert.
            cancelMainAlarmScheduleAndPrePopup(app)
            val snoozeAlarm = AlarmItem(
                id = System.currentTimeMillis(),
                epochMillis = triggerAt,
                label = snoozeDisplayLabel,
                triggerId = null,
                soundUri = originalAlarm?.soundUri,
                snoozeSourceTriggerId = chainSource
            )
            Log.i(TAG, "SNOOZE_SCHED at=$triggerAt (${snoozeMinutes}m) label=${snoozeAlarm.label} chainSource=$chainSource")
            val json = Json.encodeToString(AlarmItem.serializer(), snoozeAlarm)
            AgendaSnoozeStore.persist(
                app,
                triggerAt,
                json,
                chainSource,
                decision.nextCount
            )
            if (!scheduleExactAlarm(app, snoozeAlarm, SNOOZE_ALARM_REQUEST_CODE)) {
                AgendaSnoozeStore.clear(app)
                AlarmStateManager.onSnoozeCancel()
                SnoozePopupForegroundService.stopPopup(app, "snooze_schedule_failed")
                return null
            }
            NotificationPopupDebugLog.consistencySnapshot(
                source = "scheduleSnooze_success",
                schedulerPicked = snoozeAlarm,
                cacheEpoch = runCatching {
                    app.getSharedPreferences("alarm_cache", Context.MODE_PRIVATE)
                        .getLong("next_alarm_time", -1L).takeIf { it > 0 }
                }.getOrNull(),
                lastScheduledEpoch = lastScheduledMainAlarm?.epochMillis,
            )
            triggerAt
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling snooze", e)
            AgendaSnoozeStore.clear(context.applicationContext)
            AlarmStateManager.onSnoozeCancel()
            null
        }
    }

    /**
     * Cancels snooze PendingIntent only. Does **not** touch Home Assistant next-alarm sync
     * (unlike main alarm cancellation).
     */
    fun cancelSnoozeAlarm(context: Context) {
        cancelAlarmBroadcastOnly(context, SNOOZE_ALARM_REQUEST_CODE)
    }

    /**
     * Main alarm PendingIntent + pre-alarm + agenda pre-popup, **zonder** HA-clear.
     * Gebruikt bij sluimeren zodat de oude hoofd-PI niet naast sluimer blijft hangen.
     */
    fun cancelMainAlarmScheduleAndPrePopup(context: Context) {
        val app = context.applicationContext
        cancelAlarmBroadcastOnly(app, MAIN_ALARM_REQUEST_CODE)
        cancelPreAlarmCheck(app)
        cancelAgendaPrePopupAlarm(app)
        cancelButtonlessNotificationAlarm(app)
        Log.i(TAG, "MAIN_SCHEDULE_CANCEL (no HA clear): main PI + pre-popup + buttonless cleared")
    }

    /**
     * Cancels the main calendar alarm PendingIntent and clears HA "next alarm" mirror.
     */
    fun cancelMainAlarm(context: Context) {
        AgendaMoveDebugLog.cancelMainAlarm(lastScheduledMainAlarm)
        cancelAlarmBroadcastOnly(context, MAIN_ALARM_REQUEST_CODE)
        CoroutineScope(Dispatchers.IO).launch {
            HomeAssistantSync.clearNextAlarm(context)
        }
        disarmHaBackupWatchdogBestEffort(context, "cancel_main_alarm")
    }

    /**
     * Best-effort ontwapening van de HA-watchdog wanneer een eerder gewapend alarm geannuleerd
     * wordt zonder dat er meteen een nieuw volgend alarm gepland wordt. Faalt stil (net als de
     * andere HA-aanroepen hier) als er geen HA-verbinding is.
     */
    private fun disarmHaBackupWatchdogBestEffort(context: Context, reason: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val haSettingsStorage = HomeAssistantSettingsStorage(context.applicationContext)
                val haRepository = com.dd.daykit.data.HomeAssistantRepository(
                    com.dd.daykit.network.HomeAssistantClient,
                    haSettingsStorage
                )
                val result = haRepository.disarmBackupWatchdog()
                if (result is com.dd.daykit.data.HaUpdateResult.Error) {
                    Log.w(TAG, "HA watchdog disarm ($reason) FAILED: ${result.message}")
                } else {
                    Log.d(TAG, "HA watchdog disarmed ($reason)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error disarming HA backup watchdog ($reason)", e)
            }
        }
    }

    /** User cancelled snooze from UI: clear prefs, cancel PI, reset in-memory state. */
    fun cancelAgendaSnoozeUser(context: Context, skipScheduleRefresh: Boolean = false) {
        val app = context.applicationContext
        SnoozePopupForegroundService.stopPopup(app, "cancel_snooze_user")
        AgendaSnoozeStore.clear(app)
        cancelSnoozeAlarm(app)
        AlarmStateManager.onSnoozeCancel()
        if (skipScheduleRefresh) {
            Log.d(TAG, "cancelAgendaSnoozeUser: skipScheduleRefresh=true (caller refreshes main)")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduleNextAlarm(app, "cancel_agenda_snooze_user")
                Log.i(TAG, "cancelAgendaSnoozeUser: scheduleNextAlarm completed")
            } catch (e: Exception) {
                Log.e(TAG, "cancelAgendaSnoozeUser: scheduleNextAlarm failed", e)
            }
            try {
                app.sendBroadcast(
                    Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED).apply {
                        setPackage(app.packageName)
                    }
                )
            } catch (_: Exception) {
            }
        }
    }

    /**
     * After reboot or cold process start: if prefs contain a future snooze, reschedule AlarmManager
     * and sync [AlarmStateManager].
     */
    fun restorePendingSnoozeIfNeeded(context: Context) {
        val app = context.applicationContext
        try {
            val end = AgendaSnoozeStore.readEndMillis(app)
            if (end <= 0L) {
                if (AlarmStateManager.isSnoozeActive.value) {
                    AlarmStateManager.onSnoozeCancel()
                }
                return
            }
            val now = System.currentTimeMillis()
            if (end <= now) {
                Log.d(TAG, "restorePendingSnooze: expired end=$end, clearing")
                AgendaSnoozeStore.clear(app)
                cancelSnoozeAlarm(app)
                AlarmStateManager.onSnoozeCancel()
                SnoozePopupForegroundService.stopPopup(app, "restore_snooze_expired")
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { scheduleNextAlarm(app, "restore_pending_snooze_expired") }
                        .onFailure { Log.e(TAG, "restorePendingSnooze expired: scheduleNextAlarm failed", it) }
                    try {
                        app.sendBroadcast(
                            Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED).apply {
                                setPackage(app.packageName)
                            }
                        )
                    } catch (_: Exception) {
                    }
                }
                return
            }
            val json = AgendaSnoozeStore.readAlarmJson(app)
            if (json.isNullOrBlank()) {
                Log.w(TAG, "restorePendingSnooze: missing json, clearing")
                AgendaSnoozeStore.clear(app)
                cancelSnoozeAlarm(app)
                AlarmStateManager.onSnoozeCancel()
                SnoozePopupForegroundService.stopPopup(app, "restore_snooze_missing_json")
                return
            }
            val alarm = try {
                Json.decodeFromString<AlarmItem>(json)
            } catch (e: Exception) {
                Log.e(TAG, "restorePendingSnooze: bad json", e)
                AgendaSnoozeStore.clear(app)
                cancelSnoozeAlarm(app)
                AlarmStateManager.onSnoozeCancel()
                SnoozePopupForegroundService.stopPopup(app, "restore_snooze_bad_json")
                return
            }
            val mergedChainSource = alarm.snoozeSourceTriggerId?.trim()?.takeIf { it.isNotEmpty() }
                ?: AgendaSnoozeStore.readSourceTriggerId(app)?.trim()?.takeIf { it.isNotEmpty() }
            val aligned = alarm.copy(epochMillis = end, snoozeSourceTriggerId = mergedChainSource)
            Log.d(TAG, "restorePendingSnooze: aligned id=${aligned.id} end=$end chain=$mergedChainSource")
            if (!scheduleExactAlarm(app, aligned, SNOOZE_ALARM_REQUEST_CODE)) {
                Log.w(TAG, "restorePendingSnooze: scheduleExactAlarm failed, leaving prefs for retry")
                return
            }
            AlarmStateManager.onSnoozeStart(end)
            SnoozePopupForegroundService.startOrSync(app, "restore_snooze")
            Log.d(TAG, "restorePendingSnooze: rescheduled snooze at $end")
        } catch (e: Exception) {
            Log.e(TAG, "restorePendingSnooze failed", e)
        }
    }

    private fun scheduleExactAlarm(context: Context, alarm: AlarmItem, requestCode: Int): Boolean {
        AgendaMoveDebugLog.scheduleExactAlarm(requestCode, alarm)
        return try {
            Log.d(TAG, "scheduleExactAlarm - requestCode: $requestCode, time: ${alarm.epochMillis}")

            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!am.canScheduleExactAlarms()) {
                    Log.e(TAG, "Cannot schedule exact alarms - permission not granted")
                    AgendaAlarmForensics.log(
                        AgendaAlarmForensics.Cat.ARM,
                        "FAILED requestCode=$requestCode reason=EXACT_ALARM_PERMISSION_REVOKED " +
                            "target=${AgendaAlarmForensics.wall(alarm.epochMillis)}",
                        context
                    )
                    return false
                }
            }

            val ringIntent = Intent(context, RingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("alarm", Json.encodeToString(AlarmItem.serializer(), alarm))
            }

            val showPi = PendingIntent.getActivity(
                context,
                requestCode,
                ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("alarm", Json.encodeToString(AlarmItem.serializer(), alarm))
            }

            val alarmPi = PendingIntent.getBroadcast(
                context,
                requestCode,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmClockInfo = AlarmClockInfo(alarm.epochMillis, showPi)
            am.setAlarmClock(alarmClockInfo, alarmPi)

            if (requestCode == MAIN_ALARM_REQUEST_CODE) {
                lastScheduledMainAlarm = alarm
            }
            Log.d(TAG, "Alarm scheduled successfully")
            // Direct terugvragen wat het OS nú als eerstvolgende alarm-clock houdt. Zo staat zwart op
            // wit of setAlarmClock() echt is aangekomen, in plaats van alleen dat wij het aanriepen.
            val osNext = runCatching { am.nextAlarmClock?.triggerTime }.getOrNull()
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.ARM,
                "OK requestCode=$requestCode target=${AgendaAlarmForensics.wall(alarm.epochMillis)} " +
                    "osNextAlarmClock=${AgendaAlarmForensics.wall(osNext)} " +
                    "accepted=${if (osNext == alarm.epochMillis) "yes" else "OTHER_OR_EARLIER_ALARM"} " +
                    "inMs=${alarm.epochMillis - System.currentTimeMillis()}",
                context
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling exact alarm", e)
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.ARM,
                "FAILED requestCode=$requestCode reason=EXCEPTION ${e.javaClass.simpleName}: ${e.message}",
                context
            )
            false
        }
    }

    private fun cancelAlarmBroadcastOnly(context: Context, requestCode: Int) {
        try {
            Log.d(TAG, "Canceling AlarmReceiver PI requestCode=$requestCode")
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            Log.d(TAG, "Alarm PI canceled (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling alarm PI", e)
        }
    }

    /**
     * Schedule a pre-alarm check at T-5 minutes before the alarm.
     * This is Phase 1 of the out-of-bed detection system.
     *
     * The pre-alarm check:
     * - Runs silently (no sound, no notification)
     * - Evaluates out-of-bed status with sustained duration check
     * - Stores result for AlarmReceiver to use
     * - Fail-safe: any error allows alarm to proceed
     */
    private fun schedulePreAlarmCheck(context: Context, alarm: AlarmItem) {
        try {
            val now = System.currentTimeMillis()
            val firstSampleTime = alarm.epochMillis - PRE_ALARM_OFFSET_MS

            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!am.canScheduleExactAlarms()) {
                    Log.e(TAG, "Cannot schedule exact alarms - permission not granted")
                    return
                }
            }

            // Oude metingen van deze alarm-instantie weggooien; anders telt een vorige (mogelijk
            // afgebroken) reeks mee in de analyse.
            com.dd.daykit.rules.PreAlarmSampleStore(context.applicationContext).clear(alarm.id)

            val json = Json.encodeToString(AlarmItem.serializer(), alarm)
            val total = PreAlarmReceiver.SAMPLE_COUNT
            var scheduled = 0

            // Drie losse, korte alarmen i.p.v. één receiver die drie minuten blijft hangen.
            // Elke meting is een eigen wake-up; valt het proces tussendoor om, dan gaat alleen die
            // ene meting verloren en niet de hele voorcheck.
            for (index in 0 until total) {
                val sampleTime = firstSampleTime + index * PreAlarmReceiver.SAMPLE_INTERVAL_MS
                if (sampleTime <= now) {
                    Log.d(TAG, "Pre-alarm sample $index at $sampleTime already passed, skipping")
                    continue
                }

                val intent = Intent(context, PreAlarmReceiver::class.java).apply {
                    putExtra("alarm", json)
                    putExtra(PreAlarmReceiver.EXTRA_SAMPLE_INDEX, index)
                    putExtra(PreAlarmReceiver.EXTRA_TOTAL_SAMPLES, total)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    PRE_ALARM_SAMPLE_REQUEST_CODE_BASE + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sampleTime, pi)
                scheduled++
            }

            Log.d(TAG, "Pre-alarm check scheduled: $scheduled/$total samples for alarm ${alarm.id}")
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.PRE_ALARM,
                "voorcheck gepland: $scheduled van $total metingen, eerste op ${AgendaAlarmForensics.wall(firstSampleTime)}",
                context
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling pre-alarm check", e)
            // Fail silently - main alarm will still work
        }
    }

    /**
     * Annuleert alle geplande metingen van de voorcheck. Neemt ook de oude enkelvoudige
     * [PRE_ALARM_REQUEST_CODE] mee, zodat een alarm dat nog door een vorige app-versie gepland was
     * niet blijft hangen na een update.
     */
    fun cancelPreAlarmCheck(context: Context) {
        try {
            Log.d(TAG, "Canceling pre-alarm check")
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val codes = mutableListOf(PRE_ALARM_REQUEST_CODE)
            for (index in 0 until PreAlarmReceiver.SAMPLE_COUNT) {
                codes.add(PRE_ALARM_SAMPLE_REQUEST_CODE_BASE + index)
            }
            for (code in codes) {
                val intent = Intent(context, PreAlarmReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    context, code, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pi)
            }
            Log.d(TAG, "Pre-alarm check canceled (${codes.size} pending intents)")
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling pre-alarm check", e)
        }
    }

    fun cancelAgendaPrePopupAlarm(context: Context) {
        try {
            val app = context.applicationContext
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(app, AgendaAlarmPrePopupReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                app,
                PRE_POPUP_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            Log.d(TAG, "Agenda pre-popup alarm canceled")
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling agenda pre-popup alarm", e)
        }
    }

    private fun scheduleOrStartAgendaPrePopup(context: Context, alarm: AlarmItem) {
        val app = context.applicationContext
        if (!SettingsManager.getCalendarPopupLast30Min(app)) {
            cancelAgendaPrePopupAlarm(app)
            AgendaAlarmPopupForegroundService.stopPopup(app, "popup_disabled")
            return
        }
        cancelAgendaPrePopupAlarm(app)
        val windowMin = SettingsManager.getCalendarPopupWindowMinutes(app).coerceAtLeast(1)
        val popupStartTime = alarm.epochMillis - windowMin * 60_000L
        val json = Json.encodeToString(AlarmItem.serializer(), alarm)
        val now = System.currentTimeMillis()
        if (alarm.epochMillis <= now) return

        if (popupStartTime <= now) {
            Handler(Looper.getMainLooper()).post {
                AgendaAlarmPopupForegroundService.startWithAlarmJson(app, json, "inside_window_immediate")
            }
            return
        }
        try {
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!am.canScheduleExactAlarms()) {
                    Log.e(TAG, "Cannot schedule agenda pre-popup — exact alarms not allowed")
                    return
                }
            }
            val intent = Intent(app, AgendaAlarmPrePopupReceiver::class.java).apply {
                putExtra(AgendaAlarmPopupForegroundService.EXTRA_ALARM_JSON, json)
            }
            val pi = PendingIntent.getBroadcast(
                app,
                PRE_POPUP_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, popupStartTime, pi)
            Log.d(TAG, "Agenda pre-popup scheduled at $popupStartTime (${windowMin}m before alarm)")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling agenda pre-popup", e)
        }
    }

    private fun cancelButtonlessNotificationAlarm(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ButtonlessNotificationReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                BUTTONLESS_NOTIFICATION_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            Log.d(TAG, "Buttonless notification alarm canceled")
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling buttonless notification alarm", e)
        }
    }

    private fun scheduleOrStartButtonlessNotification(context: Context, alarm: AlarmItem) {
        val app = context.applicationContext
        if (!SettingsManager.getButtonlessNotificationEnabled(app)) {
            cancelButtonlessNotificationAlarm(app)
            ButtonlessNotificationService.stopNotification(app, "buttonless_disabled")
            return
        }
        cancelButtonlessNotificationAlarm(app)
        val windowMinutes = SettingsManager.getButtonlessNotificationMinutes(app).coerceAtLeast(120)
        val notifStartTime = alarm.epochMillis - windowMinutes * 60_000L
        val json = Json.encodeToString(AlarmItem.serializer(), alarm)
        val now = System.currentTimeMillis()
        if (alarm.epochMillis <= now) return

        if (notifStartTime <= now) {
            Handler(Looper.getMainLooper()).post {
                ButtonlessNotificationService.startWithAlarmJson(app, json, "inside_window_immediate")
            }
            return
        }
        try {
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!am.canScheduleExactAlarms()) {
                    Log.e(TAG, "Cannot schedule buttonless notification — exact alarms not allowed")
                    return
                }
            }
            val intent = Intent(app, ButtonlessNotificationReceiver::class.java).apply {
                putExtra(ButtonlessNotificationService.EXTRA_ALARM_JSON, json)
            }
            val pi = PendingIntent.getBroadcast(
                app,
                BUTTONLESS_NOTIFICATION_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifStartTime, pi)
            Log.d(TAG, "Buttonless notification scheduled at $notifStartTime (${windowMinutes}m before alarm)")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling buttonless notification", e)
        }
    }
}
