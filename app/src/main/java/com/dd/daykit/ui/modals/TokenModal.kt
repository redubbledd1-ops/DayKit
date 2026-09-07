package com.dd.daykit.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * TokenModal - Home Assistant long-lived access token configuration modal
 * 
 * @param visible Whether the modal is visible
 * @param initialToken Initially configured token
 * @param onDismiss Callback when modal is dismissed
 * @param onSave Callback with updated token when save is clicked
 * @param textColor Text color
 * @param buttonColor Button color
 * @param buttonTextColor Button text color
 * @param containerColor Modal background color
 */
@Composable
fun TokenModal(
    visible: Boolean,
    initialToken: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    // Key on visible to force reset when modal reopens after cancel
    var token by remember(visible, initialToken) { mutableStateOf(initialToken) }
    var showConfirmDialog by remember(visible) { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val hasChanges = token != initialToken
    
    val validateToken = {
        when {
            token.isBlank() -> "Token mag niet leeg zijn"
            token.length < 20 -> "Token lijkt te kort (minimaal 20 karakters)"
            else -> null
        }
    }
    
    val handleDismiss = {
        if (hasChanges) {
            showConfirmDialog = true
        } else {
            onDismiss()
        }
    }
    
    val handleSave = {
        val error = validateToken()
        if (error == null) {
            onSave(token)
        } else {
            validationError = error
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
                        .semantics { paneTitle = com.dd.daykit.LanguageManager.getString("ha_token") },
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
                                text = com.dd.daykit.LanguageManager.getString("ha_token"),
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
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            text = com.dd.daykit.LanguageManager.getString("ha_token_desc"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Token input field
                        OutlinedTextField(
                            value = token,
                            onValueChange = { 
                                token = it
                                validationError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Token", color = textColor.copy(alpha = 0.7f)) },
                            placeholder = { Text("eyJ0eXAiOiJKV1QiLCJhbGc...", color = textColor.copy(alpha = 0.5f)) },
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showToken) "Verberg token" else "Toon token",
                                        tint = buttonColor
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (validationError != null) Color.Red else buttonColor,
                                unfocusedBorderColor = if (validationError != null) Color.Red else textColor.copy(alpha = 0.5f),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                cursorColor = buttonColor
                            ),
                            isError = validationError != null,
                            singleLine = false,
                            maxLines = 3
                        )
                        
                        if (validationError != null) {
                            Text(
                                text = validationError!!,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = buttonColor.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💡 " + com.dd.daykit.LanguageManager.getString("token_tip_label"),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // Footer buttons - centered with matching styling
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = handleDismiss,
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
                                enabled = token.isNotBlank(),
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
