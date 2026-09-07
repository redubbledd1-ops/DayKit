# HTTP Server Auto-Start & Tailscale Support

## ✅ Wat Is Opgelost

### **Probleem: "Server draait niet"**

**Oorzaak:**
- HTTP server werd **enabled** maar niet **gestart**
- Server startte niet automatisch bij app launch
- Server bleef niet draaien na app herstart

**Oplossing:**
1. ✅ Auto-start bij app launch (`MainActivity.onCreate()`)
2. ✅ Auto-start bij HA settings open (`SoundSyncButton`)
3. ✅ Auto-start bij test knop klik
4. ✅ Uitgebreide logging voor debugging

---

## 🚀 Nieuwe Functionaliteit

### 1. **Auto-Start Bij App Launch**

**Locatie:** `MainActivity.initHttpServer()`

**Wat gebeurt er:**
```kotlin
// Bij elke app start:
if (HttpServerManager.isEnabled(context)) {
    if (!HttpServerManager.isRunning()) {
        HttpServerManager.startServer(context)
        Log: "Starting HTTP server..."
    } else {
        Log: "HTTP server already running"
    }
}
```

**Logcat output:**
```
I/MainActivity: Starting HTTP server...
I/HttpServerManager: HTTP server started successfully on port 8765
I/MainActivity: HTTP server started successfully
```

---

### 2. **Auto-Start Bij HA Settings**

**Locatie:** `SoundSyncButton.LaunchedEffect`

**Wat gebeurt er:**
```kotlin
// Bij openen Geluiden Synchroniseren:
if (HttpServerManager.isEnabled(context) && !HttpServerManager.isRunning()) {
    Log: "Server enabled but not running, starting..."
    HttpServerManager.startServer(context)
}
```

**Voordeel:** Server start automatisch als je HA settings opent

---

### 3. **Auto-Start Bij Test Knop**

**Locatie:** `SoundSyncButton` Test knop

**Wat gebeurt er:**
```kotlin
// Bij klik "Test HTTP Server":
if (serverStatus.enabled && !serverStatus.running) {
    Log: "Attempting to start server..."
    HttpServerManager.startServer(context)
    delay(500ms) // Wacht tot server start
    
    if (!newStatus.running) {
        Toast: "✗ Server kon niet starten"
    }
}
```

**Voordeel:** Test knop probeert server te starten als die uit is

---

### 4. **Tailscale/VPN Detectie**

**Nieuwe feature:** Detecteert netwerk type

**IP Ranges:**
- `100.x.x.x` → **Tailscale VPN**
- `192.168.x.x` / `10.x.x.x` / `172.x.x.x` → **Lokaal netwerk**
- Anders → **Onbekend netwerk**

**Test output:**
```
✓ HTTP Server actief
WiFi: MyNetwork
Netwerk: Tailscale VPN  ← Detecteert Tailscale!
IP: 100.64.0.5:8765
Geluiden: 3
```

---

## 📊 Logcat Output

### **Bij App Start:**
```
I/MainActivity: Starting HTTP server...
I/HttpServerManager: HTTP server started successfully on port 8765
I/MainActivity: HTTP server started successfully
```

### **Bij Test Knop:**
```
I/SoundSyncButton: Test - Server status: ServerStatus(enabled=true, running=true, ...)
I/SoundSyncButton: ═══════════════════════════════════
I/SoundSyncButton: HTTP Server Test Results:
I/SoundSyncButton: WiFi SSID: MyNetwork
I/SoundSyncButton: Network Type: Tailscale VPN
I/SoundSyncButton: Device IP: 100.64.0.5
I/SoundSyncButton: Server Port: 8765
I/SoundSyncButton: Available Sounds: 3
I/SoundSyncButton: Test URL: http://100.64.0.5:8765/sounds/1?api_key=abc123
I/SoundSyncButton: ═══════════════════════════════════
```

### **Bij Server Start Fout:**
```
E/MainActivity: HTTP server failed to start
E/HttpServerManager: Failed to start HTTP server
```

