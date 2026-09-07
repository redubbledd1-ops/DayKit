package com.dd.daykit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
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
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Houdt de weerlocatie actueel terwijl je onderweg bent — maar alléén als de locatie op GPS
 * staat ([SettingsManager.getWeatherLocationIsGps]). Bij een vaste/handmatig gezochte locatie
 * doet deze worker niets. Gebruikt alleen de goedkope, al-gecachete `lastLocation` (geen actieve
 * GPS-polling) en werkt de opgeslagen locatie alleen bij als er een echte verplaatsing is
 * geweest — dus geen onnodige weer-herfetches bij elke tik.
 */
class WeatherLocationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "weather_location_sync_periodic"
        const val MIN_INTERVAL_MINUTES = 15L

        /** Pas onder deze afstand wordt de opgeslagen weerlocatie NIET bijgewerkt. */
        private const val MOVE_THRESHOLD_METERS = 2000f

        private const val TAG = "WeatherLocationSync"

        fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE) {
            val intervalMinutes = SettingsManager.getWeatherLocationSyncIntervalMinutes(context)
                .coerceAtLeast(MIN_INTERVAL_MINUTES)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<WeatherLocationSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
            Log.i(TAG, "Scheduled every ${intervalMinutes}min policy=${policy.name}")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Periodic weather location sync cancelled")
        }

        /** Herstart/annuleert de worker op basis van de huidige instellingen (GPS aan/uit). */
        fun syncScheduleWithSettings(context: Context) {
            if (SettingsManager.getWeatherLocationIsGps(context)) {
                schedule(context, ExistingPeriodicWorkPolicy.KEEP)
            } else {
                cancel(context)
            }
        }
    }

    override suspend fun doWork(): Result {
        if (!SettingsManager.getWeatherLocationIsGps(applicationContext)) {
            Log.d(TAG, "Locatie staat vast (geen GPS) — skip")
            return Result.success()
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.w(TAG, "Geen locatiepermissie — skip")
            return Result.success()
        }

        return try {
            val location = getLastLocation(applicationContext)
            if (location == null) {
                Log.d(TAG, "Geen locatie beschikbaar")
                return Result.success()
            }

            val storedLat = SettingsManager.getWeatherLatitude(applicationContext)
            val storedLon = SettingsManager.getWeatherLongitude(applicationContext)

            val distanceMeters = FloatArray(1)
            Location.distanceBetween(storedLat, storedLon, location.latitude, location.longitude, distanceMeters)

            if (distanceMeters[0] < MOVE_THRESHOLD_METERS) {
                Log.d(TAG, "Locatie nauwelijks veranderd (${distanceMeters[0].toInt()}m) — geen update nodig")
                return Result.success()
            }

            SettingsManager.saveWeatherLatitude(applicationContext, location.latitude)
            SettingsManager.saveWeatherLongitude(applicationContext, location.longitude)
            val resolvedName = reverseGeocodeLocationName(applicationContext, location.latitude, location.longitude)
                ?: "GPS locatie"
            SettingsManager.saveWeatherLocationName(applicationContext, resolvedName)
            Log.i(TAG, "Weerlocatie bijgewerkt naar $resolvedName (${distanceMeters[0].toInt()}m verplaatst)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weerlocatie-sync mislukt", e)
            Result.retry()
        }
    }

    private suspend fun getLastLocation(context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                LocationServices.getFusedLocationProviderClient(context)
                    .lastLocation
                    .addOnSuccessListener { location -> if (cont.isActive) cont.resume(location) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
}
