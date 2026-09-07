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
                val threshold = SettingsManager.getWeatherTempChangeThreshold(context)
                val tomorrow = today.plusDays(1)
                val dayAfter = today.plusDays(2)
                val maxTomorrow = computeMaxTempForDate(forecast, tomorrow) ?: return
                val maxDayAfter = computeMaxTempForDate(forecast, dayAfter) ?: return
                val diff = maxDayAfter - maxTomorrow
                if (kotlin.math.abs(diff) < threshold) return
                // Deze melding gaat over OVERMORGEN, vergeleken met morgen - hij waarschuwt een dag
                // eerder dan de "Zelfde dag"-variant hieronder. De tekst zei desondanks "morgen"
                // en "dan vandaag".
                WeatherAlertWorker.deliverAlert(
                    context,
                    formatTempChangeTitle(context, maxDayAfter, WeatherAlertDay.DAY_AFTER_TOMORROW),
                    formatTempChangeMessage(diff, threshold, comparedToTomorrow = true),
                    notificationKey = 33
                )
            }
            WeatherDailyTrigger.TEMP_CHANGE_SAME_DAY -> {
                if (!SettingsManager.getWeatherTempChangeEnabled(context) ||
                    !SettingsManager.getWeatherTempChangeAlertSameDayEnabled(context)
                ) return
                val threshold = SettingsManager.getWeatherTempChangeThreshold(context)
                val tomorrow = today.plusDays(1)
                val maxToday = computeMaxTempForDate(forecast, today) ?: return
                val maxTomorrow = computeMaxTempForDate(forecast, tomorrow) ?: return
                val diff = maxTomorrow - maxToday
                if (kotlin.math.abs(diff) < threshold) return
                // Gaat over morgen, vergeleken met vandaag - "morgen" hoort hier dus wél in de titel.
                WeatherAlertWorker.deliverAlert(
                    context,
                    formatTempChangeTitle(context, maxTomorrow, WeatherAlertDay.TOMORROW),
                    formatTempChangeMessage(diff, threshold),
                    notificationKey = 34
                )
            }
        }
    }
}
