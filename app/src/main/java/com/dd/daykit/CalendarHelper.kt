package com.dd.daykit

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar

private const val AGENDA_SYNC_LOG = "AgendaAlarmSync"

/** TEMP: [AgendaMoveDebugLog] per-row logging for returned instance list. */
private fun logUpcomingWakeUpEventsForMoveDebug(view: CalendarView, alarms: List<AlarmItem>) {
    AgendaMoveDebugLog.getUpcomingWakeUpEventsHeader(view, alarms.size)
    for (alarm in alarms) {
        AgendaMoveDebugLog.getUpcomingWakeUpEventsRow(view, alarm)
    }
    if (view == CalendarView.SCHEDULING_SCAN) {
        for ((eventId, group) in alarms.groupBy { it.id }) {
            if (group.size > 1 && group.map { it.epochMillis }.distinct().size > 1) {
                AgendaMoveDebugLog.duplicateEventIdDifferentBegin(eventId, group)
            }
        }
    }
}

/**
 * Starttijden tot dit ver in het verleden blijven nog meenemen voor planning.
 * Voorkomt dat korte test-alarmen of sync-latency ervoor zorgen dat een item
 * tussen query en [AlarmManager] al "voorbij" is en dus nooit meer gepland wordt;
 * ook na verplaatsen/hergebruik van dezelfde afspraak blijft de eerstvolgende match vindbaar.
 */
const val AGENDA_ALARM_BEGIN_PAST_GRACE_MS: Long = 120_000L

/**
 * Zelfde soort grace als [AGENDA_ALARM_BEGIN_PAST_GRACE_MS], maar voor de event-gebonden
 * weermeldingen ("Melding voor agenda item" in WeatherAlertWorker/WeatherEventAlertScheduler).
 * Die hebben een eigen, ruimere TRIGGER_PAST_GRACE_MINUTES (10 min in WeatherAlertWorker) om een
 * afspraak die "0 min van tevoren" precies bij aanvang moet triggeren nog te kunnen vangen -
 * zonder deze grace op de Instances-query zelf viel zo'n net-gestarte afspraak al weg vóórdat die
 * 10-min-logica ooit de kans kreeg om 'm te beoordelen.
 */
const val WEATHER_EVENT_BEGIN_PAST_GRACE_MS: Long = 15 * 60 * 1000L

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String
)

enum class CalendarView {
    TODAY,
    NEXT_24_HOURS,
    NEXT_7_DAYS,
    /** Eerste match in venster (typisch 1 jaar); zie [SCHEDULING_SCAN] voor alle kandidaten. */
    NEXT_ALARM,
    /**
     * Zelfde tijdvenster als [NEXT_ALARM], maar alle toekomstige instanties gesorteerd
     * (voor plannen na lokaal uitschakelen zonder agenda te wijzigen).
     */
    SCHEDULING_SCAN,
    WEEK,
    MONTH,
    /**
     * Zelfde 7-dagen-venster als [NEXT_7_DAYS], maar met [WEATHER_EVENT_BEGIN_PAST_GRACE_MS] grace
     * in het verleden zodat een net-gestarte afspraak nog meegenomen wordt. Gebruikt door de
     * event-gebonden weermeldingen (zie [WEATHER_EVENT_BEGIN_PAST_GRACE_MS]).
     */
    WEATHER_EVENT_SCAN
}

fun getCalendars(context: Context): List<CalendarInfo> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }

    val calendars = mutableListOf<CalendarInfo>()
    val projection = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_NAME
    )
    val cursor = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        null,
        null,
        null
    )

    cursor?.use {
        while (it.moveToNext()) {
            val id = it.getLong(0)
            val displayName = it.getString(1)
            val accountName = it.getString(2)
            calendars.add(CalendarInfo(id, displayName, accountName))
        }
    }
    return calendars
}

