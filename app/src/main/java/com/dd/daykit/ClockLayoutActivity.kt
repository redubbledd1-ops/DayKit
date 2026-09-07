package com.dd.daykit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.SettingsScreenTemplate
import com.dd.daykit.ui.getTextAlign
import kotlin.math.abs

class ClockLayoutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                ClockLayoutScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun ClockLayoutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val currentLanguage by LanguageManager.currentLanguage

    // Original values - saved at screen open for cancel functionality
    val originalClockLayout = remember { SettingsManager.getClockLayout(context) }
    val originalCountdownSecondsMode = remember { SettingsManager.getCountdownSecondsMode(context) }
    val originalShowCurrentTimeSeconds = remember { SettingsManager.getShowCurrentTimeSeconds(context) }

    var textColor by remember { mutableStateOf(Color(SettingsManager.getTextColor(context))) }
    var buttonColor by remember { mutableStateOf(Color(SettingsManager.getButtonColor(context))) }
    var buttonTextColor by remember { mutableStateOf(Color(SettingsManager.getButtonTextColor(context))) }
    var clockLayout by remember { mutableStateOf(originalClockLayout) }
    var countdownSecondsMode by remember { mutableStateOf(originalCountdownSecondsMode) }
    var showCurrentTimeSeconds by remember { mutableStateOf(originalShowCurrentTimeSeconds) }

    val labelTextAlign = getTextAlign(SettingsManager.getTextAlignment(context))

    // Cancel handler - restore original values (no side effects)
    val handleCancel = {
        // Simply go back without saving - original values remain in storage
        onBack()
    }

    SettingsScreenTemplate(
        title = LanguageManager.getString("clock_layout"),
        onBack = handleCancel,
        showBackArrow = false,
        onSave = {
            SettingsManager.saveClockLayoutSettingsSync(
                context,
                clockLayout,
                countdownSecondsMode,
                showCurrentTimeSeconds
            )

            context.sendBroadcast(
                Intent("com.dd.daykit.DESIGN_UPDATED")
                    .setPackage(context.packageName)
            )

            onBack()
        },
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val dragThreshold = 50f
                if (abs(dragAmount.y) > abs(dragAmount.x)) {
                    if (dragAmount.y > dragThreshold || dragAmount.y < -dragThreshold) {
                        onBack()
                    }
                } else if (dragAmount.x > dragThreshold) {
                    onBack()
                }
            }
        }
    ) {
        item {
            LayoutSelector(clockLayout, { clockLayout = it }, buttonColor, buttonTextColor)
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                LanguageManager.getString("seconds_display"),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                textAlign = labelTextAlign,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SecondsSettings(
                countdownSecondsMode, { countdownSecondsMode = it },
                showCurrentTimeSeconds, { showCurrentTimeSeconds = it },
                buttonColor, buttonTextColor, textColor, labelTextAlign,
            )
        }

        item {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    clockLayout = SettingsManager.DEFAULT_CLOCK_LAYOUT
                    countdownSecondsMode = SettingsManager.DEFAULT_COUNTDOWN_SECONDS_MODE
                    showCurrentTimeSeconds = SettingsManager.DEFAULT_SHOW_CURRENT_TIME_SECONDS
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
            ) {
                Text(LanguageManager.getString("reset_default"))
            }
        }
    }
}

@Composable
private fun LayoutSelector(
    selectedLayout: String,
    onLayoutSelected: (String) -> Unit,
    buttonColor: Color,
    buttonTextColor: Color
) {
    val layouts = listOf(
        "COUNTDOWN_BIG_CURRENT_SMALL" to LanguageManager.getString("layout_countdown_big"),
        "CURRENT_BIG_COUNTDOWN_SMALL" to LanguageManager.getString("layout_current_big"),
        "COUNTDOWN_ONLY" to LanguageManager.getString("layout_countdown_only"),
        "CURRENT_TIME_ONLY" to LanguageManager.getString("layout_current_only")
    )

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        layouts.forEach { (layoutKey, layoutLabel) ->
            Button(
                onClick = { onLayoutSelected(layoutKey) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedLayout == layoutKey) buttonColor else buttonColor.copy(alpha = 0.5f),
                    contentColor = buttonTextColor
                )
            ) { Text(layoutLabel) }
        }
    }
}

@Composable
private fun SecondsSettings(
    countdownSecondsMode: String,
    onCountdownSecondsModeChange: (String) -> Unit,
    showCurrentTimeSeconds: Boolean,
    onShowCurrentTimeSecondsChange: (Boolean) -> Unit,
    buttonColor: Color,
    buttonTextColor: Color,
    textColor: Color,
    labelTextAlign: TextAlign,
) {
    val countdownModes = listOf(
        "ALWAYS" to LanguageManager.getString("always"),
        "LAST_5_MIN" to LanguageManager.getString("last_5_min"),
        "NEVER" to LanguageManager.getString("never")
    )

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            countdownModes.forEach { (mode, label) ->
                Button(
                    onClick = { onCountdownSecondsModeChange(mode) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (countdownSecondsMode == mode) buttonColor else buttonColor.copy(alpha = 0.5f),
                        contentColor = buttonTextColor
                    )
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))

        Text(
            LanguageManager.getString("current_time_seconds"),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            textAlign = labelTextAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onShowCurrentTimeSecondsChange(true) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showCurrentTimeSeconds) buttonColor else buttonColor.copy(alpha = 0.5f),
                    contentColor = buttonTextColor
                )
            ) { Text(LanguageManager.getString("on")) }
            
            Button(
                onClick = { onShowCurrentTimeSecondsChange(false) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!showCurrentTimeSeconds) buttonColor else buttonColor.copy(alpha = 0.5f),
                    contentColor = buttonTextColor
                )
            ) { Text(LanguageManager.getString("off")) }
        }
    }
}
