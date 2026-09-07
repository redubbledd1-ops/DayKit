package com.dd.daykit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

private const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"

/**
 * KalenderAlarm gebruikt de systeem [CalendarContract] provider. Dat werkt ook zonder de aparte
 * Google Calendar-app (bijv. OEM-agenda met Google-sync); daarom geen package-check op
 * `com.google.android.calendar`.
 */
sealed class CalendarFeatureStatus {
    data object Ready : CalendarFeatureStatus()
    data object CalendarPermissionMissing : CalendarFeatureStatus()
    data object CalendarAccessUnavailable : CalendarFeatureStatus()
}

object GoogleCalendarFeatureChecker {
    /** Voor UX-hints; KalenderAlarm werkt ook met andere agenda-apps via de systeem-provider. */
    fun isGoogleCalendarAppInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(GOOGLE_CALENDAR_PACKAGE) != null

    fun evaluate(context: Context): CalendarFeatureStatus {
        if (!hasCalendarPermission(context)) {
            return CalendarFeatureStatus.CalendarPermissionMissing
        }
        if (!canReadCalendarProvider(context)) {
            return CalendarFeatureStatus.CalendarAccessUnavailable
        }
        return CalendarFeatureStatus.Ready
    }

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Real access check against Calendar provider.
     * Avoids false positives where permission exists but provider access still fails.
     */
    fun canReadCalendarProvider(context: Context): Boolean {
        return try {
            val projection = arrayOf(CalendarContract.Calendars._ID)
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