/**
 * @param calendarIdsOverride Als niet-null: gebruik deze kalender-selectie in plaats van de
 * agenda-alarm-trigger-kalenders ([SettingsManager.getTriggerCalendarIds]). Een lege override-set
 * betekent "geen enkele agenda" (zelfde semantiek als de agenda-alarm-trigger-kalenders) — de
 * weer-agenda-checklists staan standaard leeg/uit totdat de gebruiker zelf agenda's aanvinkt.
 */
fun getUpcomingWakeUpEvents(context: Context, view: CalendarView, calendarIdsOverride: Set<String>? = null): List<AlarmItem> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        AgendaMoveDebugLog.getUpcomingWakeUpEventsEarlyReturn("no_read_calendar_permission view=$view")
        return emptyList()
    }

    val selectedCalendarIds = calendarIdsOverride ?: SettingsManager.getTriggerCalendarIds(context)
    if (selectedCalendarIds.isEmpty()) {
        AgendaMoveDebugLog.getUpcomingWakeUpEventsEarlyReturn("no_trigger_calendar_ids view=$view")
        return emptyList()
    }

    val upcomingAlarms = mutableListOf<AlarmItem>()
    val now = System.currentTimeMillis()
    val schedulingPastGraceCutoff = now - AGENDA_ALARM_BEGIN_PAST_GRACE_MS
    val beginCutoff = when (view) {
        CalendarView.SCHEDULING_SCAN, CalendarView.NEXT_ALARM -> schedulingPastGraceCutoff
        CalendarView.WEATHER_EVENT_SCAN -> now - WEATHER_EVENT_BEGIN_PAST_GRACE_MS
        else -> now
    }

    val projection = arrayOf(
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.CALENDAR_ID
    )

    // Voor planning: venster iets naar het verleden trekken zodat Instances nog net-gestarte
    // (of korte) afspraken teruggeeft; alleen "now" zou die anders uit de provider-query houden.
    val beginTime = when (view) {
        CalendarView.SCHEDULING_SCAN, CalendarView.NEXT_ALARM -> schedulingPastGraceCutoff
        CalendarView.WEATHER_EVENT_SCAN -> now - WEATHER_EVENT_BEGIN_PAST_GRACE_MS
        else -> now
    }
    val endTime = when (view) {
        CalendarView.TODAY -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        CalendarView.NEXT_24_HOURS -> now + 24 * 60 * 60 * 1000
        CalendarView.NEXT_7_DAYS, CalendarView.WEATHER_EVENT_SCAN -> now + 7 * 24 * 60 * 60 * 1000
        CalendarView.WEEK -> now + 7 * 24 * 60 * 60 * 1000
        CalendarView.MONTH -> now + 30 * 24 * 60 * 60 * 1000L
        CalendarView.NEXT_ALARM, CalendarView.SCHEDULING_SCAN -> now + 365 * 24 * 60 * 60 * 1000L // 1 year
    }

    // Build the URI for the Instances query
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, beginTime)
    ContentUris.appendId(builder, endTime)
    val uri = builder.build()

    // Build the selection clause
    val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${Array(selectedCalendarIds.size) { "?" }.joinToString()})"
    val selectionArgs = selectedCalendarIds.toTypedArray()

    val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, "${CalendarContract.Instances.BEGIN} ASC")

    cursor?.use {
        val titleIndex = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
        val beginIndex = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
        val eventIdIndex = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
        val calendarIdIndex = it.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)

        while (it.moveToNext()) {
            val title = it.getString(titleIndex)
            val beginMillis = it.getLong(beginIndex)
            val eventId = it.getLong(eventIdIndex)
            val calendarId = it.getLong(calendarIdIndex)
            
            // Alleen toekomst (UI), of met grace voor planning (voorkomt "gemiste" korte alarmen)
            if (beginMillis > beginCutoff) {
                upcomingAlarms.add(
                    AlarmItem(
                        id = eventId, 
                        epochMillis = beginMillis, 
                        label = title,
                        triggerId = calendarId.toString()
                    )
                )
            }
        }
    }

    val sortedAlarms = upcomingAlarms.sortedBy { it.epochMillis }

    if (view == CalendarView.SCHEDULING_SCAN) {
        val first = sortedAlarms.firstOrNull()
        Log.d(
            AGENDA_SYNC_LOG,
            "instances query: begin=$beginTime end=$endTime rows=${sortedAlarms.size} firstBegin=${first?.epochMillis} firstId=${first?.id}"
        )
    }

    val result = when (view) {
        CalendarView.NEXT_ALARM -> sortedAlarms.take(1)
        CalendarView.SCHEDULING_SCAN -> sortedAlarms.filter { it.epochMillis > schedulingPastGraceCutoff }
        else -> sortedAlarms
    }
    logUpcomingWakeUpEventsForMoveDebug(view, result)
    return result
}

