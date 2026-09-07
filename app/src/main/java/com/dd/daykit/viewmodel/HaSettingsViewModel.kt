package com.dd.daykit.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dd.daykit.ExternalSpeakerHelper
import com.dd.daykit.SettingsManager
import com.dd.daykit.data.ExternalSpeakerMode
import com.dd.daykit.data.HaAgendaAlarmConfig
import com.dd.daykit.data.HaConnectionResult
import com.dd.daykit.data.HaEntity
import com.dd.daykit.data.HaPlayMediaResult
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettings
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.data.SensorCheckResult
import com.dd.daykit.data.SpeakerContext
import com.dd.daykit.data.SpeakerSettings
import com.dd.daykit.sound.Sound
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class HaSettingsUiState(
    val baseUrls: List<String> = emptyList(),
    val activeBaseUrl: String? = null,
    val longLivedToken: String = "",
    val entities: List<String> = emptyList(), // Generieke entiteiten lijst
    val showOnlyLinkedEntities: Boolean = false, // Toon alleen gekoppelde entiteiten
    val backupAlarmEnabled: Boolean = false, // Backup-wekker via HA (deprecated)
    val availableMediaPlayers: List<HaEntity> = emptyList(), // Beschikbare speakers
    // Externe-speaker instellingen, apart per onderdeel sinds de speaker-splitsing - zie
    // SpeakerContext en HomeAssistantSettings.alarmSpeaker/timerSpeaker/weatherSpeaker.
    // Gebruik speakerSettingsFor(context)/updateSpeakerSettings(context, ...) om hiermee te werken
    // i.p.v. deze velden rechtstreeks aan te spreken.
    val alarmSpeaker: SpeakerSettings = SpeakerSettings(),
    val timerSpeaker: SpeakerSettings = SpeakerSettings(),
    val weatherSpeaker: SpeakerSettings = SpeakerSettings(),
    val availablePresenceEntities: List<HaEntity> = emptyList(), // Beschikbare presence entities
    val selectedPresenceEntityId: String? = null, // Geselecteerde presence entity
    val presenceExpectedState: String = "home", // Verwachte state voor "thuis" (automatisch afgeleid, zie deriveExpectedPresenceState)
    // Live "ben ik thuis"-status (null = nog niet opgehaald/onbekend) voor de alleen-lezen
    // statusregel in de UI - presence-detectie zelf staat altijd aan, er is geen aan/uit meer.
    val presenceIsHome: Boolean? = null,
    val isLoadingPresenceStatus: Boolean = false,
    val outOfBedCheckEnabled: Boolean = false, // Uit bed check ingeschakeld
    val outOfBedEntityId: String? = null, // Geselecteerde "uit bed" entity
    val outOfBedExpectedValue: String = "off", // Waarde die aangeeft dat iemand in bed ligt
    // Live "lig ik nog in bed"-status (null = nog niet opgehaald/onbekend, of unavailable/unknown
    // in HA zelf) voor de alleen-lezen statusregel in de UI - zelfde patroon als presenceIsHome.
    val outOfBedIsInBed: Boolean? = null,
    val isLoadingOutOfBedStatus: Boolean = false,
    val isLoadingSpeakers: Boolean = false,
    val isLoadingPresenceEntities: Boolean = false,
    val speakerError: String? = null,
    val isLoading: Boolean = true,
    // Interval in seconden waarmee HA het backup-alarm herhaalt (5-600) - alleen relevant voor de
    // HA-watchdog van het agenda-alarm, blijft bewust gedeeld (geen speaker-instelling).
    val backupAlarmDuration: Int = 10,
    val availableLocalSounds: List<Sound> = emptyList(), // Lokale geluiden voor backup-alarm selectie
    val isLoadingSounds: Boolean = false, // Laden van lokale geluiden
    // Losse HA-script instellingen voor alarm en timer (onafhankelijk van elkaar en van speaker)
    val availableScripts: List<HaEntity> = emptyList(), // Beschikbare script entities
    val alarmScriptEnabled: Boolean = false,
    val alarmScriptEntityId: String? = null,
    val timerScriptEnabled: Boolean = false,
    val timerScriptEntityId: String? = null,
    val alarmScriptIgnorePresence: Boolean = true,
    val timerScriptIgnorePresence: Boolean = true,
    // Weeralarmen uitspreken op weatherSpeaker.entityId hierboven - lokaal gegenereerd door de
    // telefoon (Android TextToSpeech), geen HA tts-platform meer nodig, zie WeatherAlertWorker.kt.
    val weatherTtsEnabled: Boolean = false,
    // HA-only "Defaults"-velden (notify-service en veiligheidsklep) - de app gebruikt ze niet zelf,
    // maar houdt ze bij zodat ze via de HA-update-melding overgenomen en daarna weer meegepusht
    // kunnen worden. Zie HomeAssistantSettings.notifyService/safetyTimeoutSeconds.
    val notifyService: String? = null,
    val safetyTimeoutSeconds: Int? = null
)

data class HaConnectionTestUiState(
    val isTesting: Boolean = false,
    val lastMessage: String? = null
)

data class SensorCheckUiState(
    val isChecking: Boolean = false,
    val results: List<SensorCheckResult> = emptyList()
)

data class SpeakerTestUiState(
    val isTesting: Boolean = false,
    val lastMessage: String? = null
)

data class BackupAlarmTestUiState(
    val isTesting: Boolean = false,
    val lastMessage: String? = null
)

data class QrPairingUiState(
    val isPairing: Boolean = false,
    val lastMessage: String? = null,
    val isError: Boolean = false
)

/**
 * Eén individueel gedetecteerd verschil tussen de lokale (leidende) waarde en wat er in HA
 * zelf staat (bv. via Configureren > Externe speaker/Aanwezigheid/Uit bed/Scripts aangepast) -
 * voor het generieke accepteer/negeer-lijstje in [HaEntityUpdateCheckState.fieldDiffs].
 * [apply] past dit ene veld toe op een [HaSettingsUiState] (zonder op te slaan) - zie
 * [HaSettingsViewModel.applyHaEntityUpdate].
 */
data class HaConfigFieldDiff(
    val key: String,
    val label: String,
    val localValue: String,
    val haValue: String,
    val apply: (HaSettingsUiState) -> HaSettingsUiState
)

/**
 * Resultaat van een handmatige, door de gebruiker gestarte controle op wijzigingen die in Home
 * Assistant zelf zijn gedaan (bv. via het "Configureren"-scherm, zie config_flow.py's
 * AgendaAlarmBackupOptionsFlow) - zowel nieuwe entiteiten als individuele velden (externe
 * speaker, aanwezigheid, uit-bed, scripts, backup-interval). De app blijft verder altijd
 * leidend - dit is de enige, bewust expliciete uitzondering: nooit automatisch toegepast,
 * altijd eerst tonen en laten bevestigen (per veld, zie [HaConfigFieldDiff]).
 */
data class HaEntityUpdateCheckState(
    val isChecking: Boolean = false,
    val newEntities: List<String> = emptyList(),
    val fieldDiffs: List<HaConfigFieldDiff> = emptyList(),
    val error: String? = null,
    // true zodra er minstens één check is afgerond - onderscheidt "nog nooit gecontroleerd" van
    // "gecontroleerd, niets nieuws gevonden" (beide hebben verder een lege newEntities-lijst).
    val hasChecked: Boolean = false
) {
    /** True zodra er iets is om aan de gebruiker voor te leggen - nieuwe entiteiten en/of velden. */
    val hasChanges: Boolean get() = newEntities.isNotEmpty() || fieldDiffs.isNotEmpty()
}

