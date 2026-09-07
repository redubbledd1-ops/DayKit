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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dd.daykit.LanguageManager
import com.dd.daykit.homeassistant.PairingClient

/**
 * Modal voor het koppelen van de app met de DayKit HA-integratie via een
 * kortdurende setup-code (gescand als QR of handmatig overgetypt) i.p.v. het
 * long-lived token zelf te moeten kopieren/plakken.
 *
 * @param scannedPayload wordt door de aanroeper gezet zodra een QR-scan resultaat
 *   opleverde (nieuw object = nieuwe scan); dit modal vult dan automatisch de
 *   velden hieronder in.
 */
@Composable
fun PairModal(
    visible: Boolean,
    initialBaseUrl: String = "",
    scannedPayload: PairingClient.PairingPayload?,
    isPairing: Boolean,
    statusMessage: String?,
    statusIsError: Boolean,
    onDismiss: () -> Unit,
    onScanQrClick: () -> Unit,
    onPair: (baseUrl: String, code: String) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    var baseUrlText by remember(visible) { mutableStateOf(initialBaseUrl) }
    var codeText by remember(visible) { mutableStateOf("") }

    LaunchedEffect(scannedPayload) {
        scannedPayload?.let {
            baseUrlText = it.base_url
            codeText = it.code
        }
    }

    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.95f, animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150))
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(0.95f)
                        .semantics { paneTitle = "Koppelen met Home Assistant" },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = textColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Koppelen met Home Assistant",
                                style = MaterialTheme.typography.headlineSmall,
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = LanguageManager.getString("close"), tint = textColor)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Genereer in Home Assistant een koppelcode (knop 'Genereer koppelcode' bij de " +
                                "AgendaAlarm Backup-integratie) en scan de QR-code, of typ de 6-cijferige code " +
                                "hieronder handmatig over. De code is 5 minuten geldig en maar 1x te gebruiken.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = onScanQrClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, buttonColor.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan QR-code")
                        }

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = baseUrlText,
                            onValueChange = { baseUrlText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Home Assistant-adres", color = textColor.copy(alpha = 0.7f)) },
                            placeholder = { Text("http://192.168.1.56:8123", color = textColor.copy(alpha = 0.5f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = buttonColor,
                                unfocusedBorderColor = textColor.copy(alpha = 0.5f),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                cursorColor = buttonColor
                            )
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = codeText,
                            onValueChange = { new ->
                                codeText = new.filter { it.isDigit() }.take(6)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Koppelcode", color = textColor.copy(alpha = 0.7f)) },
                            placeholder = { Text("123456", color = textColor.copy(alpha = 0.5f)) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = buttonColor,
                                unfocusedBorderColor = textColor.copy(alpha = 0.5f),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                cursorColor = buttonColor
                            )
                        )

                        if (statusMessage != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = statusMessage,
                                color = if (statusIsError) Color.Red else Color(0xFF2E7D32),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                            ) {
                                Text(LanguageManager.getString("cancel"))
                            }

                            Spacer(Modifier.width(12.dp))

                            Button(
                                onClick = { onPair(baseUrlText, codeText) },
                                enabled = !isPairing && baseUrlText.isNotBlank() && codeText.length == 6,
                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                            ) {
                                if (isPairing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = buttonTextColor)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Koppelen")
                            }
                        }
                    }
                }
            }
        }
    }
}
