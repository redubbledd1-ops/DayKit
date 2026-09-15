package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dd.daykit.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ontvangt de exacte dagelijkse weer-alarmen van [WeatherDailyAlertScheduler], evalueert de
 * bijbehorende conditie, levert eventueel de melding af, en herplant zichzelf meteen voor de
 * volgende dag (AlarmManager kent geen "herhaal elke dag exact" meer, dus dit is het gangbare
 * self-rescheduling patroon).
 */
class WeatherDailyAlertReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "WeatherDailyAlertRecv"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = WeatherDailyTrigger.fromExtraValue(intent.getStringExtra(WeatherDailyAlertScheduler.EXTRA_TRIGGER))
        if (trigger == null) {
            Log.w(TAG, "Onbekende trigger-waarde in intent, negeer")
            return
        }
        val app = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleTrigger(app, trigger)
            } catch (e: Exception) {
                Log.e(TAG, "Fout bij afhandelen $trigger", e)
            } finally {
                // Altijd herplannen voor morgen, ook als de check zelf niets opleverde of uitgeschakeld is.
                WeatherDailyAlertScheduler.scheduleTrigger(app, trigger)
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTrigger(context: Context, trigger: WeatherDailyTrigger) {
        if (!SettingsManager.getWeatherNotificationsEnabled(context)) return

        val repo = WeatherRepository()
        val lat = SettingsManager.getWeatherLatitude(context)
        val lon = SettingsManager.getWeatherLongitude(context)
        val model = SettingsManager.getWeatherModel(context)
        val forecast = repo.getForecast(lat, lon, model).getOrNull() ?: return
        val now = System.currentTimeMillis()
        val today = LocalDate.now(ZoneId.systemDefault())

        when (trigger) {
            WeatherDailyTrigger.BAD_WEATHER_DAY_BEFORE -> {
                if (!SettingsManager.getWeatherAlertDayBeforeEnabled(context)) return
                val rainEnabled = SettingsManager.getWeatherRainAlarmEnabled(context)
                val rainThreshold = SettingsManager.getWeatherRainThreshold(context)
                val warning = checkTomorrowWarning(context, forecast, rainEnabled, rainThreshold, now)
                if (warning.isEmpty) return
                // Puur regen: titel wordt "Morgen kleine kans op regen" (zelfde opbouw als vandaag,
                // met "Morgen " ervoor), submelding = percentage.
                // Anders (meerdere/andere redenen): titel = reden, "Morgen: " + kans in het bericht.
                val (title, chanceText) = if (warning.labels.size == 1 && warning.rainProbability != null) {
                    warning.labels.first().toRainNotificationParts(isTomorrow = true)
                } else {
                    val (t, c) = combineWeatherReasonsForNotification(warning.labels)
                    t to LanguageManager.getString("weather_tomorrow_colon").replace("{chance}", c)
                }
                WeatherAlertWorker.deliverAlert(
                    context,
                    title,
                    chanceText,
                    notificationKey = 31
                )
            }
            WeatherDailyTrigger.BAD_WEATHER_SAME_DAY -> {
                if (!SettingsManager.getWeatherAlertSameDayEnabled(context)) return
                val rainEnabled = SettingsManager.getWeatherRainAlarmEnabled(context)
                val rainThreshold = SettingsManager.getWeatherRainThreshold(context)
                val warning = checkTodayWarning(context, forecast, rainEnabled, rainThreshold, now)
                if (warning.isEmpty) return
                // Puur regen: titel = kwalitatieve tekst ("Kleine kans op regen"), bericht = percentage.
                // Anders: titel = reden, bericht = kans.
                val (title, chanceText) = if (warning.labels.size == 1 && warning.rainProbability != null) {
                    warning.labels.first().toRainNotificationParts(isTomorrow = false)
                } else {
                    combineWeatherReasonsForNotification(warning.labels)
                }
                WeatherAlertWorker.deliverAlert(
                    context,
                    title,
                    chanceText,
                    notificationKey = 32
                )
            }
            WeatherDailyTrigger.TEMP_CHANGE_DAY_BEFORE -> {
                if (!SettingsManager.getWeatherTempChangeEnabled(context) ||
                    !SettingsManager.getWeatherTempChangeAlertDayBeforeEnabled(context)
                ) return
                deliverTempChangeDailyAlert(
                    context, repo, forecast, lat, lon, model, now, today,
                    targetIsTomorrow = true,
                    notificationKey = 33
                )
            }
            WeatherDailyTrigger.TEMP_CHANGE_SAME_DAY -> {
                if (!SettingsManager.getWeatherTempChangeEnabled(context) ||
                    !SettingsManager.getWeatherTempChangeAlertSameDayEnabled(context)
                ) return
                deliverTempChangeDailyAlert(
                    context, repo, forecast, lat, lon, model, now, today,
                    targetIsTomorrow = false,
                    notificationKey = 34
                )
            }
        }
    }

    /**
     * Dagelijkse temperatuurwissel-melding (dag van tevoren / dezelfde dag).
     *
     * Agenda gekoppeld: alleen als er op de anker-dag een afspraak staat. Dag van tevoren
     * ([targetIsTomorrow] = true) kijkt naar MORGEN en vergelijkt hetzelfde kloktijdstip vandaag
     * met morgen — een afspraak van alleen vandaag telt niet. Zonder agenda blijft de oude
     * vergelijking van de dagtmax overeind.
     */
    private suspend fun deliverTempChangeDailyAlert(
        context: Context,
        repo: WeatherRepository,
        forecast: WeatherForecast,
        lat: Double,
        lon: Double,
        model: String,
        now: Long,
        today: LocalDate,
        targetIsTomorrow: Boolean,
        notificationKey: Int
    ) {
        val threshold = SettingsManager.getWeatherTempChangeThreshold(context)
        val tomorrow = today.plusDays(1)
        val linkToCalendar = SettingsManager.getWeatherLinkToCalendar(context)
        val calendarIds = SettingsManager.getWeatherTempChangeCalendarIds(context)

        if (linkToCalendar && calendarIds.isNotEmpty()) {
            val events = getUpcomingWakeUpEvents(context, CalendarView.NEXT_7_DAYS, calendarIds)
            val event = pickTempChangeAnchorEvent(
                events = events,
                nowMillis = now,
                dayBeforeEnabled = targetIsTomorrow,
                sameDayEnabled = !targetIsTomorrow,
                firstEventEnabled = false
            ) ?: return
            val (fromMillis, toMillis) = tempChangeCompareTimes(event.epochMillis, now)
            val diff = repo.getTemperatureChange(lat, lon, fromMillis, toMillis, model).getOrNull() ?: return
            if (kotlin.math.abs(diff) < threshold) return
            val targetTemp = repo.getWeatherForTime(lat, lon, toMillis, model).getOrNull()?.temperature
                ?: return
            val day = weatherAlertDayFor(toMillis, now)
            val message = LanguageManager.getString("weather_around_event")
                .replace("{combined}", formatTempChangeMessage(diff, threshold))
                .replace("{event}", event.label)
            WeatherAlertWorker.deliverAlert(
                context,
                formatTempChangeTitle(context, targetTemp, day),
                message,
                notificationKey = notificationKey
            )
            return
        }

        val maxToday = computeMaxTempForDate(forecast, today) ?: return
        val maxTomorrow = computeMaxTempForDate(forecast, tomorrow) ?: return
        val diff = maxTomorrow - maxToday
        if (kotlin.math.abs(diff) < threshold) return
        WeatherAlertWorker.deliverAlert(
            context,
            formatTempChangeTitle(context, maxTomorrow, WeatherAlertDay.TOMORROW),
            formatTempChangeMessage(diff, threshold),
            notificationKey = notificationKey
        )
    }
}
