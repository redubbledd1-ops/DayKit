package com.dd.daykit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "home_assistant_settings")

class HomeAssistantSettingsStorage(internal val context: Context) {

    private val SETTINGS_KEY = stringPreferencesKey("ha_settings_json")

    /**
     * Schaduwkopie van de laatste configuratie die met succes weggeschreven is. Puur een vangnet:
     * er wordt alleen uit gelezen als de hoofdsleutel onleesbaar of leeg blijkt.
     */
    private val LAST_GOOD_KEY = stringPreferencesKey("ha_settings_json_last_good")

    /**
     * De laatst verwerkte herkomststempel van HA's config (zie config_sync.py's
     * `config_last_modified`). Puur synchronisatie-bookkeeping, geen instelling.
     *
     * Bewust een eigen SharedPreferences-bestand: het staat daarmee los van de HA-instellingen in
     * DataStore, en het zit niet in het backupbestand (zie BackupManager's lijst met prefs) - na
     * een restore op een ander toestel wordt HA dus gewoon opnieuw gecontroleerd in plaats van dat
     * er een stempel van de oude telefoon wordt meegesleept.
     */
    private val syncStatePrefs by lazy {
        context.getSharedPreferences(HA_SYNC_STATE_PREFS, Context.MODE_PRIVATE)
    }

    fun getLastSeenHaConfigStamp(): Long = syncStatePrefs.getLong(LAST_SEEN_HA_STAMP_KEY, 0L)

    fun setLastSeenHaConfigStamp(stamp: Long) {
        syncStatePrefs.edit().putLong(LAST_SEEN_HA_STAMP_KEY, stamp).apply()
    }

    /**
     * Vingerafdruk van de HA-wijziging die de gebruiker als laatste beantwoord heeft (overgenomen
     * óf afgewezen).
     *
     * Dit staat los van [getLastSeenHaConfigStamp] en is er bewust naast blijven bestaan: die
     * stempel komt uit de HA-integratie, dus zolang die nog niet bijgewerkt is bestaat hij niet en
     * zou de app terugvallen op eindeloos opnieuw vragen. De app is leidend en moet een gemaakte
     * keuze zelfstandig kunnen onthouden, zonder van de HA-kant af te hangen. Verandert er daarna
     * écht iets (andere entiteit, andere waarde), dan wijkt de vingerafdruk af en wordt er weer
     * netjes één keer gevraagd.
     */
    fun getAnsweredHaDiffFingerprint(): String? =
        syncStatePrefs.getString(ANSWERED_HA_DIFF_KEY, null)

    fun setAnsweredHaDiffFingerprint(fingerprint: String) {
        syncStatePrefs.edit().putString(ANSWERED_HA_DIFF_KEY, fingerprint).apply()
    }

    companion object {
        private const val HA_SYNC_STATE_PREFS = "ha_sync_state"
        private const val LAST_SEEN_HA_STAMP_KEY = "last_seen_ha_config_stamp"
        private const val ANSWERED_HA_DIFF_KEY = "answered_ha_diff_fingerprint"
        private const val LAST_INTEGRITY_MARKER_KEY = "last_integrity_marker"
    }

    // ignoreUnknownKeys = true: cruciaal voor app-updates. Zonder dit gooit
    // decodeFromString een SerializationException zodra HomeAssistantSettings een veld
    // wint/verliest t.o.v. de vorige app-versie (gebeurt vaak), en werd die exception
    // hieronder stilletjes opgevangen door een LEGE HomeAssistantSettings() terug te
    // geven - dus alle HA-instellingen (speaker/presence/uit-bed/scripts/enz.) werden na
    // elke app-update gewist. Met ignoreUnknownKeys = true blijven bekende velden
    // gewoon overeind, ongeacht schema-wijzigingen tussen versies.
    private val json = Json { ignoreUnknownKeys = true }

