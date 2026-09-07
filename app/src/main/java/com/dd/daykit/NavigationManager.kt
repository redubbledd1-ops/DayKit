package com.dd.daykit

import android.app.Activity
import android.content.Context
import android.content.Intent

enum class Screen(val id: String, val activityClass: Class<*>, val titleKey: String) {
    SETTINGS("SETTINGS", SettingsActivity::class.java, "screen_settings"),
    AGENDA_ALARM("AGENDA_ALARM", MainActivity::class.java, "screen_agenda_alarm"),
    STOPWATCH("STOPWATCH", NewFeatureActivity::class.java, "screen_stopwatch"),
    TIMER("TIMER", TimerActivity::class.java, "screen_timer"),
    APPLIANCE_CALCULATOR("APPLIANCE_CALCULATOR", ApplianceCalculatorActivity::class.java, "screen_appliance_calc"),
    CALCULATOR("CALCULATOR", CalculatorActivity::class.java, "screen_calculator"),
    WEATHER("WEATHER", WeatherActivity::class.java, "screen_weather")
}

object NavigationManager {

    fun getOrderedScreens(context: Context): List<Screen> {
        val enabledScreens = SettingsManager.getEnabledScreens(context)
        val orderedIds = SettingsManager.getScreenOrder(context)
        
        val result = mutableListOf<Screen>()
        
        // Add screens in order
        for (id in orderedIds) {
            val screen = Screen.values().find { it.id == id }
            if (screen != null && enabledScreens.contains(id)) {
                result.add(screen)
            }
        }
        
        // Fallback
        if (result.isEmpty()) {
            result.add(Screen.AGENDA_ALARM)
        }
        
        // Ensure SETTINGS is always available if somehow lost (safety net)
        if (result.none { it == Screen.SETTINGS }) {
             result.add(0, Screen.SETTINGS)
        }

        return result
    }

    fun getStartScreen(context: Context): Screen {
        val startId = SettingsManager.getStartScreenId(context)
        val screens = getOrderedScreens(context)
        return screens.firstOrNull() ?: Screen.AGENDA_ALARM // First screen in order is start screen
    }

    fun getSwipeLeftTarget(context: Context, currentScreenId: String): Screen? {
        // Swipe Left Gesture -> Go to PREVIOUS Screen (Left direction)
        val screens = getOrderedScreens(context)
        if (screens.size <= 1) return null
        
        val index = screens.indexOfFirst { it.id == currentScreenId }
        if (index == -1) return null // Current screen not in list?
        
        // Loop: if index is 0, prev is last
        val targetIndex = if (index > 0) index - 1 else screens.size - 1
        return screens[targetIndex]
    }

    fun getSwipeRightTarget(context: Context, currentScreenId: String): Screen? {
        // Swipe Right Gesture -> Go to NEXT Screen
        val screens = getOrderedScreens(context)
        if (screens.size <= 1) return null
        
        val index = screens.indexOfFirst { it.id == currentScreenId }
        if (index == -1) return null
        
        // Loop: if index is last, next is 0
        val targetIndex = if (index < screens.size - 1) index + 1 else 0
        return screens[targetIndex]
    }

    fun navigateTo(context: Context, targetScreen: Screen) {
        val intent = Intent(context, targetScreen.activityClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.putExtra("FROM_NAVIGATION", true)
        context.startActivity(intent)
        // Removed overridePendingTransition to use system default transition
    }
    
    fun navigateLeft(context: Context, currentScreenId: String) {
        // Navigate to the screen on the LEFT (Previous)
        val target = getSwipeLeftTarget(context, currentScreenId)
        if (target != null) {
            navigateTo(context, target)
        }
    }

    fun navigateRight(context: Context, currentScreenId: String) {
        // Navigate to the screen on the RIGHT (Next)
        val target = getSwipeRightTarget(context, currentScreenId)
        if (target != null) {
            navigateTo(context, target)
        }
    }
}
