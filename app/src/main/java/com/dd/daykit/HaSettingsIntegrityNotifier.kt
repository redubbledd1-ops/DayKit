package com.dd.daykit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Meldt dat er iets mis was met de opgeslagen Home Assistant-instellingen.
 *
 * Tot nu toe verdween een onleesbare configuratie geruisloos: de app viel terug op defaults,
 * schreef die ook nog eens weg, en de gebruiker merkte pas dat de speakerinstellingen van
 * agenda-alarm, timer of weer verkeerd stonden op het moment dat er iets níet afging. Elke
 * ingreep van het vangnet moet daarom zichtbaar zijn.
 */
object HaSettingsIntegrityNotifier {

    private const val TAG = "HaSettingsIntegrity"

    const val CHANNEL_ID = "ha_settings_integrity"

    private const val NOTIFICATION_ID = 87401

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = context.applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Home Assistant-instellingen",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Waarschuwing als opgeslagen HA-instellingen niet gelezen konden worden"
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "ensureChannel failed", e)
        }
    }

    fun notify(context: Context, title: String, message: String) {
        try {
            val app = context.applicationContext
            ensureChannel(app)

            val openIntent = Intent(app, HaSettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPi = PendingIntent.getActivity(
                app,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setCategory(Notification.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .build()

            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)

            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.PROCESS,
                "HA-instellingen integriteit: $title — $message",
                app
            )
            Log.w(TAG, "$title: $message")
        } catch (e: Exception) {
            Log.e(TAG, "notify failed", e)
        }
    }
}
