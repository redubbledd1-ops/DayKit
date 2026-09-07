package com.dd.daykit.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dd.daykit.LanguageManager
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.getNextSchedulableWakeUpEvent
import com.dd.daykit.homeassistant.HomeAssistantSync
import com.dd.daykit.sound.HttpServerManager
import com.dd.daykit.sound.SoundRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Composable knop voor het synchroniseren van geluiden EN de volgende wekker naar Home Assistant
 */
@Composable
fun SoundSyncButton(
    modifier: Modifier = Modifier,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var syncResult by remember { mutableStateOf<SyncResult?>(null) }
    var soundCount by remember { mutableStateOf(0) }
    var deviceIp by remember { mutableStateOf<String?>(null) }
    var haUrl by remember { mutableStateOf<String?>(null) }
    var canSync by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    
    // Check location permission
    LaunchedEffect(Unit) {
        hasLocationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    // Load preview on mount
    LaunchedEffect(Unit) {
        val storage = HomeAssistantSettingsStorage(context)
        val settings = storage.settingsFlow.firstOrNull()
        val repository = SoundRepository.getInstance(context)
        val sounds = repository.sounds.value
        
        soundCount = sounds.size
        deviceIp = HttpServerManager.getDeviceIpAddress(context)
        haUrl = settings?.activeBaseUrl
        
        val serverStatus = HttpServerManager.getServerStatus(context)
        
        val httpServerEnabled = serverStatus.enabled
        val hasUrl = !haUrl.isNullOrEmpty()
        val hasSounds = sounds.isNotEmpty()
        val hasWifi = serverStatus.wifiConnected && deviceIp != null
        
        if (hasUrl && !httpServerEnabled && hasWifi) {
            HttpServerManager.setEnabled(context, true)
            Toast.makeText(
                context,
                "HTTP Server automatisch ingeschakeld voor Home Assistant",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        if (HttpServerManager.isEnabled(context) && !HttpServerManager.isRunning()) {
            HttpServerManager.startServer(context)
        }
        
        canSync = HttpServerManager.isEnabled(context) && hasUrl && hasSounds && hasWifi
        
        if (!canSync) {
            val errors = mutableListOf<String>()
            if (!hasWifi) errors.add("• ${LanguageManager.getString("sync_no_wifi")}")
            if (!hasUrl) errors.add("• ${LanguageManager.getString("sync_ha_url_not_set")}")
            if (!HttpServerManager.isEnabled(context)) errors.add("• ${LanguageManager.getString("sync_http_server_disabled")}")
            if (!hasSounds) errors.add("• ${LanguageManager.getString("sync_no_sounds")}")
            errorMessage = errors.joinToString("\n")
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(1.dp, buttonColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = buttonColor
                )
                Text(
                    text = LanguageManager.getString("sync_alarm_with_agenda"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            
            // Description
            Text(
                text = LanguageManager.getString("sync_alarm_desc"),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.7f)
            )
            
            // Preview info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InfoRow(icon = Icons.Default.MusicNote, label = LanguageManager.getString("sync_sounds"), value = "$soundCount", textColor = textColor)
                InfoRow(icon = Icons.Default.Wifi, label = LanguageManager.getString("sync_device_ip"), value = deviceIp ?: LanguageManager.getString("sync_not_connected"), textColor = textColor)
                InfoRow(icon = Icons.Default.Home, label = "Home Assistant", value = haUrl ?: LanguageManager.getString("sync_not_configured"), textColor = textColor)
            }
            
            // Warnings
            errorMessage?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                        Column {
                            Text(LanguageManager.getString("sync_cannot_sync"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Red)
                            Text(message, style = MaterialTheme.typography.bodySmall, color = textColor)
                        }
                    }
                }
            }
            
            // Sync result
            AnimatedVisibility(visible = syncResult != null) {
                syncResult?.let { result ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (result.success) Color.Green.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f))) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null, tint = if (result.success) Color.Green else Color.Red)
                            Text(result.message, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                }
            }
            
            // Location permission dialog
            if (showLocationDialog) {
                AlertDialog(
                    onDismissRequest = { showLocationDialog = false },
                    title = { Text(LanguageManager.getString("sync_location_permission_required")) },
                    text = { Text(LanguageManager.getString("sync_location_permission_desc")) },
                    confirmButton = {
                        Button(onClick = {
                            showLocationDialog = false
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", context.packageName, null)
                            context.startActivity(intent)
                        }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)) {
                            Text(LanguageManager.getString("sync_open_settings"))
                        }
                    },
                    dismissButton = { TextButton(onClick = { showLocationDialog = false }) { Text(LanguageManager.getString("cancel")) } }
                )
            }
            
            // Sync button
            Button(
                onClick = {
                    if (!hasLocationPermission) {
                        showLocationDialog = true
                        return@Button
                    }
                    
                    scope.launch {
                        try {
                            val serverStatus = HttpServerManager.getServerStatus(context)
                            if (!serverStatus.isFullyOperational()) {
                                val issues = serverStatus.getIssues().joinToString("\n• ")
                                syncResult = SyncResult(false, "Server niet operationeel:\n• $issues")
                                return@launch
                            }

                            // STAP 1: Haal de Home Assistant instellingen op
                            val haSettingsStorage = HomeAssistantSettingsStorage(context)
                            val haSettings = haSettingsStorage.settingsFlow.firstOrNull()
                            if (haSettings == null || haSettings.activeBaseUrl.isNullOrBlank()) {
                                syncResult = SyncResult(false, "Home Assistant is niet geconfigureerd.")
                                return@launch
                            }

                            // STAP 2: Zoek de eerstvolgende wekker
                            val nextAlarm = getNextSchedulableWakeUpEvent(context)

                            if (nextAlarm == null) {
                                // Geen wekker gevonden, maar wel de status in HA wissen
                                HomeAssistantSync.clearNextAlarm(context)
                                syncResult = SyncResult(true, "Geen volgende wekker gevonden. Status in Home Assistant gewist.")
                                return@launch
                            }
                            
                            // STAP 3: Synchroniseer de wekker met de geselecteerde speaker
                            val syncJob = HomeAssistantSync.syncNextAlarm(
                                context = context,
                                alarmTimeMillis = nextAlarm.epochMillis,
                                alarmName = nextAlarm.label,
                                enabled = true,
                                alarmSpeakerEntity = haSettings.alarmSpeaker.entityId // HIER WORDT DE SPEAKER MEEGEGEVEN - dit synct altijd de agenda-wekker, dus bewust alarmSpeaker
                            )

                            if (syncJob.isSuccess) {
                                val speakerInfo = if (!haSettings.alarmSpeaker.entityId.isNullOrBlank()) {
                                    "met speaker '${haSettings.alarmSpeaker.entityId}'"
                                } else {
                                    "zonder externe speaker"
                                }
                                syncResult = SyncResult(true, "✓ Wekker '${nextAlarm.label}' gesynchroniseerd $speakerInfo.")
                                Toast.makeText(context, "Synchronisatie met Home Assistant gelukt!", Toast.LENGTH_SHORT).show()
                            } else {
                                syncResult = SyncResult(false, "✗ Fout bij synchroniseren van wekker: ${syncJob.exceptionOrNull()?.message}")
                            }

                        } catch (e: Exception) {
                            android.util.Log.e("SoundSyncButton", "Sync failed", e)
                            syncResult = SyncResult(false, "✗ Synchronisatie mislukt: ${e.message}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSync,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
            ) {
                Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(LanguageManager.getString("sync_alarm_with_agenda"))
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, textColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = textColor.copy(alpha = 0.7f))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f),
            modifier = Modifier.width(100.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = textColor)
    }
}

private data class SyncResult(
    val success: Boolean,
    val message: String
)
