package com.dd.daykit

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.dd.daykit.rules.TriggerBehaviorMode
import com.dd.daykit.rules.TriggerBehaviorStorage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmRingingDebug.i(
            "RingActivity.onCreate wakeMobile=${WakeMobilePolicy.isWakeMobileEnabled(this)} " +
                "savedInstanceState=${savedInstanceState != null} hasAlarmExtra=${intent.hasExtra("alarm")}"
        )
        AlarmRingingDebug.logFsiPermission(this, "RingActivity.onCreate")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applyAlarmOverlaySystemBarColors(
            window,
            SettingsManager.resolveAlarmOverlayBackgroundColor(this),
        )
        if (WakeMobilePolicy.isWakeMobileEnabled(this)) {
            setShowWhenLocked(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
            AlarmRingingDebug.i("wakeFlow phase=RingActivity.onCreate enabled=true lockscreenWake=true")
        } else {
            AlarmRingingDebug.i("wakeFlow phase=RingActivity.onCreate enabled=false lockscreenWake=false")
        }

        val alarmJson = intent.getStringExtra("alarm")
        
        setContent {
            MaterialTheme {
                RingScreen(alarmJson) { finish() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmRingingDebug.i(
            "RingActivity.onResume wakeMobile=${WakeMobilePolicy.isWakeMobileEnabled(this)} " +
                "isRinging=${AlarmStateManager.isRingingNow} " +
                "label=${AlarmStateManager.ringingAlarmNow?.label}"
        )
    }
}

@Composable
fun RingScreen(alarmJson: String?, onFinish: () -> Unit) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf("") }
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    val alarm = remember(alarmJson) {
        alarmJson?.let {
            try {
                kotlinx.serialization.json.Json.decodeFromString<AlarmItem>(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    val isRinging by AlarmStateManager.isRinging.collectAsState()
    var seenRinging by remember { mutableStateOf(false) }
    LaunchedEffect(isRinging) {
        if (isRinging) {
            seenRinging = true
        } else if (seenRinging) {
            onFinish()
        }
    }
    
    // Haal trigger behavior mode op
    var triggerMode by remember { mutableStateOf<TriggerBehaviorMode?>(null) }
    var preventManualDismiss by remember { mutableStateOf(false) }
    var canSnooze by remember { mutableStateOf(true) }
    
    val behaviorTriggerId = alarm?.triggerId ?: alarm?.snoozeSourceTriggerId
    LaunchedEffect(behaviorTriggerId, alarm?.id, alarm?.epochMillis) {
        triggerMode = null
        preventManualDismiss = false
        canSnooze = withContext(Dispatchers.IO) {
            AgendaAlarmSnoozeCoordinator.canOfferSnooze(context, alarm)
        }
        behaviorTriggerId?.let { triggerId ->
            val storage = TriggerBehaviorStorage(context)
            val config = storage.getConfig(triggerId)
            triggerMode = config.mode
            
            // Check of dismiss toegestaan is voor SMART_ALARM
            if (config.mode == TriggerBehaviorMode.SMART_ALARM) {
                preventManualDismiss = config.smartConfig?.preventManualDismiss == true
            }
        }
    }

    DisposableEffect(Unit) {
        val timer = java.util.Timer()
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                currentTime = sdf.format(Date())
            }
        }, 0, 1000)
        onDispose { timer.cancel() }
    }
    
    fun sendAction(action: String) {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            this.action = action
            alarmJson?.let { putExtra("alarm", it) }
        }
        context.sendBroadcast(intent)
        onFinish()
    }

    AppBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = currentTime, style = MaterialTheme.typography.displayLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(text = alarm?.label ?: "Alarm", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(32.dp))
            
            // Knoppen afhankelijk van mode
            val buttonColor = Color(SettingsManager.getButtonColor(context))
            val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
            
            val iconBtnSize = 52.dp
            val iconGlyphSize = 28.dp
            val iconColors = IconButtonDefaults.filledIconButtonColors(
                containerColor = buttonColor,
                contentColor = buttonTextColor
            )

            @Composable
            fun RingSnoozeIconButton() {
                FilledIconButton(
                    onClick = { sendAction(AlarmActionReceiver.ACTION_SNOOZE) },
                    modifier = Modifier.size(iconBtnSize),
                    colors = iconColors
                ) {
                    Icon(
                        imageVector = Icons.Filled.Snooze,
                        contentDescription = context.getString(R.string.alarm_action_snooze),
                        modifier = Modifier.size(iconGlyphSize)
                    )
                }
            }

            @Composable
            fun RingDismissIconButton(useDismissAlarmLabel: Boolean) {
                FilledIconButton(
                    onClick = { sendAction(AlarmActionReceiver.ACTION_DISMISS) },
                    modifier = Modifier.size(iconBtnSize),
                    colors = iconColors
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = context.getString(
                            if (useDismissAlarmLabel) R.string.alarm_action_dismiss_alarm
                            else R.string.alarm_action_dismiss
                        ),
                        modifier = Modifier.size(iconGlyphSize)
                    )
                }
            }

            when (triggerMode) {
                TriggerBehaviorMode.ONE_TIME -> {
                    // ONE_TIME: alleen sluiten, GEEN snooze
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RingDismissIconButton(useDismissAlarmLabel = true)
                    }
                }
                TriggerBehaviorMode.SMART_ALARM -> {
                    if (!canSnooze) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RingDismissIconButton(useDismissAlarmLabel = false)
                        }
                    } else if (preventManualDismiss) {
                        // SMART_ALARM + preventManualDismiss: alleen Snooze, GEEN sluiten
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RingSnoozeIconButton()
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Alarm kan niet worden uitgezet terwijl conditie actief is",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RingSnoozeIconButton()
                            RingDismissIconButton(useDismissAlarmLabel = false)
                        }
                    }
                }
                else -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canSnooze) {
                            RingSnoozeIconButton()
                        }
                        RingDismissIconButton(useDismissAlarmLabel = false)
                    }
                }
            }
        }
    }
}