class HaSettingsViewModel(
    private val settingsStorage: HomeAssistantSettingsStorage,
    private val repository: HomeAssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HaSettingsUiState())
    val uiState: StateFlow<HaSettingsUiState> = _uiState.asStateFlow()

    private val _connectionTestState = MutableStateFlow(HaConnectionTestUiState())
    val connectionTestState: StateFlow<HaConnectionTestUiState> = _connectionTestState.asStateFlow()

    private val _sensorCheckState = MutableStateFlow(SensorCheckUiState())
    val sensorCheckState: StateFlow<SensorCheckUiState> = _sensorCheckState.asStateFlow()

    private val _speakerTestState = MutableStateFlow(SpeakerTestUiState())
    val speakerTestState: StateFlow<SpeakerTestUiState> = _speakerTestState.asStateFlow()

    private val _backupAlarmTestState = MutableStateFlow(BackupAlarmTestUiState())
    val backupAlarmTestState: StateFlow<BackupAlarmTestUiState> = _backupAlarmTestState.asStateFlow()

    private val _qrPairingState = MutableStateFlow(QrPairingUiState())
    val qrPairingState: StateFlow<QrPairingUiState> = _qrPairingState.asStateFlow()

    private val _haEntityUpdateState = MutableStateFlow(HaEntityUpdateCheckState())
    val haEntityUpdateState: StateFlow<HaEntityUpdateCheckState> = _haEntityUpdateState.asStateFlow()

    /**
     * De laatst opgehaalde HA-config, alleen bewaard om na een antwoord van de gebruiker de
     * herkomststempel te kunnen vastleggen (zie [rememberSeenHaConfigStamp]) - zonder dat zou
     * dezelfde HA-wijziging bij de volgende controle opnieuw voorgelegd worden.
     */
    private var lastFetchedHaConfig: HaAgendaAlarmConfig? = null

    private companion object {
        /**
         * Waarde van `config_last_modified_by` als HA's config alleen een push van deze app
         * weerspiegelt. Moet gelijk blijven aan SOURCE_APP in de HA-integratie's const.py.
         */
        const val HA_CONFIG_SOURCE_APP = "app"
    }

    init {
        loadSettings()
    }
    
    /**
     * CENTRALE INITIALIZATION FUNCTIE
     * Moet worden aangeroepen na import, app start, en bij openen van settings pagina.
     * Voert alle noodzakelijke initialisatie uit:
     * 1. Test verbinding met Home Assistant
     * 2. Laad media players
     * 3. Laad presence entities
     * 4. Valideer geselecteerde speaker
     * 5. Push de lokale instellingen naar HA (saveSettings) - de app is hier leidend, niet HA.
     *
     * [batteryUsagePerHour] optioneel meegeven (de ViewModel kent SettingsManager/Context niet)
     * zodat input_number.mobiel_batterij_per_uur ook meteen de lokale waarde krijgt bij een
     * (her)koppeling, i.p.v. te wachten tot de gebruiker de slider toevallig een keer aanraakt.
     */
    fun applyHomeAssistantSettings(batteryUsagePerHour: Int? = null) {
        viewModelScope.launch {
            val current = _uiState.value

            // Skip als geen HA configuratie
            if (current.baseUrls.none { it.isNotBlank() } || current.longLivedToken.isBlank()) {
                android.util.Log.i("HaSettingsViewModel", "applyHomeAssistantSettings: No HA config, skipping")
                return@launch
            }

            android.util.Log.i("HaSettingsViewModel", "applyHomeAssistantSettings: Starting initialization...")

            // 1. Test verbinding en bepaal actieve URL
            val connectionResult = repository.testConnectionOverAllUrls()
            when (connectionResult) {
                is com.dd.daykit.data.HaConnectionResult.Success -> {
                    _uiState.value = _uiState.value.copy(activeBaseUrl = connectionResult.baseUrl)
                    android.util.Log.i("HaSettingsViewModel", "applyHomeAssistantSettings: Connection OK via ${connectionResult.baseUrl}")
                }
                is com.dd.daykit.data.HaConnectionResult.Error -> {
                    android.util.Log.w("HaSettingsViewModel", "applyHomeAssistantSettings: Connection failed: ${connectionResult.message}")
                }
            }

            // 1b. Meld meteen het lokale IP van de telefoon. Moet vóór stap 4b gebeuren: de
            // automatische keuze van een aanwezigheidsbron pakt de ingebouwde ping-sensor alleen
            // als die op dat moment al een echte staat heeft, en zonder IP blijft die "niet
            // beschikbaar". Bij een verse koppeling kwam het IP pas binnen als er een agenda-alarm
            // gewapend werd, dus viel de auto-keuze daar structureel naast.
            runCatching {
                repository.reportPhoneIp(
                    com.dd.daykit.AlarmOutputDecisionEngine.getPhoneIpAddress(settingsStorage.context)
                )
            }

            // 2. Laad media players (speakers)
            loadMediaPlayers()

            // 3. Laad presence entities
            loadPresenceEntities()

            // 3b. Laad scripts (voor script-bij-alarm / script-bij-timer)
            loadScripts()

            // 3c. Laad de lokale geluiden - moet vóór de HA-update-check (stap 4c) klaar zijn,
            // anders kan een geluids-URL uit HA niet aan een lokaal geluid gekoppeld worden.
            loadLocalSoundsInternal()

            // 4. Valideer geselecteerde speaker (per onderdeel, sinds de speaker-splitsing)
            val availableSpeakers = _uiState.value.availableMediaPlayers.map { it.entityId }
            for (ctx in SpeakerContext.entries) {
                val selectedSpeaker = speakerSettingsFor(ctx).entityId
                if (!selectedSpeaker.isNullOrBlank()) {
                    if (selectedSpeaker !in availableSpeakers) {
                        android.util.Log.w("HaSettingsViewModel", "applyHomeAssistantSettings: Selected speaker $selectedSpeaker for $ctx not in available list, keeping selection")
                    } else {
                        android.util.Log.i("HaSettingsViewModel", "applyHomeAssistantSettings: Speaker $selectedSpeaker for $ctx validated")
                    }
                }
            }

            // 4b. Nog geen presence-entiteit gekozen? Probeer er automatisch een aan te nemen
            // (ingebouwde ping-sensor, of - als die er niet/nog niet is - een enkele
            // person.-entiteit) zodat de gebruiker dit niet handmatig hoeft op te zoeken.
            tryAutoAdoptPresenceEntity()
            refreshPresenceStatus()
            refreshOutOfBedStatus()

            // 4c. Controleer VOORDAT de app haar eigen lijst terugpusht (stap 5 hieronder) of er in
            // HA zelf iets is toegevoegd (bv. via Configureren > Entities) wat de app nog niet
            // kent, of aan andere velden (externe speaker, aanwezigheid, uit-bed, scripts,
            // backup-interval). Dit MOET vóór saveSettings() gebeuren: zonder deze volgorde zou
            // de push hieronder een net in HA gewijzigde waarde alweer overschrijven voordat de
            // gebruiker ooit de kans kreeg om 'm te bevestigen (precies het probleem dat deze
            // check oplost). Puur een leesactie/vergelijking - past niets toe, en toont alleen
            // een prompt bij een écht verschil.
            performHaEntityUpdateCheck()

            // 5. Save settings - pusht de huidige lokale instellingen naar HA (zie saveSettings()'s
            // pushAgendaAlarmConfigPatch) en persisteert activeBaseUrl. De app is hier bewust
            // leidend: HA's config wordt nooit meer teruggelezen om de lokale staat te
            // overschrijven (dat overschreef anders bij elke app-start/pairing de instellingen
            // van de gebruiker met HA's kale defaults als de config-entry nog leeg stond).
            // Als de check hierboven een verschil vond (entiteiten en/of velden), wordt de push
            // UITGESTELD tot de gebruiker heeft gereageerd op de prompt (zie
            // applyHaEntityUpdate/dismissHaEntityUpdate, die saveSettings() zelf aanroepen zodra
            // er een antwoord is) - anders zou deze save alsnog meteen overschrijven wat net
            // gevonden is, nog vóór de dialoog kon worden getoond.
            if (!_haEntityUpdateState.value.hasChanges) {
                saveSettings()
            }

            if (batteryUsagePerHour != null) {
                syncBatteryUsageToHomeAssistant(batteryUsagePerHour)
            }

            android.util.Log.i("HaSettingsViewModel", "applyHomeAssistantSettings: Initialization complete")
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsStorage.settingsFlow.collect { settings ->
                val migratedEntities = if (settings.entities.isEmpty() &&
                    (settings.inBedSensors.isNotEmpty() || settings.outBedSensors.isNotEmpty())) {
                    (settings.inBedSensors + settings.outBedSensors).distinct()
                } else {
                    settings.entities
                }
                
                android.util.Log.d("HaSettingsViewModel", "Loaded HA entities: ${migratedEntities.filter { it.isNotBlank() }.size} item(s)")

                _uiState.value = HaSettingsUiState(
                    baseUrls = settings.baseUrls.ifEmpty { listOf("") },
                    activeBaseUrl = settings.activeBaseUrl,
                    longLivedToken = settings.longLivedToken ?: "",
                    entities = migratedEntities.ifEmpty { listOf("") },
                    showOnlyLinkedEntities = settings.showOnlyLinkedEntities,
                    backupAlarmEnabled = settings.backupAlarmEnabled,
                    // Speaker-instellingen, apart per onderdeel sinds de speaker-splitsing (settings
                    // is hier al gemigreerd, zie HomeAssistantSettingsStorage.settingsFlow).
                    alarmSpeaker = settings.alarmSpeaker,
                    timerSpeaker = settings.timerSpeaker,
                    weatherSpeaker = settings.weatherSpeaker,
                    selectedPresenceEntityId = settings.presenceEntityId,
                    presenceExpectedState = settings.presenceExpectedState,
                    outOfBedCheckEnabled = settings.outOfBedCheckEnabled,
                    outOfBedEntityId = settings.outOfBedEntityId,
                    outOfBedExpectedValue = settings.outOfBedExpectedValue,
                    isLoading = false,
                    backupAlarmDuration = settings.backupAlarmDuration,
                    alarmScriptEnabled = settings.alarmScriptEnabled,
                    alarmScriptEntityId = settings.alarmScriptEntityId,
                    timerScriptEnabled = settings.timerScriptEnabled,
                    timerScriptEntityId = settings.timerScriptEntityId,
                    alarmScriptIgnorePresence = settings.alarmScriptIgnorePresence,
                    timerScriptIgnorePresence = settings.timerScriptIgnorePresence,
                    weatherTtsEnabled = settings.weatherTtsEnabled,
                    notifyService = settings.notifyService,
                    safetyTimeoutSeconds = settings.safetyTimeoutSeconds
                )

                if (!settings.activeBaseUrl.isNullOrBlank() && !settings.longLivedToken.isNullOrBlank()) {
                    loadMediaPlayers()
                    loadPresenceEntities()
                    loadScripts()
                }
            }
        }
    }

    fun updateUrlAt(index: Int, newValue: String) {
        val currentList = _uiState.value.baseUrls.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = newValue
            _uiState.value = _uiState.value.copy(baseUrls = currentList)
        }
    }

    fun addUrl() {
        val currentList = _uiState.value.baseUrls.toMutableList()
        currentList.add("")
        _uiState.value = _uiState.value.copy(baseUrls = currentList)
    }

    fun removeUrl(index: Int) {
        val currentList = _uiState.value.baseUrls.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _uiState.value = _uiState.value.copy(baseUrls = currentList)
        }
    }
    
    fun replaceAllUrls(newUrls: List<String>) {
        _uiState.value = _uiState.value.copy(baseUrls = newUrls)
    }

    fun updateToken(newToken: String) {
        _uiState.value = _uiState.value.copy(longLivedToken = newToken)
    }

    fun updateEntityAt(index: Int, newValue: String) {
        val currentList = _uiState.value.entities.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = newValue
            _uiState.value = _uiState.value.copy(entities = currentList)
        }
    }

    fun addEntity() {
        val currentList = _uiState.value.entities.toMutableList()
        currentList.add("")
        _uiState.value = _uiState.value.copy(entities = currentList)
    }

    fun removeEntity(index: Int) {
        val currentList = _uiState.value.entities.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _uiState.value = _uiState.value.copy(entities = currentList)
        }
    }
    
    fun replaceAllEntities(newEntities: List<String>) {
        val cleanedEntities = newEntities
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        android.util.Log.d("HaSettingsViewModel", "Replacing HA entities: ${_uiState.value.entities.filter { it.isNotBlank() }.size} -> ${cleanedEntities.size}")
        _uiState.value = _uiState.value.copy(entities = cleanedEntities)
    }

    /**
     * Losse, handmatig aan te roepen variant van dezelfde check als stap 4c in
     * [applyHomeAssistantSettings] (die zelf al automatisch draait bij elk bezoek aan een
     * HA-instellingenpagina, zie de `LaunchedEffect(Unit)` in HaSettingsActivity/WeatherActivity).
     * Niet meer aan een knop gekoppeld sinds de automatische achtergrondcheck daarvoor in de plaats
     * kwam (zie [HaEntityUpdateCheckSection] in HaSettingsActivity.kt), maar blijft bestaan als
     * herbruikbaar aanroeppunt mocht een toekomstige plek toch een losse, on-demand refresh nodig
     * hebben zonder de rest van applyHomeAssistantSettings() opnieuw te draaien.
     */
    fun checkForHaEntityUpdate() {
        viewModelScope.launch {
            performHaEntityUpdateCheck()
        }
    }

    /**
     * Vergelijkt HA's volledige config (GET /config, leesactie zonder herlaad-effect) met de
     * lokale staat en zet het resultaat in [_haEntityUpdateState] - zowel nieuwe entiteiten
     * (ongewijzigd, altijd een simpele merge) als individuele veld-verschillen (zie
     * [buildConfigFieldDiffs]). Wordt zowel door de handmatige knop als automatisch vóór elke
     * terug-push in [applyHomeAssistantSettings] aangeroepen - past zelf niets toe, dat gebeurt
     * pas na expliciete bevestiging via [applyHaEntityUpdate].
     */
    private suspend fun performHaEntityUpdateCheck() {
        _haEntityUpdateState.value = HaEntityUpdateCheckState(isChecking = true)
        try {
            val haConfig = repository.fetchAgendaAlarmConfig()
            if (haConfig == null) {
                _haEntityUpdateState.value = HaEntityUpdateCheckState(hasChecked = true)
                return
            }
            // Eerste kennismaking met de HA-only velden (notify-service, veiligheidsklep): die
            // kent de app nog niet, dus elk verschil daar is geen "wijziging in HA" maar gewoon
            // "de app had deze waarde nog nooit gezien". Stil overnemen i.p.v. de gebruiker een
            // keuze voor te leggen die er geen is - een écht latere wijziging in HA levert daarna
            // wél een normale regel in de melding op.
            adoptUnknownHaOnlyFields(haConfig)
            lastFetchedHaConfig = haConfig

            // Is er sinds de vorige keer überhaupt iets in HA gebeurd? Zo nee, dan hoeven de
            // waardes niet eens vergeleken te worden: een verschil is dan een nog-niet-gepushte
            // lokale wijziging, geen HA-wijziging. Zie [shouldSurfaceHaChanges].
            if (!shouldSurfaceHaChanges(haConfig)) {
                rememberSeenHaConfigStamp(haConfig)
                _haEntityUpdateState.value = HaEntityUpdateCheckState(hasChecked = true)
                return
            }

            val localEntities = _uiState.value.entities.filter { it.isNotBlank() }.toSet()
            val newEntities = haConfig.entities.filter { it.isNotBlank() && it !in localEntities }.distinct()
            val fieldDiffs = buildConfigFieldDiffs(haConfig, _uiState.value)

            // Stempel pas onthouden als er ook echt iets te melden valt. Blijkt de HA-wijziging
            // niets op te leveren wat van de lokale staat afwijkt (bv. HA kreeg exact dezelfde
            // waarde), dan is er niets voor te leggen en is dit alsnog een stille afhandeling.
            if (newEntities.isEmpty() && fieldDiffs.isEmpty()) {
                rememberSeenHaConfigStamp(haConfig)
                _haEntityUpdateState.value = HaEntityUpdateCheckState(hasChecked = true)
                return
            }

            // Exact deze wijziging al eens voorgelegd en beantwoord? Dan niet nog eens vragen.
            // Dit is de vangnet-laag die niet van de HA-integratie afhangt: ook zonder
            // herkomststempel (oudere integratieversie) blijft een gemaakte keuze staan.
            if (fingerprintOf(newEntities, fieldDiffs) == settingsStorage.getAnsweredHaDiffFingerprint()) {
                _haEntityUpdateState.value = HaEntityUpdateCheckState(hasChecked = true)
                return
            }

            _haEntityUpdateState.value = HaEntityUpdateCheckState(
                newEntities = newEntities,
                fieldDiffs = fieldDiffs,
                hasChecked = true
            )
        } catch (e: Exception) {
            android.util.Log.e("HaSettingsViewModel", "checkForHaEntityUpdate FAILED", e)
            _haEntityUpdateState.value = HaEntityUpdateCheckState(error = e.message ?: "Onbekende fout", hasChecked = true)
        }
    }

    /**
     * Bepaalt of een verschil met HA de moeite van een melding waard is.
     *
     * Vóór deze check werden puur wáárdes vergeleken, en dat kan het verschil niet zien tussen
     * "in HA is zojuist iets gewijzigd" en "HA heeft nog de oude waarde omdat de app nog niet
     * gepusht had". Dat tweede geval leverde bij élke schermopening opnieuw de vraag op of je je
     * eigen, net gemaakte instelling wilde terugzetten naar de oude.
     *
     * Nu bepaalt de herkomststempel uit HA het antwoord:
     * - bron "app": HA weerspiegelt alleen onze eigen push → nooit melden.
     * - stempel ongewijzigd sinds de vorige keer: er is niets nieuws gebeurd → nooit melden.
     * - bron "ha" met een nieuwe stempel: er is echt in HA iets gewijzigd → wél melden, ook als
     *   de app zelf ook iets veranderd heeft (dat conflict hoort de gebruiker te zien).
     *
     * Ontbreekt de stempel volledig, dan draait er nog een oudere integratieversie zonder deze
     * velden: dan valt alles terug op het oude, puur waarde-gebaseerde gedrag.
     */
    private fun shouldSurfaceHaChanges(ha: HaAgendaAlarmConfig): Boolean {
        val stamp = ha.configLastModified
        if (stamp == null || stamp <= 0L) return true // oudere HA-integratie: oud gedrag
        if (ha.configLastModifiedBy == HA_CONFIG_SOURCE_APP) return false
        return stamp != settingsStorage.getLastSeenHaConfigStamp()
    }

    /** Legt vast welke herkomststempel we zojuist verwerkt hebben, zodat dezelfde wijziging niet nog eens gemeld wordt. */
    private fun rememberSeenHaConfigStamp(ha: HaAgendaAlarmConfig) {
        ha.configLastModified?.takeIf { it > 0L }?.let { settingsStorage.setLastSeenHaConfigStamp(it) }
    }

    /**
     * Stabiele vingerafdruk van precies wat er aan de gebruiker voorgelegd wordt: welke entiteiten
     * nieuw zijn, en welk veld naar welke HA-waarde zou gaan. Gesorteerd, zodat een andere
     * volgorde uit HA niet als een nieuwe wijziging telt.
     *
     * Bewust op basis van de HA-waarde en niet van de lokale waarde: na een "Nee" blijft de lokale
     * waarde staan zoals hij was, en dan moet dezelfde HA-waarde niet opnieuw gevraagd worden.
     */
    private fun fingerprintOf(newEntities: List<String>, fieldDiffs: List<HaConfigFieldDiff>): String {
        val entityPart = newEntities.sorted().joinToString(",") { "E:$it" }
        val fieldPart = fieldDiffs.sortedBy { it.key }.joinToString(",") { "F:${it.key}=${it.haValue}" }
        return "$entityPart|$fieldPart"
    }

    /**
     * Slaat op dat de gebruiker de nu getoonde wijziging beantwoord heeft - overnemen én afwijzen
     * tellen allebei als antwoord. Zonder dit kwam dezelfde melding bij elke schermopening terug,
     * en omdat zowel het HA-scherm als het Weer-scherm deze check draaien voelde dat als meerdere
     * meldingen kort na elkaar.
     */
    private fun rememberAnsweredHaDiff(state: HaEntityUpdateCheckState) {
        settingsStorage.setAnsweredHaDiffFingerprint(fingerprintOf(state.newEntities, state.fieldDiffs))
    }

    /**
     * Neemt de velden over die alleen aan de HA-kant bestaan (notify-service, veiligheidsklep) op
     * het moment dat de app ze nog niet kent (`null`). Puur een eenmalige inhaalslag zodat de app
     * ze kan meepushen i.p.v. leeg te laten; verandert niets aan waarden die de app al heeft, en
     * raakt geen enkel veld dat de gebruiker in de app zelf kan zetten - die blijven 100% via de
     * bevestigingsmelding lopen.
     */
    private fun adoptUnknownHaOnlyFields(ha: HaAgendaAlarmConfig) {
        val current = _uiState.value
        var updated = current
        if (current.notifyService == null && !ha.notifyService.isNullOrBlank()) {
            updated = updated.copy(notifyService = ha.notifyService)
        }
        if (current.safetyTimeoutSeconds == null && ha.safetyTimeoutSeconds != null) {
            updated = updated.copy(safetyTimeoutSeconds = ha.safetyTimeoutSeconds)
        }
        if (updated !== current) _uiState.value = updated
    }

    /**
     * Bouwt de lijst van individuele veld-verschillen tussen [local] en [ha] - voor ELK gedeeld
     * veld, dus alle acht secties uit HA's Configureren-scherm (Entities staat los, zie
     * newEntities; verder Alarm/Timer/Weer-speaker inclusief volume en geluid, Aanwezigheid, Uit
     * bed, Scripts en Defaults). Een HA-waarde van `null` of "" betekent hier
     * bewust "niets te vergelijken" i.p.v. "HA wil dit leegmaken" - deze check is puur bedoeld om
     * ECHTE HA-kant-bewerkingen te signaleren, niet om lokale keuzes ongevraagd te wissen.
     */
    private fun buildConfigFieldDiffs(ha: HaAgendaAlarmConfig, local: HaSettingsUiState): List<HaConfigFieldDiff> {
        val diffs = mutableListOf<HaConfigFieldDiff>()

        /** Tekstveld: lege/ontbrekende HA-waarde = "niets gezegd", nooit "leegmaken". */
        fun stringDiff(
            key: String,
            label: String,
            haValue: String?,
            localValue: String?,
            display: (String) -> String = { it },
            apply: (HaSettingsUiState, String) -> HaSettingsUiState
        ) {
            val haVal = haValue?.takeIf { it.isNotBlank() && it != localValue } ?: return
            diffs += HaConfigFieldDiff(
                key, label,
                localValue?.takeIf { it.isNotBlank() }?.let(display) ?: "(geen)",
                display(haVal)
            ) { apply(it, haVal) }
        }

        fun boolDiff(
            key: String,
            label: String,
            haValue: Boolean?,
            localValue: Boolean,
            apply: (HaSettingsUiState, Boolean) -> HaSettingsUiState
        ) {
            val haVal = haValue?.takeIf { it != localValue } ?: return
            diffs += HaConfigFieldDiff(key, label, jaNee(localValue), jaNee(haVal)) { apply(it, haVal) }
        }

        fun intDiff(
            key: String,
            label: String,
            haValue: Int?,
            localValue: Int?,
            range: IntRange,
            apply: (HaSettingsUiState, Int) -> HaSettingsUiState
        ) {
            val haVal = haValue?.takeIf { it != localValue } ?: return
            diffs += HaConfigFieldDiff(
                key, label, localValue?.toString() ?: "(geen)", "$haVal"
            ) { apply(it, haVal.coerceIn(range.first, range.last)) }
        }

        /** Speaker-modus: alleen overnemen als HA een naam stuurt die deze app-versie kent. */
        fun modeDiff(
            key: String,
            label: String,
            haValue: String?,
            localValue: ExternalSpeakerMode,
            apply: (HaSettingsUiState, ExternalSpeakerMode) -> HaSettingsUiState
        ) {
            val haVal = haValue?.takeIf { it.isNotBlank() && it != localValue.name } ?: return
            val mode = runCatching { ExternalSpeakerMode.valueOf(haVal) }.getOrNull() ?: return
            diffs += HaConfigFieldDiff(key, label, localValue.name, haVal) { apply(it, mode) }
        }

        /**
         * Geluid: HA stuurt een URL, de app werkt met een lokaal geluid-id. Alleen een verschil
         * melden als de app het geluid uit die URL ook echt heeft - anders valt er niets te kiezen.
         */
        fun soundDiff(
            key: String,
            label: String,
            haUrl: String?,
            localSoundId: Long?,
            apply: (HaSettingsUiState, Long) -> HaSettingsUiState
        ) {
            val haSoundId = soundIdForHaUrl(haUrl)?.takeIf { it != localSoundId } ?: return
            diffs += HaConfigFieldDiff(
                key, label, soundNameFor(localSoundId), soundNameFor(haSoundId)
            ) { apply(it, haSoundId) }
        }

        // --- Alarm speaker ---------------------------------------------------------------
        stringDiff("speaker_entity_id", "Alarm speaker: entiteit", ha.speakerEntityId, local.alarmSpeaker.entityId) { s, v ->
            s.copy(alarmSpeaker = s.alarmSpeaker.copy(entityId = v))
        }
        modeDiff("speaker_mode", "Alarm speaker: modus", ha.speakerMode, local.alarmSpeaker.mode) { s, v ->
            s.copy(alarmSpeaker = s.alarmSpeaker.copy(mode = v))
        }
        intDiff("default_volume", "Alarm speaker: volume", ha.defaultVolume, local.alarmSpeaker.volume, 0..100) { s, v ->
            s.copy(alarmSpeaker = s.alarmSpeaker.copy(volume = v))
        }
        boolDiff("skip_backup_volume", "Alarm speaker: volume ongemoeid laten", ha.skipBackupVolume, local.alarmSpeaker.skipVolume) { s, v ->
            s.copy(alarmSpeaker = s.alarmSpeaker.copy(skipVolume = v))
        }
        soundDiff("default_sound_url", "Alarm speaker: geluid", ha.defaultSoundUrl, local.alarmSpeaker.soundId) { s, v ->
            s.copy(alarmSpeaker = s.alarmSpeaker.copy(soundId = v))
        }

        // --- Timer speaker ---------------------------------------------------------------
        stringDiff("timer_speaker_entity_id", "Timer speaker: entiteit", ha.timerSpeakerEntityId, local.timerSpeaker.entityId) { s, v ->
            s.copy(timerSpeaker = s.timerSpeaker.copy(entityId = v))
        }
        modeDiff("timer_speaker_mode", "Timer speaker: modus", ha.timerSpeakerMode, local.timerSpeaker.mode) { s, v ->
            s.copy(timerSpeaker = s.timerSpeaker.copy(mode = v))
        }
        intDiff("timer_speaker_volume", "Timer speaker: volume", ha.timerSpeakerVolume, local.timerSpeaker.volume, 0..100) { s, v ->
            s.copy(timerSpeaker = s.timerSpeaker.copy(volume = v))
        }
        boolDiff("timer_speaker_skip_volume", "Timer speaker: volume ongemoeid laten", ha.timerSpeakerSkipVolume, local.timerSpeaker.skipVolume) { s, v ->
            s.copy(timerSpeaker = s.timerSpeaker.copy(skipVolume = v))
        }
        soundDiff("timer_speaker_sound_url", "Timer speaker: geluid", ha.timerSpeakerSoundUrl, local.timerSpeaker.soundId) { s, v ->
            s.copy(timerSpeaker = s.timerSpeaker.copy(soundId = v))
        }

        // --- Weather speaker -------------------------------------------------------------
        stringDiff("weather_speaker_entity_id", "Weer speaker: entiteit", ha.weatherSpeakerEntityId, local.weatherSpeaker.entityId) { s, v ->
            s.copy(weatherSpeaker = s.weatherSpeaker.copy(entityId = v))
        }
        modeDiff("weather_speaker_mode", "Weer speaker: modus", ha.weatherSpeakerMode, local.weatherSpeaker.mode) { s, v ->
            s.copy(weatherSpeaker = s.weatherSpeaker.copy(mode = v))
        }
        intDiff("weather_speaker_volume", "Weer speaker: volume", ha.weatherSpeakerVolume, local.weatherSpeaker.volume, 0..100) { s, v ->
            s.copy(weatherSpeaker = s.weatherSpeaker.copy(volume = v))
        }
        boolDiff("weather_speaker_skip_volume", "Weer speaker: volume ongemoeid laten", ha.weatherSpeakerSkipVolume, local.weatherSpeaker.skipVolume) { s, v ->
            s.copy(weatherSpeaker = s.weatherSpeaker.copy(skipVolume = v))
        }
        soundDiff("weather_speaker_sound_url", "Weer speaker: geluid", ha.weatherSpeakerSoundUrl, local.weatherSpeaker.soundId) { s, v ->
            s.copy(weatherSpeaker = s.weatherSpeaker.copy(soundId = v))
        }
        boolDiff("weather_tts_enabled", "Weeralarm uitspreken", ha.weatherTtsEnabled, local.weatherTtsEnabled) { s, v ->
            s.copy(weatherTtsEnabled = v)
        }

        // --- Presence ---------------------------------------------------------------------
        stringDiff("presence_entity_id", "Aanwezigheid: entiteit", ha.presenceEntityId, local.selectedPresenceEntityId) { s, v ->
            // Verwachte state hoort bij de entiteit: bij een nieuwe entiteit meteen de bijpassende
            // waarde afleiden, tenzij HA er zelf ook een meestuurde (dan staat die als losse regel
            // hieronder en wint de expliciete keuze van de gebruiker).
            s.copy(selectedPresenceEntityId = v, presenceExpectedState = com.dd.daykit.data.deriveExpectedPresenceState(v))
        }
        stringDiff("presence_expected_state", "Aanwezigheid: waarde die 'thuis' betekent", ha.presenceExpectedState, local.presenceExpectedState) { s, v ->
            s.copy(presenceExpectedState = v)
        }

        // --- Out of bed -------------------------------------------------------------------
        boolDiff("out_of_bed_check_enabled", "Uit bed check ingeschakeld", ha.outOfBedEnabled, local.outOfBedCheckEnabled) { s, v ->
            s.copy(outOfBedCheckEnabled = v)
        }
        stringDiff("out_of_bed_entity_id", "Uit bed: entiteit", ha.outOfBedEntityId, local.outOfBedEntityId) { s, v ->
            s.copy(outOfBedEntityId = v)
        }
        stringDiff("out_of_bed_expected_value", "Uit bed: waarde die 'in bed' betekent", ha.outOfBedExpectedValue, local.outOfBedExpectedValue) { s, v ->
            s.copy(outOfBedExpectedValue = v)
        }

        // --- Scripts ----------------------------------------------------------------------
        stringDiff("alarm_script_entity_id", "Script bij alarm: entiteit", ha.alarmScriptEntityId, local.alarmScriptEntityId) { s, v ->
            s.copy(alarmScriptEntityId = v)
        }
        boolDiff("alarm_script_enabled", "Script bij alarm ingeschakeld", ha.alarmScriptEnabled, local.alarmScriptEnabled) { s, v ->
            s.copy(alarmScriptEnabled = v)
        }
        boolDiff("alarm_script_ignore_presence", "Script bij alarm: ook uitvoeren als je niet thuis bent", ha.alarmScriptIgnorePresence, local.alarmScriptIgnorePresence) { s, v ->
            s.copy(alarmScriptIgnorePresence = v)
        }
        stringDiff("timer_script_entity_id", "Script bij timer: entiteit", ha.timerScriptEntityId, local.timerScriptEntityId) { s, v ->
            s.copy(timerScriptEntityId = v)
        }
        boolDiff("timer_script_enabled", "Script bij timer ingeschakeld", ha.timerScriptEnabled, local.timerScriptEnabled) { s, v ->
            s.copy(timerScriptEnabled = v)
        }
        boolDiff("timer_script_ignore_presence", "Script bij timer: ook uitvoeren als je niet thuis bent", ha.timerScriptIgnorePresence, local.timerScriptIgnorePresence) { s, v ->
            s.copy(timerScriptIgnorePresence = v)
        }

        // --- Defaults ---------------------------------------------------------------------
        intDiff("default_interval", "Backup-alarm interval (seconden)", ha.defaultInterval, local.backupAlarmDuration, 0..600) { s, v ->
            s.copy(backupAlarmDuration = v)
        }
        intDiff("safety_timeout_seconds", "Veiligheidsklep: automatisch stoppen na (seconden)", ha.safetyTimeoutSeconds, local.safetyTimeoutSeconds, 10..3600) { s, v ->
            s.copy(safetyTimeoutSeconds = v)
        }
        stringDiff("notify_service", "Notificatie-service", ha.notifyService, local.notifyService) { s, v ->
            s.copy(notifyService = v)
        }

        return diffs
    }

    /** Ja/Nee i.p.v. true/false in de HA-update-melding - die lijst leest een gebruiker, geen code. */
    private fun jaNee(value: Boolean): String = if (value) "Ja" else "Nee"

    /**
     * Neemt de nieuw-in-HA-gevonden entiteiten (altijd, ongewijzigd gedrag) en de door de
     * gebruiker aangevinkte veld-wijzigingen ([selectedFieldKeys], zie [HaConfigFieldDiff.key])
     * over in de lokale (leidende) staat en pusht meteen terug. Rondt ook een eventueel door
     * [applyHomeAssistantSettings] uitgestelde save af.
     */
    fun applyHaEntityUpdate(selectedFieldKeys: Set<String> = emptySet()) {
        val state = _haEntityUpdateState.value
        if (state.newEntities.isNotEmpty()) {
            val merged = _uiState.value.entities.filter { it.isNotBlank() } + state.newEntities
            replaceAllEntities(merged)
        }
        state.fieldDiffs.filter { it.key in selectedFieldKeys }.forEach { diff ->
            _uiState.value = diff.apply(_uiState.value)
        }
        // De gebruiker heeft deze HA-wijziging gezien en beantwoord: nooit meer opnieuw voorleggen,
        // ook niet als de push hieronder mislukt omdat HA even niet bereikbaar is.
        rememberAnsweredHaDiff(state)
        lastFetchedHaConfig?.let { rememberSeenHaConfigStamp(it) }
        _haEntityUpdateState.value = HaEntityUpdateCheckState()
        saveSettings()
    }

    /**
     * Wijst de gevonden HA-wijzigingen af - lokale lijst blijft ongewijzigd. Rondt de door
     * [applyHomeAssistantSettings] uitgestelde save alsnog af (de app blijft dan gewoon leidend
     * zoals altijd, inclusief het overschrijven van wat net in HA gevonden werd).
     */
    fun dismissHaEntityUpdate() {
        // Ook een "nee" is een antwoord: deze HA-wijziging is afgehandeld en mag niet bij de
        // volgende schermopening opnieuw gevraagd worden.
        rememberAnsweredHaDiff(_haEntityUpdateState.value)
        lastFetchedHaConfig?.let { rememberSeenHaConfigStamp(it) }
        _haEntityUpdateState.value = HaEntityUpdateCheckState()
        saveSettings()
    }

    fun toggleBackupAlarm(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(backupAlarmEnabled = enabled)
    }
    
    fun toggleShowOnlyLinkedEntities(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showOnlyLinkedEntities = enabled)
        loadMediaPlayers()
        loadPresenceEntities()
        loadScripts()
    }

    /**
     * Bouwt een [HomeAssistantSettings] uit de huidige uiState - gedeeld door [saveSettings] en
     * [pairWithSetupCode] (die laatste slaat synchroon op, zie de uitleg daar). Zet altijd
     * speakerSettingsMigrated = true: de uiState-velden alarmSpeaker/timerSpeaker/weatherSpeaker
     * zijn hier al leidend, dus de oude gedeelde velden (externalSpeakerEntityId e.a.) hoeven niet
     * meer meegestuurd te worden en de migratie in HomeAssistantSettingsStorage mag deze opgeslagen
     * per-onderdeel-waarden nooit meer overschrijven.
     */
    private fun buildHomeAssistantSettings(current: HaSettingsUiState): HomeAssistantSettings {
        return HomeAssistantSettings(
            baseUrls = current.baseUrls.filter { it.isNotBlank() },
            activeBaseUrl = current.activeBaseUrl,
            longLivedToken = current.longLivedToken,
            entities = current.entities.filter { it.isNotBlank() },
            showOnlyLinkedEntities = current.showOnlyLinkedEntities,
            backupAlarmEnabled = current.backupAlarmEnabled,
            alarmSpeaker = current.alarmSpeaker,
            timerSpeaker = current.timerSpeaker,
            weatherSpeaker = current.weatherSpeaker,
            speakerSettingsMigrated = true,
            presenceEntityId = current.selectedPresenceEntityId,
            presenceExpectedState = current.presenceExpectedState,
            outOfBedCheckEnabled = current.outOfBedCheckEnabled,
            outOfBedEntityId = current.outOfBedEntityId,
            outOfBedExpectedValue = current.outOfBedExpectedValue,
            backupAlarmDuration = current.backupAlarmDuration,
            alarmScriptEnabled = current.alarmScriptEnabled,
            alarmScriptEntityId = current.alarmScriptEntityId,
            timerScriptEnabled = current.timerScriptEnabled,
            timerScriptEntityId = current.timerScriptEntityId,
            alarmScriptIgnorePresence = current.alarmScriptIgnorePresence,
            timerScriptIgnorePresence = current.timerScriptIgnorePresence,
            weatherTtsEnabled = current.weatherTtsEnabled,
            notifyService = current.notifyService,
            safetyTimeoutSeconds = current.safetyTimeoutSeconds
        )
    }

    fun saveSettings() {
        viewModelScope.launch {
            saveSettingsInternal()
        }
    }

    /**
     * Daadwerkelijke save-logica, als suspend-functie zodat andere flows (zie
     * [selectPresenceEntityAndSave]) 'm binnen hun eigen coroutine kunnen afwachten vóórdat ze
     * een vervolgstap doen - [saveSettings] blijft de fire-and-forget-variant voor alle overige
     * aanroepen.
     */
    private suspend fun saveSettingsInternal() {
        val current = _uiState.value
        val cleanedSettings = buildHomeAssistantSettings(current)
        android.util.Log.d("HaSettingsViewModel", "Persisting HA entities: ${cleanedSettings.entities.size} item(s)")
        settingsStorage.saveSettings(cleanedSettings)

        // Wis de client cache zodat de volgende API call de nieuwe settings gebruikt
        repository.clearClientCache()

        if (!cleanedSettings.activeBaseUrl.isNullOrBlank() && !cleanedSettings.longLivedToken.isNullOrBlank()) {
            loadMediaPlayers()
            loadPresenceEntities()
            loadScripts()

            // Upload geselecteerd geluid naar HA en stuur URL mee in config-patch. De HA-push
            // dekt uitsluitend de watchdog van het agenda-alarm (zie buildConfigPatchJson), dus
            // bewust alarmSpeaker.soundId hier - niet timer/weather.
            val soundId = current.alarmSpeaker.soundId
            var soundUrl: String? = null
            if (soundId != null) {
                val sound = current.availableLocalSounds.find { it.id == soundId }
                if (sound != null) {
                    soundUrl = repository.uploadAndGetSoundUrl(sound)
                }
            }

            val patch = buildConfigPatchJson(cleanedSettings)
            val patchWithSound = if (soundUrl != null) {
                buildJsonObject {
                    patch.forEach { (key, value) -> put(key, value) }
                    put("default_sound_url", soundUrl)
                }
            } else patch

            repository.pushAgendaAlarmConfigPatch(patchWithSound)
        }
    }

    /**
     * Bouwt de JSON-patch voor POST /api/daykit/config uit de zojuist opgeslagen
     * [HomeAssistantSettings] - sleutelnamen moeten exact overeenkomen met CONFIG_FIELDS in
     * de HA-integratie's config_sync.py.
     *
     * Speaker-velden komen bewust uit [HomeAssistantSettings.alarmSpeaker], niet uit
     * timerSpeaker/weatherSpeaker: deze push voedt uitsluitend de HA-kant watchdog/fail-safe
     * backup-alarm (gemist agenda-alarm bij lege telefoonbatterij), een concept dat niet bestaat
     * voor Timer of Weer. Zonder deze scoping zou opslaan vanuit Timer/Weer per ongeluk de
     * watchdog-config overschrijven met timer/weer-instellingen.
     */
    private fun buildConfigPatchJson(settings: HomeAssistantSettings): JsonObject = buildJsonObject {
        // Stempel de push met een eigen tijdstempel. De HA-integratie bewaart deze waarde
        // ongewijzigd samen met bron "app" (zie config_sync.py), zodat de app bij de volgende
        // check haar eigen push herkent en de gebruiker niet vraagt om zijn eigen instellingen
        // "terug te zetten". Bewust de app-klok: de app vergelijkt straks alleen of de stempel
        // veranderd is, dus klokverschil tussen telefoon en server maakt niet uit.
        put("config_last_modified", System.currentTimeMillis())
        put("speaker_entity_id", settings.alarmSpeaker.entityId)
        put("speaker_mode", settings.alarmSpeaker.mode.name)
        put("presence_entity_id", settings.presenceEntityId)
        // De lokaal opgeslagen waarde, niet opnieuw afgeleid uit het entity-id: sinds deze ook
        // vanuit HA gezet kan worden (Presence-sectie, vrij invulbaar veld voor sensoren met een
        // afwijkende state) zou her-afleiden een net overgenomen HA-waarde meteen weer wegdrukken.
        // Bij het KIEZEN van een nieuwe entiteit wordt de waarde nog steeds afgeleid, zie
        // selectPresenceEntityAndSave/buildConfigFieldDiffs.
        put("presence_expected_state", settings.presenceExpectedState)
        put("out_of_bed_entity_id", settings.outOfBedEntityId)
        put("out_of_bed_expected_value", settings.outOfBedExpectedValue)
        put("out_of_bed_check_enabled", settings.outOfBedCheckEnabled)
        put("alarm_script_entity_id", settings.alarmScriptEntityId)
        put("alarm_script_enabled", settings.alarmScriptEnabled)
        put("alarm_script_ignore_presence", settings.alarmScriptIgnorePresence)
        put("timer_script_entity_id", settings.timerScriptEntityId)
        put("timer_script_enabled", settings.timerScriptEnabled)
        put("timer_script_ignore_presence", settings.timerScriptIgnorePresence)
        put("default_volume", settings.alarmSpeaker.volume)
        put("skip_backup_volume", settings.alarmSpeaker.skipVolume)
        put("default_interval", settings.backupAlarmDuration)
        // Timer-/weer-speaker: eigen HA-velden (timer_speaker_*/weather_speaker_*, zie
        // config_sync.py), los van de speaker_*/default_volume-velden hierboven die uitsluitend
        // de Agenda-alarm-watchdog voeden - deze push overschrijft dus nooit de watchdog-config,
        // in tegenstelling tot wat de oudere "bewust alleen alarmSpeaker"-scoping hierboven
        // (zie saveSettings()) nog suggereert voor het geluid-veld. Zo is nu ook Timer/Weer hun
        // eigen speaker/modus/volume vanuit HA's "Configureren"-scherm in te stellen, met de
        // wijziging zichtbaar in de app via de HA-update-check (zie buildConfigFieldDiffs()).
        put("timer_speaker_entity_id", settings.timerSpeaker.entityId)
        put("timer_speaker_mode", settings.timerSpeaker.mode.name)
        put("timer_speaker_volume", settings.timerSpeaker.volume)
        put("timer_speaker_skip_volume", settings.timerSpeaker.skipVolume)
        put("weather_speaker_entity_id", settings.weatherSpeaker.entityId)
        put("weather_speaker_mode", settings.weatherSpeaker.mode.name)
        put("weather_speaker_volume", settings.weatherSpeaker.volume)
        put("weather_speaker_skip_volume", settings.weatherSpeaker.skipVolume)
        put("weather_tts_enabled", settings.weatherTtsEnabled)
        // Geluid van Timer/Weer: alleen de URL, zonder upload. Anders dan het agenda-alarm-geluid
        // (default_sound_url hieronder) speelt HA deze twee nooit zelf af - de telefoon doet dat -
        // dus het bestand hoeft daar niet te staan; de naam in de URL is puur de gedeelde sleutel
        // waarmee beide kanten hetzelfde geluid bedoelen. Zou de app hier wél elke keer uploaden,
        // dan kostte elke save drie uploads i.p.v. één, voor bestanden die HA niet gebruikt.
        put("timer_speaker_sound_url", haSoundUrlFor(settings.timerSpeaker.soundId, settings.activeBaseUrl))
        put("weather_speaker_sound_url", haSoundUrlFor(settings.weatherSpeaker.soundId, settings.activeBaseUrl))
        // HA-only "Defaults"-velden: alleen meesturen als de app ze kent (null = nooit ingesteld/
        // overgenomen). HA negeert null-waarden sowieso (zie async_apply_config_patch), maar zo is
        // ook aan deze kant expliciet dat de app een in HA gezette waarde nooit wist.
        put("notify_service", settings.notifyService)
        put("safety_timeout_seconds", settings.safetyTimeoutSeconds)
        // default_sound_url: HA-URL van het geuploadde geluid wordt ingevuld door
        // uploadSelectedSoundToHa() na een succesvolle upload. Hier bewust niet
        // meesturen: de URL is pas geldig na upload.
        put("entities", kotlinx.serialization.json.JsonArray(settings.entities.map { kotlinx.serialization.json.JsonPrimitive(it) }))
    }

    /**
     * Bouwt de gedeelde HA-URL voor een lokaal geluid ([SpeakerSettings.soundId]) - dezelfde vorm
     * als waar [com.dd.daykit.homeassistant.SoundHaSync] naartoe uploadt, zodat beide
     * kanten hetzelfde geluid met dezelfde string aanduiden. null als er geen geluid gekozen is,
     * het geluid niet (meer) bestaat, of er nog geen actieve HA-URL is.
     */
    private fun haSoundUrlFor(soundId: Long?, baseUrl: String?): String? {
        if (soundId == null || baseUrl.isNullOrBlank()) return null
        val sound = _uiState.value.availableLocalSounds.find { it.id == soundId } ?: return null
        val filename = com.dd.daykit.homeassistant.SoundHaSync.safeUploadedFilename(sound)
        return com.dd.daykit.homeassistant.HaPaths.soundUrl(baseUrl, filename)
    }

    /**
     * Omgekeerde van [haSoundUrlFor]: zoekt bij een HA-geluids-URL het lokale geluid op via de
     * bestandsnaam. null als de app dat geluid niet (meer) heeft - dan is er niets zinnigs over te
     * nemen en verschijnt er dus ook geen keuze voor in de HA-update-melding.
     */
    private fun soundIdForHaUrl(url: String?): Long? {
        if (url.isNullOrBlank()) return null
        val filename = url.substringAfterLast('/').substringBefore('?')
        if (filename.isBlank()) return null
        return _uiState.value.availableLocalSounds.find {
            com.dd.daykit.homeassistant.SoundHaSync.safeUploadedFilename(it)
                .equals(filename, ignoreCase = true)
        }?.id
    }

    /** Weergavenaam van een lokaal geluid voor de HA-update-melding. */
    private fun soundNameFor(soundId: Long?): String =
        soundId?.let { id -> _uiState.value.availableLocalSounds.find { it.id == id }?.name }
            ?: "(geen)"

    fun onTestConnectionClicked() {
        viewModelScope.launch {
            saveSettings()
            
            _connectionTestState.value = HaConnectionTestUiState(isTesting = true, lastMessage = null)
            
            val result = repository.testConnectionOverAllUrls()
            
            when (result) {
                is HaConnectionResult.Success -> {
                    _uiState.value = _uiState.value.copy(activeBaseUrl = result.baseUrl)
                    
                    _connectionTestState.value = HaConnectionTestUiState(
                        isTesting = false,
                        lastMessage = "Verbinding succesvol! HA bereikbaar via: ${result.baseUrl}"
                    )
                }
                is HaConnectionResult.Error -> {
                    _connectionTestState.value = HaConnectionTestUiState(
                        isTesting = false,
                        lastMessage = "Fout: ${result.message}"
                    )
                }
            }
        }
    }

    /**
     * Wissel een (gescande QR of handmatig ingevoerde) setup-code in voor een long-lived
     * token via de DayKit HA-integratie (vervangt handmatig token kopieren/plakken).
     * Bij succes wordt [baseUrl] toegevoegd aan de URL-lijst en als actief adres gezet, het
     * teruggekregen token opgeslagen, en de verbinding meteen toegepast (media players,
     * presence entities, scripts laden).
     */
    fun pairWithSetupCode(baseUrl: String, code: String, batteryUsagePerHour: Int? = null) {
        viewModelScope.launch {
            _qrPairingState.value = QrPairingUiState(isPairing = true)

            val result = com.dd.daykit.homeassistant.PairingClient.exchangeCode(baseUrl, code)
            when (result) {
                is com.dd.daykit.homeassistant.PairingClient.PairingResult.Success -> {
                    val existingUrls = _uiState.value.baseUrls.filter { it.isNotBlank() }.toMutableSet()
                    existingUrls.add(result.baseUrl)
                    _uiState.value = _uiState.value.copy(
                        baseUrls = existingUrls.toList(),
                        activeBaseUrl = result.baseUrl,
                        longLivedToken = result.token
                    )
                    // NIET de ViewModel's saveSettings() gebruiken hier - die start zelf een
                    // losse viewModelScope.launch en retourneert meteen (fire-and-forget),
                    // dus de verbindingstest hieronder kan dan een client pakken die het
                    // nieuwe token nog niet kent ("token ontbreekt", ook al is het token wel
                    // degelijk ontvangen). Rechtstreeks + afgewacht opslaan i.p.v. daarvan -
                    // zelfde velden als saveSettings(), maar synchroon binnen deze coroutine.
                    val current = _uiState.value
                    val settingsToSave = buildHomeAssistantSettings(current)
                    settingsStorage.saveSettings(settingsToSave)   // suspend fun, ECHT afgewacht
                    repository.clearClientCache()                 // synchroon, dwingt nieuw token te gebruiken

                    // Wacht ECHT op de verbindingstest i.p.v. het fire-and-forget
                    // applyHomeAssistantSettings() aan te roepen - anders kan er een vals
                    // succesbericht getoond worden terwijl de verbinding stuk is (dat
                    // sluit ook het koppelscherm meteen, zie de qrPairingState-observer
                    // in HaSettingsActivity.kt). Het token is op dit punt al opgeslagen
                    // en HA's is_paired staat al op True (server-side onvoorwaardelijk
                    // zodra de code geldig was) - een mislukte verbindingstest hier maakt
                    // de koppeling zelf niet ongedaan, maar toont wel een eerlijke fout
                    // i.p.v. een vals "Gekoppeld!".
                    val connectionResult = repository.testConnectionOverAllUrls()
                    when (connectionResult) {
                        is HaConnectionResult.Success -> {
                            _uiState.value = _uiState.value.copy(activeBaseUrl = connectionResult.baseUrl)
                            // Rest van de initialisatie (media players, presence, scripts,
                            // gedeelde config) mag wel fire-and-forget - dat is alleen
                            // UI-verrijking, geen correctheids-signaal. Pusht ook de zojuist
                            // opgeslagen lokale instellingen naar HA (app is leidend, zie
                            // applyHomeAssistantSettings) en het lokale batterijverbruik.
                            applyHomeAssistantSettings(batteryUsagePerHour)
                            _qrPairingState.value = QrPairingUiState(
                                isPairing = false,
                                lastMessage = "Gekoppeld met Home Assistant via ${connectionResult.baseUrl}!",
                                isError = false
                            )
                        }
                        is HaConnectionResult.Error -> {
                            _qrPairingState.value = QrPairingUiState(
                                isPairing = false,
                                lastMessage = "Token ontvangen, maar verbinding testen mislukte: ${connectionResult.message}. Controleer je netwerk en probeer het opnieuw via Testverbinding.",
                                isError = true
                            )
                        }
                    }
                }
                is com.dd.daykit.homeassistant.PairingClient.PairingResult.Error -> {
                    _qrPairingState.value = QrPairingUiState(
                        isPairing = false,
                        lastMessage = "Koppelen mislukt: ${result.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun clearQrPairingMessage() {
        _qrPairingState.value = _qrPairingState.value.copy(lastMessage = null, isError = false)
    }

    /**
     * Ontkoppelt lokaal: wist token + actief adres uit de UI-state en persisteert dat via
     * [saveSettings] (zelfde patroon, maar leeg makend i.p.v. vullend). Het long-lived token
     * blijft in HA's Profiel > Long-Lived Access Tokens bestaan - dat hoeft niet serverside
     * ingetrokken te worden om lokaal te "vergeten".
     */
    fun clearPairing() {
        _uiState.value = _uiState.value.copy(longLivedToken = "", activeBaseUrl = null)
        saveSettings()
    }

    fun onTestSensorsClicked() {
        viewModelScope.launch {
            saveSettings()

            _sensorCheckState.value = SensorCheckUiState(isChecking = true, results = emptyList())

            val results = repository.checkConfiguredSensors()

            _sensorCheckState.value = SensorCheckUiState(
                isChecking = false,
                results = results
            )
        }
    }
    
    fun loadMediaPlayers() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.activeBaseUrl.isNullOrBlank() || currentState.longLivedToken.isBlank()) {
                _uiState.value = currentState.copy(speakerError = "HA connectie niet geconfigureerd.")
                return@launch
            }

            _uiState.value = currentState.copy(isLoadingSpeakers = true, speakerError = null)

            // Alleen entiteiten uit de gedeelde/gekoppelde entiteiten-lijst (uiState.entities) -
            // door de gebruiker beheerd via het Entiteiten-scherm in de app (de app is leidend,
            // zie applyHomeAssistantSettings). Nooit een live "toon alles wat er in HA staat"-
            // fallback meer: anders komen er entiteiten in de picker die niet in de gedeelde
            // lijst staan.
            val mediaPlayers = currentState.entities
                .filter { it.isNotBlank() && it.startsWith("media_player.") }
                .map { entityId ->
                    HaEntity(
                        entityId = entityId,
                        friendlyName = entityId.substringAfter(".").replace("_", " ").replaceFirstChar { it.uppercase() },
                        state = "unknown",
                        domain = "media_player"
                    )
                }

            if (mediaPlayers.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoadingSpeakers = false,
                    availableMediaPlayers = emptyList(),
                    speakerError = "Geen media_player entiteiten toegevoegd. Voeg ze toe in de Entiteiten-lijst (in de app of via HA > Configureren)."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoadingSpeakers = false,
                    availableMediaPlayers = mediaPlayers,
                    speakerError = null
                )
            }
        }
    }
    
    /** Huidige [SpeakerSettings] voor [context] - zie SpeakerContext (ALARM/TIMER/WEATHER). */
    fun speakerSettingsFor(context: SpeakerContext): SpeakerSettings = when (context) {
        SpeakerContext.ALARM -> _uiState.value.alarmSpeaker
        SpeakerContext.TIMER -> _uiState.value.timerSpeaker
        SpeakerContext.WEATHER -> _uiState.value.weatherSpeaker
    }

    private fun updateSpeakerSettings(context: SpeakerContext, transform: (SpeakerSettings) -> SpeakerSettings) {
        val current = _uiState.value
        _uiState.value = when (context) {
            SpeakerContext.ALARM -> current.copy(alarmSpeaker = transform(current.alarmSpeaker))
            SpeakerContext.TIMER -> current.copy(timerSpeaker = transform(current.timerSpeaker))
            SpeakerContext.WEATHER -> current.copy(weatherSpeaker = transform(current.weatherSpeaker))
        }
    }

    /**
     * Slaat de volledige [SpeakerSettings] op voor [context], en - indien meegegeven - ook voor
     * de context(en) in [alsoApplyTo] (de "ook toepassen op..."-melding in SpeakerModal). Zet in
     * 1 keer alle betrokken contexten en persisteert daarna eenmalig, i.p.v. per veld los op te
     * slaan zoals de overige setters hieronder.
     */
    fun saveSpeakerSettings(context: SpeakerContext, settings: SpeakerSettings, alsoApplyTo: Set<SpeakerContext> = emptySet()) {
        var updated = _uiState.value
        for (target in setOf(context) + alsoApplyTo) {
            updated = when (target) {
                SpeakerContext.ALARM -> updated.copy(alarmSpeaker = settings)
                SpeakerContext.TIMER -> updated.copy(timerSpeaker = settings)
                SpeakerContext.WEATHER -> updated.copy(weatherSpeaker = settings)
            }
        }
        _uiState.value = updated
        saveSettings()
    }

    /**
     * Past de velden toe die uit een geplakte export komen en verder nergens een losse setter
     * hebben, zonder tussentijds op te slaan - de import-flow roept daarna één keer
     * [saveSettings] aan. Elk argument is nullable: `null` betekent "stond niet in de geplakte
     * tekst", en dan blijft de huidige waarde staan. Zonder deze functie vielen deze instellingen
     * bij exporteren+plakken stilzwijgend terug op hun standaardwaarde.
     */
    fun applyImportedHaExtras(
        activeBaseUrl: String? = null,
        showOnlyLinkedEntities: Boolean? = null,
        weatherTtsEnabled: Boolean? = null,
        notifyService: String? = null,
        safetyTimeoutSeconds: Int? = null
    ) {
        var updated = _uiState.value
        if (!activeBaseUrl.isNullOrBlank()) updated = updated.copy(activeBaseUrl = activeBaseUrl)
        showOnlyLinkedEntities?.let { updated = updated.copy(showOnlyLinkedEntities = it) }
        weatherTtsEnabled?.let { updated = updated.copy(weatherTtsEnabled = it) }
        if (!notifyService.isNullOrBlank()) updated = updated.copy(notifyService = notifyService)
        safetyTimeoutSeconds?.let { updated = updated.copy(safetyTimeoutSeconds = it) }
        _uiState.value = updated
    }

    /**
     * Past de speaker-instellingen van één onderdeel aan zonder op te slaan - bedoeld voor de
     * import-flow, die alle drie de onderdelen achter elkaar zet en daarna één keer opslaat.
     * Losse setters gebruiken zou per veld een save (en dus een HA-push) veroorzaken.
     */
    fun applyImportedSpeaker(context: SpeakerContext, transform: (SpeakerSettings) -> SpeakerSettings) {
        updateSpeakerSettings(context, transform)
    }

    fun selectSpeaker(context: SpeakerContext, entityId: String?) {
        updateSpeakerSettings(context) { it.copy(entityId = entityId) }
    }

    fun setSpeakerMode(context: SpeakerContext, mode: ExternalSpeakerMode) {
        updateSpeakerSettings(context) { it.copy(mode = mode) }
    }

    /** Aan/uit voor het uitspreken van weeralarmen op een HA-speaker; direct opgeslagen (geen apart save-scherm). */
    fun toggleWeatherTts(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(weatherTtsEnabled = enabled)
        saveSettings()
    }

    fun testSpeaker(context: Context, speakerContext: SpeakerContext) {
        viewModelScope.launch {
            val speaker = speakerSettingsFor(speakerContext)
            val speakerEntityId = speaker.entityId

            if (speakerEntityId.isNullOrBlank()) {
                _speakerTestState.value = SpeakerTestUiState(isTesting = false, lastMessage = "Selecteer eerst een speaker") // TODO: use string resource
                return@launch
            }

            saveSettings()

            _speakerTestState.value = SpeakerTestUiState(isTesting = true, lastMessage = null)

            if (!speaker.skipVolume) {
                val volume = speaker.volume.coerceIn(0, 100)
                val volumeResult = repository.setSpeakerVolume(speakerEntityId, volume)
                if (volumeResult is HaPlayMediaResult.Error) {
                    android.util.Log.w("HaSettingsViewModel", "testSpeaker: setSpeakerVolume failed: ${volumeResult.message}")
                }
            }

            // Dezelfde geluid-resolutie als een echt alarm (zie AlarmService.kt/
            // ExtraTimerManager.kt): eerst het geconfigureerde HA-backup-geluid, anders het
            // geluid dat normaal via de HTTP-server vanaf de telefoon gestreamed wordt naar de
            // externe speaker. Voorheen speelde deze knop altijd het hardcoded backup-/
            // Disco-geluid, ongeacht instellingen - dat testte dus nooit de eigenlijke
            // streaming-naar-speaker-route die Standaard/Beide-modus tijdens een echt alarm
            // gebruikt, en gaf zo een vals "dit werkt" gevoel terwijl die route stuk kon zijn.
            val testSoundUrl = ExternalSpeakerHelper.getExternalSpeakerSoundUrl(context, speakerContext)
                ?: ExternalSpeakerHelper.getAlarmSoundUrl(context, SettingsManager.getAlarmSoundUri(context))
            val result = repository.playMediaOnSpeaker(
                entityId = speakerEntityId,
                mediaContentId = testSoundUrl,
                mediaContentType = "music"
            )
            
            when (result) {
                is HaPlayMediaResult.Success -> {
                    _speakerTestState.value = SpeakerTestUiState(isTesting = false, lastMessage = "Test succesvol! Speaker speelt geluid af.") // TODO: use string resource
                }
                is HaPlayMediaResult.Error -> {
                    _speakerTestState.value = SpeakerTestUiState(isTesting = false, lastMessage = "Fout: ${result.message}")
                }
            }
        }
    }
    
    fun loadPresenceEntities() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.activeBaseUrl.isNullOrBlank() || currentState.longLivedToken.isBlank()) {
                return@launch
            }
            _uiState.value = currentState.copy(isLoadingPresenceEntities = true)

            val presenceDomains = listOf("binary_sensor", "person", "device_tracker", "input_boolean", "zone")

            // Alleen entiteiten uit de gedeelde/gekoppelde entiteiten-lijst - zie toelichting bij
            // loadMediaPlayers(). Geen live "alles uit HA"-fallback meer.
            val presenceEntities = currentState.entities
                .filter { it.isNotBlank() && it.substringBefore('.') in presenceDomains }
                .map { entityId ->
                    HaEntity(
                        entityId = entityId,
                        friendlyName = entityId.substringAfter(".").replace("_", " ").replaceFirstChar { it.uppercase() },
                        state = "unknown",
                        domain = entityId.substringBefore(".")
                    )
                }

            _uiState.value = _uiState.value.copy(
                isLoadingPresenceEntities = false,
                availablePresenceEntities = presenceEntities
            )
        }
    }
    
    /**
     * Probeert automatisch een presence-entiteit te kiezen als er nog geen gekozen is, zodat
     * de gebruiker niet zelf een entity-picker hoeft te doorzoeken. Overschrijft NOOIT een al
     * gekozen [HaSettingsUiState.selectedPresenceEntityId] - alleen relevant bij een verse/nog
     * niet geconfigureerde koppeling.
     *
     * Volgorde:
     * 1. De ingebouwde ping-detectie (binary_sensor.py's PhoneReachableBinarySensor, gevoed
     *    door de app's eigen phone_ip-rapportage, zie AlarmOutputDecisionEngine.getPhoneIpAddress) -
     *    zodra die al een echt resultaat heeft opgeleverd (state "on"/"off", niet meer
     *    "unavailable"/"unknown"). Geen internet/GPS-permissie nodig, dus de veiligste default.
     * 2. Anders, als er precies één `person.*`-entiteit in deze HA-instantie bestaat: die
     *    entiteit (HA's eigen GPS/router/Bluetooth-aggregatie via de companion-app). Bij nul of
     *    meerdere `person.*`-entiteiten wordt bewust niks gekozen - een verkeerde gok bij een
     *    multi-gebruikers-instantie is erger dan geen suggestie.
     *
     * Gebruikt [HomeAssistantRepository.fetchAllEntities] uitsluitend voor deze eenmalige,
     * gerichte zoekactie - dit draait de "geen live alles-uit-HA-fallback"-beslissing van
     * [loadPresenceEntities] niet terug, dat blijft beperkt tot de gekoppelde entiteiten-lijst.
     */
    private suspend fun tryAutoAdoptPresenceEntity() {
        if (!_uiState.value.selectedPresenceEntityId.isNullOrBlank()) {
            return // gebruiker (of eerdere auto-adopt) heeft al iets gekozen, nooit overschrijven
        }

        val allEntities = repository.fetchAllEntities()

        val pingSensor = allEntities.firstOrNull {
            it.domain == "binary_sensor" &&
                it.entityId.contains("phone_reachable_ping") &&
                it.state != "unavailable" && it.state != "unknown"
        }
        if (pingSensor != null) {
            adoptPresenceEntity(pingSensor.entityId)
            android.util.Log.i("HaSettingsViewModel", "Auto-adopted ping presence sensor: ${pingSensor.entityId}")
            return
        }

        val personEntities = allEntities.filter { it.domain == "person" }
        if (personEntities.size == 1) {
            val person = personEntities.first()
            adoptPresenceEntity(person.entityId)
            android.util.Log.i("HaSettingsViewModel", "Auto-adopted single person entity: ${person.entityId}")
        } else if (personEntities.size > 1) {
            android.util.Log.i("HaSettingsViewModel", "Multiple person.* entities found (${personEntities.size}), not auto-adopting - user must choose")
        }
    }

    private fun adoptPresenceEntity(entityId: String) {
        val current = _uiState.value
        val updatedEntities = if (entityId in current.entities) {
            current.entities
        } else {
            current.entities.filter { it.isNotBlank() } + entityId
        }
        _uiState.value = current.copy(
            entities = updatedEntities,
            selectedPresenceEntityId = entityId,
            presenceExpectedState = com.dd.daykit.data.deriveExpectedPresenceState(entityId)
        )
    }

    /**
     * Handmatige override door de gebruiker (via de vereenvoudigde PresenceModal-entiteitpicker).
     * Leeg/blanco = terug naar automatisch (ping-sensor / enkele person.-entiteit, zie
     * tryAutoAdoptPresenceEntity) - er is bewust geen losse "uitgeschakeld"-staat meer.
     */
    /**
     * Ververst de live "ben ik thuis"-status voor de alleen-lezen statusregel in de UI (geen
     * aan/uit-schakelaar meer, presence-detectie draait altijd) - zelfde logica als
     * [AlarmOutputDecisionEngine.isUserAtHome] (geen presence-entiteit -> aannemen dat je thuis
     * bent), maar hier puur voor weergave; de daadwerkelijke alarmbeslissing blijft daar.
     */
    fun refreshPresenceStatus() {
        viewModelScope.launch {
            refreshPresenceStatusInternal()
        }
    }

    /**
     * Suspend-variant van [refreshPresenceStatus], zodat [selectPresenceEntityAndSave] 'm binnen
     * dezelfde coroutine kan afwachten (i.p.v. via een losse, niet-afgewachte viewModelScope.launch
     * waarbij een directe `if (...presenceIsHome == null)`-check erna gewoon een race zou zijn).
     */
    private suspend fun refreshPresenceStatusInternal() {
        val entityId = _uiState.value.selectedPresenceEntityId
        if (entityId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(presenceIsHome = true, isLoadingPresenceStatus = false)
            return
        }
        _uiState.value = _uiState.value.copy(isLoadingPresenceStatus = true)
        val isHome = try {
            val state = repository.getEntityState(entityId)
            // "unavailable"/"unknown" zijn HA's eigen speciale placeholder-states, geen echte
            // "niet thuis"-waarde - bv. de ingebouwde ping-sensor (binary_sensor.py) staat hier
            // altijd eerst in totdat de eerste ping is voltooid, wat na elke integratie-herlaad
            // (elke instellingen-save, zie saveSettingsInternal) opnieuw gebeurt. Die
            // rechtstreeks vergelijken met de verwachte state gaf hiervoor ten onrechte een
            // confident "Onwaar" i.p.v. "Onbekend" (zie ook selectPresenceEntityAndSave, die
            // hier juist voor een korte vertraging + herhaling zorgt).
            if (state.state.equals("unavailable", ignoreCase = true) || state.state.equals("unknown", ignoreCase = true)) {
                null
            } else {
                state.state.equals(com.dd.daykit.data.deriveExpectedPresenceState(entityId), ignoreCase = true)
            }
        } catch (e: Exception) {
            android.util.Log.w("HaSettingsViewModel", "refreshPresenceStatus failed for $entityId", e)
            null
        }
        _uiState.value = _uiState.value.copy(presenceIsHome = isHome, isLoadingPresenceStatus = false)
    }

    fun selectPresenceEntity(entityId: String) {
        _uiState.value = _uiState.value.copy(
            selectedPresenceEntityId = entityId.ifBlank { null },
            presenceExpectedState = com.dd.daykit.data.deriveExpectedPresenceState(entityId.ifBlank { null })
        )
    }

    /**
     * Combineert entiteit-wissel + opslaan + verversen in 1 flow (i.p.v. de losse
     * selectPresenceEntity()/saveSettings()/refreshPresenceStatus()-aanroepen die de UI hiervoor
     * los na elkaar deed): een save pusht een config-patch naar HA, en HA herlaadt daarbij de
     * hele integratie (zie config_sync.py) - de ingebouwde ping-sensor begint na zo'n herlaad
     * altijd weer met "unavailable" totdat de eerste ping binnen is. Een refresh die daar
     * ONMIDDELLIJK na de save aan komt, ving dus vaak nog die tussenstand. Nu even wachten en zo
     * nodig 1x herhalen, zodat de gebruiker niet zelf handmatig op ververs hoeft te drukken
     * (die knop staat er nu wel altijd, als achtervanger).
     */
    fun selectPresenceEntityAndSave(entityId: String) {
        selectPresenceEntity(entityId)
        viewModelScope.launch {
            saveSettingsInternal()
            delay(2500)
            refreshPresenceStatusInternal()
            // Nog niets terug (waarschijnlijk was de eerste ping nog niet klaar) - 1x extra
            // proberen na wat langere adempauze, i.p.v. de gebruiker meteen op "Onbekend" te
            // laten zitten totdat die zelf de ververs-knop indrukt.
            if (_uiState.value.presenceIsHome == null) {
                delay(3000)
                refreshPresenceStatusInternal()
            }
        }
    }

    fun setPresenceExpectedState(state: String) {
        _uiState.value = _uiState.value.copy(presenceExpectedState = state)
    }
    
    fun setOutOfBedCheckEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(outOfBedCheckEnabled = enabled)
    }
    
    fun selectOutOfBedEntity(entityId: String) {
        _uiState.value = _uiState.value.copy(outOfBedEntityId = entityId)
    }
    
    fun setOutOfBedExpectedValue(value: String) {
        _uiState.value = _uiState.value.copy(outOfBedExpectedValue = value)
    }

    /**
     * Ververst de live "lig ik nog in bed"-status voor de alleen-lezen statusregel - zelfde
     * logica/valkuil als [refreshPresenceStatus]: "unavailable"/"unknown" (bv. vlak na een
     * integratie-herlaad, nog voordat de entity een eerste geldige state heeft) is geen echte
     * "niet meer in bed"-waarde, dus die wordt hier als onbekend (null) behandeld i.p.v. als
     * confident false.
     */
    fun refreshOutOfBedStatus() {
        viewModelScope.launch {
            refreshOutOfBedStatusInternal()
        }
    }

    private suspend fun refreshOutOfBedStatusInternal() {
        val current = _uiState.value
        if (!current.outOfBedCheckEnabled || current.outOfBedEntityId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(outOfBedIsInBed = null, isLoadingOutOfBedStatus = false)
            return
        }
        val entityId = current.outOfBedEntityId
        _uiState.value = _uiState.value.copy(isLoadingOutOfBedStatus = true)
        val isInBed = try {
            val state = repository.getEntityState(entityId)
            if (state.state.equals("unavailable", ignoreCase = true) || state.state.equals("unknown", ignoreCase = true)) {
                null
            } else {
                state.state.equals(_uiState.value.outOfBedExpectedValue, ignoreCase = true)
            }
        } catch (e: Exception) {
            android.util.Log.w("HaSettingsViewModel", "refreshOutOfBedStatus failed for $entityId", e)
            null
        }
        _uiState.value = _uiState.value.copy(outOfBedIsInBed = isInBed, isLoadingOutOfBedStatus = false)
    }

    /**
     * Combineert de OutOfBedModal-opslag + verversen in 1 flow, zelfde reden/aanpak als
     * [selectPresenceEntityAndSave]: een save herlaadt de HA-integratie, dus een refresh die er
     * ONMIDDELLIJK na komt vangt vaak nog een tussenstand ("unavailable"). Even wachten en zo
     * nodig 1x herhalen.
     */
    fun saveOutOfBedSettings(entity: String?, value: String, enabled: Boolean) {
        setOutOfBedCheckEnabled(enabled)
        if (enabled && entity != null) {
            selectOutOfBedEntity(entity)
            setOutOfBedExpectedValue(value)
        } else if (!enabled) {
            selectOutOfBedEntity("")
        }
        viewModelScope.launch {
            saveSettingsInternal()
            if (!enabled) {
                _uiState.value = _uiState.value.copy(outOfBedIsInBed = null, isLoadingOutOfBedStatus = false)
                return@launch
            }
            delay(2500)
            refreshOutOfBedStatusInternal()
            if (_uiState.value.outOfBedIsInBed == null) {
                delay(3000)
                refreshOutOfBedStatusInternal()
            }
        }
    }

    // Scripts (script.turn_on) - losse instellingen voor alarm en timer
    fun loadScripts() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.activeBaseUrl.isNullOrBlank() || currentState.longLivedToken.isBlank()) {
                return@launch
            }

            // Alleen entiteiten uit de gedeelde/gekoppelde entiteiten-lijst - zie toelichting bij
            // loadMediaPlayers(). Geen live "alles uit HA"-fallback meer.
            val scripts = currentState.entities
                .filter { it.isNotBlank() && it.startsWith("script.") }
                .map { entityId ->
                    com.dd.daykit.data.HaEntity(
                        entityId = entityId,
                        friendlyName = entityId.substringAfter(".").replace("_", " ").replaceFirstChar { it.uppercase() },
                        state = "unknown",
                        domain = "script"
                    )
                }

            _uiState.value = _uiState.value.copy(availableScripts = scripts)
        }
    }

    fun setAlarmScriptEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(alarmScriptEnabled = enabled)
    }

    fun selectAlarmScript(entityId: String) {
        _uiState.value = _uiState.value.copy(alarmScriptEntityId = entityId)
    }

    fun setTimerScriptEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(timerScriptEnabled = enabled)
    }

    fun selectTimerScript(entityId: String) {
        _uiState.value = _uiState.value.copy(timerScriptEntityId = entityId)
    }

    fun setAlarmScriptIgnorePresence(ignorePresence: Boolean) {
        _uiState.value = _uiState.value.copy(alarmScriptIgnorePresence = ignorePresence)
    }

    fun setTimerScriptIgnorePresence(ignorePresence: Boolean) {
        _uiState.value = _uiState.value.copy(timerScriptIgnorePresence = ignorePresence)
    }

    /**
     * Stuur het batterijverbruik-per-uur ook naar Home Assistant (input_number.mobiel_batterij_per_uur),
     * zodat het ook daar zichtbaar/bruikbaar is. Best-effort, blokkeert de UI niet.
     */
    fun syncBatteryUsageToHomeAssistant(value: Int) {
        viewModelScope.launch {
            val result = repository.setBatteryUsagePerHour(value.toDouble())
            if (result is com.dd.daykit.data.HaUpdateResult.Error) {
                android.util.Log.w("HaSettingsViewModel", "syncBatteryUsageToHomeAssistant failed: ${result.message}")
            }
        }
    }

    // Alarm Backup functies (speaker komt automatisch van de SpeakerSettings van de betreffende context)
    fun setBackupVolume(context: SpeakerContext, volume: Int) {
        updateSpeakerSettings(context) { it.copy(volume = volume.coerceIn(0, 100)) }
    }

    fun setSkipBackupVolume(context: SpeakerContext, skip: Boolean) {
        updateSpeakerSettings(context) { it.copy(skipVolume = skip) }
    }

    fun setBackupAlarmDuration(duration: Int) {
        _uiState.value = _uiState.value.copy(backupAlarmDuration = duration.coerceIn(0, 600))
    }

    fun setSelectedBackupSoundId(context: SpeakerContext, soundId: Long?) {
        updateSpeakerSettings(context) { it.copy(soundId = soundId) }
    }

    fun loadLocalSounds() {
        viewModelScope.launch {
            loadLocalSoundsInternal()
        }
    }

    /**
     * Suspend-variant van [loadLocalSounds] - nodig omdat de HA-update-check de geluiden-lijst al
     * moet hebben om een geluids-URL uit HA te kunnen koppelen aan een lokaal geluid (zie
     * [soundIdForHaUrl]); met de fire-and-forget-variant was die lijst daar nog leeg en verscheen
     * er dus nooit een geluid-regel in de melding.
     */
    private suspend fun loadLocalSoundsInternal() {
        run {
            _uiState.value = _uiState.value.copy(isLoadingSounds = true)
            try {
                val sounds = repository.getLocalSounds()
                // Voor elk van de 3 onderdelen apart: als het huidig gekozen geluid niet (meer)
                // bestaat, val terug op het eerste beschikbare geluid - zelfde gedrag als vóór de
                // speaker-splitsing, nu per context toegepast.
                fun SpeakerSettings.withValidSound(): SpeakerSettings {
                    val valid = soundId != null && sounds.any { it.id == soundId }
                    return if (valid) this else copy(soundId = sounds.firstOrNull()?.id)
                }
                val current = _uiState.value
                _uiState.value = current.copy(
                    availableLocalSounds = sounds,
                    alarmSpeaker = current.alarmSpeaker.withValidSound(),
                    timerSpeaker = current.timerSpeaker.withValidSound(),
                    weatherSpeaker = current.weatherSpeaker.withValidSound(),
                    isLoadingSounds = false
                )
            } catch (e: Exception) {
                android.util.Log.e("HaSettingsViewModel", "Failed to load local sounds", e)
                _uiState.value = _uiState.value.copy(isLoadingSounds = false)
            }
        }
    }

    fun testBackupAlarmWithCurrentState(
        speakerContext: SpeakerContext,
        volume: Int,
        skipVolume: Boolean = false,
        selectedSoundId: Long?
    ) {
        viewModelScope.launch {
            val current = _uiState.value
            val speakerEntityId = speakerSettingsFor(speakerContext).entityId

            if (speakerEntityId.isNullOrBlank()) {
                _backupAlarmTestState.value = BackupAlarmTestUiState(
                    isTesting = false,
                    lastMessage = "Selecteer eerst een externe speaker in de hoofdinstellingen"
                )
                return@launch
            }

            _backupAlarmTestState.value = BackupAlarmTestUiState(isTesting = true, lastMessage = null)

            val soundUrl = if (selectedSoundId != null) {
                val sound = current.availableLocalSounds.find { it.id == selectedSoundId }
                if (sound != null) {
                    // OrError-variant i.p.v. uploadAndGetSoundUrl: zo kan de gebruiker de
                    // daadwerkelijke reden zien (timeout, HTTP-fout, ...) i.p.v. een generieke
                    // "mislukt"-melding zonder aanknopingspunt.
                    val uploadResult = repository.uploadAndGetSoundUrlOrError(sound)
                    val url = uploadResult.getOrNull()
                    if (url != null) url else {
                        _backupAlarmTestState.value = BackupAlarmTestUiState(
                            isTesting = false,
                            lastMessage = "Upload naar HA mislukt: ${uploadResult.exceptionOrNull()?.message ?: "onbekende fout"}"
                        )
                        return@launch
                    }
                } else {
                    current.activeBaseUrl?.trimEnd('/')?.let {
                        com.dd.daykit.homeassistant.HaPaths.soundUrl(it, "discoAlarmBackupAlarm.mp3")
                    } ?: return@launch
                }
            } else {
                current.activeBaseUrl?.trimEnd('/')?.let {
                    com.dd.daykit.homeassistant.HaPaths.soundUrl(it, "discoAlarmBackupAlarm.mp3")
                } ?: return@launch
            }

            if (!skipVolume) {
                val volumeResult = repository.setSpeakerVolume(speakerEntityId, volume)
                if (volumeResult is HaPlayMediaResult.Error) {
                    android.util.Log.w("HaSettingsViewModel", "testBackupAlarm: setSpeakerVolume failed: ${volumeResult.message}")
                }
            }

            val playResult = repository.playMediaOnSpeaker(
                entityId = speakerEntityId,
                mediaContentId = soundUrl,
                mediaContentType = "music"
            )

            when (playResult) {
                is HaPlayMediaResult.Success -> {
                    _backupAlarmTestState.value = BackupAlarmTestUiState(
                        isTesting = false,
                        lastMessage = "Test gestart op $speakerEntityId. Check je speaker!"
                    )
                }
                is HaPlayMediaResult.Error -> {
                    _backupAlarmTestState.value = BackupAlarmTestUiState(
                        isTesting = false,
                        lastMessage = "Fout: ${playResult.message}"
                    )
                }
            }
        }
    }

    /**
     * Test voor de weer-tts-knop: volgt de op dat moment (nog niet opgeslagen) gekozen
     * speaker-modus in [com.dd.daykit.ui.modals.SpeakerModal] i.p.v. altijd alleen de telefoon te
     * testen - zo test "Standaard" de HA-speaker (met dezelfde fallback-naar-telefoon als een
     * echte weermelding, zie [com.dd.daykit.WeatherAlertWorker.speakWeatherAlertIfEnabled]) en
     * test "Beide" HA-speaker en telefoon gelijktijdig (via [coroutineScope]/[async], niet na
     * elkaar - anders duurt de test tot 2x zo lang door de losse tts-timeouts van elke route).
     * Best-effort net als de eigenlijke weermelding voor het telefoon-deel: geen falen-signaal,
     * dus "uitgevoerd" betekent niet per se "hoorbaar" (bv. stille telefoon/ontbrekende
     * tts-engine). Het HA-deel geeft wel een succes/fout terug (zie
     * [com.dd.daykit.WeatherAlertWorker.testWeatherSpeechOnHaSpeaker]).
     */
    fun testWeatherSpeaker(
        context: Context,
        testHaSpeaker: Boolean,
        testPhoneSpeaker: Boolean,
        speakerEntityId: String?,
        volume: Int,
        skipVolume: Boolean
    ) {
        viewModelScope.launch {
            if (testHaSpeaker && speakerEntityId.isNullOrBlank()) {
                _backupAlarmTestState.value = BackupAlarmTestUiState(
                    isTesting = false,
                    lastMessage = "Selecteer eerst een externe speaker"
                )
                return@launch
            }

            _backupAlarmTestState.value = BackupAlarmTestUiState(isTesting = true, lastMessage = null)

            var haOk = true
            if (testHaSpeaker && testPhoneSpeaker) {
                coroutineScope {
                    val haDeferred = async {
                        com.dd.daykit.WeatherAlertWorker.testWeatherSpeechOnHaSpeaker(
                            context.applicationContext, speakerEntityId!!, volume, skipVolume
                        )
                    }
                    val phoneDeferred = async {
                        com.dd.daykit.WeatherAlertWorker.testWeatherSpeechOnPhone(context.applicationContext)
                    }
                    haOk = haDeferred.await()
                    phoneDeferred.await()
                }
            } else if (testHaSpeaker) {
                haOk = com.dd.daykit.WeatherAlertWorker.testWeatherSpeechOnHaSpeaker(
                    context.applicationContext, speakerEntityId!!, volume, skipVolume
                )
                // Zelfde fallback als een echte weermelding op modus "Standaard": lukt de
                // HA-speaker niet, dan alsnog via de telefoon - zie speakWeatherAlertIfEnabled.
                if (!haOk) {
                    com.dd.daykit.WeatherAlertWorker.testWeatherSpeechOnPhone(context.applicationContext)
                }
            } else {
                com.dd.daykit.WeatherAlertWorker.testWeatherSpeechOnPhone(context.applicationContext)
            }

            val message = when {
                testHaSpeaker && !haOk && testPhoneSpeaker ->
                    "HA-speaker niet bereikt, telefoon-test is wel uitgevoerd - check de HA-speaker."
                testHaSpeaker && !haOk ->
                    "HA-speaker niet bereikt, teruggevallen op telefoon (zoals bij een echte melding) - check de HA-speaker."
                else ->
                    "Test uitgevoerd - hoor je 'm niet, check het volume of de tts-engine/HA-speaker."
            }
            _backupAlarmTestState.value = BackupAlarmTestUiState(isTesting = false, lastMessage = message)
        }
    }
}

