package com.dd.daykit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Helper klasse voor het berekenen van de eerstvolgende alarmtijd
 */
object NextAlarmCalculator {
    
    /**
     * Bereken de eerstvolgende alarmtijd van alle actieve triggers
     * @param context Android context
     * @param selectedCalendarIds Set van actieve calendar IDs
     * @return Instant van eerstvolgende alarm, of null als geen alarms actief zijn
     */
    fun calculateNextAlarmTime(context: Context, selectedCalendarIds: Set<String>): Instant? {
        if (selectedCalendarIds.isEmpty()) {
            return null
        }
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) 
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        
        val now = System.currentTimeMillis()
        val times = mutableListOf<Long>()
        
        // Query events voor elke geselecteerde kalender
        for (calendarId in selectedCalendarIds) {
            val projection = arrayOf(
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY
            )
            
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ?"
            val selectionArgs = arrayOf(calendarId, now.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"
            
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val startTime = it.getLong(0)
                    times.add(startTime)
                }
            }
        }
        
        val earliestTime = times.minOrNull()
        return earliestTime?.let { Instant.ofEpochMilli(it) }
    }
    
    /**
     * Converteer Instant naar ISO 8601 string met tijdzone
     * @param instant De tijd om te converteren
     * @return ISO 8601 formatted string (bijv. "2025-11-27T06:45:00+01:00")
     */
    fun formatToIso8601(instant: Instant): String {
        val zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
        return zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
    
    /**
     * Bereken en formatteer de volgende alarmtijd
     * @return ISO 8601 string of "unknown" als geen alarm actief is
     */
    fun getNextAlarmIso(context: Context, selectedCalendarIds: Set<String>): String {
        val nextAlarm = calculateNextAlarmTime(context, selectedCalendarIds)
        return if (nextAlarm != null) {
            formatToIso8601(nextAlarm)
        } else {
            "unknown"
        }
    }
}
