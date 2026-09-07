package com.dd.daykit.rules

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Eén meting van de bed-entiteit tijdens de voorcheck.
 */
@Serializable
enum class BedSampleStatus {
    IN_BED,
    OUT_OF_BED,
    UNRELIABLE,
    TIMEOUT,
    ERROR
}

@Serializable
data class BedSensorSample(
    val timestamp: Long,
    val value: String? = null,
    val status: BedSampleStatus
)

/**
 * Persistente opslag voor de losse metingen van de uit-bed-voorcheck.
 *
 * Nodig sinds de voorcheck niet langer drie minuten in één [android.content.BroadcastReceiver]
 * blijft hangen (dat is ruim over de Android-limiet en kost je in het ergste geval een force-stop
 * mét je hoofdalarm). De metingen worden nu door losse, korte alarmen gedaan, en dus moet elke
 * meting proceswisselingen kunnen overleven.
 */
class PreAlarmSampleStore(private val context: Context) {

    companion object {
        private const val TAG = "PreAlarmSampleStore"
        private const val PREFS_NAME = "pre_alarm_samples"
        private const val KEY_PREFIX = "samples_"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun key(alarmId: Long) = "$KEY_PREFIX$alarmId"

    fun read(alarmId: Long): List<BedSensorSample> {
        return try {
            val raw = prefs.getString(key(alarmId), null) ?: return emptyList()
            json.decodeFromString<List<BedSensorSample>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "read failed for $alarmId", e)
            emptyList()
        }
    }

    fun append(alarmId: Long, sample: BedSensorSample): List<BedSensorSample> {
        return try {
            val updated = read(alarmId) + sample
            prefs.edit()
                .putString(key(alarmId), json.encodeToString(updated))
                .commit() // commit i.p.v. apply: het proces mag hierna meteen verdwijnen
            updated
        } catch (e: Exception) {
            Log.e(TAG, "append failed for $alarmId", e)
            emptyList()
        }
    }

    fun clear(alarmId: Long) {
        try {
            prefs.edit().remove(key(alarmId)).commit()
        } catch (e: Exception) {
            Log.e(TAG, "clear failed for $alarmId", e)
        }
    }

    /** Opruimen van metingen van alarmen die allang geweest zijn. */
    fun clearAll() {
        try {
            prefs.edit().clear().commit()
        } catch (e: Exception) {
            Log.e(TAG, "clearAll failed", e)
        }
    }
}
