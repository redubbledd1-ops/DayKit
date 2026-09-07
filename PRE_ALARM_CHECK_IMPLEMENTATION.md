# Phase 1 Pre-Alarm Check Implementation

## Overview

This document describes the reworked out-of-bed detection system that runs **before** the alarm time, providing reliable early cancellation when the user is already awake.

---

## 1. Why the Previous Out-of-Bed Check Failed

### Root Causes

| Issue | Description | Impact |
|-------|-------------|--------|
| **Single-point check** | Only checked at T=0 (alarm time) | Race conditions with sensor lag |
| **No temporal validation** | No "how long out of bed?" requirement | Brief sensor blips caused false decisions |
| **Strict mode backfired** | Errors BLOCKED alarms instead of allowing them | Users missed alarms due to sensor issues |
| **Stale cache reliance** | 10-minute cache could be outdated | Stale data led to wrong decisions |

### False Negative Scenarios (Alarm fires when user is awake)

1. User gets out of bed at T-1 minute, sensor hasn't updated yet
2. User is standing near bed (sensor shows "in bed" due to proximity)
3. Network delay causes stale sensor reading

### False Positive Scenarios (Alarm cancelled when user is in bed)

1. Brief sensor glitch shows "out of bed"
2. User rolled over, sensor temporarily changed state
3. Cached data from earlier bathroom trip

---

## 2. Improved Out-of-Bed Strategy

### Key Changes

1. **Pre-alarm window**: Check starts at **T-5 minutes**, not T=0
2. **Sustained requirement**: User must be out of bed for **≥3 minutes**
3. **Multi-sample validation**: Takes **3 samples** over ~2.5 minutes
4. **100% consistency required**: ALL samples must show "out of bed"
5. **Fail-safe by default**: Any uncertainty → alarm proceeds

### Signals Evaluated

| Signal | Source | Purpose |
|--------|--------|---------|
| Bed occupancy sensor | Home Assistant entity | Primary out-of-bed indicator |
| User presence | Home Assistant entity | Skip bed check if not home |
| Temporal duration | Multiple samples over time | Ensure sustained out-of-bed status |

### Confidence Determination

```
Confidence = (out_of_bed_samples / valid_samples) × 100%

Cancel alarm ONLY when:
  - Confidence = 100% (all samples show out of bed)
  - Duration ≥ 3 minutes
  - No errors during sampling
  - User is confirmed home (if presence check enabled)
```

### Edge Case Handling

| Edge Case | Handling |
|-----------|----------|
| Phone picked up briefly | Multi-sample prevents single-point errors |
| User goes to bathroom then returns | Duration check catches this (back in bed = in_bed sample) |
| Sensor returns "unknown" | Treated as error → fail-safe: allow alarm |
| HA connection timeout | Fail-safe: allow alarm |
| User not home | Skip bed check, allow alarm |

---

