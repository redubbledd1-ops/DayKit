package com.dd.daykit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.StandardActionButtons
import com.dd.daykit.ui.getHorizontalAlignment
import com.dd.daykit.ui.getHorizontalArrangement
import kotlin.math.abs

class BackgroundSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                BackgroundSettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun BackgroundSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Force recomposition on language change
    val currentLanguage by LanguageManager.currentLanguage

    var backgroundType by remember { mutableStateOf(SettingsManager.getBackgroundType(context)) }
    var tempBackgroundColor by remember { mutableStateOf(Color(SettingsManager.getBackgroundColor(context))) }
    var tempBackgroundImageUri by remember { mutableStateOf(SettingsManager.getBackgroundImageUri(context)) }
    var tempBackgroundGifUri by remember { mutableStateOf(SettingsManager.getBackgroundGifUri(context)) }
    var showColorPicker by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            tempBackgroundImageUri = it.toString()
            backgroundType = "image"
        }
    }

    val gifPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            tempBackgroundGifUri = it.toString()
            backgroundType = "gif"
        }
    }

    val onSave = {
        // First save ALL settings
        SettingsManager.saveBackgroundType(context, backgroundType)
        when (backgroundType) {
            "color" -> SettingsManager.saveBackgroundColor(context, tempBackgroundColor.toArgb())
            "image" -> tempBackgroundImageUri?.let { SettingsManager.saveBackgroundImageUri(context, it) }
            "gif" -> tempBackgroundGifUri?.let { SettingsManager.saveBackgroundGifUri(context, it) }
        }
        // Restart app to apply background changes
        SettingsManager.restartApp(context)
    }

    val textColor = Color(SettingsManager.getTextColor(context))
    val horizontalAlignment = getHorizontalAlignment(SettingsManager.getTextAlignment(context))

    AppBackground(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dragThreshold = 50f
                    if (abs(dragAmount.y) > abs(dragAmount.x)) {
                        if (dragAmount.y > dragThreshold || dragAmount.y < -dragThreshold) {
                            onBack()
                        }
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
                horizontalAlignment = horizontalAlignment
            ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = horizontalAlignment
            ) {
                item {
                    Text(
                        LanguageManager.getString("background_settings_title"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = textColor
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        LanguageManager.getString("background_choose_type"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showColorPicker = true },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(LanguageManager.getString("color"), style = MaterialTheme.typography.titleMedium, color = textColor)
                                Text(
                                    LanguageManager.getString("color_desc"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(tempBackgroundColor, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(LanguageManager.getString("image"), style = MaterialTheme.typography.titleMedium, color = textColor)
                            Text(
                                LanguageManager.getString("image_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { gifPickerLauncher.launch(arrayOf("image/gif")) },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(LanguageManager.getString("gif"), style = MaterialTheme.typography.titleMedium, color = textColor)
                            Text(
                                LanguageManager.getString("gif_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            StandardActionButtons(
                onCancel = onBack,
                onSave = onSave
            )
            }
        }
    }

    if (showColorPicker) {
        BackgroundColorPickerDialog(
            initialColor = tempBackgroundColor,
            onColorSelected = { color ->
                tempBackgroundColor = color
                backgroundType = "color"
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun BackgroundColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var hexInput by remember { mutableStateOf(colorToHex(initialColor)) }

    val buttonColor = Color(SettingsManager.getButtonColor(LocalContext.current))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(LocalContext.current))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LanguageManager.getString("background_color_title")) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ColorPicker(
                    initialColor = selectedColor,
                    onColorChanged = { color ->
                        selectedColor = color
                        hexInput = colorToHex(color)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { newHex ->
                        hexInput = newHex
                        try {
                            val cleanHex = newHex.removePrefix("#")
                            if (cleanHex.length == 6) {
                                val colorInt = cleanHex.toInt(16)
                                selectedColor = Color(0xFF000000.toInt() or colorInt)
                            }
                        } catch (e: Exception) {
                            // Invalid hex, ignore
                        }
                    },
                    label = { Text(LanguageManager.getString("hex_code")) },
                    placeholder = { Text("#000000") },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(selectedColor, RoundedCornerShape(4.dp))
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(selectedColor) },
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                border = null
            ) {
                Text(LanguageManager.getString("ok"))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                border = null
            ) {
                Text(LanguageManager.getString("cancel"))
            }
        }
    )
}

private fun colorToHex(color: Color): String {
    return "#${Integer.toHexString(color.toArgb()).substring(2).uppercase()}"
}
