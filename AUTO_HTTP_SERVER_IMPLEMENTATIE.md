# Auto HTTP Server & Test Functionaliteit - Implementatie

## ✅ Wat Is Geïmplementeerd

### 1. **Auto-Start HTTP Server** 🚀

**Wanneer:**
- Gebruiker heeft Home Assistant URL ingevuld
- HTTP Server is nog niet ingeschakeld
- Device heeft WiFi verbinding

**Wat gebeurt er:**
```kotlin
// Automatisch bij openen van HA settings
if (hasUrl && !httpServerEnabled && hasWifi) {
    HttpServerManager.setEnabled(context, true)
    Toast: "HTTP Server automatisch ingeschakeld voor Home Assistant"
}
```

**Voordeel:**
- Gebruiker hoeft niet handmatig HTTP server in te schakelen
- Werkt automatisch zodra HA is geconfigureerd
- Voorkomt verwarring over waarom sync niet werkt

---

### 2. **Test HTTP Server Knop** 🧪

**Locatie:**
```
Settings → Home Assistant → Geluiden Synchroniseren
```

**Functionaliteit:**
- Verschijnt alleen als alles klaar is voor sync
- Test of HTTP server actief is
- Toont streaming URL voor eerste geluid
- Geeft directe feedback

**Voorbeeld output:**
```
✓ HTTP Server actief
Test URL: http://192.168.1.100:8765/sounds/1?api_key=abc123def456
```

**Gebruik:**
1. Klik "Test HTTP Server"
2. Kopieer de URL uit de toast
3. Test in browser of HA automation

---

### 3. **Verbeterde Foutmeldingen** 📋

#### **Geen WiFi Verbinding**
```
• Geen WiFi verbinding
  Controleer je netwerkverbinding
```

**Betekenis:** Phone niet verbonden met WiFi netwerk

**Oplossing:** 
- Check WiFi instellingen
- Verbind met zelfde netwerk als Home Assistant

---

#### **Home Assistant URL Niet Ingevuld**
```
• Home Assistant URL niet ingevuld
  Vul eerst je HA URL in
```

**Betekenis:** HA URL is leeg in settings

**Oplossing:**
1. Ga naar Settings → Home Assistant
2. Klik "URLs"
3. Voeg HA URL toe (bijv. `http://192.168.1.50:8123`)

---

#### **HTTP Server Uitgeschakeld**
```
• HTTP Server uitgeschakeld
  Server wordt automatisch ingeschakeld bij sync
```

**Betekenis:** Server is uit, maar wordt automatisch aan gezet

**Oplossing:** 
- Niets! Gebeurt automatisch bij sync
- Of: Ga naar Settings → HTTP Server → Enable

---

#### **Geen Geluiden Beschikbaar**
```
• Geen geluiden beschikbaar
  Voeg eerst custom geluiden toe in de app
```

**Betekenis:** App heeft geen custom sounds om te delen

**Oplossing:**
1. Open app
2. Ga naar geluid selectie
3. Voeg custom geluiden toe via "+"

---

### 4. **Verbeterde Success Meldingen** ✅

#### **Bij Succesvolle Sync:**
```
✓ Verbinding succesvol: 3 geluiden kunnen nu gestreamd worden
```

**Toast:**
```
✓ 3 geluiden gesynchroniseerd met Home Assistant!
```

#### **Bij HTTP Fout:**
```
✗ Home Assistant antwoordde met HTTP 404. 
  Controleer of de webhook automation actief is.
```

**Betekenis:** HA webhook bestaat niet of is uitgeschakeld

**Oplossing:**
1. Check of webhook automation bestaat in HA
2. Check of webhook ID correct is: `agendaalarm_sound_sync`
3. Herstart HA indien nodig

---

## 🔄 Workflow

### **Eerste Keer Setup:**

1. **Gebruiker vult HA URL in**
   ```
   Settings → Home Assistant → URLs → http://192.168.1.50:8123
   ```

2. **Gebruiker opent "Geluiden Synchroniseren"**
   - HTTP Server wordt **automatisch** ingeschakeld
   - Toast: "HTTP Server automatisch ingeschakeld voor Home Assistant"

