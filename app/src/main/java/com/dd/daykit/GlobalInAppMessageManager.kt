package com.dd.daykit

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.dd.daykit.util.CountdownFormatter
import com.dd.daykit.util.CountdownMode

/**
 * Type of global in-app message.
 */
enum class GlobalMessageType {
    TIMER,
    STOPWATCH,
    AGENDA_ALARM
}

/**
 * Destination screen for navigation when message is clicked.
 */
enum class MessageDestination {
    TIMER_SCREEN,
    STOPWATCH_SCREEN,
    AGENDA_ALARM_SCREEN
}

/**
 * Data model for a global in-app message.
 * 
 * @param id Unique identifier for the message
 * @param type Type of message (timer, stopwatch, agenda_alarm)
 * @param title Short title for the message
 * @param text Detailed text/countdown
 * @param destination Target screen when clicked
 * @param priority Higher priority messages appear first (default 0)
 * @param actions Icon-only strip actions for [com.dd.daykit.ui.UnifiedInternalMessageBannerRow]
 */
data class GlobalInAppMessage(
    val id: String,
    val type: GlobalMessageType,
    val title: String,
    val text: String,
    val destination: MessageDestination,
    val priority: Int = 0,
    val actions: List<InternalInAppBannerActionKind> = emptyList(),
)

/**
 * Global manager for in-app status messages.
 * 
 * This singleton manages a list of active messages that should be displayed
 * across all screens in the app. Messages are data-driven and automatically
 * updated based on feature state (timer, stopwatch, upcoming alarms).
 * 
 * Usage:
 * - Call init(context) once at app startup
 * - Observe `activeMessages` in UI to display messages
 * - Messages are automatically added/removed based on feature state
 */
object GlobalInAppMessageManager {
    
    private const val TAG = "GlobalInAppMessageManager"
    
    // Observable list of active messages
    val activeMessages: SnapshotStateList<GlobalInAppMessage> = mutableStateListOf()
    
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var appContext: Context? = null
    private var isInitialized = false
    
    // Message IDs
    private const val MSG_ID_TIMER = "global_timer"
    private const val MSG_ID_STOPWATCH = "global_stopwatch"
    private const val MSG_ID_AGENDA_ALARM = "global_agenda_alarm"
    private const val PREF_NEXT_ALARM_LABEL = "next_alarm_label"
    private const val PREF_NEXT_ALARM_TRIGGER_ID = "next_alarm_trigger_id"

    // Prefix voor extra-timer banners (2e/3e timer) — 1 bericht per ExtraTimerData.id.
    private const val EXTRA_TIMER_MSG_PREFIX = "global_timer_extra_"

    /** Herleidt het ExtraTimerData.id uit een bericht-id, of null als dit geen extra-timer bericht is. */
    fun extraTimerSlotIdFromMessageId(messageId: String): String? =
        if (messageId.startsWith(EXTRA_TIMER_MSG_PREFIX)) messageId.removePrefix(EXTRA_TIMER_MSG_PREFIX) else null
    
    // Dismissed messages - user can dismiss without stopping the feature
    // Message will reappear ONLY when feature restarts (not on every tick)
    // Key: message ID, Value: true if dismissed
    private val dismissedMessages = mutableMapOf<String, Boolean>()
    
    // Track previous feature states to detect restarts
    private var prevTimerState: GlobalTimerManager.TimerState? = null
    private var prevStopwatchState: StopwatchState? = null
    private var prevAgendaAlarmId: Long? = null
    
