package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.getBoxAlignmentFromString
import com.dd.daykit.ui.getHorizontalAlignment
import com.dd.daykit.ui.getTextAlign
import kotlin.math.abs

class KalenderAlarmInstellingenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                KalenderAlarmInstellingenScreen { finish() }
            }
        }
    }
}

@Composable
fun KalenderAlarmInstellingenScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp

    val currentLanguage by LanguageManager.currentLanguage
    remember(currentLanguage.code) { Unit }
    val swipeEnabled = SettingsManager.getSwipeEnabled(ctx)
    val arrowNavigationEnabled = SettingsManager.getArrowMode(ctx) != "Verborgen"

    var textColor by remember { mutableStateOf(Color(SettingsManager.getTextColor(ctx))) }
    var buttonColor by remember { mutableStateOf(Color(SettingsManager.getButtonColor(ctx))) }
    var buttonTextColor by remember { mutableStateOf(Color(SettingsManager.getButtonTextColor(ctx))) }
    var textAlignment by remember { mutableStateOf(SettingsManager.getTextAlignment(ctx)) }
    
    // Sync status tracking using SyncStatusManager
    val lastSyncTime by SyncStatusManager.lastSyncTime.collectAsState()
    var isPeriodicSyncActive by remember { mutableStateOf(false) }
    
    // App status tracking
    var appStatus by remember { mutableStateOf<AppStatusChecker.AppStatus?>(null) }
    
    // Initialize SyncStatusManager and check app status
    LaunchedEffect(Unit) {
        SyncStatusManager.init(ctx)
        isPeriodicSyncActive = SyncStatusManager.isPeriodicSyncActive(ctx)
        appStatus = AppStatusChecker.getDetailedStatus(ctx)
    }

    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                textColor = Color(SettingsManager.getTextColor(ctx))
                buttonColor = Color(SettingsManager.getButtonColor(ctx))
                buttonTextColor = Color(SettingsManager.getButtonTextColor(ctx))
                textAlignment = SettingsManager.getTextAlignment(ctx)
                if (context is ComponentActivity) {
                    context.window.statusBarColor = SettingsManager.getBackgroundColor(ctx)
                }
            }
        }
        val filter = IntentFilter("com.dd.daykit.DESIGN_UPDATED")
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    val titleTextAlign = getTextAlign(textAlignment)

    fun navigateToLastVisited() {
        val lastPage = SettingsManager.getLastVisitedKalenderSubSettingsPage(ctx)
        val intent = when (lastPage) {
            "ALARM" -> Intent(ctx, AlarmSettingsActivity::class.java)
            "HA" -> Intent(ctx, HaSettingsActivity::class.java)
            "CLOCK" -> Intent(ctx, ClockLayoutActivity::class.java)
            "MELDINGEN" -> Intent(ctx, MeldingenSettingsActivity::class.java)
            else -> null
        }
        intent?.let { ctx.startActivity(it) }
    }

    fun openAlarmTriggersSettings() {
        SettingsManager.saveLastVisitedKalenderSubSettingsPage(ctx, "ALARM")
        ctx.startActivity(Intent(ctx, AlarmSettingsActivity::class.java))
    }

    AppBackground(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragStart = Offset.Zero
                var isDragConsumed = false
                detectDragGestures(
                    onDragStart = {
                        dragStart = it
                        isDragConsumed = false
                    },
                    onDragEnd = { isDragConsumed = false },
                    onDragCancel = { isDragConsumed = false },
                    onDrag = { change, dragAmount ->
                        if (!swipeEnabled) return@detectDragGestures
                        if (isDragConsumed) {
                            change.consume()
                            return@detectDragGestures
                        }

                        val totalVerticalDrag = change.position.y - dragStart.y
                        val dragThreshold = 50f

                        // Check for significant vertical movement
                        if (abs(totalVerticalDrag) > dragThreshold && abs(totalVerticalDrag) > abs(change.position.x - dragStart.x)) {
                            if (totalVerticalDrag > 0) { // Swipe Down (Top to Bottom) -> Same as Arrow Up (Navigate to last visited)
                                navigateToLastVisited()
                                isDragConsumed = true
                                change.consume()
                            } else { // Swipe Up (Bottom to Top) -> Same as Arrow Down (Go Back)
                                onBack()
                                isDragConsumed = true
                                change.consume()
                            }
                        }
                    }
                )
            }
    ) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            
            // Content Column - REMOVED SCROLL to fix swipe interference
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = getHorizontalAlignment(textAlignment)
            ) {
                // App Status Banner
                appStatus?.let { status ->
                    if (status.hasIssues) {
                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = Color(0xFFFF6B6B).copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = LanguageManager.getString("cd_warning"),
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        status.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    if (!status.servicesEnabled) {
                                        Text(
                                            LanguageManager.getString("app_status_periodic_sync_warning"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    LanguageManager.getString("ka_title"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor,
                    textAlign = titleTextAlign,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = { openAlarmTriggersSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                ) {
                    Text(LanguageManager.getString("ka_alarm_options"))
                }
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        SettingsManager.saveLastVisitedKalenderSubSettingsPage(ctx, "HA")
                        ctx.startActivity(Intent(ctx, HaSettingsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                ) {
                    Text(LanguageManager.getString("ka_ha"))
                }
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        SettingsManager.saveLastVisitedKalenderSubSettingsPage(ctx, "CLOCK")
                        ctx.startActivity(Intent(ctx, ClockLayoutActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                ) {
                    Text(LanguageManager.getString("clock_layout"))
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        SettingsManager.saveLastVisitedKalenderSubSettingsPage(ctx, "MELDINGEN")
                        ctx.startActivity(Intent(ctx, MeldingenSettingsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                ) {
                    Text(LanguageManager.getString("ka_meldingen"))
                }
                
            }
            
            // Pijl omhoog (Bovenaan)
            if (arrowNavigationEnabled) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Navigate to last setting",
                    tint = textColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .size(48.dp)
                        .clickable { navigateToLastVisited() }
                )
            }
            
            // Pijl omlaag (Onderaan)
            com.dd.daykit.ui.SwipeIndicators(
                modifier = Modifier.align(Alignment.BottomCenter),
                isVertical = isVertical,
                showDown = true,
                onSwipeDown = { onBack() }
            )
        }
    }
}

// Helper function to format sync time
private fun formatSyncTime(timeMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timeMillis
    
    return when {
        diff < 60000 -> "zojuist"
        diff < 3600000 -> "${diff / 60000} min geleden"
        diff < 86400000 -> "${diff / 3600000} uur geleden"
        else -> {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timeMillis))
        }
    }
}
