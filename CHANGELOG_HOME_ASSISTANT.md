# Home Assistant Instellingen - Wijzigingen

## ✅ Wat is gefixed:

### 1. **Mega lijsten verwijderd**
- ✅ **Externe speaker dropdown**: Toont nu ALLEEN handmatig toegevoegde `media_player.*` entiteiten
- ✅ **Aanwezigheidsentiteit dropdown**: Toont nu ALLEEN handmatig toegevoegde presence entiteiten (person.*, binary_sensor.*, device_tracker.*, etc.)
- ❌ Geen volledige lijst meer uit Home Assistant - alles komt uit de handmatig toegevoegde entiteiten lijst

### 2. **UI vereenvoudigd**
- ✅ "Toon alleen gekoppelde entiteiten" schakelaar verwijderd (niet meer nodig)
- ✅ "Laad speakers" knop verwijderd (automatisch geladen uit handmatige lijst)
- ✅ "Laad aanwezigheidsentiteiten" knop verwijderd (automatisch geladen uit handmatige lijst)
- ✅ Loading indicators verwijderd (niet meer nodig)

### 3. **Nederlandse vertalingen verbeterd**
- ✅ "Home Assistant Instellingen" → "Home Assistant-instellingen"
- ✅ "Home Assistant URLs" → "Home Assistant-URL's"
- ✅ "Long-Lived Access Token" → "Toegangstoken (lange levensduur)"
- ✅ "Externe Speaker" → "Externe speaker"
- ✅ "Selecteer Speaker" → "Selecteer speaker"
- ✅ "Speaker Modus" → "Speakermodus"
- ✅ "Laad Speakers" → "Laad speakers"
- ✅ "Test Speaker" → "Test speaker"
- ✅ "Laad Aanwezigheidsentiteiten" → "Laad aanwezigheidsentiteiten"
- ✅ "Selecteer Aanwezigheidsentiteit" → "Selecteer aanwezigheidsentiteit"
- ✅ "Test Verbinding" → "Test verbinding"
- ✅ "Test Entiteiten" → "Test entiteiten"
- ✅ Alle strings nu consistent met kleine letters waar passend

### 4. **Foutmeldingen verbeterd**
- ✅ Media players: "Geen media_player entiteiten toegevoegd. Voeg ze toe in de lijst hierboven."
- ✅ Aanwezigheidsentiteiten: "Geen aanwezigheidsentiteiten toegevoegd. Voeg entiteiten toe zoals person.*, binary_sensor.*, device_tracker.*, etc."

## Hoe het nu werkt:

1. **Entiteiten toevoegen** in het "Entiteiten" veld bovenaan
   - Voeg `media_player.woonkamer` toe voor speakers
   - Voeg `person.frank` toe voor aanwezigheidsdetectie
   
2. **Speaker selecteren** 
   - Dropdown toont ALLEEN je toegevoegde `media_player.*` entiteiten
   - Geen mega lijst meer!

3. **Aanwezigheidsentiteit selecteren**
   - Dropdown toont ALLEEN je toegevoegde presence entiteiten
   - Geen mega lijst meer!

## Code wijzigingen:

### Files gewijzigd:
- ✅ `HaSettingsViewModel.kt` - Mega lijsten verwijderd, gebruikt nu alleen handmatige lijst
- ✅ `HaSettingsActivity.kt` - UI vereenvoudigd, schakelaars en knoppen verwijderd
- ✅ `strings.xml` - Alle vertalingen verbeterd
- ✅ `HaEntitySource.kt` - Domain parameter toegevoegd (bugfix)
- ✅ `HomeAssistantRepository.kt` - Domain parameter toegevoegd (bugfix)
