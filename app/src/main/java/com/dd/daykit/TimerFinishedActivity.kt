package com.dd.daykit

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Global fullscreen overlay shown whenever the timer alarm sound is active.
 *
 * Requirements (per UX spec):
 *  - Pure black background, big "Timer afgelopen!" title, two buttons [Herstart] [Stop].
 *  - Appears over every page (including Stopwatch / Witgoed / etc.) and on app re-open
 *    while the alarm is still active. The cross-page launch is driven by
 *    [AgendaWekkerApplication]'s `ActivityLifecycleCallbacks`, plus the high-priority
 *    full-screen-intent notification posted by [TimeEngineService] /
 *    [TimerFinishedAlarmUi].
 *  - Cannot be dismissed by tapping outside, by back-button, or by navigation —
 *    only the [Herstart] / [Stop] buttons may close it.
 */
class TimerFinishedActivity : ComponentActivity() {

    companion object {
        private const val TAG = "TimerFinishedActivity"
    }

    /** null = primaire timer. Anders id + index van de afgelopen 2e/3e ([ExtraTimerManager]) timer. */
    private val currentSlotId = mutableStateOf<String?>(null)
    private val currentSlotIndex = mutableStateOf<Int?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentSlotId.value = intent.getStringExtra(TimerFinishedAlarmUi.EXTRA_SLOT_ID)
        currentSlotIndex.value = if (currentSlotId.value != null) {
            intent.getIntExtra(TimerFinishedAlarmUi.EXTRA_SLOT_INDEX, 0)
        } else null
        Log.i(TAG, "onNewIntent slotId=${currentSlotId.value} slotIndex=${currentSlotIndex.value}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSlotId.value = intent.getStringExtra(TimerFinishedAlarmUi.EXTRA_SLOT_ID)
        currentSlotIndex.value = if (currentSlotId.value != null) {
            intent.getIntExtra(TimerFinishedAlarmUi.EXTRA_SLOT_INDEX, 0)
        } else null
        AlarmRingingDebug.i(
            "TimerFinishedActivity.onCreate wakeMobile=${WakeMobilePolicy.isWakeMobileEnabled(this)} " +
                "savedInstanceState=${savedInstanceState != null}"
        )
        AlarmRingingDebug.logFsiPermission(this, "TimerFinishedActivity.onCreate")
        FullScreenIntentPermission.logState(this, TAG)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Matches the pure-black [Box] surface in this Activity (not the global theme background).
        SettingsManager.applyAlarmOverlaySystemBarColors(
            window,
            android.graphics.Color.BLACK,
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
            AlarmRingingDebug.i("wakeFlow phase=TimerFinishedActivity.onCreate enabled=true lockscreenWake=true")
        } else {
            AlarmRingingDebug.i("wakeFlow phase=TimerFinishedActivity.onCreate enabled=false lockscreenWake=false")
        }

        LanguageManager.init(this)
        GlobalTimerManager.init(this)
        ExtraTimerManager.init(this)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val app = context.applicationContext

                val timerState by GlobalTimerManager.timerState
                val slotId by currentSlotId
                val slotIndex by currentSlotIndex
                // ExtraTimerManager.timers is a Compose SnapshotStateList — reading .find on it
                // here re-runs this lookup (and the slot's own .state reads below) on every
                // recomposition the list/slot triggers, exactly like the primary's timerState.
                val extraSlot = slotId?.let { id -> ExtraTimerManager.timers.find { it.id == id } }
                val extraLabel = if (slotId != null) {
                    extraSlot?.name?.ifBlank { null } ?: "Timer ${(slotIndex ?: 0) + 2}"
                } else {
                    GlobalTimerManager.timerName.value.ifBlank { null }
                }

                // Defensive auto-finish: only if the alarm is no longer playing AND the timer
                // is no longer in FINISHED state. Both buttons clear isAlarmPlaying first, so
                // closing the overlay still happens promptly via finish() in the click handlers.
                if (slotId == null) {
                    LaunchedEffect(timerState) {
                        if (
                            timerState != GlobalTimerManager.TimerState.FINISHED &&
                            !GlobalTimerManager.isAlarmPlaying &&
                            !this@TimerFinishedActivity.isFinishing
                        ) {
                            finish()
                        }
                    }
                } else {
                    LaunchedEffect(slotId, extraSlot?.state) {
                        if (
                            (extraSlot == null || extraSlot.state != GlobalTimerManager.TimerState.FINISHED) &&
                            !this@TimerFinishedActivity.isFinishing
                        ) {
                            finish()
                        }
                    }
                }

                // Block back-button entirely — overlay must persist while alarm is active.
                BackHandler(enabled = true) { /* swallow */ }

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
                            text = LanguageManager.getString("timer_finished_title") +
                                (extraLabel?.let { " · $it" } ?: ""),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Normal,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OverlayIconButton(
                                icon = Icons.Filled.PlayArrow,
                                contentDescription = LanguageManager.getString("timer_alarm_restart"),
                                onClick = {
                                    if (slotId != null) {
                                        extraSlot?.let { ExtraTimerManager.restartTimerAfterAlarm(app, it) }
                                        TimerFinishedAlarmUi.cancel(app, slotIndex)
                                    } else {
                                        GlobalTimerManager.restartTimerAfterAlarm(app)
                                        TimerFinishedAlarmUi.cancel(app)
                                    }
                                    finish()
                                }
                            )
                            OverlayIconButton(
                                icon = Icons.Filled.Close,
                                contentDescription = LanguageManager.getString("timer_alarm_close"),
                                onClick = {
                                    if (slotId != null) {
                                        extraSlot?.let { ExtraTimerManager.stopAlarmOnlyForSlot(app, it) }
                                        TimerFinishedAlarmUi.cancel(app, slotIndex)
                                    } else {
                                        GlobalTimerManager.stopAlarmOnly(app)
                                        TimerFinishedAlarmUi.cancel(app)
                                    }
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Defeat any system gesture / config-change shortcut that might dismiss the overlay
    // while the alarm is still ringing.
    override fun onResume() {
        super.onResume()
        AlarmRingingDebug.i(
            "TimerFinishedActivity.onResume wakeMobile=${WakeMobilePolicy.isWakeMobileEnabled(this)} " +
                "isAlarmPlaying=${GlobalTimerManager.isAlarmPlaying} " +
                "timerState=${GlobalTimerManager.timerState.value}"
        )
    }

    override fun onUserLeaveHint() {
        // Do nothing — we deliberately don't react to home-button leaves; the relaunch
        // hook in AgendaWekkerApplication brings the overlay back on next resume.
        super.onUserLeaveHint()
    }
}

@androidx.compose.runtime.Composable
private fun OverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp)
        )
    }
}
