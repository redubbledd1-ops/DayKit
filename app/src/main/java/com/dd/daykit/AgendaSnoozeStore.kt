package com.dd.daykit

import android.content.Context

/**
 * Disk-backed agenda-alarm snooze state so snooze survives process death and reboot
 * (AlarmManager alarms are cleared on reboot; we reschedule from prefs in [AlarmScheduler.restorePendingSnoozeIfNeeded]).
 */
object AgendaSnoozeStore {

    private const val PREFS_NAME = "agenda_snooze_state"
    private const val KEY_END_MS = "snooze_end_ms"
    private const val KEY_ALARM_JSON = "snooze_alarm_json"
    /** Kalender-trigger id van het oorspronkelijke alarm; blijft staan tijdens sluimerketen (snooze-alarm heeft geen triggerId). */
    private const val KEY_SOURCE_TRIGGER_ID = "snooze_source_trigger_id"
    /** Aantal keren dat de huidige sluimerketen al door de gebruiker is gepland. */
    private const val KEY_SNOOZE_COUNT = "snooze_count"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * @param sourceTriggerId kalender/trigger-id van het agenda-alarm; bij null blijft een eerder opgeslagen id staan (keten).
     */
    fun persist(
        context: Context,
        endMillis: Long,
        alarmJson: String,
        sourceTriggerId: String? = null,
        snoozeCount: Int? = null
    ) {
        val ed = prefs(context).edit()
            .putLong(KEY_END_MS, endMillis)
            .putString(KEY_ALARM_JSON, alarmJson)
        if (!sourceTriggerId.isNullOrBlank()) {
            ed.putString(KEY_SOURCE_TRIGGER_ID, sourceTriggerId.trim())
        }
        if (snoozeCount != null) {
            ed.putInt(KEY_SNOOZE_COUNT, snoozeCount.coerceAtLeast(0))
        }
        ed.commit()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_END_MS)
            .remove(KEY_ALARM_JSON)
            .remove(KEY_SOURCE_TRIGGER_ID)
            .remove(KEY_SNOOZE_COUNT)
            .commit()
    }

    /**
     * Na aflevering van sluimer-alarm: eindtijd + json weg (geen actief venster meer),
     * maar agenda-keten-trigger-id en teller blijven staan voor dezelfde duur/regels/limiet.
     */
    fun clearPendingWindowPreserveChainSource(context: Context) {
        prefs(context).edit()
            .remove(KEY_END_MS)
            .remove(KEY_ALARM_JSON)
            .commit()
    }

    fun readSourceTriggerId(context: Context): String? =
        prefs(context).getString(KEY_SOURCE_TRIGGER_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun readEndMillis(context: Context): Long =
        prefs(context).getLong(KEY_END_MS, 0L)

    fun readAlarmJson(context: Context): String? =
        prefs(context).getString(KEY_ALARM_JSON, null)

    fun readSnoozeCount(context: Context): Int =
        prefs(context).getInt(KEY_SNOOZE_COUNT, 0).coerceAtLeast(0)
}
