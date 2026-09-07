# Externe Speaker Integratie - Implementatie Gids

Deze gids beschrijft hoe je de nieuwe externe speaker functionaliteit kunt integreren in de bestaande alarm logica van AgendaAlarm.

## ✅ Wat is geïmplementeerd

### 1. Data Models
- **ExternalSpeakerMode enum**: `DISABLED`, `DEFAULT`, `BACKUP_ONLY`
- **HomeAssistantSettings**: Uitgebreid met:
  - `externalSpeakerEntityId: String?` - ID van de geselecteerde speaker
  - `externalSpeakerMode: ExternalSpeakerMode` - Modus van de speaker
  - `presenceEntityId: String?` - ID van de presence sensor voor "thuis" detectie
  - `presenceExpectedState: String` - Verwachte waarde voor "thuis" (default: "home")

### 2. Repository & API
- **HomeAssistantRepository.fetchMediaPlayers()**: Haalt alle media_player entities op
- **HomeAssistantRepository.fetchAllEntities()**: Haalt alle entities op (voor presence detection)
- **HomeAssistantRepository.playMediaOnSpeaker()**: Speelt media af op externe speaker
- **HomeAssistantApi.getAllStates()**: GET /api/states endpoint
- **HomeAssistantApi.callService()**: POST /api/services/{domain}/{service} endpoint

### 3. Decision Logic
- **AlarmOutputDecisionEngine**: Bepaalt waar het alarm moet worden afgespeeld
- **AlarmOutput sealed class**: 
  - `PhoneOnly` - Alleen op telefoon
  - `ExternalDefault` - Op externe speaker (standaard modus)
  - `ExternalBackup` - Op externe speaker (backup modus)
  - `PhoneAndExternal` - Beide (optioneel voor toekomstige uitbreiding)

### 4. ViewModel & UI
- **HaSettingsViewModel**: Uitgebreid met speaker en presence entity functies
- **HaSettingsActivity**: Volledig nieuwe UI sectie "Externe speaker opties"
- Dropdown selectie voor speakers en presence entities
- Radio buttons voor speaker modus selectie
- Test knop voor speaker functionaliteit

## 🔧 Integratie in AlarmService

Om de externe speaker functionaliteit te gebruiken in je alarm service, volg deze stappen:

### Stap 1: Importeer de benodigde classes

```kotlin
import com.redubbledd.agendawekker.AlarmOutputDecisionEngine
import com.redubbledd.agendawekker.AlarmOutput
import com.redubbledd.agendawekker.data.HomeAssistantRepository
import com.redubbledd.agendawekker.data.HomeAssistantSettingsStorage
import com.redubbledd.agendawekker.network.HomeAssistantClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
```

### Stap 2: Wijzig AlarmService.kt

In je `AlarmService.kt`, zoek naar de functie waar het alarm wordt afgespeeld. Voeg het volgende toe:

```kotlin
private fun triggerAlarm(alarmItem: AlarmItem) {
    // Bepaal waar het alarm moet worden afgespeeld
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val settingsStorage = HomeAssistantSettingsStorage(applicationContext)
            val client = HomeAssistantClient()
            val repository = HomeAssistantRepository(client, settingsStorage)
            
            // Bepaal alarm output op basis van configuratie
            val alarmOutput = AlarmOutputDecisionEngine.determineAlarmOutput(
                context = applicationContext,
                repository = repository,
                nextAlarmTimeMillis = alarmItem.epochMillis
            )
            
            when (alarmOutput) {
                is AlarmOutput.PhoneOnly -> {
                    // Speel alarm alleen op telefoon
                    playAlarmOnPhone(alarmItem)
                }
                
                is AlarmOutput.ExternalDefault -> {
                    // Speel alarm op externe speaker (standaard modus)
                    val result = repository.playMediaOnSpeaker(
                        entityId = alarmOutput.speakerEntityId,
                        mediaContentId = getAlarmSoundUrl(), // Jouw alarm sound URL
                        mediaContentType = "music"
                    )
                    
                    // Optioneel: telefoon ook laten rinkelen als fallback
                    playAlarmOnPhone(alarmItem)
                    
                    Log.d("AlarmService", "Alarm via externe speaker: $result")
                }
                
                is AlarmOutput.ExternalBackup -> {
                    // Speel alarm op externe speaker (backup modus - lage batterij)
                    val result = repository.playMediaOnSpeaker(
                        entityId = alarmOutput.speakerEntityId,
                        mediaContentId = getAlarmSoundUrl(),
                        mediaContentType = "music"
                    )
                    
                    Log.d("AlarmService", "Alarm via backup speaker (lage batterij): $result")
                    
                    // Bij fout, fallback naar telefoon
                    if (result is com.redubbledd.agendawekker.data.HaPlayMediaResult.Error) {
                        playAlarmOnPhone(alarmItem)
                    }
                }
                
                is AlarmOutput.PhoneAndExternal -> {
                    // Beide (optioneel voor toekomstige uitbreiding)
                    repository.playMediaOnSpeaker(
                        entityId = alarmOutput.speakerEntityId,
                        mediaContentId = getAlarmSoundUrl(),
                        mediaContentType = "music"
                    )
                    playAlarmOnPhone(alarmItem)
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Fout bij bepalen alarm output, gebruik telefoon", e)
            // Fallback: altijd telefoon laten rinkelen bij fout
            playAlarmOnPhone(alarmItem)
        }
    }
}

private fun playAlarmOnPhone(alarmItem: AlarmItem) {
    // Bestaande logica om alarm op telefoon af te spelen
    // Deze functie moet je zelf implementeren op basis van je huidige code
}

private fun getAlarmSoundUrl(): String {
    // Retourneer de URL van je alarm geluid
    // Dit kan een lokale URL zijn of een publiek toegankelijke URL
    // Bijvoorbeeld: "https://yourdomain.com/alarm.mp3"
    // Of: gebruik een TTS service in Home Assistant
    return "https://www.soundjay.com/buttons/sounds/beep-07a.mp3" // Voorbeeld
}
```

