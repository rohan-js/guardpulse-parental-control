# GuardPulse Android TV Parental Control

<p align="center">
  <img src="docs/assets/guardpulse-logo.png" alt="GuardPulse Logo" width="400" />
</p>

<p align="center">
  <b>GuardPulse</b> is a Firebase-backed parental-control system for Android TV, with a parent phone dashboard, TV-side foreground PIN wall enforcement, daily limits, tamper alerts, and optional Device Owner hardening.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-TV-3DDC84?logo=android&logoColor=white" alt="Android TV" />
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Firebase-FFCA28?logo=firebase&logoColor=black" alt="Firebase" />
  <img src="https://img.shields.io/badge/Realtime%20Database-FFCA28?logo=firebase&logoColor=black" alt="Firebase Realtime Database" />
  <img src="https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white" alt="Gradle" />
  <img src="https://img.shields.io/badge/Public%20Template-Safe%20Config-2B7A77" alt="Public template with safe config" />
</p>

---

## Quick Navigation

- [Introduction](#introduction)
- [Key Features](#key-features)
- [How It Works](#how-it-works)
- [Architecture](#architecture)
- [Modules](#modules)
- [Firebase Setup](#firebase-setup)
- [Build Guide](#build-guide)
- [Parent App Setup](#parent-app-setup)
- [TV Fallback Install](#tv-fallback-install)
- [Device Owner Provisioning](#device-owner-provisioning)
- [Remote Unlock + PIN Wall](#remote-unlock--pin-wall)
- [Firebase Paths](#firebase-paths)
- [Security Limits](#security-limits)
- [Project Info](#project-info)

---

# Introduction

**GuardPulse** is designed for Android TV environments where ordinary app blocking is not enough. Instead of relying on network blocking, the TV app watches the foreground app through Accessibility and immediately covers blocked apps with a full-screen parent PIN wall.

It supports two enforcement paths:

| Mode | Best For | Behavior |
| :--- | :--- | :--- |
| **Fallback Mode** | Real-world TVs where Device Owner is blocked by firmware | Uses Accessibility, Usage Access, Device Admin, Firebase sync, and a PIN wall |
| **Device Owner Mode** | Fresh/factory-reset TVs that allow enterprise provisioning | Adds stronger Android policy controls such as app suspension and restrictions |

> This public repository intentionally uses placeholder Firebase values. Do not commit live Firebase project IDs, API keys, device IDs, APKs, local backups, or operational notes.

---

## Key Features

| Feature | Description |
| :--- | :--- |
| **Foreground PIN Wall** | Blocked Android TV apps open at the system level, then are immediately covered by a PIN screen. |
| **Parent Dashboard** | Android parent app for pairing TVs, controlling apps, setting limits, and approving unlock requests. |
| **One-Visit Unlocks** | Correct PIN or parent approval unlocks the current app visit only; leaving the app clears the unlock. |
| **Live TV Source Lock** | Handles HDMI/source apps such as Live TV through the same PIN-wall model. |
| **Settings Section Locks** | Protects dangerous Settings areas such as Apps, Accessibility, Security, Developer options, and Reset. |
| **Daily Limits** | Usage access tracks app time and turns reached limits into PIN-wall locks. |
| **Tamper Alerts** | Firebase tamper feed reports missing protection, admin disable attempts, and protected Settings access. |
| **Hidden TV Setup** | TV setup screen can be hidden from launcher and opened remotely from the parent app. |
| **Device Owner Option** | Provisioning scripts support stronger policy mode on compatible fresh TVs. |

---

## How It Works

GuardPulse uses Firebase as the coordination layer between the parent phone and the Android TV app.

1. **Pair TV** from the parent app using a QR payload or manual device ID/code.
2. **Parent app writes policy** for each TV app: block state, daily limit, PIN hash, and commands.
3. **TV sync service downloads policy** and stores it locally for offline fallback.
4. **Accessibility service detects foreground apps** and protected Settings sections.
5. **LockActivity opens instantly** when a blocked app or protected section is in front.
6. **PIN or parent approval grants one visit**, then returns to the unlocked target app.

### Flow Summary

```text
Parent App
   |
   | policies, PIN hash, commands, unlock approvals
   v
Firebase Realtime Database
   |
   | sync, app inventory, runtime health, tamper events
   v
Android TV App
   |
   | Accessibility foreground detection
   v
PIN Wall / Device Owner Enforcement
```

---

## Architecture

| Component | Role |
| :--- | :--- |
| **Parent App** | Signs in parents, pairs TVs, controls app locks, sets daily limits, approves unlock requests, and shows tamper events. |
| **TV App** | Runs the hidden setup, sync service, app inventory upload, PIN wall, fallback monitor, and optional Device Owner policies. |
| **Shared Module** | Contains Firebase paths, policy constants, package key encoding, PIN hashing, and common helpers. |
| **Firebase Auth** | Email/password login for parents and anonymous auth for TV devices. |
| **Realtime Database** | Stores devices, policies, app state, runtime health, unlock requests, and tamper events. |

---

## Modules

| Module | Description |
| :--- | :--- |
| `:parent` | Android phone controller app built with Kotlin/Compose UI. |
| `:tv` | Android TV app with Accessibility fallback lock, hidden setup, sync, pairing, and Device Owner support. |
| `:shared` | Shared Kotlin code for Firebase contracts, constants, package keys, dates, and PIN hashing. |
| `firebase/` | Realtime Database rules and rules tests. |
| `scripts/` | PowerShell helpers for building, installing, and provisioning. |

---

## Firebase Setup

Use the Firebase Spark plan and enable:

- **Authentication**
  - Email/password for parent users
  - Anonymous auth for TV devices
- **Realtime Database**

Replace the placeholder values before building real APKs:

| File | Replace |
| :--- | :--- |
| `.firebaserc` | `your-firebase-project-id` |
| `parent/src/main/res/values/firebase_config.xml` | Parent Firebase Android app ID, API key, project ID, database URL |
| `tv/src/main/res/values/firebase_config.xml` | TV Firebase Android app ID, API key, project ID, database URL |

Deploy database rules:

```powershell
firebase use your-firebase-project-id
firebase deploy --only database
```

Run rules tests:

```powershell
npm --prefix firebase install
firebase emulators:exec --only database "npm --prefix firebase test"
```

> Keep live Firebase config local. Do not commit real project IDs, API keys, service account files, device IDs, or app backups to a public repository.

---

## Build Guide

### Recommended Build

```powershell
.\scripts\build.ps1
```

### Direct Gradle Build

```powershell
.\gradlew.bat --no-daemon --console=plain :tv:assembleDebug :parent:assembleDebug
```

### Unit Tests

```powershell
.\gradlew.bat --no-daemon --console=plain test
```

Generated APKs:

| App | Output |
| :--- | :--- |
| Parent phone app | `parent/build/outputs/apk/debug/parent-debug.apk` |
| TV app | `tv/build/outputs/apk/debug/tv-debug.apk` |

---

## Parent App Setup

1. Build the parent APK.
2. Install it on the parent Android phone.
3. Sign in with Firebase email/password authentication.
4. Pair a TV using the QR payload or manual pairing details shown on the TV setup screen.
5. Set a 6-digit parent PIN in the Security tab.
6. Control app locks, daily limits, TV setup access, and unlock approvals from the dashboard.

Install helper:

```powershell
.\scripts\install-parent.ps1
```

---

## TV Fallback Install

Fallback mode is the practical path for TVs where Device Owner provisioning is unavailable.

```powershell
.\scripts\build.ps1
.\scripts\install-tv-fallback.ps1
```

Then complete these TV-side setup steps:

1. Enable the **Device Service** Accessibility service.
2. Grant Usage Access if the firmware requires manual confirmation.
3. Allow background/battery unrestricted operation.
4. Pair the TV with the parent app.
5. Confirm the parent app has a configured PIN.

### Fallback Enforcement

| Surface | Enforcement |
| :--- | :--- |
| Normal apps | Foreground PIN wall |
| Daily limit reached | Foreground PIN wall |
| Live TV / HDMI source app | Source-lock PIN wall |
| Whole Settings app | Parent-controlled row |
| Protected Settings sections | Separate one-visit section locks |
| TV setup screen | Hidden setup opened from parent command and gated by PIN |

---

## Device Owner Provisioning

Use Device Owner mode only on a fresh/factory-reset TV where Android allows enterprise provisioning:

```powershell
.\scripts\provision-tv.ps1
```

Device Owner mode can apply stronger Android policy controls, including app suspension, uninstall restrictions, Settings restrictions, safe-mode blocking, debugging restrictions, and unknown-source install restrictions where supported by firmware.

> Some Android TV firmware blocks Device Owner setup after the TV has already been configured. Use fallback mode when provisioning fails.

---

## Remote Unlock + PIN Wall

The lock screen supports two unlock paths:

| Unlock Path | Result |
| :--- | :--- |
| **Correct PIN** | Grants a one-visit unlock and returns to the target app. |
| **Ask Parent to Unlock** | Creates a Firebase unlock request; parent approval grants the same one-visit unlock. |
| **Parent unblocks app** | Visible lock wall dismisses after sync and returns to the target app. |
| **Daily limit reset** | Visible daily-limit lock dismisses after sync. |

One-visit app unlocks clear when the user leaves the unlocked app. Settings section unlocks clear when leaving that protected section or leaving Settings.

---

## Firebase Paths

| Path | Purpose |
| :--- | :--- |
| `/users/{uid}/devices` | Parent-visible paired TV list. |
| `/devices/{deviceId}/apps` | TV-uploaded app inventory. |
| `/devices/{deviceId}/policies/apps` | Desired app lock and daily-limit policy. |
| `/devices/{deviceId}/states/apps` | Runtime app lock state uploaded by TV. |
| `/devices/{deviceId}/security/pin` | Salted PIN hash metadata written by parent app. |
| `/devices/{deviceId}/security/runtime` | TV protection health, enforcement mode, and foreground status. |
| `/devices/{deviceId}/unlockRequests` | TV-created unlock requests approved/denied by parent. |
| `/devices/{deviceId}/tamperEvents` | Protection and risky-settings tamper events. |
| `/devices/{deviceId}/commands` | Parent-issued commands such as rescan, reset today, unpair, and open setup. |

Package names are stored as Firebase-safe encoded keys. Each app record also stores its original `packageName`.

---

## Security Limits

GuardPulse fallback mode is designed for practical parental control on consumer Android TV firmware. It is not the same as a fully managed enterprise device.

| Risk | Notes |
| :--- | :--- |
| Accessibility disabled | The app can report and recover where possible, but Android still exposes system controls. |
| Device Admin disabled | Tamper events are uploaded when detected. |
| App uninstall attempts | Protected through available fallback controls, but not as strongly as Device Owner. |
| Recovery/factory reset | Cannot be prevented by a normal APK. |
| Root/firmware flashing | Out of scope for app-level protection. |
| Physical access attacks | Out of scope for software-only controls. |

For the strongest protection, use Device Owner mode on compatible hardware. For normal home TVs, fallback mode provides a practical PIN-wall and tamper-alert layer.

---

## Project Info

| Item | Value |
| :--- | :--- |
| Project | GuardPulse Android TV Parental Control |
| Primary language | Kotlin |
| Platform | Android phone + Android TV |
| Backend | Firebase Auth + Realtime Database |
| Repository mode | Public template with placeholder Firebase config |

Built for Android TV parental-control workflows where app access needs to be managed from a parent phone and enforced directly on the TV screen.
