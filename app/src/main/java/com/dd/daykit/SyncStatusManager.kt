package com.dd.daykit

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages synchronization status and provides real-time updates
 * to the UI about calendar sync operations
 */
object SyncStatusManager {
    private const val TAG = "SyncStatusManager"
    private const val PREFS_NAME = "sync_status_prefs"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_LAST_SYNC_SUCCESS = "last_sync_success"
    private const val KEY_LAST_ERROR_MESSAGE = "last_error_message"
    
    // State flows for reactive UI updates
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()
    
    private val _lastSyncSuccess = MutableStateFlow(true)
    val lastSyncSuccess: StateFlow<Boolean> = _lastSyncSuccess.asStateFlow()
    
    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()
    
    /**
     * Initialize the sync status manager and load persisted state
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _lastSyncTime.value = prefs.getLong(KEY_LAST_SYNC_TIME, 0L).takeIf { it > 0 }
        _lastSyncSuccess.value = prefs.getBoolean(KEY_LAST_SYNC_SUCCESS, true)
        _lastErrorMessage.value = prefs.getString(KEY_LAST_ERROR_MESSAGE, null)
        
        Log.d(TAG, "SyncStatusManager initialized - Last sync: ${_lastSyncTime.value}")
    }
    
    /**
     * Mark sync as started
     */
    fun onSyncStarted() {
        Log.d(TAG, "Sync started")
        _isSyncing.value = true
        _lastErrorMessage.value = null
    }
    
    /**
     * Mark sync as completed successfully
     */
    fun onSyncCompleted(context: Context, nextAlarm: AlarmItem?) {
        val now = System.currentTimeMillis()
        Log.i(TAG, "Sync completed successfully - Next alarm: ${nextAlarm?.label ?: "None"}")
        
        _isSyncing.value = false
        _lastSyncTime.value = now
        _lastSyncSuccess.value = true
        _lastErrorMessage.value = null
        
        // Persist state
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_LAST_SYNC_TIME, now)
            putBoolean(KEY_LAST_SYNC_SUCCESS, true)
            remove(KEY_LAST_ERROR_MESSAGE)
            apply()
        }
    }
    
    /**
     * Mark sync as failed
     */
    fun onSyncFailed(context: Context, error: String) {
        val now = System.currentTimeMillis()
        Log.e(TAG, "Sync failed: $error")
        
        _isSyncing.value = false
        _lastSyncTime.value = now
        _lastSyncSuccess.value = false
        _lastErrorMessage.value = error
        
        // Persist state
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_LAST_SYNC_TIME, now)
            putBoolean(KEY_LAST_SYNC_SUCCESS, false)
            putString(KEY_LAST_ERROR_MESSAGE, error)
            apply()
        }
    }
    
    /**
     * Check if periodic sync is active by monitoring calendar update receiver
     */
    fun isPeriodicSyncActive(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val componentName = android.content.ComponentName(
                context,
                CalendarUpdateReceiver::class.java
            )
            val componentEnabledSetting = pm.getComponentEnabledSetting(componentName)
            
            val isEnabled = componentEnabledSetting == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                           componentEnabledSetting == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            
            Log.d(TAG, "Periodic sync active: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking periodic sync status", e)
            false
        }
    }
    
    /**
     * Get a human-readable status message
     */
    fun getStatusMessage(context: Context): String {
        return when {
            _isSyncing.value -> "Bezig met synchroniseren..."
            !_lastSyncSuccess.value -> "Laatste sync mislukt: ${_lastErrorMessage.value}"
            _lastSyncTime.value == null -> "Nog niet gesynchroniseerd"
            else -> {
                val time = _lastSyncTime.value!!
                val diff = System.currentTimeMillis() - time
                when {
                    diff < 60000 -> "Zojuist gesynchroniseerd"
                    diff < 3600000 -> "Gesynchroniseerd ${diff / 60000} min geleden"
                    diff < 86400000 -> "Gesynchroniseerd ${diff / 3600000} uur geleden"
                    else -> "Gesynchroniseerd meer dan een dag geleden"
                }
            }
        }
    }
}