    /**
     * Initialize the manager. Call once at app startup.
     */
    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
        startUpdateLoop()
    }
    
    /**
     * Start the update loop that refreshes message state.
     */
    private fun startUpdateLoop() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                updateMessages()
                delay(200) // Update 5x per second for responsive UI (no 1-second lag)
            }
        }
    }
    
    /**
     * Update all messages based on current feature state.
     */
    private fun updateMessages() {
        val ctx = appContext ?: return
        
        updateTimerMessage()
        updateExtraTimerMessages()
        updateStopwatchMessage()
        updateAgendaAlarmMessage(ctx)
    }
    
    /**
     * Dismiss a message by ID. The feature continues running but message is hidden.
     * Message will reappear ONLY when feature restarts (IDLE -> RUNNING transition).
     * Safe from background threads (broadcast receivers / services): mutates Compose state on main.
     */
    fun dismissMessage(messageId: String) {
        val block = {
            dismissedMessages[messageId] = true
            removeMessage(messageId)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else Handler(Looper.getMainLooper()).post(block)
    }
    
    /**
     * Check if a message is dismissed.
     */
    private fun isDismissed(messageId: String): Boolean {
        return dismissedMessages[messageId] == true
    }
    
    /**
     * Clear dismissed state for a message (called when feature restarts).
     */
    private fun clearDismissed(messageId: String) {
        dismissedMessages.remove(messageId)
    }
    
    /**
     * Update timer message based on GlobalTimerManager state.
     * Uses getRemainingTimeMs() which calculates from absolute timestamps.
     */
    private fun updateTimerMessage() {
        val timerState = GlobalTimerManager.timerState.value
        
        // Detect feature restart: IDLE -> RUNNING transition clears dismissed state
        if (prevTimerState == GlobalTimerManager.TimerState.IDLE && 
            timerState == GlobalTimerManager.TimerState.RUNNING) {
            clearDismissed(MSG_ID_TIMER)
        }
        prevTimerState = timerState
        
        // If dismissed, don't show (but don't remove - just skip adding)
        if (isDismissed(MSG_ID_TIMER)) {
            removeMessage(MSG_ID_TIMER)
            return
        }
        
        val shouldShow = timerState == GlobalTimerManager.TimerState.RUNNING || 
                         timerState == GlobalTimerManager.TimerState.PAUSED
        
        if (shouldShow) {
            // Use timestamp-based calculation for accurate time even after app restart
            val remainingMs = GlobalTimerManager.getRemainingTimeMs()
            
            val timeStr = CountdownFormatter.format(remainingMs, CountdownMode.COMPACT_TRAY)
            
            val name = GlobalTimerManager.timerName.value
            val baseLabel = if (name.isNotEmpty()) name else LanguageManager.getString("nav_timer")
            val statusText = if (timerState == GlobalTimerManager.TimerState.PAUSED) {
                "$baseLabel (${LanguageManager.getString("sw_pause")})"
            } else {
                baseLabel
            }

            val actions = TimerInternalControlOrder.inAppBannerKinds(timerState)
            val message = GlobalInAppMessage(
                id = MSG_ID_TIMER,
                type = GlobalMessageType.TIMER,
                title = statusText,
                text = timeStr,
                destination = MessageDestination.TIMER_SCREEN,
                priority = 10,
                actions = actions,
            )
            
            addOrUpdateMessage(message)
        } else {
            removeMessage(MSG_ID_TIMER)
        }
    }

    /**
     * Update messages voor de 2e/3e timer ([ExtraTimerManager]). Elke lopende/gepauzeerde
     * extra timer krijgt zijn eigen banner-id, zodat ze naast de primaire timer-banner
     * (en naast elkaar) tegelijk zichtbaar kunnen zijn. Acties worden per-slot gerouteerd
     * door [com.dd.daykit.ui.GlobalTimerStatusBar] (niet door [performInternalBannerAction],
     * die kent alleen de primaire timer).
     */
    private fun updateExtraTimerMessages() {
        val liveIds = mutableSetOf<String>()

        for (slot in ExtraTimerManager.timers) {
            val msgId = "$EXTRA_TIMER_MSG_PREFIX${slot.id}"
            liveIds += msgId

            val shouldShow = slot.state == GlobalTimerManager.TimerState.RUNNING ||
                slot.state == GlobalTimerManager.TimerState.PAUSED

            if (!shouldShow || isDismissed(msgId)) {
                removeMessage(msgId)
                continue
            }

            val timeStr = CountdownFormatter.format(slot.remainingMs, CountdownMode.COMPACT_TRAY)
            val slotLabel = if (slot.name.isNotEmpty()) slot.name else LanguageManager.getString("nav_timer")
            val statusText = if (slot.state == GlobalTimerManager.TimerState.PAUSED) {
                "$slotLabel (${LanguageManager.getString("sw_pause")})"
            } else {
                slotLabel
            }
            val actions = TimerInternalControlOrder.inAppBannerKinds(slot.state)

            addOrUpdateMessage(
                GlobalInAppMessage(
                    id = msgId,
                    type = GlobalMessageType.TIMER,
                    title = statusText,
                    text = timeStr,
                    destination = MessageDestination.TIMER_SCREEN,
                    priority = 9,
                    actions = actions,
                )
            )
        }

        // Ruim banners op van extra timers die intussen gestopt/verwijderd zijn.
        activeMessages
            .filter { it.id.startsWith(EXTRA_TIMER_MSG_PREFIX) && it.id !in liveIds }
            .forEach { removeMessage(it.id) }
    }

    /**
     * Update stopwatch message based on StopwatchStateHolder state.
     * Uses getElapsedTimeMs() which calculates from absolute timestamps.
     */
    private fun updateStopwatchMessage() {
        val stopwatchState = StopwatchStateHolder.stopwatchState.value
        
        // Detect feature restart: IDLE -> RUNNING transition clears dismissed state
        if (prevStopwatchState == StopwatchState.IDLE && 
            stopwatchState == StopwatchState.RUNNING) {
            clearDismissed(MSG_ID_STOPWATCH)
        }
        prevStopwatchState = stopwatchState
        
        // If dismissed, don't show
        if (isDismissed(MSG_ID_STOPWATCH)) {
            removeMessage(MSG_ID_STOPWATCH)
            return
        }
        
        val shouldShow = stopwatchState == StopwatchState.RUNNING || 
                         stopwatchState == StopwatchState.PAUSED
        
        if (shouldShow) {
            // Use timestamp-based calculation for accurate time even after app restart
            val elapsedMs = StopwatchStateHolder.getElapsedTimeMs()
            val timeStr = CountdownFormatter.format(elapsedMs, CountdownMode.COMPACT_TRAY)
            
            val statusText = if (stopwatchState == StopwatchState.PAUSED) {
                "${LanguageManager.getString("screen_stopwatch")} (${LanguageManager.getString("sw_pause")})"
            } else {
                LanguageManager.getString("screen_stopwatch")
            }
            
            val actions = StopwatchInternalControlOrder.inAppBannerKinds(stopwatchState)
            val message = GlobalInAppMessage(
                id = MSG_ID_STOPWATCH,
                type = GlobalMessageType.STOPWATCH,
                title = statusText,
                text = timeStr,
                destination = MessageDestination.STOPWATCH_SCREEN,
                priority = 5,
                actions = actions,
            )
            
            addOrUpdateMessage(message)
        } else {
            removeMessage(MSG_ID_STOPWATCH)
        }
    }
    
    /**
     * Update agenda alarm message if popup is enabled and alarm is within the configured window.
     */
    private fun updateAgendaAlarmMessage(context: Context) {
        val nextAlarmTriggerId = getNextAlarmTriggerId(context)

        // Check if popup is enabled first
        val popupEnabled = SettingsManager.getCalendarPopupLast30Min(context, nextAlarmTriggerId)
        if (!popupEnabled) {
            removeMessage(MSG_ID_AGENDA_ALARM)
            clearDismissed(MSG_ID_AGENDA_ALARM) // Clear when disabled
            return
        }

        // Get configured popup window (in minutes), default 30
        val popupWindowMinutes = SettingsManager.getCalendarPopupWindowMinutes(context, nextAlarmTriggerId)
        val popupWindowMs = popupWindowMinutes * 60 * 1000L
        
        // Get next alarm info from shared preferences or cache
        val nextAlarmTime = getNextAlarmTime(context)
        val ringingItem = AlarmStateManager.ringingAlarmNow
        val showRingingBar = AlarmStateManager.isRingingNow && ringingItem != null

        if (nextAlarmTime != null) {
            val nextId = getNextAlarmId(context)
            if (nextId != null) {
                val probe = AlarmItem(id = nextId, epochMillis = nextAlarmTime, label = "")
                if (!AgendaAlarmLocalActivationStore.isLocallyEnabled(context, probe)) {
                    removeMessage(MSG_ID_AGENDA_ALARM)
                    return
                }
            }
            val now = System.currentTimeMillis()
            val remaining = nextAlarmTime - now
            
            // Track alarm ID to detect new alarm windows
            val currentAlarmId = getNextAlarmId(context)
            if (currentAlarmId != null && currentAlarmId != prevAgendaAlarmId) {
                // New alarm - clear dismissed state
                clearDismissed(MSG_ID_AGENDA_ALARM)
            }
            prevAgendaAlarmId = currentAlarmId
            
            // Clear dismissed state when outside window (so it shows again for next window)
            if (remaining <= 0 || remaining > popupWindowMs) {
                clearDismissed(MSG_ID_AGENDA_ALARM)
            }

            val inPreviewWindow = remaining in 1..popupWindowMs

            // Dismissed hides preview bar only; ringing bar can still show.
            if (isDismissed(MSG_ID_AGENDA_ALARM) && !showRingingBar) {
                removeMessage(MSG_ID_AGENDA_ALARM)
                return
            }

            when {
                showRingingBar -> {
                    addOrUpdateMessage(buildAgendaRingingGlobalMessage(ringingItem!!))
                }
                inPreviewWindow -> {
                    val timeStr = CountdownFormatter.format(remaining, CountdownMode.SHORT_LABEL)
                    val cachedLabel = getNextAlarmLabel(context)?.trim().orEmpty()
                    val title = cachedLabel.ifEmpty { LanguageManager.getString("screen_agenda_alarm") }
                    NotificationPopupDebugLog.notification(
                        source = "GlobalInAppMessageManager.agenda_preview_banner",
                        nextAlarmEpoch = nextAlarmTime,
                        displayedTime = timeStr,
                        notificationRebuilt = false,
                        extra = "cacheId=$currentAlarmId remainingMs=$remaining",
                    )
                    val message = GlobalInAppMessage(
                        id = MSG_ID_AGENDA_ALARM,
                        type = GlobalMessageType.AGENDA_ALARM,
                        title = title,
                        text = timeStr,
                        destination = MessageDestination.AGENDA_ALARM_SCREEN,
                        priority = 20,
                        actions = listOf(InternalInAppBannerActionKind.AGENDA_STOP),
                    )
                    addOrUpdateMessage(message)
                }
                else -> removeMessage(MSG_ID_AGENDA_ALARM)
            }
        } else {
            if (isDismissed(MSG_ID_AGENDA_ALARM) && !showRingingBar) {
                removeMessage(MSG_ID_AGENDA_ALARM)
                return
            }
            if (showRingingBar) {
                addOrUpdateMessage(buildAgendaRingingGlobalMessage(ringingItem!!))
            } else {
                removeMessage(MSG_ID_AGENDA_ALARM)
            }
        }
    }

    private fun buildAgendaRingingGlobalMessage(ringing: AlarmItem): GlobalInAppMessage {
        val title = ringing.label.trim().ifEmpty { LanguageManager.getString("screen_agenda_alarm") }
        return GlobalInAppMessage(
            id = MSG_ID_AGENDA_ALARM,
            type = GlobalMessageType.AGENDA_ALARM,
            title = title,
            text = LanguageManager.getString("alarm_ringing_short"),
            destination = MessageDestination.AGENDA_ALARM_SCREEN,
            priority = 20,
            actions = listOf(
                InternalInAppBannerActionKind.AGENDA_SNOOZE,
                InternalInAppBannerActionKind.AGENDA_STOP,
            ),
        )
    }

    /**
     * Get the next alarm time from shared preferences or cache.
     */
    private fun getNextAlarmTime(context: Context): Long? {
        return try {
            val prefs = context.getSharedPreferences("alarm_cache", Context.MODE_PRIVATE)
            val time = prefs.getLong("next_alarm_time", -1L)
            if (time > 0) time else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get the next alarm ID from shared preferences or cache.
     */
    private fun getNextAlarmId(context: Context): Long? {
        return try {
            val prefs = context.getSharedPreferences("alarm_cache", Context.MODE_PRIVATE)
            val id = prefs.getLong("next_alarm_id", -1L)
            if (id > 0) id else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Cache the next alarm time, ID, and optional calendar title for quick access.
     * Call this from AlarmScheduler when scheduling alarms.
     */
    fun cacheNextAlarmTime(context: Context, alarmTimeMs: Long?, alarmId: Long? = null, alarmLabel: String? = null, triggerId: String? = null) {
        try {
            val prefs = context.getSharedPreferences("alarm_cache", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (alarmTimeMs != null && alarmTimeMs > 0) {
                editor.putLong("next_alarm_time", alarmTimeMs)
            } else {
                editor.remove("next_alarm_time")
            }
            if (alarmId != null && alarmId > 0) {
                editor.putLong("next_alarm_id", alarmId)
            } else {
                editor.remove("next_alarm_id")
            }
            val label = alarmLabel?.trim().orEmpty()
            if (label.isNotEmpty()) {
                editor.putString(PREF_NEXT_ALARM_LABEL, label)
            } else {
                editor.remove(PREF_NEXT_ALARM_LABEL)
            }
            if (!triggerId.isNullOrEmpty()) {
                editor.putString(PREF_NEXT_ALARM_TRIGGER_ID, triggerId)
            } else {
                editor.remove(PREF_NEXT_ALARM_TRIGGER_ID)
            }
            editor.apply()
            NotificationPopupDebugLog.notification(
                source = "GlobalInAppMessageManager.cacheNextAlarmTime",
                nextAlarmEpoch = alarmTimeMs?.takeIf { it > 0 },
                displayedTime = "(prefs_write)",
                notificationRebuilt = false,
                extra = "alarmId=$alarmId label=${alarmLabel?.take(40)}",
            )
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun getNextAlarmTriggerId(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences("alarm_cache", Context.MODE_PRIVATE)
            prefs.getString(PREF_NEXT_ALARM_TRIGGER_ID, null)
        } catch (e: Exception) {
            null
        }
    }

    private fun getNextAlarmLabel(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences("alarm_cache", Context.MODE_PRIVATE)
            prefs.getString(PREF_NEXT_ALARM_LABEL, null)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Zelfde als [dismissMessage] voor het Agenda-vooraf-overzicht: na actie buiten de app
     * (tray, notificatie) verdwijnt de in-app banner direct.
     */
    fun dismissAgendaAlarmInAppOverlayAfterExternalAction() {
        dismissMessage(MSG_ID_AGENDA_ALARM)
    }

    /**
     * Add or update a message in the active list.
     */
    private fun addOrUpdateMessage(message: GlobalInAppMessage) {
        val existingIndex = activeMessages.indexOfFirst { it.id == message.id }
        if (existingIndex >= 0) {
            // Update existing message if content changed
            if (activeMessages[existingIndex] != message) {
                activeMessages[existingIndex] = message
                sortMessages()
            }
        } else {
            activeMessages.add(message)
            sortMessages()
        }
    }
    
    /**
     * Remove a message by ID.
     */
    private fun removeMessage(id: String) {
        activeMessages.removeAll { it.id == id }
    }
    
    /**
     * Sort messages by priority (highest first).
     */
    private fun sortMessages() {
        val sorted = activeMessages.sortedByDescending { it.priority }
        activeMessages.clear()
        activeMessages.addAll(sorted)
    }
    
    /**
     * Navigate to the destination screen for a message.
     */
    fun navigateToDestination(context: Context, destination: MessageDestination) {
        val targetScreen = when (destination) {
            MessageDestination.TIMER_SCREEN -> Screen.TIMER
            MessageDestination.STOPWATCH_SCREEN -> Screen.STOPWATCH
            MessageDestination.AGENDA_ALARM_SCREEN -> Screen.AGENDA_ALARM
        }
        NavigationManager.navigateTo(context, targetScreen)
    }
    
    /**
     * Check if there are any active messages.
     */
    fun hasActiveMessages(): Boolean = activeMessages.isNotEmpty()
    
    /**
     * Get the count of active messages.
     */
    fun getMessageCount(): Int = activeMessages.size

    /**
     * Next calendar alarm from [cacheNextAlarmTime] prefs (id, time, label). Used by in-app banner actions.
     */
    fun buildCachedNextAlarmItem(context: Context): AlarmItem? {
        val app = context.applicationContext
        return try {
            val prefs = app.getSharedPreferences("alarm_cache", Context.MODE_PRIVATE)
            val time = prefs.getLong("next_alarm_time", -1L)
            val id = prefs.getLong("next_alarm_id", -1L)
            if (time <= 0L || id <= 0L) return null
            val label = prefs.getString(PREF_NEXT_ALARM_LABEL, null)?.trim().orEmpty()
            AlarmItem(id = id, epochMillis = time, label = label)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes a banner action (snooze/stop/timer/stopwatch). Safe to call from the main thread from Compose.
     */
    fun performInternalBannerAction(context: Context, kind: InternalInAppBannerActionKind) {
        val app = context.applicationContext
        when (kind) {
            InternalInAppBannerActionKind.ACTION_ROW_EMPTY -> Unit
            InternalInAppBannerActionKind.AGENDA_SNOOZE -> {
                app.sendBroadcast(
                    Intent(app, AlarmActionReceiver::class.java).apply {
                        action = AlarmActionReceiver.ACTION_SNOOZE
                        setPackage(app.packageName)
                    },
                )
            }
            InternalInAppBannerActionKind.AGENDA_STOP -> {
                if (AlarmStateManager.isRingingNow) {
                    app.sendBroadcast(
                        Intent(app, AlarmActionReceiver::class.java).apply {
                            action = AlarmActionReceiver.ACTION_DISMISS
                            setPackage(app.packageName)
                        },
                    )
                } else {
                    val alarm = buildCachedNextAlarmItem(app) ?: return
                    AgendaAlarmLocalActivationCoordinator.deactivateAndReschedule(app, alarm)
                    dismissAgendaAlarmInAppOverlayAfterExternalAction()
                }
            }
            InternalInAppBannerActionKind.TIMER_PLAY -> GlobalTimerManager.resumeTimer(app)
            InternalInAppBannerActionKind.TIMER_PAUSE -> GlobalTimerManager.pauseTimer()
            InternalInAppBannerActionKind.TIMER_STOP -> GlobalTimerManager.resetTimer(app)
            InternalInAppBannerActionKind.TIMER_SAVE -> {
                TimerStateHolder.init(app)
                val remaining = GlobalTimerManager.getRemainingTimeMs()
                if (remaining > 0L) {
                    TimerStateHolder.saveDuration(app, remaining)
                }
            }
            InternalInAppBannerActionKind.STOPWATCH_LAP -> StopwatchStateHolder.addLap()
            InternalInAppBannerActionKind.STOPWATCH_PAUSE -> StopwatchStateHolder.pause()
            InternalInAppBannerActionKind.STOPWATCH_RESUME -> StopwatchStateHolder.resume()
            InternalInAppBannerActionKind.STOPWATCH_SAVE -> StopwatchStateHolder.saveAndReset()
            InternalInAppBannerActionKind.STOPWATCH_STOP -> StopwatchStateHolder.stopWithoutSave()
        }
    }
}
