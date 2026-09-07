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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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

/**
 * Lockscreen activity for Stopwatch popup.
 *
 * Same pattern as [TimerFinishedActivity] / [AgendaAlarmPopupLockscreenActivity]:
 *  [setShowWhenLocked] + [setTurnScreenOn] + window flags.
 *
 * Auto-finishes when the stopwatch goes IDLE.
 *
 * Actions per spec:
 * - RUNNING: Pause (left) + Stop (right)
 * - PAUSED: Stop (left) + Resume (right)
 */
class StopwatchPopupLockscreenActivity : ComponentActivity() {

    companion object {
        private const val TAG = "StopwatchLockscreen"
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
        StopwatchStateHolder.init(this)

        Log.i(TAG, "Showing lockscreen stopwatch popup")

        setContent {
            MaterialTheme {
                var elapsedText by remember { mutableStateOf("") }
                var currentState by remember { mutableStateOf(StopwatchStateHolder.stopwatchState.value) }

                // Ticker: update every second, auto-finish when IDLE
                LaunchedEffect(Unit) {
                    while (true) {
                        currentState = StopwatchStateHolder.stopwatchState.value
                        if (currentState == StopwatchState.IDLE) {
                            Log.i(TAG, "Stopwatch IDLE — finishing lockscreen popup")
                            finish()
                            break
                        }
                        elapsedText = TimerDisplayFormat.formatMillisCompact(
                            StopwatchStateHolder.getElapsedTimeMs()
                        )
                        delay(1000L)
                    }
                }

                val buttonColor = Color(SettingsManager.getButtonColor(this@StopwatchPopupLockscreenActivity))
                val buttonTextColor = Color(SettingsManager.getButtonTextColor(this@StopwatchPopupLockscreenActivity))
                val iconColors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = buttonColor,
                    contentColor = buttonTextColor
                )
                val iconBtnSize = 52.dp
                val iconGlyphSize = 28.dp

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
                            text = elapsedText,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Light,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (currentState == StopwatchState.PAUSED) {
                                "${LanguageManager.getString("screen_stopwatch")} (${LanguageManager.getString("sw_pause")})"
                            } else {
                                LanguageManager.getString("screen_stopwatch")
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentState == StopwatchState.RUNNING) {
                                // RUNNING: Pause (left) + Stop (right)
                                FilledIconButton(
                                    onClick = {
                                        StopwatchStateHolder.pause()
                                        StopwatchPopupForegroundService.syncFromStopwatchState(
                                            this@StopwatchPopupLockscreenActivity, "lockscreen_pause"
                                        )
                                    },
                                    modifier = Modifier.size(iconBtnSize),
                                    colors = iconColors
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Pause,
                                        contentDescription = LanguageManager.getString("sw_pause"),
                                        modifier = Modifier.size(iconGlyphSize)
                                    )
                                }
                                FilledIconButton(
                                    onClick = {
                                        StopwatchStateHolder.stopWithoutSave()
                                        StopwatchPopupForegroundService.stopPopup(
                                            this@StopwatchPopupLockscreenActivity, "lockscreen_stop"
                                        )
                                        finish()
                                    },
                                    modifier = Modifier.size(iconBtnSize),
                                    colors = iconColors
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = LanguageManager.getString("sw_stop"),
                                        modifier = Modifier.size(iconGlyphSize)
                                    )
                                }
                            } else {
                                // PAUSED: Stop (left) + Resume (right)
                                FilledIconButton(
                                    onClick = {
                                        StopwatchStateHolder.stopWithoutSave()
                                        StopwatchPopupForegroundService.stopPopup(
                                            this@StopwatchPopupLockscreenActivity, "lockscreen_stop"
                                        )
                                        finish()
                                    },
                                    modifier = Modifier.size(iconBtnSize),
                                    colors = iconColors
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = LanguageManager.getString("sw_stop"),
                                        modifier = Modifier.size(iconGlyphSize)
                                    )
                                }
                                FilledIconButton(
                                    onClick = {
                                        StopwatchStateHolder.resume()
                                        StopwatchPopupForegroundService.syncFromStopwatchState(
                                            this@StopwatchPopupLockscreenActivity, "lockscreen_resume"
                                        )
                                    },
                                    modifier = Modifier.size(iconBtnSize),
                                    colors = iconColors
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = LanguageManager.getString("sw_resume"),
                                        modifier = Modifier.size(iconGlyphSize)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
