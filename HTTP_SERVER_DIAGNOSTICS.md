# HTTP Server Diagnostics & Troubleshooting Guide

## 🔧 Changes Made

### 1. **Server Binding Fix** ✅
**Problem:** Server was binding to `localhost` (127.0.0.1), only accessible from device itself.

**Solution:** Changed to bind to `0.0.0.0` (all network interfaces).

```kotlin
// Before:
class SoundHttpServer(...) : NanoHTTPD(port)

// After:
class SoundHttpServer(...) : NanoHTTPD("0.0.0.0", port)
```

**Result:** Server is now accessible from LAN (192.168.x.x) and Tailscale (100.x.x.x).

---

### 2. **Enhanced `/status` Endpoint** ✅
**New fields added:**
```json
{
  "running": true,
  "ip": "192.168.1.75",
  "port": 8765,
  "bind_address": "0.0.0.0",
  "sounds_count": 3,
  "wifi_connected": true,
  "wifi_ssid": "MyNetwork",
  "storage_path": "/data/user/0/com.redubbledd.agendawekker/files/alarm_sounds/custom",
  "storage_exists": true,
  "storage_readable": true,
  "sounds": [
    {
      "id": 1,
      "name": "Alarm1",
      "file_exists": true,
      "file_size": 245678,
      "file_path": "/data/user/0/.../alarm_sounds/custom/alarm1.mp3"
    }
  ]
}
```

**Test URL:**
```
http://192.168.1.75:8765/status?api_key=YOUR_API_KEY
```

---

### 3. **Enhanced `/sounds` Endpoint** ✅
**New fields added:**
```json
{
  "count": 3,
  "sounds": [
    {
      "id": 1,
      "name": "Alarm1",
      "duration_ms": 30000,
      "duration_formatted": "0:30",
      "file_path": "/data/user/0/.../custom/alarm1.mp3",
      "file_exists": true,
      "file_size_bytes": 245678,
      "file_size_formatted": "240 KB",
      "file_extension": "mp3",
      "url": "http://192.168.1.75:8765/sounds/1?api_key=YOUR_API_KEY"
    }
  ]
}
```

**Test URL:**
```
http://192.168.1.75:8765/sounds?api_key=YOUR_API_KEY
```

---

### 4. **WiFi SSID Detection with Location Permission** ✅

**Problem:** SSID shows `<unknown ssid>` on Android 8.1+

**Root Cause:** Android requires `ACCESS_FINE_LOCATION` permission + Location services enabled.

**Solution:**
1. Added `ACCESS_FINE_LOCATION` permission to manifest
2. Added permission check in `getWifiNetworkName()`
3. Added location services check
4. Detailed logging for troubleshooting

**Possible SSID values:**
- `"MyNetwork"` - Success
- `"<location permission required>"` - Permission not granted
- `"<location services disabled>"` - GPS/Location off
- `"<unknown ssid>"` - Other issue
- `null` - Not connected to WiFi

---

### 5. **Enhanced Test Button Logging** ✅

**New logcat output:**
```
I/SoundSyncButton: ═══════════════════════════════════
I/SoundSyncButton: HTTP Server Test Results:
I/SoundSyncButton: WiFi SSID: MyNetwork
I/SoundSyncButton: Location Permission: true
I/SoundSyncButton: Network Type: Lokaal netwerk
I/SoundSyncButton: Device IP: 192.168.1.75
I/SoundSyncButton: Server Port: 8765
I/SoundSyncButton: Bind Address: 0.0.0.0
I/SoundSyncButton: Available Sounds: 3
I/SoundSyncButton: Storage Path: /data/user/0/com.redubbledd.agendawekker/files/alarm_sounds/custom
I/SoundSyncButton: Storage Exists: true
I/SoundSyncButton: Storage Readable: true
I/SoundSyncButton: 
I/SoundSyncButton: Test URLs:
I/SoundSyncButton:   Status: http://192.168.1.75:8765/status?api_key=abc123
I/SoundSyncButton:   Sounds List: http://192.168.1.75:8765/sounds?api_key=abc123
I/SoundSyncButton:   First Sound: http://192.168.1.75:8765/sounds/1?api_key=abc123
I/SoundSyncButton: 
I/SoundSyncButton: Sound Files:
I/SoundSyncButton:   [0] Alarm1
I/SoundSyncButton:       ID: 1
I/SoundSyncButton:       Path: /data/user/0/.../custom/alarm1.mp3
I/SoundSyncButton:       Exists: true
I/SoundSyncButton:       Size: 245678 bytes
I/SoundSyncButton:       URL: http://192.168.1.75:8765/sounds/1?api_key=abc123
I/SoundSyncButton: ═══════════════════════════════════
```

---

## 🧪 Testing Instructions

