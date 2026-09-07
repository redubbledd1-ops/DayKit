package com.dd.daykit.homeassistant

/**
 * Eén plek voor de paden die de app en de DayKit HA-integratie delen.
 *
 * Deze waren over zes bestanden verspreid als losse letterlijke strings
 * (`"/local/agendaalarm_sounds/..."` in AlarmScheduler, ExternalSpeakerHelper,
 * HomeAssistantRepository, HaSettingsViewModel, SoundHaSync). Bij de naamswijziging naar DayKit
 * moest elk van die zes precies gelijk mee, en één gemiste plek betekent een geluid-URL die naar
 * een niet-bestaande map wijst zonder dat er iets zichtbaar misgaat - het alarm valt dan gewoon
 * stil terug op het standaardgeluid.
 *
 * Deze constanten MOETEN gelijk blijven aan `DOMAIN`, `SOUNDS_SUBDIR` en `WEATHER_TTS_SUBDIR` in
 * de integratie's `const.py`. Wijzigt er één, dan moeten beide kanten in dezelfde release mee.
 */
object HaPaths {
    /** Domein van de HA-integratie; bepaalt zowel de map custom_components/<domein> als /api/<domein>/... */
    const val DOMAIN = "daykit"

    /** Map onder HA's `www/` waar de app haar alarmgeluiden naartoe uploadt. */
    const val SOUNDS_SUBDIR = "daykit_sounds"

    /** Map onder HA's `www/` voor de vluchtige weeralarm-spraak (steeds hetzelfde bestand). */
    const val WEATHER_TTS_SUBDIR = "daykit_tts"

    /** Vaste bestandsnaam van de weeralarm-spraak - gelijk aan WEATHER_TTS_FILENAME in const.py. */
    const val WEATHER_TTS_FILENAME = "weather_tts.wav"

    /**
     * Publieke URL van een naar HA geüpload geluid. [baseUrl] mag met of zonder slash eindigen.
     */
    fun soundUrl(baseUrl: String, filename: String): String =
        "${baseUrl.trimEnd('/')}/local/$SOUNDS_SUBDIR/$filename"

    /**
     * Publieke URL van de laatst geüploade weeralarm-spraak. [cacheBuster] voorkomt dat HA of de
     * speaker een eerder afgespeelde versie van hetzelfde vaste bestand hergebruikt.
     */
    fun weatherTtsUrl(baseUrl: String, cacheBuster: Long = System.currentTimeMillis()): String =
        "${baseUrl.trimEnd('/')}/local/$WEATHER_TTS_SUBDIR/$WEATHER_TTS_FILENAME?t=$cacheBuster"
}
