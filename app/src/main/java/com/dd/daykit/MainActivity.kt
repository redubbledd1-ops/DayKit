package com.dd.daykit

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.dd.daykit.rules.PreAlarmCheckStorage
import com.dd.daykit.rules.PreAlarmUiStatus
import com.dd.daykit.ui.theme.AgendaWekkerTheme
import com.dd.daykit.ui.getBoxAlignmentFromString
import com.dd.daykit.ui.getHorizontalAlignment
import com.dd.daykit.ui.CalendarFeatureGateCard
import com.dd.daykit.ui.KalenderAlarmTriggerOnboardingCard
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.rememberInAppNotificationsEnabled
import com.dd.daykit.ui.PreAlarmBanner
import com.dd.daykit.ui.isPreAlarmBannerDisplayable
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.getTextAlign
import com.dd.daykit.util.CountdownFormatter
import com.dd.daykit.util.CountdownMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    var pendingBackupUri: Uri? = null
    var pendingBackupFilename: String? = null

    companion object {
        /**
         * POC notification content tap / deep-link: show Agenda Alarm “Alarm over” inside this activity,
         * skipping the configured alternate start screen redirect.
         */
        const val EXTRA_OPEN_AGENDA_ALARM = "com.dd.daykit.EXTRA_OPEN_AGENDA_ALARM"
    }

    /**
     * Initialize HTTP server if enabled
     */
    private fun initHttpServer() {
        try {
            val serverManager = com.dd.daykit.sound.HttpServerManager
            
            if (serverManager.isEnabled(this)) {
                // Check if server is already running
                if (!serverManager.isRunning()) {
                    android.util.Log.i("MainActivity", "Starting HTTP server...")
                    serverManager.startServer(this)
                    
                    // Verify it started
                    if (serverManager.isRunning()) {
                        android.util.Log.i("MainActivity", "HTTP server started successfully")
                    } else {
                        android.util.Log.e("MainActivity", "HTTP server failed to start")
                    }
                } else {
                    android.util.Log.i("MainActivity", "HTTP server already running")
                }
            } else {
                android.util.Log.d("MainActivity", "HTTP server not enabled")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error initializing HTTP server", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start Screen Logic: Check preference and redirect if needed
        val fromNavigation = intent.getBooleanExtra("FROM_NAVIGATION", false)
        val forceAgendaAlarm = intent.getBooleanExtra(EXTRA_OPEN_AGENDA_ALARM, false)

        // Only redirect if NOT coming from navigation and NOT opening Agenda Alarm from notification POC
        if (!fromNavigation && !forceAgendaAlarm) {
            val startScreenId = SettingsManager.getStartScreenId(this)
            
            // If start screen is NOT Agenda Alarm (this activity) AND not Settings (which is a separate flow), redirect
            if (startScreenId != "AGENDA_ALARM" && startScreenId.isNotEmpty()) {
                val screen = Screen.values().find { it.id == startScreenId }
                
                // Double check to avoid infinite loop if MainActivity IS the target (should be covered by id check, but safety first)
                if (screen != null && screen.activityClass != MainActivity::class.java) {
                    val intent = Intent(this, screen.activityClass)
                    // Clear task to make it feel like the root activity
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    return // Stop execution of onCreate
                }
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        // Init Language Manager
        LanguageManager.init(this)
        
        // Init Sync Status Manager
        SyncStatusManager.init(this)
        
        // ============================================================
        // CRITICAL: Initialize ALL state holders BEFORE GlobalInAppMessageManager
        // This ensures state is restored from persistence on app restart
        // so the message bar shows correct status immediately
        // ============================================================
        
        // Init Timer state (restores running/paused timer from SharedPreferences)
        TimerSettingsStateHolder.init(this)
        GlobalTimerManager.init(this)
        
        // Init Stopwatch state (restores running/paused stopwatch from SharedPreferences)
        StopwatchStateHolder.init(this)
        
        // Init Global In-App Message Manager (reads from state holders above)
        GlobalInAppMessageManager.init(this)
        
        // Auto-start HTTP server if enabled
        initHttpServer()

        setContent {
            AgendaWekkerTheme {
                PermissionGate()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Blijft nodig: onCreate leest EXTRA_OPEN_AGENDA_ALARM van getIntent(), dus een tik op een
        // melding terwijl de activity al leeft moet het nieuwe intent doorzetten.
        setIntent(intent)
    }
}

@Composable
fun PermissionGate() {
    val context = LocalContext.current
    var permissionsState by remember { mutableStateOf(checkAllPermissions(context)) }
    var onboardingCompleted by remember { mutableStateOf(SettingsManager.getPermissionOnboardingCompleted(context)) }

    fun recheckPermissions() {
        permissionsState = checkAllPermissions(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                recheckPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (permissionsState.requiredPermissionsGranted && onboardingCompleted) {
        MainAppScreen()
    } else {
        PermissionChecklistScreen(
            state = permissionsState,
            requestNotificationPermission = {
                PermissionSettingsNavigator.openNotificationSettings(context)
            },
            requestCalendarPermission = {
                PermissionSettingsNavigator.openCalendarPermissionSettings(context)
            },
            requestExactAlarmPermission = {
                PermissionSettingsNavigator.openExactAlarmSettings(context)
            },
            requestFullScreenIntentPermission = {
                SettingsManager.savePermissionOnboardingCompleted(context, false)
                SettingsManager.saveFullScreenAlarmEnabled(context, true)
                FullScreenIntentPermission.openSettings(context)
            },
            requestOverlayPermission = {
                SettingsManager.savePermissionOnboardingCompleted(context, false)
                SettingsManager.saveDrawOverOtherAppsEnabled(context, true)
                OverlayPermission.openSettings(context)
            },
            onContinue = {
                recheckPermissions()
                if (checkAllPermissions(context).requiredPermissionsGranted) {
                    SettingsManager.savePermissionOnboardingCompleted(context, true)
                    onboardingCompleted = true
                }
            }
        )
    }
}

data class PermissionsState(
    val hasNotification: Boolean,
    val hasCalendar: Boolean,
    val hasExactAlarm: Boolean,
    val canUseFullScreenIntent: Boolean,
    val hasOverlayPermission: Boolean,
) {
    /** Full-screen intent, overlay, notificaties en kalender zijn optioneel; exact alarm blijft vereist voor betrouwbare timing. */
    val requiredPermissionsGranted: Boolean
        get() = hasExactAlarm
}

private fun checkAllPermissions(context: Context): PermissionsState {
    val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else { true }

    val calendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AlarmManagerCompat.canScheduleExactAlarms(alarmManager)
    } else { true }

    val fullScreenIntent = FullScreenIntentPermission.check(context).canUse
    val overlayPermission = OverlayPermission.hasSystemPermission(context)
    android.util.Log.i(
        "MainActivity",
        "checkAllPermissions exactAlarm=$exactAlarm canUseFullScreenIntent=$fullScreenIntent " +
            "canDrawOverlays=$overlayPermission api=${Build.VERSION.SDK_INT}"
    )

    return PermissionsState(notification, calendar, exactAlarm, fullScreenIntent, overlayPermission)
}

@Composable
fun PermissionChecklistScreen(
    state: PermissionsState,
    requestNotificationPermission: () -> Unit,
    requestCalendarPermission: () -> Unit,
    requestExactAlarmPermission: () -> Unit,
    requestFullScreenIntentPermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val scrollState = rememberScrollState()
    var selectedInfo by remember { mutableStateOf<PermissionInfoDialogState?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                LanguageManager.getString("permissions_title"),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = textColor,
            )
            Spacer(Modifier.height(14.dp))
            Text(LanguageManager.getString("permissions_section_optional"), style = MaterialTheme.typography.titleSmall, color = textColor.copy(alpha = 0.85f), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            PermissionRow(
                title = LanguageManager.getString("permission_full_screen_intent"),
                infoText = LanguageManager.getString("permission_full_screen_intent_explanation"),
                isGranted = state.canUseFullScreenIntent,
                onRequest = requestFullScreenIntentPermission,
                onInfoClick = { selectedInfo = it },
            )
            PermissionRow(
                title = LanguageManager.getString("permission_draw_over_other_apps"),
                infoText = LanguageManager.getString("permission_draw_over_other_apps_explanation"),
                isGranted = state.hasOverlayPermission,
                onRequest = requestOverlayPermission,
                onInfoClick = { selectedInfo = it },
            )
            PermissionRow(
                title = LanguageManager.getString("permission_read_calendar"),
                infoText = LanguageManager.getString("permissions_optional_calendar_explanation"),
                isGranted = state.hasCalendar,
                onRequest = requestCalendarPermission,
                onInfoClick = { selectedInfo = it },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    title = LanguageManager.getString("permission_notifications"),
                    infoText = LanguageManager.getString("permission_notifications_explanation"),
                    isGranted = state.hasNotification,
                    onRequest = requestNotificationPermission,
                    onInfoClick = { selectedInfo = it },
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = textColor.copy(alpha = 0.25f))
            Spacer(Modifier.height(10.dp))
            Text(LanguageManager.getString("permissions_section_required"), style = MaterialTheme.typography.titleSmall, color = textColor, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            PermissionRow(
                title = LanguageManager.getString("permission_alarms_and_reminders"),
                infoText = LanguageManager.getString("permission_alarms_and_reminders_explanation"),
                isGranted = state.hasExactAlarm,
                onRequest = requestExactAlarmPermission,
                onInfoClick = { selectedInfo = it },
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
            ) {
                Text(LanguageManager.getString("permission_button_check_permissions"))
            }
        }
    }

    selectedInfo?.let { info ->
        PermissionInfoDialog(
            info = info,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            onDismiss = { selectedInfo = null },
        )
    }
}

@Composable
fun PermissionRow(
    title: String,
    infoText: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    onInfoClick: (PermissionInfoDialogState) -> Unit,
) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                onInfoClick(
                    PermissionInfoDialogState(
                        title = title,
                        message = infoText,
                    ),
                )
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = LanguageManager.getString("permission_info"),
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = {
                if (!isGranted) onRequest()
            },
            enabled = !isGranted,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Settings,
                contentDescription = if (isGranted) {
                    LanguageManager.getString("permission_status_granted")
                } else {
                    LanguageManager.getString("permission_open_android_settings")
                },
                tint = if (isGranted) Color.Green else textColor.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

data class PermissionInfoDialogState(
    val title: String,
    val message: String,
)

@Composable
fun PermissionInfoDialog(
    info: PermissionInfoDialogState,
    buttonColor: Color,
    buttonTextColor: Color,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.title) },
        text = { Text(info.message) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = buttonTextColor,
                ),
            ) {
                Text(LanguageManager.getString("ok"))
            }
        },
    )
}

/** Home next-alarm UI: loading ≠ empty; keep showing last known alarm until refresh confirms none. */
private sealed interface MainNextAlarmUiState {
    data class Loaded(val alarm: AlarmItem) : MainNextAlarmUiState
    object Empty : MainNextAlarmUiState
    object Loading : MainNextAlarmUiState
}

private fun initialMainNextAlarmUiState(context: Context): MainNextAlarmUiState {
    val cached = GlobalInAppMessageManager.buildCachedNextAlarmItem(context)
    return if (cached != null) MainNextAlarmUiState.Loaded(cached) else MainNextAlarmUiState.Loading
}

private fun MainNextAlarmUiState.displayAlarm(): AlarmItem? =
    (this as? MainNextAlarmUiState.Loaded)?.alarm

@Composable
fun MainAppScreen() {
    val ctx = LocalContext.current
    val activity = LocalContext.current as? MainActivity
    var showBackupDialog by remember { mutableStateOf(false) }

    val doScanForBackup = {
        // De "al aangeboden"-lijst staat in de gewone app-instellingen, en die worden bij een
        // restore volledig overschreven met de inhoud van het backupbestand. Puur daarop
        // vertrouwen betekende dus dat na elke restore dezelfde bestanden opnieuw werden
        // aangeboden. findRestoreSuggestion() kijkt daarom óók naar de laatste-restore-tijd en
        // naar welke backups dit toestel zelf gemaakt heeft — die staan in BackupPrefs, dat niet
        // in de backup zit en een restore dus wél overleeft.
        val offeredFilenames = SettingsManager.getOfferedBackupFilenames(ctx)
        val candidate = BackupManager.findRestoreSuggestion(ctx)
            ?.takeIf { it.filename !in offeredFilenames }
        if (candidate != null && activity != null) {
            activity.pendingBackupUri = candidate.uri
            activity.pendingBackupFilename = candidate.filename
            showBackupDialog = true
        }
    }

    val storagePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { doScanForBackup() }

    LaunchedEffect(Unit) {
        val needsStoragePerm = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsStoragePerm) {
            storagePermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            doScanForBackup()
        }
    }

    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val currentScreenId = "AGENDA_ALARM"
    val swipeEnabled = SettingsManager.getSwipeEnabled(ctx)

    // Observe language changes
    val currentLanguage by LanguageManager.currentLanguage

    // State variables that update automatically when settings change
    var textColor by remember { mutableStateOf(Color(SettingsManager.getTextColor(ctx))) }
    var buttonColor by remember { mutableStateOf(Color(SettingsManager.getButtonColor(ctx))) }
    var buttonTextColor by remember { mutableStateOf(Color(SettingsManager.getButtonTextColor(ctx))) }
    var alignmentString by remember { mutableStateOf(SettingsManager.getTextAlignment(ctx)) }
    var clockLayout by remember { mutableStateOf(SettingsManager.getClockLayout(ctx)) }
    var countdownSecondsMode by remember { mutableStateOf(SettingsManager.getCountdownSecondsMode(ctx)) }
    var showCurrentTimeSeconds by remember { mutableStateOf(SettingsManager.getShowCurrentTimeSeconds(ctx)) }
    var showNavButtons by remember { mutableStateOf(SettingsManager.getShowNavButtons(ctx)) }

    val refreshDesignFromPrefs: () -> Unit = {
        textColor = Color(SettingsManager.getTextColor(ctx))
        buttonColor = Color(SettingsManager.getButtonColor(ctx))
        buttonTextColor = Color(SettingsManager.getButtonTextColor(ctx))
        alignmentString = SettingsManager.getTextAlignment(ctx)
        clockLayout = SettingsManager.getClockLayout(ctx)
        countdownSecondsMode = SettingsManager.getCountdownSecondsMode(ctx)
        showCurrentTimeSeconds = SettingsManager.getShowCurrentTimeSeconds(ctx)
        showNavButtons = SettingsManager.getShowNavButtons(ctx)
    }

    val contentAlignment = getBoxAlignmentFromString(alignmentString)
    val horizontalAlignment = getHorizontalAlignment(alignmentString)
    val titleTextAlign = getTextAlign(alignmentString)

    var alarmUiState by remember { mutableStateOf(initialMainNextAlarmUiState(ctx)) }
    val displayAlarm = alarmUiState.displayAlarm()
    var countdown by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    var calendarFeatureStatus by remember {
        mutableStateOf<CalendarFeatureStatus>(CalendarFeatureStatus.Ready)
    }
    var hasConfiguredTriggers by remember {
        mutableStateOf(SettingsManager.getTriggerCalendarIds(ctx).isNotEmpty())
    }

    val openAppDetailsSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun openAlarmTriggersFromMain() {
        SettingsManager.saveLastVisitedKalenderSubSettingsPage(ctx, "ALARM")
        ctx.startActivity(Intent(ctx, AlarmSettingsActivity::class.java))
    }

    // Pre-alarm status state
    var preAlarmStatus by remember { mutableStateOf<PreAlarmUiStatus?>(null) }
    var showPreAlarmBanner by remember { mutableStateOf(true) }
    val inAppNotificationsEnabled = rememberInAppNotificationsEnabled()
    val preAlarmStorage = remember { PreAlarmCheckStorage(ctx) }

    // Function to refresh pre-alarm status (pull-based)
    val refreshPreAlarmStatus: () -> Unit = {
        displayAlarm?.let { alarm ->
            val status = preAlarmStorage.getActivePreAlarmStatus(alarm.id, alarm.epochMillis)
            if (status != null && (preAlarmStatus == null || preAlarmStatus?.checkTimestamp != status.checkTimestamp)) {
                showPreAlarmBanner = true // Auto-show when new status arrives
            }
            preAlarmStatus = status
        } ?: run {
            preAlarmStatus = null
        }
    }

    val updateAlarm: () -> Unit = {
        coroutineScope.launch {
            val result = AlarmScheduler.scheduleNextAlarm(ctx, "main_update_alarm")
            alarmUiState = if (result != null) {
                MainNextAlarmUiState.Loaded(result)
            } else {
                MainNextAlarmUiState.Empty
            }
        }
    }

    val mainScreenCalendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        calendarFeatureStatus = GoogleCalendarFeatureChecker.evaluate(ctx)
        updateAlarm()
    }

    // Lifecycle observer for pull-based pre-alarm status refresh (Fix 2)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, displayAlarm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    refreshPreAlarmStatus()
                    calendarFeatureStatus = GoogleCalendarFeatureChecker.evaluate(ctx)
                    hasConfiguredTriggers = SettingsManager.getTriggerCalendarIds(ctx).isNotEmpty()
                    refreshDesignFromPrefs()
                    AlarmScheduler.restorePendingSnoozeIfNeeded(ctx)
                    if (event == Lifecycle.Event.ON_RESUME) {
                        // Provider can lag behind the calendar app; rescheduling on resume keeps short tests reliable.
                        updateAlarm()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Alarm + design updates; applicationContext so delivery works while MainActivity is stopped (e.g. sub-screens)
    val appContext = ctx.applicationContext
    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    CalendarUpdateReceiver.ACTION_ALARM_UPDATED -> {
                        hasConfiguredTriggers = SettingsManager.getTriggerCalendarIds(ctx).isNotEmpty()
                        updateAlarm()
                        refreshPreAlarmStatus() // Also refresh pre-alarm status
                    }
                    "com.dd.daykit.DESIGN_UPDATED" -> {
                        refreshDesignFromPrefs()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(CalendarUpdateReceiver.ACTION_ALARM_UPDATED)
            addAction("com.dd.daykit.DESIGN_UPDATED")
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            appContext.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(showCurrentTimeSeconds) {
        while (isActive) {
            val format = if (showCurrentTimeSeconds) "HH:mm:ss" else "HH:mm"
            currentTime = SimpleDateFormat(format, Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    LaunchedEffect(alarmUiState, countdownSecondsMode, currentLanguage) {
        when (val state = alarmUiState) {
            MainNextAlarmUiState.Empty -> {
                countdown = LanguageManager.getString("main_no_alarm")
            }
            MainNextAlarmUiState.Loading -> {
                // Keep previous countdown while refresh runs; never show "no alarm" during load.
            }
            is MainNextAlarmUiState.Loaded -> {
                while (isActive) {
                    val alarm = (alarmUiState as? MainNextAlarmUiState.Loaded)?.alarm ?: break
                    val remaining = alarm.epochMillis - System.currentTimeMillis()
                    if (remaining > 0) {
                        countdown = when (countdownSecondsMode) {
                            "ALWAYS" -> CountdownFormatter.format(remaining, CountdownMode.PRECISE_UI)
                            "LAST_5_MIN" -> if (remaining < 5 * 60 * 1000L) {
                                CountdownFormatter.format(remaining, CountdownMode.PRECISE_UI)
                            } else {
                                CountdownFormatter.format(remaining, CountdownMode.PRECISE_UI_NO_SECONDS)
                            }
                            "NEVER" -> CountdownFormatter.format(remaining, CountdownMode.PRECISE_UI_NO_SECONDS)
                            else -> CountdownFormatter.format(remaining, CountdownMode.PRECISE_UI_NO_SECONDS)
                        }
                        delay(1000)
                    } else {
                        val refreshed = AlarmScheduler.scheduleNextAlarm(ctx, "main_countdown_no_alarm_refresh")
                        alarmUiState = if (refreshed != null) {
                            MainNextAlarmUiState.Loaded(refreshed)
                        } else {
                            MainNextAlarmUiState.Empty
                        }
                        if (alarmUiState is MainNextAlarmUiState.Empty) {
                            countdown = LanguageManager.getString("main_no_alarm")
                            delay(2000)
                        } else {
                            delay(400)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        calendarFeatureStatus = GoogleCalendarFeatureChecker.evaluate(ctx)
        hasConfiguredTriggers = SettingsManager.getTriggerCalendarIds(ctx).isNotEmpty()
        updateAlarm()
    }

    // Foreground: periodic reschedule while the main clock is visible (catches delayed provider sync).
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(45_000)
                if (SettingsManager.getTriggerCalendarIds(ctx).isNotEmpty()) {
                    val result = AlarmScheduler.scheduleNextAlarm(ctx, "main_foreground_poll_45s")
                    alarmUiState = if (result != null) {
                        MainNextAlarmUiState.Loaded(result)
                    } else {
                        MainNextAlarmUiState.Empty
                    }
                }
            }
        }
    }

    
    // Also refresh pre-alarm status when displayed alarm changes
    LaunchedEffect(alarmUiState) {
        refreshPreAlarmStatus()
        when (val state = alarmUiState) {
            is MainNextAlarmUiState.Loaded -> {
                NotificationPopupDebugLog.notification(
                    source = "MainActivity.nextAlarm_state",
                    nextAlarmEpoch = state.alarm.epochMillis,
                    displayedTime = countdown,
                    notificationRebuilt = false,
                    extra = "id=${state.alarm.id} label=${state.alarm.label.take(40)} ui=Loaded",
                )
            }
            MainNextAlarmUiState.Empty -> {
                NotificationPopupDebugLog.notification(
                    source = "MainActivity.nextAlarm_state",
                    nextAlarmEpoch = null,
                    displayedTime = countdown,
                    notificationRebuilt = false,
                    extra = "ui=Empty",
                )
            }
            MainNextAlarmUiState.Loading -> {
                NotificationPopupDebugLog.notification(
                    source = "MainActivity.nextAlarm_state",
                    nextAlarmEpoch = displayAlarm?.epochMillis,
                    displayedTime = countdown,
                    notificationRebuilt = false,
                    extra = "ui=Loading",
                )
            }
        }
    }
    
    // Backup polling for pre-alarm status (less frequent, as primary is pull-based)
    LaunchedEffect(displayAlarm) {
        while (isActive) {
            delay(10000) // Poll every 10 seconds as backup
            refreshPreAlarmStatus()
        }
    }
    
    val titleText = when (clockLayout) {
        "CURRENT_BIG_COUNTDOWN_SMALL", "CURRENT_TIME_ONLY" -> LanguageManager.getString("main_time")
        else -> LanguageManager.getString("main_alarm_in")
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            val leftTarget = NavigationManager.getSwipeLeftTarget(ctx, currentScreenId)
            val rightTarget = NavigationManager.getSwipeRightTarget(ctx, currentScreenId)

            val mainContentModifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
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
                                val horizontalDrag = dragStart.x - change.position.x
                                val verticalDrag = change.position.y - dragStart.y

                                if (abs(horizontalDrag) > abs(verticalDrag)) {
                                    // Horizontal drag is dominant
                                    // Swipe from RIGHT to LEFT (Positive value) -> Next Screen
                                    if (horizontalDrag > dragThreshold) { 
                                        NavigationManager.navigateRight(ctx, currentScreenId)
                                        isDragConsumed = true
                                    } 
                                    // Swipe from LEFT to RIGHT (Negative value) -> Previous Screen
                                    else if (horizontalDrag < -dragThreshold) { 
                                        NavigationManager.navigateLeft(ctx, currentScreenId)
                                        isDragConsumed = true
                                    }
                                } else {
                                    // Vertical drag is dominant
                                    // Vinger Boven -> Beneden = y wordt groter = dragAmount.y > 0 (Positief) -> AgendaAlarm Instellingen
                                    if (verticalDrag > dragThreshold) { 
                                        ctx.startActivity(Intent(ctx, KalenderAlarmInstellingenActivity::class.java))
                                        isDragConsumed = true
                                    } 
                                    // Vinger Beneden -> Boven = y wordt kleiner = dragAmount.y < 0 (Negatief) -> Upcoming Alarms
                                    else if (verticalDrag < -dragThreshold) {
                                        ctx.startActivity(Intent(ctx, UpcomingAlarmsActivity::class.java))
                                        isDragConsumed = true
                                    }
                                }
                            }
                            if (isDragConsumed) {
                                change.consume()
                            }
                        }
                    )
                }
                .padding(16.dp)

            Box(
                modifier = mainContentModifier,
                contentAlignment = contentAlignment
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(), // Ensures the column uses the full width for alignment
                    horizontalAlignment = horizontalAlignment
                ) {
                    // Global in-app messages handled by GlobalTimerStatusBar in BackgroundRenderer
                    
                    // Pre-alarm banner (T-5 minutes notification)
                    PreAlarmBanner(
                        status = preAlarmStatus,
                        visible = showPreAlarmBanner && preAlarmStatus != null,
                        textColor = textColor,
                        buttonColor = buttonColor,
                        buttonTextColor = buttonTextColor,
                        onDismiss = { showPreAlarmBanner = false },
                        onForceAlarm = {
                            // User wants alarm to fire anyway - override the cancel decision
                            displayAlarm?.let { alarm ->
                                PreAlarmCheckStorage(ctx).forceAlarmToFire(alarm.id)
                                // Update local state
                                preAlarmStatus = preAlarmStatus?.copy(willAlarmFire = true)
                            }
                        }
                    )
                    
                    if (inAppNotificationsEnabled && showPreAlarmBanner && isPreAlarmBannerDisplayable(preAlarmStatus)) {
                        Spacer(Modifier.height(12.dp))
                    }

                    val showStandardAlarmContent =
                        calendarFeatureStatus == CalendarFeatureStatus.Ready && hasConfiguredTriggers

                    if (showStandardAlarmContent) {
                        Text(
                            titleText,
                            style = MaterialTheme.typography.titleLarge,
                            color = textColor,
                            textAlign = titleTextAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(-16.dp)) // More negative space
                        ClockView(
                            layout = clockLayout,
                            countdown = countdown,
                            currentTime = currentTime,
                            textColor = textColor
                        )
                        Spacer(Modifier.height(16.dp))
                    } else {
                        if (calendarFeatureStatus != CalendarFeatureStatus.Ready) {
                            CalendarFeatureGateCard(
                                status = calendarFeatureStatus,
                                textColor = textColor,
                                buttonColor = buttonColor,
                                buttonTextColor = buttonTextColor,
                                onGrantCalendarPermission = {
                                    mainScreenCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                },
                                onOpenAppSettings = openAppDetailsSettings,
                                onRetry = {
                                    calendarFeatureStatus = GoogleCalendarFeatureChecker.evaluate(ctx)
                                    updateAlarm()
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.78f)
                                    .align(Alignment.CenterHorizontally)
                            )
                        }
                        if (calendarFeatureStatus == CalendarFeatureStatus.Ready && !hasConfiguredTriggers) {
                            KalenderAlarmTriggerOnboardingCard(
                                title = LanguageManager.getString("ka_trigger_onboarding_title"),
                                buttonLabel = LanguageManager.getString("ka_trigger_onboarding_cta"),
                                textColor = textColor,
                                buttonColor = buttonColor,
                                buttonTextColor = buttonTextColor,
                                onSelectTrigger = { openAlarmTriggersFromMain() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            
            // Swipe indicators - placed in outer Box for correct positioning
            SwipeIndicators(
                isVertical = isVertical,
                showLeft = leftTarget != null,
                showRight = rightTarget != null,
                showUp = true,
                showDown = true,
                onSwipeLeft = { NavigationManager.navigateLeft(ctx, currentScreenId) }, 
                onSwipeRight = { NavigationManager.navigateRight(ctx, currentScreenId) }, 
                onSwipeUp = { ctx.startActivity(Intent(ctx, KalenderAlarmInstellingenActivity::class.java)) }, 
                onSwipeDown = { ctx.startActivity(Intent(ctx, UpcomingAlarmsActivity::class.java)) } 
            )

            // Navigation bar at the bottom
            if (showNavButtons) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                ) {
                    NavigationBar(currentPage = currentScreenId)
                }
            }

            val backupActivity = activity
            val backupUri = backupActivity?.pendingBackupUri
            if (showBackupDialog && backupActivity != null && backupUri != null) {
                val backupFilenameForDialog = backupActivity.pendingBackupFilename

                fun dismissBackupDialog() {
                    backupFilenameForDialog?.let { SettingsManager.markBackupFilenameOffered(ctx, it) }
                    showBackupDialog = false
                    backupActivity.pendingBackupUri = null
                    backupActivity.pendingBackupFilename = null
                }

                // Kleuren en teksten volgen nu het thema en de taalinstelling, net als de andere
                // dialogen in de app. Deze melding gebruikte nog de Material-standaardkleuren en
                // hardgecodeerd Nederlands, waardoor hij in een aangepast thema uit de toon viel.
                AlertDialog(
                    onDismissRequest = { dismissBackupDialog() },
                    containerColor = Color(SettingsManager.getBackgroundColor(ctx)),
                    title = {
                        Text(
                            text = LanguageManager.getString("backup_found_dialog_title"),
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Text(
                            text = if (backupFilenameForDialog != null) {
                                String.format(
                                    LanguageManager.getLocale(),
                                    LanguageManager.getString("backup_found_dialog_message"),
                                    backupFilenameForDialog
                                )
                            } else {
                                LanguageManager.getString("backup_found_dialog_message_generic")
                            },
                            color = textColor.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val restoreUri = backupActivity.pendingBackupUri ?: backupUri
                                dismissBackupDialog()
                                backupActivity.startActivity(
                                    Intent(backupActivity, BackupRestoreActivity::class.java).apply {
                                        putExtra(BackupRestoreActivity.EXTRA_RESTORE_URI, restoreUri.toString())
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = buttonTextColor
                            )
                        ) {
                            Text(LanguageManager.getString("backup_restore_yes"))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { dismissBackupDialog() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                        ) {
                            Text(LanguageManager.getString("answer_no"))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ClockView(
    layout: String,
    countdown: String,
    currentTime: String,
    textColor: Color
) {
    when (layout) {
        "COUNTDOWN_BIG_CURRENT_SMALL" -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp) // More space between lines
            ) {
                Text(
                    text = countdown,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.displayLarge,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                if (currentTime.isNotEmpty()) {
                    val timeText = "${LanguageManager.getString("main_time")} $currentTime"
                    Text(timeText, style = MaterialTheme.typography.headlineSmall, color = textColor)
                }
            }
        }
        "CURRENT_BIG_COUNTDOWN_SMALL" -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp) // More space between lines
            ) {
                Text(currentTime, style = MaterialTheme.typography.displayLarge, color = textColor)
                val countdownText = if (countdown != LanguageManager.getString("main_no_alarm")) 
                    "${LanguageManager.getString("main_alarm_in")} $countdown" 
                else countdown
                Text(countdownText, style = MaterialTheme.typography.headlineSmall, color = textColor)
            }
        }
        "COUNTDOWN_ONLY" -> {
            Text(
                text = countdown,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
        "CURRENT_TIME_ONLY" -> {
            Text(currentTime, style = MaterialTheme.typography.displayLarge, color = textColor)
        }
    }
}
