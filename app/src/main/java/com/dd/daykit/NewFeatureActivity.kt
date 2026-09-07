package com.dd.daykit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dd.daykit.ui.InternalInAppBannerDim
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.ScreenRoundIconButton
import com.dd.daykit.ui.VerticalScrollArrows
import kotlinx.coroutines.launch
import kotlin.math.abs

class NewFeatureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        
        // Initialize ALL state holders for proper state restoration on app restart
        // This ensures the global message bar shows correct status immediately
        TimerSettingsStateHolder.init(this)
        GlobalTimerManager.init(this)
        StopwatchStateHolder.init(this)
        GlobalInAppMessageManager.init(this)

        setContent {
            MaterialTheme {
                NewFeatureScreen()
            }
        }
    }
}

/**
 * !!! BELANGRIJKE INSTRUCTIE VOOR WINDSURF / AI: !!!
 * 
 * De navigatie-logica (SwipeIndicators en detectDragGestures) in dit bestand is
 * zorgvuldig geplaatst om conflicten met knoppen en andere interactieve elementen te voorkomen.
 * 
 * 1. SwipeIndicators MOETEN na de Column komen in de Box layout. Dit zorgt ervoor dat de
 *    pijltjes bovenop de content liggen en klikbaar blijven.
 * 2. detectDragGestures (voor swipe detectie) is geimplementeerd in de modifier van de Column.
 * 
 * VERANDER DE POSITIE OF LOGICA VAN DEZE ELEMENTEN NIET ZONDER EXPLICIETE TOESTEMMING!
 * Dit breekt de navigatiefunctionaliteit.
 */
