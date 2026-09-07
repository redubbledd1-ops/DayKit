# Geluiden Sync - Simpel Stappenplan

## 📱 Waar vind je de knop?

**In de app:**
```
Settings → Home Assistant → (scroll naar beneden)
```

De "**Geluiden Synchroniseren**" knop staat tussen:
- ⬆️ "Uit bed check"  
- ⬇️ "Test verbinding" knoppen

---

## 🏠 Home Assistant Setup (5 minuten)

### Stap 1: Maak Input Helper
Open Home Assistant en ga naar:
```
Settings → Devices & Services → Helpers → Create Helper → Text
```

**Configuratie:**
- **Name**: `AgendaAlarm Sounds JSON`
- **Entity ID**: `input_text.agendaalarm_sounds`
- **Max length**: `65535` (belangrijk!)
- **Initial value**: `{}`

Klik **Create**.

---

### Stap 2: Maak Webhook Automation

Ga naar:
```
Settings → Automations & Scenes → Create Automation → Start with an empty automation
```

**Klik op de 3 puntjes rechtsboven → Edit in YAML** en plak:

```yaml
alias: "AgendaAlarm - Geluiden Sync"
description: "Ontvangt geluiden van AgendaAlarm app"
trigger:
  - platform: webhook
    webhook_id: agendaalarm_sound_sync
action:
  - service: input_text.set_value
    target:
      entity_id: input_text.agendaalarm_sounds
    data:
      value: "{{ trigger.json | to_json }}"
  - service: system_log.write
    data:
      message: "AgendaAlarm: {{ trigger.json.total_count }} geluiden gesynchroniseerd"
      level: info
mode: single
```

Klik **Save**.

---

### Stap 3: Herstart Home Assistant

```
Developer Tools → YAML → Check Configuration → Restart
```

Wacht tot HA opnieuw opstart (~1 minuut).

---

## 📱 App Configuratie

### Stap 1: Enable HTTP Server
```
Settings → HTTP Server → Enable HTTP Server ✓
```

**Noteer:**
- API Key (bijv. `abc123def456...`)
- Server Address (bijv. `http://192.168.1.100:8765`)

### Stap 2: Enable Home Assistant Sync
```
Settings → Home Assistant → Enable Sync ✓
```

### Stap 3: Vul Home Assistant URL in
```
Settings → Home Assistant → Home Assistant URL
```

**Voer in** (vervang met jouw IP):
```
http://192.168.1.50:8123
```

### Stap 4: Sync Geluiden
```
Settings → Home Assistant → Geluiden Synchroniseren (knop)
```

**De knop toont:**
- 🎵 Aantal geluiden (bijv. "3")
- 📡 Device IP (bijv. "192.168.1.100")
- 🏠 HA URL (bijv. "http://192.168.1.50:8123")

**Klik op de knop** → Wacht op "✓ 3 geluiden gesynchroniseerd"

---

## ✅ Controleren of het Werkt

### In Home Assistant:

**Optie 1: Check Helper**
```
Developer Tools → States → Zoek: input_text.agendaalarm_sounds
```

Je zou JSON moeten zien zoals:
```json
{
  "sounds": [
    {
      "id": 1,
      "name": "Morning Alarm",
      "duration": 45000,
      "streaming_url": "http://192.168.1.100:8765/sounds/1?api_key=abc123"
    }
  ],
  "total_count": 3
}
```

**Optie 2: Check Automation Logs**
```
Settings → Automations → AgendaAlarm - Geluiden Sync → (klik) → History
```

Je zou moeten zien: "Last triggered: just now"

---

## 🎵 Geluiden Gebruiken in Home Assistant

### Voorbeeld 1: Speel Geluid op Speaker

```yaml
# automation.yaml
automation:
  - alias: "Test Alarm Geluid"
    trigger:
      - platform: time
        at: "07:00:00"
    action:
      # Haal eerste geluid op
      - variables:
          sounds_data: "{{ states('input_text.agendaalarm_sounds') | from_json }}"
          first_sound: "{{ sounds_data.sounds[0] }}"
          sound_url: "{{ first_sound.streaming_url }}"
      
      # Speel geluid
      - service: media_player.play_media
        target:
          entity_id: media_player.bedroom
        data:
          media_content_id: "{{ sound_url }}"
          media_content_type: "music"
```

### Voorbeeld 2: Kies Geluid via Input Select

