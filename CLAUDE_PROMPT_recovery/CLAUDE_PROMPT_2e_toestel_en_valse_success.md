# Fix 1: opnieuw kunnen koppelen (2e toestel) + Fix 2: valse "gekoppeld"-melding

Twee losstaande bugs, allebei rond koppelen.

## Fix 1 (HA, `ha-integration/custom_components/agendaalarm_backup/`): geen manier meer om een 2e toestel te koppelen

**Root cause:** de vorige "echt verbergen"-fix maakt `GeneratePairingCodeButton`,
`PairingCodeSensor` en `PairingQrCamera` conditioneel - alleen aangemaakt als
`not hub.is_paired`. Zodra er 1x gekoppeld is, verdwijnen ze dus helemaal en is
er geen weg meer terug om een 2e telefoon te koppelen (behalve eerst volledig
ontkoppelen, wat het eerste toestel ook de toegang ontneemt). Dat was niet de
bedoeling - de knop moest juist altijd blijven werken voor een 2e toestel (zie
de oorspronkelijke `CLAUDE_PROMPT_pairing_code_niet_hertonen.md`).

**Fix:** in `button.py`, `sensor.py`, `camera.py`'s `async_setup_entry`: voeg
`GeneratePairingCodeButton`, `PairingCodeSensor` en `PairingQrCamera` weer
ALTIJD toe, ongeacht `hub.is_paired` (dus terugdraaien van de
`not hub.is_paired`-conditie op precies deze drie). Alleen `UnpairButton`
blijft conditioneel (`if hub.is_paired`) - die heeft alleen zin als er iets is
om te ontkoppelen.

Dit is veilig omdat de eerder toegevoegde staat-logica al goed genoeg is om
niks gevoeligs te laten rondslingeren zodra er niks actiefs is:
- `PairingCodeSensor.native_value` toont al `"Paired"` i.p.v. een oude code
  zodra `hub.pairing_code` leeg is.
- `PairingQrCamera.async_camera_image` genereert al geen nieuwe code meer
  vanzelf (alleen bij een expliciete druk op de knop) zodra `hub.is_paired`
  True is.
- Een druk op "Generate pairing code" werkt dus gewoon altijd, ook al ben je al
  gekoppeld, en toont dan een verse (5 minuten geldige) code/QR voor een 2e
  toestel - precies zoals oorspronkelijk bedoeld.

## Fix 2 (Android, `app/src/main/java/com/dd/daykit/`): "Gekoppeld met Home Assistant" ook als de verbinding niet echt werkt

**Root cause, geverifieerd:** `HaSettingsViewModel.kt`'s `pairWithSetupCode()`
(regel ~486-517) roept na een geslaagde token-uitwisseling `applyHomeAssistantSettings()`
aan en toont DAARNA meteen het succesbericht. Maar `applyHomeAssistantSettings()`
zelf (regel ~130) start intern een EIGEN `viewModelScope.launch { ... }` en
retourneert meteen - het is dus fire-and-forget, niet af te wachten door de
aanroeper. `pairWithSetupCode()` wacht dus helemaal niet op de
`repository.testConnectionOverAllUrls()`-check die daar binnenin gebeurt, en
toont "Gekoppeld met Home Assistant via ...!" onvoorwaardelijk, ook als die
verbindingstest (die pas ná het succesbericht, ergens los op de achtergrond,
klaar is) eigenlijk faalt. Gecombineerd met de eerdere "koppelscherm sluit
direct bij succes"-fix krijg je zo precies dit: HA heeft `is_paired = True`
gezet (dat gebeurt server-side, onvoorwaardelijk zodra de code geldig was), de
app toont een groen succesbericht en sluit het scherm, maar de daadwerkelijke
verbinding kan alsnog stuk zijn zonder dat de gebruiker dat ziet.

**Fix:** in `pairWithSetupCode()`, ná het opslaan van het token
(`saveSettings()`), niet `applyHomeAssistantSettings()` aanroepen maar
rechtstreeks en wél afgewacht `repository.testConnectionOverAllUrls()` aanroepen,
en het succes-/foutbericht daarvan laten afhangen:

```kotlin
fun pairWithSetupCode(baseUrl: String, code: String) {
    viewModelScope.launch {
        _qrPairingState.value = QrPairingUiState(isPairing = true)

        val result = com.dd.daykit.homeassistant.PairingClient.exchangeCode(baseUrl, code)
        when (result) {
            is com.dd.daykit.homeassistant.PairingClient.PairingResult.Success -> {
                val existingUrls = _uiState.value.baseUrls.filter { it.isNotBlank() }.toMutableSet()
                existingUrls.add(result.baseUrl)
                _uiState.value = _uiState.value.copy(
                    baseUrls = existingUrls.toList(),
                    activeBaseUrl = result.baseUrl,
                    longLivedToken = result.token
                )
                saveSettings()

                // Wacht ECHT op de verbindingstest i.p.v. het fire-and-forget
                // applyHomeAssistantSettings() aan te roepen - anders kan er een vals
                // succesbericht getoond worden terwijl de verbinding stuk is (zie context
                // in het prompt-bestand hierboven).
                val connectionResult = repository.testConnectionOverAllUrls()
                when (connectionResult) {
                    is HaConnectionResult.Success -> {
                        _uiState.value = _uiState.value.copy(activeBaseUrl = connectionResult.baseUrl)
                        // Nu pas de rest laden (media players, presence, scripts, config) -
                        // dit mag wel fire-and-forget, dat is alleen UI-verrijking, geen
                        // correctheids-signaal.
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
            is com.dd.daykit.homeassistant.PairingClient.PairingResult.Error -> {
                _qrPairingState.value = QrPairingUiState(
                    isPairing = false,
                    lastMessage = "Koppelen mislukt: ${result.message}",
                    isError = true
                )
            }
        }
    }
}
```

Let op: het token is op dit punt al wel opgeslagen (en HA's `is_paired` staat al
op `True`, dat kan niet meer teruggedraaid worden vanuit de app zonder een
aparte ontkoppel-aanroep) - deze fix zorgt er niet voor dat een mislukte
verbindingstest de koppeling ongedaan maakt, maar wél dat de gebruiker een
eerlijke foutmelding ziet in plaats van een vals succesbericht. Gezien de
"koppelscherm sluit direct bij succes"-fix (vorige prompt): dat auto-sluiten
moet ALLEEN gebeuren bij `isError == false` - controleer dat die conditie nog
klopt met deze aangepaste logica (de `LaunchedEffect` in `HaSettingsActivity.kt`
die op `qrPairingState` reageert).

## Oplevercriteria

- Fix 1: "Generate pairing code" werkt (toont code + QR) ook als er al
  gekoppeld is; alleen "Unpair"/ontkoppel-knop blijft conditioneel.
- Fix 2: als de token-uitwisseling lukt maar de daaropvolgende verbindingstest
  faalt, toont de app een foutmelding (geen vals "Gekoppeld!"-bericht), en het
  koppelscherm sluit dan NIET automatisch.
- Als beide stappen (token + verbindingstest) lukken: gedrag ongewijzigd
  (succesbericht, scherm sluit, media players/presence/scripts worden geladen).
