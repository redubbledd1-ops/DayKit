# Quick Start Instructions - HTTP Server Testing

## 🎯 What Was Fixed

1. ✅ Server now binds to `0.0.0.0` (accessible from LAN)
2. ✅ `/status` endpoint shows full diagnostics
3. ✅ `/sounds` endpoint shows file details
4. ✅ WiFi SSID detection with location permission
5. ✅ Enhanced test button with detailed logging

---

## 🚀 Quick Test (5 Steps)

### **Step 1: Grant Location Permission**
```
Settings → Apps → CalendarAlarm → Permissions → Location → Allow
Settings → Location → Turn ON
```

**Why:** Android 8.1+ requires location permission to read WiFi SSID.

---

### **Step 2: Build & Run App**
```
Android Studio:
  Build → Make Project
  Run → Run 'app'
```

---

### **Step 3: Test in App**
```
1. Open app
2. Settings → Home Assistant
3. Scroll to "Geluiden Synchroniseren"
4. Click "Test HTTP Server"
5. Note the "Status URL" from toast
```

**Expected Toast:**
```
✓ HTTP Server actief
WiFi: YourNetworkName
Netwerk: Lokaal netwerk
IP: 192.168.1.75:8765
Geluiden: 3
Status URL: http://192.168.1.75:8765/status?api_key=...
```

---

### **Step 4: Test in Browser**

**Copy the Status URL from toast or logcat, then open in browser:**
```
http://192.168.1.75:8765/status?api_key=YOUR_API_KEY
```

**Expected Response:**
```json
{
  "running": true,
  "ip": "192.168.1.75",
  "port": 8765,
  "bind_address": "0.0.0.0",
  "sounds_count": 3,
  "wifi_connected": true,
  "wifi_ssid": "YourNetwork",
  "storage_path": "/data/user/0/.../alarm_sounds/custom",
  "storage_exists": true,
  "sounds": [...]
}
```

---

### **Step 5: Export Logcat**

**In terminal:**
```bash
adb logcat -d -s HttpServerManager:* SoundSyncButton:* SoundHttpServer:* > http_test.log
```

**Or in Android Studio:**
```
Logcat → Filter: "HttpServerManager|SoundSyncButton|SoundHttpServer"
Right-click → Export
```

---

## 🔍 What to Check

### **If WiFi shows "<unknown ssid>":**
- ✅ Location permission granted?
- ✅ Location services enabled?
- ✅ WiFi connected?

### **If "Geluiden: 0" or "null":**
- ✅ Have you added sounds via app?
- ✅ Check logcat: "Storage Exists: true"?

### **If browser can't connect:**
- ✅ Server running? (Check logcat: "HTTP server started")
- ✅ Correct IP address? (Use IP from test button)
- ✅ API key in URL? (?api_key=...)
- ✅ Phone and computer on same network?

---

## 📋 Key Logcat Lines to Look For

**Server Start:**
```
I/MainActivity: HTTP server started successfully
I/HttpServerManager: HTTP server started on 0.0.0.0:8765 (accessible via 192.168.1.75:8765)
```

**Test Results:**
```
I/SoundSyncButton: WiFi SSID: YourNetwork
I/SoundSyncButton: Location Permission: true
I/SoundSyncButton: Device IP: 192.168.1.75
I/SoundSyncButton: Available Sounds: 3
I/SoundSyncButton: Storage Exists: true
I/SoundSyncButton:   Status: http://192.168.1.75:8765/status?api_key=abc123
```

---

## 🎯 Test URLs

Replace `192.168.1.75` with your device IP and `YOUR_API_KEY` with actual key from logcat:

```bash
# Status (most important!)
http://192.168.1.75:8765/status?api_key=YOUR_API_KEY

# List all sounds
http://192.168.1.75:8765/sounds?api_key=YOUR_API_KEY

# Get specific sound (replace ID)
http://192.168.1.75:8765/sounds/1?api_key=YOUR_API_KEY

# Server info
http://192.168.1.75:8765/?api_key=YOUR_API_KEY
```

---

## 📦 What to Share for Support

1. **Logcat export** (http_test.log)
2. **Screenshot of test button output**
3. **Browser response** (if accessible)
4. **Device info:**
   - Android version
   - WiFi connected? (Yes/No)
   - Location permission granted? (Yes/No)
   - Location services enabled? (Yes/No)
   - Sounds added? (Count)

---

## ✅ Success Criteria

- [ ] Toast shows WiFi network name (not `<unknown ssid>`)
- [ ] Toast shows sounds count > 0
- [ ] Logcat shows "HTTP server started on 0.0.0.0:8765"
- [ ] Browser can access `/status` endpoint
- [ ] `/status` shows `"running": true`
- [ ] `/status` shows correct IP and sounds count

---

## 🆘 Common Issues

### **"WiFi: <location permission required>"**
→ Grant location permission in Settings

### **"WiFi: <location services disabled>"**
→ Enable Location in Settings

### **"Geluiden: null"**
→ Add sounds via app first

### **Browser: "Connection refused"**
→ Check server is running (logcat)

### **Browser: "Unauthorized"**
→ Add API key to URL (?api_key=...)

---

## 📞 Need Help?

See **HTTP_SERVER_DIAGNOSTICS.md** for detailed troubleshooting guide.

Include logcat export and screenshots when asking for help!