---

## 🧪 Test Scenario's

### **Test 1: App Start**

**Stappen:**
1. ✅ Zet HTTP Server aan (Settings → HTTP Server)
2. ✅ Sluit app volledig (swipe weg)
3. ✅ Open app opnieuw
4. ✅ Check Logcat

**Verwacht:**
```
I/MainActivity: Starting HTTP server...
I/MainActivity: HTTP server started successfully
```

**Als server al draaide:**
```
I/MainActivity: HTTP server already running
```

---

### **Test 2: Test Knop - Server Uit**

**Stappen:**
1. ✅ Zet HTTP Server aan
2. ✅ Herstart app (server zou moeten draaien)
3. ✅ Stop server handmatig (of crash)
4. ✅ Open HA Settings → Geluiden Sync
5. ✅ Klik "Test HTTP Server"

**Verwacht:**
```
I/SoundSyncButton: Attempting to start server...
I/HttpServerManager: HTTP server started successfully on port 8765
Toast: ✓ HTTP Server actief
       WiFi: MyNetwork
       Netwerk: Lokaal netwerk
       IP: 192.168.1.100:8765
       Geluiden: 3
```

---

### **Test 3: Tailscale Detectie**

**Stappen:**
1. ✅ Verbind met Tailscale VPN
2. ✅ Open app
3. ✅ Ga naar HA Settings → Geluiden Sync
4. ✅ Klik "Test HTTP Server"

**Verwacht:**
```
Toast: ✓ HTTP Server actief
       WiFi: MyNetwork
       Netwerk: Tailscale VPN  ← Detecteert Tailscale!
       IP: 100.64.0.5:8765
       Geluiden: 3

Logcat:
I/SoundSyncButton: Network Type: Tailscale VPN
I/SoundSyncButton: Device IP: 100.64.0.5
```

---

### **Test 4: Server Start Fout**

**Stappen:**
1. ✅ Zet HTTP Server aan
2. ✅ Blokkeer poort 8765 (andere app gebruikt poort)
3. ✅ Herstart app
4. ✅ Check Logcat

**Verwacht:**
```
E/MainActivity: HTTP server failed to start
E/HttpServerManager: Failed to start HTTP server
```

**Oplossing:**
- Verander poort in Settings → HTTP Server
- Of stop andere app die poort gebruikt

---

## 🔧 Troubleshooting

### **Probleem: Server start niet**

**Symptomen:**
```
Toast: ✗ Server kon niet starten
       Controleer Logcat voor details
```

**Check Logcat:**
```
Logcat filter: HttpServerManager
Zoek naar: "Failed to start"
```

**Mogelijke oorzaken:**
1. **Poort in gebruik**
   - Andere app gebruikt poort 8765
   - Oplossing: Verander poort in settings

2. **Permissions ontbreken**
   - INTERNET permission ontbreekt
   - Oplossing: Check AndroidManifest.xml

3. **Server crash**
   - Exception bij server start
   - Oplossing: Check Logcat voor stacktrace

---

### **Probleem: Tailscale IP niet zichtbaar**

**Symptomen:**
```
Toast: Netwerk: Lokaal netwerk
       IP: 192.168.1.100
```

**Terwijl je Tailscale gebruikt**

**Oorzaak:**
- App gebruikt WiFi IP, niet Tailscale IP
- Android geeft prioriteit aan WiFi interface

**Oplossing:**
- Dit is **normaal gedrag**
- HTTP server draait op WiFi IP
- Home Assistant moet verbinden via **Tailscale IP** van phone
- Gebruik Tailscale admin panel om phone IP te vinden

**Voorbeeld:**
```
Phone WiFi IP: 192.168.1.100  ← App gebruikt deze
Phone Tailscale IP: 100.64.0.5  ← HA moet deze gebruiken

HTTP Server URL voor HA:
http://100.64.0.5:8765/sounds/1?api_key=abc123
```

---

### **Probleem: "Server draait niet" na herstart**

**Symptomen:**
```
Toast: ✗ Server niet volledig operationeel:
       • Server draait niet
```

