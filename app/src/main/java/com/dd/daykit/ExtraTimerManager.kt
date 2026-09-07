package com.dd.daykit

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dd.daykit.data.HaUpdateResult
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

object ExtraTimerManager {

    private const val TAG = "ExtraTimerManager"
    private const val PREFS_NAME = "ExtraTimerPrefs"
    private const val KEY_TIMERS = "extra_timers_json"
    const val MAX_EXTRA_TIMERS = 2

    val timers = mutableStateListOf<ExtraTimerData>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val tickerJobs = mutableMapOf<String, Job>()
    private var isInitialized = false

    // Alarmgeluid/trilling per afgelopen extra timer. Losstaand van GlobalTimerManager/TimeEngineService
    // (dat is helemaal gebouwd rond de ene primaire timer) — simpel en direct, zodat elke extra
    // timer echt afgaat i.p.v. stilzwijgend op 0 te blijven staan.
    private val alarmMediaPlayers = mutableMapOf<String, MediaPlayer>()
    private val alarmVibrators = mutableMapOf<String, Vibrator>()

    // Zelfde HA-integratie (externe speaker + optioneel script) als de hoofdtimer
    // (TimeEngineService.playAlarm) — per slot bijgehouden zodat meerdere extra timers
    // onafhankelijk hun eigen externe-speaker sessie kunnen hebben.
    private val externalSpeakerJobs = mutableMapOf<String, Job>()
    private val currentExternalSpeakerEntityIds = mutableMapOf<String, String>()

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        restoreState(context.applicationContext)
        timers.forEach { slot ->
            when (slot.state) {
                GlobalTimerManager.TimerState.RUNNING -> {
                    val remaining = slot.endTimeMs - System.currentTimeMillis()
                    if (remaining > 0) {
                        slot.remainingMs = remaining
                        startTicker(context.applicationContext, slot)
                    } else {
                        // Timer liep al af terwijl de app dicht was — alsnog laten afgaan.
                        slot.remainingMs = 0
                        slot.state = GlobalTimerManager.TimerState.FINISHED
                        saveState(context.applicationContext)
                        playAlarmForSlot(context.applicationContext, slot)
                    }
                }
                GlobalTimerManager.TimerState.FINISHED -> {
                    // App herstart terwijl deze timer al klaar was — alarm hervatten.
                    playAlarmForSlot(context.applicationContext, slot)
                }
                else -> Unit
            }
        }
        Log.i(TAG, "INIT restored ${timers.size} extra timers")
    }

    fun addTimer(context: Context): ExtraTimerData? {
        if (timers.size >= MAX_EXTRA_TIMERS) return null
        val slot = ExtraTimerData()
        timers.add(slot)
        saveState(context)
        Log.i(TAG, "ADD_TIMER id=${slot.id} total=${timers.size}")
        return slot
    }

    fun startTimer(context: Context, slot: ExtraTimerData, durationMs: Long) {
        if (durationMs <= 0) return
        stopAlarmForSlot(context, slot)
        slot.initialMs = durationMs
        slot.remainingMs = durationMs
        slot.endTimeMs = System.currentTimeMillis() + durationMs
        slot.state = GlobalTimerManager.TimerState.RUNNING
        saveState(context)
        startTicker(context, slot)
        syncPopup(context, "start_extra")
        Log.i(TAG, "START_TIMER id=${slot.id} duration=$durationMs")
    }

    fun pauseTimer(context: Context, slot: ExtraTimerData) {
        tickerJobs[slot.id]?.cancel()
        tickerJobs.remove(slot.id)
        slot.state = GlobalTimerManager.TimerState.PAUSED
        saveState(context)
        syncPopup(context, "pause_extra")
        Log.i(TAG, "PAUSE_TIMER id=${slot.id} remaining=${slot.remainingMs}")
    }

    fun resumeTimer(context: Context, slot: ExtraTimerData) {
        if (slot.remainingMs <= 0) return
        slot.endTimeMs = System.currentTimeMillis() + slot.remainingMs
        slot.state = GlobalTimerManager.TimerState.RUNNING
        saveState(context)
        startTicker(context, slot)
        syncPopup(context, "resume_extra")
        Log.i(TAG, "RESUME_TIMER id=${slot.id} remaining=${slot.remainingMs}")
    }

    fun removeTimer(context: Context, slot: ExtraTimerData) {
        tickerJobs[slot.id]?.cancel()
        tickerJobs.remove(slot.id)
        stopAlarmForSlot(context, slot)
        timers.remove(slot)
        saveState(context)
        syncPopup(context, "remove_extra")
        Log.i(TAG, "REMOVE_TIMER id=${slot.id} remaining=${timers.size}")
    }

    fun hasActiveTimers(): Boolean =
        timers.any { it.state == GlobalTimerManager.TimerState.RUNNING || it.state == GlobalTimerManager.TimerState.PAUSED }

    fun activeExtraTimerCount(): Int =
        timers.count {
            it.state == GlobalTimerManager.TimerState.RUNNING ||
                it.state == GlobalTimerManager.TimerState.PAUSED
        }

    /**
     * Start een opgeslagen timer direct als extra slot (2e/3e) zolang er minder dan
     * [MAX_EXTRA_TIMERS] actieve extra timers zijn.
     *
     * Optie A — hergebruik: een IDLE- of FINISHED-slot (bijv. afgelopen maar nog niet
     * weggeklikt) telt niet als "bezet" voor capaciteit; [addTimer] blokkeert alleen op
     * rauwe lijstgrootte, dus we pakken eerst een inactief bestaand slot voordat we
     * een nieuw slot aanmaken.
     */
    fun startSavedTimerAsExtra(context: Context, saved: SavedTimer): Boolean {
        if (activeExtraTimerCount() >= MAX_EXTRA_TIMERS) return false
        if (saved.durationMillis <= 0) return false

        val slot = timers.firstOrNull { inactiveExtraSlot ->
            inactiveExtraSlot.state == GlobalTimerManager.TimerState.IDLE ||
                inactiveExtraSlot.state == GlobalTimerManager.TimerState.FINISHED
        } ?: addTimer(context) ?: return false

        slot.name = saved.name
        startTimer(context, slot, saved.durationMillis)
        return true
    }

    fun loadSavedTimerToExtra(context: Context, saved: SavedTimer): Boolean {
        if (saved.durationMillis <= 0) return false

        val slot = timers.firstOrNull { it.state == GlobalTimerManager.TimerState.IDLE ||
            it.state == GlobalTimerManager.TimerState.FINISHED
        } ?: addTimer(context) ?: return false

        slot.name = saved.name
        slot.hoursInput = (saved.durationMillis / 3600000).toInt()
        slot.minutesInput = ((saved.durationMillis % 3600000) / 60000).toInt()
        slot.secondsInput = ((saved.durationMillis % 60000) / 1000).toInt()
        slot.state = GlobalTimerManager.TimerState.IDLE
        slot.initialMs = saved.durationMillis
        saveState(context)
        return true
    }

    fun findById(id: String): ExtraTimerData? = timers.find { it.id == id }

    /**
     * Stop alarm geluid/trilling + herstart deze extra timer met dezelfde duur, precies zoals
     * [GlobalTimerManager.restartTimerAfterAlarm] voor de hoofdtimer doet vanaf de "Timer gaat
     * af"-pagina/melding.
     */
    fun restartTimerAfterAlarm(context: Context, slot: ExtraTimerData) {
        val duration = slot.initialMs.coerceAtLeast(1000L)
        stopAlarmForSlot(context, slot)
        Log.i(TAG, "EXTRA_RESTART_AFTER_ALARM id=${slot.id} duration=$duration")
        Handler(Looper.getMainLooper()).postDelayed({
            startTimer(context, slot, duration)
        }, 80L)
    }

    /**
     * Stop alarm geluid/trilling zonder de timer helemaal te verwijderen — zet het slot terug
     * naar IDLE met de laatst ingestelde duur, zodat het net als de hoofdtimer opnieuw gestart
     * kan worden. Gebruikt door de "Stop"-knop op de "Timer gaat af"-pagina/melding.
     */
    fun stopAlarmOnlyForSlot(context: Context, slot: ExtraTimerData) {
        stopAlarmForSlot(context, slot)
        slot.remainingMs = slot.initialMs
        slot.state = GlobalTimerManager.TimerState.IDLE
        saveState(context)
        syncPopup(context, "extra_stop_alarm_only")
        Log.i(TAG, "EXTRA_STOP_ALARM_ONLY id=${slot.id}")
    }

    private fun startTicker(context: Context, slot: ExtraTimerData) {
        tickerJobs[slot.id]?.cancel()
        tickerJobs[slot.id] = scope.launch {
            while (slot.state == GlobalTimerManager.TimerState.RUNNING) {
                val remaining = slot.endTimeMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    slot.remainingMs = 0
                    slot.state = GlobalTimerManager.TimerState.FINISHED
                    saveState(context)
                    syncPopup(context, "extra_finished")
                    playAlarmForSlot(context, slot)
                    Log.i(TAG, "TIMER_FINISHED id=${slot.id}")
                    break
                }
                slot.remainingMs = remaining
                delay(100L)
            }
        }
    }

    /**
     * Start geluid + trilling voor deze afgelopen extra timer, met dezelfde instellingen
     * (volume/vibratie/toon) als de hoofdtimer, PLUS dezelfde HA-integratie (externe
     * speaker-beslissing + optioneel HA-script) als [TimeEngineService.playAlarm] — zodat
     * timer 2/3 exact hetzelfde gedrag hebben als de hoofdtimer, niet alleen lokaal geluid.
     * Blijft lopen tot [stopAlarmForSlot] (Stop-knop).
     */
    private fun playAlarmForSlot(context: Context, slot: ExtraTimerData) {
        stopAlarmForSlot(context, slot)
        val appContext = context.applicationContext
        startLocalPlaybackForSlot(appContext, slot)

        externalSpeakerJobs[slot.id] = CoroutineScope(Dispatchers.IO).launch {
            try {
                playOnExternalSpeakerIfConfiguredForSlot(appContext, slot)
            } catch (e: Exception) {
                Log.e(TAG, "EXTRA_EXTERNAL_SPEAKER_FAILED id=${slot.id}", e)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runTimerScriptIfConfigured(appContext, slot)
            } catch (e: Exception) {
                Log.e(TAG, "EXTRA_TIMER_SCRIPT_FAILED id=${slot.id}", e)
            }
        }

        triggerFinishedExperience(context, slot)
    }

    /** Lokaal geluid + trilling (telefoon zelf) — los van de HA/externe-speaker-beslissing. */
    private fun startLocalPlaybackForSlot(context: Context, slot: ExtraTimerData) {
        try {
            TimerSettingsStateHolder.init(context)
            val useMobileVolume = TimerSettingsStateHolder.useMobileVolume.value
            val volume = TimerSettingsStateHolder.volume.value
            val vibrate = TimerSettingsStateHolder.vibrationEnabled.value

            if (vibrate) {
                val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(longArrayOf(0, 500, 200, 500), 0)
                }
                alarmVibrators[slot.id] = vib
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

            // DD Music: analog aan playOnExternalSpeakerIfConfiguredForSlot bij speaker-only —
            // lokale MediaPlayer onderdrukken om dubbel geluid te voorkomen. Trilling blijft
            // (haptic cue op het toestel). Bij mislukte launch: gewoon lokaal alarmgeluid.
            if (tryPlayViaDdMusic(context)) {
                Log.i(TAG, "EXTRA_ALARM_DD_MUSIC id=${slot.id}")
                return
            }

            if (useMobileVolume || volume > 0) {
                val toneUriStr = TimerSettingsStateHolder.toneUri.value
                val uri = SettingsManager.resolvePlayableAlarmSoundUri(context, toneUriStr)
                val player = try {
                    MediaPlayer().apply {
                        setDataSource(context, uri)
                        setAudioAttributes(TimerSettingsStateHolder.timerAlarmAudioAttributes())
                        isLooping = true
                        prepare()
                        start()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "EXTRA_ALARM_SOUND_FAILED id=${slot.id} uri=$uri, falling back to default", e)
                    MediaPlayer().apply {
                        setDataSource(context, Uri.parse(SettingsManager.DEFAULT_ALARM_SOUND_URI))
                        setAudioAttributes(TimerSettingsStateHolder.timerAlarmAudioAttributes())
                        isLooping = true
                        prepare()
                        start()
                    }
                }
                alarmMediaPlayers[slot.id] = player
            }
            Log.i(TAG, "EXTRA_ALARM_STARTED id=${slot.id}")
        } catch (e: Exception) {
            Log.e(TAG, "EXTRA_ALARM_START_FAILED id=${slot.id}", e)
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

    /**
     * Zelfde beslissing als [TimeEngineService.playOnExternalSpeakerIfConfigured]: bepaalt via
     * [AlarmOutputDecisionEngine] (o.a. "alleen als thuis") of het geluid (ook) op een HA-speaker
     * moet, en zet lokale afspeling uit als het een speaker-only modus is.
     */
    private suspend fun playOnExternalSpeakerIfConfiguredForSlot(context: Context, slot: ExtraTimerData) {
        val settingsStorage = HomeAssistantSettingsStorage(context)
        val repository = HomeAssistantRepository(HomeAssistantClient, settingsStorage)

        // 12s (niet 5s): isUserAtHome() kan nu 1x een auto-fix-retry doen bij een "niet thuis"-
        // uitslag (forced refresh + herhaalde check). Geen risico op stilte: startLocalPlaybackForSlot
        // is hierboven al aangeroepen, dit bepaalt alleen of/wanneer ook de externe speaker gebruikt wordt.
        val alarmOutput = withTimeoutOrNull(12000) {
            AlarmOutputDecisionEngine.determineAlarmOutput(
                context = context,
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

        Log.d(TAG, "[TIMER OUTPUT] id=${slot.id} using external speaker: $speakerEntityId")
        currentExternalSpeakerEntityIds[slot.id] = speakerEntityId

        val toneUriStr = TimerSettingsStateHolder.toneUri.value ?: SettingsManager.DEFAULT_ALARM_SOUND_URI
        val soundUrl = ExternalSpeakerHelper.getExternalSpeakerSoundUrl(context, com.dd.daykit.data.SpeakerContext.TIMER)
            ?: ExternalSpeakerHelper.getAlarmSoundUrl(context, toneUriStr)

        // Speaker-only modi: lokale afspeling uitzetten zodat het niet dubbel klinkt.
        val speakerOnly = alarmOutput !is AlarmOutput.PhoneAndExternal
        if (speakerOnly) {
            stopLocalPlaybackForSlot(slot)
        }

        val success = ExternalSpeakerHelper.playAlarmOnSpeaker(context, speakerEntityId, soundUrl, com.dd.daykit.data.SpeakerContext.TIMER)
        if (!success) {
            Log.e(TAG, "[TIMER OUTPUT] id=${slot.id} external speaker failed, falling back to phone")
            currentExternalSpeakerEntityIds.remove(slot.id)
            if (speakerOnly) {
                startLocalPlaybackForSlot(context, slot)
            }
        } else {
            Log.i(TAG, "[TIMER OUTPUT] id=${slot.id} external speaker started successfully")
        }
    }

    /**
     * Optioneel HA-script starten bij het aflopen van deze extra timer — zelfde instellingen
     * (en "alleen als thuis"-check) als [TimeEngineService.runTimerScriptIfConfigured].
     */
    private suspend fun runTimerScriptIfConfigured(context: Context, slot: ExtraTimerData) {
        val settingsStorage = HomeAssistantSettingsStorage(context)
        val repository = HomeAssistantRepository(HomeAssistantClient, settingsStorage)
        val settings = repository.getSettings()
        val scriptEntityId = settings.timerScriptEntityId
        if (!settings.timerScriptEnabled || scriptEntityId.isNullOrBlank()) return

        if (!settings.timerScriptIgnorePresence &&
            !AlarmOutputDecisionEngine.isUserAtHome(repository, settings)
        ) {
            Log.d(TAG, "[TIMER SCRIPT] id=${slot.id} skipped: user not home (alleen als thuis)")
            return
        }

        val result = repository.callScript(scriptEntityId)
        if (result is HaUpdateResult.Error) {
            Log.w(TAG, "[TIMER SCRIPT] id=${slot.id} callScript failed: ${result.message}")
        } else {
            Log.i(TAG, "[TIMER SCRIPT] id=${slot.id} started script $scriptEntityId")
        }
    }

    /**
     * Zorgt dat een afgelopen extra timer HETZELFDE oplevert als de hoofdtimer: de "Timer gaat
     * af"-full-screen pagina ([TimerFinishedActivity]) + een bijbehorende ongoing melding met
     * full-screen intent ([TimerFinishedAlarmUi]). Verloopt via [TimerPopupForegroundService]
     * (die al draait als foreground service zodra er een timer actief is) zodat het starten van
     * de activity dezelfde behandeling krijgt als vanuit [TimeEngineService] bij de hoofdtimer.
     */
    private fun triggerFinishedExperience(context: Context, slot: ExtraTimerData) {
        val app = context.applicationContext
        try {
            val i = Intent(app, TimerPopupForegroundService::class.java).apply {
                action = TimerPopupForegroundService.ACTION_EXTRA_FINISHED
                putExtra(TimerFinishedAlarmUi.EXTRA_SLOT_ID, slot.id)
            }
            androidx.core.content.ContextCompat.startForegroundService(app, i)
            Log.i(TAG, "EXTRA_FINISHED_SERVICE_TRIGGERED id=${slot.id}")
        } catch (e: Exception) {
            Log.w(TAG, "EXTRA_FINISHED_SERVICE_TRIGGER_FAILED id=${slot.id}", e)
        }
    }

    private fun stopAlarmForSlot(context: Context, slot: ExtraTimerData) {
        stopLocalPlaybackForSlot(slot)
        stopExternalSpeakerForSlot(context, slot)
    }

    private fun stopLocalPlaybackForSlot(slot: ExtraTimerData) {
        alarmMediaPlayers.remove(slot.id)?.let { player ->
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "EXTRA_ALARM_STOP_FAILED id=${slot.id}", e)
            }
        }
        alarmVibrators.remove(slot.id)?.let { vib ->
            try {
                vib.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "EXTRA_ALARM_VIBRATOR_STOP_FAILED id=${slot.id}", e)
            }
        }
    }

    /** Zelfde als [TimeEngineService.stopExternalSpeaker], maar per slot. */
    private fun stopExternalSpeakerForSlot(context: Context, slot: ExtraTimerData) {
        externalSpeakerJobs.remove(slot.id)?.cancel()
        currentExternalSpeakerEntityIds.remove(slot.id)?.let { speakerId ->
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ExternalSpeakerHelper.stopAlarmOnSpeaker(appContext, speakerId)
                } catch (e: Exception) {
                    Log.e(TAG, "EXTRA_EXTERNAL_SPEAKER_STOP_FAILED id=${slot.id}", e)
                }
            }
        }
    }

    private fun syncPopup(context: Context, reason: String) {
        TimerSettingsStateHolder.init(context)
        if (TimerSettingsStateHolder.isPopupStartupAllowed()) {
            TimerPopupForegroundService.syncFromTimerState(context, reason)
        }
    }

    internal fun saveState(context: Context) {
        val json = JSONArray()
        for (slot in timers) {
            json.put(JSONObject().apply {
                put("id", slot.id)
                put("state", slot.state.name)
                put("endTimeMs", slot.endTimeMs as Any)
                put("remainingMs", slot.remainingMs as Any)
                put("initialMs", slot.initialMs as Any)
                put("hoursInput", slot.hoursInput as Any)
                put("minutesInput", slot.minutesInput as Any)
                put("secondsInput", slot.secondsInput as Any)
                put("name", slot.name)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TIMERS, json.toString())
            .apply()
    }

    private fun restoreState(context: Context) {
        timers.clear()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TIMERS, null) ?: return
        try {
            val json = JSONArray(raw)
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val slot = ExtraTimerData(id = obj.getString("id"))
                slot.state = try {
                    GlobalTimerManager.TimerState.valueOf(obj.getString("state"))
                } catch (_: Exception) {
                    GlobalTimerManager.TimerState.IDLE
                }
                slot.endTimeMs = obj.optLong("endTimeMs", 0L)
                slot.remainingMs = obj.optLong("remainingMs", 0L)
                slot.initialMs = obj.optLong("initialMs", 0L)
                slot.hoursInput = obj.optInt("hoursInput", 0)
                slot.minutesInput = obj.optInt("minutesInput", 0)
                slot.secondsInput = obj.optInt("secondsInput", 0)
                slot.name = obj.optString("name", "")
                timers.add(slot)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RESTORE_FAILED", e)
            timers.clear()
        }
    }
}

class ExtraTimerData(val id: String = java.util.UUID.randomUUID().toString()) {
    var state by mutableStateOf(GlobalTimerManager.TimerState.IDLE)
    var name by mutableStateOf("")
    var hoursInput by mutableIntStateOf(0)
    var minutesInput by mutableIntStateOf(0)
    var secondsInput by mutableIntStateOf(0)
    var remainingMs by mutableLongStateOf(0L)
    var initialMs by mutableLongStateOf(0L)
    var endTimeMs by mutableLongStateOf(0L)
}
