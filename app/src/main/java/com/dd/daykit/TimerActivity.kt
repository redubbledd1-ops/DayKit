package com.dd.daykit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.ScreenRoundIconButton
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.RingtonePickerDialog
import com.dd.daykit.ui.VerticalScrollArrows
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs

class TimerActivity : ComponentActivity() {

    private val ringtonePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            TimerSettingsStateHolder.toneUri.value = uri?.toString()
            TimerSettingsStateHolder.save(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        
        // Initialize ALL state holders for proper state restoration on app restart
        // This ensures the global message bar shows correct status immediately
        TimerSettingsStateHolder.init(this)
        TimerStateHolder.init(this)
        GlobalTimerManager.init(this)
        StopwatchStateHolder.init(this)
        GlobalInAppMessageManager.init(this)

        setContent {
            MaterialTheme {
                TimerScreen()
            }
        }
    }
}

enum class TimerState {
    IDLE, RUNNING, PAUSED, FINISHED
}

// Helper: zelfde compacte notatie als timer-popup (TimerDisplayFormat)
private fun formatTimerTime(millis: Long): String = TimerDisplayFormat.formatMillisCompact(millis)

/** Zelfde glyphs als timer-popup (`notification_poc_compact`: 22sp bold, II / ▶). */
@Composable
private fun TimerRoundPlayPauseGlyphButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 52.dp,
    glyphFontSize: TextUnit = 22.sp,
) {
    val glyph = if (isRunning) "II" else "▶"
    val desc = if (isRunning) {
        LanguageManager.getString("sw_pause")
    } else {
        LanguageManager.getString("sw_resume")
    }
    Button(
        onClick = onClick,
        // .size() i.p.v. defaultMinSize: forceert een exact vierkant zodat de knop bij elke
        // tier-grootte een perfecte cirkel blijft (Material3's eigen ~58dp minimumbreedte
        // duwde 'm anders ovaal bij kleinere buttonSize-waarden).
        modifier = modifier
            .size(buttonSize)
            .semantics(mergeDescendants = true) { contentDescription = desc },
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Text(
            text = glyph,
            color = contentColor,
            style = TextStyle(
                fontSize = glyphFontSize,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

/**
 * Actieve timer: vaste volgorde Save → Stop → Play/Pause ([TimerInternalControlOrder]).
 * [horizontal] = true: naast elkaar (portret); false: onder elkaar (landschap-column).
 */
@Composable
private fun TimerRunningPausedPrimaryIconCluster(
    timerState: GlobalTimerManager.TimerState,
    initialTimeMillis: Long,
    buttonColor: Color,
    buttonTextColor: Color,
    horizontal: Boolean,
    buttonSize: Dp = 52.dp,
    iconSize: Dp = 28.dp,
    glyphFontSize: TextUnit = 22.sp,
    gap: Dp = com.dd.daykit.ui.InternalInAppBannerDim.actionIconGap,
) {
    val context = LocalContext.current

    @Composable
    fun SaveBtn() {
        ScreenRoundIconButton(
            onClick = { TimerStateHolder.saveDuration(context, initialTimeMillis) },
            imageVector = Icons.Filled.Save,
            contentDescription = LanguageManager.getString("save"),
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = buttonSize,
            iconSize = iconSize,
        )
    }

    @Composable
    fun StopBtn() {
        ScreenRoundIconButton(
            onClick = { GlobalTimerManager.resetTimer(context) },
            imageVector = Icons.Filled.Stop,
            contentDescription = LanguageManager.getString("sw_stop"),
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = buttonSize,
            iconSize = iconSize,
        )
    }

    @Composable
    fun PlayPauseBtn() {
        TimerRoundPlayPauseGlyphButton(
            isRunning = timerState == GlobalTimerManager.TimerState.RUNNING,
            onClick = {
                if (timerState == GlobalTimerManager.TimerState.RUNNING) {
                    GlobalTimerManager.pauseTimer()
                } else {
                    GlobalTimerManager.resumeTimer(context)
                }
            },
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = buttonSize,
            glyphFontSize = glyphFontSize,
        )
    }

    if (horizontal) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaveBtn()
            StopBtn()
            PlayPauseBtn()
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SaveBtn()
            StopBtn()
            PlayPauseBtn()
        }
    }
}

/**
 * Stel-tijd-in: Save → Reset → Play; verborgen bij 00:00:00 maar vaste layoutruimte blijft
 * gereserveerd. Als [onRemove] is meegegeven (extra timers 2/3) komt er een 4e knop met
 * Stop-icoon bij die de hele timer-slot verwijdert — deze blijft, in tegenstelling tot de
 * andere drie, ALTIJD klikbaar, ook bij 00:00:00, zodat een nog niet ingestelde extra timer
 * ook weer weg te halen is.
 */
@Composable
private fun TimerSetTimeActionButtons(
    visible: Boolean,
    horizontal: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onPlay: () -> Unit,
    buttonColor: Color,
    buttonTextColor: Color,
    buttonSize: Dp = 52.dp,
    iconSize: Dp = 28.dp,
    horizontalGap: Dp = 16.dp,
    verticalGap: Dp = 14.dp,
    onRemove: (() -> Unit)? = null,
) {
    val saveDesc = LanguageManager.getString("save")
    val resetDesc = LanguageManager.getString("timer_reset_input")
    val playDesc = LanguageManager.getString("sw_start")
    val removeDesc = LanguageManager.getString("sw_stop")
    val coreReservedWidth = buttonSize * 3 + horizontalGap * 2
    val coreReservedHeight = buttonSize * 3 + verticalGap * 2

    @Composable
    fun CoreButtons() {
        ScreenRoundIconButton(
            onClick = { if (visible) onSave() },
            imageVector = Icons.Filled.Save,
            contentDescription = saveDesc,
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = buttonSize,
            iconSize = iconSize,
        )
        ScreenRoundIconButton(
            onClick = { if (visible) onReset() },
            imageVector = Icons.Filled.Refresh,
            contentDescription = resetDesc,
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = buttonSize,
            iconSize = iconSize,
        )
        ScreenRoundIconButton(
            onClick = { if (visible) onPlay() },
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = playDesc,
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = buttonSize,
            iconSize = iconSize,
        )
    }

    val blockTouches = remember { MutableInteractionSource() }

    @Composable
    fun CoreGroup() {
        if (horizontal) {
            Box(
                modifier = Modifier
                    .width(coreReservedWidth)
                    .height(buttonSize),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.alpha(if (visible) 1f else 0f),
                    horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                ) {
                    CoreButtons()
                }
                if (!visible) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = blockTouches,
                                indication = null,
                                onClick = {},
                            ),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(buttonSize)
                    .height(coreReservedHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.alpha(if (visible) 1f else 0f),
                    verticalArrangement = Arrangement.spacedBy(verticalGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoreButtons()
                }
                if (!visible) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = blockTouches,
                                indication = null,
                                onClick = {},
                            ),
                    )
                }
            }
        }
    }

    if (onRemove == null) {
        CoreGroup()
    } else if (horizontal) {
        if (!visible) {
            // Extra timer 2/3 nog niet gestart: alleen Stop tonen, gecentreerd.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                ScreenRoundIconButton(
                    onClick = onRemove,
                    imageVector = Icons.Filled.Stop,
                    contentDescription = removeDesc,
                    containerColor = buttonColor,
                    contentColor = buttonTextColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoreGroup()
                ScreenRoundIconButton(
                    onClick = onRemove,
                    imageVector = Icons.Filled.Stop,
                    contentDescription = removeDesc,
                    containerColor = buttonColor,
                    contentColor = buttonTextColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                )
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(verticalGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoreGroup()
            ScreenRoundIconButton(
                onClick = onRemove,
                imageVector = Icons.Filled.Stop,
                contentDescription = removeDesc,
                containerColor = buttonColor,
                contentColor = buttonTextColor,
                buttonSize = buttonSize,
                iconSize = iconSize,
            )
        }
    }
}