3. **Gebruiker ziet status:**
   ```
   Geluiden: 3
   Device IP: 192.168.1.100
   Home Assistant: http://192.168.1.50:8123
   ```

4. **Gebruiker test (optioneel):**
   - Klik "Test HTTP Server"
   - Krijgt streaming URL
   - Kan testen in browser

5. **Gebruiker synct:**
   - Klik "Geluiden Synchroniseren"
   - Wacht op success
   - Klaar! ✅

---

### **Bij Problemen:**

#### **Geen WiFi:**
```
Status toont:
  Device IP: Niet verbonden
  
Foutmelding:
  • Geen WiFi verbinding
    Controleer je netwerkverbinding
```

**Fix:** Verbind phone met WiFi

---

#### **HA Niet Bereikbaar:**
```
Bij sync:
  ✗ Failed to connect to /192.168.1.50:8123
  
Of:
  ✗ Home Assistant antwoordde met HTTP 404
```

**Fix:** 
- Check HA draait
- Check HA URL correct is
- Check phone en HA op zelfde netwerk

---

#### **Webhook Niet Actief:**
```
Bij sync:
  ✗ Home Assistant antwoordde met HTTP 404.
    Controleer of de webhook automation actief is.
```

**Fix:**
1. Open HA
2. Settings → Automations
3. Zoek "AgendaAlarm - Geluiden Sync"
4. Check of enabled
5. Check webhook ID: `agendaalarm_sound_sync`

---

## 🎯 Voordelen van Deze Implementatie

### **Voor Gebruiker:**
- ✅ Minder handmatige stappen
- ✅ Duidelijke foutmeldingen
- ✅ Test functionaliteit
- ✅ Automatische configuratie

### **Voor Debugging:**
- ✅ Test URL direct beschikbaar
- ✅ Specifieke foutmeldingen
- ✅ Status indicators
- ✅ Duidelijke oplossingen

### **Voor Support:**
- ✅ Gebruiker kan zelf testen
- ✅ Foutmeldingen zijn duidelijk
- ✅ Minder "het werkt niet" vragen
- ✅ Betere troubleshooting

---

## 📱 UI Voorbeelden

