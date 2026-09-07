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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Modal voor "Uit bed check" configuratie
 * Gebruiker kan een entiteit kiezen en de waarde instellen die aangeeft dat iemand nog in bed ligt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutOfBedModal(
    visible: Boolean,
    availableEntities: List<String>,
    initialSelectedEntity: String?,
    initialExpectedValue: String,
    initialEnabled: Boolean,
    isPresenceEnabled: Boolean = true,
    onEnablePresenceClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (entityId: String?, expectedValue: String, enabled: Boolean) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    if (!visible) return

    // Key on visible to force reset when modal reopens after cancel
    var enabled by remember(visible, initialEnabled) { mutableStateOf(initialEnabled) }
    var selectedEntity by remember(visible, initialSelectedEntity) { mutableStateOf(initialSelectedEntity) }
    var expectedValue by remember(visible, initialExpectedValue) { mutableStateOf(initialExpectedValue) }
    var isDropdownExpanded by remember(visible) { mutableStateOf(false) }
    var isValueDropdownExpanded by remember(visible) { mutableStateOf(false) }
    var showConfirmDialog by remember(visible) { mutableStateOf(false) }
    val context = LocalContext.current
    val dependencyMessage = com.dd.daykit.LanguageManager.getString("ha_out_of_bed_requires_presence")
    
    // Check if there are unsaved changes
    val hasChanges = enabled != initialEnabled || 
                     selectedEntity != initialSelectedEntity || 
                     expectedValue != initialExpectedValue
    
    // Handle dismiss with unsaved changes check
    val handleDismiss = {
        if (hasChanges) {
            showConfirmDialog = true
        } else {
            onDismiss()
        }
    }
    
    // Determine entity type for UI logic
    val isBinarySensor = remember(selectedEntity) {
        selectedEntity?.startsWith("binary_sensor.") == true || 
        selectedEntity?.startsWith("input_boolean.") == true
    }
    
    val isSwitch = remember(selectedEntity) {
        selectedEntity?.startsWith("switch.") == true ||
        selectedEntity?.startsWith("light.") == true
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 600.dp)
                    .semantics { paneTitle = com.dd.daykit.LanguageManager.getString("ha_out_of_bed") },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = com.dd.daykit.LanguageManager.getString("ha_out_of_bed"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = handleDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sluiten",
                                tint = textColor
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Description
                    Text(
                        text = com.dd.daykit.LanguageManager.getString("ha_out_of_bed_desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f)
                    )

                    Spacer(Modifier.height(16.dp))

                    if (!isPresenceEnabled) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = buttonColor.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = dependencyMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.85f)
                                )
                                if (onEnablePresenceClick != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = onEnablePresenceClick,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = buttonColor,
                                            contentColor = buttonTextColor
                                        )
                                    ) {
                                        Text(com.dd.daykit.LanguageManager.getString("ha_enable_presence_detection"))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    // Enable/Disable Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPresenceEnabled) {
                                    enabled = !enabled
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        dependencyMessage,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = com.dd.daykit.LanguageManager.getString("ha_out_of_bed_enable"),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isPresenceEnabled) textColor else textColor.copy(alpha = 0.45f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                if (isPresenceEnabled) {
                                    enabled = checked
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        dependencyMessage,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = isPresenceEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = buttonTextColor,
                                checkedTrackColor = buttonColor,
                                uncheckedThumbColor = textColor.copy(alpha = 0.5f),
                                uncheckedTrackColor = textColor.copy(alpha = 0.2f)
                            )
                        )
                    }

                    HorizontalDivider(color = textColor.copy(alpha = 0.2f))

                    // Content with scroll
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AnimatedVisibility(visible = enabled && isPresenceEnabled) {
                            Column {
                                Spacer(Modifier.height(16.dp))
                                
                                // Entity selection
                                Text(
                                    text = com.dd.daykit.LanguageManager.getString("ha_select_entity"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = textColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                
                                Spacer(Modifier.height(8.dp))
                                
                                if (availableEntities.isEmpty()) {
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
                                            value = selectedEntity ?: "Selecteer entiteit...",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Entiteit", color = textColor.copy(alpha = 0.7f)) },
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
                                            availableEntities.forEach { entity ->
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
                                
                                Spacer(Modifier.height(16.dp))
                                
                                // Expected value field
                                Text(
                                    text = com.dd.daykit.LanguageManager.getString("ha_value_when_in_bed"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = textColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                
                                Spacer(Modifier.height(8.dp))
                                
                                // Logic for value selection based on entity type
                                if (isBinarySensor || isSwitch) {
                                    ExposedDropdownMenuBox(
                                        expanded = isValueDropdownExpanded,
                                        onExpandedChange = { isValueDropdownExpanded = !isValueDropdownExpanded }
                                    ) {
                                        // Determine display label for current value
                                        val displayValue = when {
                                            isSwitch -> if (expectedValue == "on") "Aan" else if (expectedValue == "off") "Uit" else expectedValue
                                            else -> if (expectedValue == "on") "Waar" else if (expectedValue == "off") "Niet waar" else expectedValue
                                        }
                                        
                                        OutlinedTextField(
                                            value = displayValue,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Waarde", color = textColor.copy(alpha = 0.7f)) },
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
                                            expanded = isValueDropdownExpanded,
                                            onDismissRequest = { isValueDropdownExpanded = false },
                                            modifier = Modifier.background(containerColor)
                                        ) {
                                            val options = if (isSwitch) {
                                                listOf("on" to "Aan", "off" to "Uit")
                                            } else {
                                                listOf("on" to "Waar", "off" to "Niet waar")
                                            }
                                            
                                            options.forEach { (value, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label, color = textColor) },
                                                    onClick = {
                                                        expectedValue = value
                                                        isValueDropdownExpanded = false
                                                    },
                                                    colors = MenuDefaults.itemColors(
                                                        textColor = textColor
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Other entities: text field
                                    OutlinedTextField(
                                        value = expectedValue,
                                        onValueChange = { expectedValue = it },
                                        label = { Text("Waarde (bijvoorbeeld 'true', 2, etc.)", color = textColor.copy(alpha = 0.7f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = textColor,
                                            unfocusedTextColor = textColor,
                                            focusedBorderColor = buttonColor,
                                            unfocusedBorderColor = textColor.copy(alpha = 0.5f)
                                        ),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    // Footer buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = handleDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = buttonTextColor
                            )
                        ) {
                            Text(com.dd.daykit.LanguageManager.getString("cancel"))
                        }

                        Button(
                            onClick = {
                                onSave(selectedEntity, expectedValue, enabled)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = buttonTextColor
                            ),
                            enabled = !enabled || (isPresenceEnabled && selectedEntity != null && expectedValue.isNotBlank())
                        ) {
                            Text(com.dd.daykit.LanguageManager.getString("save"))
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
