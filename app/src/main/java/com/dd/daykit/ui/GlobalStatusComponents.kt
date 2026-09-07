package com.dd.daykit.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dd.daykit.AlarmScheduler
import com.dd.daykit.AlarmStateManager
import com.dd.daykit.ExtraTimerManager
import com.dd.daykit.GlobalInAppMessageManager
import com.dd.daykit.InternalInAppBannerActionKind
import com.dd.daykit.LanguageManager
import com.dd.daykit.MainActivity
import com.dd.daykit.R
import com.dd.daykit.TimerStateHolder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Snooze
import com.dd.daykit.SettingsManager
import com.dd.daykit.rememberInAppNotificationsEnabled
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

private fun formatSnoozeRemainingCountdown(remainingMillis: Long): String {
    val totalSeconds = remainingMillis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/**
 * Agenda-slomer — dezelfde vaste-hoogte strip als timer/agenda ([UnifiedInternalMessageBannerRow]).
 */
@Composable
fun GlobalSnoozeBanner(
    modifier: Modifier = Modifier
) {
    if (!rememberInAppNotificationsEnabled()) return

    val context = LocalContext.current
    val isSnoozeActive by AlarmStateManager.isSnoozeActive.collectAsState()
    val snoozeEndTime by AlarmStateManager.snoozeEndTime.collectAsState()

    if (!isSnoozeActive || snoozeEndTime <= 0L) return

    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))

    var countdownLabel by remember(snoozeEndTime) {
        mutableStateOf(formatSnoozeRemainingCountdown(snoozeEndTime - System.currentTimeMillis()))
    }

    LaunchedEffect(snoozeEndTime) {
        val haSettingsStorage = HomeAssistantSettingsStorage(context)
        val haRepository = HomeAssistantRepository(HomeAssistantClient, haSettingsStorage)
        var tick = 0
        while (isActive && snoozeEndTime > 0L) {
            val remaining = snoozeEndTime - System.currentTimeMillis()
            if (remaining <= 0L) break
            countdownLabel = formatSnoozeRemainingCountdown(remaining)
            if (tick % 10 == 0) {
                try {
                    val haSettings = haRepository.getSettings()
                    if (haSettings.outOfBedCheckEnabled && !haSettings.outOfBedEntityId.isNullOrBlank()) {
                        val bedEntityId = haSettings.outOfBedEntityId.orEmpty()
                        val inBedValue = haSettings.outOfBedExpectedValue.trim().lowercase()
                        val response = haRepository.getEntityState(bedEntityId)
                        val currentState = response.state.trim().lowercase()
                        if (currentState != inBedValue && currentState != "unknown" && currentState != "unavailable") {
                            AlarmScheduler.cancelAgendaSnoozeUser(context)
                            break
                        }
                    }
                } catch (_: Exception) {
                }
            }
            tick++
            delay(1000L)
        }
    }

    fun openAgendaAlarm() {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_AGENDA_ALARM, true)
        }
        context.startActivity(i)
    }

    SideEffect {
        InternalInAppBannerLog.render("GlobalSnoozeBanner", "snooze_strip", "UnifiedInternalMessageBannerRow")
    }

    UnifiedInternalMessageBannerRow(
        modifier = modifier.fillMaxWidth(),
        pipeline = "GlobalSnoozeBanner",
        slot = "snooze_strip",
        contentColor = buttonTextColor,
        leadingIcon = Icons.Outlined.Snooze,
        title = LanguageManager.getString("snooze_active"),
        trailingText = countdownLabel,
        actions = emptyList(),
        includeRowBackground = true,
        rowBackgroundColor = buttonColor.copy(alpha = 0.7f),
        reservedActionSlots = 0,
        endClose = { AlarmScheduler.cancelAgendaSnoozeUser(context) },
        endCloseContentDescription = context.getString(R.string.snooze_dismiss_cd),
        onTitleAreaClick = { openAgendaAlarm() },
    )
}

/**
 * Global Status Bar - Shows active messages (Timer, Stopwatch, Agenda Alarm)
 * Each row is clickable and navigates to the corresponding screen.
 * Swipe left or right to dismiss (feature continues running).
 * Uses GlobalInAppMessageManager as the data source.
 */
