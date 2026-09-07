package com.dd.daykit.rules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dd.daykit.SettingsManager
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class TriggerBehaviorUiState(
    val triggerId: String = "",
    val triggerName: String = "",
    val selectedMode: TriggerBehaviorMode = TriggerBehaviorMode.NORMAL,
    val smartConfig: SmartAlarmConfig? = null,
    val availableEntities: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isFirstTimeSetup: Boolean = false,
    val entityState: String? = null, // Huidige state van geselecteerde entiteit
    val entityOptions: List<String>? = null, // Voor input_select etc.
    val isLoadingEntityState: Boolean = false,
    // Nieuwe per-trigger instellingen
    val alarmSoundUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val alarmVolume: Int = 100,
    // Snooze instellingen
    val snoozeCount: Int = 3,
    val unlimitedSnooze: Boolean = true,
    // DD Music koppeling: alleen aan/uit. De keuze wát er afspeelt gebeurt in DD Music zelf.
    val ddMusicLinked: Boolean = false,
    // Leesbare samenvatting van de DD Music-keuze (songtitel/playlistnaam/etc), null tot DD
    // Music 'm teruggestuurd heeft via broadcast.
    val ddMusicSummary: String? = null,
    // URL waarop DD Music het gekozen nummer serveert (alleen bij mode "song"). Puur bewaard
    // zodat saveConfig() 'm niet overschrijft met null - de UI toont dit veld nergens.
    val ddMusicPlayUrl: String? = null
)

