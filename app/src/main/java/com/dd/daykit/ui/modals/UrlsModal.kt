package com.dd.daykit.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * UrlsModal - Home Assistant URLs configuration modal
 * 
 * @param visible Whether the modal is visible
 * @param initialUrls Initially configured URLs
 * @param onDismiss Callback when modal is dismissed
 * @param onSave Callback with updated URLs when save is clicked
 * @param textColor Text color
 * @param buttonColor Button color
 * @param buttonTextColor Button text color
 * @param containerColor Modal background color
 */
@Composable
fun UrlsModal(
    visible: Boolean,
    initialUrls: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    // Key on both visible AND initialUrls to force reset when modal reopens
    var urls by remember(visible, initialUrls) { mutableStateOf(initialUrls.toMutableList()) }
    var showConfirmDialog by remember(visible) { mutableStateOf(false) }
    var validationErrors by remember(visible) { mutableStateOf<Map<Int, String>>(emptyMap()) }

    val hasChanges = urls != initialUrls
    
    val validateUrl = { url: String ->
        when {
            url.isBlank() -> "URL mag niet leeg zijn"
            !url.startsWith("http://") && !url.startsWith("https://") -> "URL moet beginnen met http:// of https://"
            else -> null
        }
    }
    
    val validateAllUrls = {
        val errors = mutableMapOf<Int, String>()
        urls.forEachIndexed { index, url ->
            validateUrl(url)?.let { error ->
                errors[index] = error
            }
        }
        validationErrors = errors
        errors.isEmpty()
    }
    
    val handleDismiss = {
        if (hasChanges) {
            showConfirmDialog = true
        } else {
            onDismiss()
        }
    }
    
    val handleSave = {
        if (validateAllUrls()) {
            onSave(urls.filter { it.isNotBlank() })
        }
    }

    if (visible) {
        Dialog(
            onDismissRequest = handleDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            // Center modal in screen
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.95f, animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150))
                ) {
                    Card(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(0.95f)
                        .heightIn(max = 600.dp)
                        .semantics { paneTitle = "Home Assist URLs" },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = textColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // Header with title and close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.dd.daykit.LanguageManager.getString("ha_urls"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = handleDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Sluiten",
                                    tint = textColor
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // URL list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            itemsIndexed(urls) { index, url ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = url,
                                            onValueChange = { newValue ->
                                                urls = urls.toMutableList().apply { this[index] = newValue }
                                                // Clear error for this field when user types
                                                validationErrors = validationErrors.filterKeys { it != index }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("https://homeassistant.local:8123", color = textColor.copy(alpha = 0.5f)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = if (validationErrors.containsKey(index)) Color.Red else buttonColor,
                                                unfocusedBorderColor = if (validationErrors.containsKey(index)) Color.Red else textColor.copy(alpha = 0.5f),
                                                focusedTextColor = textColor,
                                                unfocusedTextColor = textColor,
                                                cursorColor = buttonColor
                                            ),
                                            isError = validationErrors.containsKey(index)
                                        )
                                        if (validationErrors.containsKey(index)) {
                                            Text(
                                                text = validationErrors[index]!!,
                                                color = Color.Red,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            urls = urls.toMutableList().apply { removeAt(index) }
                                            validationErrors = validationErrors.filterKeys { it != index }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Verwijderen",
                                            tint = buttonColor
                                        )
                                    }
                                }
                            }
                            
                            item {
                                Button(
                                    onClick = { urls = urls.toMutableList().apply { add("") } },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = buttonColor,
                                        contentColor = buttonTextColor
                                    )
                                ) {
                                    Text("+")
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // Footer buttons - centered with matching styling
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor
                                )
                            ) {
                                Text(com.dd.daykit.LanguageManager.getString("cancel"))
                            }
                            
                            Spacer(Modifier.width(12.dp))
                            
                            Button(
                                onClick = handleSave,
                                enabled = urls.isNotEmpty() && urls.any { it.isNotBlank() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor
                                )
                            ) {
                                Text(com.dd.daykit.LanguageManager.getString("save"))
                            }
                        }
                    }
                }
            }
            }
        }
    }
    
    // Unsaved changes confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(com.dd.daykit.LanguageManager.getString("unsaved_changes"), color = textColor) },
            text = { 
                Text(
                    com.dd.daykit.LanguageManager.getString("unsaved_changes_message"),
                    color = textColor.copy(alpha = 0.7f)
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text(com.dd.daykit.LanguageManager.getString("cancel"))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textColor
                    )
                ) {
                    Text(com.dd.daykit.LanguageManager.getString("no_keep_editing"))
                }
            },
            containerColor = containerColor
        )
    }
}
