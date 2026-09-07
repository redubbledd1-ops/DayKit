# Home Assistant Integratie - Simpele Setup

## 🎯 Wat doet dit?

De app stuurt **het volgende alarm** naar Home Assistant, zodat HA weet wanneer je alarm afgaat en daar automations op kan maken.

**Voorbeeld:** Alarm om 07:00
- 06:30 → HA dimmt lichten
- 06:45 → HA zet koffie
- 07:00 → HA zet lichten aan + speelt alarm geluid

---

## 🔧 Setup (5 minuten)

### Stap 1: In Home Assistant

#### 1.1 Maak Webhook Automation
```yaml
# configuration.yaml of automations.yaml
automation:
  - alias: "AgendaAlarm - Next Alarm Updated"
    trigger:
      - platform: webhook
        webhook_id: agendaalarm_next_alarm
    action:
      # Sla alarm info op in input helpers
      - service: input_datetime.set_datetime
        target:
          entity_id: input_datetime.next_alarm_time
        data:
          datetime: "{{ trigger.json.alarm_time }}"
      
      - service: input_text.set_value
        target:
          entity_id: input_text.next_alarm_name
        data:
          value: "{{ trigger.json.alarm_name }}"
      
      # Log in Home Assistant
      - service: system_log.write
        data:
          message: "Next alarm: {{ trigger.json.alarm_name }} at {{ trigger.json.alarm_time }}"
          level: info
```

#### 1.2 Maak Input Helpers
```yaml
# configuration.yaml
input_datetime:
  next_alarm_time:
    name: "Next Alarm Time"
    has_date: true
    has_time: true

input_text:
  next_alarm_name:
    name: "Next Alarm Name"
    max: 255
```

#### 1.3 Herstart Home Assistant
```
Developer Tools → YAML → Check Configuration → Restart
```

---

### Stap 2: In AgendaAlarm App

#### 2.1 Enable Home Assistant Sync
```
Settings → Home Assistant → Enable Sync ✓
```

#### 2.2 Vul Home Assistant URL in
```
Settings → Home Assistant → Home Assistant URL
Voer in: http://192.168.1.50:8123
```

**Voorbeelden:**
- `http://192.168.1.50:8123` (lokaal IP)
- `http://homeassistant.local:8123` (mDNS)
- `http://192.168.1.50:8123` (custom port)

#### 2.3 Test Verbinding
```
Settings → Home Assistant → Test Connection
```

**Expected:** "Connection successful!" message

---

## 📱 Gebruik

### Wanneer je een alarm instelt:
1. Stel alarm in app in (bijv. 07:00)
2. App stuurt automatisch naar Home Assistant
3. HA ontvangt alarm info via webhook
4. HA kan nu automations maken

### Alarm info die wordt gestuurd:
```json
{
  "alarm_time": "2024-12-11T07:00:00",
  "alarm_time_millis": 1702274400000,
  "alarm_name": "Morning Alarm",
  "sound_name": "Gentle Wake",
  "sound_url": "http://192.168.1.100:8765/sounds/1",
  "calendar_name": "Work Calendar",
  "event_title": "Team Meeting",
  "enabled": true
}
```

---

## 🏠 Home Assistant Automations

### Voorbeeld 1: Lichten 30 min voor alarm
```yaml
automation:
  - alias: "Pre-Alarm Lights"
    trigger:
      - platform: template
        value_template: >
          {{ (as_timestamp(states('input_datetime.next_alarm_time')) - 1800) 
             == as_timestamp(now()) }}
    action:
      - service: light.turn_on
        target:
          entity_id: light.bedroom
        data:
          brightness_pct: 20
          transition: 300
```

### Voorbeeld 2: Koffie 15 min voor alarm
```yaml
automation:
  - alias: "Pre-Alarm Coffee"
    trigger:
      - platform: template
        value_template: >
          {{ (as_timestamp(states('input_datetime.next_alarm_time')) - 900) 
             == as_timestamp(now()) }}
    action:
      - service: switch.turn_on
        target:
          entity_id: switch.coffee_maker
```

### Voorbeeld 3: Alarm geluid afspelen op HA speakers
```yaml
automation:
  - alias: "Play Alarm Sound on HA"
    trigger:
      - platform: template
        value_template: >
          {{ as_timestamp(states('input_datetime.next_alarm_time')) 
             == as_timestamp(now()) }}
    action:
      # Speel alarm geluid van app
      - service: media_player.play_media
        target:
          entity_id: media_player.bedroom
        data:
          media_content_id: "{{ state_attr('sensor.agendaalarm_next_alarm', 'sound_url') }}"
          media_content_type: "music"
      
      # Zet lichten aan
      - service: light.turn_on
        target:
          entity_id: light.bedroom
        data:
          brightness_pct: 100
```

### Voorbeeld 4: Complete Morning Routine
```yaml
automation:
  - alias: "Morning Routine"
    trigger:
      - platform: template
        value_template: >
          {{ as_timestamp(states('input_datetime.next_alarm_time')) 
             == as_timestamp(now()) }}
    action:
      # Lichten aan
      - service: light.turn_on
        target:
          entity_id: 
            - light.bedroom
            - light.hallway
        data:
          brightness_pct: 100
          transition: 10
      
      # Open gordijnen
      - service: cover.open_cover
        target:
          entity_id: cover.bedroom_blinds
      
      # Start muziek
      - service: media_player.play_media
        target:
          entity_id: media_player.bedroom
        data:
          media_content_id: "{{ state_attr('sensor.agendaalarm_next_alarm', 'sound_url') }}"
          media_content_type: "music"
      
      # Zet verwarming hoger
      - service: climate.set_temperature
        target:
          entity_id: climate.bedroom
        data:
          temperature: 21
```

