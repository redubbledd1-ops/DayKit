package com.dd.daykit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

/** De vier "op bepaalde tijd" weermeldingen die op de minuut nauwkeurig moeten aflopen. */
enum class WeatherDailyTrigger(val requestCode: Int, val extraValue: String) {
    BAD_WEATHER_DAY_BEFORE(9301, "badweather_daybefore"),
    BAD_WEATHER_SAME_DAY(9302, "badweather_sameday"),
    TEMP_CHANGE_DAY_BEFORE(9303, "tempchange_daybefore"),
    TEMP_CHANGE_SAME_DAY(9304, "tempchange_sameday");

    companion object {
        fun fromExtraValue(value: String?): WeatherDailyTrigger? = values().firstOrNull { it.extraValue == value }
    }
}

/**
 * Plant de vier vaste-kloktijd weermeldingen ("Dag ervoor", "Zelfde dag" voor slecht weer, en
 * "Dag van tevoren", "Dag zelf" voor temperatuurwissel) als exacte AlarmManager-alarmen — zelfde
 * precisie/aanpak als [AlarmScheduler] voor de wekker (`setExactAndAllowWhileIdle`, werkt ook in
 * Doze). Elk alarm herplant zichzelf voor de volgende dag zodra het is afgegaan
 * ([WeatherDailyAlertReceiver]).
 */
object WeatherDailyAlertScheduler {
    private const val TAG = "WeatherDailyAlertSched"
    const val EXTRA_TRIGGER = "extra_trigger"

    /**
     * Herplant alle 4 vaste-kloktijd triggers op basis van de huidige instellingen, EN het
     * event-gebonden wake-alarm ([WeatherEventAlertScheduler]). Idempotent, mag vaak aangeroepen
     * worden. Elke bestaande aanroeper van rescheduleAll() (settings-wijziging, app-start, boot,
     * backup-restore) wil sowieso ook de event-gebonden meldingen resyncen, dus dat gebeurt hier
     * gebundeld i.p.v. op elke aanroepplek los te moeten toevoegen.
     */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        scheduleTrigger(app, WeatherDailyTrigger.BAD_WEATHER_DAY_BEFORE)
        scheduleTrigger(app, WeatherDailyTrigger.BAD_WEATHER_SAME_DAY)
        scheduleTrigger(app, WeatherDailyTrigger.TEMP_CHANGE_DAY_BEFORE)
        scheduleTrigger(app, WeatherDailyTrigger.TEMP_CHANGE_SAME_DAY)
        WeatherEventAlertScheduler.rescheduleNext(app)
    }

    /** Leest de instelling voor [trigger] en plant 'm (of annuleert 'm als hij uit staat). */
    fun scheduleTrigger(context: Context, trigger: WeatherDailyTrigger) {
        val (enabled, hhmm) = readSetting(context, trigger)
        if (!enabled || !SettingsManager.getWeatherNotificationsEnabled(context)) {
            cancel(context, trigger)
            return
        }
        schedule(context, trigger, hhmm)
    }

    private fun readSetting(context: Context, trigger: WeatherDailyTrigger): Pair<Boolean, String> {
        return when (trigger) {
            // Niet langer gegate op Regenalarm alleen — de dag-venster-check kan ook via de
            // "Extra weersomstandigheden" (storm, hagel, gladde weg, etc.) iets opleveren, ook als
            // Regenalarm zelf uit staat.
            WeatherDailyTrigger.BAD_WEATHER_DAY_BEFORE -> Pair(
                SettingsManager.getWeatherAlertDayBeforeEnabled(context),
                SettingsManager.getWeatherAlertDayBeforeTime(context)
            )
            WeatherDailyTrigger.BAD_WEATHER_SAME_DAY -> Pair(
                SettingsManager.getWeatherAlertSameDayEnabled(context),
                SettingsManager.getWeatherAlertSameDayTime(context)
            )
            WeatherDailyTrigger.TEMP_CHANGE_DAY_BEFORE -> Pair(
                SettingsManager.getWeatherTempChangeEnabled(context) && SettingsManager.getWeatherTempChangeAlertDayBeforeEnabled(context),
                SettingsManager.getWeatherTempChangeAlertDayBeforeTime(context)
            )
            WeatherDailyTrigger.TEMP_CHANGE_SAME_DAY -> Pair(
                SettingsManager.getWeatherTempChangeEnabled(context) && SettingsManager.getWeatherTempChangeAlertSameDayEnabled(context),
                SettingsManager.getWeatherTempChangeAlertSameDayTime(context)
            )
        }
    }

    private fun schedule(context: Context, trigger: WeatherDailyTrigger, hhmm: String) {
        val parts = hhmm.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (hour == null || minute == null) {
            Log.w(TAG, "Ongeldig tijdstip '$hhmm' voor ${trigger.name} — niet gepland")
            return
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Log.w(TAG, "Exacte alarmen niet toegestaan — sla ${trigger.name} over")
            return
        }

        val zone = ZoneId.systemDefault()
        val nowLocal = LocalDateTime.now(zone)
        var target = nowLocal.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(nowLocal)) {
            target = target.plusDays(1)
        }
        val triggerAtMillis = target.atZone(zone).toInstant().toEpochMilli()

        val intent = Intent(context, WeatherDailyAlertReceiver::class.java).apply {
            putExtra(EXTRA_TRIGGER, trigger.extraValue)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            trigger.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            Log.i(TAG, "Scheduled ${trigger.name} at $target")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule ${trigger.name}", e)
        }
    }

    fun cancel(context: Context, trigger: WeatherDailyTrigger) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WeatherDailyAlertReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            trigger.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
