# "Token ontvangen, maar verbinding testen mislukte: token ontbreekt" - race condition

## Context

De vorige fix (verbinding écht afwachten vóór succes tonen) werkt en heeft
meteen een échte bug blootgelegd: elke koppelpoging faalt nu met "token
ontbreekt" bij de verbindingstest, terwijl het token wel degelijk ontvangen is.

**Root cause, geverifieerd:** `pairWithSetupCode()` in `HaSettingsViewModel.kt`
roept na het ontvangen van het token `saveSettings()` aan en test daarna de
verbinding. Maar `saveSettings()` (regel ~312) start zelf een NIEUWE,
onafhankelijke coroutine via zijn eigen `viewModelScope.launch { ... }` en
retourneert meteen - exact hetzelfde fire-and-forget-patroon als de
`applyHomeAssistantSettings()`-bug die net al gefixt is, alleen nu een laag
dieper. `pairWithSetupCode()` wacht dus niet echt op `saveSettings()`: het
token/adres is op het moment dat `repository.testConnectionOverAllUrls()` al
draait mogelijk nog niet persistent opgeslagen en `repository.clearClientCache()`
(die de HTTP-client dwingt de nieuwe instellingen te gebruiken) is mogelijk nog
niet aangeroepen - vandaar dat de verbindingstest een cliënt zonder (nieuw)
token te pakken krijgt en "token ontbreekt" meldt.

`settingsStorage.saveSettings(settings)` is zelf al een `suspend fun` (zie
`HomeAssistantSettingsStorage.kt`) en dus prima rechtstreeks af te wachten;
`repository.clearClientCache()` is synchroon. Het probleem zit 'm puur in de
extra, overbodige `viewModelScope.launch`-laag binnen de ViewModel's
`saveSettings()`.

## Wat te doen

In `pairWithSetupCode()`: vervang de aanroep van `saveSettings()` (de
ViewModel-functie, met zijn eigen fire-and-forget launch) door een
rechtstreekse, wél afgewachte opslag - dezelfde stappen die `saveSettings()`
normaal doet, maar dan synchroon binnen de coroutine van `pairWithSetupCode()`
zelf:

```kotlin
is com.dd.daykit.homeassistant.PairingClient.PairingResult.Success -> {
    val existingUrls = _uiState.value.baseUrls.filter { it.isNotBlank() }.toMutableSet()
    existingUrls.add(result.baseUrl)
    _uiState.value = _uiState.value.copy(
        baseUrls = existingUrls.toList(),
        activeBaseUrl = result.baseUrl,
        longLivedToken = result.token
    )

    // NIET de ViewModel's saveSettings() gebruiken hier - die start zelf een losse
    // viewModelScope.launch en retourneert meteen (fire-and-forget), dus de
    // verbindingstest hieronder zou dan een client kunnen pakken die het nieuwe
    // token nog niet kent. Rechtstreeks + afgewacht opslaan i.p.v. daarvan.
    val current = _uiState.value
    val settingsToSave = com.dd.daykit.data.HomeAssistantSettings(
        baseUrls = current.baseUrls.filter { it.isNotBlank() },
        activeBaseUrl = current.activeBaseUrl,
        longLivedToken = current.longLivedToken,
        entities = current.entities.filter { it.isNotBlank() },
        showOnlyLinkedEntities = current.showOnlyLinkedEntities,
        backupAlarmEnabled = current.backupAlarmEnabled,
        externalSpeakerEntityId = current.selectedSpeakerEntityId,
        externalSpeakerMode = current.speakerMode,
        presenceEntityId = current.selectedPresenceEntityId,
        presenceExpectedState = current.presenceExpectedState,
        outOfBedCheckEnabled = current.outOfBedCheckEnabled,
        outOfBedEntityId = current.outOfBedEntityId,
        outOfBedExpectedValue = current.outOfBedExpectedValue,
        backupVolume = current.backupVolume,
        backupAlarmDuration = current.backupAlarmDuration,
        selectedGithubSoundUrl = current.selectedGithubSoundUrl,
        customSoundUrl = current.customSoundUrl,
        useCustomBackupSound = current.useCustomBackupSound,
        alarmScriptEnabled = current.alarmScriptEnabled,
        alarmScriptEntityId = current.alarmScriptEntityId,
        timerScriptEnabled = current.timerScriptEnabled,
        timerScriptEntityId = current.timerScriptEntityId,
        alarmScriptIgnorePresence = current.alarmScriptIgnorePresence,
        timerScriptIgnorePresence = current.timerScriptIgnorePresence
    )
    settingsStorage.saveSettings(settingsToSave)   // suspend fun, wordt nu ECHT afgewacht
    repository.clearClientCache()                  // synchroon, dwingt nieuw token te gebruiken

    // Nu pas testen - de opslag + cache-clear zijn hierboven gegarandeerd klaar.
    val connectionResult = repository.testConnectionOverAllUrls()
    when (connectionResult) {
        is HaConnectionResult.Success -> {
            _uiState.value = _uiState.value.copy(activeBaseUrl = connectionResult.baseUrl)
            applyHomeAssistantSettings()
            _qrPairingState.value = QrPairingUiState(
                isPairing = false,
                lastMessage = "Gekoppeld met Home Assistant via ${connectionResult.baseUrl}!",
                isError = false
            )
        }
        is HaConnectionResult.Error -> {
            _qrPairingState.value = QrPairingUiState(
                isPairing = false,
                lastMessage = "Token ontvangen, maar verbinding testen mislukte: ${connectionResult.message}. Controleer je netwerk en probeer het opnieuw via Testverbinding.",
                isError = true
            )
        }
    }
}
```

Als er in de codebase op meer plekken code staat die ná `saveSettings()`
(de ViewModel-functie) aanroept afhankelijk is van dat de opslag/cache-clear al
klaar is - zoek dat kort na met een grep op `saveSettings()`-aanroepen gevolgd
door een directe vervolg-actie in dezelfde functie - en pas gelijksoortig aan
als dat daar ook een probleem geeft. Voor nu is deze ene plek (koppelen) de
enige bevestigde en te fixen plek.

**Overweeg (optioneel, alleen als het makkelijk kan zonder andere aanroepplekken
te breken):** `saveSettings()` zelf herschrijven naar een `suspend fun` (zonder
eigen `viewModelScope.launch`), en bestaande aanroepen vanuit UI-code aanpassen
naar `viewModelScope.launch { saveSettings() }` - dat lost het probleem
structureel op i.p.v. per-aanroepplek. Doe dit ALLEEN als je alle bestaande
aanroepplekken van `saveSettings()` kan vinden en aanpassen zonder iets te
breken; anders liever de gerichte fix hierboven, die is veiliger.

## Oplevercriteria

- Koppelen via QR/code: verbindingstest ná het ontvangen van een token slaagt
  nu (geen "token ontbreekt" meer), mits de HA-server daadwerkelijk bereikbaar
  is.
- Succesbericht + automatisch sluiten van het koppelscherm gebeurt weer zoals
  bedoeld bij een echt geslaagde koppeling.
