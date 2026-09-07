package com.dd.daykit

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Lightweight lockscreen activity for the AgendaAlarm popup.
 *
 * Shown ONLY during the "Popup laatste X" window via the full-screen intent on
 * [AgendaAlarmPopupForegroundService]'s notification. Auto-finishes when:
 *  - The alarm epoch arrives (popup window ends).
 *  - The user taps dismiss.
 *  - The popup service is no longer running.
 *
 * Uses the same lockscreen pattern as [TimerFinishedActivity]:
 *  [setShowWhenLocked] + [setTurnScreenOn] + window flags.
 */
class AgendaAlarmPopupLockscreenActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AgendaPopupLockscreen"
        const val EXTRA_ALARM_JSON = "alarm_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setShowWhenLocked(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        LanguageManager.init(this)

        val alarmJson = intent?.getStringExtra(EXTRA_ALARM_JSON)
        val alarm = alarmJson?.let {
            runCatching { Json.decodeFromString<AlarmItem>(it) }.getOrNull()
        }

        if (alarm == null || alarm.epochMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "No valid alarm or alarm already passed — finishing")
            finish()
            return
        }

        Log.i(TAG, "Showing lockscreen popup for alarm '${alarm.label}' at ${alarm.epochMillis}")

        setContent {
            MaterialTheme {
                var countdownText by remember { mutableStateOf("") }
                val eventTitle = remember { alarm.label.trim().takeIf { it.isNotEmpty() } }

                // Ticker: update countdown every second, auto-finish when alarm epoch passes
                LaunchedEffect(alarm.epochMillis) {
                    while (true) {
                        val now = System.currentTimeMillis()
                        if (alarm.epochMillis <= now) {
                            Log.i(TAG, "Alarm time reached — finishing lockscreen popup")
                            finish()
                            break
                        }
                        countdownText = AgendaAlarmNotificationCopy.compactCountdownTitle(
                            this@AgendaAlarmPopupLockscreenActivity,
                            alarm.epochMillis
                        )
                        NotificationPopupDebugLog.popup(
                            source = "AgendaAlarmPopupLockscreenActivity.ticker",
                            nextAlarmEpoch = alarm.epochMillis,
                            displayedTime = countdownText,
                            extra = "frozenAlarmId=${alarm.id}",
                        )
                        delay(1000L)
                    }
                }

                val buttonColor = Color(SettingsManager.getButtonColor(this@AgendaAlarmPopupLockscreenActivity))
                val buttonTextColor = Color(SettingsManager.getButtonTextColor(this@AgendaAlarmPopupLockscreenActivity))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = countdownText,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Light,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )

                        if (eventTitle != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = eventTitle,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        FilledIconButton(
                            onClick = {
                                Log.i(TAG, "Dismiss tapped — finishing lockscreen popup")
                                finish()
                            },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = buttonColor,
                                contentColor = buttonTextColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
