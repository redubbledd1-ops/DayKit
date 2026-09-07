package com.dd.daykit

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.mutableStateOf

/**
 * Global manager for tracking sound playback state across the app.
 * Shows a notification bar when audio is playing and allows muting.
 */
object SoundStateManager {
    private const val PREFS_NAME = "SoundPrefs"
    private const val KEY_SOUNDS_ENABLED = "sounds_enabled"
    
    // Track if any sound is currently playing
    val isPlaying = mutableStateOf(false)
    
    // Track if sounds are enabled globally
    val soundsEnabled = mutableStateOf(true)
    
    // Current active MediaPlayer (if any)
    private var activeMediaPlayer: MediaPlayer? = null
    
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        soundsEnabled.value = prefs.getBoolean(KEY_SOUNDS_ENABLED, true)
    }
    
    fun setSoundsEnabled(context: Context, enabled: Boolean) {
        soundsEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUNDS_ENABLED, enabled)
            .apply()
        
        // If disabling sounds, stop any currently playing sound
        if (!enabled) {
            stopCurrentSound()
        }
    }
    
    fun registerMediaPlayer(player: MediaPlayer) {
        activeMediaPlayer = player
        isPlaying.value = true
    }
    
    fun unregisterMediaPlayer() {
        activeMediaPlayer = null
        isPlaying.value = false
    }
    
    fun stopCurrentSound() {
        try {
            activeMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            // Ignore errors during stop
        }
        activeMediaPlayer = null
        isPlaying.value = false
    }
    
    fun areSoundsEnabled(): Boolean = soundsEnabled.value
}
