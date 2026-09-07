package com.dd.daykit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.rules.PreAlarmCancelReason
import com.dd.daykit.rules.PreAlarmCheckStorage
import com.dd.daykit.rules.PreAlarmUiStatus
import com.dd.daykit.ui.AgendaAlarmLocalActivationConfirmDialog
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.VerticalScrollArrows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class UpcomingAlarmsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        LanguageManager.init(this)

        setContent {
            MaterialTheme {
                UpcomingAlarmsScreen { finish() }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UpcomingAlarmsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    
    // Force recomposition on language change
    val currentLanguage by LanguageManager.currentLanguage
    
    var alarms by remember { mutableStateOf<List<AlarmItem>>(emptyList()) }
    var listRefresh by remember { mutableIntStateOf(0) }
    var pendingToggle by remember { mutableStateOf<AlarmItem?>(null) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentLanguage.code, listRefresh) {
        alarms = withContext(Dispatchers.IO) {
            getUpcomingWakeUpEvents(context, CalendarView.WEEK)
        }
    }

    pendingToggle?.let { alarm ->
        val locallyEnabled = AgendaAlarmLocalActivationStore.isLocallyEnabled(context, alarm)
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.epochMillis))
        AgendaAlarmLocalActivationConfirmDialog(
            title = if (locallyEnabled) {
                LanguageManager.getString("agenda_alarm_local_deactivate_confirm_title")
            } else {
                LanguageManager.getString("agenda_alarm_local_activate_confirm_title")
            },
            timeHHmm = timeStr,
            eventLabel = alarm.label.ifBlank { "" },
            onDismiss = { pendingToggle = null },
            onConfirm = {
                if (locallyEnabled) {
                    AgendaAlarmLocalActivationCoordinator.deactivateAndReschedule(context, alarm)
                } else {
                    AgendaAlarmLocalActivationCoordinator.activateAndReschedule(context, alarm)
                }
                pendingToggle = null
                listRefresh++
            },
        )
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            // Swipe Down (y > 0) -> Back (Same logic as swiping away the sheet)
                            // Or Swipe UP? Usually if sheet comes from bottom, swipe down closes it.
                            // If screen is "below" main, swipe UP to go back? 
                            // Original logic was: DragAmount > 20 (Down) -> Back.
                            if (dragAmount.y > 20) {
                                change.consume()
                                onBack()
                            }
                        }
                    }
                    .padding(16.dp)
            ) {
                Text(
                    LanguageManager.getString("upcoming_title"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = textColor,
                    modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally)
                )

                // Navigation Arrow at the top
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Back",
                    tint = textColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally)
                        .clickable { onBack() }
                )

                if (alarms.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(LanguageManager.getString("upcoming_none"), color = textColor)
                    }
                } else {
                    val preAlarmStorage = remember { PreAlarmCheckStorage(context) }

                    // Toont alleen hele rijen: hoogte beperkt tot precies zoveel rijen als er
                    // heel passen i.p.v. de volledige resterende ruimte te vullen, anders blijft
                    // er een fractie van een rij zichtbaar aan de onderkant. Rijen kunnen
                    // verschillend hoog zijn (een agenda-item met een lange naam wrapt naar 2+
                    // regels) - daarom wordt hier per item de écht gemeten hoogte opgeteld i.p.v.
                    // uit te gaan van 1 vaste hoogte voor alle rijen; anders kon precies zo'n
                    // meerregelige rij aan de onderkant half worden afgesneden.
                    // rowSpacing wordt hier én in verticalArrangement gebruikt zodat de
                    // "hoeveel hele rijen passen er"-berekening nooit uit de pas loopt met de
                    // echte rij-afstand (dat gaf eerder onterecht onbenutte ruimte onderaan,
                    // bv. waar voorheen een navigatiebalk stond).
                    val density = LocalDensity.current
                    var availableHeightPx by remember { mutableStateOf(0) }
                    val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
                    val rowSpacing = 6.dp
                    val itemSpacingPx = with(density) { rowSpacing.toPx() }

                    val lazyColumnHeightModifier = if (visibleItemsInfo.isNotEmpty() && availableHeightPx > 0) {
                        var cumulativePx = 0f
                        var fittingCount = 0
                        for ((index, item) in visibleItemsInfo.withIndex()) {
                            val spacingBefore = if (index == 0) 0f else itemSpacingPx
                            val next = cumulativePx + spacingBefore + item.size
                            // Altijd minstens 1 rij tonen, ook al is die zelf al hoger dan de
                            // beschikbare ruimte (bv. een erg lange, meerregelige naam).
                            if (fittingCount == 0 || next <= availableHeightPx) {
                                cumulativePx = next
                                fittingCount++
                            } else {
                                break
                            }
                        }
                        Modifier.height(with(density) { cumulativePx.toDp() })
                    } else {
                        Modifier.fillMaxHeight()
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onSizeChanged { availableHeightPx = it.height }
                    ) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = false,
                            modifier = Modifier
                                .then(lazyColumnHeightModifier)
                                .fillMaxWidth(),
                            // Iets krapper dan voorheen (was 8dp) zodat er meestal 7 alarms
                            // tegelijk zichtbaar zijn zonder te hoeven scrollen — bij 8+ blijft
                            // scrollen (via de pijltjes) gewoon nog steeds mogelijk.
                            verticalArrangement = Arrangement.spacedBy(rowSpacing)
                        ) {
                            items(alarms) { alarm ->
                                val preAlarmStatus = preAlarmStorage.getActivePreAlarmStatus(alarm.id, alarm.epochMillis)
                                val locallyEnabled = AgendaAlarmLocalActivationStore.isLocallyEnabled(context, alarm)
                                AlarmItemRow(
                                    alarm = alarm,
                                    textColor = textColor,
                                    cardColor = buttonColor,
                                    preAlarmStatus = preAlarmStatus,
                                    isLocallyEnabled = locallyEnabled,
                                    onRowClick = { pendingToggle = alarm },
                                )
                            }
                        }
                    }
                }
            }

            VerticalScrollArrows(
                listState = listState,
                coroutineScope = coroutineScope,
                tint = textColor.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 8.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
            )

            // Removed bottom button as per implicit request to clean up navigation
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmItemRow(
    alarm: AlarmItem,
    textColor: Color,
    cardColor: Color,
    preAlarmStatus: PreAlarmUiStatus? = null,
    isLocallyEnabled: Boolean,
    onRowClick: () -> Unit,
) {
    var showDebug by remember { mutableStateOf(false) }
    val primaryText = if (isLocallyEnabled) textColor else textColor.copy(alpha = 0.48f)
    val secondaryText = if (isLocallyEnabled) textColor.copy(alpha = 0.7f) else textColor.copy(alpha = 0.38f)
    val timeColor = if (isLocallyEnabled) textColor else textColor.copy(alpha = 0.45f)
    val rowAlpha = if (isLocallyEnabled) 1f else 0.72f

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = rowAlpha }
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = { showDebug = !showDebug },
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Top/bottom was 16dp - iets krapper (10dp) zodat er meestal 7 rijen tegelijk
                // passen zonder scrollen, zie verticalArrangement hierboven.
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 72.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (!isLocallyEnabled) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.45f),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = formatFriendlyDate(alarm.epochMillis),
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryText,
                            textDecoration = if (!isLocallyEnabled) TextDecoration.LineThrough else null,
                        )
                        if (alarm.label.isNotEmpty()) {
                            Text(
                                text = alarm.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText,
                                textDecoration = if (!isLocallyEnabled) TextDecoration.LineThrough else null,
                            )
                        }
                    }
                }
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.epochMillis)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = timeColor,
                    textDecoration = if (!isLocallyEnabled) TextDecoration.LineThrough else null,
                )
            }

            // Pre-alarm debug info (expandable) — lang indrukken op de rij
            if (preAlarmStatus != null) {
                Spacer(Modifier.height(8.dp))
                PreAlarmDebugCard(preAlarmStatus, textColor, showDebug)
            }
        }
    }
}

