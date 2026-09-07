package com.dd.daykit.ui.modals

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.dd.daykit.CustomSoundManager
import com.dd.daykit.LanguageManager
import com.dd.daykit.database.CustomAlarmSound
import com.dd.daykit.sound.Sound
import com.dd.daykit.sound.SoundViewModel
import kotlinx.coroutines.launch

/**
 * Modal for managing custom alarm sounds
 */
@Composable
fun CustomSoundModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSoundSelected: (CustomAlarmSound) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundManager = remember { CustomSoundManager(context) }

    // HA sound-sync via de daykit-integratie (zelfde HA-verbinding als de rest
    // van de app - geen apart AppDaemon-adres meer nodig, upload gebruikt de HA-instellingen
    // die al bij Home Assistant-instellingen zijn geconfigureerd).
    var isSyncingFromHa by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    // Handmatige (her)upload van ALLE lokale geluiden naar HA - vult de automatische
    // best-effort upload-bij-toevoegen aan (SoundRepository.addSound) met een retry-pad voor
    // geluiden die al vóór die automatische upload zijn toegevoegd, of waarvan de upload eerder
    // mislukte (bv. door de te-korte timeout die hier gefixt is, zie HomeAssistantClient).
    var isUploadingAll by remember { mutableStateOf(false) }
    var uploadAllMessage by remember { mutableStateOf<String?>(null) }
    var uploadAllProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    // Use ViewModel for state management
    val viewModel: SoundViewModel = viewModel(
        factory = SoundViewModel.Factory(context)
    )
    
    // Collect StateFlows
    val sounds by viewModel.sounds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val playingSoundId by viewModel.playingSoundId.collectAsState()
    
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var soundToDelete by remember { mutableStateOf<Sound?>(null) }
    
    // Helper function to stop and release MediaPlayer
    fun stopPlayback() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                release()
            } catch (e: Exception) {
                // Already stopped/released
            }
        }
        mediaPlayer = null
        viewModel.setPlayingSound(null)
    }
    
    // Load sounds when modal becomes visible
    LaunchedEffect(visible) {
        if (visible) {
            viewModel.loadSounds()
        } else {
            // Stop playback when modal is dismissed
            stopPlayback()
        }
    }
    
    // Handle back button press - stop playback and close modal
    BackHandler(enabled = visible) {
        stopPlayback()
        onDismiss()
    }
    
    // Show Toast messages for user feedback
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    
    LaunchedEffect(successMessage) {
        successMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    // File picker launcher with SAF support
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                viewModel.clearError()
                viewModel.clearSuccessMessage()
                
                // Note: Persistable permission is now handled in CustomSoundManager
                // This ensures proper SAF support for Android 11+
                viewModel.addSound(uri)
            }
        } ?: run {
            // User cancelled file picker
            Toast.makeText(context, "Geen bestand geselecteerd", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Cleanup media player on dispose
    DisposableEffect(Unit) {
        onDispose {
            stopPlayback()
        }
    }
    
    if (visible) {
        Dialog(
            onDismissRequest = {
                stopPlayback()
                onDismiss()
            },
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
                            .semantics { paneTitle = LanguageManager.getString("custom_alarm_sounds") },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = LanguageManager.getString("custom_alarm_sounds"),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        stopPlayback()
                                        onDismiss()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = LanguageManager.getString("close"),
                                        tint = textColor
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Description
                            Text(
                                text = LanguageManager.getString("custom_sounds_description"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.7f)
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Add button
                            Button(
                                onClick = {
                                    filePickerLauncher.launch(arrayOf("audio/*"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor
                                ),
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(LanguageManager.getString("add_custom_sound"))
                            }
                            
                            Spacer(Modifier.height(16.dp))

                            // HA sound-sync: url instellen + downloaden vanaf HA
                            Text(
                                text = "Home Assistant sound-sync",
                                style = MaterialTheme.typography.titleSmall,
                                color = textColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Nieuwe geluiden die je hierboven toevoegt, worden automatisch naar Home Assistant geupload (best-effort, via je bestaande HA-verbinding). Gebruik \"Alle geluiden (opnieuw) uploaden\" hieronder om ook eerder toegevoegde geluiden of mislukte uploads (opnieuw) te versturen, of \"Download geluiden vanaf HA\" om geluiden die al op HA staan naar de telefoon te halen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    isSyncingFromHa = true
                                    syncMessage = null
                                    scope.launch {
                                        val result = com.dd.daykit.homeassistant.SoundHaSync.downloadSoundsFromHa(context)
                                        isSyncingFromHa = false
                                        syncMessage = if (result.isSuccess) {
                                            val count = result.getOrNull() ?: 0
                                            viewModel.loadSounds()
                                            if (count > 0) "✓ $count nieuw geluid(en) opgehaald van HA" else "Geen nieuwe geluiden gevonden op HA"
                                        } else {
                                            "Fout: ${result.exceptionOrNull()?.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSyncingFromHa,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonColor),
                                border = androidx.compose.foundation.BorderStroke(1.dp, buttonColor.copy(alpha = 0.5f))
                            ) {
                                if (isSyncingFromHa) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = buttonColor)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Download geluiden vanaf HA")
                            }
                            syncMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (msg.startsWith("Fout")) Color.Red else Color.Green,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    isUploadingAll = true
                                    uploadAllMessage = null
                                    scope.launch {
                                        val customSounds = sounds.filter { !it.isSystemSound }
                                        var succeeded = 0
                                        val failures = mutableListOf<String>()
                                        customSounds.forEachIndexed { index, sound ->
                                            uploadAllProgress = (index + 1) to customSounds.size
                                            val result = com.dd.daykit.homeassistant.SoundHaSync.uploadSoundToHa(context, sound)
                                            if (result.isSuccess) {
                                                succeeded++
                                            } else {
                                                failures.add("${sound.name}: ${result.exceptionOrNull()?.message ?: "onbekende fout"}")
                                            }
                                        }
                                        isUploadingAll = false
                                        uploadAllProgress = null
                                        uploadAllMessage = when {
                                            customSounds.isEmpty() -> "Geen eigen geluiden om te uploaden"
                                            failures.isEmpty() -> "✓ $succeeded geluid(en) geupload naar HA"
                                            else -> "Fout: $succeeded/${customSounds.size} gelukt. Mislukt: ${failures.joinToString("; ")}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isUploadingAll,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonColor),
                                border = androidx.compose.foundation.BorderStroke(1.dp, buttonColor.copy(alpha = 0.5f))
                            ) {
                                if (isUploadingAll) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = buttonColor)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    uploadAllProgress?.let { (done, total) -> "Uploaden... ($done/$total)" }
                                        ?: "Alle geluiden (opnieuw) uploaden naar HA"
                                )
                            }
                            uploadAllMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (msg.startsWith("Fout")) Color.Red else Color.Green,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Messages
                            errorMessage?.let { msg ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Red.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = msg,
                                            color = Color.Red,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            
                            successMessage?.let { msg ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Green.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.Green,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = msg,
                                            color = Color.Green,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            
                            // Loading indicator
                            if (isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = buttonColor)
                                }
                            } else if (sounds.isEmpty()) {
                                // Empty state
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = textColor.copy(alpha = 0.3f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = LanguageManager.getString("no_custom_sounds"),
                                            color = textColor.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else {
                                // Sound list
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    items(sounds, key = { it.id }) { sound ->
                                        CustomSoundItem(
                                            sound = sound,
                                            isPlaying = playingSoundId == sound.id,
                                            onPlay = {
                                                if (playingSoundId == sound.id) {
                                                    // Stop current playback
                                                    stopPlayback()
                                                } else {
                                                    // Stop any current playback first
                                                    stopPlayback()
                                                    
                                                    // Start new playback
                                                    try {
                                                        mediaPlayer = MediaPlayer().apply {
                                                            // Set audio attributes for alarm/notification
                                                            setAudioAttributes(
                                                                AudioAttributes.Builder()
                                                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                                                    .build()
                                                            )
                                                            setDataSource(sound.filePath)
                                                            prepare()
                                                            setOnCompletionListener {
                                                                viewModel.setPlayingSound(null)
                                                            }
                                                            setOnErrorListener { _, _, _ ->
                                                                viewModel.setPlayingSound(null)
                                                                true
                                                            }
                                                            start()
                                                        }
                                                        viewModel.setPlayingSound(sound.id)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            "Kan geluid niet afspelen: ${e.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        viewModel.setPlayingSound(null)
                                                    }
                                                }
                                            },
                                            onSelect = {
                                                stopPlayback()
                                                // Convert Sound to CustomAlarmSound for callback
                                                scope.launch {
                                                    val customSound = soundManager.getSoundById(sound.id)
                                                    customSound?.let { onSoundSelected(it) }
                                                }
                                            },
                                            onDelete = {
                                                soundToDelete = sound
                                            },
                                            viewModel = viewModel,
                                            textColor = textColor,
                                            buttonColor = buttonColor
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Close button
                            Button(
                                onClick = {
                                    stopPlayback()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor
                                )
                            ) {
                                Text(LanguageManager.getString("close"))
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    soundToDelete?.let { sound ->
        AlertDialog(
            onDismissRequest = { soundToDelete = null },
            title = { Text(LanguageManager.getString("delete_custom_sound"), color = textColor) },
            text = {
                Text(
                    LanguageManager.getString("delete_custom_sound_confirm").replace("{name}", sound.name),
                    color = textColor.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.deleteSound(sound)
                            soundToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { soundToDelete = null }
                ) {
                    Text(
                        text = LanguageManager.getString("cancel"),
                        color = textColor
                    )
                }
            },
            containerColor = containerColor
        )
    }
}

@Composable
private fun CustomSoundItem(
    sound: Sound,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    viewModel: SoundViewModel,
    textColor: Color,
    buttonColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = textColor.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play button
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = buttonColor
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sound.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = viewModel.formatDuration(sound.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
            
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}