class TriggerBehaviorViewModel(
    private val context: Context,
    private val triggerId: String,
    private val triggerName: String,
    private val storage: TriggerBehaviorStorage,
    private val haSettingsStorage: HomeAssistantSettingsStorage,
    private val haRepository: HomeAssistantRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TriggerBehaviorUiState(
        triggerId = triggerId,
        triggerName = triggerName
    ))
    val uiState: StateFlow<TriggerBehaviorUiState> = _uiState.asStateFlow()

    // True zodra de gebruiker in DEZE sessie zelf de DD Music-koppeling heeft gewijzigd
    // (updateAlarmSound: een normaal geluid kiezen ontkoppelt DD Music expliciet). saveConfig()
    // gebruikt dit om te weten of de ddMusic*-velden uit uiState leidend zijn (bewuste lokale
    // wijziging) of dat er eerst nog vers vanaf schijf gelezen moet worden (zie saveConfig).
    private var ddMusicLocallyModified = false

    init {
        loadConfig()
    }
    
    private fun loadConfig() {
        viewModelScope.launch {
            val config = storage.getConfig(triggerId)
            
            // Check of dit de eerste keer is (NORMAL mode zonder dat er ooit iets is opgeslagen)
            val isFirstTime = config.mode == TriggerBehaviorMode.NORMAL && 
                              config.smartConfig == null && 
                              !config.alreadyFired &&
                              config.alarmSoundUri == null // Aanname: als geluid null is, is het nog niet ingesteld
            
            // Haal defaults op uit SettingsManager als ze niet in de config staan
            val candidateSoundUri = config.alarmSoundUri ?: SettingsManager.getAlarmSoundUri(context)
            val soundUri = SettingsManager.resolvePlayableAlarmSoundUri(context, candidateSoundUri).toString()
            val vibrate = config.vibrate ?: SettingsManager.getVibrate(context)
            val snooze = config.snoozeMinutes ?: SettingsManager.getSnoozeMinutes(context)
            val volume = config.alarmVolume ?: SettingsManager.getAlarmVolume(context)
            
            _uiState.value = _uiState.value.copy(
                selectedMode = config.mode,
                smartConfig = config.smartConfig,
                isLoading = false,
                isFirstTimeSetup = isFirstTime,
                alarmSoundUri = soundUri,
                vibrate = vibrate,
                snoozeMinutes = snooze,
                alarmVolume = volume,
                snoozeCount = config.snoozeCount ?: 3,
                unlimitedSnooze = config.unlimitedSnooze,
                ddMusicLinked = config.ddMusicLinked,
                ddMusicSummary = config.ddMusicSummary,
                ddMusicPlayUrl = config.ddMusicPlayUrl
            )
            if (config.mode == TriggerBehaviorMode.SMART_ALARM) {
                loadAvailableEntities()
            }
        }
    }
    
    private fun loadAvailableEntities() {
        viewModelScope.launch {
            val settings = haSettingsStorage.settingsFlow.firstOrNull()
            _uiState.value = _uiState.value.copy(
                availableEntities = settings?.entities ?: emptyList()
            )
        }
    }
    
    /**
     * Selecteer een modus
     */
    fun selectMode(mode: TriggerBehaviorMode) {
        _uiState.value = _uiState.value.copy(
            selectedMode = mode,
            isFirstTimeSetup = false
        )
        if (mode == TriggerBehaviorMode.SMART_ALARM) {
            loadAvailableEntities()
        }
    }
    
    // Updates voor nieuwe instellingen
    fun updateAlarmSound(uri: String?) {
        // Een normaal geluid kiezen betekent impliciet: niet meer DD Music gebruiken voor dit alarm.
        ddMusicLocallyModified = true
        _uiState.value = _uiState.value.copy(
            alarmSoundUri = uri,
            ddMusicLinked = false,
            ddMusicSummary = null,
            ddMusicPlayUrl = null
        )
    }
    
    fun updateVibrate(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(vibrate = enabled)
    }
    
    fun updateSnoozeMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(snoozeMinutes = minutes.coerceIn(0, 30))
    }
    
    fun updateAlarmVolume(volume: Int) {
        _uiState.value = _uiState.value.copy(alarmVolume = volume)
    }
    
    fun updateSnoozeCount(count: Int) {
        _uiState.value = _uiState.value.copy(snoozeCount = count.coerceIn(0, 20))
    }

    fun updateUnlimitedSnooze(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(unlimitedSnooze = enabled)
    }

    /**
     * Markeert dit alarm meteen als DD Music-gekoppeld (optimistisch, vóór DD Music de echte
     * keuze teruggestuurd heeft) - aangeroepen zodra de gebruiker op "Gebruik DD Music Als
     * Alarm" tikt in het alarmgeluid-scherm.
     */
    fun markDdMusicLinked() {
        _uiState.value = _uiState.value.copy(ddMusicLinked = true)
    }

    /** Ververst de samenvatting/koppeling vanaf schijf, bv. na terugkeer uit DD Music. */
    fun reloadDdMusicState() {
        viewModelScope.launch {
            val config = storage.getConfig(triggerId)
            _uiState.value = _uiState.value.copy(
                ddMusicLinked = config.ddMusicLinked,
                ddMusicSummary = config.ddMusicSummary,
                ddMusicPlayUrl = config.ddMusicPlayUrl
            )
            // We staan weer synchroon met schijf - een eventuele oudere lokale ontkoppeling
            // (normaal geluid gekozen vóór dit resume) is hiermee ingehaald door de nieuwste
            // stand vanaf schijf.
            ddMusicLocallyModified = false
        }
    }
    
    /**
     * Update Smart Alarm configuratie
     */
    fun updateSmartConfig(
        entityId: String, 
        expectedValue: String, 
        preventDismiss: Boolean,
        checkUserAtHome: Boolean = false,
        userPresenceEntityId: String = "",
        userPresenceExpectedValue: String = "on"
    ) {
        _uiState.value = _uiState.value.copy(
            smartConfig = SmartAlarmConfig(entityId, expectedValue, preventDismiss, checkUserAtHome, userPresenceEntityId, userPresenceExpectedValue)
        )
        
        // Als entityId is ingevuld, haal de state op
        if (entityId.isNotBlank()) {
            loadEntityState(entityId)
        }
    }
    
    /**
     * Laad de huidige state van een entiteit
     */
    fun loadEntityState(entityId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingEntityState = true)
            
            try {
                val response = haRepository.getEntityState(entityId)
                val state = response.state
                
                // Probeer options te extraheren voor input_select
                val options = try {
                    response.attributes?.get("options")?.toString()?.let { optionsStr ->
                        // Simpele parsing van ["option1", "option2"] naar List
                        if (optionsStr.startsWith("[") && optionsStr.endsWith("]")) {
                            optionsStr.substring(1, optionsStr.length - 1)
                                .split(",")
                                .map { it.trim().removeSurrounding("\"") }
                                .filter { it.isNotBlank() }
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
                
                _uiState.value = _uiState.value.copy(
                    entityState = state,
                    entityOptions = options,
                    isLoadingEntityState = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    entityState = null,
                    entityOptions = null,
                    isLoadingEntityState = false
                )
            }
        }
    }
    
    /**
     * Sla de configuratie op
     */
    suspend fun saveConfig() {
        _uiState.value = _uiState.value.copy(isSaving = true)

        // Valideer SmartAlarmConfig als SMART_ALARM mode geselecteerd is
        val validatedSmartConfig = if (_uiState.value.selectedMode == TriggerBehaviorMode.SMART_ALARM) {
            val smartConfig = _uiState.value.smartConfig
            if (smartConfig != null &&
                smartConfig.entityId.isNotBlank() &&
                smartConfig.expectedValue.isNotBlank()) {
                smartConfig
            } else {
                null // Onvolledige config wordt niet opgeslagen
            }
        } else {
            null
        }

        // DD Music-velden: als de gebruiker ze niet zelf lokaal gewijzigd heeft (via
        // updateAlarmSound -> een normaal geluid kiezen), lees ze vlak vóór het opslaan nog
        // eens vers vanaf schijf i.p.v. de mogelijk verouderde uiState-snapshot te gebruiken.
        // Zonder dit kon een DD Music-keuze die net (via de broadcast van DdMusicLinkUpdateReceiver)
        // op schijf gezet was, alsnog overschreven worden door deze volledige-config-save als
        // reloadDdMusicState()'s achtergrond-reload nog niet was aangekomen toen op "Opslaan"
        // getikt werd - de koppeling "bleef dan niet staan".
        val ddMusicLinked: Boolean
        val ddMusicSummary: String?
        val ddMusicPlayUrl: String?
        if (ddMusicLocallyModified) {
            ddMusicLinked = _uiState.value.ddMusicLinked
            ddMusicSummary = _uiState.value.ddMusicSummary
            ddMusicPlayUrl = _uiState.value.ddMusicPlayUrl
        } else {
            val freshConfig = storage.getConfig(triggerId)
            ddMusicLinked = freshConfig.ddMusicLinked
            ddMusicSummary = freshConfig.ddMusicSummary
            ddMusicPlayUrl = freshConfig.ddMusicPlayUrl
        }

        val config = TriggerRulesConfig(
            triggerId = triggerId,
            mode = _uiState.value.selectedMode,
            smartConfig = validatedSmartConfig,
            alreadyFired = false, // Reset bij opslaan
            alarmSoundUri = _uiState.value.alarmSoundUri,
            vibrate = _uiState.value.vibrate,
            snoozeMinutes = _uiState.value.snoozeMinutes,
            alarmVolume = _uiState.value.alarmVolume,
            snoozeCount = _uiState.value.snoozeCount,
            unlimitedSnooze = _uiState.value.unlimitedSnooze,
            ddMusicLinked = ddMusicLinked,
            ddMusicSummary = ddMusicSummary,
            ddMusicPlayUrl = ddMusicPlayUrl
        )

        storage.saveConfig(config)
        ddMusicLocallyModified = false

        _uiState.value = _uiState.value.copy(
            isSaving = false,
            ddMusicLinked = ddMusicLinked,
            ddMusicSummary = ddMusicSummary,
            ddMusicPlayUrl = ddMusicPlayUrl
        )
    }
    
    /**
     * Reset de "already fired" status voor ONE_TIME modus
     */
    fun resetOneTimeAlarm() {
        viewModelScope.launch {
            storage.resetFiredStatus(triggerId)
        }
    }
    
    /**
     * Test een entiteit (haal huidige state op)
     */
    suspend fun testEntity(entityId: String): String? {
        return try {
            val response = haRepository.getEntityState(entityId)
            response.state
        } catch (e: Exception) {
            null
        }
    }
}

class TriggerBehaviorViewModelFactory(
    private val context: Context,
    private val triggerId: String,
    private val triggerName: String,
    private val storage: TriggerBehaviorStorage,
    private val haSettingsStorage: HomeAssistantSettingsStorage,
    private val haRepository: HomeAssistantRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TriggerBehaviorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TriggerBehaviorViewModel(context, triggerId, triggerName, storage, haSettingsStorage, haRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