## 3. Schematic App Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ALARM SCHEDULING                              │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│ AlarmScheduler.scheduleNextAlarm()                                   │
│  ├─ Cancel previous main alarm (REQUEST_CODE = 0)                   │
│  ├─ Cancel previous pre-alarm check (REQUEST_CODE = 2)              │
│  ├─ Schedule main alarm at T=alarm_time                             │
│  └─ Schedule pre-alarm check at T-5 minutes                         │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 │ (time passes)
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   T-5 MINUTES: PRE-ALARM CHECK                       │
│                        (PreAlarmReceiver)                            │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Phase 1 Check (SILENT - no sound, no notification)                  │
│                                                                      │
│  1. Is trigger mode SMART_ALARM?                                    │
│     └─ No → Skip pre-check (result: NOT_CHECKED)                    │
│                                                                      │
│  2. Is out-of-bed check enabled?                                    │
│     └─ No → Skip pre-check (result: CHECK_DISABLED)                 │
│                                                                      │
│  3. Is user home? (if presence check enabled)                       │
│     ├─ Error → Fail-safe (result: PRESENCE_ERROR) → Allow alarm     │
│     └─ Not home → Skip bed check (result: USER_NOT_HOME)            │
│                                                                      │
│  4. Sample bed sensor 3 times over ~2.5 minutes                     │
│     ├─ Sample 1: Query HA entity, record state                      │
│     ├─ (wait 50 seconds)                                            │
│     ├─ Sample 2: Query HA entity, record state                      │
│     ├─ (wait 50 seconds)                                            │
│     └─ Sample 3: Query HA entity, record state                      │
│                                                                      │
│  5. Analyze samples:                                                 │
│     ├─ Too many errors? → Fail-safe (result: SENSOR_ERROR)          │
│     ├─ Any "in bed" sample? → (result: USER_IN_BED)                 │
│     ├─ Duration < 3 min? → (result: INSUFFICIENT_DURATION)          │
│     └─ All "out of bed" + duration ≥ 3 min?                         │
│         → CANCEL ALARM (result: USER_OUT_OF_BED)                    │
│                                                                      │
│  6. Store result in PreAlarmCheckStorage                            │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 │ (time passes)
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   T=0: ALARM TIME                                    │
│                     (AlarmReceiver)                                  │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│ PHASE 1: Check pre-alarm result                                     │
│  ├─ Get result from PreAlarmCheckStorage                            │
│  ├─ If shouldCancel=true AND reason=USER_OUT_OF_BED:                │
│  │   └─ ✅ ALARM CANCELLED (do not start AlarmService)              │
│  └─ Otherwise: proceed to Phase 2                                   │
│                                                                      │
│ PHASE 2: RuleEngine.shouldFireAlarm() (existing logic)              │
│  ├─ Check HA connection                                             │
│  ├─ Check user presence (if enabled)                                │
│  ├─ Check out-of-bed status (single check, with FAIL-SAFE)          │
│  └─ Check smart condition entity                                    │
│                                                                      │
│ Result:                                                              │
│  ├─ shouldFire=true → Start AlarmService (alarm plays)              │
│  └─ shouldFire=false → Alarm blocked (silent)                       │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   ALARM EXECUTION                                    │
│                     (AlarmService)                                   │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│ HOME ASSISTANT BOUNDARY                                              │
│                                                                      │
│ ❌ Pre-alarm phase: NO HA triggers (read-only queries)              │
│ ✅ Alarm execution: HA triggers allowed (e.g., lights, TTS)         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. Verification Checklist

### No Timing Conflicts
- [x] Pre-alarm uses separate REQUEST_CODE (2) from main alarm (0) and snooze (1)
- [x] Pre-alarm cancelled when rescheduling
- [x] Pre-alarm result has 10-minute expiry (covers window + buffer)
- [x] Pre-alarm skipped if time already passed

### No Race Conditions
- [x] Pre-alarm result stored before main alarm fires
- [x] Result cleared after use
- [x] Atomic SharedPreferences operations

### No Alarm Skips
- [x] All error paths default to allowing alarm
- [x] Pre-alarm failure doesn't affect main alarm scheduling
- [x] RuleEngine fail-safe behavior fixed (errors → allow alarm)

### Build Verification
```
BUILD SUCCESSFUL in 43s
37 actionable tasks: 11 executed, 26 up-to-date
```

---

## 5. Decision Summary

### When Alarm is CANCELLED Early (Pre-alarm Phase 1)

An alarm is cancelled early **ONLY** when ALL of the following are true:

| Condition | Requirement |
|-----------|-------------|
| Trigger mode | SMART_ALARM |
| Out-of-bed check | Enabled in settings |
| User presence | Home (or presence check disabled) |
| Sensor samples | ALL samples show "out of bed" |
| Duration | User out of bed for ≥ 3 minutes |
| Errors | No critical errors during sampling |

### When Alarm is ALLOWED to Continue

