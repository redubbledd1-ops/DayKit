package com.dd.daykit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.SwipeIndicators
import java.util.Locale

class GlobalSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                GlobalSettingsScreen { finish() }
            }
        }
    }
}

@Composable
fun GlobalSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val textColor = Color(SettingsManager.getTextColor(ctx))
    val buttonColor = Color(SettingsManager.getButtonColor(ctx))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(ctx))
    val currentLanguage by LanguageManager.currentLanguage
    val swipeEnabled = SettingsManager.getSwipeEnabled(ctx)

    var showResetConfirmation by remember { mutableStateOf(false) }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(LanguageManager.getString("reset_default")) },
            text = { Text(LanguageManager.getString("confirm_delete_msg")) }, 
            confirmButton = {
                Button(
                    onClick = {
                        SettingsManager.clearAll(ctx)
                        // Restart app or finish activity to apply changes properly
                        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        Runtime.getRuntime().exit(0) // Brute force restart to clear memory states
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text(LanguageManager.getString("delete_all"))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showResetConfirmation = false },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
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
                    onDrag = { change, dragAmount ->
                        if (!swipeEnabled) return@detectDragGestures
                        if (!isDragConsumed) {
                            val dragThreshold = 50f
                            // Swipe from Right to Left to go back (finger moves left)
                            val horizontalDrag = change.position.x - dragStart.x
                            // Swipe Left (Go Back)
                            if (horizontalDrag > dragThreshold) { 
                                onBack()
                                isDragConsumed = true
                            }
                        }
                        if (isDragConsumed) change.consume()
                    }
                )
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SwipeIndicators(
                isVertical = isVertical,
                showLeft = true,
                onSwipeLeft = { onBack() }
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    LanguageManager.getString("settings"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor
                )
                Spacer(Modifier.height(32.dp))
                
                // Design Settings (Moved to top)
                Button(
                    onClick = {
                        ctx.startActivity(Intent(ctx, DesignSettingsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("design"))
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Navigation Settings
                Button(
                    onClick = {
                        ctx.startActivity(Intent(ctx, NavigationSettingsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("navigation"))
                }
                
                Spacer(Modifier.height(16.dp))

                // Language Selector
                Text(LanguageManager.getString("language"), color = textColor, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { expanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(currentLanguage.displayName)
                            Icon(Icons.Default.ArrowDropDown, "Select")
                        }
                    }
                    DropdownMenu(
                        expanded = expanded, 
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color.White) 
                    ) {
                        // Sort languages alphabetically
                        Language.entries.toList().sortedBy { it.displayName }.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.displayName, color = Color.Black) },
                                onClick = {
                                    LanguageManager.setLanguage(ctx, lang)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Shortcuts Settings - placed after Taal
                Button(
                    onClick = {
                        ctx.startActivity(Intent(ctx, ShortcutsActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("shortcuts"))
                }

                Spacer(Modifier.height(48.dp))

                // Reset All Button
                Button(
                    onClick = { showResetConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f),
                        contentColor = Color.White
                    )
                ) {
                    Text("Reset All Data") // Using hardcoded English/fallback or add to LanguageManager if critical
                }
            }
        }
    }
}
