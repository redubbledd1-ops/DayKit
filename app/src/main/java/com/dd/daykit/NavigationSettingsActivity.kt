package com.dd.daykit

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.swipeUpBackGesture

class NavigationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                NavigationSettingsScreen { finish() }
            }
        }
    }
}

@Composable
fun NavigationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))

    var showNavButtons by remember { mutableStateOf(SettingsManager.getShowNavButtons(context)) }
    var swipeEnabled by remember { mutableStateOf(SettingsManager.getSwipeEnabled(context)) }
    var arrowNavigationEnabled by remember {
        mutableStateOf(SettingsManager.getArrowMode(context) != "Verborgen")
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.dd.daykit.DESIGN_UPDATED") {
                    showNavButtons = SettingsManager.getShowNavButtons(context)
                }
            }
        }
        val filter = IntentFilter("com.dd.daykit.DESIGN_UPDATED")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
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
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Text(
                text = LanguageManager.getString("navigation"),
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text(
                LanguageManager.getString("swipe_navigation_label"),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                LanguageManager.getString("swipe_navigation_description"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = { swipeEnabled = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (swipeEnabled) buttonColor else buttonColor.copy(alpha = 0.5f),
                        contentColor = buttonTextColor
                    )
                ) { Text(LanguageManager.getString("on")) }

                Button(
                    onClick = {
                        swipeEnabled = false
                        if (!arrowNavigationEnabled) {
                            arrowNavigationEnabled = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!swipeEnabled) buttonColor else buttonColor.copy(alpha = 0.5f),
                        contentColor = buttonTextColor
                    )
                ) { Text(LanguageManager.getString("off")) }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                LanguageManager.getString("arrow_navigation_label"),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                LanguageManager.getString("arrow_navigation_description"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = { arrowNavigationEnabled = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (arrowNavigationEnabled) buttonColor else buttonColor.copy(alpha = 0.5f),
                        contentColor = buttonTextColor
                    )
                ) { Text(LanguageManager.getString("on")) }

                Button(
                    onClick = {
                        arrowNavigationEnabled = false
                        if (!swipeEnabled) {
                            swipeEnabled = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!arrowNavigationEnabled) buttonColor else buttonColor.copy(alpha = 0.5f),
                        contentColor = buttonTextColor
                    )
                ) { Text(LanguageManager.getString("off")) }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                LanguageManager.getString("navigation_bar_label"),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                LanguageManager.getString("navigation_bar_description"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = { showNavButtons = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showNavButtons) buttonColor else buttonColor.copy(alpha = 0.5f),
                        contentColor = buttonTextColor
                    )
                ) { Text(LanguageManager.getString("on")) }
                
                Button(
                    onClick = { showNavButtons = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!showNavButtons) buttonColor else buttonColor.copy(alpha = 0.5f),
                        contentColor = buttonTextColor
                    )
                ) { Text(LanguageManager.getString("off")) }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Annuleren/Opslaan buttons CENTRAAL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("cancel"))
                }
                Button(
                    onClick = {
                        // First save settings
                        SettingsManager.saveShowNavButtons(context, showNavButtons)
                        SettingsManager.saveSwipeEnabled(context, swipeEnabled)
                        SettingsManager.saveArrowMode(
                            context,
                            if (arrowNavigationEnabled) "Indicatie" else "Verborgen"
                        )
                        // Restart app to apply navigation changes
                        SettingsManager.restartApp(context)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("save"))
                }
            }
            }

            // NavigationBar at the bottom
            if (showNavButtons) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
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
