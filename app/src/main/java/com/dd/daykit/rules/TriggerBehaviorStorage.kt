package com.dd.daykit.rules

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.triggerBehaviorDataStore: DataStore<Preferences> by preferencesDataStore(name = "trigger_behavior")

/**
 * Storage voor TriggerRulesConfig per trigger ID
 */
class TriggerBehaviorStorage(private val context: Context) {
    
    private fun getModeKey(triggerId: String) = stringPreferencesKey("mode_$triggerId")
    private fun getEntityIdKey(triggerId: String) = stringPreferencesKey("entity_$triggerId")
    private fun getExpectedValueKey(triggerId: String) = stringPreferencesKey("value_$triggerId")
    private fun getPreventDismissKey(triggerId: String) = booleanPreferencesKey("prevent_dismiss_$triggerId")
    private fun getAlreadyFiredKey(triggerId: String) = booleanPreferencesKey("already_fired_$triggerId")
    
    // Nieuwe keys voor per-trigger instellingen
    private fun getAlarmSoundKey(triggerId: String) = stringPreferencesKey("sound_$triggerId")
    private fun getVibrateKey(triggerId: String) = booleanPreferencesKey("vibrate_$triggerId")
    private fun getSnoozeKey(triggerId: String) = intPreferencesKey("snooze_$triggerId")
    private fun getVolumeKey(triggerId: String) = intPreferencesKey("volume_$triggerId")
    
    // Keys voor snooze limiet
    private fun getSnoozeCountKey(triggerId: String) = intPreferencesKey("snooze_count_$triggerId")
    private fun getUnlimitedSnoozeKey(triggerId: String) = booleanPreferencesKey("snooze_unlimited_$triggerId")

    // Keys voor DD Music koppeling
    private fun getDdMusicLinkedKey(triggerId: String) = booleanPreferencesKey("dd_music_linked_$triggerId")
    private fun getDdMusicSummaryKey(triggerId: String) = stringPreferencesKey("dd_music_summary_$triggerId")
    private fun getDdMusicPlayUrlKey(triggerId: String) = stringPreferencesKey("dd_music_play_url_$triggerId")

    // Keys voor aanwezigheidscheck (Smart Alarm)
    private fun getCheckUserAtHomeKey(triggerId: String) = booleanPreferencesKey("check_user_at_home_$triggerId")
    private fun getUserPresenceEntityIdKey(triggerId: String) = stringPreferencesKey("user_presence_entity_$triggerId")
    private fun getUserPresenceExpectedValueKey(triggerId: String) = stringPreferencesKey("user_presence_expected_value_$triggerId")
    
    /**
     * Defensieve lezers. DataStore's `Preferences[key]` doet een ongecontroleerde cast op de naam
     * van de key — staat er een waarde van het verkeerde type onder die naam, dan knalt er pas
     * later een ClassCastException uit. Dat is geen theorie: een restore die het type niet mee
     * terugkreeg schreef `dd_music_linked_<id>` ooit als String weg, waarna élke lezer van deze
     * config omviel — de agenda-alarmplanning gaf stilletjes `null` terug ("Geen alarm") en het
     * trigger-optiescherm ging niet meer open. De backup-kant is inmiddels type-veilig
     * (zie BackupManager), maar een enkel scheefstaand veld mag nooit meer een hele trigger
     * onbruikbaar maken: hieronder lezen we daarom rauw uit de map en converteren waar mogelijk.
     */
    private fun Preferences.rawValue(key: Preferences.Key<*>): Any? = asMap()[key]

    private fun Preferences.booleanOrNull(key: Preferences.Key<Boolean>): Boolean? =
        when (val raw = rawValue(key)) {
            null -> null
            is Boolean -> raw
            is String -> raw.toBooleanStrictOrNull()
            is Number -> raw.toInt() != 0
            else -> null
        }

    private fun Preferences.intOrNull(key: Preferences.Key<Int>): Int? =
        when (val raw = rawValue(key)) {
            null -> null
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            is Boolean -> if (raw) 1 else 0
            else -> null
        }

    private fun Preferences.stringOrNull(key: Preferences.Key<String>): String? =
        when (val raw = rawValue(key)) {
            null -> null
            is String -> raw
            is Set<*> -> raw.firstOrNull()?.toString()
            else -> raw.toString()
        }

