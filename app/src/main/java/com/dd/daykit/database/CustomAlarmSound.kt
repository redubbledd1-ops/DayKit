package com.dd.daykit.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a custom alarm sound uploaded by the user
 */
@Entity(tableName = "custom_alarm_sounds")
data class CustomAlarmSound(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Display name shown to user */
    val displayName: String,
    
    /** Original filename */
    val originalFilename: String,
    
    /** Stored filename in app directory */
    val storedFilename: String,
    
    /** Original URI (for reference) */
    val originalUri: String,
    
    /** Duration in milliseconds */
    val durationMs: Long,
    
    /** File size in bytes */
    val fileSizeBytes: Long,
    
    /** MIME type */
    val mimeType: String,
    
    /** Timestamp when added */
    val addedTimestamp: Long = System.currentTimeMillis(),

    /** HA-reachable URL (/local/daykit_sounds/<naam>) after a successful upload via
     * SoundHaSync.uploadSoundToHa, or null if never uploaded/upload failed. Backed up/restored
     * so a restore can re-download the file from HA without re-uploading it first. */
    val haSoundUrl: String? = null
)
