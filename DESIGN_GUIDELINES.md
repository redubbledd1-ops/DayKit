# Design Guidelines voor Agenda Wekker App

## Overzicht
Dit document beschrijft de standaard design patterns en componenten die gebruikt moeten worden bij het maken van nieuwe pagina's in de app. Dit zorgt voor consistentie en voorkomt dat je handmatig uitlijning en layout moet aanpassen.

## Standaard Templates

### 1. SettingsScreenTemplate
Gebruik deze template voor instellingenpagina's met scrollbare content en Annuleren/Opslaan knoppen.

**Wanneer te gebruiken:**
- Pagina's met meerdere instellingen die mogelijk scrollen
- Pagina's die wijzigingen opslaan (met Annuleren/Opslaan knoppen)

**Voorbeeld:**
```kotlin
import com.redubbledd.agendawekker.ui.SettingsScreenTemplate

@Composable
fun MijnInstellingenScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    
    SettingsScreenTemplate(
        title = "Mijn Instellingen",
        onBack = onBack,
        onSave = {
            // Sla instellingen op
            onBack()
        }
    ) {
        // Content items
        item {
            Text("Instelling 1", color = textColor)
        }
        item {
            Text("Instelling 2", color = textColor)
        }
    }
}
```

**Voordelen:**
- ✅ Titel staat altijd bovenaan
- ✅ Content scrollt in het midden
- ✅ Knoppen blijven altijd onderin zichtbaar
- ✅ Automatische uitlijning op basis van gebruikersinstellingen
- ✅ Safe drawing padding (geen overlap met notch/navigation bar)

### 2. StandardScreenTemplate
Gebruik deze template voor eenvoudige pagina's zonder scrolling.

**Wanneer te gebruiken:**
- Simpele menu's met een paar knoppen
- Pagina's zonder veel content
- Pagina's zonder Annuleren/Opslaan functionaliteit

**Voorbeeld:**
```kotlin
import com.redubbledd.agendawekker.ui.StandardScreenTemplate

@Composable
fun MijnMenuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    
    StandardScreenTemplate(
        title = "Mijn Menu",
        onBack = onBack
    ) {
        Button(
            onClick = { /* actie */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonTextColor
            )
        ) {
            Text("Optie 1")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { /* actie */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonTextColor
            )
        ) {
            Text("Optie 2")
        }
    }
}
```

## Helper Functies voor Uitlijning

In plaats van handmatig uitlijning te bepalen, gebruik de helper functies uit `UIComponents.kt`:

```kotlin
import com.redubbledd.agendawekker.ui.getBoxAlignmentFromString
import com.redubbledd.agendawekker.ui.getHorizontalAlignment
import com.redubbledd.agendawekker.ui.getTextAlign
import com.redubbledd.agendawekker.ui.getHorizontalArrangement

val textAlignment = SettingsManager.getTextAlignment(context)

// Voor Box contentAlignment
val boxAlignment = getBoxAlignmentFromString(textAlignment)

// Voor Column/LazyColumn horizontalAlignment
val horizontalAlignment = getHorizontalAlignment(textAlignment)

// Voor Text textAlign
val textAlign = getTextAlign(textAlignment)

// Voor Row horizontalArrangement
val horizontalArrangement = getHorizontalArrangement(textAlignment)
```

## Standaard Kleuren en Stijlen

Gebruik altijd de kleuren uit SettingsManager:

```kotlin
val context = LocalContext.current
val textColor = Color(SettingsManager.getTextColor(context))
val buttonColor = Color(SettingsManager.getButtonColor(context))
val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
```

## Activity Setup

Elke nieuwe Activity moet deze standaard setup hebben:

```kotlin
class MijnNieuweActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Zorg dat content onder system bars kan komen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Stel status bar kleur in
        window.statusBarColor = SettingsManager.getBackgroundColor(this)
        
        setContent {
            MaterialTheme {
                MijnNieuweScreen { finish() }
            }
        }
    }
}
```

## Knoppen

### Standaard Knoppen
```kotlin
Button(
    onClick = { /* actie */ },
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(
        containerColor = buttonColor,
        contentColor = buttonTextColor
    ),
    border = null  // Geen border tenzij specifiek gewenst
) {
    Text("Knop Tekst")
}
```

### Annuleren/Opslaan Knoppen
Gebruik `StandardActionButtons` voor consistente Annuleren/Opslaan knoppen:

```kotlin
StandardActionButtons(
    onCancel = { /* annuleer actie */ },
    onSave = { /* opslaan actie */ }
)
```

## Swipe Gestures

Voor consistente swipe navigatie:

```kotlin
modifier = Modifier.pointerInput(Unit) {
    detectDragGestures { change, dragAmount ->
        change.consume()
        if (dragAmount.x > 50) {
            onBack()  // Swipe rechts = terug
        }
    }
}
```

## Checklist voor Nieuwe Pagina's

- [ ] Gebruik `SettingsScreenTemplate` of `StandardScreenTemplate`
- [ ] Gebruik helper functies voor uitlijning (niet handmatig)
- [ ] Gebruik kleuren uit `SettingsManager`
- [ ] Zet `WindowCompat.setDecorFitsSystemWindows(window, false)`
- [ ] Zet `window.statusBarColor` correct
- [ ] Gebruik `.safeDrawingPadding()` voor content
- [ ] Gebruik `.fillMaxWidth()` voor knoppen
- [ ] Test met verschillende uitlijningsinstellingen
- [ ] Test dat knoppen niet buiten scherm vallen
- [ ] Voeg swipe gesture toe voor navigatie (optioneel)

## Veelvoorkomende Fouten

❌ **NIET DOEN:**
```kotlin
// Handmatige uitlijning
val alignment = when (textAlignment) {
    "CENTER" -> Alignment.Center
    // etc...
}

// Hardcoded padding zonder safe drawing
Column(modifier = Modifier.padding(16.dp))

// Knoppen zonder fillMaxWidth
Button(onClick = {}) { Text("Knop") }
```

✅ **WEL DOEN:**
```kotlin
// Helper functies gebruiken
val alignment = getBoxAlignmentFromString(textAlignment)

// Safe drawing padding gebruiken
Column(
    modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .padding(16.dp)
)

// Knoppen met fillMaxWidth
Button(
    onClick = {},
    modifier = Modifier.fillMaxWidth()
) { Text("Knop") }
```

## Voorbeelden

Bekijk deze bestanden voor goede voorbeelden:
- `NavigationSettingsActivity.kt` - Gebruikt SettingsScreenTemplate
- `DesignSettingsActivity.kt` - Gebruikt SettingsScreenTemplate met color picker
- `GlobalSettingsActivity.kt` - Gebruikt StandardScreenTemplate
- `BackgroundSettingsActivity.kt` - Custom layout met helper functies

## Vragen?

Als je twijfelt welke template te gebruiken:
- Veel content die moet scrollen + Opslaan/Annuleren? → `SettingsScreenTemplate`
- Simpel menu met knoppen? → `StandardScreenTemplate`
- Heel specifieke layout nodig? → Gebruik helper functies en `AppBackground`