class HaSettingsViewModelFactory(
    private val settingsStorage: HomeAssistantSettingsStorage,
    private val repository: HomeAssistantRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HaSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HaSettingsViewModel(settingsStorage, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

fun HaEntity.isPresenceType(): Boolean {
    return this.entityId.substringBefore('.') in listOf("binary_sensor", "person", "device_tracker", "input_boolean", "zone")
}

/** Tekstvorm van een speakermodus zoals de plak-parser 'm verwacht. */
private fun ExternalSpeakerMode.toExportString(): String = when (this) {
    ExternalSpeakerMode.DISABLED -> "Off"
    ExternalSpeakerMode.DEFAULT -> "Standard"
    ExternalSpeakerMode.BACKUP_ONLY -> "Backup"
    ExternalSpeakerMode.BOTH -> "Both"
}

/**
 * Generate export text for all Home Assistant settings
 * Format is compatible with the paste/import parser
 *
 * [speakerContext] bepaalt welke speaker onder de ONGEPREFIXTE sleutels (`Speaker:`,
 * `SpeakerModus:`, `Volume:`, `BackupAlarm:`) terechtkomt - dat is het scherm van waaruit
 * geëxporteerd wordt (Agenda-alarm/Timer via HaSettingsActivity, Weer via WeatherActivity). Die
 * sleutels blijven bestaan zodat een export die in een oudere app-versie geplakt wordt nog steeds
 * werkt.
 *
 * Daarnaast staan sinds deze versie ALLE Home Assistant-instellingen in de export, niet alleen die
 * van het scherm waar je toevallig vandaan komt: alle drie de speakers apart
 * (`SpeakerAlarm*`/`SpeakerTimer*`/`SpeakerWeer*`), de weeralarm-uitspreken-schakelaar, de
 * uit-bed-check ook als er (nog) geen entiteit bij hoort, of de scripts aan staan los van hun
 * entiteit, het backup-alarm-interval, de actieve URL, "toon alleen gekoppelde entiteiten" en de
 * twee HA-only Defaults-velden. Alles wat hier ontbrak ging bij exporteren+plakken stilzwijgend
 * verloren - precies wat er met "Uitspreken" gebeurde.
 */
fun generateExportText(
    uiState: HaSettingsUiState,
    batteryUsage: Int,
    speakerContext: SpeakerContext
): String {
    val speaker = when (speakerContext) {
        SpeakerContext.ALARM -> uiState.alarmSpeaker
        SpeakerContext.TIMER -> uiState.timerSpeaker
        SpeakerContext.WEATHER -> uiState.weatherSpeaker
    }
    val sb = StringBuilder()

    fun soundNameFor(soundId: Long?): String? =
        soundId?.let { id -> uiState.availableLocalSounds.find { it.id == id }?.name }

    // URLs
    val urls = uiState.baseUrls.filter { it.isNotBlank() }
    if (urls.isNotEmpty()) {
        sb.appendLine("URL:")
        urls.forEach { sb.appendLine(it) }
        sb.appendLine()
    }

    // Welke van die URL's op dit moment de actieve verbinding is. Zonder deze regel koos een
    // import zelf maar iets uit de lijst.
    if (!uiState.activeBaseUrl.isNullOrBlank()) {
        sb.appendLine("ActieveURL: ${uiState.activeBaseUrl}")
        sb.appendLine()
    }

    // Token
    if (uiState.longLivedToken.isNotBlank()) {
        sb.appendLine("Token:")
        sb.appendLine(uiState.longLivedToken)
        sb.appendLine()
    }

    // Entities
    val entities = uiState.entities.filter { it.isNotBlank() }
    if (entities.isNotEmpty()) {
        sb.appendLine("Entities:")
        entities.forEach { sb.appendLine(it) }
        sb.appendLine()
    }

    sb.appendLine("ToonAlleenGekoppeld: ${uiState.showOnlyLinkedEntities}")

    // Speaker van het huidige scherm, onder de ongeprefixte sleutels (oude formaat).
    if (!speaker.entityId.isNullOrBlank()) {
        sb.appendLine("Speaker: ${speaker.entityId}")
    }
    sb.appendLine("SpeakerModus: ${speaker.mode.toExportString()}")

    // Battery usage
    sb.appendLine("Battery: $batteryUsage")

    // Volume
    sb.appendLine("Volume: ${if (speaker.skipVolume) "onveranderd gelaten" else speaker.volume}")

    // Backup alarm sound
    soundNameFor(speaker.soundId)?.let { sb.appendLine("BackupAlarm: $it") }

    // Hoelang HA het backup-alarm herhaalt. Stond wel in de plak-parser (Minuten/Seconden) maar
    // werd nooit geëxporteerd, dus viel deze waarde bij elke export+plak terug op de standaard.
    sb.appendLine("Minuten: ${uiState.backupAlarmDuration / 60}")
    sb.appendLine("Seconden: ${uiState.backupAlarmDuration % 60}")
    sb.appendLine()

    // Alle drie de speakers apart, zodat een export vanuit één scherm de andere twee niet meer
    // laat verdwijnen.
    sb.appendLine("SpeakerAlarm: ${uiState.alarmSpeaker.entityId.orEmpty()}")
    sb.appendLine("SpeakerAlarmModus: ${uiState.alarmSpeaker.mode.toExportString()}")
    sb.appendLine("SpeakerAlarmVolume: ${if (uiState.alarmSpeaker.skipVolume) "onveranderd gelaten" else uiState.alarmSpeaker.volume}")
    soundNameFor(uiState.alarmSpeaker.soundId)?.let { sb.appendLine("SpeakerAlarmGeluid: $it") }

    sb.appendLine("SpeakerTimer: ${uiState.timerSpeaker.entityId.orEmpty()}")
    sb.appendLine("SpeakerTimerModus: ${uiState.timerSpeaker.mode.toExportString()}")
    sb.appendLine("SpeakerTimerVolume: ${if (uiState.timerSpeaker.skipVolume) "onveranderd gelaten" else uiState.timerSpeaker.volume}")
    soundNameFor(uiState.timerSpeaker.soundId)?.let { sb.appendLine("SpeakerTimerGeluid: $it") }

    sb.appendLine("SpeakerWeer: ${uiState.weatherSpeaker.entityId.orEmpty()}")
    sb.appendLine("SpeakerWeerModus: ${uiState.weatherSpeaker.mode.toExportString()}")
    sb.appendLine("SpeakerWeerVolume: ${if (uiState.weatherSpeaker.skipVolume) "onveranderd gelaten" else uiState.weatherSpeaker.volume}")
    soundNameFor(uiState.weatherSpeaker.soundId)?.let { sb.appendLine("SpeakerWeerGeluid: $it") }
    sb.appendLine()

    // Weeralarmen laten uitspreken op de weer-speaker. Dit is de instelling die helemaal niet in
    // de export zat.
    sb.appendLine("Uitspreken: ${uiState.weatherTtsEnabled}")
    sb.appendLine()

    // AtHome (presence)
    if (!uiState.selectedPresenceEntityId.isNullOrBlank()) {
        sb.appendLine("AtHome:")
        sb.appendLine("${uiState.selectedPresenceEntityId}:${uiState.presenceExpectedState}")
        sb.appendLine()
    }

    // InBed (out of bed check). De aan/uit-schakelaar staat er nu altijd bij: stond de check aan
    // zonder gekozen entiteit, dan kwam dat feit vroeger niet in de export terecht.
    sb.appendLine("InBedCheck: ${uiState.outOfBedCheckEnabled}")
    if (!uiState.outOfBedEntityId.isNullOrBlank()) {
        sb.appendLine("InBed:")
        sb.appendLine("${uiState.outOfBedEntityId}:${uiState.outOfBedExpectedValue}")
    }
    sb.appendLine()

    // Script bij alarm (los van speaker-instellingen). Aan/uit apart van de entiteit, zodat een
    // ingestelde-maar-uitgeschakelde koppeling niet stilzwijgend aan of weg gaat.
    sb.appendLine("ScriptAlarmAan: ${uiState.alarmScriptEnabled}")
    if (!uiState.alarmScriptEntityId.isNullOrBlank()) {
        sb.appendLine("ScriptAlarm: ${uiState.alarmScriptEntityId}")
    }
    sb.appendLine("ScriptAlarmAltijdUit: ${uiState.alarmScriptIgnorePresence}")

    // Script bij timer (los van speaker-instellingen en van script bij alarm)
    sb.appendLine("ScriptTimerAan: ${uiState.timerScriptEnabled}")
    if (!uiState.timerScriptEntityId.isNullOrBlank()) {
        sb.appendLine("ScriptTimer: ${uiState.timerScriptEntityId}")
    }
    sb.appendLine("ScriptTimerAltijdUit: ${uiState.timerScriptIgnorePresence}")

    // HA-only Defaults-velden: alleen meenemen als de app ze kent (null = nooit ingesteld). Ze
    // horen bij de gedeelde configuratie, dus een export zonder deze twee zette ze bij plakken op
    // een ander toestel terug op "onbekend".
    uiState.notifyService?.takeIf { it.isNotBlank() }?.let { sb.appendLine("NotifyService: $it") }
    uiState.safetyTimeoutSeconds?.let { sb.appendLine("SafetyTimeout: $it") }

    return sb.toString().trim()
}
