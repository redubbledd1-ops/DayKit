package com.dd.daykit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.work.ExistingPeriodicWorkPolicy
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.swipeUpBackGesture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AgendaSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        setContent {
            MaterialTheme {
                AgendaSettingsScreen { finish() }
            }
        }
    }
}

private val INTERVAL_OPTIONS: List<Long> = listOf(15L, 30L, 60L, 120L, 240L, 360L)

private fun intervalLabel(minutes: Long): String {
    return if (minutes < 60L) {
        "$minutes ${LanguageManager.getString("min")}"
    } else {
        "${minutes / 60L} ${LanguageManager.getString("hour")}"
    }
}

@Composable
fun AgendaSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    val showNavButtons = SettingsManager.getShowNavButtons(context)

    var autoSyncEnabled by remember { mutableStateOf(SettingsManager.getAutoSyncEnabled(context)) }
    var intervalMinutes by remember { mutableStateOf(SettingsManager.getPeriodicSyncIntervalMinutes(context)) }

    val isSyncing by SyncStatusManager.isSyncing.collectAsState()
    val lastSyncSuccess by SyncStatusManager.lastSyncSuccess.collectAsState()
    val lastErrorMessage by SyncStatusManager.lastErrorMessage.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SyncStatusManager.init(context)
    }

    val navigateBackToSettings: () -> Unit = {
        context.startActivity(Intent(context, SettingsActivity::class.java))
        (context as? Activity)?.finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .swipeUpBackGesture(
                enabled = swipeEnabled,
                onBack = navigateBackToSettings,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = LanguageManager.getString("nav_agenda"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                // --- Handmatige sync (volledige flow, incl. HA + broadcast) ---
                Button(
                    onClick = {
                        if (!isSyncing) {
                            coroutineScope.launch {
                                val result = KalenderAlarmManualSync.run(context)
                                if (result != null && SyncStatusManager.lastSyncSuccess.value) {
                                    delay(300)
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        }
                    },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSyncing) buttonColor.copy(alpha = 0.6f) else buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    if (isSyncing) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = buttonTextColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(LanguageManager.getString("ka_syncing"))
                        }
                    } else {
                        Text(LanguageManager.getString("ka_sync"))
                    }
                }

                Spacer(Modifier.height(4.dp))

                val statusText = SyncStatusManager.getStatusMessage(context)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lastSyncSuccess) textColor.copy(alpha = 0.7f) else Color.Red.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!lastSyncSuccess && lastErrorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${LanguageManager.getString("ka_sync")}: $lastErrorMessage",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                // --- Auto-sync toggle ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            LanguageManager.getString("agenda_auto_sync_title"),
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            LanguageManager.getString("agenda_auto_sync_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f),
                        )
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { enabled ->
                            // Alleen lokale UI-state; pas bij "Opslaan" wordt dit echt toegepast.
                            autoSyncEnabled = enabled
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = buttonColor,
                            checkedTrackColor = buttonColor.copy(alpha = 0.5f)
                        )
                    )
                }

                // --- Interval selector (alleen zichtbaar als auto-sync AAN) ---
                if (autoSyncEnabled) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        LanguageManager.getString("agenda_interval_title"),
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        LanguageManager.getString("agenda_interval_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    val rows = INTERVAL_OPTIONS.chunked(3)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rows.forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                rowOptions.forEach { mins ->
                                    Button(
                                        onClick = {
                                            // Alleen lokale UI-state; pas bij "Opslaan" wordt dit echt toegepast.
                                            intervalMinutes = mins
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (intervalMinutes == mins) buttonColor else buttonColor.copy(alpha = 0.4f),
                                            contentColor = buttonTextColor
                                        )
                                    ) { Text(intervalLabel(mins)) }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // --- Batterij-uitzondering: 1 korte regel + 1 knop, geen extra uitleg ---
                Text(
                    LanguageManager.getString("agenda_appinfo_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { PermissionSettingsNavigator.openAppInfoSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor.copy(alpha = 0.85f),
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("agenda_appinfo_button"))
                }

                Spacer(Modifier.height(20.dp))

                // --- Diagnose-log: vastleggen waarom een agenda-alarm niet afging ---
                Text(
                    "Diagnose-log alarm",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (!AgendaAlarmForensics.share(context)) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Nog geen diagnose-gegevens vastgelegd",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor.copy(alpha = 0.85f),
                            contentColor = buttonTextColor
                        )
                    ) { Text("Log delen") }

                    Button(
                        onClick = {
                            AgendaAlarmForensics.clear(context)
                            AgendaAlarmForensics.log(
                                AgendaAlarmForensics.Cat.PROCESS,
                                "Log handmatig gewist door gebruiker",
                                context
                            )
                            AgendaAlarmForensics.snapshotArmedState(context, "na_handmatig_wissen")
                            android.widget.Toast.makeText(
                                context,
                                "Log gewist",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor.copy(alpha = 0.55f),
                            contentColor = buttonTextColor
                        )
                    ) { Text("Log wissen") }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor.copy(alpha = 0.7f),
                            contentColor = buttonTextColor
                        )
                    ) { Text(LanguageManager.getString("cancel")) }

                    Button(
                        onClick = {
                            SettingsManager.saveAutoSyncEnabled(context, autoSyncEnabled)
                            SettingsManager.savePeriodicSyncIntervalMinutes(context, intervalMinutes)
                            if (autoSyncEnabled) {
                                CalendarSyncWorker.schedule(context.applicationContext, ExistingPeriodicWorkPolicy.REPLACE)
                            } else {
                                CalendarSyncWorker.cancel(context.applicationContext)
                            }
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) { Text(LanguageManager.getString("save")) }
                }
            }

            if (showNavButtons) {
                Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                    NavigationBar(currentPage = "SETTINGS")
                }
            }
        }

        SwipeIndicators(
            isVertical = isVertical,
            showUp = true,
            upIndicatorPointsDown = true,
            upTouchSize = 120.dp,
            onSwipeUp = navigateBackToSettings
        )
    }
}