/**
 * Alle afmetingen voor 1 "dichtheid" van de timer-lijst. Hoe meer timers tegelijk
 * getoond worden, hoe kleiner de gekozen tier — zie [pickTimerSizeTier], die kiest op
 * basis van de daadwerkelijk gemeten beschikbare ruimte (schaalt dus mee met elk schermformaat).
 */
private data class TimerSizeTier(
    val timeStyle: TextStyle,
    val wheelValueStyle: TextStyle,
    val wheelArrowSize: Dp,
    val wheelBoxHeight: Dp,
    val wheelColumnWidth: Dp,
    val buttonSize: Dp,
    val iconSize: Dp,
    val glyphFontSize: TextUnit,
    val gapAfterContent: Dp,
    val buttonRowGap: Dp,
    val gapBetweenSlots: Dp,
    val reservedSlotHeight: Dp,
)

/**
 * Kiest de tier op basis van de écht beschikbare hoogte per slot ([perSlotBudget]) —
 * dit is de sleutel voor "schaalt mee met elke telefoongrootte": op een klein scherm
 * met 3 timers valt [perSlotBudget] lager uit en wordt automatisch een kleinere tier gekozen.
 */
@Composable
private fun pickTimerSizeTier(perSlotBudget: Dp): TimerSizeTier {
    return when {
        perSlotBudget >= 280.dp -> TimerSizeTier(
            timeStyle = MaterialTheme.typography.displayLarge,
            wheelValueStyle = MaterialTheme.typography.displayMedium,
            wheelArrowSize = 40.dp, wheelBoxHeight = 80.dp, wheelColumnWidth = 80.dp,
            buttonSize = 52.dp, iconSize = 28.dp, glyphFontSize = 22.sp,
            gapAfterContent = 32.dp, buttonRowGap = 16.dp, gapBetweenSlots = 32.dp,
            reservedSlotHeight = 280.dp,
        )
        perSlotBudget >= 210.dp -> TimerSizeTier(
            timeStyle = MaterialTheme.typography.displayMedium,
            wheelValueStyle = MaterialTheme.typography.headlineMedium,
            wheelArrowSize = 30.dp, wheelBoxHeight = 60.dp, wheelColumnWidth = 68.dp,
            buttonSize = 46.dp, iconSize = 24.dp, glyphFontSize = 19.sp,
            gapAfterContent = 18.dp, buttonRowGap = 14.dp, gapBetweenSlots = 22.dp,
            reservedSlotHeight = 210.dp,
        )
        perSlotBudget >= 165.dp -> TimerSizeTier(
            timeStyle = MaterialTheme.typography.headlineLarge,
            wheelValueStyle = MaterialTheme.typography.headlineSmall,
            wheelArrowSize = 24.dp, wheelBoxHeight = 46.dp, wheelColumnWidth = 56.dp,
            buttonSize = 40.dp, iconSize = 20.dp, glyphFontSize = 16.sp,
            gapAfterContent = 10.dp, buttonRowGap = 10.dp, gapBetweenSlots = 14.dp,
            reservedSlotHeight = 165.dp,
        )
        else -> TimerSizeTier(
            timeStyle = MaterialTheme.typography.headlineMedium,
            wheelValueStyle = MaterialTheme.typography.titleLarge,
            wheelArrowSize = 20.dp, wheelBoxHeight = 38.dp, wheelColumnWidth = 48.dp,
            buttonSize = 36.dp, iconSize = 18.dp, glyphFontSize = 14.sp,
            gapAfterContent = 6.dp, buttonRowGap = 8.dp, gapBetweenSlots = 10.dp,
            reservedSlotHeight = 140.dp,
        )
    }
}