The alarm proceeds normally in ANY of these cases:

| Scenario | Reason Code | Behavior |
|----------|-------------|----------|
| Trigger mode is NORMAL or ONE_TIME | NOT_CHECKED | No pre-check performed |
| Out-of-bed check disabled | CHECK_DISABLED | Feature not active |
| User not home | USER_NOT_HOME | Bed check irrelevant |
| User still in bed | USER_IN_BED | Alarm needed |
| Out of bed < 3 minutes | INSUFFICIENT_DURATION | Could be brief movement |
| Sensor error/timeout | SENSOR_ERROR | Fail-safe: wake user |
| Sensor returns unknown | SENSOR_UNRELIABLE | Fail-safe: wake user |
| HA connection failed | HA_CONNECTION_ERROR | Fail-safe: wake user |
| Presence check error | PRESENCE_ERROR | Fail-safe: wake user |

---

## 6. Files Modified/Created

| File | Change |
|------|--------|
| `rules/PreAlarmCheckResult.kt` | **NEW** - Data classes for pre-alarm results |
| `PreAlarmReceiver.kt` | **NEW** - Pre-alarm check receiver |
| `AlarmScheduler.kt` | Added pre-alarm scheduling |
| `AlarmReceiver.kt` | Added Phase 1 pre-alarm result check |
| `rules/RuleEngine.kt` | Fixed fail-safe behavior (errors → allow alarm) |
| `AndroidManifest.xml` | Registered PreAlarmReceiver |

---

## 7. Testing Recommendations

### Manual Test Scenarios

1. **User in bed at alarm time**
   - Expected: Alarm fires normally

2. **User out of bed 10 minutes before alarm**
   - Expected: Pre-alarm check cancels alarm

3. **User briefly out of bed (< 3 min) then returns**
   - Expected: Alarm fires (duration too short)

4. **HA connection fails during pre-check**
   - Expected: Alarm fires (fail-safe)

5. **Sensor returns "unknown"**
   - Expected: Alarm fires (fail-safe)

6. **User not home**
   - Expected: Alarm fires (bed check skipped)

### Log Tags to Monitor

- `PreAlarmReceiver` - Pre-alarm check execution
- `AlarmReceiver` - Alarm trigger and pre-alarm result usage
- `RuleEngine` - SMART_ALARM evaluation
- `AlarmScheduler` - Scheduling events

---

## 8. Step 3: Verifying PreAlarmReceiver Runs (Logcat)

### Logcat Filter Command

```bash
adb logcat -s PreAlarmReceiver:D AlarmScheduler:D AlarmReceiver:D PreAlarmCheckStorage:D
```

Or in Android Studio Logcat, use filter:
```
tag:PreAlarmReceiver | tag:AlarmScheduler | tag:AlarmReceiver | tag:PreAlarmCheckStorage
```

### Expected Log Sequence

**At Alarm Scheduling (T-∞):**
```
AlarmScheduler: Scheduling next alarm
AlarmScheduler: Next alarm: [label] at [epochMillis]
AlarmScheduler: Scheduling pre-alarm check at [preAlarmTime] (5 min before alarm)
AlarmScheduler: Pre-alarm check scheduled successfully for alarm [id]
```

**At T-5 Minutes (Pre-alarm check):**
```
PreAlarmReceiver: ======================================================================
PreAlarmReceiver: [PRE-ALARM] PreAlarmReceiver triggered at [timestamp]
PreAlarmReceiver: [PRE-ALARM] Alarm: id=[id], label=[label], triggerId=[triggerId]
PreAlarmReceiver: [PRE-ALARM] Bed entity: [entityId], in-bed value: '[value]'
PreAlarmReceiver: [PRE-ALARM] Starting sustained bed sensor sampling (3 samples)...
PreAlarmReceiver: [PRE-ALARM] Sample 0: state='[state]', isInBed=[true/false]
PreAlarmReceiver: [PRE-ALARM] Sample 1: state='[state]', isInBed=[true/false]
PreAlarmReceiver: [PRE-ALARM] Sample 2: state='[state]', isInBed=[true/false]
PreAlarmReceiver: [PRE-ALARM] Analysis: total=3, valid=3, outOfBed=[count], errors=0
PreAlarmReceiver: [PRE-ALARM] Check complete: shouldCancel=[true/false], reason=[reason]
PreAlarmCheckStorage: Stored pre-alarm result for alarm [id]: cancel=[true/false], reason=[reason]
```