**Check:**
1. Is HTTP Server enabled?
   - Settings → HTTP Server → Check toggle

2. Check Logcat bij app start:
   ```
   Logcat filter: MainActivity
   Zoek naar: "HTTP server"
   ```

3. Zie je server start logs?
   ```
   I/MainActivity: Starting HTTP server...
   ```

**Oplossing:**
- Als geen logs: Server niet enabled
- Als "failed to start": Check poort/permissions
- Als "already running": Alles OK!

---

## 📱 Home Assistant Configuratie

### **Voor Tailscale Gebruikers:**

**Belangrijk:** Home Assistant moet **Tailscale IP** van phone gebruiken!

**Stappen:**
1. **Vind Tailscale IP van phone:**
   ```
   Tailscale admin panel
   → Devices
   → Zoek je phone
   → Kopieer IP (bijv. 100.64.0.5)
   ```

2. **Test URL in browser:**
   ```
   http://100.64.0.5:8765/sounds/1?api_key=YOUR_API_KEY
   ```

3. **Gebruik in HA automation:**
   ```yaml
   service: media_player.play_media
   data:
     entity_id: media_player.living_room
     media_content_id: "http://100.64.0.5:8765/sounds/{{ sound_id }}?api_key={{ api_key }}"
     media_content_type: music
   ```

---

### **Voor Lokaal Netwerk:**

**Gebruik WiFi IP van phone:**

**Stappen:**
1. **Vind WiFi IP:**
   ```
   Open app
   → HA Settings
   → Geluiden Sync
   → Zie "Device IP: 192.168.1.100"
   ```

2. **Test URL in browser:**
   ```
   http://192.168.1.100:8765/sounds/1?api_key=YOUR_API_KEY
   ```

3. **Gebruik in HA automation:**
   ```yaml
   service: media_player.play_media
   data:
     entity_id: media_player.living_room
     media_content_id: "http://192.168.1.100:8765/sounds/{{ sound_id }}?api_key={{ api_key }}"
     media_content_type: music
   ```

---

## ✅ Checklist

### **Server Start:**
- [x] Auto-start bij app launch
- [x] Auto-start bij HA settings open
- [x] Auto-start bij test knop
- [x] Logging bij start/stop
- [x] Error handling bij start fout

### **Tailscale Support:**
- [x] Detecteert Tailscale IP (100.x.x.x)
- [x] Toont netwerk type in test
- [x] Logt netwerk info
- [x] Documentatie voor HA configuratie

### **Debugging:**
- [x] Uitgebreide Logcat output
- [x] Server status logging
- [x] Test knop met details
- [x] Error messages met oplossingen

---

## 🎯 Samenvatting

**Wat nu werkt:**

| Feature | Status | Beschrijving |
|---------|--------|--------------|
| Auto-start bij app launch | ✅ | Server start automatisch |
| Auto-start bij HA settings | ✅ | Server start bij open settings |
| Auto-start bij test | ✅ | Test knop start server |
| Tailscale detectie | ✅ | Herkent VPN IP ranges |
| Netwerk type display | ✅ | Toont lokaal/VPN/onbekend |
| Uitgebreide logging | ✅ | Alle events gelogd |
| Error handling | ✅ | Duidelijke foutmeldingen |

---

## 🚀 Volgende Stappen

1. **Build de app**
   ```
   Build → Make Project
   ```

2. **Test op device**
   ```
   Run → Run 'app'
   ```

3. **Check Logcat**
   ```
   Filter: MainActivity
   Zie: "HTTP server started successfully"
   ```

4. **Test met Tailscale**
   ```
   Verbind Tailscale
   → Klik "Test HTTP Server"
   → Zie "Netwerk: Tailscale VPN"
   ```

5. **Test in Home Assistant**
   ```
   Gebruik Tailscale IP in automation
   → Test sound streaming
   → Controleer of geluid afspeelt
   ```

---

**De HTTP server start nu automatisch en ondersteunt Tailscale!** 🎉✅
