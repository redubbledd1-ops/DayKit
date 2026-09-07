package com.dd.daykit

import android.content.Context
import com.dd.daykit.util.CountdownFormatter
import com.dd.daykit.util.CountdownMode
import java.util.Calendar

/**
 * Titelregels voor Agenda Alarm tray-notificaties (zelfde layout als timer).
 */
object AgendaAlarmNotificationCopy {

    fun upcomingTitle(context: Context, eventLabel: String, minutesUntil: Int): String {
        LanguageManager.init(context.applicationContext)
        val fmt = LanguageManager.getString("agenda_alarm_notify_upcoming_format")
        return String.format(
            LanguageManager.getLocale(),
            fmt,
            eventLabel,
            minutesUntil.coerceAtLeast(1)
        )
    }

    /**
     * Compacte hoofdtekst voor de vooraf-popup: alleen aftelweergave (geen afspraaknaam).
     * Tijd via [CountdownFormatter] (floor). Andere kalenderdag: "Morgen".
     */
    fun compactCountdownTitle(context: Context, alarmEpochMillis: Long): String {
        LanguageManager.init(context.applicationContext)
        val now = System.currentTimeMillis()
        val remaining = alarmEpochMillis - now
        val calAlarm = Calendar.getInstance().apply { timeInMillis = alarmEpochMillis }
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val laterCalendarDay =
            calAlarm.get(Calendar.YEAR) > calNow.get(Calendar.YEAR) ||
                (
                    calAlarm.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                        calAlarm.get(Calendar.DAY_OF_YEAR) > calNow.get(Calendar.DAY_OF_YEAR)
                    )
        if (laterCalendarDay) {
            return LanguageManager.getString("agenda_popup_countdown_tomorrow")
        }
        return CountdownFormatter.format(remaining, CountdownMode.COMPACT_TRAY)
    }

    /**
     * Aftel-titel voor de knopless-melding: NOOIT "Morgen", altijd een klok-achtige
     * H:MM-aftelling (geen seconden — de melding tikt toch maar 1x per minuut bij).
     * Bewust een aparte functie t.o.v. [compactCountdownTitle] zodat de andere
     * agenda-alarm-meldingen (popup, snooze, lockscreen) ongewijzigd blijven.
     */
    fun buttonlessCountdownTitle(context: Context, alarmEpochMillis: Long): String {
        LanguageManager.init(context.applicationContext)
        val remaining = (alarmEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val totalMinutes = remaining / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}"
        } else {
            "$minutes ${LanguageManager.getString("min")}"
        }
    }
}
