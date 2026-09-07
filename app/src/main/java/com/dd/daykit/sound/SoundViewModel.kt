package com.dd.daykit.sound

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing custom alarm sounds
 * Provides reactive state management for UI
 */
class SoundViewModel(
    private val repository: SoundRepository
) : ViewModel() {
    
    // Expose repository StateFlows
    val sounds: StateFlow<List<Sound>> = repository.sounds
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val error: StateFlow<String?> = repository.error
    
    // UI state for success messages
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    // Currently playing sound ID
    private val _playingSoundId = MutableStateFlow<Long?>(null)
    val playingSoundId: StateFlow<Long?> = _playingSoundId.asStateFlow()
    
    init {
        loadSounds()
    }
    
    /**
     * Load all sounds
     */
    fun loadSounds() {
        repository.loadSounds()
    }
    
    /**
     * Add a new sound from URI
     */
    fun addSound(uri: Uri) {
        viewModelScope.launch {
            val result = repository.addSound(uri)
            if (result.isSuccess) {
                val sound = result.getOrNull()
                _successMessage.value = "✓ ${sound?.name ?: "Geluid"} toegevoegd"
            }
        }
    }
    
    /**
     * Delete a sound
     */
    fun deleteSound(sound: Sound) {
        viewModelScope.launch {
            val success = repository.deleteSound(sound)
            if (success) {
                _successMessage.value = "✓ ${sound.name} verwijderd"
            }
        }
    }
    
    /**
     * Get sound by ID
     */
    suspend fun getSoundById(id: Long): Sound? {
        return repository.getSoundById(id)
    }
    
    /**
     * Get URI for a sound
     */
    fun getUriForSound(sound: Sound): Uri {
        return repository.getUriForSound(sound)
    }
    
    /**
     * Get file path for a sound
     */
    fun getFilePathForSound(sound: Sound): String {
        return sound.filePath
    }
    
    /**
     * Format duration for display
     */
    fun formatDuration(durationMs: Long): String {
        return repository.formatDuration(durationMs)
    }
    
    /**
     * Set currently playing sound
     */
    fun setPlayingSound(soundId: Long?) {
        _playingSoundId.value = soundId
    }
    
    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        repository.clearError()
    }
    
    /**
     * Factory for creating SoundViewModel with repository
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SoundViewModel::class.java)) {
                val repository = SoundRepository.getInstance(context)
                return SoundViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
