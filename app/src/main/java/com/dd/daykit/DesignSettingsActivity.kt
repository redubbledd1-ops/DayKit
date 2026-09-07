package com.dd.daykit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.SettingsScreenTemplate
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.swipeUpBackGesture

class DesignSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                DesignSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun DesignSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))

    // Force recomposition on language change
    val currentLanguage by LanguageManager.currentLanguage

    SideEffect {
        (context as? ComponentActivity)?.let {
            SettingsManager.applySystemBarColors(it.window, context)
        }
    }

    var textColor by remember { mutableStateOf(Color(SettingsManager.getTextColor(context))) }
    var buttonColor by remember { mutableStateOf(Color(SettingsManager.getButtonColor(context))) }
    var buttonTextColor by remember { mutableStateOf(Color(SettingsManager.getButtonTextColor(context))) }

    var selectedColorType by remember { mutableStateOf<ColorType?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

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
                lazyListState = lazyListState,
                onBack = navigateBackToSettings,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            SettingsScreenTemplate(
                title = LanguageManager.getString("design"),
                onBack = onBack,
                showBackArrow = false,
                showTitle = false,
                onSave = {
                    // First save ALL settings (commit synchronously)
                    SettingsManager.saveTextColor(context, textColor.toArgb())
                    SettingsManager.saveButtonColor(context, buttonColor.toArgb())
                    SettingsManager.saveButtonTextColor(context, buttonTextColor.toArgb())
                    SettingsManager.saveTextAlignment(context, "CENTER")

                    // Restart app to apply design changes
                    SettingsManager.restartApp(context)
                },
                lazyListState = lazyListState,
                modifier = Modifier.weight(1f),
            ) {
            item {
                Text(
                    text = LanguageManager.getString("design"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                ColorSettingItem(LanguageManager.getString("text_color"), textColor, textColor) {
                    selectedColorType = ColorType.TEXT
                    showColorPicker = true
                }
            }

            item {
                ColorSettingItem(LanguageManager.getString("button_color"), buttonColor, textColor) {
                    selectedColorType = ColorType.BUTTON
                    showColorPicker = true
                }
            }

            item {
                ColorSettingItem(LanguageManager.getString("button_text_color"), buttonTextColor, textColor) {
                    selectedColorType = ColorType.BUTTON_TEXT
                    showColorPicker = true
                }
            }

            item {
                Button(
                    onClick = { context.startActivity(Intent(context, BackgroundSettingsActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("background"))
                }
            }

            item {
                Button(
                    onClick = { context.startActivity(Intent(context, NavigationSettingsActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("navigation"))
                }
            }

            item {
                Button(
                    onClick = {
                        textColor = Color(0xFFFFFFFF)
                        buttonColor = Color(0xFFFFFFFF)
                        buttonTextColor = Color(0xFF000000)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) {
                    Text(LanguageManager.getString("reset_default"))
                }
            }
            }
        }

        SwipeIndicators(
            isVertical = isVertical,
            showUp = true,
            upIndicatorPointsDown = true,
            upTouchSize = 120.dp,
            onSwipeUp = { navigateBackToSettings() },
        )
    }

    if (showColorPicker && selectedColorType != null) {
        ColorPickerDialog(
            title = when (selectedColorType) {
                ColorType.TEXT -> LanguageManager.getString("text_color")
                ColorType.BUTTON -> LanguageManager.getString("button_color")
                ColorType.BUTTON_TEXT -> LanguageManager.getString("button_text_color")
                else -> LanguageManager.getString("pick_color")
            },
            initialColor = when (selectedColorType) {
                ColorType.TEXT -> textColor
                ColorType.BUTTON -> buttonColor
                ColorType.BUTTON_TEXT -> buttonTextColor
                else -> Color.Black
            },
            onColorSelected = { color ->
                when (selectedColorType) {
                    ColorType.TEXT -> textColor = color
                    ColorType.BUTTON -> buttonColor = color
                    ColorType.BUTTON_TEXT -> buttonTextColor = color
                    else -> {}
                }
                showColorPicker = false
                selectedColorType = null
            },
            onDismiss = {
                showColorPicker = false
                selectedColorType = null
            }
        )
    }
}

@Composable
private fun ColorSettingItem(label: String, color: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${Integer.toHexString(color.toArgb()).substring(2).uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.7f)
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.size(50.dp)
            ) {}
        }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String, initialColor: Color, onColorSelected: (Color) -> Unit, onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var hexInput by remember { mutableStateOf(colorToHex(initialColor)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                ColorPicker(selectedColor, { color ->
                    selectedColor = color
                    hexInput = colorToHex(color)
                }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { newHex ->
                        hexInput = newHex
                        try {
                            if (newHex.removePrefix("#").length == 6) {
                                selectedColor = Color(android.graphics.Color.parseColor(newHex))
                            }
                        } catch (e: Exception) {}
                    },
                    label = { Text(LanguageManager.getString("hex_code")) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.fillMaxWidth().height(50.dp).background(selectedColor, RoundedCornerShape(4.dp)))
            }
        },
        confirmButton = { Button(onClick = { onColorSelected(selectedColor) }) { Text(LanguageManager.getString("ok")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LanguageManager.getString("cancel")) } }
    )
}

private enum class ColorType { TEXT, BUTTON, BUTTON_TEXT }

private fun colorToHex(color: Color): String = String.format("#%06X", (0xFFFFFF and color.toArgb()))