### **Step 1: Build & Install**
```bash
# In Android Studio:
Build → Make Project
Run → Run 'app'
```

### **Step 2: Enable Location Permission**
```
Settings → Apps → CalendarAlarm → Permissions → Location → Allow
```

**Important:** Also enable Location services:
```
Settings → Location → Turn ON
```

### **Step 3: Test in App**
```
1. Open app
2. Settings → Home Assistant
3. Scroll to "Geluiden Synchroniseren"
4. Click "Test HTTP Server"
5. Check Logcat output
```

### **Step 4: Test in Browser**

**Get API Key from Logcat:**
```
Logcat filter: HttpServerManager
Look for: "Generated new API key" or use URL from test output
```

**Test URLs:**
```bash
# Status endpoint (most important!)
http://192.168.1.75:8765/status?api_key=YOUR_API_KEY

# Sounds list
http://192.168.1.75:8765/sounds?api_key=YOUR_API_KEY

# Individual sound (replace ID)
http://192.168.1.75:8765/sounds/1?api_key=YOUR_API_KEY

# Root info
http://192.168.1.75:8765/?api_key=YOUR_API_KEY
```

**Expected Result:**
- JSON response with server info
- If sounds exist, you should see file details
- If no sounds, `sounds_count: 0`

---

## 🔍 Troubleshooting

### **Issue 1: "WiFi: <unknown ssid>"**

**Symptoms:**
```
Toast shows: WiFi: <unknown ssid>
Logcat: WiFi SSID: <unknown ssid>
```

**Diagnosis:**
```
Logcat filter: HttpServerManager
Look for: "WiFi SSID unavailable"
```

**Possible causes:**

#### A) Location Permission Not Granted
```
Logcat: WiFi SSID unavailable: Location permission not granted
```

**Fix:**
```
1. Settings → Apps → CalendarAlarm → Permissions
2. Location → Allow
3. Restart app
```

#### B) Location Services Disabled
```
Logcat: WiFi SSID unavailable: Location services disabled
```

**Fix:**
```
1. Settings → Location → Turn ON
2. Restart app
```

#### C) WiFi Not Connected
```
Logcat: WiFi connected: false
```

**Fix:**
```
1. Connect to WiFi network
2. Wait 5 seconds
3. Test again
```

---

### **Issue 2: "Geluiden: null" or "Geluiden: 0"**

**Symptoms:**
```
Toast shows: Geluiden: null
or
Toast shows: Geluiden: 0
```

**Diagnosis:**
```
Logcat filter: SoundSyncButton
Look for: "Available Sounds: 0"
Look for: "Storage Path: ..."
Look for: "Storage Exists: false"
```

**Possible causes:**

#### A) No Sounds Added Yet
```
Logcat: Available Sounds: 0
Logcat: Storage Exists: true
```

**Fix:**
```
1. Open app
2. Go to alarm settings
3. Add custom sounds via "+" button
4. Import audio files via SAF (Storage Access Framework)
```

#### B) Storage Directory Missing
```
Logcat: Storage Exists: false
```

**Fix:**
```
1. Add at least one sound (will create directory)
2. Or check app data not cleared
```

#### C) Storage Not Readable
```
Logcat: Storage Readable: false
```

**Fix:**
```
1. Check app permissions
2. Reinstall app if needed
```

---

### **Issue 3: Server Not Accessible from Browser**

**Symptoms:**
```
Browser: "Connection refused" or "Timeout"
```

**Diagnosis:**
```
Logcat filter: SoundHttpServer
Look for: "HTTP server started on 0.0.0.0:8765"
```

**Possible causes:**

#### A) Server Not Running
```
Logcat: No "HTTP server started" message
```

**Fix:**
```
1. Check Logcat for errors
2. Settings → HTTP Server → Toggle off/on
3. Restart app
```

#### B) Wrong IP Address
```
Browser URL uses: 192.168.1.100
But device IP is: 192.168.1.75
```

**Fix:**
```
1. Check test button output for correct IP
2. Use IP from logcat: "Device IP: 192.168.1.75"
```

#### C) Firewall/Network Issue
```
Server running but not accessible
```

**Fix:**
```
1. Ensure phone and computer on same network
2. Disable VPN on computer (if testing locally)
3. Check router firewall settings
```

#### D) Wrong API Key
```
Browser: "Unauthorized: Invalid or missing API key"
```

**Fix:**
```
1. Get API key from logcat test output
2. Add to URL: ?api_key=YOUR_KEY
```

---

### **Issue 4: Tailscale Connection**

**Symptoms:**
```
Toast shows: Netwerk: Lokaal netwerk
But using Tailscale
```

**Explanation:**
- App shows WiFi IP (192.168.x.x)
- Tailscale IP is different (100.x.x.x)
- This is normal Android behavior

