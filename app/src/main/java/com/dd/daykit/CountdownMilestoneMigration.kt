package com.dd.daykit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * One-time cleanup for legacy OS-scheduled milestone alarms and prefs from the old
 * countdown-milestone system (receiver class removed). Uses the same request codes
 * and explicit component as the former [CountdownMilestoneScheduler].
 */
object CountdownMilestoneMigration {

    private const val TAG = "MilestoneMigration"
    private const val LEGACY_PREFS = "CountdownMilestonePrefs"
    private const val MIGRATION_PREFS = "app_migrations"
    private const val KEY_DONE = "legacy_milestone_intents_cancelled_v1"

    private const val REQ_CAL_BASE = 6100
    private const val REQ_CAL_COUNT = 4
    private const val REQ_TIMER_BASE = 6200
    private const val REQ_TIMER_COUNT = 16

    fun runOnce(context: Context) {
        val app = context.applicationContext
        val marker = app.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (marker.getBoolean(KEY_DONE, false)) return

        try {
            val cn = ComponentName(app.packageName, "${app.packageName}.CountdownMilestoneReceiver")
            val intent = Intent().setComponent(cn)
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (i in 0 until REQ_CAL_COUNT) {
                val pi = PendingIntent.getBroadcast(
                    app,
                    REQ_CAL_BASE + i,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pi)
            }
            for (i in 0 until REQ_TIMER_COUNT) {
                val pi = PendingIntent.getBroadcast(
                    app,
                    REQ_TIMER_BASE + i,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pi)
            }
            app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            marker.edit().putBoolean(KEY_DONE, true).apply()
            Log.i(TAG, "Legacy milestone alarms cancelled and prefs cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
        }
    }
}
