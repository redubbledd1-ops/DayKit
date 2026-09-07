package com.dd.daykit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "calendar_sync_periodic"
        const val DEFAULT_INTERVAL_MINUTES = 30L
        const val MIN_INTERVAL_MINUTES = 15L

        private const val TAG = "CalendarSyncWorker"

        fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
            val intervalMinutes = SettingsManager.getPeriodicSyncIntervalMinutes(context)
                .coerceAtLeast(MIN_INTERVAL_MINUTES)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
            Log.i(TAG, "Scheduled every ${intervalMinutes}min policy=${policy.name}")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Periodic sync cancelled")
        }
    }

    override suspend fun doWork(): Result {
        // Hartslag: elke run legt vast of het alarm op dát moment nog gewapend stond. Ontbreken deze
        // regels een halve nacht, dan heeft Doze de worker uitgesteld — precies de situatie waarin
        // een ontwapend alarm niet meer hersteld wordt.
        AgendaAlarmForensics.init(applicationContext)
        AgendaAlarmForensics.snapshotArmedState(applicationContext, "periodic_sync_heartbeat")

        val calendarPermission = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.READ_CALENDAR
        )
        if (calendarPermission != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALENDAR permission not granted — skipping")
            AgendaAlarmForensics.log(
                AgendaAlarmForensics.Cat.SCHEDULE,
                "Periodieke sync OVERGESLAGEN: READ_CALENDAR-permissie ontbreekt — er wordt niets gepland.",
                applicationContext
            )
            return Result.success()
        }
        // Self-healing: als een backup-restore de agenda-trigger-ID's niet meteen kon herkoppelen
        // (bv. omdat er toen nog geen agenda-permissie of gesynchroniseerde kalenders waren — heel
        // gebruikelijk vlak na het instellen van een nieuwe telefoon), probeert elke periodieke
        // sync het hier opnieuw. Zie BackupManager.remapTriggerCalendarIdsAfterRestore. No-op als
        // er niets pending staat.
        try {
            BackupManager.reconcilePendingCalendarTriggerRemap(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "reconcilePendingCalendarTriggerRemap failed", e)
        }
        return try {
            Log.i(TAG, "Periodic calendar sync starting")
            SyncStatusManager.onSyncStarted()
            val nextAlarm = AlarmScheduler.scheduleNextAlarm(applicationContext, "workmanager_periodic_sync")
            SyncStatusManager.onSyncCompleted(applicationContext, nextAlarm)
            reportPhoneIpToHomeAssistant()
            Log.i(TAG, "Periodic calendar sync done — nextAlarm=${nextAlarm?.label}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Periodic calendar sync failed", e)
            SyncStatusManager.onSyncFailed(applicationContext, e.message ?: "Unknown error")
            Result.retry()
        }
    }

    /**
     * Meldt het huidige lokale IP-adres van de telefoon aan de DayKit-integratie, zodat de
     * ingebouwde ping-detectie (binary_sensor.py) blijft kloppen.
     *
     * Bewust hier en niet alleen in [AlarmScheduler]: daar lift het IP mee op het wapenen van de
     * watchdog, en dat gebeurt alleen bij een SMART_ALARM met een aankomend agenda-item. Wie een
     * gewoon alarm gebruikt, of even geen afspraak heeft staan, meldde zijn IP dus nooit - en dan
     * blijft de ping-sensor leeg of blijft hij een oud, door de router hergebruikt adres pingen.
     * Deze worker draait sowieso elke [DEFAULT_INTERVAL_MINUTES] minuten, dus dit is de goedkoopste
     * plek om het adres actueel te houden.
     *
     * Volledig best-effort: geen HA-koppeling of een mislukte call mag de agenda-sync nooit als
     * mislukt laten terugkomen.
     */
    private suspend fun reportPhoneIpToHomeAssistant() {
        try {
            val storage = com.dd.daykit.data.HomeAssistantSettingsStorage(applicationContext)
            val settings = storage.settingsFlow.first()
            if (settings.activeBaseUrl.isNullOrBlank() || settings.longLivedToken.isNullOrBlank()) {
                return
            }
            val phoneIp = AlarmOutputDecisionEngine.getPhoneIpAddress(applicationContext) ?: return
            val repository = com.dd.daykit.data.HomeAssistantRepository(
                com.dd.daykit.network.HomeAssistantClient,
                storage
            )
            repository.reportPhoneIp(phoneIp)
        } catch (e: Exception) {
            Log.d(TAG, "reportPhoneIpToHomeAssistant overgeslagen: ${e.message}")
        }
    }
}
