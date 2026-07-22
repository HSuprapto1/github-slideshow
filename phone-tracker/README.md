# Phone Tracker (Android)

Real-time GPS location app for Android (tested design target: Samsung Galaxy S23 Ultra).

Enter a **country code + mobile number**, watch **this phone’s live location** on a map, and keep contacts identified by phone number.

## Important limit

**A phone number alone cannot track someone else’s location.**

Carriers and Google do not give arbitrary apps that power. This app can:

1. Track **the device it is installed on** in real time (GPS).
2. Store contacts by country code + number.
3. Show contact pins only when a location is known (shared by a consented device that also runs a sharing app, or the built-in **demo pin** for UI testing).

It cannot locate strangers, lost phones you do not control, or any number without their device sharing location.

## Features

- Country code picker + mobile number setup
- Live GPS via Google Play services fused location
- Foreground service so tracking continues while the screen is off
- OpenStreetMap map (osmdroid) — no Google Maps API key required
- Contacts list with optional demo pin near you
- Local storage only (DataStore) — no cloud backend in v1.0.0

## Requirements

- Android Studio Ladybug / Koala or newer (or SDK 34 command-line tools)
- Android 8.0+ (API 26), Google Play services
- Physical phone recommended (Samsung Galaxy S23 Ultra works well)

## Quick start

1. Open the `phone-tracker` folder in **Android Studio**.
2. Let Gradle sync.
3. Connect your Galaxy S23 Ultra (USB debugging on) **or** use an emulator with Google Play.
4. Run the `app` configuration.
5. On first launch:
   - Enter your name, country code, and mobile number
   - Allow location (and notifications on Android 13+)
   - Tap **Start live share**

### Build APK from command line

```bash
cd phone-tracker
./gradlew assembleDebug
```

APK path:

`app/build/outputs/apk/debug/app-debug.apk`

Install on the phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## How to use

1. **Setup** — your identity (country code + number).
2. **Map** — start live share; your pin updates every ~1–2 seconds.
3. **Contacts** — add people by code + number.
4. **Demo pin** (location icon on a contact) — places a test pin near you so you can verify the map UI without a second phone.

## Project structure

```
phone-tracker/
  app/src/main/java/com/hardian/phonetracker/
    MainActivity.kt
    PhoneTrackerApp.kt
    data/          # profile, contacts, storage
    location/      # GPS tracker + foreground service
    ui/            # Compose screens + theme
  README.md
```

## Permissions

| Permission | Why |
|---|---|
| Fine / coarse location | Real-time GPS |
| Foreground service (location) | Keep updates while backgrounded |
| Notifications | Android 13+ service notification |
| Internet | Map tiles |

On Android 10+, for tracking with the screen off for long periods, also allow **Allow all the time** location in system settings if prompted.

## Roadmap

- Cloud sync (Firebase / your backend) so two phones with this app can share live pins by phone number
- Invite link / SMS consent flow
- Background battery optimizations for Samsung One UI
- Optional Google Maps SDK

## Troubleshooting

| Issue | Fix |
|---|---|
| Map is blank | Need internet for OSM tiles; wait a few seconds |
| No GPS updates | Outdoor / clear view of sky; ensure Live share is on |
| Install blocked | Enable **Install unknown apps** for your file manager / ADB |
| Gradle sync fails | Use JDK 17 and Android SDK 34 |

## Author

© Hardian Suprapto
