package com.dd.daykit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.data.ExternalSpeakerMode
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.data.SpeakerContext
import com.dd.daykit.data.SpeakerSettings
import com.dd.daykit.network.HomeAssistantClient
import com.dd.daykit.ui.modals.*
import com.dd.daykit.viewmodel.HaSettingsViewModel
import com.dd.daykit.viewmodel.HaSettingsViewModelFactory
import kotlinx.coroutines.launch
import kotlin.math.abs

class HaSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        val storage = HomeAssistantSettingsStorage(applicationContext)
        val client = HomeAssistantClient // Singleton
        val repository = HomeAssistantRepository(client, storage)
        val factory = HaSettingsViewModelFactory(storage, repository)
        
        val viewModel: HaSettingsViewModel by viewModels { factory }
        
        // Check if we should open speaker modal directly
        val openSpeakerModal = intent.getBooleanExtra("open_speaker_modal", false)
        // Timer heeft geen "uit bed" check - die kaart/modal is alleen relevant voor het alarm
        val hideOutOfBed = intent.getBooleanExtra("hide_out_of_bed", false)

        setContent {
            MaterialTheme {
                HaSettingsOverviewScreen(
                    viewModel = viewModel,
                    initialOpenSpeakerModal = openSpeakerModal,
                    hideOutOfBed = hideOutOfBed
                ) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaSettingsOverviewScreen(
    viewModel: HaSettingsViewModel,
    initialOpenSpeakerModal: Boolean = false,
    hideOutOfBed: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // hideOutOfBed is al de bestaande marker voor "dit scherm is via Timer geopend" (zie
    // TimerActivity.kt) - hergebruikt om ook de speaker-modus-opties/teksten aan te passen,
    // want "Backup alarm" (modus + teksten) is een concept dat alleen bij agenda-alarm van
    // toepassing is.
    val isAlarmContext = !hideOutOfBed
    // Dit scherm dient zowel Agenda-alarm als Timer (zie hideOutOfBed hierboven) - nooit Weer,
    // die heeft zijn eigen scherm in WeatherActivity.kt.
    val speakerContext = if (isAlarmContext) SpeakerContext.ALARM else SpeakerContext.TIMER

    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val containerColor = Color(SettingsManager.getBackgroundColor(context))
    
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionTestState.collectAsState()
    val sensorCheckState by viewModel.sensorCheckState.collectAsState()

    // Modal visibility states
    var showUrlsModal by remember { mutableStateOf(false) }
    var showTokenModal by remember { mutableStateOf(false) }
    var showEntitiesModal by remember { mutableStateOf(false) }
    var showSpeakerModal by remember { mutableStateOf(initialOpenSpeakerModal) }
    var showPresenceModal by remember { mutableStateOf(false) }
    var showOutOfBedModal by remember { mutableStateOf(false) }
    var showAlarmScriptModal by remember { mutableStateOf(false) }
    var showTimerScriptModal by remember { mutableStateOf(false) }
    var showPasteModal by remember { mutableStateOf(false) }
    var showPairModal by remember { mutableStateOf(false) }
    var scannedPairingPayload by remember {
        mutableStateOf<com.dd.daykit.homeassistant.PairingClient.PairingPayload?>(null)
    }

    val backupAlarmTestState by viewModel.backupAlarmTestState.collectAsState()
    val qrPairingState by viewModel.qrPairingState.collectAsState()

    // QR-scanner (setup-code koppelflow) - camera-permissie + zxing scanner
    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val parsed = com.dd.daykit.homeassistant.PairingClient.parseScannedPayload(result.contents)
            if (parsed != null) {
                scannedPairingPayload = parsed
            } else {
                android.widget.Toast.makeText(context, "QR-code niet herkend als AgendaAlarm-koppelcode", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val launchQrScan = {
        qrScanLauncher.launch(
            ScanOptions().apply {
                setPrompt("Scan de QR-code in Home Assistant")
                setBeepEnabled(false)
                setOrientationLocked(true)
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            }
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchQrScan()
        } else {
            android.widget.Toast.makeText(context, "Camera-toestemming nodig om te scannen", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val onScanQrClick = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchQrScan()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        SettingsManager.saveLastVisitedKalenderSubSettingsPage(context, "HA")
        // Auto-initialize Home Assistant connection when settings page opens
        viewModel.applyHomeAssistantSettings(SettingsManager.getBatteryUsagePerHour(context))
    }

    // Opnieuw kijken of er in HA iets gewijzigd is zodra dit scherm weer op de voorgrond komt.
    // De typische volgorde is namelijk: app open laten staan -> in Home Assistant iets aanpassen
    // -> terug naar de app. Zonder deze her-controle bleef de melding uit tot je het scherm
    // helemaal opnieuw opende, en leek een net gemaakte HA-wijziging dus niet aan te komen.
    // De eerste ON_RESUME wordt overgeslagen: die valt samen met de LaunchedEffect hierboven,
    // die de check zelf al uitvoert (stap 4c van applyHomeAssistantSettings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var skipFirstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipFirstResume) skipFirstResume = false else viewModel.checkForHaEntityUpdate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppBackground(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Single scrollable column - Terug button at bottom of scroll, not sticky
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = LanguageManager.getString("ha_settings_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Plakken and Export buttons at TOP
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showPasteModal = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) {
                        Text(LanguageManager.getString("ha_paste"))
                    }
                    Button(
                        onClick = {
                            // Export settings to clipboard and file
                            val exportText = com.dd.daykit.viewmodel.generateExportText(
                                uiState = uiState,
                                batteryUsage = SettingsManager.getBatteryUsagePerHour(context),
                                speakerContext = speakerContext
                            )
                            // Copy to clipboard
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("HA Settings", exportText)
                            clipboardManager.setPrimaryClip(clip)
                            // Show toast
                            android.widget.Toast.makeText(context, "Settings gekopieerd naar klembord", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) {
                        Text(LanguageManager.getString("ha_export"))
                    }
                }
            }
                    // URLs Card
                    item {
                        SettingCard(
                            title = LanguageManager.getString("ha_urls"),
                            description = "${uiState.baseUrls.filter { it.isNotBlank() }.size} ${LanguageManager.getString("ha_urls_configured")}",
                            onClick = { showUrlsModal = true },
                            textColor = textColor,
                            buttonColor = buttonColor
                        )
                    }
                    
                    // Koppelen via QR/setup-code Card - snelweg i.p.v. handmatig token kopieren/plakken.
                    // Toont "Verbonden" + ontkoppel-knop zodra er al een geldig token + actieve
                    // HA-verbinding is; anders de gewone scan/code-invoer-flow.
                    val isPaired = uiState.longLivedToken.isNotBlank() && !uiState.activeBaseUrl.isNullOrBlank()
                    item {
                        if (isPaired) {
                            PairedCard(
                                activeBaseUrl = uiState.activeBaseUrl!!,
                                onDisconnect = { viewModel.clearPairing() },
                                textColor = textColor,
                                buttonColor = buttonColor,
                                buttonTextColor = buttonTextColor
                            )
                        } else {
                            SettingCard(
                                title = "Koppelen (QR/code)",
                                description = "Scan een QR-code of typ een setup-code over vanuit Home Assistant - geen token kopieren nodig",
                                onClick = {
                                    scannedPairingPayload = null
                                    viewModel.clearQrPairingMessage()
                                    showPairModal = true
                                },
                                textColor = textColor,
                                buttonColor = buttonColor
                            )
                        }
                    }

                    // Token Card - alleen als fallback voor wie liever handmatig een token plakt;
                    // verdwijnt zodra er al gekoppeld is (via QR/code of handmatig token).
                    if (!isPaired) {
                        item {
                            SettingCard(
                                title = LanguageManager.getString("ha_token"),
                                description = if (uiState.longLivedToken.isNotBlank()) LanguageManager.getString("ha_token_configured") else LanguageManager.getString("ha_not_configured"),
                                onClick = { showTokenModal = true },
                                textColor = textColor,
                                buttonColor = buttonColor
                            )
                        }
                    }
                    
                    // Entities Card
                    item {
                        SettingCard(
                            title = LanguageManager.getString("ha_entities"),
                            description = LanguageManager.getString("ha_entities_add_desc"),
                            onClick = { showEntitiesModal = true },
                            textColor = textColor,
                            buttonColor = buttonColor
                        )
                    }

                    // Externe speaker (samengevoegd met alarm-backup geluid / volume / duur)
                    item {
                        SettingCard(
                            title = LanguageManager.getString("ha_speaker"),
                            description = LanguageManager.getString("ha_speaker_desc"),
                            onClick = { showSpeakerModal = true },
                            textColor = textColor,
                            buttonColor = buttonColor
                        )
                    }
                    
                    // Presence Card - alleen-lezen live status (thuisdetectie staat altijd aan,
                    // geen aan/uit-schakelaar meer, zie AlarmOutputDecisionEngine.isUserAtHome()'s
                    // fail-safe + tryAutoAdoptPresenceEntity). Tikken opent de picker voor het
                    // randgeval dat de automatisch gekozen bron overschreven moet worden.
                    item {
                        val presenceStatusText = when (uiState.presenceIsHome) {
                            true -> LanguageManager.getString("ha_presence_status_true")
                            false -> LanguageManager.getString("ha_presence_status_false")
                            null -> LanguageManager.getString("ha_presence_status_unknown")
                        }
                        val presenceSourceText = uiState.selectedPresenceEntityId?.takeIf { it.isNotBlank() }
                            ?: LanguageManager.getString("ha_presence_automatic")
                        SettingCard(
                            title = "${LanguageManager.getString("ha_presence_status_label")}: $presenceStatusText",
                            description = "$presenceSourceText  •  ${LanguageManager.getString("ha_presence_change")}",
                            onClick = { showPresenceModal = true },
                            textColor = textColor,
                            buttonColor = buttonColor,
                            // Status kan ten onrechte op "onbekend" OF confident "onwaar" staan
                            // (bv. vlak na een instellingen-save: de ping-sensor is dan net
                            // herladen en nog "unavailable" totdat de eerste ping binnen is, zie
                            // refreshPresenceStatus()) - dus altijd tonen, niet alleen bij
                            // "onbekend". Rechts i.p.v. links van de titel.
                            trailingContent = {
                                IconButton(
                                    onClick = { viewModel.refreshPresenceStatus() },
                                    enabled = !uiState.isLoadingPresenceStatus
                                ) {
                                    if (uiState.isLoadingPresenceStatus) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = buttonColor, strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = LanguageManager.getString("ha_presence_refresh"),
                                            tint = buttonColor
                                        )
                                    }
                                }
                            }
                        )
                    }
                    
                    // Out of Bed Check Card - niet relevant voor timer (geen "uit bed" concept)
                    if (!hideOutOfBed) {
                        item {
                            // Live status direct zichtbaar in de titel - zelfde patroon als de
                            // Presence Card hierboven ("Thuis: Waar/Onwaar/Onbekend").
                            val outOfBedStatusText = when {
                                !uiState.outOfBedCheckEnabled -> LanguageManager.getString("ha_out_of_bed_status_off")
                                uiState.outOfBedIsInBed == true -> LanguageManager.getString("ha_presence_status_true")
                                uiState.outOfBedIsInBed == false -> LanguageManager.getString("ha_out_of_bed_status_false")
                                else -> LanguageManager.getString("ha_presence_status_unknown")
                            }
                            SettingCard(
                                title = "${LanguageManager.getString("ha_out_of_bed_status_label")}: $outOfBedStatusText",
                                // Presence-detectie staat altijd aan (geen aan/uit meer), dus geen
                                // "vereist eerst thuis-detectie"-waarschuwing meer nodig hier.
                                description = LanguageManager.getString("ha_out_of_bed_desc"),
                                onClick = { showOutOfBedModal = true },
                                textColor = textColor,
                                buttonColor = buttonColor,
                                // Zelfde valkuil als de presence-sensor: vlak na een instellingen-
                                // save is de HA-entity soms nog "unavailable" totdat de eerste
                                // update binnen is - ververs-knop als achtervanger, alleen zinvol
                                // als de check ook daadwerkelijk aan staat.
                                trailingContent = if (uiState.outOfBedCheckEnabled) {
                                    {
                                        IconButton(
                                            onClick = { viewModel.refreshOutOfBedStatus() },
                                            enabled = !uiState.isLoadingOutOfBedStatus
                                        ) {
                                            if (uiState.isLoadingOutOfBedStatus) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = buttonColor, strokeWidth = 2.dp)
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = LanguageManager.getString("ha_presence_refresh"),
                                                    tint = buttonColor
                                                )
                                            }
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                    
                    // Script bij alarm Card - alleen op Agenda Alarm-scherm (niet vanuit Timer geopend)
                    if (!hideOutOfBed) {
                        item {
                            SettingCard(
                                title = LanguageManager.getString("ha_script_alarm"),
                                description = if (uiState.alarmScriptEnabled && !uiState.alarmScriptEntityId.isNullOrBlank()) {
                                    uiState.alarmScriptEntityId!!
                                } else {
                                    LanguageManager.getString("ha_script_alarm_desc")
                                },
                                onClick = { showAlarmScriptModal = true },
                                textColor = textColor,
                                buttonColor = buttonColor
                            )
                        }
                    }

                    // Script bij timer Card - alleen op Timer-scherm
                    if (hideOutOfBed) {
                        item {
                            SettingCard(
                                title = LanguageManager.getString("ha_script_timer"),
                                description = if (uiState.timerScriptEnabled && !uiState.timerScriptEntityId.isNullOrBlank()) {
                                    uiState.timerScriptEntityId!!
                                } else {
                                    LanguageManager.getString("ha_script_timer_desc")
                                },
                                onClick = { showTimerScriptModal = true },
                                textColor = textColor,
                                buttonColor = buttonColor
                            )
                        }
                    }

                    // Geluiden Sync Card
                    item {
                        com.dd.daykit.ui.components.SoundSyncButton(
                            textColor = textColor,
                            buttonColor = buttonColor,
                            buttonTextColor = buttonTextColor,
                            containerColor = containerColor
                        )
                    }
                    
                    // Test buttons - horizontal layout (no extra spacer - use default 12dp from verticalArrangement)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            // Test Connection Button
                            Button(
                                onClick = { viewModel.onTestConnectionClicked() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor.copy(alpha = 0.8f),
                                    contentColor = buttonTextColor
                                ),
                                enabled = !connectionState.isTesting
                            ) {
                                if (connectionState.isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = buttonTextColor
                                    )
                                } else {
                                    Text(LanguageManager.getString("ha_test_connection"))
                                }
                            }
                            
                            // Test Entities Button
                            Button(
                                onClick = { viewModel.onTestSensorsClicked() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor.copy(alpha = 0.8f),
                                    contentColor = buttonTextColor
                                ),
                                enabled = !sensorCheckState.isChecking
                            ) {
                                if (sensorCheckState.isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = buttonTextColor
                                    )
                                } else {
                                    Text(LanguageManager.getString("ha_test_entities"))
                                }
                            }
                        }
                    }
                    
                    // Test results
                    if (connectionState.lastMessage != null) {
                        item {
                            Text(
                                text = connectionState.lastMessage!!,
                                color = if (connectionState.lastMessage!!.contains("Fout")) Color.Red else Color.Green,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    
                    if (sensorCheckState.results.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    sensorCheckState.results.forEach { result ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Icon(
                                                if (result.exists) Icons.Default.CheckCircle else Icons.Default.Error,
                                                contentDescription = null,
                                                tint = if (result.exists) Color.Green else Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    result.entityId,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = textColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (result.exists) {
                                                    Text(
                                                        "State: ${result.state}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = textColor.copy(alpha = 0.7f)
                                                    )
                                                } else {
                                                    Text(
                                                        "Fout: ${result.errorMessage}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.Red.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                        if (result != sensorCheckState.results.last()) {
                                            HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
            // Terug button - at bottom of scroll content, not sticky
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("back"))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // "HA-update gevonden"-melding: wijzigingen die in Home Assistant zelf zijn gedaan (via
    // Configureren > Entities, Alarm/Timer/Weer-speaker, Aanwezigheid, Uit bed, Scripts of
    // Defaults). Bewust HIER, náást de LazyColumn en niet als item erin: een LazyColumn stelt
    // items buiten beeld niet samen, dus zodra je ook maar een stukje scrollde verdween de
    // dialoog weer - precies op het moment dat je 'm wilde beantwoorden. Als los broertje van de
    // lijst blijft hij staan tot je Ja of Nee kiest, net als de andere modals hieronder.
    HaEntityUpdateCheckSection(
        viewModel = viewModel,
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    // Modals
    UrlsModal(
        visible = showUrlsModal,
        initialUrls = uiState.baseUrls,
        onDismiss = { showUrlsModal = false },
        onSave = { urls ->
            // Replace all URLs at once to avoid state mutation issues
            viewModel.replaceAllUrls(urls)
            viewModel.saveSettings() // Persist changes immediately
            showUrlsModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )
    
    TokenModal(
        visible = showTokenModal,
        initialToken = uiState.longLivedToken,
        onDismiss = { showTokenModal = false },
        onSave = { token ->
            viewModel.updateToken(token)
            viewModel.saveSettings() // Persist changes immediately
            showTokenModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    LaunchedEffect(qrPairingState) {
        if (showPairModal && !qrPairingState.isPairing && !qrPairingState.isError && !qrPairingState.lastMessage.isNullOrBlank()) {
            showPairModal = false
            scannedPairingPayload = null
            viewModel.clearQrPairingMessage()
        }
    }

    PairModal(
        visible = showPairModal,
        initialBaseUrl = uiState.activeBaseUrl ?: uiState.baseUrls.firstOrNull { it.isNotBlank() } ?: "",
        scannedPayload = scannedPairingPayload,
        isPairing = qrPairingState.isPairing,
        statusMessage = qrPairingState.lastMessage,
        statusIsError = qrPairingState.isError,
        onDismiss = {
            showPairModal = false
            scannedPairingPayload = null
        },
        onScanQrClick = onScanQrClick,
        onPair = { url, code ->
            viewModel.pairWithSetupCode(url, code, SettingsManager.getBatteryUsagePerHour(context))
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )
    
    EntitiesModal(
        visible = showEntitiesModal,
        availableEntities = uiState.entities,
        initialSelected = uiState.entities.filter { it.isNotBlank() },
        onDismiss = { showEntitiesModal = false },
        onSave = { entities ->
            android.util.Log.d("HaSettingsActivity", "Saving entities from modal: ${entities.size} item(s)")
            viewModel.replaceAllEntities(entities)
            viewModel.saveSettings() // Persist changes immediately
            viewModel.loadMediaPlayers()
            viewModel.loadPresenceEntities()
            showEntitiesModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    LaunchedEffect(showSpeakerModal) {
        if (showSpeakerModal) {
            viewModel.loadLocalSounds()
        }
    }

    val speakerForModal = when (speakerContext) {
        SpeakerContext.ALARM -> uiState.alarmSpeaker
        SpeakerContext.TIMER -> uiState.timerSpeaker
        SpeakerContext.WEATHER -> uiState.weatherSpeaker
    }
    SpeakerModal(
        visible = showSpeakerModal,
        availableSpeakers = uiState.availableMediaPlayers.map { it.entityId },
        initialSelectedSpeaker = speakerForModal.entityId,
        initialSpeakerMode = when (speakerForModal.mode) {
            ExternalSpeakerMode.DISABLED -> SpeakerMode.DISABLED
            ExternalSpeakerMode.DEFAULT -> SpeakerMode.STANDARD
            ExternalSpeakerMode.BACKUP_ONLY -> SpeakerMode.BACKUP
            ExternalSpeakerMode.BOTH -> SpeakerMode.BOTH
        },
        availableLocalSounds = uiState.availableLocalSounds,
        isLoadingSounds = uiState.isLoadingSounds,
        initialBatteryUsage = SettingsManager.getBatteryUsagePerHour(context),
        initialVolume = speakerForModal.volume,
        initialSkipVolume = speakerForModal.skipVolume,
        initialSelectedSoundId = speakerForModal.soundId,
        speakerContext = speakerContext,
        isTesting = backupAlarmTestState.isTesting,
        testMessage = backupAlarmTestState.lastMessage,
        onDismiss = { showSpeakerModal = false },
        onSave = { speaker, mode, batteryUsage, volume, skipVolume, selectedSoundId, applyToOtherContexts ->
            val externalMode = when (mode) {
                SpeakerMode.DISABLED -> ExternalSpeakerMode.DISABLED
                SpeakerMode.STANDARD -> ExternalSpeakerMode.DEFAULT
                SpeakerMode.BACKUP -> ExternalSpeakerMode.BACKUP_ONLY
                SpeakerMode.BOTH -> ExternalSpeakerMode.BOTH
            }
            SettingsManager.saveBatteryUsagePerHour(context, batteryUsage)
            viewModel.syncBatteryUsageToHomeAssistant(batteryUsage)
            // Slaat op voor speakerContext + eventueel aangevinkte andere onderdelen (de "ook
            // toepassen op..."-melding in SpeakerModal) in 1 keer, en persisteert direct.
            viewModel.saveSpeakerSettings(
                speakerContext,
                SpeakerSettings(
                    entityId = speaker,
                    mode = externalMode,
                    volume = volume,
                    skipVolume = skipVolume,
                    soundId = selectedSoundId
                ),
                applyToOtherContexts
            )
            showSpeakerModal = false
        },
        onTestSpeaker = { speakerId ->
            viewModel.selectSpeaker(speakerContext, speakerId)
            viewModel.testSpeaker(context, speakerContext)
        },
        onTestBackupAlarm = { volume, skipVolume, selectedSoundId ->
            viewModel.testBackupAlarmWithCurrentState(speakerContext, volume, skipVolume, selectedSoundId)
        },
        onBatteryUsageChanged = { batteryUsage ->
            SettingsManager.saveBatteryUsagePerHour(context, batteryUsage)
            viewModel.syncBatteryUsageToHomeAssistant(batteryUsage)
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )
    
    PresenceModal(
        visible = showPresenceModal,
        availablePresenceEntities = uiState.availablePresenceEntities.map { it.entityId },
        initialSelectedEntity = uiState.selectedPresenceEntityId,
        onDismiss = { showPresenceModal = false },
        onSave = { entity ->
            // entity == null -> terug naar automatisch (ping-sensor/person., zie
            // tryAutoAdoptPresenceEntity) - er is bewust geen "uit"-stand meer.
            // selectPresenceEntityAndSave() i.p.v. losse select+save+refresh-aanroepen: wacht de
            // save af en geeft de ping-sensor daarna wat tijd om weer te reageren (elke save
            // herlaadt de HA-integratie, zie de uitleg daar).
            viewModel.selectPresenceEntityAndSave(entity ?: "")
            showPresenceModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    LaunchedEffect(showAlarmScriptModal, showTimerScriptModal) {
        if (showAlarmScriptModal || showTimerScriptModal) {
            viewModel.loadScripts()
        }
    }

    ScriptModal(
        visible = showAlarmScriptModal,
        title = LanguageManager.getString("ha_script_alarm"),
        description = LanguageManager.getString("ha_script_alarm_desc"),
        enableLabel = LanguageManager.getString("ha_script_enable"),
        entityLabel = LanguageManager.getString("ha_select_script_entity"),
        availableScripts = uiState.availableScripts.map { it.entityId },
        initialSelectedEntity = uiState.alarmScriptEntityId,
        initialEnabled = uiState.alarmScriptEnabled,
        initialIgnorePresence = uiState.alarmScriptIgnorePresence,
        onDismiss = { showAlarmScriptModal = false },
        onSave = { entity, enabled, ignorePresence ->
            viewModel.setAlarmScriptEnabled(enabled)
            if (enabled && entity != null) {
                viewModel.selectAlarmScript(entity)
            }
            viewModel.setAlarmScriptIgnorePresence(ignorePresence)
            viewModel.saveSettings()
            showAlarmScriptModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    ScriptModal(
        visible = showTimerScriptModal,
        title = LanguageManager.getString("ha_script_timer"),
        description = LanguageManager.getString("ha_script_timer_desc"),
        enableLabel = LanguageManager.getString("ha_script_enable"),
        entityLabel = LanguageManager.getString("ha_select_script_entity"),
        availableScripts = uiState.availableScripts.map { it.entityId },
        initialSelectedEntity = uiState.timerScriptEntityId,
        initialEnabled = uiState.timerScriptEnabled,
        initialIgnorePresence = uiState.timerScriptIgnorePresence,
        onDismiss = { showTimerScriptModal = false },
        onSave = { entity, enabled, ignorePresence ->
            viewModel.setTimerScriptEnabled(enabled)
            if (enabled && entity != null) {
                viewModel.selectTimerScript(entity)
            }
            viewModel.setTimerScriptIgnorePresence(ignorePresence)
            viewModel.saveSettings()
            showTimerScriptModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    if (!hideOutOfBed) {
    // isPresenceEnabled/onEnablePresenceClick bewust niet meer meegegeven (defaults true/null):
    // presence-detectie staat altijd aan (geen aan/uit meer, zie de Presence Card hierboven),
    // dus uit-bed-check hoeft daar niet meer los op te wachten/gaten.
    OutOfBedModal(
        visible = showOutOfBedModal,
        availableEntities = uiState.entities.filter { it.isNotBlank() },
        initialSelectedEntity = uiState.outOfBedEntityId,
        initialExpectedValue = uiState.outOfBedExpectedValue,
        initialEnabled = uiState.outOfBedCheckEnabled,
        onDismiss = { showOutOfBedModal = false },
        onSave = { entity, value, enabled ->
            // saveOutOfBedSettings() i.p.v. losse set+save-aanroepen: wacht de save af en geeft
            // de HA-entity daarna wat tijd om weer te reageren (elke save herlaadt de
            // HA-integratie, zie de uitleg daar en bij selectPresenceEntityAndSave).
            viewModel.saveOutOfBedSettings(entity, value, enabled)
            showOutOfBedModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )
    }

    // Paste Modal for bulk import
    PasteModal(
        visible = showPasteModal,
        onDismiss = { showPasteModal = false },
        onParsed = { parseResult ->
            // Alles wat er in de geplakte tekst stond toepassen - gedeeld met WeatherActivity,
            // zodat beide schermen exact dezelfde velden overnemen.
            applyHaPasteResult(
                context = context,
                viewModel = viewModel,
                uiState = uiState,
                speakerContext = speakerContext,
                parseResult = parseResult
            )

            // Save settings first
            viewModel.saveSettings()
            
            // Wis cache
            com.dd.daykit.network.HomeAssistantClient.clearCache()
            
            // Apply settings (initialize connection, validate entities, etc.)
            viewModel.applyHomeAssistantSettings(SettingsManager.getBatteryUsagePerHour(context))
            
            // Close modal and stay on page (no restart needed anymore)
            showPasteModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )
}

/**
 * Past een geplakte HA-export toe op de ViewModel - gedeeld door HaSettingsActivity (Agenda-alarm/
 * Timer) en WeatherActivity (Weer).
 *
 * Bewust één functie voor beide schermen: die twee hadden een bijna identiek, met de hand
 * gekopieerd blok, waardoor een veld dat aan de ene kant werd toegevoegd aan de andere kant bleef
 * ontbreken. Alles wat hier ontbreekt, gaat bij exporteren+plakken verloren.
 *
 * [speakerContext] is het onderdeel van het scherm waar geplakt wordt; dat krijgt de ongeprefixte
 * `Speaker:`/`SpeakerModus:`/`Volume:`-waarden uit oudere exports. Bevat de tekst de nieuwe
 * `SpeakerAlarm*`/`SpeakerTimer*`/`SpeakerWeer*`-sleutels, dan worden alle drie de onderdelen
 * gezet en heeft die per-scherm-toewijzing verder geen effect meer.
 *
 * Slaat zelf niet op; de aanroeper doet dat één keer aan het eind.
 */
fun applyHaPasteResult(
    context: android.content.Context,
    viewModel: com.dd.daykit.viewmodel.HaSettingsViewModel,
    uiState: com.dd.daykit.viewmodel.HaSettingsUiState,
    speakerContext: SpeakerContext,
    parseResult: HaPasteResult
) {
    fun parseMode(raw: String): com.dd.daykit.data.ExternalSpeakerMode? =
        when (raw.trim().lowercase()) {
            "off", "uit", "disabled" -> com.dd.daykit.data.ExternalSpeakerMode.DISABLED
            "standard", "standaard", "default" -> com.dd.daykit.data.ExternalSpeakerMode.DEFAULT
            "backup", "backup_only" -> com.dd.daykit.data.ExternalSpeakerMode.BACKUP_ONLY
            "both", "beide" -> com.dd.daykit.data.ExternalSpeakerMode.BOTH
            else -> null
        }

    // URLs (samenvoegen met bestaande, geen dubbelen)
    if (parseResult.urls.isNotEmpty()) {
        val existingUrls = uiState.baseUrls.filter { it.isNotBlank() }.toMutableSet()
        existingUrls.addAll(parseResult.urls)
        viewModel.replaceAllUrls(existingUrls.toList())
    }
    if (parseResult.token.isNotBlank()) {
        viewModel.updateToken(parseResult.token)
    }
    if (parseResult.entities.isNotEmpty()) {
        val existingEntities = uiState.entities.filter { it.isNotBlank() }.toMutableSet()
        existingEntities.addAll(parseResult.entities)
        viewModel.replaceAllEntities(existingEntities.toList())
    }

    // Aanwezigheid
    if (parseResult.atHomeEntity.isNotBlank()) {
        viewModel.selectPresenceEntity(parseResult.atHomeEntity)
        if (parseResult.atHomeValue.isNotBlank()) {
            viewModel.setPresenceExpectedState(parseResult.atHomeValue)
        }
    }

    // Uit-bed-check. De aan/uit-schakelaar staat sinds kort los in de export ("InBedCheck"), zodat
    // "check aan zonder gekozen entiteit" en "check uit met entiteit" allebei bewaard blijven.
    val hasPresenceAfterImport = parseResult.atHomeEntity.isNotBlank() ||
        !uiState.selectedPresenceEntityId.isNullOrBlank()
    val wantsOutOfBed = parseResult.outOfBedCheckEnabled
        ?: parseResult.inBedEntity.takeIf { it.isNotBlank() }?.let { true }
    if (wantsOutOfBed != null) {
        if (wantsOutOfBed && !hasPresenceAfterImport) {
            android.widget.Toast.makeText(
                context,
                LanguageManager.getString("ha_out_of_bed_requires_presence"),
                android.widget.Toast.LENGTH_LONG
            ).show()
        } else {
            viewModel.setOutOfBedCheckEnabled(wantsOutOfBed)
        }
    }
    if (parseResult.inBedEntity.isNotBlank()) {
        viewModel.selectOutOfBedEntity(parseResult.inBedEntity)
        if (parseResult.inBedValue.isNotBlank()) {
            viewModel.setOutOfBedExpectedValue(parseResult.inBedValue)
        }
    }

    // Scripts. De aan/uit-vlag komt nu apart mee; ontbreekt die (oude export), dan geldt zoals
    // voorheen: een meegegeven script-entiteit betekent "aan".
    if (!parseResult.alarmScriptEntityId.isNullOrBlank()) {
        viewModel.selectAlarmScript(parseResult.alarmScriptEntityId)
    }
    (parseResult.alarmScriptEnabled ?: parseResult.alarmScriptEntityId?.takeIf { it.isNotBlank() }?.let { true })
        ?.let { viewModel.setAlarmScriptEnabled(it) }
    viewModel.setAlarmScriptIgnorePresence(parseResult.alarmScriptIgnorePresence)

    if (!parseResult.timerScriptEntityId.isNullOrBlank()) {
        viewModel.selectTimerScript(parseResult.timerScriptEntityId)
    }
    (parseResult.timerScriptEnabled ?: parseResult.timerScriptEntityId?.takeIf { it.isNotBlank() }?.let { true })
        ?.let { viewModel.setTimerScriptEnabled(it) }
    viewModel.setTimerScriptIgnorePresence(parseResult.timerScriptIgnorePresence)

    // Speaker van het huidige scherm (oude, ongeprefixte sleutels).
    if (parseResult.speakerEntity.isNotBlank()) {
        viewModel.selectSpeaker(speakerContext, parseResult.speakerEntity)
    }
    parseMode(parseResult.speakerMode)?.let { viewModel.setSpeakerMode(speakerContext, it) }
    parseResult.volume?.let { viewModel.setBackupVolume(speakerContext, it) }
    parseResult.skipVolume?.let { viewModel.setSkipBackupVolume(speakerContext, it) }

    // Alle drie de speakers uit de nieuwe, geprefixte sleutels. Staat er niets over een onderdeel
    // in de tekst, dan blijft dat onderdeel ongemoeid.
    listOf(
        SpeakerContext.ALARM to parseResult.alarmSpeaker,
        SpeakerContext.TIMER to parseResult.timerSpeaker,
        SpeakerContext.WEATHER to parseResult.weatherSpeaker
    ).forEach { (target, parsed) ->
        if (parsed.isEmpty()) return@forEach
        viewModel.applyImportedSpeaker(target) { current ->
            var updated = current
            // Lege string is betekenisvol: "geen speaker gekozen".
            parsed.entityId?.let { raw -> updated = updated.copy(entityId = raw.takeIf { it.isNotBlank() }) }
            parseMode(parsed.mode)?.let { updated = updated.copy(mode = it) }
            parsed.volume?.let { updated = updated.copy(volume = it) }
            parsed.skipVolume?.let { updated = updated.copy(skipVolume = it) }
            // Geluid op naam terugzoeken: de ID is toestel-lokaal, de naam is wat beide kanten
            // delen. Niet gevonden = laat het huidige geluid staan.
            if (parsed.soundName.isNotBlank()) {
                uiState.availableLocalSounds.find { it.name.equals(parsed.soundName, ignoreCase = true) }
                    ?.let { sound -> updated = updated.copy(soundId = sound.id) }
            }
            updated
        }
    }

    // Batterijgebruik (staat in SettingsManager, niet in de HA-instellingen)
    parseResult.batteryUsage?.let { SettingsManager.saveBatteryUsagePerHour(context, it) }

    // Duur/interval van het HA-backup-alarm
    val totalDuration = ((parseResult.alarmDurationMinutes ?: 0) * 60) +
        (parseResult.alarmDurationSeconds ?: 0)
    if (totalDuration > 0) {
        viewModel.setBackupAlarmDuration(totalDuration)
    }

    // Overige losse velden zonder eigen setter
    viewModel.applyImportedHaExtras(
        activeBaseUrl = parseResult.activeUrl.takeIf { it.isNotBlank() },
        showOnlyLinkedEntities = parseResult.showOnlyLinkedEntities,
        weatherTtsEnabled = parseResult.weatherTtsEnabled,
        notifyService = parseResult.notifyService,
        safetyTimeoutSeconds = parseResult.safetyTimeoutSeconds
    )
}

/**
 * Data class holding all parsed paste results
 * Extended format supports:
 * - URL/URLs: Home Assistant base URLs
 * - Token: Long-lived access token
 * - Entities: Entity IDs
 * - AtHome: Presence entity with value
 * - InBed: Out of bed entity with value
 * - Speaker: External speaker entity
 * - SpeakerModus: Off/Standard/Backup/Both
 * - Battery: 0-20 (battery usage per hour)
 * - Volume: 0-100 (backup alarm volume)
 * - Minutes: 0-10 (alarm duration minutes)
 * - Seconds: 0-55 (alarm duration seconds)
 * - BackupAlarm: Sound name or URL
 * - CustomUrl: Custom sound URL
 */
data class HaPasteResult(
    val urls: List<String> = emptyList(),
    val token: String = "",
    val entities: List<String> = emptyList(),
    val atHomeEntity: String = "",
    val atHomeValue: String = "",
    val inBedEntity: String = "",
    val inBedValue: String = "",
    val speakerEntity: String = "",
    val speakerMode: String = "", // Off, Standard, Backup, Both
    val useCustomSound: Boolean? = null, // Whether to use custom sound URL
    val batteryUsage: Int? = null, // 0-20
    val volume: Int? = null, // 0-100
    val alarmDurationMinutes: Int? = null, // 0-10
    val alarmDurationSeconds: Int? = null, // 0-55
    val backupAlarmSound: String = "", // Sound name or URL
    val customSoundUrl: String = "",
    val alarmScriptEntityId: String? = null, // Script entity voor "script bij alarm", indien herkend
    val timerScriptEntityId: String? = null, // Script entity voor "script bij timer", indien herkend
    val alarmScriptIgnorePresence: Boolean = true, // Default true: backwards compatible met oude exports zonder dit veld
    val timerScriptIgnorePresence: Boolean = true,
    // --- Velden hieronder zijn toegevoegd toen bleek dat de export/import maar een deel van de
    // HA-instellingen dekte. Allemaal nullable: null betekent "stond niet in de geplakte tekst",
    // en dan blijft de bestaande waarde staan i.p.v. dat een oude export 'm op de default zet.
    /** Welke van [urls] de actieve verbinding is. */
    val activeUrl: String = "",
    val showOnlyLinkedEntities: Boolean? = null,
    /** Weeralarmen laten uitspreken op de weer-speaker (HomeAssistantSettings.weatherTtsEnabled). */
    val weatherTtsEnabled: Boolean? = null,
    /** Uit-bed-check aan/uit, los van of er een entiteit bij gekozen is. */
    val outOfBedCheckEnabled: Boolean? = null,
    val alarmScriptEnabled: Boolean? = null,
    val timerScriptEnabled: Boolean? = null,
    val notifyService: String? = null,
    val safetyTimeoutSeconds: Int? = null,
    /** Volume ongemoeid laten ("Volume: onveranderd gelaten") - werd vroeger als onleesbaar getal genegeerd. */
    val skipVolume: Boolean? = null,
    // De drie per-onderdeel speakers. Een export bevat ze sinds kort allemaal; oudere exports
    // hebben alleen de ongeprefixte Speaker:/SpeakerModus:/Volume:-sleutels hierboven, en dan
    // blijven deze drie leeg (= niets overschrijven).
    val alarmSpeaker: HaPasteSpeaker = HaPasteSpeaker(),
    val timerSpeaker: HaPasteSpeaker = HaPasteSpeaker(),
    val weatherSpeaker: HaPasteSpeaker = HaPasteSpeaker()
)

/**
 * Speaker-instellingen van één onderdeel zoals ze in een geplakte tekst kunnen staan
 * (`SpeakerAlarm:`/`SpeakerTimer:`/`SpeakerWeer:` en varianten). Alles nullable/leeg als default:
 * wat niet in de tekst stond, wordt bij het toepassen niet aangeraakt.
 */
data class HaPasteSpeaker(
    val entityId: String? = null,
    val mode: String = "",
    val volume: Int? = null,
    val skipVolume: Boolean? = null,
    val soundName: String = ""
) {
    /** True als deze tekst helemaal niets over dit onderdeel zei. */
    fun isEmpty(): Boolean =
        entityId == null && mode.isBlank() && volume == null && skipVolume == null && soundName.isBlank()
}

@Composable
fun PasteModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    onParsed: (HaPasteResult) -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    var pasteText by remember(visible) { mutableStateOf("") }
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    
    // Functie om clipboard te lezen en direct te importeren
    val pasteFromClipboard = {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val clipText = clipData.getItemAt(0).text?.toString() ?: ""
            if (clipText.isNotBlank()) {
                val result = parseHaPasteText(clipText)
                onParsed(result)
            }
        }
    }
    
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(LanguageManager.getString("ha_paste"), color = textColor) },
            text = {
                Column {
                    Text(
                        LanguageManager.getString("ha_paste_description"),
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        label = { Text(LanguageManager.getString("ha_paste_hint"), color = textColor.copy(alpha = 0.7f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = buttonColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.5f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = buttonColor
                        ),
                        maxLines = 20
                    )
                }
            },
            confirmButton = {
                // Show different button based on whether text field has content
                if (pasteText.isBlank()) {
                    // Empty field: show "Paste from clipboard" button
                    Button(
                        onClick = { pasteFromClipboard() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) {
                        Text(LanguageManager.getString("ha_paste_clipboard"))
                    }
                } else {
                    // Has text: show "Import" button
                    Button(
                        onClick = {
                            val result = parseHaPasteText(pasteText)
                            onParsed(result)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) {
                        Text(LanguageManager.getString("ha_paste_import"))
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = onDismiss,
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
}

/**
 * Parse structured text for Home Assistant configuration
 * 
 * Supported sections and key:value pairs:
 * - URL: / URLs: - Home Assistant base URLs (multi-line)
 * - Token: - Long-lived access token
 * - Entities: / Entity: - Entity IDs (one per line)
 * - AtHome: - Presence entity with expected value (format: entity_id:value)
 * - InBed: - Out of bed entity with expected value (format: entity_id:value)
 * - Speaker: media_player.xxx - External speaker entity
 * - SpeakerModus: Off/Standard/Backup/Both - Speaker mode
 * - Battery: 0-20 - Battery usage per hour
 * - Volume: 0-100 - Backup alarm volume
 * - Minutes: 0-10 - Alarm duration minutes
 * - Seconds: 0-55 - Alarm duration seconds
 * - BackupAlarm: Sound name or URL
 * - CustomUrl: Custom sound URL
 * 
 * Example:
 * URL:
 * http://192.168.1.56:8123
 * 
 * Token:
 * eyJ0eXAiOiJKV1Q...
 * 
 * Entities:
 * binary_sensor.example
 * media_player.speaker
 * 
 * Speaker: media_player.woonkamer
 * SpeakerModus: Backup
 * Volume: 80
 * Minutes: 1
 * Seconds: 30
 * 
 * AtHome:
 * binary_sensor.user_home:home
 * 
 * InBed:
 * binary_sensor.user_in_bed:on
 */
private fun parseHaPasteText(text: String): HaPasteResult {
    val urls = mutableListOf<String>()
    var token = ""
    val entities = mutableSetOf<String>() // Use Set to avoid duplicates
    var atHomeEntity = ""
    var atHomeValue = ""
    var inBedEntity = ""
    var inBedValue = ""
    var speakerEntity = ""
    var speakerMode = ""
    var batteryUsage: Int? = null
    var volume: Int? = null
    var alarmDurationMinutes: Int? = null
    var alarmDurationSeconds: Int? = null
    var backupAlarmSound = ""
    var customSoundUrl = ""
    var useCustomSound: Boolean? = null
    var alarmScriptEntityId: String? = null
    var timerScriptEntityId: String? = null
    var alarmScriptIgnorePresence = true
    var timerScriptIgnorePresence = true
    var activeUrl = ""
    var showOnlyLinkedEntities: Boolean? = null
    var weatherTtsEnabled: Boolean? = null
    var outOfBedCheckEnabled: Boolean? = null
    var alarmScriptEnabled: Boolean? = null
    var timerScriptEnabled: Boolean? = null
    var notifyService: String? = null
    var safetyTimeoutSeconds: Int? = null
    var skipVolume: Boolean? = null
    var alarmSpeaker = HaPasteSpeaker()
    var timerSpeaker = HaPasteSpeaker()
    var weatherSpeaker = HaPasteSpeaker()

    // "true"/"1"/"ja"/"aan"/"on" tellen allemaal als aan - een export schrijft "true", maar
    // gebruikers plakken ook met de hand samengestelde tekst.
    fun parseBool(value: String): Boolean =
        value.trim().lowercase() in setOf("true", "1", "ja", "aan", "on", "yes")

    // "Volume: onveranderd gelaten" betekent "laat het speakervolume met rust". Dat viel vroeger
    // door de mand als een getal dat niet te lezen was, waarna de instelling gewoon wegviel.
    fun isSkipVolumeText(value: String): Boolean =
        value.trim().lowercase().let { it.startsWith("onveranderd") || it == "skip" || it == "unchanged" }

    var currentSection = ""
    
    // Defensive parsing - handle null/empty gracefully
    if (text.isBlank()) {
        return HaPasteResult()
    }
    
    try {
        text.lines().forEach { line ->
            // Trim and skip empty lines
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            
            // Check for section headers (case-insensitive)
            val lowerTrimmed = trimmed.lowercase()
            
            // First check for key:value pairs on single line
            if (trimmed.contains(":") && !lowerTrimmed.startsWith("http")) {
                val colonIndex = trimmed.indexOf(':')
                val key = trimmed.substring(0, colonIndex).trim().lowercase()
                val value = trimmed.substring(colonIndex + 1).trim()
                
                when (key) {
                    "speaker" -> {
                        if (value.startsWith("media_player.")) {
                            speakerEntity = value
                            entities.add(value) // Also add to entities
                        }
                        return@forEach
                    }
                    "speakermodus", "speaker_modus", "speakermode", "speaker_mode" -> {
                        speakerMode = value
                        return@forEach
                    }
                    "battery", "batterij" -> {
                        batteryUsage = value.toIntOrNull()?.coerceIn(0, 20)
                        return@forEach
                    }
                    "volume" -> {
                        if (isSkipVolumeText(value)) {
                            skipVolume = true
                        } else {
                            value.toIntOrNull()?.let {
                                volume = it.coerceIn(0, 100)
                                skipVolume = false
                            }
                        }
                        return@forEach
                    }
                    "activeurl", "active_url", "actieveurl", "actieve_url" -> {
                        if (value.startsWith("http")) activeUrl = value
                        return@forEach
                    }
                    "toonalleengekoppeld", "toon_alleen_gekoppeld", "showonlylinked", "show_only_linked_entities" -> {
                        showOnlyLinkedEntities = parseBool(value)
                        return@forEach
                    }
                    "uitspreken", "weathertts", "weather_tts", "weather_tts_enabled", "speak" -> {
                        weatherTtsEnabled = parseBool(value)
                        return@forEach
                    }
                    "inbedcheck", "inbed_check", "in_bed_check", "outofbedcheck", "out_of_bed_check_enabled" -> {
                        outOfBedCheckEnabled = parseBool(value)
                        return@forEach
                    }
                    "scriptalarmaan", "script_alarm_aan", "alarmscriptenabled", "alarm_script_enabled" -> {
                        alarmScriptEnabled = parseBool(value)
                        return@forEach
                    }
                    "scripttimeraan", "script_timer_aan", "timerscriptenabled", "timer_script_enabled" -> {
                        timerScriptEnabled = parseBool(value)
                        return@forEach
                    }
                    "notifyservice", "notify_service" -> {
                        if (value.isNotBlank()) notifyService = value
                        return@forEach
                    }
                    "safetytimeout", "safety_timeout", "safety_timeout_seconds" -> {
                        safetyTimeoutSeconds = value.toIntOrNull()
                        return@forEach
                    }
                    // Per-onderdeel speakers. Een lege waarde is betekenisvol ("geen speaker
                    // gekozen"), dus die wordt als lege string bewaard en niet overgeslagen.
                    "speakeralarm", "speaker_alarm" -> {
                        alarmSpeaker = alarmSpeaker.copy(entityId = value)
                        if (value.startsWith("media_player.")) entities.add(value)
                        return@forEach
                    }
                    "speakeralarmmodus", "speaker_alarm_modus", "speakeralarmmode" -> {
                        alarmSpeaker = alarmSpeaker.copy(mode = value)
                        return@forEach
                    }
                    "speakeralarmvolume", "speaker_alarm_volume" -> {
                        alarmSpeaker = if (isSkipVolumeText(value)) {
                            alarmSpeaker.copy(skipVolume = true)
                        } else {
                            value.toIntOrNull()?.let { alarmSpeaker.copy(volume = it.coerceIn(0, 100), skipVolume = false) } ?: alarmSpeaker
                        }
                        return@forEach
                    }
                    "speakeralarmgeluid", "speaker_alarm_geluid", "speakeralarmsound" -> {
                        alarmSpeaker = alarmSpeaker.copy(soundName = value)
                        return@forEach
                    }
                    "speakertimer", "speaker_timer" -> {
                        timerSpeaker = timerSpeaker.copy(entityId = value)
                        if (value.startsWith("media_player.")) entities.add(value)
                        return@forEach
                    }
                    "speakertimermodus", "speaker_timer_modus", "speakertimermode" -> {
                        timerSpeaker = timerSpeaker.copy(mode = value)
                        return@forEach
                    }
                    "speakertimervolume", "speaker_timer_volume" -> {
                        timerSpeaker = if (isSkipVolumeText(value)) {
                            timerSpeaker.copy(skipVolume = true)
                        } else {
                            value.toIntOrNull()?.let { timerSpeaker.copy(volume = it.coerceIn(0, 100), skipVolume = false) } ?: timerSpeaker
                        }
                        return@forEach
                    }
                    "speakertimergeluid", "speaker_timer_geluid", "speakertimersound" -> {
                        timerSpeaker = timerSpeaker.copy(soundName = value)
                        return@forEach
                    }
                    "speakerweer", "speaker_weer", "speakerweather", "speaker_weather" -> {
                        weatherSpeaker = weatherSpeaker.copy(entityId = value)
                        if (value.startsWith("media_player.")) entities.add(value)
                        return@forEach
                    }
                    "speakerweermodus", "speaker_weer_modus", "speakerweathermode" -> {
                        weatherSpeaker = weatherSpeaker.copy(mode = value)
                        return@forEach
                    }
                    "speakerweervolume", "speaker_weer_volume", "speakerweathervolume" -> {
                        weatherSpeaker = if (isSkipVolumeText(value)) {
                            weatherSpeaker.copy(skipVolume = true)
                        } else {
                            value.toIntOrNull()?.let { weatherSpeaker.copy(volume = it.coerceIn(0, 100), skipVolume = false) } ?: weatherSpeaker
                        }
                        return@forEach
                    }
                    "speakerweergeluid", "speaker_weer_geluid", "speakerweathersound" -> {
                        weatherSpeaker = weatherSpeaker.copy(soundName = value)
                        return@forEach
                    }
                    "minutes", "minuten" -> {
                        alarmDurationMinutes = value.toIntOrNull()?.coerceIn(0, 10)
                        return@forEach
                    }
                    "seconds", "seconden" -> {
                        // 0..59, niet 0..55: de export schrijft de echte restseconden van
                        // backupAlarmDuration, en 56-59 werd anders stilzwijgend naar 55 getrokken.
                        alarmDurationSeconds = value.toIntOrNull()?.coerceIn(0, 59)
                        return@forEach
                    }
                    "backupalarm", "backup_alarm", "alarm" -> {
                        backupAlarmSound = value
                        return@forEach
                    }
                    "customurl", "custom_url", "eigen_url", "custom" -> {
                        customSoundUrl = value
                        return@forEach
                    }
                    "usecustomsound", "use_custom_sound", "usecustom" -> {
                        useCustomSound = value.lowercase() == "true" || value == "1"
                        return@forEach
                    }
                    "scriptalarm", "script_alarm", "script alarm", "alarmscript", "alarm_script" -> {
                        if (value.contains(".")) {
                            alarmScriptEntityId = value
                        }
                        return@forEach
                    }
                    "scripttimer", "script_timer", "script timer", "timerscript", "timer_script" -> {
                        if (value.contains(".")) {
                            timerScriptEntityId = value
                        }
                        return@forEach
                    }
                    "scriptalarmaltijduit", "scriptalarmaltijd", "scriptalarmalways", "alarmscriptalwaysrun",
                    "script_alarm_altijd", "script_alarm_always", "alarm_script_always_run" -> {
                        alarmScriptIgnorePresence = value.lowercase() == "true" || value == "1"
                        return@forEach
                    }
                    "scripttimeraltijduit", "scripttimeraltijd", "scripttimeralways", "timerscriptalwaysrun",
                    "script_timer_altijd", "script_timer_always", "timer_script_always_run" -> {
                        timerScriptIgnorePresence = value.lowercase() == "true" || value == "1"
                        return@forEach
                    }
                }
            }
            
            // Check for section headers
            when {
                lowerTrimmed == "url:" || lowerTrimmed == "urls:" -> {
                    currentSection = "url"
                    return@forEach
                }
                lowerTrimmed == "token:" -> {
                    currentSection = "token"
                    return@forEach
                }
                lowerTrimmed == "entities:" || lowerTrimmed == "entity:" -> {
                    currentSection = "entities"
                    return@forEach
                }
                lowerTrimmed == "athome:" || lowerTrimmed == "at home:" || lowerTrimmed == "thuis:" -> {
                    currentSection = "athome"
                    return@forEach
                }
                lowerTrimmed == "inbed:" || lowerTrimmed == "in bed:" || lowerTrimmed == "bed:" -> {
                    currentSection = "inbed"
                    return@forEach
                }
            }
            
            // Process content based on current section
            when (currentSection) {
                "url" -> {
                    // Only add valid URLs
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        urls.add(trimmed)
                    }
                }
                "token" -> {
                    // Only set token once (first non-empty line)
                    if (token.isBlank() && trimmed.isNotBlank()) {
                        token = trimmed
                    }
                }
                "entities" -> {
                    // Validate entity format: must contain dot, not be URL, not be empty
                    val cleanEntity = trimmed.trimEnd(',', ';') // Remove trailing commas/semicolons
                    if (cleanEntity.contains(".") && 
                        !cleanEntity.startsWith("http") && 
                        cleanEntity.isNotBlank()) {
                        entities.add(cleanEntity)
                        // Automatisch media_player entities als speaker detecteren (als nog niet expliciet gezet)
                        if (speakerEntity.isBlank() && cleanEntity.startsWith("media_player.")) {
                            speakerEntity = cleanEntity
                        }
                    }
                }
                "athome" -> {
                    // Format: entity_id:expected_value
                    if (atHomeEntity.isBlank() && trimmed.contains(":")) {
                        val parts = trimmed.split(":", limit = 2)
                        if (parts.size == 2 && parts[0].contains(".")) {
                            atHomeEntity = parts[0].trim()
                            atHomeValue = parts[1].trim()
                        }
                    } else if (atHomeEntity.isBlank() && trimmed.contains(".")) {
                        // Just entity without value
                        atHomeEntity = trimmed
                    }
                }
                "inbed" -> {
                    // Format: entity_id:expected_value
                    if (inBedEntity.isBlank() && trimmed.contains(":")) {
                        val parts = trimmed.split(":", limit = 2)
                        if (parts.size == 2 && parts[0].contains(".")) {
                            inBedEntity = parts[0].trim()
                            inBedValue = parts[1].trim()
                        }
                    } else if (inBedEntity.isBlank() && trimmed.contains(".")) {
                        // Just entity without value
                        inBedEntity = trimmed
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Defensive: return whatever we parsed so far, don't crash
        android.util.Log.e("HaPasteParser", "Error parsing paste text", e)
    }
    
    return HaPasteResult(
        urls = urls.toList(),
        token = token,
        entities = entities.toList(),
        atHomeEntity = atHomeEntity,
        atHomeValue = atHomeValue,
        inBedEntity = inBedEntity,
        inBedValue = inBedValue,
        speakerEntity = speakerEntity,
        speakerMode = speakerMode,
        batteryUsage = batteryUsage,
        volume = volume,
        alarmDurationMinutes = alarmDurationMinutes,
        alarmDurationSeconds = alarmDurationSeconds,
        backupAlarmSound = backupAlarmSound,
        customSoundUrl = customSoundUrl,
        useCustomSound = useCustomSound,
        alarmScriptEntityId = alarmScriptEntityId,
        timerScriptEntityId = timerScriptEntityId,
        alarmScriptIgnorePresence = alarmScriptIgnorePresence,
        timerScriptIgnorePresence = timerScriptIgnorePresence,
        activeUrl = activeUrl,
        showOnlyLinkedEntities = showOnlyLinkedEntities,
        weatherTtsEnabled = weatherTtsEnabled,
        outOfBedCheckEnabled = outOfBedCheckEnabled,
        alarmScriptEnabled = alarmScriptEnabled,
        timerScriptEnabled = timerScriptEnabled,
        notifyService = notifyService,
        safetyTimeoutSeconds = safetyTimeoutSeconds,
        skipVolume = skipVolume,
        alarmSpeaker = alarmSpeaker,
        timerSpeaker = timerSpeaker,
        weatherSpeaker = weatherSpeaker
    )
}

@Composable
fun PairedCard(
    activeBaseUrl: String,
    onDisconnect: () -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, buttonColor.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Verbonden",
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = activeBaseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor.copy(alpha = 0.8f),
                        contentColor = buttonTextColor
                    )
                ) {
                    Text("Ontkoppelen")
                }
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    textColor: Color,
    buttonColor: Color,
    // Optioneel, links van de titel/omschrijving te tonen - niet als klik-target op de hele
    // kaart, maar als eigen klikbaar element, zodat 'm indrukken niet ook de kaart zelf opent.
    leadingContent: (@Composable () -> Unit)? = null,
    // Optioneel, rechts van de titel/omschrijving te tonen (bv. de ververs-knop bij de
    // Thuis-detectie-kaart) - zelfde reden als leadingContent: eigen klikbaar element, los van
    // de kaart-brede onClick.
    trailingContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, buttonColor.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(4.dp))
                trailingContent()
            }
        }
    }
}

/**
 * Toont alleen iets als de automatische "is er in HA zelf iets gewijzigd?"-check (die al bij het
 * openen van de HA-instellingenpagina's op de achtergrond draait, zie
 * [HaSettingsViewModel.applyHomeAssistantSettings]'s stap 4c) daadwerkelijk een nieuwe entiteit
 * vindt. Geen knop, geen "niets gevonden"/foutmelding meer - een stille achtergrondcheck die bij
 * elk bezoek opnieuw draait, hoort niet elke keer een toast te geven als er toevallig niets te
 * melden is; alleen een écht verschil is de moeite van een melding waard. Haalt alleen op en toont
 * een Ja/Nee-bevestiging; past nooit iets automatisch toe. Gedeeld tussen HaSettingsOverviewScreen
 * en WeatherHomeAssistantSettingsPage (zelfde package, geen import nodig) zodat de check overal
 * waar de Entiteiten-kaart staat beschikbaar is.
 */
@Composable
fun HaEntityUpdateCheckSection(
    viewModel: HaSettingsViewModel,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    val state by viewModel.haEntityUpdateState.collectAsState()

    if (state.hasChanges) {
        // Welke veld-wijzigingen (naast de altijd-mee-overgenomen nieuwe entiteiten) de gebruiker
        // wil overnemen - standaard allemaal aangevinkt, zelfde patroon als de "ook toepassen
        // op..."-checkboxen in SpeakerModal. Herinitialiseert zodra er een nieuwe check-uitkomst
        // binnenkomt (state.fieldDiffs als remember-key).
        var checkedFieldKeys by remember(state.fieldDiffs) {
            mutableStateOf(state.fieldDiffs.map { it.key }.toSet())
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissHaEntityUpdate() },
            containerColor = containerColor,
            title = {
                Text(
                    text = LanguageManager.getString("ha_update_title"),
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.newEntities.isNotEmpty()) {
                        Text(
                            text = LanguageManager.getString("ha_update_new_entities"),
                            color = textColor.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        state.newEntities.forEach { entityId ->
                            Text(
                                text = entityId,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (state.fieldDiffs.isNotEmpty()) {
                        Text(
                            text = LanguageManager.getString("ha_update_changed_settings"),
                            color = textColor.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        state.fieldDiffs.forEach { diff ->
                            // Vinkje links, tekst gecentreerd: de checkbox uit de leesvolgorde
                            // halen zou 'm onvindbaar maken, maar de regels zelf lezen wel als
                            // gecentreerde tekst.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checkedFieldKeys = if (diff.key in checkedFieldKeys) {
                                            checkedFieldKeys - diff.key
                                        } else {
                                            checkedFieldKeys + diff.key
                                        }
                                    }
                            ) {
                                Checkbox(
                                    checked = diff.key in checkedFieldKeys,
                                    onCheckedChange = { checked ->
                                        checkedFieldKeys = if (checked) checkedFieldKeys + diff.key else checkedFieldKeys - diff.key
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = buttonColor)
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = diff.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "${diff.localValue} → ${diff.haValue}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = LanguageManager.getString("ha_update_take_over"),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.applyHaEntityUpdate(checkedFieldKeys) },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                ) { Text(LanguageManager.getString("ha_update_apply")) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissHaEntityUpdate() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                ) { Text(LanguageManager.getString("answer_no")) }
            }
        )
    }
}