### Stap 3: Alarm geluid beschikbaar maken

Voor externe speakers moet het alarm geluid beschikbaar zijn via een URL. Je hebt twee opties:

**Optie A: Gebruik een publieke URL**
```kotlin
private fun getAlarmSoundUrl(): String {
    // Gebruik een vaste publieke URL
    return "https://yourdomain.com/sounds/alarm.mp3"
}
```

**Optie B: Gebruik Home Assistant TTS (Text-to-Speech)**
```kotlin
private suspend fun playAlarmWithTTS(repository: HomeAssistantRepository, speakerEntityId: String, message: String) {
    // Roep TTS service aan in Home Assistant
    val serviceCall = com.redubbledd.agendawekker.network.HaServiceCall(
        entity_id = speakerEntityId,
        media_content_id = "media-source://tts/google_translate?message=${java.net.URLEncoder.encode(message, "UTF-8")}",
        media_content_type = "music"
    )
    // Implementeer via repository.playMediaOnSpeaker()
}
```

## 🧪 Testen

### Test 1: Speaker selectie
1. Open AgendaAlarm
2. Ga naar "Kalender Alarm Instellingen" → "Entiteiten"
3. Scroll naar "Externe speaker opties"
4. Klik op "Laad speakers" als de lijst leeg is
5. Selecteer een speaker uit de dropdown
6. Klik op "Test Speaker" - je moet een test geluid horen

### Test 2: Standaard speaker modus
1. Selecteer een speaker
2. Kies "Standaard speaker" modus
3. Stel een presence entity in (bijv. `person.frank`)
4. Zet "Verwachte state voor 'thuis'" op "home"
5. Zorg dat je thuis bent volgens Home Assistant
6. Stel een test alarm in
7. Alarm moet op externe speaker afgaan

### Test 3: Backup speaker modus
1. Selecteer een speaker
2. Kies "Backup speaker" modus
3. Laat batterij onder ~15% komen (of pas de threshold aan in AlarmOutputDecisionEngine.kt)
4. Stel een alarm in de toekomst in
5. Alarm moet op externe speaker afgaan als backup

### Test 4: Presence detection
1. Configureer een presence entity
2. Verlaat je huis (zorg dat Home Assistant dit detecteert)
3. Stel een alarm in
4. Alarm moet op telefoon afgaan (niet op speaker)

## 📋 Checklist voor integratie

- [ ] AlarmService.kt aangepast om AlarmOutputDecisionEngine te gebruiken
- [ ] Alarm sound URL geconfigureerd (publiek toegankelijk of via HA TTS)
- [ ] Fallback logica geïmplementeerd (telefoon bij fout)
- [ ] Getest met alle drie de modi: DISABLED, DEFAULT, BACKUP_ONLY
- [ ] Presence detection getest (thuis vs weg)
- [ ] Battery check getest (>15% vs <15%)
- [ ] Error handling getest (HA niet bereikbaar, speaker niet gevonden)

## 🔍 Debugging

Gebruik logcat om te debuggen:
```bash
adb logcat | grep -E "AlarmOutputDecision|AlarmService|HAAlarmSync"
```

Belangrijke log berichten:
- `"Gebruiker is thuis"` / `"Gebruiker is niet thuis"`
- `"Batterij mogelijk te laag"` / `"Batterij voldoende"`
- `"Externe speaker niet geconfigureerd"`
- `"Alarm via externe speaker: Success"`

## 🚨 Error Handling

De implementatie heeft ingebouwde error handling:
1. Als HA niet bereikbaar is → fallback naar telefoon
2. Als speaker niet gevonden → fallback naar telefoon  
3. Als presence entity niet bestaat → assume thuis
4. Als batterij status niet beschikbaar → assume voldoende

## 📝 Toekomstige uitbreidingen

Mogelijke verbeteringen:
1. Volume controle voor externe speaker
2. Verschillende alarm geluiden per speaker
3. Meerdere speakers tegelijk gebruiken
4. Slimme volume aanpassing op basis van tijd van de dag
5. Integration met Google Assistant routines
6. Visuele feedback via smart lights

## ⚠️ Belangrijke opmerkingen

1. **Netwerk vereist**: Externe speakers werken alleen als de telefoon en HA beide online zijn
2. **Batterij monitoring**: De batterij check is een schatting, niet 100% nauwkeurig
3. **Presence detection**: Zorg dat je presence entity real-time is (niet delayed)
4. **Media URL**: De URL moet toegankelijk zijn voor Home Assistant (niet alleen voor de telefoon)
5. **Permissions**: Zorg dat de app WAKE_LOCK en INTERNET permissions heeft

## 🆘 Support

Bij problemen:
1. Check Home Assistant logs: Settings → System → Logs
2. Check Android logcat voor errors
3. Test de connectie via "Test HA Koppeling" knop
4. Test de speaker via "Test Speaker" knop
5. Verify dat de presence entity bestaat in HA

---

**Status**: ✅ Volledig geïmplementeerd en klaar voor integratie
**Versie**: 1.0
**Datum**: 27 November 2024