**Stap 1: Maak Input Select**
```yaml
# configuration.yaml
input_select:
  alarm_sound:
    name: "Alarm Geluid"
    options:
      - "Morning Alarm"
      - "Gentle Wake"
    initial: "Morning Alarm"
```

**Stap 2: Update Options Automatisch**
```yaml
# automation.yaml
automation:
  - alias: "Update Alarm Sound Options"
    trigger:
      - platform: state
        entity_id: input_text.agendaalarm_sounds
    action:
      - service: input_select.set_options
        target:
          entity_id: input_select.alarm_sound
        data:
          options: >
            {% set data = trigger.to_state.state | from_json %}
            {{ data.sounds | map(attribute='name') | list }}
```

**Stap 3: Speel Gekozen Geluid**
```yaml
# automation.yaml
automation:
  - alias: "Speel Gekozen Alarm"
    trigger:
      - platform: time
        at: "07:00:00"
    action:
      - variables:
          selected_name: "{{ states('input_select.alarm_sound') }}"
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

## 🔄 Wanneer Opnieuw Syncen?

Sync opnieuw wanneer je:
- ✅ Nieuwe geluiden toevoegt in app
- ✅ Geluiden verwijdert in app
- ✅ Home Assistant URL wijzigt
- ✅ HTTP Server API key regenereert
- ✅ Device IP verandert (nieuw WiFi netwerk)

**Tip:** Sync is snel (< 1 seconde), dus je kunt het gerust vaker doen!

---

## ❌ Problemen Oplossen

### "HTTP Server moet ingeschakeld zijn"
**Oplossing:**
1. Ga naar Settings → HTTP Server
2. Zet "Enable HTTP Server" aan
3. Probeer opnieuw

### "Home Assistant sync moet ingeschakeld zijn"
**Oplossing:**
1. Ga naar Settings → Home Assistant
2. Zet "Enable Sync" aan
3. Vul HA URL in
4. Probeer opnieuw

### "Geen geluiden gevonden"
**Oplossing:**
1. Voeg custom geluiden toe in app
2. Check of ze zichtbaar zijn in app
3. Probeer sync opnieuw

### "Device IP niet verbonden"
**Oplossing:**
1. Check WiFi verbinding op phone
2. Zorg dat phone en HA op zelfde netwerk
3. Herstart app
4. Probeer opnieuw

### Webhook wordt niet getriggerd
**Oplossing:**
1. Check webhook ID in automation: `agendaalarm_sound_sync`
2. Check of automation enabled is
3. Check HA logs: Settings → System → Logs
4. Test webhook handmatig:
   ```bash
   curl -X POST http://192.168.1.50:8123/api/webhook/agendaalarm_sound_sync \
        -H "Content-Type: application/json" \
        -d '{"test": true, "total_count": 0, "sounds": [], "timestamp": 1234567890}'
   ```

### Geluid speelt niet af
**Oplossing:**
1. Check of HTTP Server enabled is in app
2. Test URL in browser: `http://192.168.1.100:8765/sounds/1?api_key=KEY`
3. Verify phone en HA op zelfde netwerk
4. Check HA media player werkt (test met andere audio)

---

## 📊 Samenvatting

### In Home Assistant (eenmalig):
1. ✅ Maak input helper: `input_text.agendaalarm_sounds`
2. ✅ Maak webhook automation met ID: `agendaalarm_sound_sync`
3. ✅ Herstart Home Assistant

### In App (eenmalig):
1. ✅ Enable HTTP Server
2. ✅ Enable HA Sync + vul URL in
3. ✅ Klik "Geluiden Synchroniseren"

### Gebruik in HA:
- Geluiden zijn beschikbaar via `input_text.agendaalarm_sounds`
- Stream via URL: `http://[phone-ip]:8765/sounds/[id]?api_key=[key]`
- Gebruik in automations met `media_player.play_media`

**Klaar!** 🎉

---

## 🎯 Snelle Checklist

- [ ] Input helper gemaakt (`input_text.agendaalarm_sounds`)
- [ ] Webhook automation gemaakt (ID: `agendaalarm_sound_sync`)
- [ ] Home Assistant herstart
- [ ] HTTP Server enabled in app
- [ ] HA Sync enabled in app
- [ ] HA URL ingevuld in app
- [ ] "Geluiden Synchroniseren" knop geklikt
- [ ] Success message gezien
- [ ] Input helper bevat JSON data
- [ ] Test automation gemaakt
- [ ] Geluid speelt af op speaker

**Alles werkt?** Perfect! Je kunt nu alarm geluiden van je app gebruiken in Home Assistant automations! 🎵
