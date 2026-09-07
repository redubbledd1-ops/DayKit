package com.dd.daykit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.swipeUpBackGesture

class ShortcutsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                ShortcutsScreen { finish() }
            }
        }
    }
}

data class ShortcutItem(
    val id: String,
    val labelKey: String,
    val screenId: String,
    val targetClass: Class<*>
)

/** Same resource as manifest `android:icon` / `android:roundIcon` (@drawable/clockgood). */
private fun appLauncherShortcutIcon(context: Context): IconCompat =
    IconCompat.createWithResource(context, R.drawable.clockgood)

@Composable
fun ShortcutsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    val scrollState = rememberScrollState()
    
    val enabledScreens = SettingsManager.getEnabledScreens(context)

    LaunchedEffect(Unit) {
        SettingsManager.saveLastVisitedSubSettingsPage(context, "SHORTCUTS")
    }

    // Define all shortcuts with their screen IDs - use screen_* keys for labels
    val allShortcuts = listOf(
        ShortcutItem("settings", "screen_settings", "SETTINGS", GlobalSettingsActivity::class.java),
        ShortcutItem("agenda_alarm", "screen_agenda_alarm", "AGENDA_ALARM", MainActivity::class.java),
        ShortcutItem("stopwatch", "screen_stopwatch", "STOPWATCH", NewFeatureActivity::class.java),
        ShortcutItem("timer", "screen_timer", "TIMER", TimerActivity::class.java),
        ShortcutItem("appliances", "screen_appliance_calc", "APPLIANCE_CALCULATOR", ApplianceCalculatorActivity::class.java),
        ShortcutItem("calculator", "screen_calculator", "CALCULATOR", CalculatorActivity::class.java),
        ShortcutItem("weather", "screen_weather", "WEATHER", WeatherActivity::class.java)
    )
    
    // Home Assist is only shown if AGENDA_ALARM is enabled
    val showHomeAssist = enabledScreens.contains("AGENDA_ALARM")
    
    fun createShortcut(item: ShortcutItem) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val intent = Intent(context, item.targetClass).apply {
                action = Intent.ACTION_VIEW
            }
            
            val shortcutInfo = ShortcutInfoCompat.Builder(context, item.id)
                .setShortLabel(LanguageManager.getString(item.labelKey))
                .setLongLabel(LanguageManager.getString(item.labelKey))
                .setIcon(appLauncherShortcutIcon(context))
                .setIntent(intent)
                .build()
            
            val success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
            if (success) {
                Toast.makeText(
                    context, 
                    "${LanguageManager.getString(item.labelKey)} ${LanguageManager.getString("shortcut_added")}", 
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context, 
                    LanguageManager.getString("shortcut_failed"), 
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                context, 
                LanguageManager.getString("shortcut_not_supported"), 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    fun createHomeAssistShortcut() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val intent = Intent(context, HaSettingsActivity::class.java).apply {
                action = Intent.ACTION_VIEW
            }
            
            val shortcutInfo = ShortcutInfoCompat.Builder(context, "home_assist")
                .setShortLabel(LanguageManager.getString("home_assist"))
                .setLongLabel(LanguageManager.getString("home_assist"))
                .setIcon(appLauncherShortcutIcon(context))
                .setIntent(intent)
                .build()
            
            val success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
            if (success) {
                Toast.makeText(
                    context, 
                    "${LanguageManager.getString("home_assist")} ${LanguageManager.getString("shortcut_added")}", 
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context, 
                    LanguageManager.getString("shortcut_failed"), 
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                context, 
                LanguageManager.getString("shortcut_not_supported"), 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    AppBackground(
        modifier = Modifier
            .fillMaxSize()
            .swipeUpBackGesture(
                enabled = swipeEnabled,
                scrollState = scrollState,
                onBack = onBack,
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(32.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    LanguageManager.getString("shortcuts"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = textColor
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    LanguageManager.getString("shortcuts_description"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.7f)
                )
                
                Spacer(Modifier.height(32.dp))
                
                allShortcuts.forEach { shortcut ->
                    val isEnabled = enabledScreens.contains(shortcut.screenId)
                    Button(
                        onClick = { createShortcut(shortcut) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEnabled) buttonColor else buttonColor.copy(alpha = 0.3f),
                            contentColor = if (isEnabled) buttonTextColor else buttonTextColor.copy(alpha = 0.5f),
                            disabledContainerColor = buttonColor.copy(alpha = 0.3f),
                            disabledContentColor = buttonTextColor.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(LanguageManager.getString(shortcut.labelKey))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                
                // Home Assist shortcut - only shown if AGENDA_ALARM is enabled
                if (showHomeAssist) {
                    Button(
                        onClick = { createHomeAssistShortcut() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) {
                        Text(LanguageManager.getString("home_assist"))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                
                Spacer(Modifier.height( 24.dp))
                
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("back"))
                }
            }

            SwipeIndicators(
                isVertical = isVertical,
                showUp = true,
                upIndicatorPointsDown = true,
                upTouchSize = 160.dp,
                onSwipeUp = onBack
            )
        }
    }
}
