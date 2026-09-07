package com.dd.daykit

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TEMP: instrumentation for agenda move / reschedule diagnosis. Remove when root cause is proven.
 * Filter logcat: `AgendaMoveDebug`
 */
object AgendaMoveDebugLog {

    const val TAG = "AgendaMoveDebug"

    private val wallClockFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun formatWallClock(epochMillis: Long = System.currentTimeMillis()): String =
        "${epochMillis} (${wallClockFmt.format(Date(epochMillis))})"

    fun formatAlarm(alarm: AlarmItem?): String {
        if (alarm == null) return "null"
        return "id=${alarm.id} epoch=${formatWallClock(alarm.epochMillis)} title=${alarm.label.orEmpty().take(80)}"
    }

    fun calendarUpdateReceiver(action: String?, intentData: String?) {
        Log.i(
            TAG,
            "[1 CalendarUpdateReceiver.onReceive] wallClock=${formatWallClock()} action=${action ?: "null"} data=${intentData ?: "null"}"
        )
    }

    fun scheduleNextAlarmStart(source: String) {
        Log.i(
            TAG,
            "[2 AlarmScheduler.scheduleNextAlarm] START source=$source wallClock=${formatWallClock()}"
        )
    }

    fun scheduleNextAlarmResult(source: String, selected: AlarmItem?, detail: String) {
        Log.i(
            TAG,
            "[2 AlarmScheduler.scheduleNextAlarm] END source=$source wallClock=${formatWallClock()} " +
                "selected=${formatAlarm(selected)} detail=$detail"
        )
    }

    /** TEMP: uniform exit marker — filter `AgendaMoveDebug EXIT` */
    fun scheduleNextAlarmExit(source: String, reason: String, detail: String = "") {
        val extra = if (detail.isEmpty()) "" else " detail=$detail"
        Log.i(TAG, "EXIT reason=$reason source=$source wallClock=${formatWallClock()}$extra")
    }

    fun scheduleNextAlarmAfterFetch(source: String, picked: AlarmItem?, attemptsUsed: Int) {
        Log.i(
            TAG,
            "[2 AlarmScheduler.scheduleNextAlarm] AFTER_FETCH source=$source wallClock=${formatWallClock()} " +
                "attemptsUsed=$attemptsUsed picked=${formatAlarm(picked)}"
        )
    }

    fun getNextSchedulableEarlyReturn(reason: String) {
        Log.i(
            TAG,
            "[4 CalendarHelper.getNextSchedulableWakeUpEvent] EARLY_RETURN wallClock=${formatWallClock()} reason=$reason"
        )
    }

    fun getUpcomingWakeUpEventsEarlyReturn(reason: String) {
        Log.i(
            TAG,
            "[3 CalendarHelper.getUpcomingWakeUpEvents] EARLY_RETURN wallClock=${formatWallClock()} reason=$reason"
        )
    }

    fun getUpcomingWakeUpEventsHeader(view: CalendarView, rowCount: Int) {
        Log.i(
            TAG,
            "[3 CalendarHelper.getUpcomingWakeUpEvents] wallClock=${formatWallClock()} view=$view returnedRows=$rowCount"
        )
    }

    fun getUpcomingWakeUpEventsRow(view: CalendarView, alarm: AlarmItem) {
        Log.i(
            TAG,
            "[3 CalendarHelper.getUpcomingWakeUpEvents] candidate view=$view eventId=${alarm.id} " +
                "epochMillis=${alarm.epochMillis} begin=${formatWallClock(alarm.epochMillis)} title=${alarm.label.orEmpty().take(80)}"
        )
    }

    fun duplicateEventIdDifferentBegin(eventId: Long, group: List<AlarmItem>) {
        Log.w(
            TAG,
            "[3 CalendarHelper.getUpcomingWakeUpEvents] DUPLICATE_EVENT_ID_DIFFERENT_BEGIN eventId=$eventId " +
                "rows=${group.joinToString { "epoch=${it.epochMillis}(${formatWallClock(it.epochMillis)})" }}"
        )
    }

    fun getNextSchedulableWakeUpEvent(
        candidateIds: List<String>,
        chosen: AlarmItem?,
        pickReason: String,
        now: Long,
    ) {
        Log.i(
            TAG,
            "[4 CalendarHelper.getNextSchedulableWakeUpEvent] wallClock=${formatWallClock()} now=$now " +
                "candidateIds=[$candidateIds] chosen=${formatAlarm(chosen)} reason=$pickReason"
        )
    }

    fun cancelMainAlarm(lastKnown: AlarmItem?) {
        Log.i(
            TAG,
            "[5 AlarmScheduler.cancelMainAlarm] wallClock=${formatWallClock()} lastKnownScheduled=${formatAlarm(lastKnown)}"
        )
    }

    fun scheduleExactAlarm(requestCode: Int, alarm: AlarmItem) {
        Log.i(
            TAG,
            "[6 AlarmScheduler.scheduleExactAlarm] wallClock=${formatWallClock()} requestCode=$requestCode " +
                "eventId=${alarm.id} title=${alarm.label.orEmpty().take(80)} scheduledEpochMillis=${alarm.epochMillis} " +
                "scheduledBegin=${formatWallClock(alarm.epochMillis)}"
        )
    }
}