---

## 🎵 Alarm Geluiden Delen

### Optie 1: Via HTTP Server (Aanbevolen)

#### Stap 1: Enable HTTP Server in App
```
Settings → HTTP Server → Enable HTTP Server ✓
```

#### Stap 2: Noteer Server URL
```
Server Address: http://192.168.1.100:8765
API Key: abc123def456...
```

#### Stap 3: Gebruik in Home Assistant
```yaml
# Speel alarm geluid van app
service: media_player.play_media
data:
  media_content_id: "http://192.168.1.100:8765/sounds/1?api_key=YOUR_KEY"
  media_content_type: "music"
```

**Voordelen:**
- ✅ Direct streamen van app
- ✅ Geen files kopiëren
- ✅ Altijd up-to-date

### Optie 2: Files Handmatig Kopiëren

#### Stap 1: Vind Alarm Files op Phone
```bash
adb shell run-as com.redubbledd.agendawekker
cd files/alarm_sounds/custom
ls -la
```

#### Stap 2: Kopieer naar Computer
```bash
adb pull /data/data/com.redubbledd.agendawekker/files/alarm_sounds/custom/sound.mp3 .
```

#### Stap 3: Upload naar Home Assistant
```
Home Assistant → Media → Upload → sound.mp3
```

#### Stap 4: Gebruik in Automation
```yaml
service: media_player.play_media
data:
  media_content_id: "media-source://media_source/local/sound.mp3"
  media_content_type: "music"
```

---

## 📊 Sensor voor Dashboard

### Template Sensor
```yaml
# configuration.yaml
template:
  - sensor:
      - name: "Next Alarm"
        state: "{{ states('input_datetime.next_alarm_time') }}"
        attributes:
          alarm_name: "{{ states('input_text.next_alarm_name') }}"
          time_until: >
            {% set alarm = states('input_datetime.next_alarm_time') %}
            {% if alarm != 'unknown' %}
              {{ (as_timestamp(alarm) - as_timestamp(now())) | int }}
            {% else %}
              0
            {% endif %}
          formatted_time: >
            {% set alarm = states('input_datetime.next_alarm_time') %}
            {% if alarm != 'unknown' %}
              {{ as_timestamp(alarm) | timestamp_custom('%H:%M') }}
            {% else %}
              No alarm set
            {% endif %}
```

### Dashboard Card
```yaml
# Lovelace dashboard
type: entities
title: Next Alarm
entities:
  - entity: sensor.next_alarm
    name: Alarm Time
    icon: mdi:alarm
  - type: attribute
    entity: sensor.next_alarm
    attribute: alarm_name
    name: Alarm Name
  - type: attribute
    entity: sensor.next_alarm
    attribute: formatted_time
    name: Time
```

---

## 🔍 Troubleshooting

### Issue: "Connection failed"
**Oplossing:**
1. Check Home Assistant URL correct is
2. Verify Home Assistant bereikbaar is: `ping 192.168.1.50`
3. Check firewall settings
4. Test in browser: `http://192.168.1.50:8123`

### Issue: "Webhook not triggered"
**Oplossing:**
1. Check webhook ID: `agendaalarm_next_alarm`
2. Verify automation bestaat in HA
3. Check HA logs: `Settings → System → Logs`
4. Test webhook manually:
   ```bash
   curl -X POST http://192.168.1.50:8123/api/webhook/agendaalarm_next_alarm \
        -H "Content-Type: application/json" \
        -d '{"test": true}'
   ```

### Issue: "Sound URL not working"
**Oplossing:**
1. Enable HTTP Server in app
2. Check API key correct is
3. Test URL in browser: `http://192.168.1.100:8765/sounds/1?api_key=KEY`
4. Verify phone en HA op zelfde netwerk

---

## 🔐 Security

### Webhook
- ✅ Webhook ID is uniek per gebruiker
- ✅ Alleen toegankelijk op lokaal netwerk
- ✅ Geen gevoelige data in payload

### HTTP Server
- ✅ Opt-in (disabled by default)
- ✅ API key required
- ✅ Alleen lokaal netwerk

**Aanbeveling:** Gebruik alleen op vertrouwd netwerk (thuis)

---

## ✅ Checklist

### Setup
- [ ] Home Assistant automation gemaakt
- [ ] Input helpers aangemaakt
- [ ] HA herstart
- [ ] App sync enabled
- [ ] HA URL ingevuld
- [ ] Verbinding getest

### Optional (Sounds)
- [ ] HTTP server enabled
- [ ] API key genoteerd
- [ ] Sound URL getest
- [ ] Media player automation gemaakt

### Testing
- [ ] Stel test alarm in
- [ ] Check HA logs voor webhook
- [ ] Verify input helpers updated
- [ ] Test automation triggered

---

## 🎯 Samenvatting

**Wat je nodig hebt:**
1. Home Assistant met webhook automation
2. AgendaAlarm app met HA sync enabled
3. HA URL ingevuld in app

**Wat het doet:**
- App stuurt volgend alarm naar HA
- HA kan automations maken (lichten, koffie, etc.)
- Optioneel: alarm geluiden streamen via HTTP server

**Voordelen:**
- ✅ Automatische sync
- ✅ Geen handmatig werk
- ✅ Flexibele automations
- ✅ Alarm geluiden beschikbaar in HA

**Status:** ✅ Klaar voor gebruik!