/**
 * Debug card showing pre-alarm check details.
 * Tap the alarm row to expand/collapse.
 */
@Composable
fun PreAlarmDebugCard(status: PreAlarmUiStatus, textColor: Color, expanded: Boolean) {
    val statusColor = if (status.willAlarmFire) Color(0xFF4CAF50) else Color(0xFFFF9800)
    val statusText = if (status.willAlarmFire) "✓ Alarm zal afgaan" else "✗ Alarm geannuleerd"
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧠 Pre-alarm check",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.8f)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
            
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Divider(color = textColor.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))
                
                // Debug details
                DebugRow("Reden", getReasonText(status.reason), textColor)
                DebugRow("Confidence", "${status.confidence}%", textColor)
                DebugRow("Duur uit bed", formatDuration(status.duration), textColor)
                status.sensorValue?.let {
                    DebugRow("Sensor waarde", it, textColor)
                }
                DebugRow("Check tijd", formatTime(status.checkTimestamp), textColor)
                
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "(tik om te verbergen)",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    text = "(tik voor details)",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}

private fun getReasonText(reason: PreAlarmCancelReason): String {
    return when (reason) {
        PreAlarmCancelReason.NOT_CHECKED -> "Niet gecontroleerd"
        PreAlarmCancelReason.CHECK_DISABLED -> "Check uitgeschakeld"
        PreAlarmCancelReason.USER_IN_BED -> "Gebruiker in bed"
        PreAlarmCancelReason.USER_OUT_OF_BED -> "Gebruiker uit bed"
        PreAlarmCancelReason.SENSOR_ERROR -> "Sensor fout"
        PreAlarmCancelReason.SENSOR_UNRELIABLE -> "Sensor onbetrouwbaar"
        PreAlarmCancelReason.HA_CONNECTION_ERROR -> "HA connectie fout"
        PreAlarmCancelReason.INSUFFICIENT_DURATION -> "Te kort uit bed"
        PreAlarmCancelReason.USER_NOT_HOME -> "Gebruiker niet thuis"
        PreAlarmCancelReason.PRESENCE_ERROR -> "Presence check fout"
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 sec"
    val minutes = ms / 60000
    val seconds = (ms % 60000) / 1000
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

fun formatFriendlyDate(epochMillis: Long): String {
    val now = Calendar.getInstance()
    val alarmTime = Calendar.getInstance().apply { timeInMillis = epochMillis }
    
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    return when {
        isSameDay(alarmTime, today) -> LanguageManager.getString("today")
        isSameDay(alarmTime, tomorrow) -> LanguageManager.getString("tomorrow")
        else -> SimpleDateFormat("EEEE d MMM", LanguageManager.getLocale()).format(alarmTime.time).replaceFirstChar { it.uppercase() }
    }
}

fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
