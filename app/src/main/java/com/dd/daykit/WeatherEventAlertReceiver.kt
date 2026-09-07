package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Ontvangt het exacte wake-alarm van [WeatherEventAlertScheduler] en forceert een directe
 * [WeatherAlertWorker]-check (dezelfde `evaluate()` als de periodieke 15-min-poll en "Test nu" -
 * dus met alle bestaande dedup/max-per-dag-logica intact). In tegenstelling tot "Test nu" wordt de
 * dedup-status HIER niet gereset - dit is geen test-actie maar de "echte" precieze trigger.
 *
 * [WeatherAlertWorker.doWork] plant na afloop altijd zelf de eerstvolgende wake weer in (zie de
 * `WeatherEventAlertScheduler.rescheduleNext`-aanroep aan het eind van doWork()), dus dit
 * self-rescheduling patroon is hetzelfde als bij [WeatherDailyAlertReceiver].
 */
class WeatherEventAlertReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "WeatherEventAlertRecv"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Exact wake-alarm afgegaan - forceer directe weer-check voor agenda-gebonden meldingen")
        val request = OneTimeWorkRequestBuilder<WeatherAlertWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }
}
