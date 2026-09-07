# Implementatieprompt: opgeslagen timer starten als 2e/3e timer (met naam)

Plak dit in Cursor of Claude Code (agent-modus, met schrijfrechten) in de root van dit project (CalenderAlarm).

## Gewenst gedrag (acceptatiecriteria)

1. Gebruiker tikt op een opgeslagen/preset timer in het "opgeslagen timers"-scherm (`TimerHistoryScreen`).
2. **Als de primaire timer IDLE is** (niets loopt): huidig gedrag blijft ongewijzigd — de duur wordt in de invoerwielen van de primaire timer gezet, gebruiker moet zelf op play drukken.
3. **Als de primaire timer RUNNING of PAUSED is**: de opgeslagen timer wordt automatisch gestart als 2e (of, als de 2e al bezet is, 3e) timer-slot — direct lopend, zonder dat de gebruiker nog een keer op play hoeft te drukken. Het opgeslagen-timers-scherm sluit zoals nu.
4. **Als er al 3 timers actief zijn** (primair + 2 extra, allemaal RUNNING of PAUSED): de tik wordt genegeerd — geen crash, geen foutmelding nodig, gewoon niets doen.
5. De 2e/3e timer toont overal de **naam van de opgeslagen timer** in plaats van de generieke "Timer 2"/"Timer 3": in de compacte rij op het timer-scherm én in de notificatie-subtitel. Als er geen naam is ingesteld (`SavedTimer.name` is leeg), val terug op de huidige tekst ("Timer 2"/"Timer 3" resp. "Timer").

## Bevestigde context (al onderzocht, niet opnieuw hoeven zoeken)

### Opgeslagen timers hebben al een naam-veld
`TimerStateHolder.kt:7`
```kotlin
data class SavedTimer(val durationMillis: Long, val name: String = "")
```
Lijst: `TimerStateHolder.savedTimers`. Hernoemen kan al via `TimerStateHolder.updateTimerName(context, index, newName)`.

### Huidige klik-flow (moet aangepast worden)
`TimerActivity.kt:657-670`
```kotlin
onSelectTimer = { selectedTime ->
    hoursInput = (selectedTime / 3600000).toInt()
    minutesInput = ((selectedTime % 3600000) / 60000).toInt()
    secondsInput = ((selectedTime % 60000) / 1000).toInt()
    TimerSettingsStateHolder.lastTimeMillis.value = selectedTime
    TimerSettingsStateHolder.save(context)
    showHistory = false
}
```
`TimerHistoryScreen`'s signature (`TimerActivity.kt:964`) is `fun TimerHistoryScreen(onBack: () -> Unit, onSelectTimer: (Long) -> Unit)` — krijgt alleen de duur door, geen naam. De klikbare rij zit op `TimerActivity.kt:1137`: `.clickable { onSelectTimer(savedTimer.durationMillis) }`.

De variabele `timerState` (primaire timer-status) is al in scope op deze plek in de composable (gebruikt op regel 637, 709, 807) — gebruik die om te bepalen of de primaire timer IDLE/FINISHED is of RUNNING/PAUSED.

### Extra-timer datamodel mist een naam-veld
`ExtraTimerManager.kt:457-465`
```kotlin
class ExtraTimerData(val id: String = java.util.UUID.randomUUID().toString()) {
    var state by mutableStateOf(GlobalTimerManager.TimerState.IDLE)
    var hoursInput by mutableIntStateOf(0)
    var minutesInput by mutableIntStateOf(0)
    var secondsInput by mutableIntStateOf(0)
    var remainingMs by mutableLongStateOf(0L)
    var initialMs by mutableLongStateOf(0L)
    var endTimeMs by mutableLongStateOf(0L)
}
```
Persistente op-/afslag zit in `saveState()`/`restoreState()`, `ExtraTimerManager.kt:408-454`, via `org.json.JSONArray`/`JSONObject` (dus een nieuw `"name"`-veld toevoegen is triviaal, geen delimiter-escaping nodig zoals bij een handmatig string-format).

