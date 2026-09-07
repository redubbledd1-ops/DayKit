package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dd.daykit.rules.TriggerBehaviorStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ontvangt de broadcast die DD Music terugstuurt zodra de gebruiker in het selectiescherm een
 * keuze heeft gemaakt (nieuw of gewijzigd), zie [DdMusicBridge.ACTION_LINK_UPDATED]. Slaat de
 * leesbare samenvatting op zodat het alarmgeluid-scherm "Gebruik DD Music Als Alarm" kan
 * vervangen door bv. de songtitel of playlistnaam.
 *
 * Werkt voor zowel agenda-alarm-triggers (opgeslagen via [TriggerBehaviorStorage]) als de
 * gedeelde timer-trigger ([DdMusicBridge.TIMER_TRIGGER_ID], opgeslagen via
 * [TimerSettingsStateHolder] met rauwe SharedPreferences - de mutableStateOf-properties worden
 * hier bewust niet direct gezet omdat deze receiver in een aparte/korte proces-lifecycle kan
 * draaien; een volgende scherm-opening leest gewoon de nieuwe waarde uit prefs/DataStore).
 */
class DdMusicLinkUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DdMusicLinkUpdate"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DdMusicBridge.ACTION_LINK_UPDATED) return
        val triggerId = intent.getStringExtra(DdMusicBridge.EXTRA_LINK_TRIGGER_ID)
        if (triggerId.isNullOrBlank()) {
            Log.w(TAG, "Broadcast zonder triggerId genegeerd")
            return
        }
        val removed = intent.getBooleanExtra(DdMusicBridge.EXTRA_LINK_REMOVED, false)
        val summary = intent.getStringExtra(DdMusicBridge.EXTRA_LINK_SUMMARY)
        // Alleen aanwezig bij mode "song" in DD Music (zie DdMusicBridge.EXTRA_LINK_PLAY_URL) -
        // null/afwezig voor playlist/favorieten/doorgaan, en wist dan ook een eerder opgeslagen
        // URL (bv. na wisselen van "Kies nummer" naar "Kies playlist" voor dezelfde trigger).
        val playUrl = intent.getStringExtra(DdMusicBridge.EXTRA_LINK_PLAY_URL)
        if (!removed && summary.isNullOrBlank()) {
            Log.w(TAG, "Broadcast zonder summary genegeerd (triggerId=$triggerId)")
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        if (triggerId == DdMusicBridge.TIMER_TRIGGER_ID) {
            // Timer-koppeling: gedeelde instelling, rauwe SharedPreferences-write is voldoende -
            // TimerSettingsStateHolder.init() leest deze de volgende keer dat het scherm opent.
            try {
                val editor = appContext.getSharedPreferences("TimerSettings", Context.MODE_PRIVATE)
                    .edit()
                if (removed) {
                    editor.putBoolean("dd_music_linked", false)
                        .remove("dd_music_summary")
                        .remove("dd_music_play_url")
                    Log.i(TAG, "Timer DD Music-koppeling verwijderd (vanuit DD Music)")
                } else {
                    editor.putBoolean("dd_music_linked", true)
                        .putString("dd_music_summary", summary)
                        .putString("dd_music_play_url", playUrl)
                    Log.i(TAG, "Timer DD Music-koppeling bijgewerkt: $summary")
                }
                editor.apply()
            } finally {
                pendingResult.finish()
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storage = TriggerBehaviorStorage(appContext)
                val config = storage.getConfig(triggerId)
                if (removed) {
                    storage.saveConfig(
                        config.copy(ddMusicLinked = false, ddMusicSummary = null, ddMusicPlayUrl = null)
                    )
                    Log.i(TAG, "DD Music-koppeling verwijderd voor trigger=$triggerId (vanuit DD Music)")
                } else {
                    storage.saveConfig(
                        config.copy(ddMusicLinked = true, ddMusicSummary = summary, ddMusicPlayUrl = playUrl)
                    )
                    Log.i(TAG, "DD Music-koppeling bijgewerkt voor trigger=$triggerId: $summary")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Kon DD Music-koppeling niet bijwerken voor trigger=$triggerId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