### **Alles Werkt:**
```
┌─────────────────────────────────────┐
│ 🔄 Geluiden Synchroniseren          │
├─────────────────────────────────────┤
│ Stuur alle beschikbare geluiden     │
│ naar Home Assistant...               │
│                                      │
│ 🎵 Geluiden: 3                       │
│ 📡 Device IP: 192.168.1.100          │
│ 🏠 Home Assistant:                   │
│    http://192.168.1.50:8123          │
│                                      │
│ ┌─────────────────────────────────┐ │
│ │ ▶ Test HTTP Server              │ │
│ └─────────────────────────────────┘ │
│                                      │
│ ┌─────────────────────────────────┐ │
│ │ 🔄 Geluiden Synchroniseren      │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### **Geen WiFi:**
```
┌─────────────────────────────────────┐
│ 🔄 Geluiden Synchroniseren          │
├─────────────────────────────────────┤
│ 🎵 Geluiden: 3                       │
│ 📡 Device IP: Niet verbonden         │
│ 🏠 Home Assistant:                   │
│    http://192.168.1.50:8123          │
│                                      │
│ ┌─────────────────────────────────┐ │
│ │ ⚠️ Kan niet synchroniseren      │ │
│ │                                  │ │
│ │ • Geen WiFi verbinding           │ │
│ │   Controleer je netwerk-         │ │
│ │   verbinding                     │ │
│ └─────────────────────────────────┘ │
│                                      │
│ ┌─────────────────────────────────┐ │
│ │ 🔄 Geluiden Synchroniseren      │ │
│ │    (uitgeschakeld)               │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### **Na Succesvolle Sync:**
```
┌─────────────────────────────────────┐
│ 🔄 Geluiden Synchroniseren          │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ ✅ Verbinding succesvol:         │ │
│ │    3 geluiden kunnen nu          │ │
│ │    gestreamd worden              │ │
│ └─────────────────────────────────┘ │
│                                      │
│ ┌─────────────────────────────────┐ │
│ │ ▶ Test HTTP Server              │ │
│ └─────────────────────────────────┘ │
│                                      │
│ ┌─────────────────────────────────┐ │
│ │ 🔄 Geluiden Synchroniseren      │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

---

## 🧪 Test Scenario's

### **Test 1: Eerste Keer Setup**
1. ✅ Vul HA URL in
2. ✅ Open "Geluiden Synchroniseren"
3. ✅ Zie toast: "HTTP Server automatisch ingeschakeld"
4. ✅ Zie status: alles groen
5. ✅ Klik "Test HTTP Server"
6. ✅ Zie test URL in toast
7. ✅ Klik "Geluiden Synchroniseren"
8. ✅ Zie success message

### **Test 2: Geen WiFi**
1. ✅ Zet WiFi uit op phone
2. ✅ Open "Geluiden Synchroniseren"
3. ✅ Zie "Device IP: Niet verbonden"
4. ✅ Zie foutmelding: "Geen WiFi verbinding"
5. ✅ Knop is disabled
6. ✅ Zet WiFi aan
7. ✅ Refresh (sluit en open opnieuw)
8. ✅ Alles werkt weer

### **Test 3: Geen HA URL**
1. ✅ Verwijder HA URL
2. ✅ Open "Geluiden Synchroniseren"
3. ✅ Zie "Home Assistant: Niet geconfigureerd"
4. ✅ Zie foutmelding: "HA URL niet ingevuld"
5. ✅ Vul URL in
6. ✅ Refresh
7. ✅ Alles werkt weer

### **Test 4: Geen Geluiden**
1. ✅ Verwijder alle custom geluiden
2. ✅ Open "Geluiden Synchroniseren"
3. ✅ Zie "Geluiden: 0"
4. ✅ Zie foutmelding: "Geen geluiden beschikbaar"
5. ✅ Voeg geluid toe
6. ✅ Refresh
7. ✅ Alles werkt weer

---

## 🔧 Technische Details

### **Auto-Enable Logica:**
```kotlin
// Checkt bij LaunchedEffect (on mount)
if (hasUrl && !httpServerEnabled && hasWifi) {
    HttpServerManager.setEnabled(context, true)
    Toast.makeText(...)
}
```

**Voorwaarden:**
- HA URL is ingevuld
- HTTP Server is UIT
- WiFi is verbonden

**Resultaat:**
- HTTP Server wordt aangezet
- Toast melding
- Server draait op poort 8765

---

### **Test Functionaliteit:**
```kotlin
val testUrl = "http://$ip:$port/sounds/${firstSound.id}?api_key=$apiKey"
Toast.makeText(context, "✓ HTTP Server actief\nTest URL: $testUrl", ...)
```

**Output:**
- Streaming URL van eerste geluid
- Kan gekopieerd worden
- Kan getest worden in browser

---

### **Foutmelding Logica:**
```kotlin
if (!hasWifi) {
    errors.add("• Geen WiFi verbinding")
    errors.add("  Controleer je netwerkverbinding")
}
if (!hasUrl) {
    errors.add("• Home Assistant URL niet ingevuld")
    errors.add("  Vul eerst je HA URL in")
}
// etc...
```

**Voordelen:**
- Specifieke foutmeldingen
- Duidelijke oplossingen
- Prioriteit (WiFi eerst, dan URL, etc.)

---

## 📊 Samenvatting

| Feature | Status | Beschrijving |
|---------|--------|--------------|
| Auto-enable HTTP Server | ✅ | Automatisch bij HA configuratie |
| Test HTTP Server knop | ✅ | Test streaming URL |
| Verbeterde foutmeldingen | ✅ | Specifiek en duidelijk |
| WiFi check | ✅ | Controleert netwerkverbinding |
| HA URL check | ✅ | Controleert configuratie |
| Geluiden check | ✅ | Controleert beschikbaarheid |
| Success feedback | ✅ | Duidelijke bevestiging |
| Error feedback | ✅ | Specifieke foutmeldingen |

---

## ✅ Klaar Voor Gebruik!

De implementatie is compleet en getest. Gebruikers kunnen nu:

1. **Automatisch** HTTP server laten starten
2. **Testen** of alles werkt
3. **Begrijpen** wat er mis is als het niet werkt
4. **Oplossen** met duidelijke instructies

**Geen handmatige stappen meer nodig!** 🎉
