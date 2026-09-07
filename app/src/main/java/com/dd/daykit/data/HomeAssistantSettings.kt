package com.dd.daykit.data

import kotlinx.serialization.Serializable

@Serializable
data class HomeAssistantSettings(
    val baseUrls: List<String> = emptyList(),
    val activeBaseUrl: String? = null,
    val longLivedToken: String? = null,
    val entities: List<String> = emptyList(), // Generieke lijst met handige entiteiten
    val showOnlyLinkedEntities: Boolean = false, // Toon alleen gekoppelde entiteiten in dropdowns
    val backupAlarmEnabled: Boolean = false, // Backup-wekker via Home Assistant (deprecated - use externalSpeakerMode)
    // Externe-speaker instellingen, apart per onderdeel (zie SpeakerContext) sinds de speaker-
    // splitsing - elk onderdeel heeft zijn eigen speaker/modus/volume/geluid, i.p.v. één gedeelde
    // instelling voor Agenda-alarm, Timer en Weer samen. Zie ook migrateSpeakerSettingsIfNeeded().
    val alarmSpeaker: SpeakerSettings = SpeakerSettings(),
    val timerSpeaker: SpeakerSettings = SpeakerSettings(),
    val weatherSpeaker: SpeakerSettings = SpeakerSettings(),
    // true zodra de eenmalige migratie van de oude gedeelde velden (externalSpeakerEntityId e.a.
    // hieronder) naar alarmSpeaker/timerSpeaker/weatherSpeaker heeft gedraaid. Zie
    // HomeAssistantSettingsStorage.settingsFlow.
    val speakerSettingsMigrated: Boolean = false,
    @Deprecated("Gebruik alarmSpeaker/timerSpeaker/weatherSpeaker - blijft alleen bestaan als eenmalige migratiebron voor bestaande gebruikers")
    val externalSpeakerEntityId: String? = null, // Entity ID van externe speaker (bijv. media_player.woonkamer)
    @Deprecated("Gebruik alarmSpeaker/timerSpeaker/weatherSpeaker - blijft alleen bestaan als eenmalige migratiebron voor bestaande gebruikers")
    val externalSpeakerMode: ExternalSpeakerMode = ExternalSpeakerMode.BOTH, // Modus voor externe speaker
    val presenceEntityId: String? = null, // Entity ID voor "gebruiker thuis" check (bijv. binary_sensor.frank_thuis)
    val presenceExpectedState: String = "home", // Verwachte waarde voor "thuis" (bijv. "home", "on", "true")
    val outOfBedCheckEnabled: Boolean = false, // Uit bed check ingeschakeld
    val outOfBedEntityId: String? = null, // Entity ID voor "uit bed" check (bijv. binary_sensor.bed_occupied)
    val outOfBedExpectedValue: String = "off", // Waarde die aangeeft dat iemand in bed ligt (bijv. "on", "home", "1")
    // Alarm Backup instellingen (voor webhook naar Home Assistant)
    // NOTE: backupSpeakerEntityId is verwijderd - we gebruiken alarmSpeaker.entityId
    @Deprecated("Gebruik alarmSpeaker/timerSpeaker/weatherSpeaker - blijft alleen bestaan als eenmalige migratiebron voor bestaande gebruikers")
    val backupVolume: Int = 70, // Volume voor backup alarm (0-100)
    // true = laat het volume van de externe HA-speaker ongemoeid (stuur geen volume_set), gebruikt
    // dus altijd wat het volume daar toevallig al op staat. Geldt overal waar backupVolume anders
    // gebruikt zou worden (kalender-alarm, timer, "test speaker"-knoppen, én de HA-kant watchdog).
    @Deprecated("Gebruik alarmSpeaker/timerSpeaker/weatherSpeaker - blijft alleen bestaan als eenmalige migratiebron voor bestaande gebruikers")
    val skipBackupVolume: Boolean = false,
    val backupAlarmDuration: Int = 10, // Duur in seconden hoelang het alarm afspeelt (0-600, standaard 10) - alleen relevant voor de HA-watchdog van het agenda-alarm, blijft bewust gedeeld
    @Deprecated("Gebruik alarmSpeaker/timerSpeaker/weatherSpeaker - blijft alleen bestaan als eenmalige migratiebron voor bestaande gebruikers")
    val selectedBackupSoundId: Long? = null, // ID van lokaal geluid voor HA backup-alarm
    @Deprecated("Gebruik entities in plaats hiervan") val inBedSensors: List<String> = emptyList(),
    @Deprecated("Gebruik entities in plaats hiervan") val outBedSensors: List<String> = emptyList(),
    // Losse HA-script (script.turn_on) instellingen voor alarm en timer, onafhankelijk van elkaar
    // en van de externe-speaker instellingen.
    val alarmScriptEnabled: Boolean = false,
    val alarmScriptEntityId: String? = null,
    val timerScriptEnabled: Boolean = false,
    val timerScriptEntityId: String? = null,
    // true = altijd uitvoeren (default, geen gedragsverandering voor bestaande gebruikers),
    // false = alleen uitvoeren als gebruiker thuis is (zelfde presence-check als speaker DEFAULT)
    val alarmScriptIgnorePresence: Boolean = true,
    val timerScriptIgnorePresence: Boolean = true,
    // Weeralarmen laten uitspreken op weatherSpeaker.entityId hierboven. De spraak wordt lokaal
    // gegenereerd door de telefoon (Android TextToSpeech, zie WeatherAlertWorker.kt) en naar HA
    // geupload voor afspelen via media_player.play_media - er is dus geen los HA tts-platform
    // (voorheen weatherTtsEntityId) meer nodig.
    val weatherTtsEnabled: Boolean = false,
    // --- Velden die alleen de HA-kant zelf gebruikt, maar die wel gedeeld zijn (zie de sectie
    // "Defaults" in HA's Configureren-scherm). De app doet er zelf niets mee; ze staan hier zodat
    // een wijziging in HA netjes via de "HA-update gevonden"-melding kan binnenkomen en daarna
    // gewoon mee terug-gepusht wordt, i.p.v. dat de app ze bij de eerstvolgende save stilzwijgend
    // op een lege waarde zet. null = "de app kent deze waarde nog niet" - die wordt bewust NIET
    // gepusht (zie buildConfigPatchJson), zodat een in HA ingestelde waarde nooit gewist wordt
    // door een app die er nog nooit van gehoord heeft.
    val notifyService: String? = null,
    val safetyTimeoutSeconds: Int? = null
) {
    /**
     * Eenmalige migratie van de oude, gedeelde externe-speaker-velden naar de nieuwe
     * per-onderdeel-velden (alarmSpeaker/timerSpeaker/weatherSpeaker). Idempotent: als
     * [speakerSettingsMigrated] al true is, gebeurt er niets. Wordt aangeroepen vanuit
     * HomeAssistantSettingsStorage.settingsFlow zodat elke lezer (ViewModel, AlarmService,
     * ExtraTimerManager, WeatherAlertWorker, enz.) altijd de gemigreerde vorm ziet.
     */
    fun migrateSpeakerSettingsIfNeeded(): HomeAssistantSettings {
        if (speakerSettingsMigrated) return this

        @Suppress("DEPRECATION")
        val legacy = SpeakerSettings(
            entityId = externalSpeakerEntityId,
            mode = externalSpeakerMode,
            volume = backupVolume,
            skipVolume = skipBackupVolume,
            soundId = selectedBackupSoundId
        )

        // Alleen contexten vullen die nog nooit ingesteld zijn. Deze migratie overschreef vroeger
        // alarmSpeaker/timerSpeaker/weatherSpeaker onvoorwaardelijk, óók als ze al goed gevuld
        // waren. Omdat de ViewModel de oude gedeelde velden bij opslaan niet meer meeschrijft,
        // stonden die op hun defaults (entityId=null, mode=BOTH, volume=70) — dus zodra de vlag om
        // welke reden dan ook weer false was (oudere backup, mislukte lezing), werden alle drie de
        // onderdelen daarmee overschreven en stond de modus opeens op BOTH zonder speaker.
        val hasLegacyValue = !legacy.entityId.isNullOrBlank() || legacy.soundId != null
        return copy(
            alarmSpeaker = if (hasLegacyValue && alarmSpeaker.isUnset()) legacy else alarmSpeaker,
            timerSpeaker = if (hasLegacyValue && timerSpeaker.isUnset()) legacy else timerSpeaker,
            weatherSpeaker = if (hasLegacyValue && weatherSpeaker.isUnset()) legacy else weatherSpeaker,
            speakerSettingsMigrated = true
        )
    }
}

