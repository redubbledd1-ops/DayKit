package com.dd.daykit.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
 * PresenceModal - kiest welke entiteit als thuis-detectie gebruikt wordt.
 *
 * Presence-detectie zelf staat altijd aan (geen aan/uit-schakelaar meer, zie
 * HaSettingsActivity.kt's statusregel + AlarmOutputDecisionEngine.isUserAtHome()'s fail-safe):
 * zonder gekozen entiteit valt de app terug op de automatische keuze (ingebouwde ping-sensor,
 * of een enkele person.-entiteit, zie HaSettingsViewModel.tryAutoAdoptPresenceEntity) of, als
 * ook dat niets oplevert, wordt fail-safe aangenomen dat de gebruiker thuis is. Deze modal is
 * dus alleen nog een override-picker voor het randgeval dat iemand de automatische keuze wil
 * vervangen - "Automatisch" (lege selectie) is expliciet een keuze-optie, geen losse "uit"-stand.
 *
 * De "waarde als je thuis bent" (presenceExpectedState) is sinds de automatische afleiding op
 * domein (zie deriveExpectedPresenceState) geen apart invulveld meer - die wordt overal waar
 * nodig (app + HA-config-push) automatisch bepaald uit het domein van de gekozen entiteit.
 *
 * @param visible Whether the modal is visible
 * @param availablePresenceEntities List of available presence entity IDs (person.*, device_tracker.*, binary_sensor.*)
 * @param initialSelectedEntity Initially selected presence entity ID (null/blank = automatisch)
 * @param onDismiss Callback when modal is dismissed
 * @param onSave Callback with the chosen entity id (null = terug naar automatisch) when save is clicked
 * @param textColor Text color
 * @param buttonColor Button color
 * @param buttonTextColor Button text color
 * @param containerColor Modal background color
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresenceModal(
    visible: Boolean,
    availablePresenceEntities: List<String> = emptyList(),
    initialSelectedEntity: String? = null,
    onDismiss: () -> Unit,
    onSave: (entity: String?) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    // Key on visible to force reset when modal reopens after cancel
    var selectedEntity by remember(visible, initialSelectedEntity) { mutableStateOf(initialSelectedEntity) }
    var showConfirmDialog by remember(visible) { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val hasChanges = selectedEntity != initialSelectedEntity

    val handleDismiss = {
        if (hasChanges) {
            showConfirmDialog = true
        } else {
            onDismiss()
        }
    }

    val handleSave = {
        onSave(selectedEntity)
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
                        .heightIn(max = 500.dp)
                        .semantics { paneTitle = com.dd.daykit.LanguageManager.getString("ha_presence") },
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
                                text = com.dd.daykit.LanguageManager.getString("ha_presence"),
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
                            text = com.dd.daykit.LanguageManager.getString("ha_presence_desc"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = com.dd.daykit.LanguageManager.getString("ha_select_presence_entity"),
                            style = MaterialTheme.typography.titleSmall,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = isDropdownExpanded,
                            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedEntity?.takeIf { it.isNotBlank() }
                                    ?: com.dd.daykit.LanguageManager.getString("ha_presence_automatic"),
                                onValueChange = {},
                                readOnly = true,
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
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            com.dd.daykit.LanguageManager.getString("ha_presence_automatic"),
                                            color = textColor
                                        )
                                    },
                                    onClick = {
                                        selectedEntity = null
                                        isDropdownExpanded = false
                                    },
                                    colors = MenuDefaults.itemColors(textColor = textColor)
                                )
                                availablePresenceEntities.forEach { entity ->
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

                        if (availablePresenceEntities.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
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
