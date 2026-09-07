package com.dd.daykit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Plant een exact AlarmManager-alarm voor het eerstvolgende moment waarop een event-gebonden
 * weermelding ("Melding voor agenda item", slecht weer en/of temperatuurwissel) zou moeten
 * triggeren.
 *
 * [WeatherAlertWorker] deed dit voorheen ALLEEN via een 15-min periodic WorkManager-poll, met een
 * vangvenster van maar [WeatherAlertWorker.TRIGGER_PAST_GRACE_MINUTES] minuten NÁ het berekende
 * trigger-moment (nooit ervoor). Een periodic WorkManager-poll heeft echter geen enkele garantie
 * over exacte timing (Doze,
 * App Standby en OEM-batterijbeheer mogen 'm best uitstellen) - als die 15-min-cadans het venster
 * miste, werd de melding stilletjes en definitief nooit afgeleverd, ook al "wist" de app al
 * (via de losse UI-preview in WeatherActivity) dat er een melding aan zat te komen.
 *
 * Dit object plant daarnaast een exact wake-alarm (zelfde precisie als de wekker zelf, zie
 * [WeatherDailyAlertScheduler]) voor het eerstvolgende kandidaat-moment, zodat
 * [WeatherEventAlertReceiver] een check forceert precies wanneer dat nodig is. De 15-min-poll
 * blijft daarnaast gewoon bestaan als vangnet (bv. voor weersveranderingen zonder agenda-wijziging).
 *
 * Bewust conservatief: dit plant alleen op basis van agenda-tijden + instellingen, zonder de
 * "al vandaag afgegaan"/"max per dag"-status of het weer zelf te checken - dat blijft de taak van
 * [WeatherAlertWorker.evaluate]. Een enkele overbodige wake (bv. voor een afspraak die z'n
 * dag-limiet al gehad heeft) is een goedkope no-op; een gemiste wake is dat niet.
 */
object WeatherEventAlertScheduler {
    private const val TAG = "WeatherEventAlertSched"
    private const val REQUEST_CODE = 9350

    /** Kleine marge zodat het geplande moment altijd (net) in de toekomst ligt voor AlarmManager. */
    private const val MIN_LEAD_MS = 3_000L

    /** Herplant het eerstvolgende event-gebonden wake-alarm op basis van agenda + instellingen. Idempotent. */
    fun rescheduleNext(context: Context) {
        val app = context.applicationContext

        if (!SettingsManager.getWeatherNotificationsEnabled(app)) {
            Log.d(TAG, "rescheduleNext: 'Meldingen inschakelen' staat uit - annuleren")
            cancel(app)
            return
        }

        val linkToCalendar = SettingsManager.getWeatherLinkToCalendar(app)
        val badWeatherEnabled = linkToCalendar && SettingsManager.getWeatherAlertBeforeEventEnabled(app)
        val tempChangeEnabled = linkToCalendar &&
            SettingsManager.getWeatherTempChangeEnabled(app) &&
            SettingsManager.getWeatherTempChangeAlertFirstEventEnabled(app)

        if (!badWeatherEnabled && !tempChangeEnabled) {
            Log.d(TAG, "rescheduleNext: geen event-gebonden weermelding ingeschakeld - annuleren")
            cancel(app)
            return
        }

        val now = System.currentTimeMillis()
        // Een kandidaat is nog de moeite waard om een wake voor in te plannen zolang de
        // post-trigger genade ([WeatherAlertWorker.TRIGGER_PAST_GRACE_MINUTES]) nog niet volledig
        // verstreken is. Bewust GEEN pre-trigger tolerantie meer (zie WeatherAlertWorker) - anders
        // zou dit object zelf ook weer te vroege wakes kunnen inplannen.
        val graceAfterMs = WeatherAlertWorker.TRIGGER_PAST_GRACE_MINUTES * 60_000L
        val candidates = mutableListOf<Long>()

        if (badWeatherEnabled) {
            val minutesBefore = SettingsManager.getWeatherRainMinutesBefore(app)
            val events = getUpcomingWakeUpEvents(
                app,
                CalendarView.WEATHER_EVENT_SCAN,
                SettingsManager.getWeatherBadWeatherCalendarIds(app)
            )
            for (event in events) {
                val triggerAt = event.epochMillis - minutesBefore * 60_000L
                if (triggerAt + graceAfterMs > now) candidates.add(triggerAt)
            }
        }

        if (tempChangeEnabled) {
            val minutesBefore = SettingsManager.getWeatherTempChangeAlertFirstEventMinutesBefore(app)
            val events = getUpcomingWakeUpEvents(
                app,
                CalendarView.WEATHER_EVENT_SCAN,
                SettingsManager.getWeatherTempChangeCalendarIds(app)
            )
            for (event in events) {
                val triggerAt = event.epochMillis - minutesBefore * 60_000L
                if (triggerAt + graceAfterMs > now) candidates.add(triggerAt)
            }
        }

        val earliest = candidates.minOrNull()
        if (earliest == null) {
            Log.d(TAG, "rescheduleNext: geen kandidaat-agenda-items binnen bereik - annuleren")
            cancel(app)
            return
        }

        val wakeAt = maxOf(earliest, now + MIN_LEAD_MS)
        schedule(app, wakeAt)
    }

    private fun schedule(context: Context, wakeAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Log.w(TAG, "Exacte alarmen niet toegestaan - event-gebonden weermelding blijft op de 15-min-poll leunen")
            return
        }
        val pi = pendingIntent(context)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAtMillis, pi)
            Log.i(TAG, "rescheduleNext: gepland op $wakeAtMillis (over ${(wakeAtMillis - System.currentTimeMillis()) / 1000}s)")
        } catch (e: Exception) {
            Log.e(TAG, "Kon event-gebonden weermelding-alarm niet plannen", e)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WeatherEventAlertReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
