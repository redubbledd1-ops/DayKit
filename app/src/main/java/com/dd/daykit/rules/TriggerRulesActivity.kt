package com.dd.daykit.rules

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.AppBackground
import com.dd.daykit.LanguageManager
import com.dd.daykit.SettingsManager
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import com.dd.daykit.ui.RingtonePickerDialog
import com.dd.daykit.CustomSoundManager
import com.dd.daykit.DdMusicBridge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TriggerRulesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        
        val triggerId = intent.getStringExtra("TRIGGER_ID") ?: ""
        val triggerName = intent.getStringExtra("TRIGGER_NAME") ?: "Unknown"
        val isFirstActivation = intent.getBooleanExtra("IS_FIRST_ACTIVATION", false)
        
        val storage = TriggerBehaviorStorage(applicationContext)
        val haSettingsStorage = HomeAssistantSettingsStorage(applicationContext)
        val haClient = HomeAssistantClient // Singleton
        val haRepository = HomeAssistantRepository(haClient, haSettingsStorage)
        // Pass applicationContext to factory
        val factory = TriggerBehaviorViewModelFactory(applicationContext, triggerId, triggerName, storage, haSettingsStorage, haRepository)
        
        val viewModel: TriggerBehaviorViewModel by viewModels { factory }
        
        setContent {
            MaterialTheme {
                TriggerRulesScreen(
                    viewModel = viewModel,
                    isFirstActivation = isFirstActivation,
                    onCancel = {
                        // Bij annuleren tijdens eerste activatie: trigger terugzetten
                        if (isFirstActivation) {
                            val resultIntent = Intent().apply {
                                putExtra("TRIGGER_ID", triggerId)
                            }
                            setResult(RESULT_CANCELED, resultIntent)
                        }
                        finish()
                    },
                    onSaveSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun TriggerRulesScreen(
    viewModel: TriggerBehaviorViewModel,
    isFirstActivation: Boolean,
    onCancel: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentLanguage by LanguageManager.currentLanguage
    remember(currentLanguage.code) { Unit }

    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))
    
    // Hersteld: Variabele voor dialoog state
    var showEntitySelectionDialog by remember { mutableStateOf(false) }
    var showRingtoneDialog by remember { mutableStateOf(false) }
    var refreshRingtoneDialog by remember { mutableStateOf(0) }

    // Ververs DD Music-koppeling bij terugkeer naar dit scherm (bv. na het maken van een keuze
    // in DD Music) - de broadcast met de samenvatting kan pas ná deze reload binnenkomen, maar
    // de koppeling zelf (ddMusicLinked) staat dan al goed en een volgende reload pakt de rest op.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadDdMusicState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val scope = rememberCoroutineScope()
    val soundManager = remember { CustomSoundManager(context) }
    
    // File picker launcher for adding custom sounds
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                when (val result = soundManager.addCustomSound(uri)) {
                    is CustomSoundManager.AddSoundResult.Success -> {
                        val soundUri = soundManager.getUriForSound(result.sound)
                        viewModel.updateAlarmSound(soundUri.toString())
                        Toast.makeText(context, "✓ ${result.sound.displayName} toegevoegd", Toast.LENGTH_SHORT).show()
                        // Refresh the ringtone dialog to show the new sound
                        refreshRingtoneDialog++
                        showRingtoneDialog = true
                    }
                    is CustomSoundManager.AddSoundResult.Error -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        showRingtoneDialog = true
                    }
                }
            }
        } ?: run {
            Toast.makeText(context, "Geen bestand geselecteerd", Toast.LENGTH_SHORT).show()
            showRingtoneDialog = true
        }
    }
    
    // Calculate current ringtone title
    val currentRingtoneTitle = remember(uiState.alarmSoundUri) {
        val uri = uiState.alarmSoundUri?.let { Uri.parse(it) }
        uri?.let { RingtoneManager.getRingtone(context, it).getTitle(context) } 
            ?: LanguageManager.getString("default_alarm_sound")
    }
    
    // Use refreshRingtoneDialog as key to force recomposition when custom sounds are added
    if (showRingtoneDialog) {
        key(refreshRingtoneDialog) {
            RingtonePickerDialog(
                onDismissRequest = { showRingtoneDialog = false },
                onRingtoneSelected = { uri ->
                    viewModel.updateAlarmSound(uri?.toString())
                    showRingtoneDialog = false
                },
                currentUriString = uiState.alarmSoundUri,
                textColor = textColor,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                containerColor = backgroundColor,
                onAddCustomSound = {
                    showRingtoneDialog = false
                    filePickerLauncher.launch(arrayOf("audio/*"))
                },
                ddMusicLinked = uiState.ddMusicLinked,
                ddMusicSummary = uiState.ddMusicSummary,
                onUseDdMusic = {
                    // Bewust GEEN optimistische ddMusicLinked=true hier - dat gebeurt pas
                    // via DdMusicLinkUpdateReceiver zodra DD Music een echte keuze bevestigt.
                    val started = DdMusicBridge.launch(context, uiState.triggerId, uiState.triggerName, forcePicker = true)
                    if (!started) {
                        Toast.makeText(context, "DD Music is niet geïnstalleerd", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
    
    AppBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp)
            ) {
                // Header - gebruik triggernaam uit agenda
                Text(
                    uiState.triggerName.ifBlank { LanguageManager.getString("alarm_triggers") },
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Scrollable opties
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- PER-TRIGGER INSTELLINGEN ---
                    
                    // 1. Alarm Geluid (incl. DD Music - zie RingtonePickerDialog)
                    item {
                        Text(LanguageManager.getString("alarm_sound"), style = MaterialTheme.typography.titleMedium, color = textColor)
                        Text(
                            if (uiState.ddMusicLinked) {
                                "DD Music: ${uiState.ddMusicSummary ?: "Gebruik DD Music Als Alarm"}"
                            } else {
                                currentRingtoneTitle
                            },
                            modifier = Modifier.padding(bottom = 4.dp),
                            color = textColor.copy(alpha = 0.7f)
                        )

                        // Change sound button only (Play/Stop is in the picker dialog)
                        Button(
                            onClick = {
                                showRingtoneDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(LanguageManager.getString("change_alarm_sound"))
                        }
                    }

                    // 2. Trillen
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(LanguageManager.getString("vibrate"), color = textColor)
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = uiState.vibrate, 
                                onCheckedChange = { viewModel.updateVibrate(it) }, 
                                colors = SwitchDefaults.colors(checkedThumbColor = buttonColor, checkedTrackColor = buttonColor.copy(alpha = 0.5f))
                            )
                        }
                    }

                    // 3. Sluimertijd
                    item {
                        Text("${LanguageManager.getString("snooze_time")}: ${uiState.snoozeMinutes}", color = textColor)
                        Slider(
                            value = uiState.snoozeMinutes.toFloat(),
                            onValueChange = { viewModel.updateSnoozeMinutes(it.roundToInt()) },
                            valueRange = 0f..30f,
                            steps = 29,
                            colors = SliderDefaults.colors(thumbColor = buttonColor, activeTrackColor = buttonColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 4. Alarm Volume
                    item {
                        Text("${LanguageManager.getString("alarm_volume")}: ${uiState.alarmVolume}%", color = textColor)
                        Slider(
                            value = uiState.alarmVolume.toFloat(),
                            onValueChange = { viewModel.updateAlarmVolume(it.toInt()) },
                            valueRange = 0f..100f,
                            steps = 100,
                            colors = SliderDefaults.colors(thumbColor = buttonColor, activeTrackColor = buttonColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        HorizontalDivider(color = textColor.copy(alpha = 0.3f))
                    }

                    // 5. Meldingen (per trigger)
                    item {
                        Button(
                            onClick = {
                                val intent = Intent(context, com.dd.daykit.MeldingenSettingsActivity::class.java).apply {
                                    putExtra("TRIGGER_ID", uiState.triggerId)
                                    putExtra("TRIGGER_NAME", uiState.triggerName)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                        ) {
                            Text(LanguageManager.getString("ka_meldingen"))
                        }
                    }

                    // --- MODE SELECTIE (Bestaande content) ---

                    item {
                         if (uiState.isFirstTimeSetup) {
                            Text(
                                LanguageManager.getString("alarm_mode"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                LanguageManager.getString("alarm_mode"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // OPTIE 1: Simpel alarm (NORMAL — sluimeren instelbaar)
                    item {
                        ModeOptionCard(
                            mode = TriggerBehaviorMode.NORMAL,
                            title = LanguageManager.getString("trigger_snooze_alarm"),
                            description = LanguageManager.getString("trigger_snooze_desc"),
                            isSelected = uiState.selectedMode == TriggerBehaviorMode.NORMAL,
                            textColor = textColor,
                            buttonColor = buttonColor,
                            onClick = { viewModel.selectMode(TriggerBehaviorMode.NORMAL) }
                        ) {
                             if (uiState.selectedMode == TriggerBehaviorMode.NORMAL) {
                                Spacer(Modifier.height(8.dp))
                                
                                // Unlimited Snooze toggle
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { 
                                         viewModel.updateUnlimitedSnooze(!uiState.unlimitedSnooze)
                                    }
                                ) {
                                    Text("Onbeperkt sluimeren", color = textColor, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = uiState.unlimitedSnooze,
                                        onCheckedChange = { viewModel.updateUnlimitedSnooze(it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = buttonColor, checkedTrackColor = buttonColor.copy(alpha = 0.5f))
                                    )
                                }
                                
                                // Snooze count if not unlimited
                                if (!uiState.unlimitedSnooze) {
                                    Spacer(Modifier.height(16.dp))
                                    Text("Aantal keer sluimeren", color = textColor, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(8.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FilledIconButton(
                                            onClick = { if (uiState.snoozeCount > 0) viewModel.updateSnoozeCount(uiState.snoozeCount - 1) },
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = buttonColor.copy(alpha = 0.2f), contentColor = textColor)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Minder")
                                        }
                                        
                                        Text(
                                            text = uiState.snoozeCount.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                        
                                        FilledIconButton(
                                            onClick = { if (uiState.snoozeCount < 20) viewModel.updateSnoozeCount(uiState.snoozeCount + 1) },
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = buttonColor.copy(alpha = 0.2f), contentColor = textColor)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Meer")
                                        }
                                    }
                                }
                             }
                        }
                    }
                    
                    // OPTIE 2: SMART_ALARM
                    item {
                        ModeOptionCard(
                            mode = TriggerBehaviorMode.SMART_ALARM,
                            title = LanguageManager.getString("trigger_smart_alarm"),
                            description = "",
                            isSelected = uiState.selectedMode == TriggerBehaviorMode.SMART_ALARM,
                            textColor = textColor,
                            buttonColor = buttonColor,
                            onClick = { viewModel.selectMode(TriggerBehaviorMode.SMART_ALARM) }
                        ) {
                            // Slim Alarm uitleg en doorverwijzing
                            if (uiState.selectedMode == TriggerBehaviorMode.SMART_ALARM) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = textColor.copy(alpha = 0.2f))
                                Spacer(Modifier.height(12.dp))
                                
                                // Uitleg tekst
                                Text(
                                    text = LanguageManager.getString("trigger_smart_desc"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor.copy(alpha = 0.9f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(Modifier.height(16.dp))
                                
                                // Doorverwijzen knop naar Home Assist instellingen
                                Button(
                                    onClick = {
                                        val intent = Intent(context, com.dd.daykit.HaSettingsActivity::class.java)
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = buttonColor,
                                        contentColor = buttonTextColor
                                    )
                                ) {
                                    Text(LanguageManager.getString("ka_ha"))
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Validatie: check of configuratie compleet is
                // SMART_ALARM mag opgeslagen worden zonder entiteit/value (optioneel)
                val canSave = true // Alle modes kunnen altijd opslaan
                
                // Helper om te checken of SMART_ALARM volledig geconfigureerd is
                val smartAlarmFullyConfigured = when (uiState.selectedMode) {
                    TriggerBehaviorMode.SMART_ALARM -> {
                        val config = uiState.smartConfig
                        config != null && 
                        config.entityId.isNotBlank() && 
                        config.expectedValue.isNotBlank()
                    }
                    else -> true // Andere modes zijn altijd OK
                }
                
                // Waarschuwing als Smart Alarm niet compleet is
                if (uiState.selectedMode == TriggerBehaviorMode.SMART_ALARM && !canSave) {
                    Text(
                        "Kies een entiteit en waarde om op te slaan",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                
                // Opslaan en Annuleren knoppen naast elkaar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Annuleren knop
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textColor
                        )
                    ) {
                        Text(LanguageManager.getString("cancel"))
                    }
                    
                    // Opslaan knop
                    Button(
                        onClick = {
                            scope.launch {
                                // Toon info toast als Slim Alarm zonder entiteit wordt opgeslagen
                                if (uiState.selectedMode == TriggerBehaviorMode.SMART_ALARM && !smartAlarmFullyConfigured) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Slim Alarm opgeslagen. Configureer 'Uit bed check' onder Home Assist Instellingen voor volledige functionaliteit.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                
                                android.util.Log.d("TriggerRules", "Saving config - mode: ${uiState.selectedMode}, smartConfig: ${uiState.smartConfig}")
                                viewModel.saveConfig()
                                onSaveSuccess()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        ),
                        enabled = !uiState.isSaving && canSave
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = buttonTextColor
                            )
                        } else {
                            Text(LanguageManager.getString("save"))
                        }
                    }
                }
                
                // Waarschuwing bij eerste activatie
                if (isFirstActivation) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        LanguageManager.getString("trigger_first_activation_cancel_warning"),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
    
    // Entity selection dialog
    if (showEntitySelectionDialog) {
        EntitySelectionDialog(
            availableEntities = uiState.availableEntities,
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            backgroundColor = backgroundColor,
            onDismiss = { showEntitySelectionDialog = false },
            onEntitySelected = { entityId ->
                val prevent = uiState.smartConfig?.preventManualDismiss ?: false
                val checkHome = uiState.smartConfig?.checkUserAtHome ?: false
                val userEntity = uiState.smartConfig?.userPresenceEntityId ?: ""
                val presenceExpected = uiState.smartConfig?.userPresenceExpectedValue ?: "on"
                // Bewaar de eerder ingestelde verwachte waarde. Hier stond hardcoded "", waardoor
                // elke entiteitkeuze de bijbehorende waarde wiste en de smart-conditie dus nooit
                // kon werken.
                val existingExpected = uiState.smartConfig?.expectedValue.orEmpty()
                viewModel.updateSmartConfig(entityId, existingExpected, prevent, checkHome, userEntity, presenceExpected)
                showEntitySelectionDialog = false
            }
        )
    }
}

@Composable
fun ModeOptionCard(
    mode: TriggerBehaviorMode,
    title: String,
    description: String,
    isSelected: Boolean,
    textColor: Color,
    buttonColor: Color,
    onClick: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null
) {
    val borderColor = if (isSelected) buttonColor else textColor.copy(alpha = 0.3f)
    val backgroundColor = if (isSelected) buttonColor.copy(alpha = 0.1f) else Color.Transparent
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = buttonColor,
                        unselectedColor = textColor.copy(alpha = 0.6f)
                    )
                )
                
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    if (description.isNotEmpty()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Extra content (bijv. entity selectie voor Smart Alarm)
            extraContent?.invoke()
        }
    }
}

@Composable
fun EntitySelectionDialog(
    availableEntities: List<String>,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    backgroundColor: Color,
    onDismiss: () -> Unit,
    onEntitySelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredEntities = remember(searchQuery, availableEntities) {
        if (searchQuery.isBlank()) {
            availableEntities
        } else {
            availableEntities.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = backgroundColor,
        title = {
            Text("Kies een entiteit", color = textColor)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Zoekbalk
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Zoek entiteit...", color = textColor.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = buttonColor,
                        unfocusedBorderColor = textColor.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
                
                Spacer(Modifier.height(12.dp))
                
                // Lijst met entiteiten
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    if (filteredEntities.isEmpty()) {
                        item {
                            Text(
                                "Geen entiteiten gevonden",
                                color = textColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(filteredEntities.size) { index ->
                            val entity = filteredEntities[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEntitySelected(entity) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    entity,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                            }
                            if (index < filteredEntities.size - 1) {
                                HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Handmatige invoer optie
                Text(
                    "Of voer handmatig in:",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
                
                Spacer(Modifier.height(8.dp))
                
                var manualInput by remember { mutableStateOf("") }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        placeholder = { Text("bijv. light.woonkamer", color = textColor.copy(alpha = 0.5f)) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = buttonColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (manualInput.isNotBlank()) {
                                onEntitySelected(manualInput)
                            }
                        },
                        enabled = manualInput.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Bevestig",
                            tint = if (manualInput.isNotBlank()) buttonColor else textColor.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuleren", color = buttonColor)
            }
        }
    )
}
