package com.dd.daykit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.system.exitProcess

// Data class for Appliance History
data class ApplianceHistoryItem(
    val targetHour: Int,
    val targetMinute: Int,
    val durationHour: Int,
    val durationMinute: Int,
    val name: String = ""
)

data class CalculatorStep(
    val expression: String,
    val result: String
)

// Data class for Calculator History
data class CalculatorHistoryItem(
    val steps: List<CalculatorStep>,
    val name: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object SettingsManager {
    private const val TAG = "SettingsManager"

    // Bundled fallback alarm sound (replaces OS default alarm sound as the app-wide fallback).
    // A null/blank stored sound URI resolves to this everywhere sound is played.
    const val DEFAULT_ALARM_SOUND_URI = "android.resource://com.dd.daykit/raw/disco_ring_end"

    /**
     * Single source of truth for turning a stored sound-URI string into something that will
     * actually play. A restored backup (or any other stale reference) can leave behind a
     * non-null, non-empty URI that points at a file/content-URI no longer present on this
     * device - that's not caught by a plain null/blank check, so callers must resolve through
     * here instead of using the raw string directly. Synchronous and local-only (no network):
     * this runs on the critical path right before an alarm fires.
     */
    fun resolvePlayableAlarmSoundUri(context: Context, candidateUriString: String?): android.net.Uri {
        if (candidateUriString.isNullOrBlank()) {
            return android.net.Uri.parse(DEFAULT_ALARM_SOUND_URI)
        }

        val fallback = {
            android.util.Log.w(TAG, "Alarm sound URI unreadable/missing ('$candidateUriString'), falling back to default")
            android.net.Uri.parse(DEFAULT_ALARM_SOUND_URI)
        }

        return try {
            val uri = android.net.Uri.parse(candidateUriString)
            val isPlayable = when (uri.scheme) {
                "android.resource" -> true
                "content" -> try {
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (e: Exception) {
                    false
                }
                "file" -> uri.path?.let { java.io.File(it).exists() } ?: false
                else -> java.io.File(candidateUriString).exists()
            }
            if (isPlayable) uri else fallback()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse alarm sound URI ('$candidateUriString'), falling back to default", e)
            android.net.Uri.parse(DEFAULT_ALARM_SOUND_URI)
        }
    }

    private const val PREFS_NAME = "alarm_settings"
    private const val KEY_CLOCK_LAYOUT = "clock_layout"
    private const val KEY_COUNTDOWN_SECONDS_MODE = "countdown_seconds_mode"
    private const val KEY_SHOW_CURRENT_TIME_SECONDS = "show_current_time_seconds"

    const val DEFAULT_CLOCK_LAYOUT = "COUNTDOWN_BIG_CURRENT_SMALL"
    const val DEFAULT_COUNTDOWN_SECONDS_MODE = "LAST_5_MIN"
    const val DEFAULT_SHOW_CURRENT_TIME_SECONDS = false

    const val DEFAULT_CALENDAR_POPUP_LAST_ENABLED = true
    const val DEFAULT_CALENDAR_POPUP_WINDOW_MINUTES = 30
    const val DEFAULT_BUTTONLESS_NOTIFICATION_ENABLED = false
    const val DEFAULT_BUTTONLESS_NOTIFICATION_MINUTES = 540
    const val DEFAULT_BUTTONLESS_NOTIFICATION_HOURS = 9
    const val DEFAULT_SHOW_NAV_BUTTONS = true
    const val DEFAULT_IN_APP_NOTIFICATIONS_ENABLED = false
    const val DEFAULT_FULL_SCREEN_ALARM_ENABLED = false
    const val DEFAULT_DRAW_OVER_OTHER_APPS_ENABLED = false
    private const val KEY_LAST_VISITED_SETTINGS_PAGE = "last_visited_settings_page"
    private const val KEY_LAST_VISITED_SUB_SETTINGS_PAGE = "last_visited_sub_settings_page"
    private const val KEY_LAST_VISITED_KALENDER_SUB_SETTINGS_PAGE = "last_visited_kalender_sub_settings_page"
    private const val KEY_LAST_VISITED_WEATHER_SUB_SETTINGS_PAGE = "last_visited_weather_sub_settings_page"
    private const val KEY_CALC_HISTORY = "calculator_history"
    private const val KEY_WAKE_SCREEN_ENABLED_LEGACY = "wake_screen_enabled"
    private const val KEY_FULL_SCREEN_ALARM_ENABLED = "full_screen_alarm_enabled"
    private const val KEY_DRAW_OVER_OTHER_APPS_ENABLED = "draw_over_other_apps_enabled"
    private const val KEY_PERMISSION_ONBOARDING_COMPLETED = "permission_onboarding_completed"
    private const val KEY_FIRST_STARTUP_BACKUP_CHECKED = "first_startup_backup_checked"
    private const val KEY_OFFERED_BACKUP_FILENAMES = "offered_backup_filenames"

    // --- Screen Navigation Settings ---
    private const val KEY_SCREEN_ORDER = "screen_order_v2"
    private const val KEY_ENABLED_SCREENS = "enabled_screens_v2"
    private const val KEY_START_SCREEN = "start_screen_id"

    // --- Weather Settings ---
    private const val KEY_WEATHER_LATITUDE = "weather_latitude"
    private const val KEY_WEATHER_LONGITUDE = "weather_longitude"
    private const val KEY_WEATHER_LOCATION_NAME = "weather_location_name"
    private const val KEY_WEATHER_LOCATION_IS_GPS = "weather_location_is_gps"
    private const val KEY_WEATHER_LOCATION_SYNC_INTERVAL = "weather_location_sync_interval_minutes"
    private const val KEY_WEATHER_MODEL = "weather_model"
    private const val KEY_WEATHER_USE_FAHRENHEIT = "weather_use_fahrenheit"
    private const val KEY_WEATHER_RAIN_ALARM_ENABLED = "weather_rain_alarm_enabled"
    private const val KEY_WEATHER_RAIN_THRESHOLD = "weather_rain_threshold"
    private const val KEY_WEATHER_RAIN_MINUTES_BEFORE = "weather_rain_minutes_before"
    private const val KEY_WEATHER_TEMP_CHANGE_ENABLED = "weather_temp_change_enabled"
    private const val KEY_WEATHER_TEMP_CHANGE_THRESHOLD = "weather_temp_change_threshold"
    private const val KEY_WEATHER_NOTIFICATIONS_ENABLED = "weather_notifications_enabled"
    private const val KEY_WEATHER_ALERT_DELIVERY_STYLE = "weather_alert_delivery_style"
    private const val KEY_WEATHER_NOTIFY_ENABLED = "weather_notify_enabled"
    private const val KEY_WEATHER_POPUP_ENABLED = "weather_popup_enabled"
    private const val KEY_WEATHER_LINK_TO_CALENDAR = "weather_link_to_calendar"
    private const val KEY_WEATHER_LAST_FETCH_TIME = "weather_last_fetch_time"
    private const val KEY_WEATHER_EVENING_SWITCH_TIME = "weather_evening_switch_time"
    private const val KEY_WEATHER_WIDGET_1_ENTITIES = "weather_widget_1_entities"
    private const val KEY_WEATHER_WIDGET_2_ENTITIES = "weather_widget_2_entities"
    private const val KEY_WEATHER_ALERT_BEFORE_EVENT_ENABLED = "weather_alert_before_event_enabled"
    private const val KEY_WEATHER_ALERT_BEFORE_EVENT_MAX_PER_DAY = "weather_alert_before_event_max_per_day"
    /** TEST: negeert de max-per-dag-limiet hierboven — tijdelijk, om tijdens testen niet tegen de cap aan te lopen. */
    private const val KEY_WEATHER_ALERT_BEFORE_EVENT_NO_MAX = "weather_alert_before_event_no_max"
    private const val KEY_WEATHER_ALERT_BEFORE_EVENT_WHOLE_DAY = "weather_alert_before_event_whole_day"
    /** "Bereik rond tijdstip" (min) — kijkt ook naar het weer vóór/na het agenda-item, niet alleen het exacte uur. */
    private const val KEY_WEATHER_ALERT_BEFORE_EVENT_RANGE_MINUTES = "weather_alert_before_event_range_minutes"
    private const val KEY_WEATHER_ALERT_DAY_BEFORE_ENABLED = "weather_alert_day_before_enabled"
    private const val KEY_WEATHER_ALERT_DAY_BEFORE_TIME = "weather_alert_day_before_time"
    private const val KEY_WEATHER_ALERT_SAME_DAY_ENABLED = "weather_alert_same_day_enabled"
    private const val KEY_WEATHER_ALERT_SAME_DAY_TIME = "weather_alert_same_day_time"
    private const val KEY_WEATHER_BADWEATHER_CALENDAR_IDS = "weather_badweather_calendar_ids"
    private const val KEY_WEATHER_TEMPCHANGE_CALENDAR_IDS = "weather_tempchange_calendar_ids"
    // --- Temperatuurwissel: eigen "wanneer waarschuwen"-instellingen, los van slecht weer ---
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_DAY_BEFORE_ENABLED = "weather_tempchange_alert_day_before_enabled"
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_DAY_BEFORE_TIME = "weather_tempchange_alert_day_before_time"
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_SAME_DAY_ENABLED = "weather_tempchange_alert_same_day_enabled"
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_SAME_DAY_TIME = "weather_tempchange_alert_same_day_time"
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_ENABLED = "weather_tempchange_alert_first_event_enabled"
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_MINUTES_BEFORE = "weather_tempchange_alert_first_event_minutes_before"
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_MAX_PER_DAY = "weather_tempchange_alert_first_event_max_per_day"
    /** TEST: negeert de max-per-dag-limiet hierboven — tijdelijk, om tijdens testen niet tegen de cap aan te lopen. */
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_NO_MAX = "weather_tempchange_alert_first_event_no_max"
    private const val KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_WHOLE_DAY = "weather_tempchange_alert_first_event_whole_day"

    // --- Slecht weer: extra weersomstandigheden (los toggle-baar, allemaal standaard aan) ---
    private const val KEY_WEATHER_EXTRA_STORM_ENABLED = "weather_extra_storm_enabled"
    private const val KEY_WEATHER_EXTRA_STORM_THRESHOLD = "weather_extra_storm_threshold_kmh"
    private const val KEY_WEATHER_EXTRA_SNOW_ENABLED = "weather_extra_snow_enabled"
    private const val KEY_WEATHER_EXTRA_ICE_ROAD_ENABLED = "weather_extra_ice_road_enabled"
    private const val KEY_WEATHER_EXTRA_HAIL_ENABLED = "weather_extra_hail_enabled"
    private const val KEY_WEATHER_EXTRA_WET_SNOW_ENABLED = "weather_extra_wet_snow_enabled"
    private const val KEY_WEATHER_EXTRA_HEAT_ENABLED = "weather_extra_heat_enabled"
    private const val KEY_WEATHER_EXTRA_HEAT_THRESHOLD = "weather_extra_heat_threshold_c"
    private const val KEY_WEATHER_EXTRA_HURRICANE_ENABLED = "weather_extra_hurricane_enabled"
    private const val KEY_WEATHER_EXTRA_HURRICANE_THRESHOLD = "weather_extra_hurricane_threshold_kmh"

    // --- Reset All Settings ---
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun getScreenOrder(context: Context): List<String> {
        val defaultOrder = "SETTINGS,AGENDA_ALARM,STOPWATCH,TIMER,APPLIANCE_CALCULATOR,CALCULATOR,WEATHER"
        val orderStr = getPrefs(context).getString(KEY_SCREEN_ORDER, defaultOrder) ?: defaultOrder
        val order = orderStr.split(",").filter { it.isNotEmpty() }.toMutableList()
        val allScreenIds = Screen.values().map { it.id }
        for (id in allScreenIds) {
            if (id !in order) order.add(id)
        }
        return order
    }

    fun saveScreenOrder(context: Context, order: List<String>) {
        val orderStr = order.joinToString(",")
        getPrefs(context).edit().putString(KEY_SCREEN_ORDER, orderStr).apply()
    }

    fun getEnabledScreens(context: Context): Set<String> {
        val defaultEnabled = setOf("SETTINGS", "AGENDA_ALARM", "STOPWATCH", "TIMER", "APPLIANCE_CALCULATOR", "CALCULATOR", "WEATHER")
        return getPrefs(context).getStringSet(KEY_ENABLED_SCREENS, defaultEnabled) ?: defaultEnabled
    }

    fun saveEnabledScreens(context: Context, enabled: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_ENABLED_SCREENS, enabled).apply()
    }
    
    fun getStartScreenId(context: Context): String {
        return getPrefs(context).getString(KEY_START_SCREEN, "AGENDA_ALARM") ?: "AGENDA_ALARM"
    }

    fun saveStartScreenId(context: Context, id: String) {
        getPrefs(context).edit().putString(KEY_START_SCREEN, id).apply()
    }

    fun getFirstStartupDone(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_STARTUP_BACKUP_CHECKED, false)
    }

    fun saveFirstStartupDone(context: Context, done: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_STARTUP_BACKUP_CHECKED, done).apply()
    }

    fun getOfferedBackupFilenames(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_OFFERED_BACKUP_FILENAMES, emptySet()) ?: emptySet()
    }

    fun markBackupFilenameOffered(context: Context, filename: String) {
        val current = getOfferedBackupFilenames(context).toMutableSet()
        current.add(filename)
        getPrefs(context).edit().putStringSet(KEY_OFFERED_BACKUP_FILENAMES, current).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLastVisitedSettingsPage(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_VISITED_SETTINGS_PAGE, "") ?: ""
    }

    fun saveLastVisitedSettingsPage(context: Context, page: String) {
        getPrefs(context).edit().putString(KEY_LAST_VISITED_SETTINGS_PAGE, page).apply()
    }

    fun getLastVisitedSubSettingsPage(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_VISITED_SUB_SETTINGS_PAGE, "") ?: ""
    }

    fun saveLastVisitedSubSettingsPage(context: Context, page: String) {
        getPrefs(context).edit().putString(KEY_LAST_VISITED_SUB_SETTINGS_PAGE, page).apply()
    }

    fun getLastVisitedKalenderSubSettingsPage(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_VISITED_KALENDER_SUB_SETTINGS_PAGE, "") ?: ""
    }

    fun saveLastVisitedKalenderSubSettingsPage(context: Context, page: String) {
        getPrefs(context).edit().putString(KEY_LAST_VISITED_KALENDER_SUB_SETTINGS_PAGE, page).apply()
    }

    // Onthoudt de laatst bezochte subpagina binnen Weerinstellingen (bv. "algemeen", "locatie",
    // "meldingen", ...), zodat een swipe-omlaag op de Weerinstellingen-hoofdpagina er direct naartoe
    // kan springen - zelfde patroon als [getLastVisitedSubSettingsPage] voor de hoofdinstellingen.
    fun getLastVisitedWeatherSubPage(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_VISITED_WEATHER_SUB_SETTINGS_PAGE, "") ?: ""
    }

    fun saveLastVisitedWeatherSubPage(context: Context, page: String) {
        getPrefs(context).edit().putString(KEY_LAST_VISITED_WEATHER_SUB_SETTINGS_PAGE, page).apply()
    }

    fun getHaUrls(context: Context): String {
        return getPrefs(context).getString("ha_urls", "http://homeassistant.local:8123") ?: ""
    }

    fun saveHaUrls(context: Context, urls: String) {
        getPrefs(context).edit().putString("ha_urls", urls).apply()
    }

    fun getHaToken(context: Context): String {
        return getPrefs(context).getString("ha_token", "PASTE_LONG_LIVED_TOKEN") ?: ""
    }

    fun saveHaToken(context: Context, token: String) {
        getPrefs(context).edit().putString("ha_token", token).apply()
    }

    fun getPresentSensors(context: Context): String {
        return getPrefs(context).getString("present_sensors", "binary_sensor.slaapkamer_verlaten") ?: ""
    }

    fun savePresentSensors(context: Context, sensors: String) {
        getPrefs(context).edit().putString("present_sensors", sensors).apply()
    }

    fun getAbsentSensors(context: Context): String {
        return getPrefs(context).getString("absent_sensors", "") ?: ""
    }

    fun saveAbsentSensors(context: Context, sensors: String) {
        getPrefs(context).edit().putString("absent_sensors", sensors).apply()
    }

    fun getAlarmSoundUri(context: Context): String? {
        return getPrefs(context).getString("alarm_sound_uri", null)
    }

    fun saveAlarmSoundUri(context: Context, uri: String?) {
        getPrefs(context).edit().putString("alarm_sound_uri", uri).apply()
    }

    fun getVibrate(context: Context): Boolean {
        return getPrefs(context).getBoolean("vibrate", true)
    }

    fun saveVibrate(context: Context, vibrate: Boolean) {
        getPrefs(context).edit().putBoolean("vibrate", vibrate).apply()
    }

    fun getSnoozeMinutes(context: Context): Int {
        return getPrefs(context).getInt("snooze_minutes", 5).coerceAtLeast(0)
    }

    fun saveSnoozeMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt("snooze_minutes", minutes.coerceAtLeast(0)).apply()
    }

    fun getTriggerCalendarIds(context: Context): Set<String> {
        val raw = getPrefs(context).getStringSet("trigger_calendar_ids", emptySet()) ?: emptySet()
        return HashSet(raw)
    }

    fun saveTriggerCalendarIds(context: Context, calendarIds: Set<String>) {
        // HashSet copy + commit(): avoid StringSet reference bugs and ensure the next read/sync sees this write immediately (apply() can lag).
        getPrefs(context).edit()
            .putStringSet("trigger_calendar_ids", HashSet(calendarIds))
            .commit()
    }


    // --- Design Settings -- -
    fun getBackgroundColor(context: Context): Int {
        return getPrefs(context).getInt("background_color", 0xFF000000.toInt())
    }

    fun saveBackgroundColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt("background_color", color).commit()
    }

    fun getTextColor(context: Context): Int {
        return getPrefs(context).getInt("text_color", 0xFFFFFFFF.toInt())
    }

    fun saveTextColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt("text_color", color).commit()
    }

    fun getButtonColor(context: Context): Int {
        return getPrefs(context).getInt("button_color", 0xFFFFFFFF.toInt())
    }

    fun saveButtonColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt("button_color", color).commit()
    }

    fun getButtonTextColor(context: Context): Int {
        return getPrefs(context).getInt("button_text_color", 0xFF000000.toInt())
    }

    fun saveButtonTextColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt("button_text_color", color).commit()
    }

    fun getAlarmVolume(context: Context): Int {
        return getPrefs(context).getInt("alarm_volume", 100)
    }

    fun saveAlarmVolume(context: Context, volume: Int) {
        getPrefs(context).edit().putInt("alarm_volume", volume).apply()
    }

    // --- Alignment Settings -- -
    /** Tekst- en schermpositie: altijd gecentreerd (instelling uitlijning verwijderd). */
    fun getTextAlignment(context: Context): String {
        return "CENTER"
    }

    fun saveTextAlignment(context: Context, alignment: String) {
        getPrefs(context).edit().putString("text_alignment", alignment).commit()
    }

    // --- Background Settings -- -
    fun getBackgroundType(context: Context): String {
        return getPrefs(context).getString("background_type", "color") ?: "color"
    }

    fun saveBackgroundType(context: Context, type: String) {
        getPrefs(context).edit().putString("background_type", type).commit()
    }

    fun getBackgroundImageUri(context: Context): String? {
        return getPrefs(context).getString("background_image_uri", null)
    }

    fun saveBackgroundImageUri(context: Context, uri: String) {
        getPrefs(context).edit().putString("background_image_uri", uri).commit()
    }

    fun getBackgroundGifUri(context: Context): String? {
        return getPrefs(context).getString("background_gif_uri", null)
    }

    fun saveBackgroundGifUri(context: Context, uri: String) {
        getPrefs(context).edit().putString("background_gif_uri", uri).commit()
    }

    // --- Clock Layout Settings ---
    fun getClockLayout(context: Context): String {
        return getPrefs(context).getString(KEY_CLOCK_LAYOUT, DEFAULT_CLOCK_LAYOUT) ?: DEFAULT_CLOCK_LAYOUT
    }

    fun saveClockLayout(context: Context, layout: String) {
        getPrefs(context).edit().putString(KEY_CLOCK_LAYOUT, layout).apply()
    }

    // --- Clock Seconds Settings ---
    fun getCountdownSecondsMode(context: Context): String {
        return getPrefs(context).getString(KEY_COUNTDOWN_SECONDS_MODE, DEFAULT_COUNTDOWN_SECONDS_MODE)
            ?: DEFAULT_COUNTDOWN_SECONDS_MODE
    }

    fun saveCountdownSecondsMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_COUNTDOWN_SECONDS_MODE, mode).apply()
    }

    fun getShowCurrentTimeSeconds(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_CURRENT_TIME_SECONDS, DEFAULT_SHOW_CURRENT_TIME_SECONDS)
    }

    fun saveShowCurrentTimeSeconds(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_CURRENT_TIME_SECONDS, show).apply()
    }

    /**
     * Persists clock layout + seconds options in one synchronous write so receivers
     * that run immediately after (e.g. DESIGN_UPDATED) read the new values from prefs.
     */
    fun saveClockLayoutSettingsSync(
        context: Context,
        clockLayout: String,
        countdownSecondsMode: String,
        showCurrentTimeSeconds: Boolean
    ) {
        getPrefs(context).edit()
            .putString(KEY_CLOCK_LAYOUT, clockLayout)
            .putString(KEY_COUNTDOWN_SECONDS_MODE, countdownSecondsMode)
            .putBoolean(KEY_SHOW_CURRENT_TIME_SECONDS, showCurrentTimeSeconds)
            .commit()
    }

    // --- Navigation Settings ---
    fun getShowNavButtons(context: Context): Boolean {
        return getPrefs(context).getBoolean("show_nav_buttons", DEFAULT_SHOW_NAV_BUTTONS)
    }

    fun saveShowNavButtons(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean("show_nav_buttons", show).commit()
    }

    fun getSwipeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("swipe_enabled", true)
    }

    fun saveSwipeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("swipe_enabled", enabled).commit()
    }

    fun getArrowMode(context: Context): String {
        return getPrefs(context).getString("arrow_mode", "Indicatie") ?: "Indicatie"
    }

    fun saveArrowMode(context: Context, mode: String) {
        getPrefs(context).edit().putString("arrow_mode", mode).commit()
    }

    // Battery usage per hour setting (for Home Assistant sync)
    fun getBatteryUsagePerHour(context: Context): Int {
        return getPrefs(context).getInt("battery_usage_per_hour", 5)
    }

    fun saveBatteryUsagePerHour(context: Context, value: Int) {
        getPrefs(context).edit().putInt("battery_usage_per_hour", value).apply()
    }

    // (Sound sync gebruikt sinds de daykit-integratie de bestaande HA-verbinding
    // (baseUrl + token) rechtstreeks - geen los AppDaemon upload-URL-instelling meer nodig.)

    // Timer settings
    fun getTimerSoundUri(context: Context): android.net.Uri {
        val uriString = getPrefs(context).getString("timer_sound_uri", null)
        return if (uriString != null) {
            android.net.Uri.parse(uriString)
        } else {
            val alarmUri = getAlarmSoundUri(context)
            if (alarmUri != null) android.net.Uri.parse(alarmUri)
            else android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        }
    }
    
    fun saveTimerSoundUri(context: Context, uri: String) {
        getPrefs(context).edit().putString("timer_sound_uri", uri).apply()
    }
    
    fun getSavedTimers(context: Context): List<Triple<Int, Int, Int>> {
        val timersString = getPrefs(context).getString("saved_timers", "") ?: ""
        if (timersString.isEmpty()) return emptyList()
        
        return timersString.split(";").mapNotNull { timerStr ->
            val parts = timerStr.split(",")
            if (parts.size == 3) {
                Triple(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0, parts[2].toIntOrNull() ?: 0)
            } else null
        }
    }
    
    fun saveTimers(context: Context, timers: List<Triple<Int, Int, Int>>) {
        val timersString = timers.joinToString(";") { "${it.first},${it.second},${it.third}" }
        getPrefs(context).edit().putString("saved_timers", timersString).apply()
    }
    
    fun addTimer(context: Context, hours: Int, minutes: Int, seconds: Int) {
        val timers = getSavedTimers(context).toMutableList()
        val newTimer = Triple(hours, minutes, seconds)
        if (!timers.contains(newTimer)) {
            timers.add(newTimer)
            saveTimers(context, timers)
        }
    }
    
    fun clearSavedTimers(context: Context) {
        getPrefs(context).edit().putString("saved_timers", "").apply()
    }
    
    // Appliance Calculator Settings
    
    fun getApplianceLastInputs(context: Context): IntArray {
        val prefs = getPrefs(context)
        val hasSavedInputs = prefs.contains("appliance_target_h")
        val tH = prefs.getInt("appliance_target_h", -1)
        val tM = prefs.getInt("appliance_target_m", 0)
        val dH = if (hasSavedInputs) {
            prefs.getInt("appliance_duration_h", 2)
        } else {
            3
        }
        val dM = if (hasSavedInputs) {
            prefs.getInt("appliance_duration_m", 30)
        } else {
            0
        }
        return intArrayOf(tH, tM, dH, dM)
    }
    
    fun saveApplianceLastInputs(context: Context, tH: Int, tM: Int, dH: Int, dM: Int) {
        getPrefs(context).edit()
            .putInt("appliance_target_h", tH)
            .putInt("appliance_target_m", tM)
            .putInt("appliance_duration_h", dH)
            .putInt("appliance_duration_m", dM)
            .apply()
    }
    
    fun getApplianceHistory(context: Context): List<ApplianceHistoryItem> {
        val historyStr = getPrefs(context).getString("appliance_history", "") ?: ""
        if (historyStr.isEmpty()) return emptyList()
        
        return historyStr.split(";").mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size >= 4) {
                val tH = parts[0].toIntOrNull() ?: 0
                val tM = parts[1].toIntOrNull() ?: 0
                val dH = parts[2].toIntOrNull() ?: 0
                val dM = parts[3].toIntOrNull() ?: 0
                val name = if (parts.size > 4) parts[4] else ""
                ApplianceHistoryItem(tH, tM, dH, dM, name)
            } else null
        }
    }
    
    fun saveApplianceHistoryEntry(context: Context, tH: Int, tM: Int, dH: Int, dM: Int, name: String = "") {
        val currentList = getApplianceHistory(context).toMutableList()
        
        if (currentList.isNotEmpty()) {
            val lastEntry = currentList[0]
            if (lastEntry.targetHour == tH && lastEntry.targetMinute == tM && 
                lastEntry.durationHour == dH && lastEntry.durationMinute == dM && lastEntry.name == name) {
                return 
            }
        }
        
        currentList.add(0, ApplianceHistoryItem(tH, tM, dH, dM, name))
        if (currentList.size > 20) {
            currentList.removeAt(currentList.lastIndex)
        }
        
        saveApplianceHistoryList(context, currentList)
    }

    fun updateApplianceHistoryName(context: Context, index: Int, newName: String) {
        val currentList = getApplianceHistory(context).toMutableList()
        if (index in 0 until currentList.size) {
            val item = currentList[index]
            currentList[index] = item.copy(name = newName)
            saveApplianceHistoryList(context, currentList)
        }
    }

    fun deleteApplianceHistoryEntry(context: Context, index: Int) {
        val currentList = getApplianceHistory(context).toMutableList()
        if (index in 0 until currentList.size) {
            currentList.removeAt(index)
            saveApplianceHistoryList(context, currentList)
        }
    }

    private fun saveApplianceHistoryList(context: Context, list: List<ApplianceHistoryItem>) {
        val str = list.joinToString(";") { 
            "${it.targetHour},${it.targetMinute},${it.durationHour},${it.durationMinute},${it.name.replace(",", "").replace(";", "")}" 
        }
        getPrefs(context).edit().putString("appliance_history", str).apply()
    }
    
    fun clearApplianceHistory(context: Context) {
        getPrefs(context).edit().remove("appliance_history").apply()
    }

    // --- Calculator History ---
    
    fun getCalculatorHistory(context: Context): List<CalculatorHistoryItem> {
        val historyStr = getPrefs(context).getString(KEY_CALC_HISTORY, "") ?: ""
        if (historyStr.isEmpty()) return emptyList()

        return historyStr.split(";").mapNotNull { item ->
            val parts = item.split("|")
            if (parts.isNotEmpty()) {
                val stepsStr = parts[0]
                val name = if (parts.size > 1) parts[1] else ""
                val timestamp = if (parts.size > 2) parts[2].toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()
                
                val steps = if (stepsStr.isNotEmpty()) {
                    stepsStr.split("~").mapNotNull { stepStr ->
                        val stepParts = stepStr.split("#")
                        if (stepParts.size >= 2) {
                            CalculatorStep(stepParts[0], stepParts[1])
                        } else null
                    }
                } else emptyList()
                
                CalculatorHistoryItem(steps, name, timestamp)
            } else null
        }
    }

    fun saveCalculatorHistoryEntry(context: Context, steps: List<CalculatorStep>, name: String = "") {
        val currentList = getCalculatorHistory(context).toMutableList()

        // Prevent duplicate saves of the exact same session (e.g. spam-tapping Save).
        // Saving is still allowed when any expression/result (or name) changes.
        val isDuplicate = currentList.any { entry ->
            entry.name == name && entry.steps == steps
        }
        if (isDuplicate) return

        currentList.add(0, CalculatorHistoryItem(steps, name, System.currentTimeMillis()))
        
        if (currentList.size > 20) {
            currentList.removeAt(currentList.lastIndex)
        }

        saveCalculatorHistoryList(context, currentList)
    }

    fun updateCalculatorHistoryName(context: Context, index: Int, newName: String) {
        val currentList = getCalculatorHistory(context).toMutableList()
        if (index in 0 until currentList.size) {
            val item = currentList[index]
            currentList[index] = item.copy(name = newName)
            saveCalculatorHistoryList(context, currentList)
        }
    }
    
    fun deleteCalculatorHistoryEntry(context: Context, index: Int) {
        val currentList = getCalculatorHistory(context).toMutableList()
        if (index in 0 until currentList.size) {
            currentList.removeAt(index)
            saveCalculatorHistoryList(context, currentList)
        }
    }

    fun clearCalculatorHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_CALC_HISTORY).apply()
    }

    private fun saveCalculatorHistoryList(context: Context, list: List<CalculatorHistoryItem>) {
        val str = list.joinToString(";") { item ->
            val stepsStr = item.steps.joinToString("~") { step ->
                val expr = step.expression.replace("~", "").replace("#", "").replace("|", "").replace(";", "")
                val res = step.result.replace("~", "").replace("#", "").replace("|", "").replace(";", "")
                "$expr#$res"
            }
            
            val name = item.name.replace("|", "").replace(";", "")
            "$stepsStr|$name|${item.timestamp}"
        }
        getPrefs(context).edit().putString(KEY_CALC_HISTORY, str).apply()
    }
    
    // Calendar popup notification for last X minutes
    fun getCalendarPopupLast30Min(context: Context): Boolean {
        return getPrefs(context).getBoolean("calendar_popup_last_30_min", DEFAULT_CALENDAR_POPUP_LAST_ENABLED)
    }
    
    fun saveCalendarPopupLast30Min(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("calendar_popup_last_30_min", enabled).apply()
    }
    
    // Calendar popup window in minutes: "Alarm melding met Stop knop" - min 1 minute, max 3 uur
    // (180 min). Mag nooit langer zijn dan de buttonless ("zonder knop") melding hieronder: de
    // Stop-knop melding hoort altijd binnen dat grotere venster te vallen, nooit erbuiten, zodat
    // hij die melding altijd kan overnemen/uitzetten in plaats van er los naast te bestaan.
    const val CALENDAR_POPUP_WINDOW_MIN_MINUTES = 1
    const val CALENDAR_POPUP_WINDOW_MAX_MINUTES = 180

    /** Max toegestane waarde voor de Stop-knop melding, begrensd door de buttonless-melding. */
    fun getCalendarPopupWindowAllowedMax(context: Context): Int {
        return getButtonlessNotificationMinutes(context)
            .coerceIn(CALENDAR_POPUP_WINDOW_MIN_MINUTES, CALENDAR_POPUP_WINDOW_MAX_MINUTES)
    }

    fun getCalendarPopupWindowMinutes(context: Context): Int {
        val stored = getPrefs(context).getInt("calendar_popup_window_minutes", DEFAULT_CALENDAR_POPUP_WINDOW_MINUTES)
        return stored.coerceIn(CALENDAR_POPUP_WINDOW_MIN_MINUTES, getCalendarPopupWindowAllowedMax(context))
    }

    fun saveCalendarPopupWindowMinutes(context: Context, minutes: Int) {
        val clamped = minutes.coerceIn(CALENDAR_POPUP_WINDOW_MIN_MINUTES, getCalendarPopupWindowAllowedMax(context))
        getPrefs(context).edit().putInt("calendar_popup_window_minutes", clamped).apply()
    }

    fun getPopupWindowDisplayText(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            minutes < 60 -> "$minutes ${LanguageManager.getString("min")}"
            mins == 0 -> "$hours ${LanguageManager.getString("hour")}"
            else -> "${hours}u ${mins}min"
        }
    }
    
    // Buttonless alarm notification (passive, no dismiss/snooze)
    fun getButtonlessNotificationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("buttonless_notification_enabled", DEFAULT_BUTTONLESS_NOTIFICATION_ENABLED)
    }

    fun saveButtonlessNotificationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("buttonless_notification_enabled", enabled).apply()
    }

    fun getButtonlessNotificationMinutes(context: Context): Int {
        val stored = getPrefs(context).getInt("buttonless_notification_minutes", -1)
        if (stored >= 0) return stored.coerceIn(120, 840)
        val legacyHours = getPrefs(context).getInt("buttonless_notification_hours", DEFAULT_BUTTONLESS_NOTIFICATION_HOURS)
        return (legacyHours * 60).coerceIn(120, 840)
    }

    fun saveButtonlessNotificationMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt("buttonless_notification_minutes", minutes.coerceIn(120, 840)).apply()
        // De Stop-knop melding mag nooit langer zijn dan dit venster - als dit venster kleiner
        // wordt dan de opgeslagen Stop-knop waarde, klem die meteen mee terug.
        val currentPopupWindow = getPrefs(context).getInt("calendar_popup_window_minutes", DEFAULT_CALENDAR_POPUP_WINDOW_MINUTES)
        val allowedMax = getCalendarPopupWindowAllowedMax(context)
        if (currentPopupWindow > allowedMax) {
            saveCalendarPopupWindowMinutes(context, allowedMax)
        }
    }

    // Global popup notification setting
    fun getGlobalPopupEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("global_popup_enabled", false)
    }
    
    fun saveGlobalPopupEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("global_popup_enabled", enabled).apply()
    }
    
    // Notification settings
    fun getInAppNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("in_app_notifications_enabled", DEFAULT_IN_APP_NOTIFICATIONS_ENABLED)
    }
    
    fun saveInAppNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("in_app_notifications_enabled", enabled).apply()
        InAppNotificationsVisibility.setEnabled(context, enabled)
    }

    fun getFullScreenAlarmEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_FULL_SCREEN_ALARM_ENABLED)) {
            return prefs.getBoolean(KEY_FULL_SCREEN_ALARM_ENABLED, DEFAULT_FULL_SCREEN_ALARM_ENABLED)
        }

        if (prefs.contains(KEY_WAKE_SCREEN_ENABLED_LEGACY)) {
            val migrated = prefs.getBoolean(KEY_WAKE_SCREEN_ENABLED_LEGACY, true)
            prefs.edit().putBoolean(KEY_FULL_SCREEN_ALARM_ENABLED, migrated).apply()
            return migrated
        }

        // Keep the old default behavior for users who already had app settings before this option existed.
        if (hasLegacySettingsBeforeOptionalPermissionPrefs(prefs)) {
            prefs.edit().putBoolean(KEY_FULL_SCREEN_ALARM_ENABLED, true).apply()
            return true
        }

        return DEFAULT_FULL_SCREEN_ALARM_ENABLED
    }

    fun saveFullScreenAlarmEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_FULL_SCREEN_ALARM_ENABLED, enabled)
            .putBoolean(KEY_WAKE_SCREEN_ENABLED_LEGACY, enabled)
            .apply()
        WakeMobilePolicy.notifySettingChanged(context)
    }

    fun getDrawOverOtherAppsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DRAW_OVER_OTHER_APPS_ENABLED, DEFAULT_DRAW_OVER_OTHER_APPS_ENABLED)
    }

    fun saveDrawOverOtherAppsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DRAW_OVER_OTHER_APPS_ENABLED, enabled).apply()
    }

    fun getPermissionOnboardingCompleted(context: Context): Boolean {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_PERMISSION_ONBOARDING_COMPLETED)) {
            return prefs.getBoolean(KEY_PERMISSION_ONBOARDING_COMPLETED, false)
        }
        return hasLegacySettingsBeforeOptionalPermissionPrefs(prefs)
    }

    fun savePermissionOnboardingCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PERMISSION_ONBOARDING_COMPLETED, completed).apply()
    }

    fun getWakeScreenEnabled(context: Context): Boolean = getFullScreenAlarmEnabled(context)

    fun saveWakeScreenEnabled(context: Context, enabled: Boolean) {
        saveFullScreenAlarmEnabled(context, enabled)
    }

    fun getAutoSyncEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean("auto_sync_enabled", false)

    fun saveAutoSyncEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("auto_sync_enabled", enabled).apply()
    }

    fun getPeriodicSyncIntervalMinutes(context: Context): Long =
        getPrefs(context).getLong("periodic_sync_interval_minutes", 30L)

    fun savePeriodicSyncIntervalMinutes(context: Context, minutes: Long) {
        getPrefs(context).edit().putLong("periodic_sync_interval_minutes", minutes.coerceAtLeast(15L)).apply()
    }

    // --- Weather Settings ---

    fun getWeatherLatitude(context: Context): Double {
        return Double.fromBits(getPrefs(context).getLong(KEY_WEATHER_LATITUDE, 52.3676.toBits()))
    }

    fun saveWeatherLatitude(context: Context, latitude: Double) {
        getPrefs(context).edit().putLong(KEY_WEATHER_LATITUDE, latitude.toBits()).apply()
    }

    fun getWeatherLongitude(context: Context): Double {
        return Double.fromBits(getPrefs(context).getLong(KEY_WEATHER_LONGITUDE, 4.9041.toBits()))
    }

    fun saveWeatherLongitude(context: Context, longitude: Double) {
        getPrefs(context).edit().putLong(KEY_WEATHER_LONGITUDE, longitude.toBits()).apply()
    }

    fun getWeatherLocationName(context: Context): String {
        return getPrefs(context).getString(KEY_WEATHER_LOCATION_NAME, "Amsterdam") ?: "Amsterdam"
    }

    fun saveWeatherLocationName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_WEATHER_LOCATION_NAME, name).apply()
    }

    /** Open-Meteo weermodel-ID, bv. "best_match", "ecmwf_ifs04", "knmi_harmonie_arome_europe". */
    fun getWeatherModel(context: Context): String {
        return getPrefs(context).getString(KEY_WEATHER_MODEL, "best_match") ?: "best_match"
    }

    fun saveWeatherModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_WEATHER_MODEL, model).apply()
    }

    /**
     * True = temperaturen tonen in Fahrenheit i.p.v. Celsius. Zolang de gebruiker dit nog nooit
     * zelf heeft ingesteld, wordt automatisch de in de regio van dit toestel gangbare eenheid
     * gekozen (Fahrenheit in de VS en een handvol andere landen, anders Celsius) - zie
     * [defaultUsesFahrenheit]. Open-Meteo levert altijd Celsius; deze instelling is puur voor de
     * weergave, alle interne drempels/vergelijkingen (bv. "Extra weersomstandigheden" > hitte-
     * drempel) blijven in Celsius werken.
     */
    fun getWeatherUseFahrenheit(context: Context): Boolean {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_WEATHER_USE_FAHRENHEIT)) {
            prefs.getBoolean(KEY_WEATHER_USE_FAHRENHEIT, false)
        } else {
            defaultUsesFahrenheit(context)
        }
    }

    fun saveWeatherUseFahrenheit(context: Context, useFahrenheit: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_USE_FAHRENHEIT, useFahrenheit).apply()
    }

    /** Landen waar Fahrenheit de gangbare eenheid is voor het (weer)bericht - VS plus een handvol andere. */
    private val FAHRENHEIT_COUNTRY_CODES = setOf("US", "BS", "BZ", "KY", "LR", "PW", "FM", "MH")

    private fun defaultUsesFahrenheit(context: Context): Boolean {
        val country = context.resources.configuration.locales.get(0)?.country
            ?: java.util.Locale.getDefault().country
        return country.uppercase() in FAHRENHEIT_COUNTRY_CODES
    }

    /** True als de weerlocatie via GPS bepaald is (dus periodiek mag meebewegen), false bij een handmatig gezochte/vaste locatie. */
    fun getWeatherLocationIsGps(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_LOCATION_IS_GPS, false)
    }

    fun saveWeatherLocationIsGps(context: Context, isGps: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_LOCATION_IS_GPS, isGps).apply()
    }

    fun getWeatherLocationSyncIntervalMinutes(context: Context): Long {
        return getPrefs(context).getLong(KEY_WEATHER_LOCATION_SYNC_INTERVAL, 30L)
    }

    fun saveWeatherLocationSyncIntervalMinutes(context: Context, minutes: Long) {
        getPrefs(context).edit().putLong(KEY_WEATHER_LOCATION_SYNC_INTERVAL, minutes).apply()
    }

    fun getWeatherRainAlarmEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_RAIN_ALARM_ENABLED, true)
    }

    fun saveWeatherRainAlarmEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_RAIN_ALARM_ENABLED, enabled).apply()
    }

    fun getWeatherRainThreshold(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_RAIN_THRESHOLD, 30)
    }

    fun saveWeatherRainThreshold(context: Context, threshold: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_RAIN_THRESHOLD, threshold).apply()
    }

    fun getWeatherRainMinutesBefore(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_RAIN_MINUTES_BEFORE, 30)
    }

    fun saveWeatherRainMinutesBefore(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_RAIN_MINUTES_BEFORE, minutes).apply()
    }

    fun getWeatherTempChangeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_TEMP_CHANGE_ENABLED, true)
    }

    fun saveWeatherTempChangeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_TEMP_CHANGE_ENABLED, enabled).apply()
    }

    fun getWeatherTempChangeThreshold(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_TEMP_CHANGE_THRESHOLD, 5)
    }

    fun saveWeatherTempChangeThreshold(context: Context, threshold: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_TEMP_CHANGE_THRESHOLD, threshold).apply()
    }

    fun getWeatherNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_NOTIFICATIONS_ENABLED, true)
    }

    fun saveWeatherNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    /** @deprecated Vervangen door [getWeatherNotifyEnabled]/[getWeatherPopupEnabled] (los van elkaar
     * te combineren i.p.v. een enkelvoudige keuze). Blijft bestaan als migratiebron voor bestaande
     * gebruikers - zie die functies. */
    private fun getWeatherAlertDeliveryStyle(context: Context): String {
        return getPrefs(context).getString(KEY_WEATHER_ALERT_DELIVERY_STYLE, "notification") ?: "notification"
    }

    /** "Melding" - gewone (stille) pushmelding. Onafhankelijk van [getWeatherPopupEnabled] en
     * "Uitspreken" (HomeAssistantSettings.weatherTtsEnabled) - alle drie mogen tegelijk aan staan. */
    fun getWeatherNotifyEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_WEATHER_NOTIFY_ENABLED)) {
            return prefs.getBoolean(KEY_WEATHER_NOTIFY_ENABLED, true)
        }
        // Migratie: bestaande gebruikers hadden een enkelvoudige keuze - "popup" betekende toen
        // impliciet "geen losse gewone melding" (al kreeg je er in de praktijk toch een, zie
        // deliverAlert). Nieuwe/oude "notification"-keuze wordt hier 1x gelezen als startwaarde.
        return getWeatherAlertDeliveryStyle(context) != "popup"
    }

    fun saveWeatherNotifyEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_NOTIFY_ENABLED, enabled).apply()
    }

    /** "Pop-up" - volledig scherm met geluid, zoals de wekker. Zie [getWeatherNotifyEnabled]. */
    fun getWeatherPopupEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_WEATHER_POPUP_ENABLED)) {
            return prefs.getBoolean(KEY_WEATHER_POPUP_ENABLED, false)
        }
        return getWeatherAlertDeliveryStyle(context) == "popup"
    }

    fun saveWeatherPopupEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_POPUP_ENABLED, enabled).apply()
    }

    fun getWeatherLinkToCalendar(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_LINK_TO_CALENDAR, true)
    }

    fun saveWeatherLinkToCalendar(context: Context, link: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_LINK_TO_CALENDAR, link).apply()
    }

    fun getWeatherLastFetchTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_WEATHER_LAST_FETCH_TIME, 0L)
    }

    fun saveWeatherLastFetchTime(context: Context, epochMillis: Long) {
        getPrefs(context).edit().putLong(KEY_WEATHER_LAST_FETCH_TIME, epochMillis).apply()
    }

    fun getWeatherAlertBeforeEventEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_ALERT_BEFORE_EVENT_ENABLED, true)
    }

    fun saveWeatherAlertBeforeEventEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_ALERT_BEFORE_EVENT_ENABLED, enabled).apply()
    }

    /** Max. aantal "Melding voor agenda item"-meldingen (slecht weer) per dag — 1 t/m 10, standaard 1. */
    fun getWeatherAlertBeforeEventMaxPerDay(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_ALERT_BEFORE_EVENT_MAX_PER_DAY, 1)
    }

    fun saveWeatherAlertBeforeEventMaxPerDay(context: Context, max: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_ALERT_BEFORE_EVENT_MAX_PER_DAY, max).apply()
    }

    /** TEST: staat aan = de max-per-dag-limiet hierboven wordt genegeerd (tijdelijk, voor testen). */
    fun getWeatherAlertBeforeEventNoMax(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_ALERT_BEFORE_EVENT_NO_MAX, false)
    }

    fun saveWeatherAlertBeforeEventNoMax(context: Context, noMax: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_ALERT_BEFORE_EVENT_NO_MAX, noMax).apply()
    }

    /**
     * True = kijk naar de algemene voorspelling van de hele kalenderdag van het agenda-item
     * (zelfde soort scan als Dag ervoor/Zelfde dag). False (standaard) = kijk puur naar het exacte
     * tijdstip van het agenda-item zelf. In beide gevallen blijft de melding getimed rond het
     * agenda-item (X min van tevoren).
     */
    /**
     * Bereik van álle slecht-weer-meldingen: true = kijk naar de hele dag, false = kijk alleen naar
     * de uren rond je agenda-afspraken (met [getWeatherAlertBeforeEventRangeMinutes] als marge).
     *
     * Gold voorheen alleen voor de melding rond een agenda-item; "Dag ervoor" en "Zelfde dag"
     * scanden altijd de volledige dag, ongeacht deze instelling en ongeacht of er die dag iets
     * gepland stond. Dat leverde meldingen op over weer waar je niets mee te maken had - bv. regen
     * 's avonds laat terwijl je alleen 's ochtends buiten hoefde te zijn.
     *
     * De opslagsleutel heet nog "before_event" zodat bestaande installaties hun keuze houden; de
     * betekenis is verbreed, niet veranderd.
     */
    fun getWeatherAlertScopeWholeDay(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_ALERT_BEFORE_EVENT_WHOLE_DAY, false)
    }

    fun saveWeatherAlertScopeWholeDay(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_ALERT_BEFORE_EVENT_WHOLE_DAY, enabled).apply()
    }

    /**
     * "Bereik rond tijdstip" (minuten, standaard 1 = alleen het exacte uur, ongewijzigd gedrag).
     * Alleen relevant als "Hele dag" uit staat — kijkt dan ook naar het weer tot dit ver vóór/na
     * het agenda-item, i.p.v. puur het exacte uur, zodat regen iets eerder/later op de dag ook
     * meetelt in de beslissing.
     */
    fun getWeatherAlertBeforeEventRangeMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_ALERT_BEFORE_EVENT_RANGE_MINUTES, 0)
    }

    fun saveWeatherAlertBeforeEventRangeMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_ALERT_BEFORE_EVENT_RANGE_MINUTES, minutes).apply()
    }

    fun getWeatherAlertDayBeforeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_ALERT_DAY_BEFORE_ENABLED, false)
    }

    fun saveWeatherAlertDayBeforeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_ALERT_DAY_BEFORE_ENABLED, enabled).apply()
    }

    fun getWeatherEveningSwitchTime(context: Context): String {
        return getPrefs(context).getString(KEY_WEATHER_EVENING_SWITCH_TIME, "18:00") ?: "18:00"
    }

    fun saveWeatherEveningSwitchTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_WEATHER_EVENING_SWITCH_TIME, time).apply()
    }

    // --- Weer-widgets: max 2 widgets, elk met max 3 HA-entiteiten (evt. eigen naam) ---
    // Opslagformaat: rijen gescheiden door ";;", per rij "entityId::naam" (naam mag leeg zijn).

    private fun weatherWidgetKey(widgetIndex: Int): String = when (widgetIndex) {
        1 -> KEY_WEATHER_WIDGET_1_ENTITIES
        else -> KEY_WEATHER_WIDGET_2_ENTITIES
    }

    fun getWeatherWidgetEntities(context: Context, widgetIndex: Int): List<WeatherWidgetEntityConfig> {
        val raw = getPrefs(context).getString(weatherWidgetKey(widgetIndex), "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";;")
            .mapNotNull { entry ->
                val parts = entry.split("::", limit = 2)
                val entityId = parts.getOrNull(0)?.trim().orEmpty()
                if (entityId.isBlank()) null else WeatherWidgetEntityConfig(entityId, parts.getOrNull(1)?.trim().orEmpty())
            }
            .take(3)
    }

    fun saveWeatherWidgetEntities(context: Context, widgetIndex: Int, entities: List<WeatherWidgetEntityConfig>) {
        val encoded = entities
            .take(3)
            .filter { it.entityId.isNotBlank() }
            .joinToString(";;") { "${it.entityId.replace(";;", "").replace("::", "")}::${it.name.replace(";;", "").replace("::", "")}" }
        getPrefs(context).edit().putString(weatherWidgetKey(widgetIndex), encoded).apply()
    }

    fun getWeatherAlertDayBeforeTime(context: Context): String {
        return getPrefs(context).getString(KEY_WEATHER_ALERT_DAY_BEFORE_TIME, "20:00") ?: "20:00"
    }

    fun saveWeatherAlertDayBeforeTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_WEATHER_ALERT_DAY_BEFORE_TIME, time).apply()
    }

    fun getWeatherAlertSameDayEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_ALERT_SAME_DAY_ENABLED, false)
    }

    fun saveWeatherAlertSameDayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_ALERT_SAME_DAY_ENABLED, enabled).apply()
    }

    fun getWeatherAlertSameDayTime(context: Context): String {
        return getPrefs(context).getString(KEY_WEATHER_ALERT_SAME_DAY_TIME, "07:00") ?: "07:00"
    }

    fun saveWeatherAlertSameDayTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_WEATHER_ALERT_SAME_DAY_TIME, time).apply()
    }

    /** Eigen agenda-selectie voor slecht-weer-meldingen — los van de agenda-alarm-kalenders. Standaard leeg = geen enkele agenda (uit) totdat de gebruiker zelf agenda's aanvinkt. */
    fun getWeatherBadWeatherCalendarIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_WEATHER_BADWEATHER_CALENDAR_IDS, emptySet()) ?: emptySet()
    }

    fun saveWeatherBadWeatherCalendarIds(context: Context, calendarIds: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_WEATHER_BADWEATHER_CALENDAR_IDS, calendarIds).apply()
    }

    /** Eigen agenda-selectie voor temperatuurwissel-meldingen — los van de agenda-alarm-kalenders. Standaard leeg = geen enkele agenda (uit) totdat de gebruiker zelf agenda's aanvinkt. */
    fun getWeatherTempChangeCalendarIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_WEATHER_TEMPCHANGE_CALENDAR_IDS, emptySet()) ?: emptySet()
    }

    fun saveWeatherTempChangeCalendarIds(context: Context, calendarIds: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_WEATHER_TEMPCHANGE_CALENDAR_IDS, calendarIds).apply()
    }

    // --- Temperatuurwissel: wanneer waarschuwen (los van slecht weer) ---

    fun getWeatherTempChangeAlertDayBeforeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_DAY_BEFORE_ENABLED, false)
    }

    fun saveWeatherTempChangeAlertDayBeforeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_DAY_BEFORE_ENABLED, enabled).apply()
    }

    fun getWeatherTempChangeAlertDayBeforeTime(context: Context): String {
        return getPrefs(context).getString(KEY_WEATHER_TEMPCHANGE_ALERT_DAY_BEFORE_TIME, "20:00") ?: "20:00"
    }

    fun saveWeatherTempChangeAlertDayBeforeTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_WEATHER_TEMPCHANGE_ALERT_DAY_BEFORE_TIME, time).apply()
    }

    fun getWeatherTempChangeAlertSameDayEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_SAME_DAY_ENABLED, false)
    }

    fun saveWeatherTempChangeAlertSameDayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_SAME_DAY_ENABLED, enabled).apply()
    }

    fun getWeatherTempChangeAlertSameDayTime(context: Context): String {
        return getPrefs(context).getString(KEY_WEATHER_TEMPCHANGE_ALERT_SAME_DAY_TIME, "07:00") ?: "07:00"
    }

    fun saveWeatherTempChangeAlertSameDayTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_WEATHER_TEMPCHANGE_ALERT_SAME_DAY_TIME, time).apply()
    }

    fun getWeatherTempChangeAlertFirstEventEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_ENABLED, false)
    }

    fun saveWeatherTempChangeAlertFirstEventEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_ENABLED, enabled).apply()
    }

    fun getWeatherTempChangeAlertFirstEventMinutesBefore(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_MINUTES_BEFORE, 0)
    }

    fun saveWeatherTempChangeAlertFirstEventMinutesBefore(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_MINUTES_BEFORE, minutes).apply()
    }

    /** Max. aantal "Melding voor agenda item"-meldingen (temperatuurwissel) per dag — 1 t/m 10, standaard 1. */
    fun getWeatherTempChangeAlertFirstEventMaxPerDay(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_MAX_PER_DAY, 1)
    }

    fun saveWeatherTempChangeAlertFirstEventMaxPerDay(context: Context, max: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_MAX_PER_DAY, max).apply()
    }

    /** TEST: staat aan = de max-per-dag-limiet hierboven wordt genegeerd (tijdelijk, voor testen). */
    fun getWeatherTempChangeAlertFirstEventNoMax(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_NO_MAX, false)
    }

    fun saveWeatherTempChangeAlertFirstEventNoMax(context: Context, noMax: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_NO_MAX, noMax).apply()
    }

    /**
     * True = vergelijk de max. temperatuur van de hele kalenderdag van het agenda-item met de dag
     * erna (zelfde soort vergelijking als Dag van tevoren/Dag zelf). False (standaard) = vergelijk
     * puur het exacte tijdstip van het agenda-item met 24u later. In beide gevallen blijft de
     * melding getimed rond het agenda-item.
     */
    fun getWeatherTempChangeAlertFirstEventWholeDay(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_WHOLE_DAY, false)
    }

    fun saveWeatherTempChangeAlertFirstEventWholeDay(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_TEMPCHANGE_ALERT_FIRST_EVENT_WHOLE_DAY, enabled).apply()
    }

    // --- Slecht weer: extra weersomstandigheden ---
    // Gebruiken dezelfde agenda-selectie en "wanneer waarschuwen"-triggers als de rest van
    // Slecht weer — dit zijn alleen extra detectie-typen, geen aparte timing-instellingen.
    // Standaard allemaal AAN.

    fun getWeatherExtraStormEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_EXTRA_STORM_ENABLED, true)
    }

    fun saveWeatherExtraStormEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_EXTRA_STORM_ENABLED, enabled).apply()
    }

    /** Windstoten-drempel in km/u vanaf wanneer het als storm geldt. */
    fun getWeatherExtraStormThreshold(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_EXTRA_STORM_THRESHOLD, 75)
    }

    fun saveWeatherExtraStormThreshold(context: Context, thresholdKmh: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_EXTRA_STORM_THRESHOLD, thresholdKmh).apply()
    }

    fun getWeatherExtraSnowEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_EXTRA_SNOW_ENABLED, true)
    }

    fun saveWeatherExtraSnowEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_EXTRA_SNOW_ENABLED, enabled).apply()
    }

    fun getWeatherExtraIceRoadEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_EXTRA_ICE_ROAD_ENABLED, true)
    }

    fun saveWeatherExtraIceRoadEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_EXTRA_ICE_ROAD_ENABLED, enabled).apply()
    }

    fun getWeatherExtraHailEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_EXTRA_HAIL_ENABLED, true)
    }

    fun saveWeatherExtraHailEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_EXTRA_HAIL_ENABLED, enabled).apply()
    }

    fun getWeatherExtraWetSnowEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_EXTRA_WET_SNOW_ENABLED, true)
    }

    fun saveWeatherExtraWetSnowEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_EXTRA_WET_SNOW_ENABLED, enabled).apply()
    }

    fun getWeatherExtraHeatEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_EXTRA_HEAT_ENABLED, true)
    }

    fun saveWeatherExtraHeatEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_EXTRA_HEAT_ENABLED, enabled).apply()
    }

    /** Temperatuurdrempel in °C vanaf wanneer het als extreme hitte geldt. */
    fun getWeatherExtraHeatThreshold(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_EXTRA_HEAT_THRESHOLD, 30)
    }

    fun saveWeatherExtraHeatThreshold(context: Context, thresholdCelsius: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_EXTRA_HEAT_THRESHOLD, thresholdCelsius).apply()
    }

    fun getWeatherExtraHurricaneEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WEATHER_EXTRA_HURRICANE_ENABLED, true)
    }

    fun saveWeatherExtraHurricaneEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WEATHER_EXTRA_HURRICANE_ENABLED, enabled).apply()
    }

    /** Windstoten-drempel in km/u vanaf wanneer het als orkaanachtige wind geldt. */
    fun getWeatherExtraHurricaneThreshold(context: Context): Int {
        return getPrefs(context).getInt(KEY_WEATHER_EXTRA_HURRICANE_THRESHOLD, 120)
    }

    fun saveWeatherExtraHurricaneThreshold(context: Context, thresholdKmh: Int) {
        getPrefs(context).edit().putInt(KEY_WEATHER_EXTRA_HURRICANE_THRESHOLD, thresholdKmh).apply()
    }

    private fun hasLegacySettingsBeforeOptionalPermissionPrefs(prefs: SharedPreferences): Boolean {
        val newPermissionKeys = setOf(
            KEY_PERMISSION_ONBOARDING_COMPLETED,
            KEY_FULL_SCREEN_ALARM_ENABLED,
            KEY_DRAW_OVER_OTHER_APPS_ENABLED,
        )
        return prefs.all.keys.any { it !in newPermissionKeys }
    }
    
    /**
     * Apply app colors to the system navigation bar (back, home, recents buttons).
     * Call this in onCreate of every Activity after setDecorFitsSystemWindows.
     */
    fun applySystemBarColors(window: Window, context: Context) {
        val backgroundColor = getBackgroundColor(context)
        val textColor = getTextColor(context)
        
        // Set navigation bar background color
        window.navigationBarColor = backgroundColor
        
        // Set navigation bar icon colors (light or dark based on text color brightness)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val decorView = window.decorView
            val isLightIcons = isColorDark(textColor)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ uses WindowInsetsController
                val controller = window.insetsController
                if (isLightIcons) {
                    controller?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                } else {
                    controller?.setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            } else {
                // Android 8-10 uses systemUiVisibility flags
                @Suppress("DEPRECATION")
                if (isLightIcons) {
                    decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
        }
        
        // Also set status bar color
        window.statusBarColor = backgroundColor
        
        // Set status bar icon colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            val isLightStatusIcons = isColorDark(textColor)
            if (isLightStatusIcons) {
                controller?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            } else {
                controller?.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val decorView = window.decorView
            val isLightStatusIcons = isColorDark(textColor)
            if (isLightStatusIcons) {
                decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
    
    /**
     * Background used by fullscreen alarm overlays ([RingActivity] [AppBackground],
     * same rules as [com.dd.daykit.AppBackground]).
     */
    fun resolveAlarmOverlayBackgroundColor(context: Context): Int {
        val backgroundType = getBackgroundType(context).lowercase()
        val gifUri = getBackgroundGifUri(context).orEmpty()
        val useBlackFallback = backgroundType.contains("gif") || gifUri.isNotBlank()
        return if (useBlackFallback) android.graphics.Color.BLACK else getBackgroundColor(context)
    }

    /**
     * Status + navigation bar colors for fullscreen alarm Activities.
     * Icon appearance is derived from [backgroundColor] luminance (not text color).
     */
    fun applyAlarmOverlaySystemBarColors(window: Window, backgroundColor: Int) {
        window.navigationBarColor = backgroundColor
        window.statusBarColor = backgroundColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = backgroundColor
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val darkBackground = isColorDark(backgroundColor)
        controller.isAppearanceLightStatusBars = !darkBackground
        controller.isAppearanceLightNavigationBars = !darkBackground
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * Check if a color is dark (for determining if icons should be light or dark)
     */
    private fun isColorDark(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        // Calculate luminance
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return luminance < 0.5
    }
    
    /**
     * Restart the app after design/navigation settings change.
     * Starts MainActivity fresh and clears the entire task stack.
     */
    fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        
        // Finish current activity
        if (context is Activity) {
            context.finishAffinity()
        }
        
        // Kill the process after starting the new activity
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
