package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import com.dd.daykit.rules.PreAlarmCancelReason
import com.dd.daykit.rules.PreAlarmCheckStorage
import com.dd.daykit.rules.RuleEngine
import com.dd.daykit.rules.TriggerBehaviorMode
import com.dd.daykit.rules.TriggerBehaviorStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "AlarmReceiver onReceive - action: ${intent.action}")
        AlarmRingingDebug.i("AlarmReceiver.onReceive action=${intent.action}")
        AlarmRingingDebug.logWakeMobile(context.applicationContext, "AlarmReceiver.onReceive")
        AlarmRingingDebug.logFsiPermission(context.applicationContext, "AlarmReceiver.onReceive")
        
        // Use goAsync to keep the process alive long enough for the service to start
        val pendingResult = goAsync()

        // Parse alarm item to get triggerId
        val alarmJson = intent.getStringExtra("alarm")
        val alarmItem = alarmJson?.let {
            try {
                Json.decodeFromString<AlarmItem>(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing alarm item", e)
                null
            }
        }

        // Scharnierpunt van de hele diagnose: staat deze regel in de log, dan heeft het OS het alarm
        // wél afgevuurd en is de oorzaak hieronder te vinden. Ontbreekt hij, dan is het alarm nooit
        // gewapend geweest of tussentijds ontwapend — dan telt de SCHEDULE/ARM-historie.
        AgendaAlarmForensics.init(context)
        AgendaAlarmForensics.log(
            AgendaAlarmForensics.Cat.RECEIVE,
            "AlarmReceiver.onReceive ${AgendaAlarmForensics.describe(alarmItem)} " +
                "latenessMs=${alarmItem?.let { System.currentTimeMillis() - it.epochMillis }}",
            context
        )

        // Check RuleEngine if we should fire this alarm
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var shouldFire = true

                if (alarmItem != null && !AgendaAlarmLocalActivationStore.isLocallyEnabled(context.applicationContext, alarmItem)) {
                    Log.w(TAG, "Alarm locally disabled — skipping fire; rescheduling")
                    AgendaAlarmForensics.log(
                        AgendaAlarmForensics.Cat.BLOCK,
                        "GEBLOKKEERD door=LOKAAL_UITGESCHAKELD ${AgendaAlarmForensics.describe(alarmItem)}",
                        context
                    )
                    AgendaAlarmSkipNotifier.notifySkipped(
                        context,
                        alarmItem,
                        "dit alarm stond in de app uitgeschakeld",
                        "Je hebt deze afspraak eerder handmatig uitgezet in Agenda Alarm. " +
                            "De afspraak zelf is niet gewijzigd."
                    )
                    AlarmScheduler.scheduleNextAlarm(context.applicationContext, "alarm_receiver_locally_disabled")
                    pendingResult.finish()
                    return@launch
                }
                
                // PHASE 1: Check pre-alarm result first (if available)
                alarmItem?.let { alarm ->
                    val preAlarmStorage = PreAlarmCheckStorage(context)
                    val preAlarmMode = alarm.triggerId?.let { triggerId ->
                        TriggerBehaviorStorage(context).getConfig(triggerId).mode
                    }
                    val preAlarmResult = if (preAlarmMode == TriggerBehaviorMode.SMART_ALARM) {
                        preAlarmStorage.getResult(alarm.id)
                    } else {
                        preAlarmStorage.clearResult(alarm.id)
                        Log.d(TAG, "[ALARM] Standalone/simple alarm - ignoring Home Assistant pre-alarm result")
                        null
                    }
                    
                    AgendaAlarmForensics.log(
                        AgendaAlarmForensics.Cat.RECEIVE,
                        "mode=$preAlarmMode preAlarmResult=" + (preAlarmResult?.let {
                            "shouldCancel=${it.shouldCancelAlarm} reason=${it.reason} " +
                                "confidence=${it.outOfBedConfidencePercent}% sensor='${it.sensorValue}' " +
                                "verwachtInBed='${it.expectedInBedValue}'"
                        } ?: "none"),
                        context
                    )
                    if (preAlarmResult != null) {
                        Log.d(TAG, "=".repeat(70))
                        Log.d(TAG, "[ALARM] Pre-alarm check result found:")
                        Log.d(TAG, "[ALARM]   - shouldCancel: ${preAlarmResult.shouldCancelAlarm}")
                        Log.d(TAG, "[ALARM]   - reason: ${preAlarmResult.reason}")
                        Log.d(TAG, "[ALARM]   - confidence: ${preAlarmResult.outOfBedConfidencePercent}%")
                        Log.d(TAG, "[ALARM]   - duration: ${preAlarmResult.outOfBedDurationMs}ms")
                        
                        if (preAlarmResult.shouldCancelAlarm && 
                            preAlarmResult.reason == PreAlarmCancelReason.USER_OUT_OF_BED) {
                            Log.d(TAG, "[ALARM] ✅ ALARM CANCELLED by Phase 1 pre-alarm check")
                            Log.d(TAG, "[ALARM] Reason: User was out of bed with high confidence")
                            Log.d(TAG, "=".repeat(70))
                            AgendaAlarmForensics.log(
                                AgendaAlarmForensics.Cat.BLOCK,
                                "GEBLOKKEERD door=PRE_ALARM_UIT_BED (T-5min check) " +
                                    "confidence=${preAlarmResult.outOfBedConfidencePercent}% " +
                                    "duurMs=${preAlarmResult.outOfBedDurationMs} " +
                                    "sensor='${preAlarmResult.sensorValue}' verwachtInBed='${preAlarmResult.expectedInBedValue}' " +
                                    AgendaAlarmForensics.describe(alarm),
                                context
                            )
                            AgendaAlarmSkipNotifier.notifySkipped(
                                context,
                                alarm,
                                "de voorcheck zag je al uit bed",
                                "Vijf minuten voor het alarm werd ${preAlarmResult.outOfBedConfidencePercent}% " +
                                    "van de metingen als 'uit bed' gelezen " +
                                    "(${preAlarmResult.outOfBedDurationMs / 1000} seconden lang). " +
                                    "Sensorwaarde '${preAlarmResult.sensorValue}', " +
                                    "'${preAlarmResult.expectedInBedValue}' betekent 'in bed'."
                            )
                            shouldFire = false
                            
                            // Clear the result after using it
                            preAlarmStorage.clearResult(alarm.id)
                            
                            // Cancel the pre-alarm check if still pending
                            AlarmScheduler.cancelPreAlarmCheck(context)
                        } else {
                            Log.d(TAG, "[ALARM] Pre-alarm check did NOT cancel alarm - proceeding with normal checks")
                            // Clear the result
                            preAlarmStorage.clearResult(alarm.id)
                        }
                    } else {
                        Log.d(TAG, "[ALARM] No pre-alarm result found - proceeding with normal checks")
                    }
                }
                
                // PHASE 2: If not cancelled by pre-alarm, run normal RuleEngine checks
                if (shouldFire) {
                    alarmItem?.triggerId?.let { triggerId ->
                        Log.d(TAG, "Checking rules for trigger: $triggerId")
                        
                        val storage = TriggerBehaviorStorage(context)
                        val config = storage.getConfig(triggerId)
                        if (config.mode == TriggerBehaviorMode.NORMAL) {
                            Log.d(TAG, "NORMAL mode - standalone simple alarm, skipping Home Assistant RuleEngine")
                            shouldFire = true
                            return@let
                        }
                        
                        val haSettingsStorage = HomeAssistantSettingsStorage(context)
                        val haClient = HomeAssistantClient // Singleton
                        val haRepository = HomeAssistantRepository(haClient, haSettingsStorage)
                        val ruleEngine = RuleEngine(context, haRepository, storage)
                        shouldFire = ruleEngine.shouldFireAlarm(triggerId)
                        Log.d(TAG, "Should fire alarm: $shouldFire")
                        if (!shouldFire) {
                            AgendaAlarmForensics.log(
                                AgendaAlarmForensics.Cat.BLOCK,
                                "GEBLOKKEERD door=RULE_ENGINE mode=${config.mode} trigger=$triggerId " +
                                    "reden=${ruleEngine.lastBlockReason}",
                                context
                            )
                            AgendaAlarmSkipNotifier.notifySkipped(
                                context,
                                alarmItem,
                                ruleEngine.lastBlockReason ?: "een Slim alarm-controle hield het tegen",
                                ruleEngine.lastBlockDetail
                            )
                        }
                        
                        // Markeer als fired voor ONE_TIME mode
                        if (config.mode == TriggerBehaviorMode.ONE_TIME && shouldFire) {
                            ruleEngine.markAlarmAsFired(triggerId)
                            Log.d(TAG, "Marked ONE_TIME alarm as fired")
                        }
                    }
                }
                
                if (shouldFire) {
                    AgendaAlarmPopupForegroundService.stopPopup(context.applicationContext, "main_alarm_firing")
                    SnoozePopupForegroundService.stopPopup(context.applicationContext, "main_alarm_firing")
                    val serviceIntent = Intent(context, AlarmService::class.java).apply {
                        if (intent.extras != null) {
                            putExtras(intent.extras!!)
                        }
                    }

                    AlarmRingingDebug.i(
                        "AlarmReceiver starting AlarmService label=${alarmItem?.label} id=${alarmItem?.id}"
                    )
                    Log.d(TAG, "Starting AlarmService")
                    try {
                        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                        AlarmRingingDebug.i("AlarmReceiver AlarmService startService requested")
                        AgendaAlarmForensics.log(
                            AgendaAlarmForensics.Cat.SERVICE,
                            "startForegroundService(AlarmService) aangevraagd ${AgendaAlarmForensics.describe(alarmItem)}",
                            context
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting AlarmService", e)
                        AlarmRingingDebug.e("AlarmReceiver AlarmService start failed", e)
                        AgendaAlarmForensics.log(
                            AgendaAlarmForensics.Cat.SERVICE,
                            "START MISLUKT ${e.javaClass.simpleName}: ${e.message} — alarm gaat niet af.",
                            context
                        )
                    }
                } else {
                    Log.d(TAG, "Alarm blocked by RuleEngine")
                }

                if (!shouldFire && alarmItem != null) {
                    Log.w(
                        TAG,
                        "ALARM_CHAIN reschedule after skip eventId=${alarmItem.id} epoch=${alarmItem.epochMillis}"
                    )
                    try {
                        AlarmScheduler.scheduleNextAlarm(context.applicationContext, "alarm_receiver_skip_chain")
                    } catch (e: Exception) {
                        Log.e(TAG, "ALARM_CHAIN scheduleNextAlarm after skip failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onReceive", e)
            } finally {
                // Always finish the pending result
                pendingResult.finish()
            }
        }
    }
}
