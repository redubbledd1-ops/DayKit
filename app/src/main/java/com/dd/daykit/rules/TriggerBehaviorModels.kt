package com.dd.daykit.rules

/**
 * Mogelijke modi voor een trigger (ONE_TIME alleen nog voor oude opgeslagen data; wordt bij lezen gemigreerd).
 */
enum class TriggerBehaviorMode {
    NORMAL,       // Simpel alarm: sluimeren instelbaar
    SMART_ALARM,  // Home Assistant entiteit check
    ONE_TIME      // Legacy: eenmalig alarm (niet meer in UI)
}

/**
 * Configuratie voor Smart Alarm modus
 */
data class SmartAlarmConfig(
    val entityId: String,
    val expectedValue: String,
    val preventManualDismiss: Boolean = false,
    // Nieuwe instellingen voor aanwezigheidscheck
    val checkUserAtHome: Boolean = false,
    val userPresenceEntityId: String = "",
    val userPresenceExpectedValue: String = "on"
)

/**
 * Volledige configuratie voor een trigger
 */
data class TriggerRulesConfig(
    val triggerId: String,
    val mode: TriggerBehaviorMode = TriggerBehaviorMode.NORMAL,
    val smartConfig: SmartAlarmConfig? = null,
    val alreadyFired: Boolean = false,
    // Per-trigger alarm instellingen (nullable = gebruik global default/legacy of nog niet ingesteld)
    val alarmSoundUri: String? = null,
    val vibrate: Boolean? = null,
    val snoozeMinutes: Int? = null,
    val alarmVolume: Int? = null,
    
    // Snooze limiet instellingen (voor NORMAL mode)
    val snoozeCount: Int? = 3, // Standaard 3 keer als niet onbeperkt
    val unlimitedSnooze: Boolean = true, // Standaard onbeperkt

    // DD Music koppeling: alleen aan/uit. Wát er precies afgespeeld wordt (laatste nummer/
    // specifiek nummer/playlist/favorieten, evt. willekeurig) kiest de gebruiker in DD Music
    // zelf - zie DdMusicBridge. AlarmService stuurt bij true de trigger-id door; DD Music
    // beslist zelf of dat een nieuwe koppeling (selectiescherm) of een bekende is.
    val ddMusicLinked: Boolean = false,
    // Leesbare samenvatting van de keuze die in DD Music gemaakt is (bv. songtitel of
    // playlistnaam), teruggestuurd via broadcast door DdMusicLinkUpdateReceiver. Null zolang
    // er nog geen keuze bekend is (dan toont de UI de generieke "Gebruik DD Music Als Alarm").
    val ddMusicSummary: String? = null,
    // URL waarop DD Music het gekozen nummer over het lokale netwerk aanbiedt (alleen gezet bij
    // mode "song" in DD Music - andere modi sturen dit niet mee). Als een HA-speaker voor deze
    // trigger geconfigureerd is, gebruikt AlarmService/TimeEngineService deze URL i.p.v. het
    // normale alarmgeluid, zodat het gekozen nummer op de speaker te horen is.
    val ddMusicPlayUrl: String? = null
)
