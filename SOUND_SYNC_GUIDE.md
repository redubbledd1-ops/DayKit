# Geluiden Sync naar Home Assistant

## 🎵 Overzicht

De "Geluiden Sync" functie stuurt metadata van alle beschikbare alarm geluiden naar Home Assistant, zodat HA weet welke geluiden beschikbaar zijn en deze kan gebruiken in automations.

---

## 🔧 Setup

### Stap 1: In de App

#### 1.1 Enable HTTP Server
```
Settings → HTTP Server → Enable HTTP Server ✓
```
Dit is nodig zodat HA de geluiden kan streamen.

#### 1.2 Enable Home Assistant Sync
```
Settings → Home Assistant → Enable Sync ✓
Settings → Home Assistant → Home Assistant URL: http://192.168.1.50:8123
```

#### 1.3 Gebruik Sync Knop
```
Settings → Home Assistant → Geluiden Synchroniseren
```

**De knop toont:**
- Aantal geluiden
- Device IP
- Home Assistant URL
- Status (kan synchroniseren of niet)

**Bij klikken:**
- App haalt alle geluiden op
- Genereert streaming URLs met device IP
- Stuurt JSON naar HA webhook
- Toont "Sync voltooid" of foutmelding

---

### Stap 2: In Home Assistant

#### 2.1 Maak Webhook Automation
```yaml
# configuration.yaml of automations.yaml
automation:
  - alias: "AgendaAlarm - Sound Sync"
    trigger:
      - platform: webhook
        webhook_id: agendaalarm_sound_sync
    action:
      # Sla geluiden op in input_text als JSON
      - service: input_text.set_value
        target:
          entity_id: input_text.agendaalarm_sounds
        data:
          value: "{{ trigger.json | to_json }}"
      
      # Log in Home Assistant
      - service: system_log.write
        data:
          message: "AgendaAlarm: {{ trigger.json.total_count }} sounds synced"
          level: info
      
      # Optioneel: Stuur notificatie
      - service: notify.mobile_app
        data:
          title: "AgendaAlarm Sync"
          message: "{{ trigger.json.total_count }} geluiden gesynchroniseerd"
```

#### 2.2 Maak Input Helper
```yaml
# configuration.yaml
input_text:
  agendaalarm_sounds:
    name: "AgendaAlarm Sounds JSON"
    max: 65535  # Max size for large JSON
    initial: "{}"
```

#### 2.3 Herstart Home Assistant
```
Developer Tools → YAML → Check Configuration → Restart
```

---

## 📡 Wat Wordt Gestuurd

### JSON Payload
```json
{
  "sounds": [
    {
      "id": 1,
      "name": "Morning Alarm",
      "duration": 45000,
      "streaming_url": "http://192.168.1.100:8765/sounds/1?api_key=abc123"
    },
    {
      "id": 2,
      "name": "Gentle Wake",
      "duration": 30000,
      "streaming_url": "http://192.168.1.100:8765/sounds/2?api_key=abc123"
    },
    {
      "id": 3,
      "name": "Urgent Alert",
      "duration": 15000,
      "streaming_url": "http://192.168.1.100:8765/sounds/3?api_key=abc123"
    }
  ],
  "total_count": 3,
  "timestamp": 1702394567890
}
```

### Velden:
- `id` - Uniek ID van geluid
- `name` - Naam van geluid
- `duration` - Duur in milliseconden (optioneel)
- `streaming_url` - Volledige URL om geluid te streamen
- `total_count` - Totaal aantal geluiden
- `timestamp` - Wanneer sync werd uitgevoerd

---

## 🏠 Home Assistant Gebruik

### Template Sensor voor Geluid Lijst
```yaml
# configuration.yaml
template:
  - sensor:
      - name: "AgendaAlarm Sound List"
        state: >
          {% set data = states('input_text.agendaalarm_sounds') | from_json %}
          {{ data.total_count | default(0) }}
        attributes:
          sounds: >
            {% set data = states('input_text.agendaalarm_sounds') | from_json %}
            {{ data.sounds | default([]) }}
          last_sync: >
            {% set data = states('input_text.agendaalarm_sounds') | from_json %}
            {{ data.timestamp | default(0) | timestamp_local }}
```

### Input Select voor Geluid Keuze
```yaml
# configuration.yaml
input_select:
  alarm_sound_choice:
    name: "Alarm Sound"
    options:
      - "Morning Alarm"
      - "Gentle Wake"
      - "Urgent Alert"
    initial: "Morning Alarm"

# Automation om options te updaten na sync
automation:
  - alias: "Update Alarm Sound Options"
    trigger:
      - platform: state
        entity_id: input_text.agendaalarm_sounds
    action:
      - service: input_select.set_options
        target:
          entity_id: input_select.alarm_sound_choice
        data:
          options: >
            {% set data = trigger.to_state.state | from_json %}
            {{ data.sounds | map(attribute='name') | list }}
```

### Automation: Speel Gekozen Geluid
```yaml
automation:
  - alias: "Play Selected Alarm Sound"
    trigger:
      - platform: time
        at: "07:00:00"
    action:
      # Zoek URL van gekozen geluid
      - variables:
          selected_name: "{{ states('input_select.alarm_sound_choice') }}"
          sounds_data: "{{ states('input_text.agendaalarm_sounds') | from_json }}"
          selected_sound: >
            {{ sounds_data.sounds | selectattr('name', 'eq', selected_name) | first }}
          sound_url: "{{ selected_sound.streaming_url }}"
      
      # Speel geluid
      - service: media_player.play_media
        target:
          entity_id: media_player.bedroom
        data:
          media_content_id: "{{ sound_url }}"
          media_content_type: "music"
```

