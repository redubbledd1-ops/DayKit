package com.dd.daykit.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex

/**
 * Reusable modal dialog component with animations, accessibility, and unsaved changes handling
 * 
 * @param visible Whether the modal is visible
 * @param title Modal title
 * @param onDismiss Callback when modal should be dismissed
 * @param onSave Callback when save button is clicked
 * @param hasUnsavedChanges Whether there are unsaved changes
 * @param showSaveButton Whether to show the save button
 * @param containerColor Background color of the modal
 * @param textColor Text color
 * @param buttonColor Button color
 * @param buttonTextColor Button text color
 * @param content Modal content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    hasUnsavedChanges: Boolean = false,
    showSaveButton: Boolean = true,
    containerColor: Color = Color.White,
    textColor: Color = Color.Black,
    buttonColor: Color = Color.Blue,
    buttonTextColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Calculate modal width: max 720dp on desktop, full width with padding on mobile
    val modalWidth = if (screenWidth > 720.dp) 720.dp else screenWidth - 32.dp
    
    // Handle ESC key
    val onEscapePressed = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onDismiss()
        }
    }
    
    // Handle dismiss with unsaved changes check
    val handleDismiss = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onDismiss()
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
            // Animated container with fade + scale
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(200)) + 
                        scaleIn(initialScale = 0.95f, animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150)) + 
                       scaleOut(targetScale = 0.95f, animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { handleDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(modalWidth)
                            .heightIn(max = configuration.screenHeightDp.dp - 100.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Prevent click-through */ }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                                    onEscapePressed()
                                    true
                                } else {
                                    false
                                }
                            }
                            .focusRequester(focusRequester)
                            .semantics {
                                contentDescription = title
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            // Header with title and close button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
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
                            
                            // Content
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                            ) {
                                content()
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            
                            // Footer buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = handleDismiss,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = textColor
                                    )
                                ) {
                                    Text("Annuleren")
                                }
                                
                                if (showSaveButton) {
                                    Spacer(Modifier.width(12.dp))
                                    Button(
                                        onClick = onSave,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor
                                        )
                                    ) {
                                        Text("Opslaan")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Request focus when modal opens
            LaunchedEffect(visible) {
                if (visible) {
                    focusRequester.requestFocus()
                }
            }
        }
    }
    
    // Unsaved changes confirmation dialog
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Onopgeslagen wijzigingen", color = textColor) },
            text = { 
                Text(
                    "Er zijn onopgeslagen wijzigingen. Weet je zeker dat je wilt annuleren?",
                    color = textColor
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text("Ja, annuleren")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showUnsavedDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textColor
                    )
                ) {
                    Text("Nee, blijf bewerken")
                }
            },
            containerColor = containerColor
        )
    }
}