/** Uren:min:sec swipe-wielen op 1 rij, met dubbele punt — afmetingen komen uit [TimerSizeTier]. */
@Composable
private fun TimerWheelsRow(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHours: (Int) -> Unit,
    onMinutes: (Int) -> Unit,
    onSeconds: (Int) -> Unit,
    textColor: Color,
    tier: TimerSizeTier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        SwipeableTimeUnitEnhanced(hours, onHours, 0..99, LanguageManager.getString("hour"), textColor, tier.wheelArrowSize, tier.wheelBoxHeight, tier.wheelColumnWidth, tier.wheelValueStyle)
        Text(":", style = tier.wheelValueStyle, color = textColor, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-10).dp))
        SwipeableTimeUnitEnhanced(minutes, onMinutes, 0..59, LanguageManager.getString("min"), textColor, tier.wheelArrowSize, tier.wheelBoxHeight, tier.wheelColumnWidth, tier.wheelValueStyle)
        Text(":", style = tier.wheelValueStyle, color = textColor, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-10).dp))
        SwipeableTimeUnitEnhanced(seconds, onSeconds, 0..59, LanguageManager.getString("sec"), textColor, tier.wheelArrowSize, tier.wheelBoxHeight, tier.wheelColumnWidth, tier.wheelValueStyle)
    }
}

/** Save → Stop → Play/Pause voor een lopende/gepauzeerde extra timer-slot. */
@Composable
private fun TimerExtraRunningButtons(
    slot: ExtraTimerData,
    context: Context,
    buttonColor: Color,
    buttonTextColor: Color,
    tier: TimerSizeTier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(tier.buttonRowGap)) {
        ScreenRoundIconButton(
            onClick = { TimerStateHolder.saveDuration(context, slot.initialMs) },
            imageVector = Icons.Filled.Save,
            contentDescription = LanguageManager.getString("save"),
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = tier.buttonSize,
            iconSize = tier.iconSize,
        )
        ScreenRoundIconButton(
            onClick = { ExtraTimerManager.removeTimer(context, slot) },
            imageVector = Icons.Filled.Stop,
            contentDescription = LanguageManager.getString("sw_stop"),
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = tier.buttonSize,
            iconSize = tier.iconSize,
        )
        TimerRoundPlayPauseGlyphButton(
            isRunning = slot.state == GlobalTimerManager.TimerState.RUNNING,
            onClick = {
                if (slot.state == GlobalTimerManager.TimerState.RUNNING) {
                    ExtraTimerManager.pauseTimer(context, slot)
                } else {
                    ExtraTimerManager.resumeTimer(context, slot)
                }
            },
            containerColor = buttonColor,
            contentColor = buttonTextColor,
            buttonSize = tier.buttonSize,
            glyphFontSize = tier.glyphFontSize,
        )
    }
}

/**
 * Compacte, aanklikbare rij voor een niet-geselecteerde timer: alleen label + tijd, geen
 * pijltjes/knoppen. Klikken maakt deze timer de actief bewerkte ([TimerSizeTier]-slot met
 * volledige bediening) — geldt zowel voor lopende als nog niet gestarte timers.
 */
