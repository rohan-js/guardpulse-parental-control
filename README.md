# GuardPulse Android TV Parental Control

Android TV parental control system with two enforcement paths:

- Device Owner mode for TVs that can be provisioned as an enterprise-owned device.
- Fallback mode for TVs where Device Owner is blocked by firmware/setup behavior, using Device Admin, Accessibility, Usage Access, PIN lock, and Firebase tamper alerts.

## Modules

- `:tv` - Android TV app: Device Owner suspension when available, fallback lock screen when not, app inventory, heartbeat, usage limits, pairing, tamper alerts.
- `:parent` - Android phone controller app: Firebase Auth, pairing, app ON/OFF, daily limits, PIN setup, unlock approval, reset/rescan commands, tamper feed.
- `:shared` - shared Firebase paths, package-key encoding, Firebase initialization, constants.

## Firebase Setup

Use the Firebase Spark plan and enable:

- Authentication: Email/password for parent users, Anonymous for TVs.
- Realtime Database.

Replace the placeholder values in these files before building APKs for a real deployment:

- `tv/src/main/res/values/firebase_config.xml`
- `parent/src/main/res/values/firebase_config.xml`
- `.firebaserc`

The public repository intentionally uses placeholder Firebase values. Use Android app IDs from your Firebase project for the parent and TV apps, use your Firebase web/API key, and set the Realtime Database URL for your project. Do not commit live project credentials or operational IDs to a public fork.

Deploy rules from:

- `firebase/database.rules.json`

```powershell
firebase use your-firebase-project-id
firebase deploy --only database
```

Package names are stored as URL-safe encoded Realtime Database keys because Firebase keys cannot contain dots. Each app record also stores the original `packageName`.

## Build

```powershell
.\scripts\build.ps1
```

## TV Provisioning

Use a fresh/factory-reset Android TV where Device Owner provisioning is allowed:

```powershell
.\scripts\provision-tv.ps1
```

The script installs the TV APK, sets Device Owner, grants usage access for daily limits, and launches the app. The app then applies hardening policies, including uninstall blocking, Settings/app-control restrictions, safe-mode blocking, debugging restriction, factory-reset UI restriction, and unknown-source install restriction where supported.

## TV Fallback Install

Use this path when Device Owner cannot be set on the current TV:

```powershell
.\scripts\build.ps1
.\scripts\install-tv-fallback.ps1
```

Then on the TV app dashboard:

1. Activate Device Admin.
2. Enable the GuardPulse Accessibility Service.
3. Confirm Usage Access is enabled. The script attempts to grant this by ADB, but some firmware builds require the TV settings screen.
4. Pair the TV with the parent app using the QR payload or manual device ID and code.
5. In the parent app Security tab, set a 6-digit PIN.

Install the parent APK on the parent phone:

```powershell
.\scripts\install-parent.ps1
```

Fallback mode blocks apps by detecting the foreground package and opening a full-screen PIN gate. It also gates risky Settings/package-management flows and uploads tamper events. This is practical protection against casual UI tampering, but it is not as strong as true Device Owner because Android still lets a determined user try to disable Accessibility or Device Admin from system settings.

## Firebase Paths Added For Fallback

- `/devices/{deviceId}/security/pin` - salted SHA-256 PIN hash metadata written by the parent app.
- `/devices/{deviceId}/security/runtime` - TV protection health, enforcement mode, and last foreground package.
- `/devices/{deviceId}/tamperEvents/{eventId}` - admin disable attempts, missing protection, risky Settings openings.
- `/devices/{deviceId}/unlockRequests/{requestId}` - TV-created remote unlock requests approved or denied by the parent app.

## Important Limits

Device Owner mode is strong against normal Android UI tampering by a child/user. Fallback mode is a practical lock and tamper-alert layer for this Mi TV, but not uninstall-proof or disable-proof against a technical user. Neither mode protects against recovery-mode factory reset, root, bootloader unlock, firmware flashing, or physical hardware attacks.