@Composable
fun NewFeatureScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val currentScreenId = "STOPWATCH"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val controlGap = InternalInAppBannerDim.actionIconGap
    val alignmentString = SettingsManager.getTextAlignment(context)
    val showNavButtons = SettingsManager.getShowNavButtons(context)
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    var showHistory by remember { mutableStateOf(false) }

    BackHandler(enabled = showHistory) {
        showHistory = false
    }

    val currentLanguage by LanguageManager.currentLanguage

    val verticalArrangement = when {
        alignmentString.startsWith("TOP_") -> Arrangement.Top
        alignmentString.startsWith("BOTTOM_") -> Arrangement.Bottom
        else -> Arrangement.Center
    }
    val horizontalAlignment = when {
        alignmentString.endsWith("_START") -> Alignment.Start
        alignmentString.endsWith("_END") -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    val timeMillis by StopwatchStateHolder.timeMillis
    val stopwatchState by StopwatchStateHolder.stopwatchState
    val laps = StopwatchStateHolder.laps

    if (showHistory) {
        HistoryScreen(
            onBack = { showHistory = false }
        )
    } else {
        AppBackground {
            Box(modifier = Modifier.fillMaxSize()) {
                val leftTarget = NavigationManager.getSwipeLeftTarget(context, currentScreenId)
                val rightTarget = NavigationManager.getSwipeRightTarget(context, currentScreenId)
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        // !!! SWIPE DETECTIE LOGICA - NIET AANPASSEN !!!
                        .pointerInput(Unit) {
                            var dragStart = Offset.Zero
                            var isDragConsumed = false

                            detectDragGestures(
                                onDragStart = { 
                                    dragStart = it
                                    isDragConsumed = false
                                 },
                                onDrag = { change, dragAmount -> 
                                    if (!swipeEnabled) return@detectDragGestures
                                    if (!isDragConsumed) {
                                        val dragThreshold = 50f
                                        val horizontalDrag = change.position.x - dragStart.x
                                        val verticalDrag = change.position.y - dragStart.y

                                        if (abs(horizontalDrag) > abs(verticalDrag)) {
                                            if (horizontalDrag > dragThreshold) { 
                                                // Left to Right -> Previous
                                                NavigationManager.navigateLeft(context, currentScreenId)
                                                isDragConsumed = true
                                            } else if (horizontalDrag < -dragThreshold) { 
                                                // Right to Left -> Next
                                                NavigationManager.navigateRight(context, currentScreenId) 
                                                isDragConsumed = true
                                            }
                                        } else {
                                            // Swipe Up (dragAmount.y < 0) -> Show History
                                            // Swipe Down (dragAmount.y > 0) -> Do nothing (as requested)
                                            if (dragAmount.y < -dragThreshold) { 
                                                showHistory = true
                                                isDragConsumed = true
                                            }
                                        }
                                    }
                                    if(isDragConsumed) change.consume()
                                }
                            )
                        }
                        .padding(16.dp),
                    verticalArrangement = verticalArrangement,
                    horizontalAlignment = horizontalAlignment
                ) {
                    // Global in-app messages handled by top status bar
                    
                    Text(
                        text = formatTime(timeMillis),
                        style = MaterialTheme.typography.displayLarge,
                        color = textColor
                    )
                    Spacer(Modifier.height(16.dp))
                    when (stopwatchState) {
                        StopwatchState.IDLE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ScreenRoundIconButton(
                                    onClick = { StopwatchStateHolder.start() },
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_start),
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor,
                                )
                            }
                        }
                        StopwatchState.RUNNING -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isVertical) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(controlGap),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.addLap() },
                                            imageVector = Icons.Outlined.Flag,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_lap),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.stopWithoutSave() },
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_stop),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.pause() },
                                            imageVector = Icons.Filled.Pause,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_pause),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(controlGap),
                                    ) {
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.addLap() },
                                            imageVector = Icons.Outlined.Flag,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_lap),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.stopWithoutSave() },
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_stop),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.pause() },
                                            imageVector = Icons.Filled.Pause,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_pause),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                    }
                                }
                            }
                        }
                        StopwatchState.PAUSED -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isVertical) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(controlGap),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.saveAndReset() },
                                            imageVector = Icons.Outlined.Save,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_save),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.stopWithoutSave() },
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_stop),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.resume() },
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_resume),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(controlGap),
                                    ) {
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.saveAndReset() },
                                            imageVector = Icons.Outlined.Save,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_save),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.stopWithoutSave() },
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_stop),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                        ScreenRoundIconButton(
                                            onClick = { StopwatchStateHolder.resume() },
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.internal_banner_cd_stopwatch_resume),
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (laps.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            laps.reversed().forEachIndexed { index, lapTime ->
                                val lapNumber = laps.size - index
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${LanguageManager.getString("sw_lap")} $lapNumber", color = textColor)
                                    Text(formatTime(lapTime), color = textColor)
                                }
                            }
                        }
                    }
                }
                
                // !!! NAVIGATIE PIJLTJES - MOET NA CONTENT KOMEN VOOR Z-ORDER !!!
                com.dd.daykit.ui.SwipeIndicators(
                    isVertical = isVertical,
                    showLeft = leftTarget != null,
                    showRight = rightTarget != null,
                    showDown = true, // Arrow at bottom to show swipe UP is possible
                    showUp = false,  
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
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val arrowNavigationEnabled = SettingsManager.getArrowMode(context) != "Verborgen"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val currentLanguage by LanguageManager.currentLanguage
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    
    val savedSessions = StopwatchStateHolder.savedTimes
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var sessionToRenameIndex by remember { mutableStateOf(-1) }
    var newName by remember { mutableStateOf("") }
    
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(LanguageManager.getString("sw_rename_title")) },
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
                        if (sessionToRenameIndex != -1) {
                            StopwatchStateHolder.updateSessionName(sessionToRenameIndex, newName)
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

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = { Text(LanguageManager.getString("confirm_delete_title")) },
            text = { Text(LanguageManager.getString("sw_confirm_delete")) },
            confirmButton = {
                Button(
                    onClick = {
                        StopwatchStateHolder.clearSavedTimes()
                        showDeleteAllConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("delete"))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteAllConfirmation = false },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
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
                            if (!swipeEnabled) return@detectDragGestures
                            // Swipe down to close
                            if (dragAmount.y > 20) { 
                                change.consume()
                                onBack()
                            }
                        }
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    LanguageManager.getString("sw_saved_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor
                )
                Spacer(Modifier.height(16.dp))
                
                // Popup notification toggle
                val popupEnabled by StopwatchStateHolder.popupEnabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        LanguageManager.getString("sw_popup"),
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = popupEnabled,
                        onCheckedChange = { StopwatchStateHolder.setPopupEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = buttonColor,
                            checkedTrackColor = buttonColor.copy(alpha = 0.5f)
                        )
                    )
                }
                Spacer(Modifier.height(16.dp))

                // "Big arrow" replacing the small scroll arrow
                // Changed to KeyboardArrowUp as requested ("naar boven gedraaid")
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
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (savedSessions.isEmpty()) {
                         item {
                             Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(LanguageManager.getString("sw_empty"), color = textColor)
                             }
                         }
                    } else {
                        itemsIndexed(savedSessions.reversed()) { reversedIndex, session ->
                            val originalIndex = savedSessions.size - 1 - reversedIndex
                            var expanded by remember { mutableStateOf(false) }
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Main Row (Button behaviour)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expanded = !expanded },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val displayName = if (session.name.isNotEmpty()) session.name else "${LanguageManager.getString("save")} ${originalIndex + 1}"
                                        Text(displayName, color = textColor, style = MaterialTheme.typography.titleMedium)
                                        Text(formatTime(session.finalTime), color = textColor, style = MaterialTheme.typography.titleMedium)
                                    }

                                    if (expanded) {
                                        Spacer(Modifier.height(16.dp))
                                        // Laps
                                        if (session.laps.size > 1) {
                                            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                                session.laps.forEachIndexed { lapIndex, lapTime ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("${LanguageManager.getString("sw_lap")} ${lapIndex + 1}", color = textColor.copy(alpha = 0.7f))
                                                        Text(formatTime(lapTime), color = textColor.copy(alpha = 0.7f))
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Action Buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    sessionToRenameIndex = originalIndex
                                                    newName = session.name
                                                    showRenameDialog = true
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                                            ) {
                                                Text(LanguageManager.getString("rename"))
                                            }
                                            Button(
                                                onClick = {
                                                    StopwatchStateHolder.deleteSession(originalIndex)
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f), contentColor = Color.White)
                                            ) {
                                                Text(LanguageManager.getString("delete"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (savedSessions.isNotEmpty()) Arrangement.SpaceBetween else Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                    ) {
                        Text(LanguageManager.getString("back"))
                    }
                    if (savedSessions.isNotEmpty()) {
                        Button(
                            onClick = { showDeleteAllConfirmation = true },
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                        ) {
                            Text(LanguageManager.getString("delete_all"))
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
        }
    }
}

private fun formatTime(timeMillis: Long): String {
    val hours = timeMillis / 3600000
    if (hours > 0) {
        val minutes = (timeMillis % 3600000) / 60000
        val seconds = (timeMillis % 60000) / 1000
        // Wanneer langer dan een uur: Uur:Minuten:Seconden (zoals in notificatie)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        val minutes = (timeMillis / 1000) / 60
        val seconds = (timeMillis / 1000) % 60
        val millis = (timeMillis % 1000) / 10
        // Standaard: Minuten:Seconden:Honderdsten
        return String.format("%02d:%02d:%02d", minutes, seconds, millis)
    }
}
