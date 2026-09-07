package com.dd.daykit.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dd.daykit.data.HomeAssistantSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

data class TriggerRulesUiState(
    val triggerId: String,
    val triggerName: String,
    val rules: List<AlarmRule> = emptyList(),
    val availableTemplates: List<RuleTemplate> = emptyList(),
    val isLoading: Boolean = true
)

class TriggerRulesViewModel(
    private val triggerId: String,
    private val triggerName: String,
    private val rulesStorage: TriggerRulesStorage,
    private val haSettingsStorage: HomeAssistantSettingsStorage
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TriggerRulesUiState(
        triggerId = triggerId,
        triggerName = triggerName
    ))
    val uiState: StateFlow<TriggerRulesUiState> = _uiState.asStateFlow()
    
    init {
        loadRules()
        loadTemplates()
    }
    
    /**
     * Laad regels voor deze trigger
     */
    private fun loadRules() {
        viewModelScope.launch {
            rulesStorage.getRulesFlow(triggerId).collect { rules ->
                _uiState.value = _uiState.value.copy(
                    rules = rules,
                    isLoading = false
                )
            }
        }
    }
    
    /**
     * Laad beschikbare templates met HA sensor data
     */
    private fun loadTemplates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                availableTemplates = RuleTemplates.ALL_TEMPLATES
            )
        }
    }
    
    /**
     * Voeg een regel toe vanuit een template
     * @param entityId Optioneel: specifieke entiteit voor templates die dat vereisen
     * @param stateValue Optioneel: gewenste state waarde
     */
    fun addRuleFromTemplate(template: RuleTemplate, entityId: String? = null, stateValue: String? = null) {
        viewModelScope.launch {
            // Maak condities op basis van template type
            val conditions = if (template.requiresEntitySelection && entityId != null && stateValue != null) {
                // Generieke entiteit conditie
                listOf(
                    HaCondition(
                        entityId = entityId,
                        comparison = Comparison.EQUALS,
                        value = stateValue,
                        description = "$entityId is '$stateValue'"
                    )
                )
            } else {
                // Geen condities nodig (bijv. "één keer afspelen")
                emptyList()
            }
            
            // Maak nieuwe regel
            val newRule = AlarmRule(
                id = UUID.randomUUID().toString(),
                templateId = template.id,
                enabled = true,
                actionType = template.actionType,
                conditions = conditions,
                title = template.title,
                description = template.description
            )
            
            rulesStorage.addRule(triggerId, newRule)
        }
    }
    
    /**
     * Toggle enabled status van een regel
     */
    fun toggleRule(ruleId: String) {
        viewModelScope.launch {
            rulesStorage.toggleRuleEnabled(triggerId, ruleId)
        }
    }
    
    /**
     * Verwijder een regel
     */
    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            rulesStorage.deleteRule(triggerId, ruleId)
        }
    }
    
    /**
     * Sla alle wijzigingen op (wordt automatisch gedaan via Flow, maar kan expliciet aangeroepen worden)
     */
    fun saveChanges() {
        // Changes worden automatisch opgeslagen via de storage functies
        // Deze functie is hier voor expliciete save acties indien nodig
    }
}

/**
 * Factory voor TriggerRulesViewModel
 */
class TriggerRulesViewModelFactory(
    private val triggerId: String,
    private val triggerName: String,
    private val rulesStorage: TriggerRulesStorage,
    private val haSettingsStorage: HomeAssistantSettingsStorage
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TriggerRulesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TriggerRulesViewModel(triggerId, triggerName, rulesStorage, haSettingsStorage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