@Composable
private fun TimerCompactSlotRow(
    label: String,
    timeText: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = timeText,
            style = MaterialTheme.typography.headlineSmall,
            color = textColor.copy(alpha = 0.75f),
        )
    }
}

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val currentScreenId = "TIMER"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val showNavButtons = SettingsManager.getShowNavButtons(context)
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)

    // Force recomposition on language change
    val currentLanguage by LanguageManager.currentLanguage

    // Observe GlobalTimerManager state - this is the SINGLE source of truth
    val timerState by GlobalTimerManager.timerState
    val timeMillis by GlobalTimerManager.timeMillis
    val initialTimeMillis by GlobalTimerManager.initialTimeMillis
    
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingStartDurationMs by remember { mutableStateOf<Long?>(null) }
    var pendingStartName by remember { mutableStateOf<String?>(null) }

    val popupPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val duration = pendingStartDurationMs
        pendingStartDurationMs = null
        if (granted && duration != null && duration > 0L) {
            GlobalTimerManager.startTimer(context, duration, pendingStartName.orEmpty())
            pendingStartName = null
        }
    }

    fun startTimerWithPopupPermissionGuard(duration: Long, name: String = "") {
        if (duration <= 0L) return
        TimerSettingsStateHolder.init(context)
        val popupEnabled = TimerSettingsStateHolder.popupEnabled.value
        val needsNotificationPermission =
            popupEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        if (needsNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStartDurationMs = duration
            pendingStartName = name
            popupPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        GlobalTimerManager.startTimer(context, duration, name)
    }

    BackHandler(enabled = showHistory) {
        showHistory = false
    }
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    // Input fields for setting timer duration
    val savedTime = if (GlobalTimerManager.hasActiveTimer()) {
        GlobalTimerManager.getInitialTimeMillis()
    } else {
        TimerSettingsStateHolder.lastTimeMillis.value
    }
    var hoursInput by remember { mutableStateOf((savedTime / 3600000).toInt()) }
    var minutesInput by remember { mutableStateOf(((savedTime % 3600000) / 60000).toInt()) }
    var secondsInput by remember { mutableStateOf(((savedTime % 60000) / 1000).toInt()) }
    var primaryTimerName by remember { mutableStateOf(GlobalTimerManager.timerName.value) }
    var selectedSlotIndex by remember { mutableStateOf(0) }

    val resetInputToZero: () -> Unit = {
        hoursInput = 0
        minutesInput = 0
        secondsInput = 0
        primaryTimerName = ""
        TimerSettingsStateHolder.persistLastInputTime(context, 0L)
        GlobalTimerManager.persistIdleInputDuration(context, 0L)
    }

    // Update input fields when timer resets
    LaunchedEffect(timerState) {
        if (timerState == GlobalTimerManager.TimerState.IDLE) {
            val initTime = GlobalTimerManager.getInitialTimeMillis()
            if (initTime > 0) {
                hoursInput = (initTime / 3600000).toInt()
                minutesInput = ((initTime % 3600000) / 60000).toInt()
                secondsInput = ((initTime % 60000) / 1000).toInt()
            }
            // Naam-draft opnieuw gelijktrekken met de echte staat: leeg na een volledige reset/stop
            // (zie GlobalTimerManager.resetTimer/stopTimer), of de bewaarde naam na stopAlarmOnly -
            // anders kon hier de naam van de vorige run (bv. "Witgoed") blijven hangen.
            primaryTimerName = GlobalTimerManager.timerName.value
        }
    }

    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))

    LaunchedEffect(Unit) {
        TimerSettingsStateHolder.init(context)
        ExtraTimerManager.init(context)
    }
    val multiTimerEnabled by remember { derivedStateOf { TimerSettingsStateHolder.multiTimerEnabled.value } }
    val extraTimers = ExtraTimerManager.timers

    if (showHistory) {
        TimerHistoryScreen(
            onBack = { showHistory = false },
            onSelectTimer = { saved ->
                if (selectedSlotIndex == 0) {
                    hoursInput = (saved.durationMillis / 3600000).toInt()
                    minutesInput = ((saved.durationMillis % 3600000) / 60000).toInt()
                    secondsInput = ((saved.durationMillis % 60000) / 1000).toInt()
                    primaryTimerName = saved.name
                    TimerSettingsStateHolder.lastTimeMillis.value = saved.durationMillis
                    TimerSettingsStateHolder.save(context)
                } else {
                    val extraSlot = extraTimers.getOrNull(selectedSlotIndex - 1)
                    if (extraSlot != null && (extraSlot.state == GlobalTimerManager.TimerState.IDLE || extraSlot.state == GlobalTimerManager.TimerState.FINISHED)) {
                        extraSlot.name = saved.name
                        extraSlot.hoursInput = (saved.durationMillis / 3600000).toInt()
                        extraSlot.minutesInput = ((saved.durationMillis % 3600000) / 60000).toInt()
                        extraSlot.secondsInput = ((saved.durationMillis % 60000) / 1000).toInt()
                        extraSlot.initialMs = saved.durationMillis
                        extraSlot.state = GlobalTimerManager.TimerState.IDLE
                        ExtraTimerManager.saveState(context)
                    } else {
                        ExtraTimerManager.loadSavedTimerToExtra(context, saved)
                    }
                }
                showHistory = false
            }
        )
    } else if (showSettings) {
        TimerSettingsScreen(
            onBack = { showSettings = false }
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
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                if (!swipeEnabled) return@detectDragGestures
                                change.consume()
                                val dragThreshold = 50f
                                if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                    if (dragAmount.x > dragThreshold) {
                                        NavigationManager.navigateLeft(context, currentScreenId)
                                    } else if (dragAmount.x < -dragThreshold) {
                                        NavigationManager.navigateRight(context, currentScreenId)
                                    }
                                } else {
                                    if (dragAmount.y > dragThreshold) {
                                        showSettings = true
                                    } else if (dragAmount.y < -dragThreshold) {
                                        showHistory = true
                                    }
                                }
                            }
                        }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val primaryIdle = timerState == GlobalTimerManager.TimerState.IDLE ||
                        timerState == GlobalTimerManager.TimerState.FINISHED

                    // Alle timer-slots (primair + extra) in vaste aanmaakvolgorde — geen
                    // herschikking op resterende tijd, zodat rijen nooit van plek wisselen.
                    val totalTimerSlots = 1 + extraTimers.size
                    val maxTimerSlots = 1 + ExtraTimerManager.MAX_EXTRA_TIMERS
                    val showPlusButton = multiTimerEnabled && totalTimerSlots < maxTimerSlots

                    LaunchedEffect(totalTimerSlots) {
                        if (selectedSlotIndex > totalTimerSlots - 1) {
                            selectedSlotIndex = (totalTimerSlots - 1).coerceAtLeast(0)
                        }
                    }
                    val selectedIsIdle = if (selectedSlotIndex == 0) {
                        primaryIdle
                    } else {
                        extraTimers.getOrNull(selectedSlotIndex - 1)?.state == GlobalTimerManager.TimerState.IDLE
                    }

                    // Meet de écht beschikbare ruimte, zodat de gekozen tier (tekst/wielen/knoppen-
                    // grootte van de ENE actieve timer) altijd past — op elk schermformaat. Onderaan
                    // houden we ruimte vrij voor het omlaag-swipe-pijltje (opgeslagen timers).
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val compactSlotCount = (totalTimerSlots - 1).coerceAtLeast(0)
                        val compactRowHeight = 52.dp
                        val slotGap = 20.dp
                        val bottomArrowClearance = if (totalTimerSlots > 1) 80.dp else 0.dp
                        val primaryIdleSelected = selectedSlotIndex == 0 && primaryIdle
                        val titleReserve = if (primaryIdleSelected) 60.dp else 0.dp
                        val plusReserve = if (showPlusButton) 68.dp else 0.dp
                        val gapsReserve = slotGap * (compactSlotCount + (if (showPlusButton) 1 else 0))
                        val compactReserve = compactRowHeight * compactSlotCount
                        val usableForSelected = (maxHeight - bottomArrowClearance - titleReserve - plusReserve - gapsReserve - compactReserve)
                            .coerceAtLeast(0.dp)
                        val tier = pickTimerSizeTier(usableForSelected)
                        // Bij precies 1 timer (idle of lopend): hele blok iets omhoog zodat de
                        // wieltijd weer netjes uitlijnt met de < en > pijltjes.
                        val soleTimerLayout = totalTimerSlots == 1

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (soleTimerLayout) Modifier.offset(y = (-6).dp) else Modifier,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // "Stel tijd in" alleen bij één timer. Bij 2+ timers en geselecteerde
                            // timer 1: geen tekst, wel lege ruimte (timer 2/3 hebben dit niet nodig).
                            if (primaryIdleSelected) {
                                if (totalTimerSlots == 1) {
                                    Text(
                                        primaryTimerName.ifEmpty { LanguageManager.getString("timer_set_title") },
                                        color = textColor,
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                    Spacer(Modifier.height(32.dp))
                                } else {
                                    Spacer(Modifier.height(60.dp))
                                }
                            }

                            // Primaire timer-slot. Geen vaste min-hoogte meer: zodra de timer loopt
                            // is de inhoud (tijd + knoppen) korter dan de instel-wielen, en anders
                            // bleef daar een grote lege ruimte staan tot het volgende item.
                            if (selectedSlotIndex == 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (primaryIdle) {
                                            val hasInput = hoursInput > 0 || minutesInput > 0 || secondsInput > 0
                                            TimerWheelsRow(
                                                hours = hoursInput, minutes = minutesInput, seconds = secondsInput,
                                                onHours = { hoursInput = it }, onMinutes = { minutesInput = it }, onSeconds = { secondsInput = it },
                                                textColor = textColor, tier = tier,
                                            )
                                            Spacer(Modifier.height(tier.gapAfterContent))
                                            TimerSetTimeActionButtons(
                                                visible = hasInput,
                                                horizontal = true,
                                                onSave = {
                                                    val duration = (hoursInput * 3600 + minutesInput * 60 + secondsInput) * 1000L
                                                    TimerStateHolder.saveDuration(context, duration)
                                                },
                                                onReset = resetInputToZero,
                                                onPlay = {
                                                    val duration = (hoursInput * 3600 + minutesInput * 60 + secondsInput) * 1000L
                                                    if (duration > 0) startTimerWithPopupPermissionGuard(duration, primaryTimerName)
                                                },
                                                buttonColor = buttonColor,
                                                buttonTextColor = buttonTextColor,
                                                buttonSize = tier.buttonSize,
                                                iconSize = tier.iconSize,
                                                horizontalGap = tier.buttonRowGap,
                                            )
                                        } else {
                                            // Naam van de lopende timer (bv. "Witgoed") tonen - las
                                            // rechtstreeks uit GlobalTimerManager i.p.v. de lokale
                                            // primaryTimerName-draft, want deze pagina kan via de
                                            // navigatiebalk (FLAG_ACTIVITY_REORDER_TO_FRONT) worden
                                            // hergebruikt zonder herstart, waardoor die draft anders
                                            // was blijven hangen op de waarde van vóór het starten.
                                            val runningPrimaryName = GlobalTimerManager.timerName.value
                                            if (totalTimerSlots == 1 && runningPrimaryName.isNotBlank()) {
                                                Text(
                                                    runningPrimaryName,
                                                    color = textColor,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                )
                                                Spacer(Modifier.height(8.dp))
                                            }
                                            Text(
                                                text = formatTimerTime(timeMillis),
                                                style = tier.timeStyle,
                                                color = textColor
                                            )
                                            Spacer(Modifier.height(tier.gapAfterContent))
                                            TimerRunningPausedPrimaryIconCluster(
                                                timerState = timerState,
                                                initialTimeMillis = initialTimeMillis,
                                                buttonColor = buttonColor,
                                                buttonTextColor = buttonTextColor,
                                                horizontal = true,
                                                buttonSize = tier.buttonSize,
                                                iconSize = tier.iconSize,
                                                glyphFontSize = tier.glyphFontSize,
                                                gap = tier.buttonRowGap,
                                            )
                                        }
                                    }
                                }
                            } else if (totalTimerSlots > 1) {
                                TimerCompactSlotRow(
                                    // Idle: laat de lokale naam-draft zien (voorproefje van de naam
                                    // waarmee 'm gestart wordt); lopend: altijd de écht actieve naam
                                    // uit GlobalTimerManager, zelfde reden als hierboven.
                                    label = (if (primaryIdle) primaryTimerName else GlobalTimerManager.timerName.value).ifBlank { "Timer 1" },
                                    timeText = formatTimerTime(
                                        if (primaryIdle) (hoursInput * 3600 + minutesInput * 60 + secondsInput) * 1000L else timeMillis
                                    ),
                                    textColor = textColor,
                                    onClick = { selectedSlotIndex = 0 },
                                )
                            }

                            // Extra timer-slots, in aanmaakvolgorde
                            extraTimers.forEachIndexed { i, slot ->
                                val slotIndex = i + 1
                                Spacer(Modifier.height(slotGap))
                                if (selectedSlotIndex == slotIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (slot.state == GlobalTimerManager.TimerState.IDLE) {
                                                TimerWheelsRow(
                                                    hours = slot.hoursInput, minutes = slot.minutesInput, seconds = slot.secondsInput,
                                                    onHours = { slot.hoursInput = it }, onMinutes = { slot.minutesInput = it }, onSeconds = { slot.secondsInput = it },
                                                    textColor = textColor, tier = tier,
                                                )
                                                Spacer(Modifier.height(tier.gapAfterContent))
                                                TimerSetTimeActionButtons(
                                                    visible = slot.hoursInput > 0 || slot.minutesInput > 0 || slot.secondsInput > 0,
                                                    horizontal = true,
                                                    onSave = {
                                                        val duration = (slot.hoursInput * 3600 + slot.minutesInput * 60 + slot.secondsInput) * 1000L
                                                        TimerStateHolder.saveDuration(context, duration)
                                                    },
                                                    onReset = {
                                                        slot.hoursInput = 0
                                                        slot.minutesInput = 0
                                                        slot.secondsInput = 0
                                                    },
                                                    onPlay = {
                                                        val duration = (slot.hoursInput * 3600 + slot.minutesInput * 60 + slot.secondsInput) * 1000L
                                                        if (duration > 0) {
                                                            ExtraTimerManager.startTimer(context, slot, duration)
                                                        }
                                                    },
                                                    buttonColor = buttonColor,
                                                    buttonTextColor = buttonTextColor,
                                                    buttonSize = tier.buttonSize,
                                                    iconSize = tier.iconSize,
                                                    horizontalGap = tier.buttonRowGap,
                                                    onRemove = {
                                                        ExtraTimerManager.removeTimer(context, slot)
                                                        if (selectedSlotIndex == slotIndex) {
                                                            selectedSlotIndex = 0
                                                        }
                                                    },
                                                )
                                            } else {
                                                Text(
                                                    text = formatTimerTime(slot.remainingMs),
                                                    style = tier.timeStyle,
                                                    color = textColor
                                                )
                                                Spacer(Modifier.height(tier.gapAfterContent))
                                                TimerExtraRunningButtons(
                                                    slot = slot,
                                                    context = context,
                                                    buttonColor = buttonColor,
                                                    buttonTextColor = buttonTextColor,
                                                    tier = tier,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    TimerCompactSlotRow(
                                        label = slot.name.ifBlank { "Timer ${slotIndex + 1}" },
                                        timeText = formatTimerTime(
                                            if (slot.state == GlobalTimerManager.TimerState.IDLE) {
                                                (slot.hoursInput * 3600 + slot.minutesInput * 60 + slot.secondsInput) * 1000L
                                            } else {
                                                slot.remainingMs
                                            }
                                        ),
                                        textColor = textColor,
                                        onClick = { selectedSlotIndex = slotIndex },
                                    )
                                }
                            }

                            // "+" om de volgende timer klaar te zetten. Mag al vóór de vorige timer
                            // is gestart — enige voorwaarde is dat het max aantal nog niet bereikt is.
                            // Selecteert de nieuwe timer meteen, zodat je 'm direct kan instellen.
                            if (showPlusButton) {
                                Spacer(Modifier.height(slotGap))
                                ScreenRoundIconButton(
                                    onClick = {
                                        val newSlotIndex = totalTimerSlots
                                        if (ExtraTimerManager.addTimer(context) != null) {
                                            selectedSlotIndex = newSlotIndex
                                        }
                                    },
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "+",
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor,
                                    buttonSize = tier.buttonSize,
                                    iconSize = tier.iconSize,
                                )
                            }
                        }
                    }
                }

                // Moved after Column to ensure clicks are registered
                SwipeIndicators(
                    isVertical = isVertical,
                    showLeft = leftTarget != null,
                    showRight = rightTarget != null,
                    showUp = true,
                    showDown = true,
                    onSwipeLeft = { NavigationManager.navigateLeft(context, currentScreenId) },
                    onSwipeRight = { NavigationManager.navigateRight(context, currentScreenId) },
                    onSwipeUp = { showSettings = true },
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
fun TimerHistoryScreen(onBack: () -> Unit, onSelectTimer: (SavedTimer) -> Unit) {
    val context = LocalContext.current
    val arrowNavigationEnabled = SettingsManager.getArrowMode(context) != "Verborgen"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)

    // Force recomposition on language change
    val currentLanguage by LanguageManager.currentLanguage

    val savedTimers = TimerStateHolder.savedTimers

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var itemToRenameIndex by remember { mutableStateOf(-1) }
    var newName by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(LanguageManager.getString("confirm_delete_title")) },
            text = { Text(LanguageManager.getString("timer_confirm_delete")) },
            confirmButton = {
                Button(
                    onClick = {
                        TimerStateHolder.clearHistory(context)
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
            title = { Text(LanguageManager.getString("timer_rename_title")) },
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
                            TimerStateHolder.updateTimerName(context, itemToRenameIndex, newName)
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
        Box(modifier = Modifier.fillMaxSize()) {
            
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
                    // Navigation Arrow (Back) above the title
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
                    Spacer(Modifier.height(8.dp))
                    Text(LanguageManager.getString("timer_saved_title"), style = MaterialTheme.typography.headlineSmall, color = textColor)
                    Spacer(Modifier.height(24.dp))

                    // Toont alleen hele kaarten: hoogte wordt beperkt tot een exact veelvoud
                    // van (item-hoogte + spacing) i.p.v. de volledige resterende ruimte te
                    // vullen (weight(1f)), anders blijft er vaak een fractie van een kaart
                    // zichtbaar aan de onderkant. Voor de eerste meting (nog geen items
                    // gelayout) valt terug op weight(1f)/fillMaxHeight.
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
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Display in NORMAL order (newest at bottom, as requested)
                        itemsIndexed(savedTimers) { index, savedTimer ->
                            var expanded by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 56.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left part: Click to Select (Set inputs)
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSelectTimer(savedTimer) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatTimerTime(savedTimer.durationMillis),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = textColor
                                            )
                                            if (savedTimer.name.isNotEmpty()) {
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = savedTimer.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = textColor.copy(alpha = 0.8f)
                                                )
                                            }
                                        }

                                        // Right part: Edit Icon
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
                                                    newName = savedTimer.name
                                                    showRenameDialog = true
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                                            ) {
                                                Text(LanguageManager.getString("rename"))
                                            }
                                            Button(
                                                onClick = {
                                                    TimerStateHolder.deleteTimer(context, index)
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor) // Changed color to use theme
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                        ) { Text(LanguageManager.getString("back")) }

                        if (savedTimers.isNotEmpty()) {
                            Button(
                                onClick = { showDeleteConfirmation = true },
                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                            ) { Text(LanguageManager.getString("delete_all")) }
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
}

@Composable
fun TimerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    
    // Force recomposition on language change
    val currentLanguage by LanguageManager.currentLanguage

    // Direct binding aan holder: schakelaar en slider blijven synchroon met opslag en alarm-afspeellijn.
    val volume by TimerSettingsStateHolder.volume
    val vibrate by TimerSettingsStateHolder.vibrationEnabled
    val useMobileVolume by TimerSettingsStateHolder.useMobileVolume
    val popupEnabled by TimerSettingsStateHolder.popupEnabled
    val ddMusicLinked by TimerSettingsStateHolder.ddMusicLinked
    val ddMusicSummary by TimerSettingsStateHolder.ddMusicSummary
    LaunchedEffect(Unit) {
        TimerSettingsStateHolder.init(context.applicationContext)
    }
    // Ververs bij terugkeer naar dit scherm (bv. na het maken van een keuze in DD Music).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TimerSettingsStateHolder.init(context.applicationContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showRingtoneDialog by remember { mutableStateOf(false) }
    var selectedRingtoneUriString by remember { mutableStateOf(TimerSettingsStateHolder.toneUri.value) }
    var showCustomSoundModal by remember { mutableStateOf(false) }
    val onBackUpdated by rememberUpdatedState(onBack)

    if (showRingtoneDialog) {
        RingtonePickerDialog(
            onDismissRequest = { showRingtoneDialog = false },
            onRingtoneSelected = { uri ->
                selectedRingtoneUriString = uri?.toString()
                TimerSettingsStateHolder.toneUri.value = uri?.toString()
                // Een normaal geluid kiezen betekent impliciet: niet meer DD Music voor timers.
                TimerSettingsStateHolder.ddMusicLinked.value = false
                TimerSettingsStateHolder.ddMusicSummary.value = null
                TimerSettingsStateHolder.ddMusicPlayUrl.value = null
                TimerSettingsStateHolder.save(context)
                showRingtoneDialog = false
            },
            currentUriString = selectedRingtoneUriString,
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            containerColor = backgroundColor,
            onAddCustomSound = {
                showRingtoneDialog = false
                showCustomSoundModal = true
            },
            ddMusicLinked = ddMusicLinked,
            ddMusicSummary = ddMusicSummary,
            onUseDdMusic = {
                // Bewust GEEN optimistische ddMusicLinked=true hier - dat gebeurt pas via
                // DdMusicLinkUpdateReceiver zodra DD Music een echte keuze bevestigt.
                val started = DdMusicBridge.launch(context, DdMusicBridge.TIMER_TRIGGER_ID, "Timer", forcePicker = true)
                if (!started) {
                    Toast.makeText(context, "DD Music is niet geïnstalleerd", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    // Custom sound modal - reuses same implementation as Agenda Alarm
    if (showCustomSoundModal) {
        com.dd.daykit.ui.modals.CustomSoundModal(
            visible = true,
            onDismiss = { showCustomSoundModal = false },
            onSoundSelected = { customSound ->
                val customSoundManager = com.dd.daykit.CustomSoundManager(context)
                val uri = customSoundManager.getUriForSound(customSound)
                selectedRingtoneUriString = uri.toString()
                TimerSettingsStateHolder.toneUri.value = uri.toString()
                TimerSettingsStateHolder.save(context)
                showCustomSoundModal = false
            },
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            containerColor = backgroundColor
        )
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            
                Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .then(
                        if (swipeEnabled) {
                            Modifier.pointerInput(swipeEnabled) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount < -20f) onBackUpdated()
                                }
                            }
                        } else Modifier
                    )
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(LanguageManager.getString("timer_settings_title"), style = MaterialTheme.typography.headlineMedium, color = textColor)
                Spacer(Modifier.height(32.dp))

                // Use mobile volume toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LanguageManager.getString("timer_use_mobile_volume"), color = textColor, modifier = Modifier.weight(1f))
                    Switch(
                        checked = useMobileVolume,
                        onCheckedChange = {
                            TimerSettingsStateHolder.useMobileVolume.value = it
                            // commit: voorkomt dat init() nog oude async apply()-waarde inleest
                            TimerSettingsStateHolder.save(context, sync = true)
                            if (!it) {
                                TimerSystemAlarmVolume.syncAlarmStreamFromSlider(context)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = buttonColor, checkedTrackColor = buttonColor.copy(alpha = 0.5f))
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Volume slider - disabled when using mobile volume
                Text(
                    "${LanguageManager.getString("timer_volume")}: ${(volume * 100).toInt()}%",
                    color = if (useMobileVolume) textColor.copy(alpha = 0.4f) else textColor
                )
                Slider(
                    value = volume,
                    onValueChange = { newVal ->
                        if (!useMobileVolume) {
                            TimerSettingsStateHolder.volume.value = newVal
                            TimerSettingsStateHolder.persistVolumeFractionOnly(context)
                            TimerSystemAlarmVolume.syncAlarmStreamFromSlider(context)
                        }
                    },
                    enabled = !useMobileVolume,
                    colors = SliderDefaults.colors(
                        thumbColor = if (useMobileVolume) buttonColor.copy(alpha = 0.4f) else buttonColor,
                        activeTrackColor = if (useMobileVolume) buttonColor.copy(alpha = 0.4f) else buttonColor,
                        disabledThumbColor = buttonColor.copy(alpha = 0.4f),
                        disabledActiveTrackColor = buttonColor.copy(alpha = 0.4f)
                    )
                )

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LanguageManager.getString("timer_vibrate"), color = textColor, modifier = Modifier.weight(1f))
                    Switch(
                        checked = vibrate,
                        onCheckedChange = {
                            TimerSettingsStateHolder.vibrationEnabled.value = it
                            TimerSettingsStateHolder.save(context)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = buttonColor, checkedTrackColor = buttonColor.copy(alpha = 0.5f))
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LanguageManager.getString("timer_popup"), color = textColor, modifier = Modifier.weight(1f))
                    Switch(
                        checked = popupEnabled,
                        onCheckedChange = {
                            TimerSettingsStateHolder.setPopupEnabled(context, it)
                            if (it) {
                                TimerPopupForegroundService.syncFromTimerState(context, "settings_toggle_on")
                            } else {
                                TimerPopupForegroundService.stopPopup(context, "settings_toggle_off")
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = buttonColor, checkedTrackColor = buttonColor.copy(alpha = 0.5f))
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LanguageManager.getString("timer_multi_timer"), color = textColor, modifier = Modifier.weight(1f))
                    Switch(
                        checked = TimerSettingsStateHolder.multiTimerEnabled.value,
                        onCheckedChange = {
                            TimerSettingsStateHolder.multiTimerEnabled.value = it
                            TimerSettingsStateHolder.save(context)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = buttonColor, checkedTrackColor = buttonColor.copy(alpha = 0.5f))
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (ddMusicLinked) {
                    Text(
                        "DD Music: ${ddMusicSummary ?: "Gebruik DD Music Als Alarm"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Button(
                    onClick = { showRingtoneDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("timer_select_sound"))
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        // Deelt dezelfde HA-instellingen als het alarm (HomeAssistantSettingsStorage);
                        // "uit bed" check is hier niet relevant voor een timer.
                        val intent = Intent(context, HaSettingsActivity::class.java)
                        intent.putExtra("hide_out_of_bed", true)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("ka_ha"))
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("back"))
                }
            }
            
            SwipeIndicators(isVertical = isVertical, showDown = true, onSwipeDown = onBack)
        }
    }
}

@Composable
private fun InteractionSource.collectIsPressedAsStateLocal(): State<Boolean> {
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
fun SwipeableTimeUnitEnhanced(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    textColor: Color,
    arrowSize: Dp = 40.dp,
    valueBoxHeight: Dp = 80.dp,
    columnWidth: Dp = 80.dp,
    valueStyle: TextStyle? = null,
) {
    val upInteractionSource = remember { MutableInteractionSource() }
    val downInteractionSource = remember { MutableInteractionSource() }
    val isUpPressed by upInteractionSource.collectIsPressedAsStateLocal()
    val isDownPressed by downInteractionSource.collectIsPressedAsStateLocal()

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

    fun adjustValue(delta: Int) {
        var newValue = value + delta
        if (newValue > range.last) newValue = range.first
        if (newValue < range.first) newValue = range.last
        onValueChange(newValue)
    }

    val resolvedValueStyle = valueStyle ?: MaterialTheme.typography.displayMedium

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(columnWidth)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Omhoog",
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier
                .size(arrowSize)
                .clickable(interactionSource = upInteractionSource, indication = null) { adjustValue(1) }
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(valueBoxHeight)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { },
                        onDragCancel = { }
                    ) { _, dragAmount ->
                        // Drag logic handled by gestures if needed, but here we rely on click/long-press
                    }
                }
        ) {
            Text(
                text = String.format("%02d", value),
                style = resolvedValueStyle,
                color = textColor
            )
        }

        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Omlaag",
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier
                .size(arrowSize)
                .clickable(interactionSource = downInteractionSource, indication = null) { adjustValue(-1) }
        )

        Text(label, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
    }
}

