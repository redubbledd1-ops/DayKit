package com.dd.daykit

import android.content.Context

/**
 * Lokaal (alleen app) Agenda Alarm aan/uit per agenda-instantie.
 * Sleutel: [AlarmItem.id] (eventId) + [AlarmItem.epochMillis] — geen wijziging aan echte agenda.
 */
object AgendaAlarmLocalActivationStore {

    private const val PREFS_NAME = "agenda_alarm_local_activation"
    private const val KEY_DISABLED_KEYS = "disabled_keys"

    fun stableKey(alarm: AlarmItem): String = "${alarm.id}_${alarm.epochMillis}"

    fun isLocallyEnabled(context: Context, alarm: AlarmItem): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val disabled = prefs.getStringSet(KEY_DISABLED_KEYS, emptySet()) ?: emptySet()
        return !disabled.contains(stableKey(alarm))
    }

    fun setLocallyEnabled(context: Context, alarm: AlarmItem, enabled: Boolean) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_DISABLED_KEYS, emptySet()) ?: emptySet()
        val next = HashSet(existing)
        val k = stableKey(alarm)
        if (enabled) next.remove(k) else next.add(k)
        prefs.edit().putStringSet(KEY_DISABLED_KEYS, next).apply()
    }
}