**At T=0 (Alarm time):**
```
AlarmReceiver: AlarmReceiver onReceive - action: null
AlarmReceiver: ======================================================================
AlarmReceiver: [ALARM] Pre-alarm check result found:
AlarmReceiver: [ALARM]   - shouldCancel: [true/false]
AlarmReceiver: [ALARM]   - reason: [reason]
AlarmReceiver: [ALARM]   - confidence: [X]%
AlarmReceiver: [ALARM]   - duration: [Y]ms
AlarmReceiver: [ALARM] ✅ ALARM CANCELLED by Phase 1 pre-alarm check  (if cancelled)
    OR
AlarmReceiver: [ALARM] Pre-alarm check did NOT cancel alarm - proceeding with normal checks
```

### Troubleshooting

| Symptom | Possible Cause | Solution |
|---------|----------------|----------|
| No PreAlarmReceiver logs | Pre-alarm not scheduled | Check AlarmScheduler logs for scheduling |
| Pre-alarm scheduled but no trigger | Android Doze killed receiver | Test with phone connected to charger |
| "Pre-alarm time is in the past" | Alarm too soon (< 5 min) | Schedule alarm ≥8-10 minutes ahead |
| No result in AlarmReceiver | Storage issue | Check PreAlarmCheckStorage logs |

---

## 9. Step 4: Testing with Sufficient Time

### Why ≥8-10 Minutes is Required

```
T-10 min  ──────────────────────────────────────────────  Alarm scheduled
    │
    │     (5 minutes pass)
    │
T-5 min   ──────────────────────────────────────────────  PreAlarmReceiver fires
    │     
    │     Sample 1 (immediate)
    │     (50 sec wait)
    │     Sample 2
    │     (50 sec wait)  
    │     Sample 3
    │     
    │     Total sampling: ~2.5 minutes
    │
T-2.5 min ──────────────────────────────────────────────  Pre-alarm check complete
    │
    │     Result stored in SharedPreferences
    │
T=0       ──────────────────────────────────────────────  AlarmReceiver fires
```

### Test Scenarios

**Scenario A: User Out of Bed (Alarm Should Cancel)**
1. Set alarm for T+10 minutes
2. Ensure bed sensor shows "out of bed" state
3. Keep app open to see banner
4. At T-5: Banner should appear with "Het alarm zal NIET afgaan"
5. At T=0: Alarm should NOT fire

**Scenario B: User In Bed (Alarm Should Fire)**
1. Set alarm for T+10 minutes
2. Ensure bed sensor shows "in bed" state
3. Keep app open
4. At T-5: Banner should appear with "Het alarm zal afgaan"
5. At T=0: Alarm should fire normally

**Scenario C: Force Alarm Override**
1. Follow Scenario A setup
2. When banner appears, tap "Alarm toch af laten gaan"
3. Banner should update to show alarm will fire
4. At T=0: Alarm should fire

**Scenario D: Fail-safe (HA Offline)**
1. Disable Home Assistant or disconnect network
2. Set alarm for T+10 minutes
3. At T-5: Banner should show "status onzeker" or no banner
4. At T=0: Alarm should fire (fail-safe)

### Debug View Location

- Swipe UP from main screen to open "Upcoming Alarms"
- Tap on any alarm row to expand pre-alarm debug details
- Shows: reason, confidence, duration, sensor value, check time