### Dashboard Card
```yaml
# Lovelace dashboard
type: vertical-stack
cards:
  # Sound selector
  - type: entities
    title: Alarm Sound
    entities:
      - entity: input_select.alarm_sound_choice
        name: Kies Geluid
  
  # Sound info
  - type: markdown
    content: >
      **Beschikbare geluiden:** {{ states('sensor.agendaalarm_sound_list') }}
      
      **Laatst gesynchroniseerd:** {{ state_attr('sensor.agendaalarm_sound_list', 'last_sync') }}
  
  # Test button
  - type: button
    name: Test Geluid
    icon: mdi:play
    tap_action:
      action: call-service
      service: script.test_alarm_sound

# Script voor test
script:
  test_alarm_sound:
    alias: "Test Alarm Sound"
    sequence:
      - variables:
          selected_name: "{{ states('input_select.alarm_sound_choice') }}"
          sounds_data: "{{ states('input_text.agendaalarm_sounds') | from_json }}"
          selected_sound: >
            {{ sounds_data.sounds | selectattr('name', 'eq', selected_name) | first }}
          sound_url: "{{ selected_sound.streaming_url }}"
      
      - service: media_player.play_media
        target:
          entity_id: media_player.bedroom
        data:
          media_content_id: "{{ sound_url }}"
          media_content_type: "music"
```

---

## 💻 Code Gebruik

### In je Settings Screen
```kotlin
import com.redubbledd.agendawekker.ui.components.SoundSyncButton

@Composable
fun HomeAssistantSettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ... andere settings ...
        
        // Geluiden Sync knop
        SoundSyncButton()
        
        // ... meer settings ...
    }
}
```

### Programmatisch Sync
```kotlin
// Sync alle geluiden
scope.launch {
    val result = SoundSyncHelper.syncAllSounds(context)
    
    if (result.isSuccess) {
        Toast.makeText(context, result.getOrNull(), Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Fout: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
    }
}

// Preview van wat gesynchroniseerd wordt
scope.launch {
    val preview = SoundSyncHelper.getSyncPreview(context)
    println("Kan synchroniseren: ${preview.canSync}")
    println("Aantal geluiden: ${preview.soundCount}")
    println("Device IP: ${preview.deviceIp}")
}
```

---

## 🔍 Troubleshooting

### Issue: "HTTP Server moet ingeschakeld zijn"
**Oplossing:**
1. Ga naar Settings → HTTP Server
2. Enable HTTP Server
3. Noteer API key
4. Probeer opnieuw

### Issue: "Home Assistant sync moet ingeschakeld zijn"
**Oplossing:**
1. Ga naar Settings → Home Assistant
2. Enable Sync
3. Vul HA URL in
4. Probeer opnieuw

### Issue: "Geen geluiden gevonden"
**Oplossing:**
1. Voeg custom geluiden toe in app
2. Check of geluiden zichtbaar zijn in app
3. Probeer sync opnieuw

### Issue: "Device IP niet verbonden"
**Oplossing:**
1. Check WiFi verbinding
2. Zorg dat phone en HA op zelfde netwerk
3. Herstart app
4. Probeer opnieuw

### Issue: Webhook niet getriggerd in HA
**Oplossing:**
1. Check webhook ID: `agendaalarm_sound_sync`
2. Verify automation bestaat
3. Check HA logs: Settings → System → Logs
4. Test webhook manually:
   ```bash
   curl -X POST http://192.168.1.50:8123/api/webhook/agendaalarm_sound_sync \
        -H "Content-Type: application/json" \
        -d '{"test": true, "total_count": 0, "sounds": []}'
   ```

---

## ✅ Voordelen

### vs. File Upload
- ✅ **Geen duplicatie** - Files blijven op phone
- ✅ **Altijd up-to-date** - Nieuwe geluiden direct beschikbaar na sync
- ✅ **Geen storage** - Neemt geen ruimte in op HA server
- ✅ **Simpeler** - Geen file management in HA

### vs. Manual Configuration
- ✅ **Automatisch** - Geen handmatig URLs typen
- ✅ **Correct IP** - Device IP automatisch gedetecteerd
- ✅ **API key included** - Geen handmatig key kopiëren
- ✅ **Alle geluiden** - Mist geen geluiden

---

## 🎯 Workflow

```
┌─────────────────────────────────────┐
│     AgendaAlarm App                 │
│                                     │
│  User clicks "Geluiden Sync"       │
│         ↓                           │
│  1. Get all sounds from repository │
│  2. Get device IP (WiFi)           │
│  3. Get API key                    │
│  4. Build streaming URLs           │
│  5. Create JSON payload            │
│         ↓                           │
│  POST to webhook                   │
└─────────────────┬───────────────────┘
                  │
                  │ HTTP POST
                  │ /api/webhook/agendaalarm_sound_sync
                  │
┌─────────────────▼───────────────────┐
│     Home Assistant                  │
│                                     │
│  Webhook triggered                 │
│         ↓                           │
│  1. Receive JSON                   │
│  2. Store in input_text            │
│  3. Update template sensors        │
│  4. Update input_select options    │
│         ↓                           │
│  Sounds available in automations   │
└─────────────────────────────────────┘
```

---

## 📊 Samenvatting

**Wat het doet:**
- Stuurt metadata van alle geluiden naar HA
- Inclusief streaming URLs met device IP en API key
- HA kan geluiden gebruiken in automations

**Wanneer gebruiken:**
- Na toevoegen van nieuwe geluiden
- Na wijzigen van HA URL
- Na regenereren van API key
- Bij eerste setup

**Voordelen:**
- ✅ Automatische URL generatie
- ✅ Geen handmatig werk
- ✅ Altijd correcte URLs
- ✅ Makkelijk te gebruiken

**Status:** ✅ Klaar voor gebruik!
