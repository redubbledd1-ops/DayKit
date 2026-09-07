package com.dd.daykit.rules

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.triggerRulesDataStore: DataStore<Preferences> by preferencesDataStore(name = "trigger_rules")

/**
 * Storage voor trigger regels per calendar ID
 */
class TriggerRulesStorage(private val context: Context) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Haal regels op voor een specifieke trigger (calendar ID)
     */
    fun getRulesFlow(triggerId: String): Flow<List<AlarmRule>> {
        val key = stringPreferencesKey("rules_$triggerId")
        return context.triggerRulesDataStore.data.map { preferences ->
            val jsonString = preferences[key]
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<AlarmRule>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }
    
    /**
     * Sla regels op voor een specifieke trigger
     */
    suspend fun saveRules(triggerId: String, rules: List<AlarmRule>) {
        val key = stringPreferencesKey("rules_$triggerId")
        val jsonString = json.encodeToString(rules)
        context.triggerRulesDataStore.edit { preferences ->
            preferences[key] = jsonString
        }
    }
    
    /**
     * Voeg een regel toe aan een trigger
     */
    suspend fun addRule(triggerId: String, rule: AlarmRule) {
        val key = stringPreferencesKey("rules_$triggerId")
        context.triggerRulesDataStore.edit { preferences ->
            val existing = preferences[key]?.let { 
                try {
                    json.decodeFromString<List<AlarmRule>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            
            val updated = existing + rule
            preferences[key] = json.encodeToString(updated)
        }
    }
    
    /**
     * Update een bestaande regel
     */
    suspend fun updateRule(triggerId: String, ruleId: String, updatedRule: AlarmRule) {
        val key = stringPreferencesKey("rules_$triggerId")
        context.triggerRulesDataStore.edit { preferences ->
            val existing = preferences[key]?.let { 
                try {
                    json.decodeFromString<List<AlarmRule>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            
            val updated = existing.map { if (it.id == ruleId) updatedRule else it }
            preferences[key] = json.encodeToString(updated)
        }
    }
    
    /**
     * Verwijder een regel
     */
    suspend fun deleteRule(triggerId: String, ruleId: String) {
        val key = stringPreferencesKey("rules_$triggerId")
        context.triggerRulesDataStore.edit { preferences ->
            val existing = preferences[key]?.let { 
                try {
                    json.decodeFromString<List<AlarmRule>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            
            val updated = existing.filter { it.id != ruleId }
            preferences[key] = json.encodeToString(updated)
        }
    }
    
    /**
     * Toggle enabled status van een regel
     */
    suspend fun toggleRuleEnabled(triggerId: String, ruleId: String) {
        val key = stringPreferencesKey("rules_$triggerId")
        context.triggerRulesDataStore.edit { preferences ->
            val existing = preferences[key]?.let { 
                try {
                    json.decodeFromString<List<AlarmRule>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            
            val updated = existing.map { 
                if (it.id == ruleId) it.copy(enabled = !it.enabled) else it 
            }
            preferences[key] = json.encodeToString(updated)
        }
    }
}
