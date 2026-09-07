package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import com.dd.daykit.rules.BedSampleStatus
import com.dd.daykit.rules.BedSensorSample
import com.dd.daykit.rules.PreAlarmCancelReason
import com.dd.daykit.rules.PreAlarmCheckResult
import com.dd.daykit.rules.PreAlarmCheckStorage
import com.dd.daykit.rules.PreAlarmSampleStore
import com.dd.daykit.rules.TriggerBehaviorMode
import com.dd.daykit.rules.TriggerBehaviorStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Fase 1 voorcheck: bepaalt vóór het alarm of de gebruiker al uit bed is.
 *
 * **Eén meting per aanroep.** Deze receiver deed de drie metingen ooit in één keer, met
 * `delay(90_000)` ertussen binnen `goAsync()` — samen ruim 180 seconden. Android geeft een
 * manifest-receiver hooguit 60 seconden; daarboven telt het als een vastgelopen broadcast en mag
 * het systeem het proces opruimen. Op een toestel met agressief accubeheer betekende dat: precies
 * vijf minuten vóór het alarm minutenlang een achtergrondproces dat netwerkverkeer doet, met kans
 * op een force-stop — en een force-stop wist óók het hoofdalarm uit [android.app.AlarmManager].
 *
 * Daarom plant [AlarmScheduler.schedulePreAlarmCheck] nu drie losse, korte alarmen. Elke aanroep
 * hier doet één HA-lezing (< enkele seconden), schrijft die weg via [PreAlarmSampleStore], en
 * alleen de láátste voert de analyse uit. Metingen overleven daarmee proceswisselingen.
 *
 * Gedrag verder ongewijzigd: stil (geen geluid/melding), en fail-safe — bij twijfel, fout of
 * onbetrouwbare sensorwaarde gaat het alarm gewoon af.
 */
class PreAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PreAlarmReceiver"

        const val EXTRA_SAMPLE_INDEX = "pre_alarm_sample_index"
        const val EXTRA_TOTAL_SAMPLES = "pre_alarm_total_samples"

        /** Minimale tijd dat iemand uit bed moet zijn voordat het alarm vervalt. */
        const val MIN_OUT_OF_BED_DURATION_MS = 2 * 60 * 1000L

        /** Aantal metingen tijdens het voorcheck-venster. */
        const val SAMPLE_COUNT = 3

        /** Afstand tussen twee metingen. Venster = (SAMPLE_COUNT - 1) * dit = 180s. */
        const val SAMPLE_INTERVAL_MS = 90 * 1000L

        private const val HA_QUERY_TIMEOUT_MS = 2000L

        fun isUnreliableState(value: String): Boolean {
            val v = value.trim().lowercase()
            return v.isBlank() || v == "unknown" || v == "unavailable" || v == "none"
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sampleIndex = intent.getIntExtra(EXTRA_SAMPLE_INDEX, 0)
        val totalSamples = intent.getIntExtra(EXTRA_TOTAL_SAMPLES, SAMPLE_COUNT).coerceAtLeast(1)
        val isLastSample = sampleIndex >= totalSamples - 1

        val pendingResult = goAsync()

        val alarmItem = intent.getStringExtra("alarm")?.let {
            try {
                Json.decodeFromString<AlarmItem>(it)
            } catch (e: Exception) {
                Log.e(TAG, "[PRE-ALARM] Error parsing alarm item", e)
                null
            }
        }

        AgendaAlarmForensics.init(context)
        AgendaAlarmForensics.log(
            AgendaAlarmForensics.Cat.PRE_ALARM,
            "meting ${sampleIndex + 1}/$totalSamples ${AgendaAlarmForensics.describe(alarmItem)}",
            context
        )

        if (alarmItem == null) {
            Log.e(TAG, "[PRE-ALARM] No alarm item in intent, aborting pre-check")
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                runSample(context, alarmItem, sampleIndex, totalSamples, isLastSample)
            } catch (e: Exception) {
                Log.e(TAG, "[PRE-ALARM] Error during pre-alarm sample", e)
                // Fail-safe: alarm mag door
                finish(context, alarmItem, false, PreAlarmCancelReason.SENSOR_ERROR)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Slaat het eindresultaat op, ruimt de metingen op en annuleert eventuele resterende
     * meet-alarmen (bijvoorbeeld als al vóór de laatste meting duidelijk is wat de uitkomst is).
     */
    private fun finish(
        context: Context,
        alarmItem: AlarmItem,
        shouldCancel: Boolean,
        reason: PreAlarmCancelReason,
        confidence: Int = 0,
        duration: Long = 0,
        sensorValue: String? = null,
        expectedInBedValue: String? = null
    ) {
        val result = PreAlarmCheckResult(
            alarmId = alarmItem.id,
            triggerId = alarmItem.triggerId,
            shouldCancelAlarm = shouldCancel,
            reason = reason,
            outOfBedConfidencePercent = confidence,
            outOfBedDurationMs = duration,
            sensorValue = sensorValue,
            expectedInBedValue = expectedInBedValue
        )
        PreAlarmCheckStorage(context).storeResult(result)
        PreAlarmSampleStore(context).clear(alarmItem.id)

        AgendaAlarmForensics.log(
            AgendaAlarmForensics.Cat.PRE_ALARM,
            "voorcheck klaar: shouldCancel=$shouldCancel reden=$reason confidence=$confidence% " +
                "duurMs=$duration sensor='$sensorValue' verwachtInBed='$expectedInBedValue' " +
                AgendaAlarmForensics.describe(alarmItem),
            context
        )
        Log.d(TAG, "[PRE-ALARM] Done: cancel=$shouldCancel reason=$reason")
    }

    private suspend fun runSample(
        context: Context,
        alarmItem: AlarmItem,
        sampleIndex: Int,
        totalSamples: Int,
        isLastSample: Boolean
    ) {
        val triggerId = alarmItem.triggerId
        if (triggerId == null) {
            AlarmScheduler.cancelPreAlarmCheck(context)
            finish(context, alarmItem, false, PreAlarmCancelReason.NOT_CHECKED)
            return
        }

        val config = TriggerBehaviorStorage(context).getConfig(triggerId)
        if (config.mode != TriggerBehaviorMode.SMART_ALARM) {
            AlarmScheduler.cancelPreAlarmCheck(context)
            finish(context, alarmItem, false, PreAlarmCancelReason.NOT_CHECKED)
            return
        }

        val haSettingsStorage = HomeAssistantSettingsStorage(context)
        val haRepository = HomeAssistantRepository(HomeAssistantClient, haSettingsStorage)
        val haSettings = haRepository.getSettings()

        if (!haSettings.outOfBedCheckEnabled || haSettings.outOfBedEntityId.isNullOrBlank()) {
            AlarmScheduler.cancelPreAlarmCheck(context)
            finish(context, alarmItem, false, PreAlarmCancelReason.CHECK_DISABLED)
            return
        }

        val bedEntityId = haSettings.outOfBedEntityId!!
        val inBedValue = haSettings.outOfBedExpectedValue.trim().lowercase()

        // Aanwezigheid alleen bij de eerste meting: is de gebruiker niet thuis, dan heeft de
        // bed-check geen betekenis en mag het alarm gewoon af.
        val smartConfig = config.smartConfig
        if (sampleIndex == 0 && smartConfig != null && smartConfig.checkUserAtHome &&
            smartConfig.userPresenceEntityId.isNotBlank()
        ) {
            val presence = try {
                withTimeoutOrNull(HA_QUERY_TIMEOUT_MS) {
                    haRepository.getEntityState(smartConfig.userPresenceEntityId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[PRE-ALARM] Presence error: ${e.message}")
                null
            }

            if (presence == null) {
                AlarmScheduler.cancelPreAlarmCheck(context)
                finish(context, alarmItem, false, PreAlarmCancelReason.PRESENCE_ERROR)
                return
            }
            if (presence.state.trim().lowercase() !=
                smartConfig.userPresenceExpectedValue.trim().lowercase()
            ) {
                AlarmScheduler.cancelPreAlarmCheck(context)
                finish(context, alarmItem, false, PreAlarmCancelReason.USER_NOT_HOME)
                return
            }
        }

        // Precies één lezing per aanroep — dit is wat de receiver kort houdt.
        val sample = readSample(haRepository, bedEntityId, inBedValue)
        val store = PreAlarmSampleStore(context)
        val samples = store.append(alarmItem.id, sample)

        AgendaAlarmForensics.log(
            AgendaAlarmForensics.Cat.PRE_ALARM,
            "meting ${sampleIndex + 1}/$totalSamples entity=$bedEntityId " +
                "waarde='${sample.value}' status=${sample.status} verwachtInBed='$inBedValue'",
            context
        )

        if (!isLastSample) return

        val analysis = analyzeSamples(samples)
        finish(
            context = context,
            alarmItem = alarmItem,
            shouldCancel = analysis.shouldCancel,
            reason = analysis.reason,
            confidence = analysis.confidencePercent,
            duration = analysis.outOfBedDurationMs,
            sensorValue = analysis.lastValue,
            expectedInBedValue = inBedValue
        )
    }

    private suspend fun readSample(
        haRepository: HomeAssistantRepository,
        bedEntityId: String,
        inBedValue: String
    ): BedSensorSample {
        val now = System.currentTimeMillis()
        return try {
            val response = withTimeoutOrNull(HA_QUERY_TIMEOUT_MS) {
                haRepository.getEntityState(bedEntityId)
            } ?: return BedSensorSample(now, null, BedSampleStatus.TIMEOUT)

            val state = response.state.trim().lowercase()
            when {
                isUnreliableState(state) -> BedSensorSample(now, state, BedSampleStatus.UNRELIABLE)
                state == inBedValue -> BedSensorSample(now, state, BedSampleStatus.IN_BED)
                else -> BedSensorSample(now, state, BedSampleStatus.OUT_OF_BED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[PRE-ALARM] Sample error: ${e.message}")
            BedSensorSample(now, null, BedSampleStatus.ERROR)
        }
    }

    private data class Analysis(
        val shouldCancel: Boolean,
        val reason: PreAlarmCancelReason,
        val confidencePercent: Int,
        val outOfBedDurationMs: Long,
        val lastValue: String?
    )

    /**
     * Ongewijzigde beslislogica: alleen annuleren als élke bruikbare meting "uit bed" zegt én dat
     * lang genoeg duurde. Bij te veel fouten of onbruikbare metingen gaat het alarm gewoon af.
     */
    private fun analyzeSamples(samples: List<BedSensorSample>): Analysis {
        val valid = samples.filter {
            it.status == BedSampleStatus.IN_BED || it.status == BedSampleStatus.OUT_OF_BED
        }
        val outOfBed = samples.filter { it.status == BedSampleStatus.OUT_OF_BED }
        val errors = samples.filter {
            it.status == BedSampleStatus.ERROR || it.status == BedSampleStatus.TIMEOUT
        }
        val lastValue = valid.lastOrNull()?.value

        if (samples.isNotEmpty() && errors.size >= samples.size / 2 && errors.isNotEmpty()) {
            return Analysis(false, PreAlarmCancelReason.SENSOR_ERROR, 0, 0, lastValue)
        }
        if (valid.isEmpty()) {
            return Analysis(false, PreAlarmCancelReason.SENSOR_UNRELIABLE, 0, 0, lastValue)
        }

        val confidence = (outOfBed.size * 100) / valid.size
        val firstOutOfBedTime = outOfBed.minByOrNull { it.timestamp }?.timestamp
        val duration = if (firstOutOfBedTime != null) {
            System.currentTimeMillis() - firstOutOfBedTime
        } else 0L

        val lastSample = valid.last()
        val consistentlyOutOfBed = valid.all { it.status == BedSampleStatus.OUT_OF_BED }

        return when {
            lastSample.status == BedSampleStatus.IN_BED ->
                Analysis(false, PreAlarmCancelReason.USER_IN_BED, 0, 0, lastValue)

            !consistentlyOutOfBed ->
                Analysis(false, PreAlarmCancelReason.USER_IN_BED, confidence, duration, lastValue)

            duration < MIN_OUT_OF_BED_DURATION_MS ->
                Analysis(false, PreAlarmCancelReason.INSUFFICIENT_DURATION, confidence, duration, lastValue)

            else ->
                Analysis(true, PreAlarmCancelReason.USER_OUT_OF_BED, confidence, duration, lastValue)
        }
    }
}
