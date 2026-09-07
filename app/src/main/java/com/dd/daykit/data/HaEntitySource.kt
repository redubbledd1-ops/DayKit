package com.dd.daykit.data

import kotlinx.coroutines.flow.firstOrNull

/**
 * Centrale interface voor het ophalen van Home Assistant entities
 * Gebruikt door Slim Alarm, Gebruiker thuis check, en Externe speaker selectie
 */
interface HaEntitySource {
    /**
     * Haal gekoppelde entiteiten op (toegevoegd in AgendaAlarm)
     * @return List van entity IDs die in de app zijn geconfigureerd
     */
    suspend fun getLinkedEntityIds(): List<String>
    
    /**
     * Haal alle entities op van Home Assistant via /api/states
     * @return List van HaEntity objecten met alle details
     */
    suspend fun getAllEntities(): List<HaEntity>
    
    /**
     * Haal media_player entities op
     * @param useLinkedOnly Als true, filter gekoppelde entities; anders /api/states
     * @return List van HaEntity objecten met media_player.* entities
     */
    suspend fun getMediaPlayers(useLinkedOnly: Boolean): List<HaEntity>
    
    /**
     * Haal presence entities op
     * @param useLinkedOnly Als true, filter gekoppelde entities; anders /api/states
     * @return List van HaEntity objecten met presence-achtige entities
     */
    suspend fun getPresenceEntities(useLinkedOnly: Boolean): List<HaEntity>
}

/**
 * Implementatie van HaEntitySource
 */
class HaEntitySourceImpl(
    private val settingsStorage: HomeAssistantSettingsStorage,
    private val repository: HomeAssistantRepository
) : HaEntitySource {
    
    override suspend fun getLinkedEntityIds(): List<String> {
        val settings = settingsStorage.settingsFlow.firstOrNull()
        return settings?.entities?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    override suspend fun getAllEntities(): List<HaEntity> {
        return repository.fetchAllEntities()
    }
    
    override suspend fun getMediaPlayers(useLinkedOnly: Boolean): List<HaEntity> {
        return if (useLinkedOnly) {
            // Gebruik gekoppelde entiteiten als bron
            val linkedIds = getLinkedEntityIds()
            linkedIds.mapNotNull { entityId ->
                if (entityId.startsWith("media_player.")) {
                    HaEntity(
                        entityId = entityId,
                        friendlyName = entityId.substringAfter(".").replace("_", " ").replaceFirstChar { it.uppercase() },
                        state = "unknown",
                        domain = "media_player"
                    )
                } else null
            }
        } else {
            // Haal alle media_player entities op uit HA
            repository.fetchMediaPlayers()
        }
    }
    
    override suspend fun getPresenceEntities(useLinkedOnly: Boolean): List<HaEntity> {
        return if (useLinkedOnly) {
            // Gebruik gekoppelde entiteiten als bron
            val linkedIds = getLinkedEntityIds()
            linkedIds.mapNotNull { entityId ->
                if (isPresenceEntity(entityId)) {
                    HaEntity(
                        entityId = entityId,
                        friendlyName = entityId.substringAfter(".").replace("_", " ").replaceFirstChar { it.uppercase() },
                        state = "unknown",
                        domain = entityId.substringBefore(".")
                    )
                } else null
            }
        } else {
            // Haal alle entities op uit HA en filter op presence types
            val allEntities = getAllEntities()
            allEntities.filter { entity -> isPresenceEntity(entity.entityId) }
        }
    }
    
    private fun isPresenceEntity(entityId: String): Boolean {
        return entityId.startsWith("binary_sensor.") ||
               entityId.startsWith("person.") ||
               entityId.startsWith("device_tracker.") ||
               entityId.startsWith("input_boolean.") ||
               entityId.startsWith("zone.")
    }
}
