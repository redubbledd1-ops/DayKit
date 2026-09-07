package com.dd.daykit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/** Opens the most specific Android settings page available for each permission-like feature. */
object PermissionSettingsNavigator {

    private const val TAG = "PermissionSettings"

    fun openExactAlarmSettings(context: Context): String? {
        val app = context.applicationContext
        return openFirstAvailable(
            app,
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsCandidate("REQUEST_SCHEDULE_EXACT_ALARM_APP") {
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = packageUri(app)
                        }
                    }
                } else {
                    null
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsCandidate("REQUEST_SCHEDULE_EXACT_ALARM") {
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    }
                } else {
                    null
                },
                appDetailsCandidate(app),
            ),
        )
    }

    fun openFullScreenIntentSettings(context: Context): String? {
        val app = context.applicationContext
        return openFirstAvailable(
            app,
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    SettingsCandidate("MANAGE_APP_USE_FULL_SCREEN_INTENT") {
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = packageUri(app)
                        }
                    }
                } else {
                    null
                },
                appNotificationSettingsCandidate(app),
                appDetailsCandidate(app),
            ),
        )
    }

    fun openOverlaySettings(context: Context): String? {
        val app = context.applicationContext
        return openFirstAvailable(
            app,
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SettingsCandidate("MANAGE_OVERLAY_PERMISSION_APP") {
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(app))
                    }
                } else {
                    null
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SettingsCandidate("MANAGE_OVERLAY_PERMISSION") {
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    }
                } else {
                    null
                },
                appDetailsCandidate(app),
            ),
        )
    }

    fun openNotificationSettings(context: Context): String? {
        val app = context.applicationContext
        return openFirstAvailable(
            app,
            listOf(
                appNotificationSettingsCandidate(app),
                appDetailsCandidate(app),
            ),
        )
    }

    fun openCalendarPermissionSettings(context: Context): String? {
        val app = context.applicationContext
        return openFirstAvailable(
            app,
            listOf(
                appDetailsCandidate(app),
            ),
        )
    }

    /**
     * Directe, gegarandeerde route naar het App-info-scherm van deze app (ACTION_APPLICATION_DETAILS_SETTINGS).
     * Dit scherm moet op elk Android-toestel/OEM (inclusief ColorOS/Oppo) aanwezig zijn — geen fallback-keten
     * met merk-specifieke schermen nodig. Vanaf App-info kan de gebruiker zelf naar "Batterij"/"Accuverbruik"
     * navigeren, ook als de directe batterij-optimalisatie-intent op dit toestel niet werkt.
     */
    fun openAppInfoSettings(context: Context): String? {
        val app = context.applicationContext
        return openFirstAvailable(
            app,
            listOf(appDetailsCandidate(app)),
        )
    }

    /**
     * Generieke AOSP-route (werkt op Samsung en de meeste andere merken). Op ColorOS-toestellen
     * (Oppo/Realme/OnePlus) is dit onbetrouwbaar gebleken — daar gebruikt de UI in plaats daarvan
     * [openAppInfoSettings], gestuurd door [isOppoLikeDevice].
     */
    fun openBatteryOptimizationSettings(context: Context): String? {
        val app = context.applicationContext
        return openFirstAvailable(
            app,
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SettingsCandidate("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_APP") {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = packageUri(app)
                        }
                    }
                } else {
                    null
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SettingsCandidate("IGNORE_BATTERY_OPTIMIZATION_SETTINGS") {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    }
                } else {
                    null
                },
                appDetailsCandidate(app),
            ),
        )
    }

    /**
     * True voor Oppo/Realme/OnePlus (gedeelde ColorOS-codebase). Op deze toestellen negeert het
     * systeem vaak de directe batterij-optimalisatie-intent, dus de UI moet dan [openAppInfoSettings]
     * gebruiken in plaats van [openBatteryOptimizationSettings].
     */
    fun isOppoLikeDevice(): Boolean {
        val needle = listOf("oppo", "realme", "oneplus")
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return needle.any { manufacturer.contains(it) || brand.contains(it) }
    }

    private fun openFirstAvailable(context: Context, candidates: List<SettingsCandidate>): String? {
        val app = context.applicationContext
        val pm = app.packageManager
        for (candidate in candidates) {
            try {
                val intent = candidate.buildIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(pm) == null) {
                    Log.i(TAG, "settings skip ${candidate.label}: no resolving activity")
                    continue
                }
                app.startActivity(intent)
                Log.i(TAG, "settings opened ${candidate.label} action=${intent.action} data=${intent.data}")
                return candidate.label
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "settings ${candidate.label} ActivityNotFoundException", e)
            } catch (e: Exception) {
                Log.w(TAG, "settings ${candidate.label} failed", e)
            }
        }
        Log.e(TAG, "settings all candidates failed")
        return null
    }

    private fun appNotificationSettingsCandidate(app: Context): SettingsCandidate =
        SettingsCandidate("APP_NOTIFICATION_SETTINGS") {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            }
        }

    private fun appDetailsCandidate(app: Context): SettingsCandidate =
        SettingsCandidate("APPLICATION_DETAILS_SETTINGS") {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = packageUri(app)
            }
        }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private data class SettingsCandidate(
        val label: String,
        val buildIntent: () -> Intent,
    )
}
