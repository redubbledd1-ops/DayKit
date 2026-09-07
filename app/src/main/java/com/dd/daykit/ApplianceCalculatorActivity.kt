package com.dd.daykit

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.ScreenRoundIconButton
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.VerticalScrollArrows
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class ApplianceCalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        
        // Initialize ALL state holders for proper state restoration on app restart
        TimerSettingsStateHolder.init(this)
        GlobalTimerManager.init(this)
        StopwatchStateHolder.init(this)
        GlobalInAppMessageManager.init(this)

        setContent {
            MaterialTheme {
                ApplianceCalculatorScreen()
            }
        }
    }
}

@Composable
fun ApplianceCalculatorScreen() {
    val context = LocalContext.current
    val currentScreenId = "APPLIANCE_CALCULATOR"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val showNavButtons = SettingsManager.getShowNavButtons(context)
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    val arrowNavigationEnabled = SettingsManager.getArrowMode(context) != "Verborgen"
    val savePlayButtonGap = if (arrowNavigationEnabled) 68.dp else 16.dp
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 780
    val isVerticalMode = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isRotatedMobileView = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            configuration.smallestScreenWidthDp < 600
    val topSpacing = if (isCompactHeight) 6.dp else 32.dp
    val sectionSpacing = if (isCompactHeight) 8.dp else 24.dp
    val blockSpacing = if (isCompactHeight) 10.dp else 32.dp
    val arrowBottomPadding = if (isVerticalMode) 2.dp else 64.dp

    val currentLanguage by LanguageManager.currentLanguage

    val savedInputs = remember { SettingsManager.getApplianceLastInputs(context) }
    
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
    val initialTarget = if (savedInputs[0] != -1) {
        LocalTime.of(savedInputs[0], savedInputs[1])
    } else {
        LocalTime.now().plusHours(2).withMinute(0)
    }
    
    var targetHour by remember { mutableIntStateOf(initialTarget.hour) }
    var targetMinute by remember { mutableIntStateOf(initialTarget.minute) }
    
    var durationHour by remember { mutableIntStateOf(savedInputs[2]) }
    var durationMinute by remember { mutableIntStateOf(savedInputs[3]) }
    
    var delayHours by remember { mutableIntStateOf(0) }
    var delayMinutes by remember { mutableIntStateOf(0) }
    var startTimeText by remember { mutableStateOf("") }
    var isPossible by remember { mutableStateOf(true) }

    var showHistory by remember { mutableStateOf(false) }

    // Naam van de opgeslagen witgoed-invoer die geselecteerd is via de geschiedenis (leeg = geen
    // selectie, dan valt de gestarte timer terug op de standaardnaam "Witgoed"). Wordt gewist
    // zodra de gebruiker de tijden zelf weer aanpast, zodat de timer nooit de naam van een niet
    // (meer) matchende opgeslagen invoer overneemt.
    var selectedEntryName by remember { mutableStateOf("") }

    BackHandler(enabled = showHistory) {
        showHistory = false
    }

    LaunchedEffect(targetHour, targetMinute, durationHour, durationMinute) {
        SettingsManager.saveApplianceLastInputs(context, targetHour, targetMinute, durationHour, durationMinute)
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            kotlinx.coroutines.delay(10000)
        }
    }

    LaunchedEffect(currentTime, targetHour, targetMinute, durationHour, durationMinute) {
        try {
            val targetTime = LocalTime.of(targetHour, targetMinute)
            val durationMinutesTotal = (durationHour * 60) + durationMinute
            
            val startTime = targetTime.minusMinutes(durationMinutesTotal.toLong())
            startTimeText = String.format("%02d:%02d", startTime.hour, startTime.minute)
            
            var minutesUntilStart = ChronoUnit.MINUTES.between(currentTime, startTime)
            
            if (minutesUntilStart < 0) {
                minutesUntilStart += 24 * 60
            }
            
            delayHours = (minutesUntilStart / 60).toInt()
            delayMinutes = (minutesUntilStart % 60).toInt()
            isPossible = true
            
        } catch (e: Exception) {
            isPossible = false
        }
    }

    if (showHistory) {
        ApplianceHistoryScreen(
            onBack = { showHistory = false },
            onSelectEntry = { tH, tM, dH, dM, name ->
                targetHour = tH
                targetMinute = tM
                durationHour = dH
                durationMinute = dM
                selectedEntryName = name
                showHistory = false
            }
        )
    } else {
        AppBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                            var dragY = 0f
                            var dragX = 0f
                            var triggered = false

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull()
                                if (change == null || !change.pressed) break

                                if (!triggered) {
                                    if (!swipeEnabled) continue
                                    dragX += change.position.x - change.previousPosition.x
                                    dragY += change.position.y - change.previousPosition.y
                                    
                                    val dragThreshold = 50f

                                    if (abs(dragX) > abs(dragY)) {
                                         if (dragX > dragThreshold) { // Swipe Right (to go back)
                                             NavigationManager.navigateLeft(context, currentScreenId)
                                             triggered = true 
                                         } else if (dragX < -dragThreshold) { // Swipe Left (to next)
                                             NavigationManager.navigateRight(context, currentScreenId)
                                             triggered = true
                                         }
                                    } else {
                                         if (dragY < -dragThreshold) { // Swipe Up
                                            showHistory = true
                                            triggered = true
                                         }
                                    }
                                }
                            }
                        }
                    }
            ) {
                val leftTarget = NavigationManager.getSwipeLeftTarget(context, currentScreenId)
                val rightTarget = NavigationManager.getSwipeRightTarget(context, currentScreenId)
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .then(if (isRotatedMobileView) Modifier else Modifier.verticalScroll(rememberScrollState()))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(Modifier.height(topSpacing))

                    if (isRotatedMobileView) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .offset(x = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(LanguageManager.getString("calc_target_short"), style = MaterialTheme.typography.titleMedium, color = textColor)
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TimeInput(targetHour, { targetHour = it; selectedEntryName = "" }, 0..23, LanguageManager.getString("hour"), textColor, compact = false)
                                    Text(":", style = MaterialTheme.typography.headlineMedium, color = textColor, modifier = Modifier.padding(horizontal = 8.dp).offset(y = (-8).dp))
                                    TimeInput(targetMinute, { targetMinute = it; selectedEntryName = "" }, 0..59, LanguageManager.getString("min"), textColor, compact = false)
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(LanguageManager.getString("calc_duration_short"), style = MaterialTheme.typography.titleMedium, color = textColor)
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TimeInput(durationHour, { durationHour = it; selectedEntryName = "" }, 0..12, LanguageManager.getString("hour"), textColor, compact = false)
                                    Text(":", style = MaterialTheme.typography.headlineMedium, color = textColor, modifier = Modifier.padding(horizontal = 8.dp).offset(y = (-8).dp))
                                    TimeInput(durationMinute, { durationMinute = it; selectedEntryName = "" }, 0..59, LanguageManager.getString("min"), textColor, compact = false)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .offset(x = (-6).dp)
                                    .padding(top = 0.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Startuitstel:", style = MaterialTheme.typography.titleMedium, color = textColor)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "${delayHours}u ${delayMinutes}m",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${LanguageManager.getString("calc_start_at")} $startTimeText",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        val targetLabelKey = if (isVerticalMode) "calc_target_short" else "calc_target_label"
                        val durationLabelKey = if (isVerticalMode) "calc_duration_short" else "calc_duration_label"
                        val delayLabelKey = if (isVerticalMode) "calc_delay_short" else "calc_set_delay"
                        val topColonPortraitOffset = 37.dp
                        val bottomColonPortraitOffset = (-17).dp

                        Text(
                            LanguageManager.getString(targetLabelKey),
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor
                        )
                        Spacer(Modifier.height(if (isCompactHeight) 4.dp else 8.dp))
                        Row(
                            horizontalArrangement = if (isVerticalMode) Arrangement.Center else Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = if (isVerticalMode) Modifier else Modifier.offset(x = 6.dp)) {
                                TimeInput(
                                    targetHour,
                                    { targetHour = it; selectedEntryName = "" },
                                    0..23,
                                    LanguageManager.getString("hour"),
                                    textColor,
                                    compact = isCompactHeight,
                                    edgeAlignedArrows = isVerticalMode
                                )
                                Text(":", style = if (isCompactHeight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall, color = textColor, modifier = Modifier.padding(horizontal = if (isCompactHeight) 6.dp else 8.dp).offset(y = if (isVerticalMode) topColonPortraitOffset else (-8).dp))
                                TimeInput(
                                    targetMinute,
                                    { targetMinute = it; selectedEntryName = "" },
                                    0..59,
                                    LanguageManager.getString("min"),
                                    textColor,
                                    compact = isCompactHeight,
                                    edgeAlignedArrows = isVerticalMode
                                )
                            }
                        }

                        Spacer(Modifier.height(sectionSpacing))

                        Text(
                            LanguageManager.getString(durationLabelKey),
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor
                        )
                        Spacer(Modifier.height(if (isCompactHeight) 4.dp else 8.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimeInput(
                                durationHour,
                                { durationHour = it; selectedEntryName = "" },
                                0..12,
                                LanguageManager.getString("hour"),
                                textColor,
                                compact = isCompactHeight,
                                edgeAlignedArrows = isVerticalMode
                            )
                            Text(":", style = if (isCompactHeight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall, color = textColor, modifier = Modifier.padding(horizontal = if (isCompactHeight) 6.dp else 8.dp).offset(y = if (isVerticalMode) bottomColonPortraitOffset else (-8).dp))
                            TimeInput(
                                durationMinute,
                                { durationMinute = it; selectedEntryName = "" },
                                0..59,
                                LanguageManager.getString("min"),
                                textColor,
                                compact = isCompactHeight,
                                edgeAlignedArrows = isVerticalMode
                            )
                        }

                        Spacer(Modifier.height(blockSpacing))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isVerticalMode) Modifier else Modifier.offset(x = (-6).dp))
                                .padding(if (isCompactHeight) 8.dp else 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    LanguageManager.getString(delayLabelKey),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = textColor
                                )
                                Spacer(Modifier.height(if (isCompactHeight) 2.dp else 4.dp))
                                Text(
                                    text = "${delayHours}u ${delayMinutes}m",
                                    style = if (isCompactHeight) {
                                        MaterialTheme.typography.headlineSmall
                                    } else {
                                        MaterialTheme.typography.headlineMedium
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(Modifier.height(if (isCompactHeight) 2.dp else 4.dp))
                                Text(
                                    "${LanguageManager.getString("calc_start_at")} $startTimeText",
                                    style = if (isCompactHeight) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(if (isRotatedMobileView) 28.dp else sectionSpacing))

                    // Check if timer is running
                    val timerState by GlobalTimerManager.timerState
                    val isTimerRunning = timerState == GlobalTimerManager.TimerState.RUNNING || 
                                         timerState == GlobalTimerManager.TimerState.PAUSED
                    
                    // Calculate time until appliance is ready (in milliseconds)
                    val timeUntilReadyMs = remember(delayHours, delayMinutes, durationHour, durationMinute) {
                        val delayMs = (delayHours * 60 + delayMinutes) * 60 * 1000L
                        val durationMs = (durationHour * 60 + durationMinute) * 60 * 1000L
                        delayMs + durationMs
                    }

                    Row(
                        modifier = if (isRotatedMobileView) {
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        horizontalArrangement = Arrangement.spacedBy(savePlayButtonGap, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Opslaan (links) — zelfde ScreenRoundIconButton + Save als Timer
                        ScreenRoundIconButton(
                            onClick = {
                                try {
                                    SettingsManager.saveApplianceHistoryEntry(context, targetHour, targetMinute, durationHour, durationMinute)
                                    showHistory = true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            imageVector = Icons.Filled.Save,
                            contentDescription = LanguageManager.getString("save"),
                            containerColor = buttonColor,
                            contentColor = buttonTextColor,
                        )

                        // Start timer tot witgoed klaar is — zelfde Play-icoon als Timer/Stopwatch
                        if (!isTimerRunning && timeUntilReadyMs > 0) {
                            ScreenRoundIconButton(
                                onClick = {
                                    val timerName = selectedEntryName.ifBlank {
                                        LanguageManager.getString("appliance_default_timer_name")
                                    }
                                    GlobalTimerManager.startTimer(context, timeUntilReadyMs, timerName)
                                },
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = LanguageManager.getString("time_witgoed"),
                                containerColor = buttonColor,
                                contentColor = buttonTextColor,
                            )
                        }
                    }
                }
                
                // SwipeIndicators moved to end of Box to be on top
                SwipeIndicators(
                    isVertical = isVerticalMode,
                    showLeft = leftTarget != null,
                    showRight = rightTarget != null,
                    showDown = true, 
                    downBottomPadding = arrowBottomPadding,
                    onSwipeLeft = { NavigationManager.navigateLeft(context, currentScreenId) },
                    onSwipeRight = { NavigationManager.navigateRight(context, currentScreenId) },
                    onSwipeDown = { showHistory = true }
                )

                if (showNavButtons) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                    ) {
                        NavigationBar(currentPage = currentScreenId)
                    }
                }
            }
        }
    }
}

@Composable
fun ApplianceHistoryScreen(
    onBack: () -> Unit,
    onSelectEntry: (Int, Int, Int, Int, String) -> Unit
) {
    val context = LocalContext.current
    val arrowNavigationEnabled = SettingsManager.getArrowMode(context) != "Verborgen"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    val currentLanguage by LanguageManager.currentLanguage

    // Trigger recomposition when list changes (using a simple state for refresh)
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val historyList = remember(refreshTrigger) { SettingsManager.getApplianceHistory(context) }

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var itemToRenameIndex by remember { mutableIntStateOf(-1) }
    var newName by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(LanguageManager.getString("confirm_delete_title")) },
            text = { Text(LanguageManager.getString("calc_confirm_delete")) },
            confirmButton = {
                Button(
                    onClick = {
                        SettingsManager.clearApplianceHistory(context)
                        refreshTrigger++
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("delete"))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirmation = false },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(LanguageManager.getString("calc_rename_title")) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(LanguageManager.getString("name_placeholder")) }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (itemToRenameIndex != -1) {
                            SettingsManager.updateApplianceHistoryName(context, itemToRenameIndex, newName)
                            refreshTrigger++
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("save"))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showRenameDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        if (!swipeEnabled) return@detectDragGestures
                        if (dragAmount.y > 20) {
                            change.consume()
                            onBack()
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(LanguageManager.getString("calc_saved_history"), style = MaterialTheme.typography.headlineSmall, color = textColor)
                Spacer(Modifier.height(16.dp))

                // Navigation Arrow (Back) - Placed here instead of small scroll up
                if (arrowNavigationEnabled) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Back",
                        tint = textColor.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onBack() }
                    )
                }
                Spacer(Modifier.height(16.dp))

                // Toont alleen hele kaarten: hoogte beperkt tot een exact veelvoud van
                // (item-hoogte + spacing) i.p.v. de volledige resterende ruimte te vullen,
                // anders blijft er een fractie van een kaart zichtbaar aan de onderkant.
                val density = LocalDensity.current
                var availableHeightPx by remember { mutableStateOf(0) }
                val itemHeightPx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                val itemSpacingPx = with(density) { 16.dp.toPx() }

                val lazyColumnHeightModifier = if (itemHeightPx > 0 && availableHeightPx > 0) {
                    val unitPx = itemHeightPx + itemSpacingPx
                    val whole = kotlin.math.floor(availableHeightPx / unitPx).toInt().coerceAtLeast(1)
                    Modifier.height(with(density) { (whole * unitPx).toDp() })
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
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp), // Side padding for edge swipe
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(historyList) { index, entry ->
                        var expanded by remember { mutableStateOf(false) }
                        val tH = entry.targetHour; val tM = entry.targetMinute
                        val dH = entry.durationHour; val dM = entry.durationMinute

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Column(
                                modifier = Modifier
                                    // Extra ruimte rechts (36dp icoon + 8dp marge + buffer) zodat
                                    // naam/subtitel en Edit-knop nooit onder VerticalScrollArrows
                                    // doorlopen (zelfde fix als Aankomende alarms).
                                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 56.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: Click to select
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onSelectEntry(tH, tM, dH, dM, entry.name) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            val readyAtLabel = LanguageManager.getString("calc_ready_at")
                                            val durationLabel = LanguageManager.getString("calc_duration")
                                            
                                            val displayName = if (entry.name.isNotEmpty()) entry.name else "$readyAtLabel ${String.format("%02d:%02d", tH, tM)}"
                                            Text(
                                                text = displayName,
                                                color = textColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (entry.name.isNotEmpty()) {
                                                 Text(
                                                    text = "$readyAtLabel ${String.format("%02d:%02d", tH, tM)}, $durationLabel ${dH}u ${dM}m",
                                                    color = textColor.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            } else {
                                                Text(
                                                    text = "$durationLabel ${dH}u ${dM}m",
                                                    color = textColor.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }

                                    // Right: Edit button
                                    IconButton(onClick = { expanded = !expanded }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = textColor
                                        )
                                    }
                                }

                                if (expanded) {
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                itemToRenameIndex = index
                                                newName = entry.name
                                                showRenameDialog = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                                        ) {
                                            Text(LanguageManager.getString("rename"))
                                        }
                                        Button(
                                            onClick = {
                                                SettingsManager.deleteApplianceHistoryEntry(context, index)
                                                refreshTrigger++
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f), contentColor = Color.White)
                                        ) {
                                            Text(LanguageManager.getString("delete_short"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                    ) { Text(LanguageManager.getString("back")) }

                    if (historyList.isNotEmpty()) {
                        Button(
                            onClick = { showDeleteConfirmation = true },
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                        ) { Text(LanguageManager.getString("delete_all")) }
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
        }
    }
}

@Composable
private fun InteractionSource.collectIsPressedAsState(): State<Boolean> {
    val isPressed = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed.value = true
                is PressInteraction.Release, is PressInteraction.Cancel -> isPressed.value = false
            }
        }
    }
    return isPressed
}

