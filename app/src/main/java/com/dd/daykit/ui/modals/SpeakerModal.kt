package com.dd.daykit.ui.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dd.daykit.LanguageManager
import com.dd.daykit.data.SpeakerContext
import com.dd.daykit.sound.Sound

/**
 * SpeakerModal - External speaker configuration modal (merged with Alarm Backup)
 *
 * Contains:
 * - Speaker selection
 * - Speaker mode selection
 * - Battery usage per hour slider
 * - Volume slider
 * - Sound selection (GitHub sounds list or custom URL)
 * - Test speaker button
 * - Test backup alarm button
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SpeakerModal(
    visible: Boolean,
    availableSpeakers: List<String> = emptyList(),
    initialSelectedSpeaker: String? = null,
    initialSpeakerMode: SpeakerMode = SpeakerMode.BOTH,
    // Welk onderdeel dit is (ALARM/TIMER/WEATHER) - sinds de speaker-splitsing heeft elk zijn
    // eigen speaker/modus/volume/geluid. Bepaalt ook: ALARM toont Backup-modus + "Backup alarm"-
    // teksten, TIMER/WEATHER niet (dat concept bestaat alleen om een gemist ALARM op te vangen bij
    // lege batterij) - en welke twee andere onderdelen de "ook toepassen op..."-melding bij
    // opslaan aanbiedt.
    speakerContext: SpeakerContext = SpeakerContext.ALARM,
    // Alarm backup parameters
    availableLocalSounds: List<Sound> = emptyList(),
    isLoadingSounds: Boolean = false,
    initialBatteryUsage: Int = 5,
    initialVolume: Int = 70,
    initialSkipVolume: Boolean = false,
    initialSelectedSoundId: Long? = null,
    isTesting: Boolean = false,
    testMessage: String? = null,
    // Callbacks
    onDismiss: () -> Unit,
    // applyToOtherContexts = de contexten die de gebruiker in de "ook toepassen op..."-melding
    // heeft aangevinkt (leeg als er niets gewijzigd was, of als de gebruiker alles heeft uitgevinkt).
    onSave: (speaker: String?, mode: SpeakerMode, batteryUsage: Int, volume: Int, skipVolume: Boolean, selectedSoundId: Long?, applyToOtherContexts: Set<SpeakerContext>) -> Unit,
    onTestSpeaker: (String) -> Unit = {},
    onTestBackupAlarm: (volume: Int, skipVolume: Boolean, selectedSoundId: Long?) -> Unit = { _, _, _ -> },
    // Weer-specifieke test: volgt de op dat moment gekozen speaker-modus hierboven (Standaard =
    // HA-speaker, Beide = HA-speaker + telefoon tegelijk) i.p.v. onTestBackupAlarm dat altijd een
    // geluidsbestand op een HA-speaker afspeelt - zie de Geluid-sectie hieronder, die bij Weer
    // niet getoond wordt. Params: de nog-niet-opgeslagen mode/speaker/volume/skipVolume uit deze
    // modal, zodat de test ook werkt vóórdat op "Opslaan" is gedrukt.
    onTestWeatherSpeaker: (mode: SpeakerMode, speakerId: String?, volume: Int, skipVolume: Boolean) -> Unit = { _, _, _, _ -> },
    onBatteryUsageChanged: (Int) -> Unit = {},
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color
) {
    val isAlarmContext = speakerContext == SpeakerContext.ALARM
    // Bij Weer is er geen keuze uit alarmgeluidjes (die worden hier nergens gebruikt) en test de
    // "Mobiel speaker"-knop iets anders (zie onTestWeatherSpeaker hierboven).
    val isWeatherContext = speakerContext == SpeakerContext.WEATHER
    var selectedSpeaker by remember(visible, initialSelectedSpeaker) { mutableStateOf(initialSelectedSpeaker) }
    // Uitgeschakeld is niet meer kiesbaar (overal), en Backup-modus is niet van toepassing
    // buiten de agenda-alarm-context - een oud opgeslagen waarde in die staat wordt hier
    // stilzwijgend naar "Beide" omgezet zodat de radiogroep altijd een geldige selectie toont.
    val coercedInitialMode = remember(visible, initialSpeakerMode, isAlarmContext) {
        when {
            initialSpeakerMode == SpeakerMode.DISABLED -> SpeakerMode.BOTH
            !isAlarmContext && initialSpeakerMode == SpeakerMode.BACKUP -> SpeakerMode.BOTH
            else -> initialSpeakerMode
        }
    }
    var selectedMode by remember(visible, coercedInitialMode) { mutableStateOf(coercedInitialMode) }
    val availableModes = remember(isAlarmContext) {
        SpeakerMode.values().filter { it != SpeakerMode.DISABLED && (isAlarmContext || it != SpeakerMode.BACKUP) }
    }
    // Bij Weer, met "Mobiel speaker" gekozen (selectedSpeaker == null), is er geen HA-speaker om
    // een modus voor te kiezen of instellingen (volume/skip-volume) voor te tonen - die secties
    // hieronder blijven dan helemaal weg i.p.v. nutteloos/verwarrend zichtbaar te zijn.
    val isWeatherPhoneOnly = isWeatherContext && selectedSpeaker == null
    var showConfirmDialog by remember(visible) { mutableStateOf(false) }

    // Alarm backup state
    var batteryUsage by remember(visible, initialBatteryUsage) { mutableStateOf(initialBatteryUsage) }
    var volume by remember(visible, initialVolume) { mutableStateOf(initialVolume) }
    var skipVolume by remember(visible, initialSkipVolume) { mutableStateOf(initialSkipVolume) }
    var selectedSoundId by remember(visible, initialSelectedSoundId) { mutableStateOf(initialSelectedSoundId) }

    val hasChanges = selectedSpeaker != initialSelectedSpeaker ||
            selectedMode != coercedInitialMode ||
            batteryUsage != initialBatteryUsage ||
            volume != initialVolume ||
            skipVolume != initialSkipVolume ||
            selectedSoundId != initialSelectedSoundId

    val handleDismiss = {
        if (hasChanges) {
            showConfirmDialog = true
        } else {
            onDismiss()
        }
    }

    // "Ook toepassen op..."-melding: alleen relevant als er iets gewijzigd is (hasChanges) - bij
    // niets gewijzigd of bij annuleren van de modal zelf (handleDismiss hierboven) verschijnt hij
    // nooit. De andere twee onderdelen staan bij openen standaard aangevinkt.
    val otherContexts = remember(speakerContext) { SpeakerContext.entries.filter { it != speakerContext } }
    var showApplyOtherDialog by remember(visible) { mutableStateOf(false) }
    var checkedOtherContexts by remember(visible, otherContexts) { mutableStateOf(otherContexts.toSet()) }

    val performSave = { applyTo: Set<SpeakerContext> ->
        onSave(selectedSpeaker, selectedMode, batteryUsage, volume, skipVolume, selectedSoundId, applyTo)
    }

    val handleSave = {
        if (hasChanges) {
            checkedOtherContexts = otherContexts.toSet()
            showApplyOtherDialog = true
        } else {
            performSave(emptySet())
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
                        .semantics { paneTitle = "Externe speaker" },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Column(modifier = Modifier.padding(24.dp)) {
                        // Header with title and close button (fixed at top)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = com.dd.daykit.LanguageManager.getString("ha_speaker"),
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
                        
                        // Scrollable content area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                        
                        Text(
                            text = com.dd.daykit.LanguageManager.getString("speaker_description"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Restart app button - voor als speaker selectie niet werkt
                        OutlinedButton(
                            onClick = {
                                // Wis de HA client cache en herlaad de activity
                                com.dd.daykit.network.HomeAssistantClient.clearCache()
                                
                                // Start HaSettingsActivity opnieuw met schone stack
                                val intent = android.content.Intent(context, com.dd.daykit.HaSettingsActivity::class.java)
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                intent.putExtra("open_speaker_modal", true)
                                context.startActivity(intent)
                                
                                // Sluit huidige activity netjes af
                                (context as? android.app.Activity)?.finishAffinity()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = buttonColor
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, buttonColor.copy(alpha = 0.5f))
                        ) {
                            Text(com.dd.daykit.LanguageManager.getString("restart_app"))
                        }
                        
                        Spacer(Modifier.height(16.dp))

                        // Speaker selection
                        Text(
                            text = com.dd.daykit.LanguageManager.getString("select_speaker"),
                            style = MaterialTheme.typography.titleSmall,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(Modifier.height(8.dp))

                        // "Mobiel speaker" als expliciete keuze in de lijst - alleen bij Timer/Weer.
                        // Functioneel identiek aan geen speaker-entiteit kiezen (selectedSpeaker =
                        // null): de HA-speaker wordt dan sowieso niet gebruikt, zie
                        // AlarmOutputDecisionEngine.determineAlarmOutput. Dit maakt die keuze alleen
                        // expliciet zichtbaar i.p.v. impliciet "niets aanvinken".
                        if (!isAlarmContext) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSpeaker = null }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedSpeaker == null,
                                    onClick = { selectedSpeaker = null },
                                    colors = RadioButtonDefaults.colors(selectedColor = buttonColor)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = LanguageManager.getString("ha_phone_speaker_option"),
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = LanguageManager.getString("ha_phone_speaker_option_desc"),
                                        color = textColor.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        if (availableSpeakers.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Red.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = com.dd.daykit.LanguageManager.getString("no_speakers_found"),
                                    color = Color.Red,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                            ) {
                                items(availableSpeakers) { speaker ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedSpeaker = speaker }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        RadioButton(
                                            selected = selectedSpeaker == speaker,
                                            onClick = { selectedSpeaker = speaker },
                                            colors = RadioButtonDefaults.colors(selectedColor = buttonColor)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = speaker,
                                            color = textColor,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        IconButton(
                                            onClick = { onTestSpeaker(speaker) },
                                            enabled = selectedSpeaker == speaker
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Test speaker",
                                                tint = if (selectedSpeaker == speaker) buttonColor else textColor.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // Speaker mode selection - niet bij Weer+Mobiel speaker, zie isWeatherPhoneOnly.
                        if (!isWeatherPhoneOnly) {
                        Text(
                            text = com.dd.daykit.LanguageManager.getString("speaker_mode_label"),
                            style = MaterialTheme.typography.titleSmall,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(8.dp))

                        Column {
                            availableModes.forEach { mode ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedMode = mode }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedMode == mode,
                                        onClick = { selectedMode = mode },
                                        colors = RadioButtonDefaults.colors(selectedColor = buttonColor)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = mode.displayName,
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (mode.description.isNotBlank()) {
                                            Text(
                                                text = mode.description,
                                                color = textColor.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        } // End !isWeatherPhoneOnly (speaker mode)

                        // Alarm backup (volume, sound, test)
                        Spacer(Modifier.height(24.dp))

                        // Backup settings title - "Backup alarm" alleen bij agenda-alarm (het
                        // concept "backup bij gemist alarm" is niet van toepassing bij Timer/Weer).
                        // Bij Timer/Weer zelf geen titel/uitleg meer - overbodig naast de "Mobiel
                        // speaker"-optie hierboven in de speakerlijst.
                        if (isAlarmContext) {
                        Text(
                            text = LanguageManager.getString("ha_alarm_backup"),
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = LanguageManager.getString("ha_alarm_backup_desc"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )

                        Spacer(Modifier.height(24.dp))
                        }

                        // Battery usage per hour - hoeveel procent de telefoon per uur verliest.
                        // Wordt gebruikt om in te schatten of de batterij het volgende alarm haalt;
                        // zo niet, dan geldt de telefoon als "onbetrouwbaar" en mag de HA-backup helpen.
                        // Alleen relevant bij agenda-alarm - Timer/Weer hebben geen "haalt de batterij
                        // het volgende alarm"-concept, dus deze hele sectie blijft daar weg.
                        if (isAlarmContext) {
                        Text(
                            text = LanguageManager.getString("ha_battery_usage_title"),
                            style = MaterialTheme.typography.titleSmall,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = batteryUsage.toFloat(),
                                onValueChange = { batteryUsage = it.toInt() },
                                onValueChangeFinished = { onBatteryUsageChanged(batteryUsage) },
                                enabled = true,
                                valueRange = 1f..100f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = buttonColor,
                                    activeTrackColor = buttonColor,
                                    inactiveTrackColor = textColor.copy(alpha = 0.3f)
                                )
                            )
                            Text(
                                text = "${batteryUsage}%",
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.width(48.dp)
                            )
                        }

                        Text(
                            text = "Test-instelling: zet dit tijdelijk hoog (bv. 90-100%) om te checken of de batterij-check de backup activeert. Later terugzetten naar een realistische waarde (5-10%).",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.65f),
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        Spacer(Modifier.height(24.dp))
                        } // End isAlarmContext (battery usage)

                        // Volume/skip-volume - niet bij Weer+Mobiel speaker: die instellingen gelden
                        // alleen voor de HA-speaker, zie isWeatherPhoneOnly hierboven.
                        if (!isWeatherPhoneOnly) {
                        // Volume niet aanpassen - vlak boven de volume-slider, want die schakelt hij uit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = LanguageManager.getString("ha_skip_volume"),
                                style = MaterialTheme.typography.titleSmall,
                                color = textColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = skipVolume,
                                onCheckedChange = { skipVolume = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = buttonColor,
                                    checkedTrackColor = buttonColor.copy(alpha = 0.5f)
                                )
                            )
                        }
                        Text(
                            text = LanguageManager.getString("ha_skip_volume_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.65f),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        // Volume slider
                        Text(
                            text = LanguageManager.getString("ha_backup_volume"),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (skipVolume) textColor.copy(alpha = 0.4f) else textColor,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = volume.toFloat(),
                                onValueChange = { volume = it.toInt() },
                                enabled = !skipVolume,
                                valueRange = 0f..100f,
                                steps = 9,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = buttonColor,
                                    activeTrackColor = buttonColor,
                                    inactiveTrackColor = textColor.copy(alpha = 0.3f)
                                )
                            )
                            Text(
                                text = "${volume}%",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (skipVolume) textColor.copy(alpha = 0.4f) else textColor,
                                modifier = Modifier.width(48.dp)
                            )
                        }

                        // Volume disclaimer
                        if (!skipVolume) {
                            Text(
                                text = LanguageManager.getString("ha_backup_volume_disclaimer"),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                        } // End !isWeatherPhoneOnly (skip-volume/volume)

                        // Sound selection - niet bij Weer: daar wordt geen geluidsbestand gekozen
                        // of afgespeeld, zie onTestWeatherSpeaker/isWeatherContext hieronder.
                        if (!isWeatherContext) {
                        Text(
                            text = LanguageManager.getString("ha_backup_sound"),
                            style = MaterialTheme.typography.titleSmall,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(8.dp))

                        if (isLoadingSounds) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = buttonColor
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Laden...",
                                    color = textColor.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else if (availableLocalSounds.isEmpty()) {
                            Text(
                                text = LanguageManager.getString("sync_no_sounds"),
                                color = textColor.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column {
                                availableLocalSounds.forEach { sound ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedSoundId = sound.id }
                                            .padding(vertical = 6.dp)
                                    ) {
                                        RadioButton(
                                            selected = selectedSoundId == sound.id,
                                            onClick = { selectedSoundId = sound.id },
                                            colors = RadioButtonDefaults.colors(selectedColor = buttonColor)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = sound.name,
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        } // End !isWeatherContext (sound selection)

                        // Test button: bij Weer een lokale telefoon-tts-test (geen HA, geen
                        // geluidsbestand), bij Agenda-alarm/Timer de bestaande HA-speaker-test.
                        if (isWeatherContext) {
                            Button(
                                onClick = {
                                    onTestWeatherSpeaker(selectedMode, selectedSpeaker, volume, skipVolume)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isTesting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor.copy(alpha = 0.8f),
                                    contentColor = buttonTextColor
                                )
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = buttonTextColor
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(LanguageManager.getString("ha_backup_testing"))
                                } else {
                                    // Knoptekst volgt de gekozen speaker/modus hierboven: Mobiel
                                    // speaker -> alleen telefoon, "Beide" -> HA-speaker + telefoon,
                                    // anders (Standaard) -> alleen HA-speaker. TextAlign.Center + de
                                    // \n in de vertaling zelf (i.p.v. hier hardcoded) houden de
                                    // "(...)"-toevoeging gecentreerd op een eigen regel.
                                    Text(
                                        text = when {
                                            isWeatherPhoneOnly -> LanguageManager.getString("ha_test_weather_speaker_phone")
                                            selectedMode == SpeakerMode.BOTH -> LanguageManager.getString("ha_test_weather_speaker_both")
                                            else -> LanguageManager.getString("ha_test_weather_speaker")
                                        },
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                        Button(
                            onClick = { onTestBackupAlarm(volume, skipVolume, selectedSoundId) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isTesting && !selectedSpeaker.isNullOrBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor.copy(alpha = 0.8f),
                                contentColor = buttonTextColor
                            )
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = buttonTextColor
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(LanguageManager.getString("ha_backup_testing"))
                            } else {
                                Text(LanguageManager.getString(if (isAlarmContext) "ha_test_backup_alarm" else "ha_test_mobile_speaker_only"))
                            }
                        }
                        } // End isWeatherContext test button

                        // Test result message
                        if (testMessage != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = testMessage,
                                color = if (testMessage.contains("Fout") || testMessage.contains("Error")) Color.Red else Color.Green,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        } // End scrollable Column
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Footer buttons - fixed at bottom, outside scrollable area
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

    // "Ook toepassen op..."-melding: verschijnt bij Opslaan mits er echt iets gewijzigd is (zie
    // handleSave hierboven). Terug = niet opslaan, modal blijft open voor verder bewerken.
    // Bevestigen = opslaan voor deze context + de aangevinkte andere context(en).
    if (showApplyOtherDialog) {
        AlertDialog(
            onDismissRequest = { showApplyOtherDialog = false },
            title = { Text(LanguageManager.getString("speaker_apply_other_title"), color = textColor) },
            text = {
                Column {
                    Text(
                        LanguageManager.getString("speaker_apply_other_desc"),
                        color = textColor.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    otherContexts.forEach { ctx ->
                        val label = String.format(
                            LanguageManager.getString("speaker_apply_checkbox_format"),
                            speakerContextDisplayName(ctx)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checkedOtherContexts = if (ctx in checkedOtherContexts) {
                                        checkedOtherContexts - ctx
                                    } else {
                                        checkedOtherContexts + ctx
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = ctx in checkedOtherContexts,
                                onCheckedChange = { checked ->
                                    checkedOtherContexts = if (checked) {
                                        checkedOtherContexts + ctx
                                    } else {
                                        checkedOtherContexts - ctx
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = buttonColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApplyOtherDialog = false
                        performSave(checkedOtherContexts)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(com.dd.daykit.LanguageManager.getString("save"))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showApplyOtherDialog = false },
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

/** Leesbare naam voor een [SpeakerContext], gebruikt in de "ook toepassen op..."-melding. */
private fun speakerContextDisplayName(context: SpeakerContext): String = when (context) {
    SpeakerContext.ALARM -> LanguageManager.getString("screen_agenda_alarm")
    SpeakerContext.TIMER -> LanguageManager.getString("nav_timer")
    SpeakerContext.WEATHER -> LanguageManager.getString("screen_weather")
}

/**
 * Speaker mode enum
 */
enum class SpeakerMode(val key: String, val descKey: String) {
    DISABLED("speaker_mode_disabled", ""),
    STANDARD("speaker_mode_standard", "speaker_mode_standard_desc"),
    BACKUP("speaker_mode_backup", "speaker_mode_backup_desc"),
    BOTH("speaker_mode_both", "speaker_mode_both_desc");
    
    val displayName: String
        get() = com.dd.daykit.LanguageManager.getString(key)
    
    val description: String
        get() = if (descKey.isNotEmpty()) com.dd.daykit.LanguageManager.getString(descKey) else ""
}
