package com.dd.daykit

import android.content.Context
import android.content.Intent
import android.util.Log
import com.dd.daykit.rules.TriggerBehaviorStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Centrale plek voor Agenda-slomer: minuten-resolutie, keten-trigger-id, logging,
 * en opruimen van AlarmManager-slomer-PI na aflevering zonder de keten-context te verliezen.
 */
object AgendaAlarmSnoozeCoordinator {

    const val TAG = "AgendaAlarmSnooze"

    data class SnoozeDecision(
        val allowed: Boolean,
        val reason: String,
        val minutes: Int,
        val chainSourceTriggerId: String?,
        val currentCount: Int,
        val nextCount: Int,
        val maxCount: Int?,
        val unlimited: Boolean
    )

    /**
     * Kalender/trigger-id voor sluimerketen: echte agenda-alarm heeft [AlarmItem.triggerId];
     * vervolg-slomer heeft [AlarmItem.snoozeSourceTriggerId]; anders prefs (tussen twee snoozes).
     */
    fun resolveChainSourceTriggerId(context: Context, original: AlarmItem?): String? {
        val app = context.applicationContext
        original?.triggerId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "CHAIN_SOURCE from triggerId=$it")
            return it
        }
        original?.snoozeSourceTriggerId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "CHAIN_SOURCE from snoozeSourceTriggerId=$it")
            return it
        }
        val fromStore = AgendaSnoozeStore.readSourceTriggerId(app)
        if (!fromStore.isNullOrBlank()) {
            Log.d(TAG, "CHAIN_SOURCE from store=$fromStore")
        } else {
            Log.d(TAG, "CHAIN_SOURCE none (no trigger on item, empty store)")
        }
        return fromStore?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Sluimertijd: per trigger ([TriggerBehaviorStorage]) wanneer er een keten-trigger-id is,
     * anders altijd [SettingsManager.getSnoozeMinutes] (globale instelling “Sluimertijd”).
     */
    suspend fun resolveSnoozeMinutes(context: Context, originalAlarm: AlarmItem?): Int {
        val app = context.applicationContext
        val globalMinutes = SettingsManager.getSnoozeMinutes(app).coerceAtLeast(0)
        val triggerId = resolveChainSourceTriggerId(app, originalAlarm)?.takeIf { it.isNotBlank() }
        if (triggerId == null) {
            Log.i(TAG, "SNOOZE_MINUTES no chain trigger → global=$globalMinutes")
            return globalMinutes
        }
        return try {
            withContext(Dispatchers.IO) {
                val storage = TriggerBehaviorStorage(app)
                val perTrigger = storage.getConfig(triggerId).snoozeMinutes
                val resolved = (perTrigger ?: SettingsManager.getSnoozeMinutes(app)).coerceAtLeast(0)
                Log.i(
                    TAG,
                    "SNOOZE_MINUTES triggerId=$triggerId perTriggerStored=$perTrigger → use=$resolved (global fallback=$globalMinutes)"
                )
                resolved
            }
        } catch (e: Exception) {
            Log.w(TAG, "SNOOZE_MINUTES read failed triggerId=$triggerId → global=$globalMinutes", e)
            globalMinutes
        }
    }

    /**
     * Centrale beslissing voor de volgende sluimeractie. Dit ondersteunt 0 minuten
     * (geen sluimer) en dwingt de per-trigger maximumlimiet af over herstarts heen.
     */
    suspend fun evaluateNextSnooze(context: Context, originalAlarm: AlarmItem?): SnoozeDecision {
        val app = context.applicationContext
        val chainSource = resolveChainSourceTriggerId(app, originalAlarm)
        val currentCount = AgendaSnoozeStore.readSnoozeCount(app)
        val globalMinutes = SettingsManager.getSnoozeMinutes(app).coerceAtLeast(0)

        var minutes = globalMinutes
        var unlimited = true
        var maxCount: Int? = null

        if (!chainSource.isNullOrBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    val config = TriggerBehaviorStorage(app).getConfig(chainSource)
                    minutes = (config.snoozeMinutes ?: globalMinutes).coerceAtLeast(0)
                    unlimited = config.unlimitedSnooze
                    maxCount = config.snoozeCount?.coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "SNOOZE_DECISION config read failed triggerId=$chainSource; using global defaults", e)
            }
        }

        if (minutes <= 0) {
            return SnoozeDecision(
                allowed = false,
                reason = "snooze_disabled_zero_minutes",
                minutes = minutes,
                chainSourceTriggerId = chainSource,
                currentCount = currentCount,
                nextCount = currentCount,
                maxCount = maxCount,
                unlimited = unlimited
            )
        }

        if (!unlimited) {
            val limit = maxCount ?: 0
            if (currentCount >= limit) {
                return SnoozeDecision(
                    allowed = false,
                    reason = "snooze_limit_reached",
                    minutes = minutes,
                    chainSourceTriggerId = chainSource,
                    currentCount = currentCount,
                    nextCount = currentCount,
                    maxCount = limit,
                    unlimited = false
                )
            }
        }

        return SnoozeDecision(
            allowed = true,
            reason = "allowed",
            minutes = minutes,
            chainSourceTriggerId = chainSource,
            currentCount = currentCount,
            nextCount = currentCount + 1,
            maxCount = maxCount,
            unlimited = unlimited
        )
    }

    suspend fun canOfferSnooze(context: Context, originalAlarm: AlarmItem?): Boolean =
        evaluateNextSnooze(context, originalAlarm).allowed

    /**
     * Wanneer een sluimer-alarm daadwerkelijk rinkelt: annuleer eventuele dubbele AlarmManager-PI,
     * wis het venster in prefs maar **behoud** [AgendaSnoozeStore] keten-trigger-id voor volgende slomer.
     */
    fun onSnoozeAlarmDeliveredClearGhostSchedulingOnly(context: Context) {
        val app = context.applicationContext
        Log.i(TAG, "DELIVERY_CLEAR cancel snooze PI + clear pending window, KEEP chain source")
        AlarmScheduler.cancelSnoozeAlarm(app)
        AgendaSnoozeStore.clearPendingWindowPreserveChainSource(app)
    }

    fun launchPostSnoozeCalendarResync(app: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "POST_SNOOZE scheduleNextAlarm start")
                AlarmScheduler.scheduleNextAlarm(app, "post_snooze_resync")
                Log.i(TAG, "POST_SNOOZE scheduleNextAlarm OK")
            } catch (e: Exception) {
                Log.e(TAG, "POST_SNOOZE scheduleNextAlarm failed", e)
            }
            try {
                app.sendBroadcast(
                    Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED).apply {
                        setPackage(app.packageName)
                    }
                )
            } catch (_: Exception) {
            }
        }
    }
}