/**
 * Externe-speaker instellingen voor 1 onderdeel (Agenda-alarm, Timer of Weer) - zie
 * [SpeakerContext]. Elk onderdeel heeft zijn eigen kopie sinds de speaker-splitsing.
 */
@Serializable
data class SpeakerSettings(
    val entityId: String? = null, // Entity ID van externe speaker (bijv. media_player.woonkamer)
    val mode: ExternalSpeakerMode = ExternalSpeakerMode.BOTH,
    val volume: Int = 70, // 0-100
    // true = laat het volume van de externe HA-speaker ongemoeid (stuur geen volume_set)
    val skipVolume: Boolean = false,
    val soundId: Long? = null // ID van lokaal geluid
) {
    /**
     * True als dit onderdeel nog nooit is ingesteld: geen speaker gekozen én geen geluid gekozen.
     * De modus alleen is geen bewijs, want die heeft een niet-neutrale default ([ExternalSpeakerMode.BOTH]).
     * Gebruikt door [HomeAssistantSettings.migrateSpeakerSettingsIfNeeded] om te bepalen of er nog
     * iets te migreren valt, zodat een bestaande instelling nooit overschreven wordt.
     */
    fun isUnset(): Boolean = entityId.isNullOrBlank() && soundId == null
}

/**
 * De drie plekken die elk hun eigen externe-speaker instellingen hebben. Gebruikt door
 * HaSettingsViewModel om te bepalen welke [SpeakerSettings] gelezen/geschreven worden, en door
 * SpeakerModal om de "ook toepassen op..."-melding bij opslaan te tonen.
 */