### Extra timer aanmaken/starten (bestaande functies, hergebruiken)
```kotlin
// ExtraTimerManager.kt:87-94
fun addTimer(context: Context): ExtraTimerData? {
    if (timers.size >= MAX_EXTRA_TIMERS) return null   // let op: raw list-size check, zie edge case hieronder
    val slot = ExtraTimerData()
    timers.add(slot)
    saveState(context)
    return slot
}

// ExtraTimerManager.kt:96-107
fun startTimer(context: Context, slot: ExtraTimerData, durationMs: Long) {
    if (durationMs <= 0) return
    ...
    slot.state = GlobalTimerManager.TimerState.RUNNING
    saveState(context)
    startTicker(context, slot)
    syncPopup(context, "start_extra")
}
```
`hasActiveTimers(): Boolean` bestaat al (regel 138-139): `timers.any { it.state == RUNNING || it.state == PAUSED }`.

### Waar het label nu hardcoded is
- In-app compacte rij, extra slots: `TimerActivity.kt:898` → `label = "Timer ${slotIndex + 1}"` (binnen `TimerCompactSlotRow`, regel 896-909).
- In-app compacte rij, primaire slot: `TimerActivity.kt:822` → `label = "Timer 1"` (niet per se aan te passen, zie "Scope" hieronder).
- Notificatie-subtitel extra timer: `ExtraTimerPopupNotifications.kt:28` → `val subtitle = LanguageManager.getString("screen_timer")` (functie `build(context: Context, slot: ExtraTimerData, index: Int): Notification`, regel 22).

## Implementatiestappen

### 1. `name`-veld toevoegen aan `ExtraTimerData`
```kotlin
var name by mutableStateOf("")
```
En meenemen in `saveState()` (`put("name", slot.name)`) en `restoreState()` (`slot.name = obj.optString("name", "")`).

### 2. `TimerHistoryScreen` / `onSelectTimer` de volledige `SavedTimer` laten doorgeven
Verander de signature naar `onSelectTimer: (SavedTimer) -> Unit` (of `(Long, String) -> Unit` als je liever geen `SavedTimer`-afhankelijkheid in de call site wil) en pas de aanroep op regel 1137 aan naar `onSelectTimer(savedTimer)`.

### 3. Routinglogica in de `onSelectTimer`-lambda (`TimerActivity.kt:657-670`)
Vervang door iets in de trant van:
```kotlin
onSelectTimer = { saved ->
    val primaryBusy = timerState == GlobalTimerManager.TimerState.RUNNING ||
        timerState == GlobalTimerManager.TimerState.PAUSED

    if (!primaryBusy) {
        // bestaand gedrag: alleen invoerwielen vullen
        hoursInput = (saved.durationMillis / 3600000).toInt()
        minutesInput = ((saved.durationMillis % 3600000) / 60000).toInt()
        secondsInput = ((saved.durationMillis % 60000) / 1000).toInt()
        TimerSettingsStateHolder.lastTimeMillis.value = saved.durationMillis
        TimerSettingsStateHolder.save(context)
    } else {
        val activeExtraCount = ExtraTimerManager.timers.count {
            it.state == GlobalTimerManager.TimerState.RUNNING ||
            it.state == GlobalTimerManager.TimerState.PAUSED
        }
        if (activeExtraCount < ExtraTimerManager.MAX_EXTRA_TIMERS) {
            val slot = ExtraTimerManager.addTimer(context)
            if (slot != null) {
                slot.name = saved.name
                ExtraTimerManager.startTimer(context, slot, saved.durationMillis)
            }
        }
        // else: 3 timers actief -> bewust niets doen (negeren)
    }
    showHistory = false
}
```

### 4. Naam gebruiken in de UI-labels
`TimerActivity.kt:898`:
```kotlin
label = slot.name.ifBlank { "Timer ${slotIndex + 1}" },
```

### 5. Naam gebruiken in de notificatie
`ExtraTimerPopupNotifications.kt:28`:
```kotlin
val subtitle = slot.name.ifBlank { LanguageManager.getString("screen_timer") }
```

