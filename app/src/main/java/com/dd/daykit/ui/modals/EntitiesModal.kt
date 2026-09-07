package com.dd.daykit.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.dd.daykit.LanguageManager

/**
 * EntitiesModal - Beheerbare entiteiten lijst
 * 
 * @param visible Whether the modal is visible
 * @param availableEntities List of all available entity IDs (unused in new design, kept for compat)
 * @param initialSelected Initially selected entity IDs
 * @param onDismiss Callback when modal is dismissed
 * @param onSave Callback with selected entities when save is clicked
 * @param textColor Text color
 * @param buttonColor Button color
 * @param buttonTextColor Button text color
 * @param containerColor Modal background color
 */
@Composable
fun EntitiesModal(
    visible: Boolean,
    availableEntities: List<String> = emptyList(),
    initialSelected: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    // Key on visible to force reset when modal reopens after cancel
    var selectedEntities by remember(visible, initialSelected) { mutableStateOf(initialSelected.toSet()) }
    var showConfirmDialog by remember(visible) { mutableStateOf(false) }
    var showAddDialog by remember(visible) { mutableStateOf(false) }
    var newEntityId by remember(visible) { mutableStateOf("") }
    var entityToDelete by remember(visible) { mutableStateOf<String?>(null) }

    val hasChanges = selectedEntities != initialSelected.toSet()
    
    val handleDismiss = {
        if (hasChanges) {
            showConfirmDialog = true
        } else {
            onDismiss()
        }
    }

    val handleSave = {
        android.util.Log.d("EntitiesModal", "Save clicked with ${selectedEntities.size} selected entity/entities")
        onSave(selectedEntities.toList())
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
                        .semantics { paneTitle = "Entiteiten beheren" },
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
                                text = LanguageManager.getString("ha_entities"),
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
                            text = LanguageManager.getString("entities_description"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        
                        Spacer(Modifier.height(16.dp))

                        // Entity list
                        if (selectedEntities.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = LanguageManager.getString("no_entities_available"),
                                    color = textColor.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .heightIn(max = 400.dp)
                            ) {
                                items(selectedEntities.sorted()) { entity ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = entity,
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { entityToDelete = entity }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Verwijderen",
                                                tint = textColor.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        
                        // Add custom entity button
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = buttonTextColor
                            )
                        ) {
                            Text("+ Entiteit toevoegen")
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
                                Text(LanguageManager.getString("cancel"))
                            }
                            
                            Spacer(Modifier.width(12.dp))
                            
                            Button(
                                onClick = handleSave,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor
                                ),
                                enabled = hasChanges
                            ) {
                                Text(LanguageManager.getString("save"))
                            }
                        }
                    }
                }
            }
            }
        }
    }

    
    // Add custom entity dialog - supports bulk input (multiple lines)
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                newEntityId = ""
            },
            title = { Text(LanguageManager.getString("ha_add_entity"), color = textColor) },
            text = {
                Column {
                    Text(
                        LanguageManager.getString("ha_add_entity_desc"),
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newEntityId,
                        onValueChange = { newEntityId = it },
                        label = { Text(LanguageManager.getString("ha_entity_id"), color = textColor.copy(alpha = 0.7f)) },
                        placeholder = { Text("media_player.woonkamer\nbinary_sensor.example", color = textColor.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = buttonColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.5f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = buttonColor
                        ),
                        singleLine = false,
                        maxLines = 10,
                        modifier = Modifier.height(150.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Parse multiple entities (one per line)
                        val newEntities = newEntityId.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() && it.contains(".") }
                        if (newEntities.isNotEmpty()) {
                            selectedEntities = selectedEntities + newEntities.toSet()
                            showAddDialog = false
                            newEntityId = ""
                        }
                    },
                    enabled = newEntityId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("ha_add"))
                }
            },
            dismissButton = {
                Button(
                    onClick = { 
                        showAddDialog = false
                        newEntityId = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("cancel"))
                }
            },
            containerColor = containerColor
        )
    }

    // Delete confirmation dialog
    if (entityToDelete != null) {
        AlertDialog(
            onDismissRequest = { entityToDelete = null },
            title = { Text("Entiteit verwijderen", color = textColor) },
            text = { 
                Text(
                    "Weet je zeker dat je '$entityToDelete' wilt verwijderen?",
                    color = textColor
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        entityToDelete?.let {
                            val newSize = selectedEntities.size - 1
                            android.util.Log.d("EntitiesModal", "Deleting entity '$it'; new size: $newSize")
                            selectedEntities = selectedEntities - it
                        }
                        entityToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text("Verwijderen")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { entityToDelete = null },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textColor
                    )
                ) {
                    Text(LanguageManager.getString("cancel"))
                }
            },
            containerColor = containerColor
        )
    }
    
    // Unsaved changes confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(LanguageManager.getString("unsaved_changes"), color = textColor) },
            text = { 
                Text(
                    LanguageManager.getString("unsaved_changes_message"),
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
                    Text(LanguageManager.getString("cancel"))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textColor
                    )
                ) {
                    Text(LanguageManager.getString("no_keep_editing"))
                }
            },
            containerColor = containerColor
        )
    }
}