**Solution for Home Assistant:**
```
1. Find Tailscale IP of phone:
   - Tailscale admin panel
   - Devices → Your phone
   - Copy IP (e.g., 100.64.0.5)

2. Use Tailscale IP in HA automation:
   http://100.64.0.5:8765/sounds/1?api_key=YOUR_KEY
   
3. Test in browser from HA server:
   curl http://100.64.0.5:8765/status?api_key=YOUR_KEY
```

---

## 📋 Logcat Filters

### **Essential Filters:**
```
HttpServerManager    - Server lifecycle, WiFi checks
SoundSyncButton      - Test button output, diagnostics
SoundHttpServer      - HTTP requests, responses
SoundRepository      - Sound loading, file access
MainActivity         - App startup, server auto-start
```

### **Recommended Logcat Command:**
```bash
adb logcat -s HttpServerManager:I SoundSyncButton:I SoundHttpServer:I SoundRepository:I MainActivity:I
```

### **Full Diagnostic Log:**
```bash
adb logcat -s HttpServerManager:* SoundSyncButton:* SoundHttpServer:* SoundRepository:* MainActivity:* > server_diagnostics.log
```

---

## 🎯 Expected Behavior

### **After App Start:**
```
I/MainActivity: Starting HTTP server...
I/HttpServerManager: HTTP server started on 0.0.0.0:8765 (accessible via 192.168.1.75:8765)
I/MainActivity: HTTP server started successfully
```

### **After Test Button Click:**
```
I/SoundSyncButton: Test - Server status: ServerStatus(enabled=true, running=true, ...)
I/SoundSyncButton: ═══════════════════════════════════
I/SoundSyncButton: HTTP Server Test Results:
I/SoundSyncButton: WiFi SSID: MyNetwork
I/SoundSyncButton: Location Permission: true
I/SoundSyncButton: Device IP: 192.168.1.75
I/SoundSyncButton: Available Sounds: 3
I/SoundSyncButton: Storage Exists: true
I/SoundSyncButton: Test URLs:
I/SoundSyncButton:   Status: http://192.168.1.75:8765/status?api_key=abc123
```

### **Browser Test Success:**
```json
// http://192.168.1.75:8765/status?api_key=YOUR_KEY
{
  "running": true,
  "ip": "192.168.1.75",
  "port": 8765,
  "sounds_count": 3,
  "wifi_ssid": "MyNetwork",
  "storage_exists": true
}
```

---

## 📦 File Locations

### **Sound Storage:**
```
Internal Storage:
/data/user/0/com.redubbledd.agendawekker/files/alarm_sounds/custom/

Files are stored here when imported via SAF (Storage Access Framework)
```

### **How to Add Sounds:**
```
1. Open app
2. Go to alarm settings
3. Click "+" to add sound
4. Select audio file via file picker
5. File is copied to app's internal storage
6. Database entry created
7. Sound available for HTTP streaming
```

### **Database:**
```
/data/user/0/com.redubbledd.agendawekker/databases/agenda_wekker_db

Table: custom_alarm_sounds
Columns: id, displayName, storedFilename, durationMs, addedAt
```

---

## 🚀 Quick Test Checklist

- [ ] Location permission granted
- [ ] Location services enabled
- [ ] WiFi connected
- [ ] HTTP Server enabled (Settings → HTTP Server)
- [ ] At least one sound added
- [ ] Test button shows correct IP
- [ ] Test button shows WiFi SSID (not `<unknown ssid>`)
- [ ] Test button shows sounds count > 0
- [ ] Logcat shows "HTTP server started on 0.0.0.0:8765"
- [ ] Browser can access `/status` endpoint
- [ ] Browser can access `/sounds` endpoint
- [ ] Browser can download sound file

---

## 📊 Summary of Changes

| Component | Change | Purpose |
|-----------|--------|---------|
| `SoundHttpServer` | Bind to `0.0.0.0` | LAN accessibility |
| `/status` endpoint | Add IP, storage, WiFi info | Diagnostics |
| `/sounds` endpoint | Add file details | File verification |
| `HttpServerManager` | Location permission check | SSID detection |
| `AndroidManifest.xml` | Add `ACCESS_FINE_LOCATION` | SSID access |
| `SoundSyncButton` | Enhanced logging | Troubleshooting |
| Test button | Show status URL | Easy browser testing |

---

## ✅ Next Steps for User

1. **Build and install app**
2. **Grant location permission**
3. **Enable location services**
4. **Click "Test HTTP Server"**
5. **Copy status URL from toast**
6. **Open URL in browser**
7. **Verify JSON response**
8. **Share logcat output if issues persist**

---

## 📝 Logcat Export Command

```bash
# Export 10 seconds before and after test:
adb logcat -d -s HttpServerManager:* SoundSyncButton:* SoundHttpServer:* > http_server_test.log
```

**Share this file for support!**