/**
 * Eerste agenda-alarm voor planning dat lokaal niet is uitgeschakeld ([AgendaAlarmLocalActivationStore]).
 *
 * **Strikt toekomst eerst**: voorkomt dat na sluimeren/verwerken dezelfde (al gepasseerde) instantie
 * opnieuw als "eerstvolgende" wordt gekozen terwijl er latere afspraken zijn → lege planning / 0:00 UI.
 * Alleen als er geen strikt-toekomstige match is, valt terug op grace ([AGENDA_ALARM_BEGIN_PAST_GRACE_MS]).
 */
fun getNextSchedulableWakeUpEvent(context: Context): AlarmItem? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        != PackageManager.PERMISSION_GRANTED
    ) {
        AgendaMoveDebugLog.getNextSchedulableEarlyReturn("no_read_calendar_permission")
        return null
    }
    if (SettingsManager.getTriggerCalendarIds(context).isEmpty()) {
        AgendaMoveDebugLog.getNextSchedulableEarlyReturn("no_trigger_calendar_ids")
        return null
    }
    val now = System.currentTimeMillis()
    val softCutoff = now - AGENDA_ALARM_BEGIN_PAST_GRACE_MS
    val candidates = getUpcomingWakeUpEvents(context, CalendarView.SCHEDULING_SCAN)
        .asSequence()
        .filter { AgendaAlarmLocalActivationStore.isLocallyEnabled(context, it) }
        .sortedBy { it.epochMillis }
        .toList()

    val strict = candidates.firstOrNull { it.epochMillis > now }
    val gracePick = candidates.firstOrNull { it.epochMillis > softCutoff }
    val picked = strict ?: gracePick
    val pickReason = when {
        picked == null -> "none_no_candidate"
        picked == strict -> "strict_future_earliest_enabled"
        picked == gracePick -> "grace_fallback_earliest_within_${AGENDA_ALARM_BEGIN_PAST_GRACE_MS}ms"
        else -> "unknown"
    }
    val candidateSummary = candidates.map { "id=${it.id}@${it.epochMillis}" }
    AgendaMoveDebugLog.getNextSchedulableWakeUpEvent(candidateSummary, picked, pickReason, now)
    Log.d(
        AGENDA_SYNC_LOG,
        "getNextSchedulableWakeUpEvent: now=$now candidates=${candidates.size} " +
            "strict=${strict?.let { "id=${it.id} at=${it.epochMillis}" } ?: "null"} " +
            "picked=${picked?.let { "id=${it.id} at=${it.epochMillis} deltaMs=${it.epochMillis - now}" } ?: "null"} reason=$pickReason"
    )
    return picked
}

/** Snelle check: is er nog minstens één ingeschakelde instantie strikt in de toekomst? */
fun hasStrictFutureSchedulableWakeUp(context: Context): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    if (SettingsManager.getTriggerCalendarIds(context).isEmpty()) return false
    val now = System.currentTimeMillis()
    return getUpcomingWakeUpEvents(context, CalendarView.SCHEDULING_SCAN).any {
        AgendaAlarmLocalActivationStore.isLocallyEnabled(context, it) && it.epochMillis > now
    }
}
