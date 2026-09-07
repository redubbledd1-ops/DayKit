package com.dd.daykit

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object AlarmStorage {
    private const val FILENAME = "alarms.json"

    fun getAlarms(context: Context): List<AlarmItem> {
        val file = File(context.filesDir, FILENAME)
        if (!file.exists()) {
            return emptyList()
        }
        return try {
            val jsonString = file.readText()
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addAlarm(context: Context, alarm: AlarmItem) {
        val alarms = getAlarms(context).toMutableList()
        alarms.add(alarm)
        saveAlarms(context, alarms)
    }

    fun removeAlarm(context: Context, alarmId: Long) {
        val alarms = getAlarms(context).toMutableList()
        alarms.removeAll { it.id == alarmId }
        saveAlarms(context, alarms)
    }

    private fun saveAlarms(context: Context, alarms: List<AlarmItem>) {
        val file = File(context.filesDir, FILENAME)
        try {
            val jsonString = Json.encodeToString(alarms)
            file.writeText(jsonString)
        } catch (e: Exception) {
            // Handle error
        }
    }
}
