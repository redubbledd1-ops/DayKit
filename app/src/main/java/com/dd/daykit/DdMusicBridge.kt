package com.dd.daykit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Bridge om DD Music te starten vanuit een CalenderAlarm-trigger (agenda-alarm, wekker of timer).
 *
 * Werking: we sturen een expliciete Intent naar DD Music's MainActivity met extras "trigger_id"
 * en optioneel "display_name". Twee apps, twee losse Gradle-projecten (native Android hier,
 * Flutter in DD Music) - dus geen gedeelde code, alleen dit Intent-contract dat aan beide kanten
 * hetzelfde moet zijn. Zie DD-Music/android/.../MainActivity.kt (ALARM_BRIDGE_CHANNEL).
 *
 * Wat er precies afgespeeld wordt (laatste nummer / specifiek nummer / playlist / favorieten,
 * eventueel willekeurig) wordt bewust NIET hier bepaald - dat kiest de gebruiker in DD Music
 * zelf, de eerste keer dat een trigger_id daar binnenkomt zonder bestaande koppeling. CalenderAlarm
 * stuurt alleen "welke trigger vuurde" (triggerId) en een leesbare naam voor in DD Music's lijst
 * met gekoppelde triggers.
 */
object DdMusicBridge {

    private const val TAG = "DdMusicBridge"

    private const val DD_MUSIC_PACKAGE = "com.musicgraph.musicgraph"
    private const val DD_MUSIC_MAIN_ACTIVITY = "com.musicgraph.musicgraph.MainActivity"

    private const val EXTRA_TRIGGER_ID = "trigger_id"
    private const val EXTRA_DISPLAY_NAME = "display_name"
    private const val EXTRA_FORCE_PICKER = "force_picker"

    /** Broadcast die DD Music terugstuurt zodra de gebruiker in het selectiescherm een keuze heeft
     *  gemaakt (nieuw of gewijzigd), zodat CalenderAlarm een leesbare samenvatting kan tonen i.p.v.
     *  "Gebruik DD Music Als Alarm". Zie DdMusicLinkUpdateReceiver. */
    const val ACTION_LINK_UPDATED = "com.dd.daykit.ACTION_DD_MUSIC_LINK_UPDATED"
    const val EXTRA_LINK_TRIGGER_ID = "trigger_id"
    const val EXTRA_LINK_SUMMARY = "summary"
    /** URL waarop DD Music het gekozen nummer over het lokale netwerk aanbiedt (alleen bij
     *  mode "song" in DD Music) - voor gebruik op een gekoppelde HA-speaker. Optioneel/afwezig
     *  voor andere afspeelmodi (playlist/favorieten/doorgaan spelen alleen lokaal op de telefoon). */
    const val EXTRA_LINK_PLAY_URL = "play_url"
    /** true = de koppeling is verwijderd in DD Music (via het instellingenscherm daar), niet
     *  een nieuwe/gewijzigde keuze. CalenderAlarm moet dan ddMusicLinked weer op false zetten
     *  i.p.v. de (afwezige) summary/play_url te verwerken. Afwezig/false = normale update. */
    const val EXTRA_LINK_REMOVED = "removed"

    /** Vaste, gedeelde trigger-id voor alle timers (hoofdtimer + extra timers 1/2) - zie
     *  GlobalTimerManager/ExtraTimerManager: één DD Music-koppeling geldt voor timers in het
     *  algemeen, niet per individuele timer-instantie. */
    const val TIMER_TRIGGER_ID = "timer"

    /** Checkt of DD Music geïnstalleerd staat op dit toestel. */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(DD_MUSIC_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Start DD Music voor deze trigger. DD Music beslist zelf wat er gebeurt: als [triggerId]
     * al eerder gekoppeld is, speelt het direct de opgeslagen keuze af; zo niet, toont DD Music
     * een selectiescherm (Doorgaan/Kies nummer/Kies playlist/Favorieten) en onthoudt de keuze
     * voor volgende keren.
     *
     * @param context Android context (bij voorkeur applicationContext vanuit een Service).
     * @param triggerId Unieke id van de trigger. Gebruik [TIMER_TRIGGER_ID] voor timers, of de
     *   agenda-alarm trigger-id voor agenda-alarm/wekker (elke agenda-alarm krijgt zo zijn eigen
     *   koppeling in DD Music).
     * @param displayName Leesbare naam (bv. alarm-label) voor in DD Music's lijst met gekoppelde
     *   triggers. Optioneel; DD Music valt terug op de triggerId als deze ontbreekt.
     * @param forcePicker true = toon altijd het selectiescherm, ook als deze trigger al eerder
     *   gekoppeld is (gebruikt door de "Gebruik DD Music Als Alarm"-knop in het alarmgeluid-scherm
     *   om een bestaande keuze te kunnen wijzigen). false (default) = normale afvuur-flow: bekende
     *   trigger speelt direct af, alleen een écht onbekende trigger toont het selectiescherm.
     * @return true als de Intent succesvol is verstuurd, false als DD Music niet geïnstalleerd is
     *         of het starten om een andere reden mislukte (bv. geen geschikte activity).
     */
    fun launch(
        context: Context,
        triggerId: String,
        displayName: String? = null,
        forcePicker: Boolean = false,
    ): Boolean {
        if (triggerId.isBlank()) {
            Log.w(TAG, "Lege triggerId - kan DD Music niet starten")
            return false
        }
        if (!isInstalled(context)) {
            Log.w(TAG, "DD Music ($DD_MUSIC_PACKAGE) is niet geïnstalleerd - kan niet starten")
            return false
        }

        return try {
            // Bewust GEEN ACTION_MAIN + CATEGORY_LAUNCHER: dat is het "alsof de gebruiker het
            // app-icoon tikt"-signaal, waarbij Android voor een reeds draaiende taak vaak alleen
            // de bestaande UI naar voren haalt (zoals bij app-switchen) zónder gegarandeerd de
            // nieuwe intent-extras via onNewIntent af te leveren - merkbaar als DD Music soms
            // gewoon het laatst geopende scherm toont i.p.v. het koppelingsscherm. Een gewone
            // expliciete intent (setClassName is al voldoende om te resolven, geen action/category
            // nodig) + FLAG_ACTIVITY_SINGLE_TOP levert de intent betrouwbaar af aan de bestaande
            // Activity via onNewIntent (MainActivity's launchMode="singleTop" in DD Music's
            // manifest maakt dit consistent, ook als de app al open staat).
            val intent = Intent().apply {
                setClassName(DD_MUSIC_PACKAGE, DD_MUSIC_MAIN_ACTIVITY)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TRIGGER_ID, triggerId)
                if (!displayName.isNullOrBlank()) {
                    putExtra(EXTRA_DISPLAY_NAME, displayName)
                }
                if (forcePicker) {
                    putExtra(EXTRA_FORCE_PICKER, true)
                }
            }
            context.startActivity(intent)
            Log.d(TAG, "DD Music gestart voor triggerId=$triggerId displayName=$displayName forcePicker=$forcePicker")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Kon DD Music niet starten (triggerId=$triggerId)", e)
            false
        }
    }
}
