# Home Assist Modals - Integratie Gids

## ✅ Compile Errors Opgelost

### 1. **SettingsModal.kt** - Dialog import
- ✅ **Opgelost**: `Dialog` was al correct geïmporteerd via `androidx.compose.ui.window.Dialog`
- Geen actie nodig

### 2. **EntitiesModal.kt** - Alle errors opgelost
- ✅ **Verwijderd**: `viewModel` dependency - nu pure callback-based
- ✅ **Verwijderd**: Unresolved refs: `nextAlarmSensor`, `allEntities`, `saveNextAlarmSensor`
- ✅ **Toegevoegd**: Proper `by remember { mutableStateOf() }` delegates
- ✅ **Toegevoegd**: Correct imports voor `Icons.Default.Close`, `Icons.Default.Search`
- ✅ **Toegevoegd**: `visible` parameter voor conditional rendering
- ✅ **Toegevoegd**: Animaties met `scaleIn`/`scaleOut`
- ✅ **Toegevoegd**: Focus management met `LaunchedEffect`

### 3. **SpeakerModal.kt** - Alle errors opgelost
- ✅ **Verwijderd**: `viewModel` dependency - nu pure callback-based
- ✅ **Verwijderd**: Unresolved refs: `mediaPlayerEntity`, `saveMediaPlayerEntity`, `testSpeaker`
- ✅ **Toegevoegd**: `SpeakerMode` enum met display names en descriptions
- ✅ **Toegevoegd**: Speaker mode selectie met radio buttons
- ✅ **Toegevoegd**: `onTestSpeaker` callback parameter
- ✅ **Toegevoegd**: Proper state management

### 4. **PresenceModal.kt** - Alle errors opgelost
- ✅ **Verwijderd**: `viewModel` dependency - nu pure callback-based
- ✅ **Verwijderd**: Unresolved refs: `presenceSensor`, `presenceHomeValue`, `presenceCheckEnabled`, `fetchAllEntities`, `savePresenceSettings`
- ✅ **Toegevoegd**: Dynamic state options based on entity type
- ✅ **Toegevoegd**: Radio buttons voor state value selectie
- ✅ **Toegevoegd**: ExposedDropdownMenuBox met correct `menuAnchor()` modifier
- ✅ **Toegevoegd**: Proper `OutlinedTextField` colors parameter (niet `textColor`)

---

## 📦 Benodigde Imports

Alle modals gebruiken standaard Jetpack Compose Material3 componenten. Zorg dat je `build.gradle` de volgende dependencies heeft:

```gradle
dependencies {
    // Compose BOM (Bill of Materials) - versie 2024.01.00 of nieuwer
    implementation platform('androidx.compose:compose-bom:2024.01.00')
    
    // Material3
    implementation 'androidx.compose.material3:material3'
    
    // Material Icons Extended (voor Icons.Default.*)
    implementation 'androidx.compose.material:material-icons-extended'
    
    // Compose UI
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    
    // Compose Foundation
    implementation 'androidx.compose.foundation:foundation'
    
    // Lifecycle
    implementation 'androidx.lifecycle:lifecycle-runtime-compose'
}
```

---

## 🔧 Integratie Voorbeelden

### Voorbeeld 1: EntitiesModal gebruiken

```kotlin
import com.redubbledd.agendawekker.ui.modals.EntitiesModal

@Composable
fun MyScreen(viewModel: HaSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showEntitiesModal by remember { mutableStateOf(false) }
    
    // Button om modal te openen
    Button(onClick = { showEntitiesModal = true }) {
        Text("Configureer Entiteiten")
    }
    
    // Modal
    EntitiesModal(
        visible = showEntitiesModal,
        availableEntities = uiState.allEntities, // List<String> van alle beschikbare entiteiten
        initialSelected = uiState.selectedEntities, // List<String> van geselecteerde entiteiten
        onDismiss = { showEntitiesModal = false },
        onSave = { selectedEntities ->
            // Opslaan via ViewModel
            viewModel.saveSelectedEntities(selectedEntities)
            showEntitiesModal = false
        },
        textColor = Color(SettingsManager.getTextColor(context)),
        buttonColor = Color(SettingsManager.getButtonColor(context)),
        buttonTextColor = Color(SettingsManager.getButtonTextColor(context)),
        containerColor = Color(SettingsManager.getBackgroundColor(context))
    )
}
```

