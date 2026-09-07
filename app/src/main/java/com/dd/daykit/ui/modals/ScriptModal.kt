package com.dd.daykit.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
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
 * ScriptModal - Home Assistant script (script.turn_on) configuration modal.
 * Reused for both "script bij alarm" and "script bij timer" (identical shape, different
 * labels/callback wiring), same hand-rolled structure as PresenceModal.
 *
 * @param visible Whether the modal is visible
 * @param title Modal/header title
 * @param description Short explanation shown under the title
 * @param enableLabel Label next to the enable/disable toggle
 * @param entityLabel Label above the script entity dropdown
 * @param availableScripts List of available script entity IDs (script.*)
 * @param initialSelectedEntity Initially selected script entity ID
 * @param initialEnabled Whether the script is initially enabled
 * @param onDismiss Callback when modal is dismissed
 * @param onSave Callback with entity and enabled state when save is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptModal(
    visible: Boolean,
    title: String,
    description: String,
    enableLabel: String,
    entityLabel: String,
    availableScripts: List<String> = emptyList(),
    initialSelectedEntity: String? = null,
    initialEnabled: Boolean = false,
    initialIgnorePresence: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (entity: String?, enabled: Boolean, ignorePresence: Boolean) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    // Key on visible to force reset when modal reopens after cancel
    var selectedEntity by remember(visible, initialSelectedEntity) { mutableStateOf(initialSelectedEntity) }
    var enabled by remember(visible, initialEnabled) { mutableStateOf(initialEnabled) }
    var ignorePresence by remember(visible, initialIgnorePresence) { mutableStateOf(initialIgnorePresence) }
    var showConfirmDialog by remember(visible) { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val hasChanges = selectedEntity != initialSelectedEntity ||
        enabled != initialEnabled ||
        ignorePresence != initialIgnorePresence

    val handleDismiss = {
        if (hasChanges) {
            showConfirmDialog = true
        } else {
            onDismiss()
        }
    }

    val handleSave = {
        onSave(selectedEntity, enabled, ignorePresence)
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
                            .semantics { paneTitle = title },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = containerColor),
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

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.7f)
                            )

                            Spacer(Modifier.height(16.dp))

                            // Enable/disable toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = enableLabel,
                                    color = textColor,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { enabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = buttonColor,
                                        checkedTrackColor = buttonColor.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            AnimatedVisibility(visible = enabled) {
                                Column {
                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                        text = entityLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = textColor,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    if (availableScripts.isEmpty()) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color.Red.copy(alpha = 0.1f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = com.dd.daykit.LanguageManager.getString("ha_no_entities_found"),
                                                color = Color.Red,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    } else {
                                        ExposedDropdownMenuBox(
                                            expanded = isDropdownExpanded,
                                            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                                        ) {
                                            OutlinedTextField(
                                                value = selectedEntity ?: "Selecteer script...",
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text(entityLabel, color = textColor.copy(alpha = 0.7f)) },
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = "Dropdown",
                                                        tint = buttonColor
                                                    )
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = buttonColor,
                                                    unfocusedBorderColor = textColor.copy(alpha = 0.5f),
                                                    cursorColor = buttonColor,
                                                    focusedTextColor = textColor,
                                                    unfocusedTextColor = textColor
                                                )
                                            )
                                            ExposedDropdownMenu(
                                                expanded = isDropdownExpanded,
                                                onDismissRequest = { isDropdownExpanded = false },
                                                modifier = Modifier.background(containerColor)
                                            ) {
                                                availableScripts.forEach { entity ->
                                                    DropdownMenuItem(
                                                        text = { Text(entity, color = textColor) },
                                                        onClick = {
                                                            selectedEntity = entity
                                                            isDropdownExpanded = false
                                                        },
                                                        colors = MenuDefaults.itemColors(
                                                            textColor = textColor
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    // Altijd uitvoeren vs. alleen als gebruiker thuis is (zelfde
                                    // presence-check als externe-speaker DEFAULT-modus)
                                    Text(
                                        text = com.dd.daykit.LanguageManager.getString("ha_script_run_condition"),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = textColor,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { ignorePresence = true }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            RadioButton(
                                                selected = ignorePresence,
                                                onClick = { ignorePresence = true },
                                                colors = RadioButtonDefaults.colors(selectedColor = buttonColor)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = com.dd.daykit.LanguageManager.getString("ha_script_always_run"),
                                                color = textColor,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { ignorePresence = false }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            RadioButton(
                                                selected = !ignorePresence,
                                                onClick = { ignorePresence = false },
                                                colors = RadioButtonDefaults.colors(selectedColor = buttonColor)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = com.dd.daykit.LanguageManager.getString("ha_script_only_if_home"),
                                                color = textColor,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
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
            title = { Text("Onopgeslagen wijzigingen", color = textColor) },
            text = {
                Text(
                    "Er zijn onopgeslagen wijzigingen. Weet je zeker dat je wilt annuleren?",
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
                    Text("Nee, blijf bewerken")
                }
            },
            containerColor = containerColor
        )
    }
}
