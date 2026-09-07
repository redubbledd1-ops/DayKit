package com.dd.daykit

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Checks if the AgendaAlarm app is actively running and provides status updates
 */
object AppStatusChecker {
    private const val TAG = "AppStatusChecker"
    
    // State flows for reactive UI updates
    private val _isAppRunning = MutableStateFlow(true) // Default to true
    val isAppRunning: StateFlow<Boolean> = _isAppRunning.asStateFlow()
    
    private val _lastCheckTime = MutableStateFlow<Long?>(null)
    val lastCheckTime: StateFlow<Long?> = _lastCheckTime.asStateFlow()
    
    /**
     * Check if the app is currently running
     */
    fun checkAppStatus(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses
            
            // Check if our app process is in the list
            val isRunning = runningAppProcesses?.any { processInfo ->
                processInfo.processName == context.packageName &&
                processInfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
            } ?: true // Default to true if we can't determine
            
            _isAppRunning.value = isRunning
            _lastCheckTime.value = System.currentTimeMillis()
            
            Log.d(TAG, "App running status: $isRunning")
            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "Error checking app status", e)
            // On error, assume app is running to avoid false negatives
            _isAppRunning.value = true
            true
        }
    }
    
    /**
     * Check if critical services are enabled
     */
    fun areCriticalServicesEnabled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            
            // Check if CalendarUpdateReceiver is enabled
            val calendarReceiverComponent = android.content.ComponentName(
                context,
                CalendarUpdateReceiver::class.java
            )
            val calendarReceiverState = pm.getComponentEnabledSetting(calendarReceiverComponent)
            val isCalendarReceiverEnabled = calendarReceiverState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                                           calendarReceiverState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            
            // Check if BootReceiver is enabled
            val bootReceiverComponent = android.content.ComponentName(
                context,
                BootCompletedReceiver::class.java
            )
            val bootReceiverState = pm.getComponentEnabledSetting(bootReceiverComponent)
            val isBootReceiverEnabled = bootReceiverState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                                       bootReceiverState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            
            val allEnabled = isCalendarReceiverEnabled && isBootReceiverEnabled
            
            Log.d(TAG, "Critical services enabled: Calendar=$isCalendarReceiverEnabled, Boot=$isBootReceiverEnabled")
            allEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking critical services", e)
            true // Default to true on error
        }
    }
    
    /**
     * Get a comprehensive status message
     */
    fun getStatusMessage(context: Context): String {
        val isRunning = checkAppStatus(context)
        val servicesEnabled = areCriticalServicesEnabled(context)
        
        return when {
            !isRunning && !servicesEnabled -> LanguageManager.getString("app_status_not_active_and_services")
            !isRunning -> LanguageManager.getString("app_status_not_active")
            !servicesEnabled -> LanguageManager.getString("app_status_services_disabled")
            else -> LanguageManager.getString("app_status_ok")
        }
    }
    
    /**
     * Check if there are any issues that need attention
     */
    fun hasIssues(context: Context): Boolean {
        val isRunning = checkAppStatus(context)
        val servicesEnabled = areCriticalServicesEnabled(context)
        return !isRunning || !servicesEnabled
    }
    
    /**
     * Get detailed status information
     */
    data class AppStatus(
        val isRunning: Boolean,
        val servicesEnabled: Boolean,
        val message: String,
        val hasIssues: Boolean
    )
    
    fun getDetailedStatus(context: Context): AppStatus {
        val isRunning = checkAppStatus(context)
        val servicesEnabled = areCriticalServicesEnabled(context)
        val message = getStatusMessage(context)
        val hasIssues = !isRunning || !servicesEnabled
        
        return AppStatus(
            isRunning = isRunning,
            servicesEnabled = servicesEnabled,
            message = message,
            hasIssues = hasIssues
        )
    }
}
