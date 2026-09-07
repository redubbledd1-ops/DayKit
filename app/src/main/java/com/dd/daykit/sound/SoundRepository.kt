package com.dd.daykit.sound

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.dd.daykit.CustomSoundManager
import com.dd.daykit.SettingsManager
import com.dd.daykit.database.AppDatabase
import com.dd.daykit.database.CustomAlarmSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Repository for managing custom alarm sounds
 * Provides StateFlow for reactive UI updates
 * Reads from context.getExternalFilesDir("alarm_sounds")/custom
 */
class SoundRepository(private val context: Context) {
    
    private val soundManager = CustomSoundManager(context)
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.customAlarmSoundDao()
    
    // Repository scope for background operations
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // StateFlow for sounds list
    private val _sounds = MutableStateFlow<List<Sound>>(emptyList())
    val sounds: StateFlow<List<Sound>> = _sounds.asStateFlow()
    
    // StateFlow for loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // StateFlow for error messages
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    companion object {
        private const val TAG = "SoundRepository"

        // Reserved id for the bundled default sound entry (system ringtones use negative ids,
        // custom sounds use positive Room-generated ids, so 0 never collides with either).
        private const val DEFAULT_SOUND_ID = 0L

        @Volatile
        private var INSTANCE: SoundRepository? = null
        
        fun getInstance(context: Context): SoundRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SoundRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    init {
        // Start observing database changes
        observeDatabaseChanges()
        // Load initial data
        loadSounds()
    }
    
    /**
     * Observe database changes and update StateFlow
     * Combines custom sounds with system ringtones
     */
    private fun observeDatabaseChanges() {
        repositoryScope.launch {
            dao.getAllSounds().collect { customSounds ->
                val allSounds = mutableListOf<Sound>()
                
                // Add custom sounds
                allSounds.addAll(customSounds.map { it.toSound() })
                
                // Add system ringtones
                allSounds.addAll(getSystemRingtones())
                
                _sounds.value = allSounds
            }
        }
    }
    
    /**
     * Get all system alarm ringtones
     */
    private fun getSystemRingtones(): List<Sound> {
        val systemSounds = mutableListOf<Sound>()

        // Bundled fallback sound, shown alongside system ringtones for preview/sync consistency
        // with the RingtonePickerDialog default entry.
        systemSounds.add(
            Sound(
                id = DEFAULT_SOUND_ID,
                name = "Disco",
                filePath = SettingsManager.DEFAULT_ALARM_SOUND_URI,
                duration = 0L,
                isSystemSound = true,
                uri = SettingsManager.DEFAULT_ALARM_SOUND_URI
            )
        )

        try {
            val ringtoneManager = RingtoneManager(context)
            ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
            val cursor = ringtoneManager.cursor
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val ringtoneUri = ringtoneManager.getRingtoneUri(cursor.position)
                    
                    // Use negative IDs for system sounds to avoid conflicts with custom sounds
                    val id = -(cursor.position.toLong() + 1)
                    
                    systemSounds.add(
                        Sound(
                            id = id,
                            name = title,
                            filePath = ringtoneUri.toString(), // Store URI as filePath for compatibility
                            duration = 0L, // Duration unknown for system sounds
                            isSystemSound = true,
                            uri = ringtoneUri.toString()
                        )
                    )
                } while (cursor.moveToNext())
            }
            
            Log.d(TAG, "Loaded ${systemSounds.size} system ringtones")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading system ringtones", e)
        }
        
        return systemSounds
    }
    
    /**
     * Load sounds from database and system
     */
    fun loadSounds() {
        repositoryScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val allSounds = mutableListOf<Sound>()
                
                // Load custom sounds
                val customSounds = dao.getAllSoundsList()
                allSounds.addAll(customSounds.map { it.toSound() })
                
                // Load system ringtones
                allSounds.addAll(getSystemRingtones())
                
                _sounds.value = allSounds
                
                Log.d(TAG, "Loaded ${customSounds.size} custom sounds + ${allSounds.size - customSounds.size} system sounds")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading sounds", e)
                _error.value = "Fout bij laden van geluiden: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Add a custom sound from URI
     */
    suspend fun addSound(uri: Uri): Result<Sound> {
        return try {
            _isLoading.value = true
            _error.value = null
            
            when (val result = soundManager.addCustomSound(uri)) {
                is CustomSoundManager.AddSoundResult.Success -> {
                    val sound = result.sound.toSound()
                    Log.d(TAG, "Successfully added sound: ${sound.name}")

                    // Best-effort: upload ook naar Home Assistant zodat de backup-speaker
                    // dit geluid ook kan afspelen als de telefoon zelf niet bereikbaar is.
                    // Mag het lokaal toevoegen nooit blokkeren of laten mislukken.
                    repositoryScope.launch {
                        val uploadResult = com.dd.daykit.homeassistant.SoundHaSync.uploadSoundToHa(context, sound)
                        if (uploadResult.isFailure) {
                            Log.w(TAG, "Upload naar HA overgeslagen/mislukt: ${uploadResult.exceptionOrNull()?.message}")
                        }
                    }

                    Result.success(sound)
                }
                is CustomSoundManager.AddSoundResult.Error -> {
                    _error.value = result.message
                    Log.e(TAG, "Error adding sound: ${result.message}")
                    Result.failure(Exception(result.message))
                }
            }
        } catch (e: Exception) {
            _error.value = "Fout bij toevoegen: ${e.message}"
            Log.e(TAG, "Error adding sound", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Delete a sound
     */
    suspend fun deleteSound(sound: Sound): Boolean {
        return try {
            _isLoading.value = true
            _error.value = null
            
            // Get the CustomAlarmSound from database
            val customSound = dao.getSoundById(sound.id)
            if (customSound != null) {
                val success = soundManager.deleteSound(customSound)
                if (success) {
                    Log.d(TAG, "Successfully deleted sound: ${sound.name}")
                } else {
                    _error.value = "Fout bij verwijderen van geluid"
                }
                success
            } else {
                _error.value = "Geluid niet gevonden"
                false
            }
        } catch (e: Exception) {
            _error.value = "Fout bij verwijderen: ${e.message}"
            Log.e(TAG, "Error deleting sound", e)
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Get sound by ID
     */
    suspend fun getSoundById(id: Long): Sound? {
        return try {
            dao.getSoundById(id)?.toSound()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sound by ID", e)
            null
        }
    }
    
    /**
     * Get file for a sound
     */
    fun getFileForSound(sound: Sound): File {
        return File(sound.filePath)
    }
    
    /**
     * Get URI for a sound
     */
    fun getUriForSound(sound: Sound): Uri {
        return Uri.fromFile(File(sound.filePath))
    }
    
    /**
     * Format duration for display
     */
    fun formatDuration(durationMs: Long): String {
        return soundManager.formatDuration(durationMs)
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        return soundManager.formatFileSize(bytes)
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Convert CustomAlarmSound to Sound domain model
     */
    private fun CustomAlarmSound.toSound(): Sound {
        val customSoundsDir = File(context.filesDir, "alarm_sounds/custom")
        val filePath = File(customSoundsDir, storedFilename).absolutePath
        
        return Sound(
            id = id,
            name = displayName,
            filePath = filePath,
            duration = durationMs
        )
    }
}
