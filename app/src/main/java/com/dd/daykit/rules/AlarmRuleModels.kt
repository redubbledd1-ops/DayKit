package com.dd.daykit.rules

import kotlinx.serialization.Serializable

/**
 * Vergelijkingsoperatoren voor Home Assistant condities
 */
@Serializable
enum class Comparison {
    EQUALS,         // ==
    NOT_EQUALS,     // !=
    GREATER_THAN,   // >
    LESS_THAN       // <
}

/**
 * Een conditie gebaseerd op een Home Assistant entity
 */
@Serializable
data class HaCondition(
    val entityId: String,           // bijv. "binary_sensor.presence_sensor"
    val comparison: Comparison,      // hoe te vergelijken
    val value: String,              // verwachte waarde: "on", "off", "1000", etc.
    val description: String         // menselijk leesbare beschrijving voor UI
)

/**
 * Type actie die een regel uitvoert
 */
@Serializable
enum class RuleActionType {
    SKIP_ALARM,                     // Sla de trigger over
    ONLY_ONCE_PER_DAY,             // Max 1x per dag
    ALLOW_IF_CONDITIONS_TRUE       // Alleen afspelen als condities waar zijn
}

/**
 * Een regel die bepaalt of een alarm afgaat
 */
@Serializable
data class AlarmRule(
    val id: String,                     // Uniek ID per regel
    val templateId: String?,            // Verwijzing naar template (optioneel)
    val enabled: Boolean,               // Is deze regel actief?
    val actionType: RuleActionType,     // Wat doet deze regel?
    val conditions: List<HaCondition>,  // Lijst met HA condities
    val title: String,                  // Titel voor UI
    val description: String             // Beschrijving voor UI
)

/**
 * Template voor voorgedefinieerde regels
 */
data class RuleTemplate(
    val id: String,
    val title: String,
    val description: String,
    val actionType: RuleActionType,
    val requiresEntitySelection: Boolean = false, // Moet gebruiker een entiteit kiezen?
    val conditionsBuilder: (List<String>) -> List<HaCondition> = { emptyList() } // Functie die condities maakt o.b.v. entity IDs
)

/**
 * Voorgedefinieerde rule templates
 */
object RuleTemplates {
    
    val SKIP_IF_ENTITY_EQUALS = RuleTemplate(
        id = "skip_if_entity_equals",
        title = "Sla deze trigger over als een entiteit een bepaalde waarde heeft",
        description = "Kies een Home Assistant entiteit en een waarde (bijv. 'on'). Als deze entiteit die waarde heeft op het moment van de trigger, wordt de trigger overgeslagen.",
        actionType = RuleActionType.SKIP_ALARM,
        requiresEntitySelection = true
    )
    
    val PLAY_ONLY_ONCE = RuleTemplate(
        id = "play_only_once",
        title = "Speel dit alarm maar één keer af",
        description = "Na het afspelen gaat deze trigger niet nog een keer af.",
        actionType = RuleActionType.ONLY_ONCE_PER_DAY
    )
    
    /**
     * Alle beschikbare templates
     */
    val ALL_TEMPLATES = listOf(
        SKIP_IF_ENTITY_EQUALS,
        PLAY_ONLY_ONCE
    )
}
