# WiFi Permissions & Server Status Implementatie

## ✅ Wat Is Toegevoegd

### 1. **WiFi Permissions in Manifest** 📱

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

**Wat deze permissions doen:**
- `ACCESS_WIFI_STATE` - Lees WiFi status (verbonden/niet verbonden, SSID, IP adres)
- `CHANGE_WIFI_STATE` - Wijzig WiFi instellingen (niet gebruikt, maar beschikbaar)

**Belangrijk:** Deze permissions zijn **NIET runtime permissions**, dus geen gebruikerstoestemming nodig!

---

### 2. **WiFi Status Check Functies** 🔍

#### **`isWifiConnected(context)`**
```kotlin
HttpServerManager.isWifiConnected(context)
```

**Functionaliteit:**
- Checkt of device verbonden is met WiFi netwerk
- Gebruikt `ConnectivityManager` en `NetworkCapabilities`
- Logt status naar Logcat
- Returns `true` als WiFi verbonden, `false` anders

**Logging:**
```
D/HttpServerManager: WiFi connected: true
```

---

#### **`getWifiNetworkName(context)`**
```kotlin
val ssid = HttpServerManager.getWifiNetworkName(context)
// Returns: "MyHomeNetwork" of null
```

**Functionaliteit:**
- Haalt WiFi netwerk naam (SSID) op
- Verwijdert quotes uit SSID
- Logt SSID naar Logcat
- Returns `null` als niet verbonden

**Logging:**
```
D/HttpServerManager: WiFi SSID: MyHomeNetwork
```

---

### 3. **Server Status Object** 📊

#### **`getServerStatus(context)`**
```kotlin
val status = HttpServerManager.getServerStatus(context)
```

**Returns `ServerStatus` object:**
```kotlin
data class ServerStatus(
    val enabled: Boolean,        // Server ingeschakeld in settings
    val running: Boolean,        // Server draait momenteel
    val wifiConnected: Boolean,  // WiFi verbinding actief
    val deviceIp: String?,       // Device IP adres
    val port: Int,               // Server poort (8765)
    val hasApiKey: Boolean       // API key aanwezig
)
```

**Helper functies:**
```kotlin
// Check of alles werkt
status.isFullyOperational()  // true als alles OK

// Krijg lijst van problemen
status.getIssues()  // ["Geen WiFi verbinding", "Server draait niet"]
```

**Logging:**
```
I/HttpServerManager: Server status: ServerStatus(
    enabled=true, 
    running=true, 
    wifiConnected=true, 
    deviceIp=192.168.1.100, 
    port=8765, 
    hasApiKey=true
)
```

---

### 4. **Verbeterde Test Functionaliteit** 🧪

#### **Test HTTP Server Knop**

**Wat het nu doet:**
1. Haalt volledige server status op
2. Checkt of server volledig operationeel is
3. Toont WiFi netwerk naam
4. Toont IP adres en poort
5. Toont test URL
6. Logt alles naar Logcat

**Voorbeeld output bij success:**
```
✓ HTTP Server actief
WiFi: MyHomeNetwork
IP: 192.168.1.100:8765
Test URL: http://192.168.1.100:8765/sounds/1?api_key=abc123
```

**Voorbeeld output bij problemen:**
```
✗ Server niet volledig operationeel:
Geen WiFi verbinding
Server draait niet
```

**Logcat output:**
```
I/SoundSyncButton: Test - Server status: ServerStatus(...)
I/SoundSyncButton: Test URL: http://192.168.1.100:8765/sounds/1?api_key=abc123
```

---

## 🔍 Hoe Te Debuggen

### **Stap 1: Check Logcat**

**Filter op tag:**
```
HttpServerManager
SoundSyncButton
```

**Wat je zou moeten zien:**
```
I/HttpServerManager: Server status: ServerStatus(enabled=true, running=true, ...)
D/HttpServerManager: WiFi connected: true
D/HttpServerManager: WiFi SSID: MyHomeNetwork
I/SoundSyncButton: Server status: ServerStatus(...)
```