enum class SpeakerContext {
    ALARM, TIMER, WEATHER
}

/** Haalt de [SpeakerSettings] voor [context] op - centrale plek zodat AlarmService/ExtraTimerManager/
 * TimeEngineService/WeatherAlertWorker/ExternalSpeakerHelper/enz. niet allemaal hun eigen when-blok
 * hoeven te dupliceren. */
fun HomeAssistantSettings.speakerFor(context: SpeakerContext): SpeakerSettings = when (context) {
    SpeakerContext.ALARM -> alarmSpeaker
    SpeakerContext.TIMER -> timerSpeaker
    SpeakerContext.WEATHER -> weatherSpeaker
}

/**
 * Automatisch afgeleide "aanwezig"-waarde voor een presence-entiteit, gebaseerd op het domein.
 * Vervangt het vroegere handmatige presenceExpectedState-invulveld in de UI: nu de primaire/
 * automatische bron een binary_sensor (ping-detectie, zie tryAutoAdoptPresenceEntity) of een
 * person-entiteit is, is een vrij tekstveld niet meer nodig om te bepalen wat "thuis" betekent.
 * Gebruikt door zowel [com.dd.daykit.AlarmOutputDecisionEngine.isUserAtHome] (app-kant)
 * als de config-push naar HA (presence_expected_state, gelezen door HA's eigen
 * AlarmBackupHub.is_user_home() fail-safe watchdog-logica).
 */
fun deriveExpectedPresenceState(entityId: String?): String {
    if (entityId.isNullOrBlank()) return "home"
    return when (entityId.substringBefore('.')) {
        "binary_sensor" -> "on"
        "person", "device_tracker" -> "home"
        else -> "on"
    }
}

@Serializable
enum class ExternalSpeakerMode {
    DISABLED,        // Externe speaker niet gebruiken
    DEFAULT,         // Standaard speaker (hoofd-alarm via deze speaker)
    BACKUP_ONLY,     // Alleen gebruiken als backup bij lege batterij
    BOTH             // Alarm op beide speakers (externe + mobiel)
}