    private fun buildConfigFromPreferences(preferences: Preferences, triggerId: String): TriggerRulesConfig {
        val modeString = preferences.stringOrNull(getModeKey(triggerId)) ?: TriggerBehaviorMode.NORMAL.name
        val mode = try {
            TriggerBehaviorMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            TriggerBehaviorMode.NORMAL
        }

        val smartConfig = if (mode == TriggerBehaviorMode.SMART_ALARM) {
            val entityId = preferences.stringOrNull(getEntityIdKey(triggerId)) ?: ""
            val expectedValue = preferences.stringOrNull(getExpectedValueKey(triggerId)) ?: ""
            val preventDismiss = preferences.booleanOrNull(getPreventDismissKey(triggerId)) ?: false
            val checkUserAtHome = preferences.booleanOrNull(getCheckUserAtHomeKey(triggerId)) ?: false
            val userPresenceEntityId = preferences.stringOrNull(getUserPresenceEntityIdKey(triggerId)) ?: ""
            val userPresenceExpectedValue = preferences.stringOrNull(getUserPresenceExpectedValueKey(triggerId)) ?: "on"

            // De smart-conditie (entiteit + verwachte waarde) is optioneel; de verwachte waarde
            // wordt in de UI niet altijd ingevuld. Vroeger werd bij een lege waarde de héle
            // smartConfig weggegooid, waardoor óók de aanwezigheidscheck (checkUserAtHome) stil
            // verdween — die kon daardoor nooit werken. Bewaar de config zodra er iets in staat;
            // de aanroepers controleren zelf per onderdeel of het bruikbaar is
            // (zie RuleEngine STAP 2 en STAP 4).
            if (entityId.isNotBlank() || checkUserAtHome || userPresenceEntityId.isNotBlank()) {
                SmartAlarmConfig(entityId, expectedValue, preventDismiss, checkUserAtHome, userPresenceEntityId, userPresenceExpectedValue)
            } else {
                null
            }
        } else {
            null
        }

        val alreadyFired = preferences.booleanOrNull(getAlreadyFiredKey(triggerId)) ?: false

        val alarmSound = preferences.stringOrNull(getAlarmSoundKey(triggerId))
        val vibrate = preferences.booleanOrNull(getVibrateKey(triggerId))
        val snooze = preferences.intOrNull(getSnoozeKey(triggerId))?.coerceAtLeast(0)
        val volume = preferences.intOrNull(getVolumeKey(triggerId))

        val snoozeCount = (preferences.intOrNull(getSnoozeCountKey(triggerId)) ?: 3).coerceAtLeast(0)
        val unlimitedSnooze = preferences.booleanOrNull(getUnlimitedSnoozeKey(triggerId)) ?: true

        val ddMusicLinked = preferences.booleanOrNull(getDdMusicLinkedKey(triggerId)) ?: false
        val ddMusicSummary = preferences.stringOrNull(getDdMusicSummaryKey(triggerId))
        val ddMusicPlayUrl = preferences.stringOrNull(getDdMusicPlayUrlKey(triggerId))

        return TriggerRulesConfig(
            triggerId = triggerId,
            mode = mode,
            smartConfig = smartConfig,
            alreadyFired = alreadyFired,
            alarmSoundUri = alarmSound,
            vibrate = vibrate,
            snoozeMinutes = snooze,
            alarmVolume = volume,
            snoozeCount = snoozeCount,
            unlimitedSnooze = unlimitedSnooze,
            ddMusicLinked = ddMusicLinked,
            ddMusicSummary = ddMusicSummary,
            ddMusicPlayUrl = ddMusicPlayUrl
        )
    }

    /**
     * Oude modus ONE_TIME (verwijderd uit UI) → simpel alarm met beperkt sluimeren.
     */
    private fun migrateOneTimeToNormal(config: TriggerRulesConfig): TriggerRulesConfig {
        return TriggerRulesConfig(
            triggerId = config.triggerId,
            mode = TriggerBehaviorMode.NORMAL,
            smartConfig = null,
            alreadyFired = false,
            alarmSoundUri = config.alarmSoundUri,
            vibrate = config.vibrate,
            snoozeMinutes = config.snoozeMinutes,
            alarmVolume = config.alarmVolume,
            snoozeCount = 1,
            unlimitedSnooze = false,
            ddMusicLinked = config.ddMusicLinked,
            ddMusicSummary = config.ddMusicSummary,
            ddMusicPlayUrl = config.ddMusicPlayUrl
        )
    }

    /**
     * Haal configuratie op voor een trigger
     */
    suspend fun getConfig(triggerId: String): TriggerRulesConfig {
        val preferences = context.triggerBehaviorDataStore.data.first()
        val config = buildConfigFromPreferences(preferences, triggerId)
        if (config.mode != TriggerBehaviorMode.ONE_TIME) return config
        val migrated = migrateOneTimeToNormal(config)
        saveConfig(migrated)
        return migrated
    }
    
    /**
     * Flow voor configuratie van een trigger
     */
    fun getConfigFlow(triggerId: String): Flow<TriggerRulesConfig> {
        return context.triggerBehaviorDataStore.data.map { preferences ->
            val config = buildConfigFromPreferences(preferences, triggerId)
            if (config.mode == TriggerBehaviorMode.ONE_TIME) migrateOneTimeToNormal(config) else config
        }
    }
    
