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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Meldt dat een agenda-alarm is overgeslagen.
 *
 * Een Slim alarm kan zichzelf blokkeren (uit-bed-check, smart-conditie, lokaal uitgeschakeld) en
 * deed dat tot nu toe volledig geruisloos: geen geluid, geen melding, geen spoor in de app. Het
 * verschil met "het alarm is nooit afgegaan" was voor de gebruiker niet te zien — vandaar dat een
 * gemiste ochtend pas uren later opviel. Deze melding maakt de beslissing zichtbaar, inclusief de
 * waarde die de doorslag gaf, zodat een verkeerd gekozen entiteit meteen opvalt.
 */
object AgendaAlarmSkipNotifier {

    private const val TAG = "AgendaAlarmSkip"

    /** Eigen kanaal: dit is nadrukkelijk géén alarm, dus niet op het alarmkanaal. */
    const val CHANNEL_ID = "agenda_alarm_skipped"

    private const val NOTIFICATION_ID = 87301

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = context.applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overgeslagen agenda-alarm",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Melding wanneer een agenda-alarm bewust is overgeslagen"
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "ensureChannel failed", e)
        }
    }

    /**
     * @param reason korte reden, bv. "Uit-bed-check zei dat je uit bed was"
     * @param detail optionele technische toelichting (entiteit, gelezen waarde, verwachte waarde)
     */
    fun notifySkipped(
        context: Context,
        alarm: AlarmItem?,
        reason: String,
        detail: String? = null
    ) {
        try {
            val app = context.applicationContext
            ensureChannel(app)

            val label = alarm?.label?.trim()?.takeIf { it.isNotEmpty() } ?: "Agenda-alarm"
            val time = alarm?.epochMillis?.takeIf { it > 0L }?.let { timeFmt.format(Date(it)) }
            val title = if (time != null) "Alarm $time overgeslagen" else "Agenda-alarm overgeslagen"

            val short = "'$label' ging niet af — $reason"
            val long = buildString {
                append(short)
                if (!detail.isNullOrBlank()) {
                    append("\n\n")
                    append(detail)
                }
            }

            val openIntent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPi = PendingIntent.getActivity(
                app,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(short)
                .setStyle(NotificationCompat.BigTextStyle().bigText(long))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .build()

            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)

            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.BLOCK,
                "melding getoond: $short${detail?.let { " | $it" } ?: ""}",
                app
            )
            Log.i(TAG, "Skip notification shown: $short")
        } catch (e: Exception) {
            Log.e(TAG, "notifySkipped failed", e)
        }
    }
}
