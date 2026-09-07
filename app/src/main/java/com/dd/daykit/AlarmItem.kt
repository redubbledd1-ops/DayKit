package com.dd.daykit

import kotlinx.serialization.Serializable

@Serializable
data class AlarmItem(
    val id: Long,
    val epochMillis: Long,
    val label: String,
    val triggerId: String? = null, // Calendar ID van de trigger
    val soundUri: String? = null,
    /** Blijft gezet over sluimerketen (agenda-trigger) terwijl [triggerId] null blijft op sluimer-AlarmItems. */
    val snoozeSourceTriggerId: String? = null,
)