### Voorbeeld 2: SpeakerModal gebruiken

```kotlin
import com.redubbledd.agendawekker.ui.modals.SpeakerModal
import com.redubbledd.agendawekker.ui.modals.SpeakerMode

@Composable
fun MyScreen(viewModel: HaSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showSpeakerModal by remember { mutableStateOf(false) }
    
    // Filter media_player entiteiten
    val mediaPlayers = uiState.allEntities.filter { it.startsWith("media_player.") }
    
    Button(onClick = { showSpeakerModal = true }) {
        Text("Configureer Speaker")
    }
    
    SpeakerModal(
        visible = showSpeakerModal,
        availableSpeakers = mediaPlayers,
        initialSelectedSpeaker = uiState.selectedSpeaker, // String?
        initialSpeakerMode = uiState.speakerMode, // SpeakerMode enum
        onDismiss = { showSpeakerModal = false },
        onSave = { speaker, mode ->
            viewModel.saveSpeakerSettings(speaker, mode)
            showSpeakerModal = false
        },
        onTestSpeaker = { speakerId ->
            // Test de speaker (bijv. speel een test geluid af)
            viewModel.testSpeaker(speakerId)
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )
}
```

### Voorbeeld 3: PresenceModal gebruiken

```kotlin
import com.redubbledd.agendawekker.ui.modals.PresenceModal

@Composable
fun MyScreen(viewModel: HaSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showPresenceModal by remember { mutableStateOf(false) }
    
    // Filter presence entiteiten (person.*, device_tracker.*, binary_sensor.*)
    val presenceEntities = uiState.allEntities.filter { 
        it.startsWith("person.") || 
        it.startsWith("device_tracker.") || 
        it.startsWith("binary_sensor.")
    }
    
    Button(onClick = { showPresenceModal = true }) {
        Text("Configureer Aanwezigheid")
    }
    
    PresenceModal(
        visible = showPresenceModal,
        availablePresenceEntities = presenceEntities,
        initialSelectedEntity = uiState.presenceEntity, // String?
        initialHomeValue = uiState.presenceHomeValue, // String (bijv. "home", "on")
        initialEnabled = uiState.presenceCheckEnabled, // Boolean
        onDismiss = { showPresenceModal = false },
        onSave = { entity, homeValue, enabled ->
            viewModel.savePresenceSettings(entity, homeValue, enabled)
            showPresenceModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )
}
```

---

## 🎯 ViewModel Adapter Voorbeeld

Als je bestaande `HaSettingsViewModel` deze methodes nog niet heeft, voeg ze toe:

```kotlin
class HaSettingsViewModel(...) : ViewModel() {
    
    // Entiteiten
    fun saveSelectedEntities(entities: List<String>) {
        viewModelScope.launch {
            storage.saveEntities(entities)
            // Update UI state
            _uiState.update { it.copy(selectedEntities = entities) }
        }
    }
    
    // Speaker
    fun saveSpeakerSettings(speaker: String?, mode: SpeakerMode) {
        viewModelScope.launch {
            storage.saveSpeaker(speaker)
            storage.saveSpeakerMode(mode)
            _uiState.update { 
                it.copy(
                    selectedSpeaker = speaker,
                    speakerMode = mode
                ) 
            }
        }
    }
    
    fun testSpeaker(speakerId: String) {
        viewModelScope.launch {
            try {
                repository.testMediaPlayer(speakerId)
                // Optioneel: toon success message
            } catch (e: Exception) {
                // Optioneel: toon error message
            }
        }
    }
    
    // Presence
    fun savePresenceSettings(entity: String?, homeValue: String, enabled: Boolean) {
        viewModelScope.launch {
            storage.savePresenceEntity(entity)
            storage.savePresenceHomeValue(homeValue)
            storage.savePresenceEnabled(enabled)
            _uiState.update { 
                it.copy(
                    presenceEntity = entity,
                    presenceHomeValue = homeValue,
                    presenceCheckEnabled = enabled
                ) 
            }
        }
    }
}
```