@Composable
fun GlobalTimerStatusBar(
    modifier: Modifier = Modifier
) {
    if (!rememberInAppNotificationsEnabled()) return

    val context = LocalContext.current
    val messages = GlobalInAppMessageManager.activeMessages

    // Only show when there are active messages
    if (messages.isEmpty()) return

    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))

    // Stack multiple messages vertically
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        messages.toList().forEach { message ->
            // Berichten van de 2e/3e timer moeten op hun EIGEN ExtraTimerManager-slot werken,
            // niet op de primaire GlobalTimerManager — anders drukt Play/Pause/Stop/Save op
            // de banner van timer 2 per ongeluk de primaire timer aan.
            val extraSlotId = GlobalInAppMessageManager.extraTimerSlotIdFromMessageId(message.id)
            val extraSlot = extraSlotId?.let { id -> ExtraTimerManager.timers.find { it.id == id } }
            val extraTimerActionHandler: ((Context, InternalInAppBannerActionKind) -> Unit)? =
                if (extraSlot != null) {
                    { ctx, kind ->
                        when (kind) {
                            InternalInAppBannerActionKind.TIMER_PLAY -> ExtraTimerManager.resumeTimer(ctx, extraSlot)
                            InternalInAppBannerActionKind.TIMER_PAUSE -> ExtraTimerManager.pauseTimer(ctx, extraSlot)
                            InternalInAppBannerActionKind.TIMER_STOP -> ExtraTimerManager.removeTimer(ctx, extraSlot)
                            InternalInAppBannerActionKind.TIMER_SAVE -> {
                                if (extraSlot.initialMs > 0L) TimerStateHolder.saveDuration(ctx, extraSlot.initialMs)
                            }
                            else -> Unit
                        }
                    }
                } else {
                    null
                }

            SwipeableDismissableRow(
                messageId = message.id,
                backgroundColor = buttonColor.copy(alpha = 0.7f),
                onDismiss = {
                    GlobalInAppMessageManager.dismissMessage(message.id)
                },
            ) {
                SideEffect {
                    InternalInAppBannerLog.render(
                        "GlobalTimerStatusBar",
                        "message_row:${message.id}",
                        "type=${message.type} dest=${message.destination}"
                    )
                }
                UnifiedInternalMessageBannerRow(
                    pipeline = "GlobalTimerStatusBar",
                    slot = "message:${message.id}",
                    contentColor = buttonTextColor,
                    leadingIcon = globalMessageLeadingIcon(message.type),
                    title = message.title,
                    trailingText = message.text,
                    actions = message.actions,
                    includeRowBackground = false,
                    rowBackgroundColor = Color.Transparent,
                    reservedActionSlots = InternalInAppBannerDim.GLOBAL_MESSAGE_ACTION_SLOTS,
                    onInternalBannerAction = extraTimerActionHandler,
                    onTitleAreaClick = {
                        GlobalInAppMessageManager.navigateToDestination(context, message.destination)
                    },
                )
            }
        }
    }
}

/**
 * A row that can be swiped left or right to dismiss.
 */
@Composable
private fun SwipeableDismissableRow(
    messageId: String,
    backgroundColor: Color,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val dismissThreshold = 150f

    SideEffect {
        InternalInAppBannerLog.render(
            "GlobalTimerStatusBar",
            "SwipeableDismissableRow:$messageId",
            "swipeDismissHost bgAlpha=${backgroundColor.alpha}"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .background(backgroundColor)
            .pointerInput(messageId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(offsetX) > dismissThreshold) {
                            onDismiss()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = {
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            }
    ) {
        content()
    }
}

/**
 * Wrapper composable that includes all global status components
 * Use this at the top of each screen's content
 */
@Composable
fun GlobalStatusBanners(
    modifier: Modifier = Modifier
) {
    if (!rememberInAppNotificationsEnabled()) return

    Column(modifier = modifier) {
        GlobalSnoozeBanner()
        GlobalTimerStatusBar()
    }
}