    private fun decodeOrNull(raw: String?): HomeAssistantSettings? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString<HomeAssistantSettings>(raw)
        } catch (e: Exception) {
            Log.e("HaSettingsStorage", "Kon opgeslagen HA-settings niet lezen", e)
            null
        }
    }

    /**
     * Meldt een leesprobleem hooguit één keer per unieke situatie. Zonder deze rem zou elke
     * collector van [settingsFlow] (ViewModel, AlarmService, ExtraTimerManager, WeatherAlertWorker)
     * bij elke emissie opnieuw een melding tonen.
     */
    private fun reportOnce(marker: String, title: String, message: String) {
        try {
            if (syncStatePrefs.getString(LAST_INTEGRITY_MARKER_KEY, null) == marker) return
            syncStatePrefs.edit().putString(LAST_INTEGRITY_MARKER_KEY, marker).apply()
            com.dd.daykit.HaSettingsIntegrityNotifier.notify(context, title, message)
        } catch (e: Exception) {
            Log.e("HaSettingsStorage", "reportOnce failed", e)
        }
    }

    val settingsFlow: Flow<HomeAssistantSettings> = context.dataStore.data
        .map { preferences ->
            val stored = preferences[SETTINGS_KEY]
            val lastGood = preferences[LAST_GOOD_KEY]

            // Leeg of ontbrekend: verse installatie óf leeggeschreven door een restore die een
            // lege HA-config meebracht. In dat tweede geval staat de echte configuratie nog in de
            // schaduwkopie — teruggevallen op defaults zou hier stilzwijgend alles wissen.
            if (stored.isNullOrBlank()) {
                val recovered = decodeOrNull(lastGood)
                return@map if (recovered != null) {
                    reportOnce(
                        "empty_recovered",
                        "Home Assistant-instellingen hersteld",
                        "De opgeslagen HA-configuratie was leeg. De laatst werkende versie is " +
                            "teruggezet, inclusief de speakerinstellingen voor agenda-alarm, timer en weer."
                    )
                    recovered.migrateSpeakerSettingsIfNeeded()
                } else {
                    HomeAssistantSettings()
                }
            }

            val decoded = decodeOrNull(stored)
            if (decoded == null) {
                // Onleesbaar. Hier werd vroeger een lege HomeAssistantSettings() teruggegeven én
                // meteen opgeslagen, waardoor één mislukte lezing (gewijzigd veldtype na een
                // update, half weggeschreven bestand) de complete HA-configuratie definitief
                // verving door defaults — met de speaker op BOTH en zonder entiteit. Nooit meer
                // schrijven vanaf hier: de onleesbare tekst blijft staan zodat er niets verloren
                // gaat, en we lezen verder uit de schaduwkopie.
                val recovered = decodeOrNull(lastGood)
                reportOnce(
                    "decode_failed_${stored.length}_${stored.hashCode()}",
                    if (recovered != null) "Home Assistant-instellingen hersteld"
                    else "Home Assistant-instellingen onleesbaar",
                    if (recovered != null) {
                        "De opgeslagen configuratie kon niet gelezen worden. De laatst werkende " +
                            "versie is teruggezet — controleer de speakerinstellingen bij " +
                            "agenda-alarm, timer en weer."
                    } else {
                        "De opgeslagen configuratie kon niet gelezen worden en er is geen reservekopie. " +
                            "Er is niets overschreven, maar controleer je HA-instellingen."
                    }
                )
                return@map (recovered ?: HomeAssistantSettings()).migrateSpeakerSettingsIfNeeded()
            }

            // Eenmalige migratie: oude gedeelde externe-speaker-velden -> alarmSpeaker/
            // timerSpeaker/weatherSpeaker (zie HomeAssistantSettings.migrateSpeakerSettingsIfNeeded).
            // Alleen terugschrijven als de lezing gelukt is; anders zouden we een noodwaarde
            // vastleggen als ware het de echte configuratie.
            val migrated = decoded.migrateSpeakerSettingsIfNeeded()
            if (migrated != decoded) {
                saveSettings(migrated)
            }
            migrated
        }

    /**
     * Bewaart naast de actieve configuratie een schaduwkopie ("laatst werkende versie"), zodat een
     * onleesbare of leeggeschreven hoofdsleutel hersteld kan worden in plaats van op defaults uit
     * te komen. De kopie wordt alleen bijgewerkt als de instellingen daadwerkelijk inhoud hebben —
     * anders zou een leeg tussenstadium de reservekopie onbruikbaar maken.
     */
    suspend fun saveSettings(settings: HomeAssistantSettings) {
        val encoded = json.encodeToString(settings)
        val worthKeeping = !settings.activeBaseUrl.isNullOrBlank() ||
            settings.baseUrls.any { it.isNotBlank() } ||
            !settings.alarmSpeaker.isUnset() ||
            !settings.timerSpeaker.isUnset() ||
            !settings.weatherSpeaker.isUnset()

        context.dataStore.edit { preferences ->
            preferences[SETTINGS_KEY] = encoded
            if (worthKeeping) {
                preferences[LAST_GOOD_KEY] = encoded
            }
        }
    }
}
