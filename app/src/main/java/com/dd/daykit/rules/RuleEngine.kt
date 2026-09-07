package com.dd.daykit.rules

import android.content.Context
import android.util.Log
import com.dd.daykit.data.HaConnectionResult
import com.dd.daykit.data.HomeAssistantRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Engine voor het evalueren van alarm regels op basis van TriggerBehaviorMode
 */
class RuleEngine(
    private val context: Context,
    private val haRepository: HomeAssistantRepository,
    private val behaviorStorage: TriggerBehaviorStorage
) {
    
    companion object {
        private const val TAG = "RuleEngine"
        private const val OUT_OF_BED_CACHE_PREFS = "out_of_bed_cache"
        private const val OUT_OF_BED_CACHE_MAX_AGE_MS = 10 * 60 * 1000L
    }

    /**
     * Korte reden waarom [shouldFireAlarm] als laatste `false` teruggaf, plus de waarden die de
     * doorslag gaven. Alleen een boolean teruggeven was niet genoeg: de aanroeper moet de gebruiker
     * kunnen vertellen wélke check het alarm tegenhield, anders blijft het blokkeren onzichtbaar.
     * Per-instantie (RuleEngine wordt per aanroep aangemaakt), dus geen gedeelde state.
     */
    var lastBlockReason: String? = null
        private set

    var lastBlockDetail: String? = null
        private set

    private fun markBlocked(reason: String, detail: String) {
        lastBlockReason = reason
        lastBlockDetail = detail
    }

    private fun normalizeState(value: String): String = value.trim().lowercase()

    private fun isUnreliableState(value: String): Boolean {
        val v = normalizeState(value)
        return v.isBlank() || v == "unknown" || v == "unavailable" || v == "none"
    }

    private fun bedStateKey(entityId: String) = "bed_state_$entityId"
    private fun bedStateTimeKey(entityId: String) = "bed_state_time_$entityId"

    private fun cacheBedState(entityId: String, normalizedState: String) {
        try {
            context.getSharedPreferences(OUT_OF_BED_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(bedStateKey(entityId), normalizedState)
                .putLong(bedStateTimeKey(entityId), System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "[SLIM ALARM] Error caching bed state: ${e.message}", e)
        }
    }

    private fun getCachedBedState(entityId: String): Pair<String, Long>? {
        return try {
            val prefs = context.getSharedPreferences(OUT_OF_BED_CACHE_PREFS, Context.MODE_PRIVATE)
            val state = prefs.getString(bedStateKey(entityId), null) ?: return null
            val time = prefs.getLong(bedStateTimeKey(entityId), 0L)
            if (time <= 0L) return null
            Pair(state, time)
        } catch (e: Exception) {
            Log.e(TAG, "[SLIM ALARM] Error reading cached bed state: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchEntityStateNormalized(entityId: String, attempts: Int, timeoutMs: Long): String? {
        var lastError: Throwable? = null
        repeat(attempts) { attemptIndex ->
            try {
                val response = withTimeoutOrNull(timeoutMs) {
                    haRepository.getEntityState(entityId)
                }
                if (response != null) {
                    return normalizeState(response.state)
                }
            } catch (e: Exception) {
                lastError = e
            }

            if (attemptIndex < attempts - 1) {
                delay(250)
            }
        }

        if (lastError != null) {
            Log.e(TAG, "[SLIM ALARM] Error fetching entity state for $entityId: ${lastError?.message}", lastError)
        }
        return null
    }
    
    /**
     * Bepaal of een alarm mag afgaan op basis van de TriggerBehaviorMode
     * 
     * @param triggerId ID van de trigger
     * @return true als het alarm mag afgaan, false als het overgeslagen moet worden
     */
    suspend fun shouldFireAlarm(triggerId: String): Boolean {
        val config = behaviorStorage.getConfig(triggerId)
        Log.d(TAG, "shouldFireAlarm - triggerId: $triggerId, mode: ${config.mode}")
        
        return when (config.mode) {
            TriggerBehaviorMode.NORMAL -> {
                // Standaard: alarm gaat altijd af
                // GEEN Home Assist checks, GEEN uit bed check
                Log.d(TAG, "NORMAL mode - alarm will ALWAYS fire (ignores all HA checks)")
                true
            }
            
            TriggerBehaviorMode.SMART_ALARM -> {
                // =====================================================================
                // SLIM ALARM LOGIC - Vereenvoudigd en robuust
                // 
                // Dit alarm controleert:
                // 1. HA Connectie (verplicht)
                // 2. Thuis detectie (optioneel - als ingeschakeld in smartConfig)
                // 3. Uit bed check (optioneel - als ingeschakeld in HA Settings)
                // 4. Smart condition entity (optioneel - als geconfigureerd in smartConfig)
                //
                // BELANGRIJK: Uit bed check werkt ONAFHANKELIJK van smart condition entity!
                // =====================================================================
                
                Log.d(TAG, "=".repeat(70))
                Log.d(TAG, "[SLIM ALARM] shouldFireAlarm - triggerId: $triggerId, mode: SMART_ALARM")
                
                val smartConfig = config.smartConfig
                val haSettings = haRepository.getSettings()

                com.dd.daykit.AgendaAlarmForensics.log(
                    com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                    "SLIM ALARM start trigger=$triggerId " +
                        "uitBedCheck=${if (haSettings.outOfBedCheckEnabled) "AAN(${haSettings.outOfBedEntityId}, inBed='${haSettings.outOfBedExpectedValue}')" else "UIT"} " +
                        "thuisCheck=${if (smartConfig?.checkUserAtHome == true) "AAN(${smartConfig.userPresenceEntityId})" else "UIT"} " +
                        "smartEntity=${smartConfig?.entityId?.takeIf { it.isNotBlank() } ?: "geen"}",
                    context
                )

                // Log what's configured
                Log.d(TAG, "[SLIM ALARM] Configuration:")
                Log.d(TAG, "[SLIM ALARM]   - SmartConfig: ${if (smartConfig != null) "present" else "null"}")
                Log.d(TAG, "[SLIM ALARM]   - Uit bed check: ${if (haSettings.outOfBedCheckEnabled) "ENABLED (${haSettings.outOfBedEntityId})" else "DISABLED"}")
                if (smartConfig != null) {
                    Log.d(TAG, "[SLIM ALARM]   - Thuis detectie: ${if (smartConfig.checkUserAtHome) "ENABLED (${smartConfig.userPresenceEntityId})" else "DISABLED"}")
                    Log.d(TAG, "[SLIM ALARM]   - Smart entity: ${if (smartConfig.entityId.isNotBlank()) smartConfig.entityId else "NIET GECONFIGUREERD"}")
                }
                
                // =====================================================================
                // STAP 1: Test HA connectie
                // =====================================================================
                Log.d(TAG, "[SLIM ALARM] Stap 1: HA connectie testen...")
                val connectionResult = withTimeoutOrNull(3000) {
                    haRepository.testConnection()
                }
                
                if (connectionResult == null) {
                    Log.e(TAG, "[SLIM ALARM] ❌ HA connectie TIMEOUT (3s)")
                    Log.e(TAG, "[SLIM ALARM] ✅ FALLBACK → ALARM GAAT AF")
                    Log.d(TAG, "=".repeat(70))
                    com.dd.daykit.AgendaAlarmForensics.log(
                        com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                        "STAP1 HA_TIMEOUT (3s) → fail-open, alarm mag afgaan",
                        context
                    )
                    return true
                } else if (connectionResult is HaConnectionResult.Error) {
                    Log.e(TAG, "[SLIM ALARM] ❌ HA connectie MISLUKT: ${connectionResult.message}")
                    Log.e(TAG, "[SLIM ALARM] ✅ FALLBACK → ALARM GAAT AF")
                    Log.d(TAG, "=".repeat(70))
                    com.dd.daykit.AgendaAlarmForensics.log(
                        com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                        "STAP1 HA_ONBEREIKBAAR (${connectionResult.message}) → fail-open, alarm mag afgaan",
                        context
                    )
                    return true
                }
                Log.d(TAG, "[SLIM ALARM] ✅ HA connectie OK")

                // =====================================================================
                // STAP 2: Thuis detectie (indien ingeschakeld)
                // Als gebruiker NIET thuis is → sla uit bed check over, alarm gaat af
                // =====================================================================
                var isUserAtHome: Boolean? = null
                val presenceCheckEnabled = smartConfig?.checkUserAtHome == true && 
                                           !smartConfig.userPresenceEntityId.isNullOrBlank()
                
                if (presenceCheckEnabled) {
                    Log.d(TAG, "[SLIM ALARM] Stap 2: Thuis detectie...")
                    Log.d(TAG, "[SLIM ALARM]   - Entity: ${smartConfig!!.userPresenceEntityId}")
                    Log.d(TAG, "[SLIM ALARM]   - Verwachte waarde (thuis): '${smartConfig.userPresenceExpectedValue}'")
                    
                    try {
                        val presenceState = withTimeoutOrNull(2000) {
                            haRepository.getEntityState(smartConfig.userPresenceEntityId)
                        }
                        
                        if (presenceState == null) {
                            Log.e(TAG, "[SLIM ALARM] ❌ Thuis detectie TIMEOUT")
                            Log.e(TAG, "[SLIM ALARM] ✅ FALLBACK → ALARM GAAT AF")
                            Log.d(TAG, "=".repeat(70))
                            return true
                        }
                        
                        val actualState = normalizeState(presenceState.state)
                        val expectedState = normalizeState(smartConfig.userPresenceExpectedValue)
                        isUserAtHome = actualState == expectedState
                        
                        Log.d(TAG, "[SLIM ALARM]   - Huidige waarde: '$actualState'")
                        
                        if (isUserAtHome == false) {
                            // Gebruiker is NIET thuis → alarm gaat gewoon af
                            // (uit bed check wordt overgeslagen want gebruiker is niet thuis)
                            Log.w(TAG, "[SLIM ALARM] ❌ Gebruiker is NIET thuis")
                            Log.w(TAG, "[SLIM ALARM] ✅ Uit bed check overgeslagen → ALARM GAAT AF")
                            Log.d(TAG, "=".repeat(70))
                            return true
                        }
                        Log.d(TAG, "[SLIM ALARM] ✅ Gebruiker IS thuis")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "[SLIM ALARM] ❌ Fout bij thuis detectie: ${e.message}", e)
                        Log.e(TAG, "[SLIM ALARM] ✅ FALLBACK → ALARM GAAT AF")
                        Log.d(TAG, "=".repeat(70))
                        return true
                    }
                } else {
                    Log.d(TAG, "[SLIM ALARM] Stap 2: Thuis detectie OVERGESLAGEN (niet ingeschakeld)")
                }
                
                // =====================================================================
                // STAP 3: Uit bed check (indien ingeschakeld)
                // Dit is de KERN van slim alarm - als gebruiker uit bed is, GEEN alarm
                // =====================================================================
                var isUserInBed: Boolean? = null
                val outOfBedCheckEnabled = haSettings.outOfBedCheckEnabled && 
                                           !haSettings.outOfBedEntityId.isNullOrBlank()
                
                if (outOfBedCheckEnabled) {
                    Log.d(TAG, "[SLIM ALARM] Stap 3: Uit bed check...")
                    Log.d(TAG, "[SLIM ALARM]   - Entity: ${haSettings.outOfBedEntityId}")
                    Log.d(TAG, "[SLIM ALARM]   - Waarde als IN bed: '${haSettings.outOfBedExpectedValue}'")
                    
                    try {
                        val bedEntityId = haSettings.outOfBedEntityId!!
                        val inBedValue = normalizeState(haSettings.outOfBedExpectedValue)
                        
                        val fetchedBedState = fetchEntityStateNormalized(
                            entityId = bedEntityId,
                            attempts = 2,
                            timeoutMs = 1500
                        )
                        
                        if (fetchedBedState != null && !isUnreliableState(fetchedBedState)) {
                            cacheBedState(bedEntityId, fetchedBedState)
                            Log.d(TAG, "[SLIM ALARM]   - Huidige waarde: '$fetchedBedState'")
                            
                            isUserInBed = fetchedBedState == inBedValue
                            
                            if (isUserInBed == false) {
                                // 🎯 KERNLOGICA: Gebruiker is UIT bed → GEEN ALARM
                                Log.w(TAG, "[SLIM ALARM] 🚫 Gebruiker is UIT BED")
                                Log.w(TAG, "[SLIM ALARM] 🚫 ALARM GEBLOKKEERD (gebruiker is al wakker)")
                                Log.d(TAG, "=".repeat(70))
                                com.dd.daykit.AgendaAlarmForensics.log(
                                    com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                                    "STAP3 UIT_BED → ALARM GEBLOKKEERD. entity=$bedEntityId " +
                                        "gelezenWaarde='$fetchedBedState' verwachtInBed='$inBedValue' (live uitgelezen)",
                                    context
                                )
                                markBlocked(
                                    "de uit-bed-check zag je als 'uit bed'",
                                    "Entiteit $bedEntityId stond op '$fetchedBedState', terwijl " +
                                        "'$inBedValue' is ingesteld als de waarde die 'in bed' betekent."
                                )
                                return false  // ← ALARM GAAT NIET AF
                            }
                            Log.d(TAG, "[SLIM ALARM] ✅ Gebruiker ligt IN bed")
                            com.dd.daykit.AgendaAlarmForensics.log(
                                com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                                "STAP3 IN_BED → niet geblokkeerd. entity=$bedEntityId " +
                                    "gelezenWaarde='$fetchedBedState' verwachtInBed='$inBedValue' (live)",
                                context
                            )

                        } else {
                            // Kan bed status niet bepalen → probeer cache
                            Log.w(TAG, "[SLIM ALARM] ⚠️ Kan bed status niet ophalen")
                            com.dd.daykit.AgendaAlarmForensics.log(
                                com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                                "STAP3 LIVE_LEZING_MISLUKT entity=$bedEntityId gelezen='$fetchedBedState' " +
                                    "→ valt terug op cache (tot 10 min oud)",
                                context
                            )
                            
                            val cached = getCachedBedState(bedEntityId)
                            if (cached != null) {
                                val (cachedState, cachedTime) = cached
                                val ageMs = System.currentTimeMillis() - cachedTime
                                
                                if (ageMs in 1..OUT_OF_BED_CACHE_MAX_AGE_MS && !isUnreliableState(cachedState)) {
                                    Log.d(TAG, "[SLIM ALARM]   - Gebruik cache: '$cachedState' (${ageMs/1000}s oud)")
                                    isUserInBed = normalizeState(cachedState) == inBedValue
                                    
                                    if (isUserInBed == false) {
                                        Log.w(TAG, "[SLIM ALARM] 🚫 Gebruiker is UIT BED (cache)")
                                        Log.w(TAG, "[SLIM ALARM] 🚫 ALARM GEBLOKKEERD")
                                        Log.d(TAG, "=".repeat(70))
                                        com.dd.daykit.AgendaAlarmForensics.log(
                                            com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                                            "STAP3 UIT_BED_UIT_CACHE → ALARM GEBLOKKEERD. entity=$bedEntityId " +
                                                "cacheWaarde='$cachedState' leeftijdMs=$ageMs verwachtInBed='$inBedValue' " +
                                                "(HA gaf NIETS terug; besluit op basis van oude waarde)",
                                            context
                                        )
                                        markBlocked(
                                            "de uit-bed-check zag je als 'uit bed' (oude waarde)",
                                            "Home Assistant gaf op dat moment niets terug. Er is besloten " +
                                                "op een opgeslagen waarde van ${ageMs / 1000} seconden oud: " +
                                                "$bedEntityId stond op '$cachedState', terwijl '$inBedValue' " +
                                                "'in bed' betekent."
                                        )
                                        return false
                                    }
                                    Log.d(TAG, "[SLIM ALARM] ✅ Gebruiker ligt IN bed (cache)")
                                } else {
                                    Log.w(TAG, "[SLIM ALARM] ⚠️ Cache te oud of onbetrouwbaar → ALARM GAAT AF")
                                }
                            } else {
                                Log.w(TAG, "[SLIM ALARM] ⚠️ Geen cache beschikbaar → ALARM GAAT AF")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[SLIM ALARM] ❌ Fout bij uit bed check: ${e.message}", e)
                        Log.w(TAG, "[SLIM ALARM] ⚠️ FAIL-SAFE → ALARM GAAT AF")
                    }
                } else {
                    Log.d(TAG, "[SLIM ALARM] Stap 3: Uit bed check OVERGESLAGEN (niet ingeschakeld)")
                }
                
                // =====================================================================
                // STAP 4: Smart condition entity (optioneel)
                // Als een smart entity is geconfigureerd, check of waarde klopt
                // =====================================================================
                val smartEntityConfigured = smartConfig != null && 
                                            smartConfig.entityId.isNotBlank() && 
                                            smartConfig.expectedValue.isNotBlank()
                
                if (smartEntityConfigured) {
                    Log.d(TAG, "[SLIM ALARM] Stap 4: Smart condition entity...")
                    Log.d(TAG, "[SLIM ALARM]   - Entity: ${smartConfig!!.entityId}")
                    Log.d(TAG, "[SLIM ALARM]   - Verwachte waarde: '${smartConfig.expectedValue}'")
                    
                    try {
                        val entityState = withTimeoutOrNull(2000) {
                            haRepository.getEntityState(smartConfig.entityId)
                        }
                        
                        if (entityState == null) {
                            Log.e(TAG, "[SLIM ALARM] ❌ Smart entity TIMEOUT")
                            Log.e(TAG, "[SLIM ALARM] ✅ FALLBACK → ALARM GAAT AF")
                            Log.d(TAG, "=".repeat(70))
                            return true
                        }
                        
                        val actualState = entityState.state
                        val matches = actualState == smartConfig.expectedValue
                        
                        Log.d(TAG, "[SLIM ALARM]   - Huidige waarde: '$actualState'")
                        
                        if (!matches) {
                            Log.w(TAG, "[SLIM ALARM] ❌ Conditie NIET voldaan")
                            Log.w(TAG, "[SLIM ALARM] 🚫 ALARM GEBLOKKEERD")
                            Log.d(TAG, "=".repeat(70))
                            com.dd.daykit.AgendaAlarmForensics.log(
                                com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                                "STAP4 SMART_CONDITIE_NIET_VOLDAAN → ALARM GEBLOKKEERD. " +
                                    "entity=${smartConfig.entityId} gelezenWaarde='$actualState' " +
                                    "verwacht='${smartConfig.expectedValue}'",
                                context
                            )
                            markBlocked(
                                "de smart-conditie was niet voldaan",
                                "Entiteit ${smartConfig.entityId} stond op '$actualState', " +
                                    "terwijl '${smartConfig.expectedValue}' verwacht werd."
                            )
                            return false
                        }
                        Log.d(TAG, "[SLIM ALARM] ✅ Conditie voldaan")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "[SLIM ALARM] ❌ Fout bij smart entity check: ${e.message}", e)
                        Log.w(TAG, "[SLIM ALARM] ⚠️ FALLBACK → ALARM GAAT AF")
                    }
                } else {
                    Log.d(TAG, "[SLIM ALARM] Stap 4: Smart entity OVERGESLAGEN (niet geconfigureerd)")
                }
                
                // =====================================================================
                // RESULTAAT: Alle checks doorstaan → ALARM GAAT AF
                // =====================================================================
                Log.d(TAG, "[SLIM ALARM] ✅ Alle checks doorstaan → ALARM GAAT AF")
                Log.d(TAG, "=".repeat(70))
                Log.i(TAG, "SLIM ALARM SAMENVATTING - triggerId: $triggerId")
                Log.i(TAG, "  HA Connectie: ✓ OK")
                if (presenceCheckEnabled) {
                    Log.i(TAG, "  Thuis Detectie: ✓ Gebruiker is thuis")
                }
                if (outOfBedCheckEnabled) {
                    Log.i(TAG, "  Uit Bed Check: ✓ Gebruiker ligt in bed")
                }
                if (smartEntityConfigured) {
                    Log.i(TAG, "  Smart Conditie: ✓ Voldaan")
                }
                Log.i(TAG, "  → RESULTAAT: ALARM GAAT AF ✓")
                Log.d(TAG, "=".repeat(70))
                com.dd.daykit.AgendaAlarmForensics.log(
                    com.dd.daykit.AgendaAlarmForensics.Cat.RULE,
                    "ALLE CHECKS DOORSTAAN → alarm mag afgaan. " +
                        "thuisCheck=${if (presenceCheckEnabled) "ok" else "n.v.t."} " +
                        "uitBedCheck=${if (outOfBedCheckEnabled) "in bed" else "n.v.t."} " +
                        "smartConditie=${if (smartEntityConfigured) "voldaan" else "n.v.t."}",
                    context
                )
                true
            }
            
            TriggerBehaviorMode.ONE_TIME -> {
                // Check of alarm al is afgevuurd
                // GEEN Home Assist checks, GEEN uit bed check
                // Alarm gaat 1x af, ongeacht Home Assist status
                val shouldFire = !config.alreadyFired
                Log.d(TAG, "ONE_TIME mode - alreadyFired: ${config.alreadyFired}, shouldFire: $shouldFire (ignores all HA checks)")
                shouldFire
            }
        }
    }
    
    /**
     * Geeft terug of het alarm onbeperkt moet snoozen.
     * Dit wordt gebruikt voor de fallback logica.
     */
    suspend fun shouldUseFallbackSnooze(triggerId: String): Boolean {
        val config = behaviorStorage.getConfig(triggerId)
        if (config.mode == TriggerBehaviorMode.SMART_ALARM) {
             // 1. Check connection failure
             val connectionResult = haRepository.testConnection()
             if (connectionResult is HaConnectionResult.Error) {
                 return true // Fallback active -> unlimited snooze
             }
             
             val smartConfig = config.smartConfig ?: return false // Should not happen if mode is SMART_ALARM really, but safe default

             // 2. Check User Presence failure (user NOT home or error)
             if (smartConfig.checkUserAtHome) {
                 if (smartConfig.userPresenceEntityId.isBlank()) return true // Config error -> fallback
                 
                 try {
                     val userStateResponse = haRepository.getEntityState(smartConfig.userPresenceEntityId)
                     val actualState = userStateResponse.state.lowercase()
                     val expectedState = smartConfig.userPresenceExpectedValue.lowercase()
                     val isUserAtHome = actualState == expectedState
                     
                     if (!isUserAtHome) {
                         return true // User not home -> Fallback active
                     }
                 } catch (e: Exception) {
                     return true // Error checking user -> Fallback active
                 }
             }

             // 3. Check specific entity fetch failure
             // Als de specifieke entity niet opgehaald kan worden, is het ook een fallback scenario.
             // Een lege entityId betekent "geen smart-conditie ingesteld" en is dus géén storing.
             if (smartConfig.entityId.isNotBlank()) {
                 try {
                     haRepository.getEntityState(smartConfig.entityId)
                 } catch (e: Exception) {
                     return true // Error fetching specific entity -> Fallback active
                 }
             }
        }
        return false
    }
    
    /**
     * Markeer een alarm als afgevuurd (voor ONE_TIME modus)
     */
    suspend fun markAlarmAsFired(triggerId: String) {
        behaviorStorage.markAsFired(triggerId)
    }
    
    /**
     * Check of handmatig dismiss is toegestaan voor SMART_ALARM modus
     */
    suspend fun canManuallyDismiss(triggerId: String): Boolean {
        val config = behaviorStorage.getConfig(triggerId)
        
        if (config.mode != TriggerBehaviorMode.SMART_ALARM) {
            return true // Andere modi: altijd toestaan
        }
        
        // Fallback check 1: als HA niet bereikbaar is
        val connectionResult = haRepository.testConnection()
        if (connectionResult is HaConnectionResult.Error) {
            return true
        }
        
        val smartConfig = config.smartConfig ?: return true
        
        // Fallback check 2: Check user presence failure/absence
        if (smartConfig.checkUserAtHome) {
             try {
                 val userStateResponse = haRepository.getEntityState(smartConfig.userPresenceEntityId)
                 val actualState = userStateResponse.state.lowercase()
                 val expectedState = smartConfig.userPresenceExpectedValue.lowercase()
                 val isUserAtHome = actualState == expectedState
                 if (!isUserAtHome) return true // Fallback mode -> allow dismiss
             } catch (e: Exception) {
                 return true // Error -> Fallback -> allow dismiss
             }
        }

        if (!smartConfig.preventManualDismiss) {
            return true // Prevent dismiss niet actief
        }
        
        // Check of entiteit nog steeds de verwachte waarde heeft
        try {
            val entityState = haRepository.getEntityState(smartConfig.entityId)
            // Dismiss alleen toestaan als state NIET meer overeenkomt
            return entityState.state != smartConfig.expectedValue
        } catch (e: Exception) {
            return true // Bij fout (fallback): toestaan
        }
    }
    
    /**
     * Evalueer een lijst met condities
     * @return true als ten minste één conditie waar is (OR logica)
     */
    private suspend fun evaluateConditions(conditions: List<HaCondition>): Boolean {
        if (conditions.isEmpty()) return false
        
        return coroutineScope {
            // Evalueer alle condities parallel
            val results = conditions.map { condition ->
                async {
                    evaluateCondition(condition)
                }
            }.awaitAll()
            
            // OR logica: als ten minste één conditie waar is
            results.any { it }
        }
    }
    
    /**
     * Evalueer een enkele conditie
     */
    private suspend fun evaluateCondition(condition: HaCondition): Boolean {
        return try {
            val state = haRepository.getEntityState(condition.entityId)
            
            when (condition.comparison) {
                Comparison.EQUALS -> state.state == condition.value
                Comparison.NOT_EQUALS -> state.state != condition.value
                Comparison.GREATER_THAN -> {
                    val stateValue = state.state.toDoubleOrNull()
                    val conditionValue = condition.value.toDoubleOrNull()
                    if (stateValue != null && conditionValue != null) {
                        stateValue > conditionValue
                    } else {
                        false
                    }
                }
                Comparison.LESS_THAN -> {
                    val stateValue = state.state.toDoubleOrNull()
                    val conditionValue = condition.value.toDoubleOrNull()
                    if (stateValue != null && conditionValue != null) {
                        stateValue < conditionValue
                    } else {
                        false
                    }
                }
            }
        } catch (e: Exception) {
            // Bij fout (bijv. entity niet bereikbaar), beschouwen we conditie als false
            false
        }
    }
    
    /**
     * Test functie om te zien welke regels actief zijn
     * @return Lijst met regel IDs die zouden triggeren
     */
    suspend fun getActiveRules(rules: List<AlarmRule>): List<String> {
        val activeRules = mutableListOf<String>()
        
        for (rule in rules.filter { it.enabled }) {
            when (rule.actionType) {
                RuleActionType.SKIP_ALARM -> {
                    if (evaluateConditions(rule.conditions)) {
                        activeRules.add(rule.id)
                    }
                }
                RuleActionType.ALLOW_IF_CONDITIONS_TRUE -> {
                    if (evaluateConditions(rule.conditions)) {
                        activeRules.add(rule.id)
                    }
                }
                RuleActionType.ONLY_ONCE_PER_DAY -> {
                    // TODO: Check of alarm vandaag al afging
                    activeRules.add(rule.id)
                }
            }
        }
        
        return activeRules
    }
}
