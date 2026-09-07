package com.dd.daykit.sound

/**
 * Domain model representing an alarm sound (custom or system)
 * Simplified model for UI consumption
 */
data class Sound(
    val id: Long,
    val name: String,
    val filePath: String,
    val duration: Long, // in milliseconds
    val isSystemSound: Boolean = false, // true for system ringtones, false for custom sounds
    val uri: String? = null // URI for system sounds (e.g., content://media/...)
)