    /**
     * Sla configuratie op voor een trigger
     */
    suspend fun saveConfig(config: TriggerRulesConfig) {
        context.triggerBehaviorDataStore.edit { preferences ->
            preferences[getModeKey(config.triggerId)] = config.mode.name
            
            // Verwijder oude smart config data
            preferences.remove(getEntityIdKey(config.triggerId))
            preferences.remove(getExpectedValueKey(config.triggerId))
            preferences.remove(getPreventDismissKey(config.triggerId))
            preferences.remove(getCheckUserAtHomeKey(config.triggerId))
            preferences.remove(getUserPresenceEntityIdKey(config.triggerId))
            preferences.remove(getUserPresenceExpectedValueKey(config.triggerId))
            
            // Sla nieuwe smart config op indien aanwezig
            config.smartConfig?.let { smartConfig ->
                preferences[getEntityIdKey(config.triggerId)] = smartConfig.entityId
                preferences[getExpectedValueKey(config.triggerId)] = smartConfig.expectedValue
                preferences[getPreventDismissKey(config.triggerId)] = smartConfig.preventManualDismiss
                preferences[getCheckUserAtHomeKey(config.triggerId)] = smartConfig.checkUserAtHome
                preferences[getUserPresenceEntityIdKey(config.triggerId)] = smartConfig.userPresenceEntityId
                preferences[getUserPresenceExpectedValueKey(config.triggerId)] = smartConfig.userPresenceExpectedValue
            }
            
            preferences[getAlreadyFiredKey(config.triggerId)] = config.alreadyFired
            
            // Sla nieuwe instellingen op
            if (config.alarmSoundUri != null) {
                preferences[getAlarmSoundKey(config.triggerId)] = config.alarmSoundUri
            } else {
                preferences.remove(getAlarmSoundKey(config.triggerId))
            }
            
            if (config.vibrate != null) {
                preferences[getVibrateKey(config.triggerId)] = config.vibrate
            } else {
                preferences.remove(getVibrateKey(config.triggerId))
            }
            
            if (config.snoozeMinutes != null) {
                preferences[getSnoozeKey(config.triggerId)] = config.snoozeMinutes.coerceAtLeast(0)
            } else {
                preferences.remove(getSnoozeKey(config.triggerId))
            }
            
            if (config.alarmVolume != null) {
                preferences[getVolumeKey(config.triggerId)] = config.alarmVolume
            } else {
                preferences.remove(getVolumeKey(config.triggerId))
            }
            
            // Sla snooze limiet op
            if (config.snoozeCount != null) {
                preferences[getSnoozeCountKey(config.triggerId)] = config.snoozeCount.coerceAtLeast(0)
            } else {
                preferences[getSnoozeCountKey(config.triggerId)] = 3
            }
            preferences[getUnlimitedSnoozeKey(config.triggerId)] = config.unlimitedSnooze

            // Sla DD Music koppeling op
            preferences[getDdMusicLinkedKey(config.triggerId)] = config.ddMusicLinked
            if (config.ddMusicSummary != null) {
                preferences[getDdMusicSummaryKey(config.triggerId)] = config.ddMusicSummary
            } else {
                preferences.remove(getDdMusicSummaryKey(config.triggerId))
            }
            if (config.ddMusicPlayUrl != null) {
                preferences[getDdMusicPlayUrlKey(config.triggerId)] = config.ddMusicPlayUrl
            } else {
                preferences.remove(getDdMusicPlayUrlKey(config.triggerId))
            }
        }
    }
    
    /**
     * Markeer een trigger als afgevuurd (voor ONE_TIME modus)
     */
    suspend fun markAsFired(triggerId: String) {
        context.triggerBehaviorDataStore.edit { preferences ->
            preferences[getAlreadyFiredKey(triggerId)] = true
        }
    }
    
    /**
     * Reset de "already fired" status (zodat ONE_TIME alarm opnieuw kan afgaan)
     */
    suspend fun resetFiredStatus(triggerId: String) {
        context.triggerBehaviorDataStore.edit { preferences ->
            preferences[getAlreadyFiredKey(triggerId)] = false
        }
    }
    
    /**
     * Alias voor resetFiredStatus - reset alreadyFired flag
     */
    suspend fun resetAlreadyFired(triggerId: String) = resetFiredStatus(triggerId)
    
    /**
     * Verwijder alle configuratie voor een trigger
     */
    suspend fun deleteConfig(triggerId: String) {
        context.triggerBehaviorDataStore.edit { preferences ->
            preferences.remove(getModeKey(triggerId))
            preferences.remove(getEntityIdKey(triggerId))
            preferences.remove(getExpectedValueKey(triggerId))
            preferences.remove(getPreventDismissKey(triggerId))
            preferences.remove(getAlreadyFiredKey(triggerId))
            preferences.remove(getCheckUserAtHomeKey(triggerId))
            preferences.remove(getUserPresenceEntityIdKey(triggerId))
            preferences.remove(getUserPresenceExpectedValueKey(triggerId))
            
            preferences.remove(getAlarmSoundKey(triggerId))
            preferences.remove(getVibrateKey(triggerId))
            preferences.remove(getSnoozeKey(triggerId))
            preferences.remove(getVolumeKey(triggerId))
            
            preferences.remove(getSnoozeCountKey(triggerId))
            preferences.remove(getUnlimitedSnoozeKey(triggerId))

            preferences.remove(getDdMusicLinkedKey(triggerId))
            preferences.remove(getDdMusicSummaryKey(triggerId))
            preferences.remove(getDdMusicPlayUrlKey(triggerId))
        }
    }
}