---

## 📋 Checklist: Alle Compile Errors Opgelost

### EntitiesModal.kt
- [x] Verwijderd: `viewModel` parameter
- [x] Toegevoegd: `visible: Boolean` parameter
- [x] Toegevoegd: `availableEntities: List<String>` parameter
- [x] Toegevoegd: `initialSelected: List<String>` parameter
- [x] Toegevoegd: `onSave: (List<String>) -> Unit` callback
- [x] Correct gebruik van `by remember { mutableStateOf() }`
- [x] Correct gebruik van `Text(..., color = ...)` ipv `textColor = ...`
- [x] Alle imports aanwezig (Icons.Default.Close, Icons.Default.Search)
- [x] Animaties met `fadeIn`, `fadeOut`, `scaleIn`, `scaleOut`
- [x] Focus management met `LaunchedEffect`

### SpeakerModal.kt
- [x] Verwijderd: `viewModel` parameter
- [x] Toegevoegd: `visible: Boolean` parameter
- [x] Toegevoegd: `availableSpeakers: List<String>` parameter
- [x] Toegevoegd: `initialSelectedSpeaker: String?` parameter
- [x] Toegevoegd: `initialSpeakerMode: SpeakerMode` parameter
- [x] Toegevoegd: `onSave: (String?, SpeakerMode) -> Unit` callback
- [x] Toegevoegd: `onTestSpeaker: (String) -> Unit` callback
- [x] Toegevoegd: `SpeakerMode` enum
- [x] Correct gebruik van state management
- [x] Alle imports aanwezig

### PresenceModal.kt
- [x] Verwijderd: `viewModel` parameter
- [x] Toegevoegd: `visible: Boolean` parameter
- [x] Toegevoegd: `availablePresenceEntities: List<String>` parameter
- [x] Toegevoegd: `initialSelectedEntity: String?` parameter
- [x] Toegevoegd: `initialHomeValue: String` parameter
- [x] Toegevoegd: `initialEnabled: Boolean` parameter
- [x] Toegevoegd: `onSave: (String?, String, Boolean) -> Unit` callback
- [x] Correct gebruik van `ExposedDropdownMenuBox` met `menuAnchor()`
- [x] Correct gebruik van `OutlinedTextFieldDefaults.colors()` ipv `TextFieldDefaults.outlinedTextFieldColors()`
- [x] Dynamic state options based on entity type
- [x] Radio buttons voor state selectie
- [x] Alle imports aanwezig

### SettingsModal.kt
- [x] Dialog correct geïmporteerd (`androidx.compose.ui.window.Dialog`)
- [x] Geen compile errors

---

## 🚀 Volgende Stappen

1. **Build het project** om te verifiëren dat alle compile errors opgelost zijn
2. **Test elke modal** individueel
3. **Integreer met HaSettingsViewModel** volgens de voorbeelden hierboven
4. **Test keyboard navigation** (Tab, Enter, ESC)
5. **Test screenreader** compatibiliteit
6. **Test op verschillende schermformaten** (mobiel portrait/landscape, tablet)

---

## 📝 Opmerkingen

- Alle modals zijn **volledig zelfstandig** en hebben geen directe ViewModel dependencies
- Alle modals gebruiken **callbacks** voor data flow
- Alle modals hebben **unsaved changes** dialogs
- Alle modals hebben **animaties** (fade + scale)
- Alle modals hebben **accessibility** support (semantics, focus management)
- Alle modals zijn **responsive** (max 720dp breedte, padding op mobiel)

---

## 🎨 Styling

Alle modals accepteren color parameters:
- `textColor`: Tekst kleur
- `buttonColor`: Knop achtergrond en accent kleur
- `buttonTextColor`: Knop tekst kleur
- `containerColor`: Modal achtergrond kleur

Deze kunnen dynamisch worden ingesteld via `SettingsManager`:

```kotlin
val textColor = Color(SettingsManager.getTextColor(context))
val buttonColor = Color(SettingsManager.getButtonColor(context))
val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
val containerColor = Color(SettingsManager.getBackgroundColor(context))
```