---

### **Stap 2: Test WiFi Status**

**In de app:**
1. Open Settings → Home Assistant
2. Scroll naar "Geluiden Synchroniseren"
3. Check "Device IP" veld

**Verwachte waarden:**
- **WiFi aan:** `192.168.1.100` (of ander lokaal IP)
- **WiFi uit:** `Niet verbonden`

---

### **Stap 3: Test Server Status**

**In de app:**
1. Klik "Test HTTP Server" knop
2. Lees de toast melding

**Bij problemen:**
- Check Logcat voor details
- Zie welke issues worden gerapporteerd
- Los op volgens foutmelding

---

## 📋 Troubleshooting

### **Probleem: "Geen WiFi verbinding"**

**Mogelijke oorzaken:**
1. WiFi is uitgeschakeld op device
2. Device is verbonden met mobiel netwerk
3. WiFi permissions ontbreken

**Oplossing:**
```
1. Check WiFi instellingen op phone
2. Verbind met WiFi netwerk
3. Check Logcat:
   D/HttpServerManager: WiFi connected: false
4. Als false, check WiFi settings
```

---

### **Probleem: "Geen IP adres"**

**Mogelijke oorzaken:**
1. WiFi verbonden maar geen IP toegewezen
2. DHCP problemen
3. WiFi net verbonden (IP nog niet toegewezen)

**Oplossing:**
```
1. Wacht 5-10 seconden na WiFi verbinding
2. Herstart app
3. Check Logcat:
   D/HttpServerManager: WiFi SSID: MyNetwork
   (maar geen IP)
4. Check WiFi instellingen → IP adres
```

---

### **Probleem: "Server draait niet"**

**Mogelijke oorzaken:**
1. Server niet ingeschakeld in settings
2. Server crashed
3. Poort al in gebruik

**Oplossing:**
```
1. Check Logcat:
   I/HttpServerManager: Server status: ServerStatus(enabled=true, running=false, ...)
2. Ga naar Settings → HTTP Server
3. Toggle uit en weer aan
4. Check Logcat voor errors
```

---

## 🧪 Test Scenario's

### **Test 1: WiFi Verbinding**

**Stappen:**
1. ✅ Zet WiFi uit op phone
2. ✅ Open app → HA settings → Geluiden Sync
3. ✅ Check "Device IP: Niet verbonden"
4. ✅ Check foutmelding: "Geen WiFi verbinding"
5. ✅ Zet WiFi aan
6. ✅ Sluit en heropen Geluiden Sync
7. ✅ Check "Device IP: 192.168.1.x"
8. ✅ Foutmelding verdwenen

**Logcat verwacht:**
```
D/HttpServerManager: WiFi connected: false
D/HttpServerManager: WiFi connected: true
D/HttpServerManager: WiFi SSID: MyNetwork
```

---

### **Test 2: Server Status**

**Stappen:**
1. ✅ Open app → HA settings → Geluiden Sync
2. ✅ Klik "Test HTTP Server"
3. ✅ Zie toast met WiFi naam, IP, en URL
4. ✅ Check Logcat voor volledige status
5. ✅ Kopieer test URL uit Logcat
6. ✅ Test URL in browser

**Logcat verwacht:**
```
I/SoundSyncButton: Server status: ServerStatus(enabled=true, running=true, wifiConnected=true, deviceIp=192.168.1.100, port=8765, hasApiKey=true)
I/SoundSyncButton: Test URL: http://192.168.1.100:8765/sounds/1?api_key=abc123
```

---

### **Test 3: Auto-Enable Server**

**Stappen:**
1. ✅ Zet HTTP Server uit (Settings → HTTP Server)
2. ✅ Vul HA URL in (Settings → HA → URLs)
3. ✅ Open Geluiden Sync
4. ✅ Zie toast: "HTTP Server automatisch ingeschakeld"
5. ✅ Check Settings → HTTP Server is nu aan
6. ✅ Check Logcat