@Composable
fun TimeInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    textColor: Color,
    compact: Boolean = false,
    edgeAlignedArrows: Boolean = false
) {
    val upInteractionSource = remember { MutableInteractionSource() }
    val downInteractionSource = remember { MutableInteractionSource() }
    val isUpPressed by upInteractionSource.collectIsPressedAsState()
    val isDownPressed by downInteractionSource.collectIsPressedAsState()

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(isUpPressed) {
        if (isUpPressed) {
            delay(300)
            var delayMs = 200L
            while (isUpPressed) {
                var newValue = currentValue + 1
                if (newValue > range.last) newValue = range.first
                currentOnValueChange(newValue)
                delay(delayMs)
                delayMs = (delayMs * 0.8).toLong().coerceAtLeast(30L)
            }
        }
    }

    LaunchedEffect(isDownPressed) {
        if (isDownPressed) {
            delay(300)
            var delayMs = 200L
            while (isDownPressed) {
                var newValue = currentValue - 1
                if (newValue < range.first) newValue = range.last
                currentOnValueChange(newValue)
                delay(delayMs)
                delayMs = (delayMs * 0.8).toLong().coerceAtLeast(30L)
            }
        }
    }

    var cumulativeDrag by remember { mutableStateOf(0f) }
    val dragThreshold = 20f

    fun adjustValue(delta: Int) {
        var newValue = value + delta
        if (newValue > range.last) newValue = range.first
        if (newValue < range.first) newValue = range.last
        onValueChange(newValue)
    }

    val upArrowOffset = if (edgeAlignedArrows) (-14).dp else 0.dp
    val downArrowOffset = if (edgeAlignedArrows) 14.dp else 0.dp
    val valueBoxTopPadding = if (edgeAlignedArrows) 2.dp else 0.dp
    val labelTopPadding = if (edgeAlignedArrows) 10.dp else 0.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(if (compact) 60.dp else 70.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Omhoog",
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 22.dp else 30.dp)
                .offset(y = upArrowOffset)
                .clickable(interactionSource = upInteractionSource, indication = null) { adjustValue(1) }
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 44.dp else 60.dp)
                .padding(top = valueBoxTopPadding)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { cumulativeDrag = 0f },
                        onDragCancel = { cumulativeDrag = 0f }
                    ) { _, dragAmount ->
                        cumulativeDrag += dragAmount
                        if (cumulativeDrag <= -dragThreshold) {
                            adjustValue(1)
                            cumulativeDrag = 0f
                        } else if (cumulativeDrag >= dragThreshold) {
                            adjustValue(-1)
                            cumulativeDrag = 0f
                        }
                    }
                }
        ) {
            Text(
                text = String.format("%02d", value),
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                color = textColor
            )
        }

        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Omlaag",
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 22.dp else 30.dp)
                .offset(y = downArrowOffset)
                .clickable(interactionSource = downInteractionSource, indication = null) { adjustValue(-1) }
        )

        Text(
            label,
            color = textColor.copy(alpha = 0.7f),
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = labelTopPadding)
        )
    }
}