### 6. Scope-check: `TimerPopupForegroundService.kt`
Er bestaat ook een `buildExtraTimerNotification()` in `TimerPopupForegroundService.kt` (uit eerdere sessie, rond regel 269-315) — controleer of dit dezelfde notificatie-bouwcode is als `ExtraTimerPopupNotifications.build()`, of dat het twee losse implementaties zijn (mogelijk is er intussen gerefactored). Pas **beide** aan als ze los van elkaar bestaan, anders krijgt de notificatie alsnog het generieke label.

## Edge cases — expliciet beslissen, niet negeren

1. **`addTimer()` gebruikt `timers.size >= MAX_EXTRA_TIMERS`, niet een "actief"-telling.** Een extra timer die is afgelopen (`FINISHED`) maar nog niet is weggeklikt door de gebruiker blijft in de `timers`-lijst staan en telt dus mee voor de capaciteitscheck van `addTimer()`, óók al is hij niet meer "actief" volgens `hasActiveTimers()`/deze feature. Beslis en implementeer bewust:
   - Optie A (aanbevolen): als er een `FINISHED`-slot bestaat terwijl er nog ruimte "actief" is (minder dan 2 actieve extra timers), hergebruik dat slot (reset het en start het opnieuw) in plaats van te blokkeren op de rauwe lijstgrootte.
   - Optie B: laat het zo — een niet-weggeklikte afgelopen timer blokkeert dan een nieuwe. Documenteer deze keuze in een codecommentaar zodat het geen verrassing is.
2. **Race/dubbeltik:** als de gebruiker razendsnel twee keer op (verschillende) opgeslagen timers tikt vlak na elkaar, zorg dat de tweede tik de actuele staat van `ExtraTimerManager.timers` ziet (niet een verouderde `activeExtraCount` uit de eerste render) — dit werkt vanzelf goed als je de check binnen dezelfde lambda-aanroep doet zoals hierboven, maar test dit expliciet (zie verificatie).
3. **Naam-persistentie na app-herstart:** controleer dat `slot.name` na `restoreState()` correct terugkomt in zowel de UI-labels als de notificatie, ook als de app tijdens een lopende 2e/3e timer volledig is afgesloten en herstart.
4. **Lange namen:** `TimerCompactSlotRow`-label en notificatie-titel hebben beperkte ruimte — controleer of lange opgeslagen-timer-namen worden afgekapt (ellipsis) in plaats van de layout te breken.

## Scope-afbakening

Alleen de 2e/3e (extra) timer-slots hoeven de naam te tonen, zoals gevraagd. De primaire timer ("Timer 1"-label, regel 822) hoeft niet aangepast — laat dat met rust tenzij het triviaal meegenomen kan worden zonder risico.

## Verificatie (verplicht)

- Build slaagt zonder warnings over gewijzigde signatures (`onSelectTimer`, `ExtraTimerData`).
- Start de primaire timer, tik op een opgeslagen timer met een naam → verschijnt direct lopend als 2e timer, met die naam zichtbaar in de compacte rij én in de notificatie.
- Herhaal terwijl de 2e al loopt → komt als 3e timer, ook met naam.
- Met alle 3 actief: tik nog een opgeslagen timer aan → er gebeurt niets (geen crash, geen 4e slot, geen foutmelding).
- Pauzeer de 2e timer, tik een opgeslagen timer aan terwijl primair nog loopt en 2e gepauzeerd staat maar 3e nog vrij is → moet als 3e timer starten (gepauzeerd telt als "actief"/bezet, dus dit test dat pauze de slot niet vrijgeeft).
- Verwijder/laat een extra timer aflopen zonder 'm weg te klikken, tik dan een opgeslagen timer aan → gedraagt zich volgens de gekozen edge-case-optie hierboven (test bewust wat je hebt gekozen).
- Sluit de app geheel af terwijl 2e/3e timer met naam loopt, start opnieuw op → naam nog steeds zichtbaar in UI en notificatie.
- Test met een erg lange naam → geen layout-breuk.
- Rapporteer kort: wat is aangepast, welke edge-case-keuze is gemaakt bij punt 1, en of alle bovenstaande stappen zijn getest.