**Logcat verwacht:**
```
I/HttpServerManager: HTTP server enabled
I/SoundSyncButton: Server status: ServerStatus(enabled=true, ...)
```

---

## 📊 Status Indicators

### **In UI:**

| Indicator | Betekenis | Actie |
|-----------|-----------|-------|
| `Device IP: 192.168.1.100` | WiFi verbonden | ✅ OK |
| `Device IP: Niet verbonden` | Geen WiFi | ❌ Verbind WiFi |
| `Home Assistant: http://...` | HA geconfigureerd | ✅ OK |
| `Home Assistant: Niet geconfigureerd` | Geen HA URL | ❌ Vul URL in |
| `Geluiden: 3` | Geluiden beschikbaar | ✅ OK |
| `Geluiden: 0` | Geen geluiden | ❌ Voeg toe |

---

### **In Logcat:**

| Log | Betekenis |
|-----|-----------|
| `WiFi connected: true` | WiFi werkt |
| `WiFi connected: false` | Geen WiFi |
| `WiFi SSID: MyNetwork` | Verbonden met netwerk |
| `Server status: ServerStatus(enabled=true, running=true, ...)` | Alles OK |
| `isFullyOperational: true` | Server volledig operationeel |
| `Issues: [Geen WiFi verbinding]` | Probleem gevonden |

---

## 🎯 Voordelen

### **Voor Gebruiker:**
- ✅ Duidelijke WiFi status
- ✅ Automatische server start
- ✅ Gedetailleerde test informatie
- ✅ WiFi netwerk naam zichtbaar

### **Voor Developer:**
- ✅ Volledige logging
- ✅ Server status object
- ✅ Makkelijk debuggen
- ✅ Duidelijke error messages

### **Voor Support:**
- ✅ Gebruiker kan zelf testen
- ✅ Logcat bevat alle info
- ✅ Specifieke foutmeldingen
- ✅ Test URL beschikbaar

---

## 🔧 Technische Details

### **WiFi Check Implementatie:**
```kotlin
fun isWifiConnected(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
        as? ConnectivityManager
    
    val network = connectivityManager?.activeNetwork
    val capabilities = connectivityManager?.getNetworkCapabilities(network)
    
    return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
}
```

**Waarom deze methode:**
- Werkt op alle Android versies (API 21+)
- Gebruikt moderne `NetworkCapabilities` API
- Betrouwbaarder dan oude `WifiManager.isWifiEnabled()`
- Checkt daadwerkelijke verbinding, niet alleen WiFi aan/uit

---

### **Server Status Check:**
```kotlin
fun getServerStatus(context: Context): ServerStatus {
    return ServerStatus(
        enabled = isEnabled(context),
        running = isRunning(),
        wifiConnected = isWifiConnected(context),
        deviceIp = getDeviceIpAddress(context),
        port = getPort(context),
        hasApiKey = getApiKey(context).isNotEmpty()
    )
}
```

**Voordelen:**
- Alle checks in één call
- Immutable data class
- Helper functies voor validatie
- Automatische logging

---

## ✅ Checklist

- [x] WiFi permissions toegevoegd aan manifest
- [x] `isWifiConnected()` functie geïmplementeerd
- [x] `getWifiNetworkName()` functie geïmplementeerd
- [x] `ServerStatus` data class gemaakt
- [x] `getServerStatus()` functie geïmplementeerd
- [x] Test knop gebruikt nieuwe status checks
- [x] Logging toegevoegd aan alle functies
- [x] Foutmeldingen verbeterd
- [x] WiFi naam getoond in test output
- [x] Documentatie compleet

---

## 🎉 Klaar Voor Gebruik!

De app heeft nu:
1. ✅ Correcte WiFi permissions
2. ✅ Betrouwbare WiFi status checks
3. ✅ Gedetailleerde server status
4. ✅ Uitgebreide logging
5. ✅ Verbeterde test functionaliteit
6. ✅ Duidelijke foutmeldingen

**Build de app en test!** 🚀
