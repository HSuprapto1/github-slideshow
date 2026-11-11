# RoamAtlas

Personal travel history, world map, GPS location tracker, and country day-counting application for Android.

## Features

- **Dashboard** - Custom day trackers with threshold alerts for tax residency calculations
- **World Map** - Interactive Google Maps showing travel history and visited countries
- **GPS Tracking** - Battery-efficient background location recording
- **Google Timeline Import** - Import location history from Google Takeout exports
- **Travel Statistics** - Countries visited, cities explored, yearly summaries
- **Recorded Days Audit** - Filter by date and inspect country evidence or missing data day by day
- **Privacy-First** - Local-only storage, no cloud uploads, no account required

## Quick Start

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34

### Setup

1. Clone the repository
2. Open in Android Studio
3. Add your Google Maps API key to `local.properties`:
   ```properties
   MAPS_API_KEY=your_api_key_here
   ```
4. Sync Gradle and build

### Building

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```
RoamAtlas/
├── app/
│   └── src/main/java/com/roamatlas/
│       ├── data/              # Data layer
│       │   ├── local/         # Room database, DAOs, entities
│       │   └── repository/    # Repository implementations
│       ├── domain/            # Domain layer
│       │   ├── model/         # Domain models
│       │   ├── repository/    # Repository interfaces
│       │   └── usecase/       # Business logic (future)
│       ├── presentation/      # UI layer
│       │   ├── navigation/    # Navigation setup
│       │   ├── screens/       # Screen composables
│       │   ├── components/    # Reusable UI components
│       │   └── theme/         # Material 3 theme
│       ├── di/                # Hilt dependency injection
│       └── util/              # Utilities
├── gradle/
│   └── libs.versions.toml     # Dependency version catalog
└── local.properties           # Local SDK path and API keys (not in VCS)
```

## Tech Stack

- **Kotlin** - Primary language
- **Jetpack Compose** - Modern declarative UI
- **Material 3** - Design system with dynamic colors
- **Room** - Local SQLite database
- **Hilt** - Dependency injection
- **Coroutines & Flow** - Asynchronous programming
- **Google Maps SDK** - Interactive maps
- **Fused Location Provider** - Battery-efficient GPS

## Roadmap

- [x] Project setup and architecture
- [x] Dashboard and statistics using real user data
- [x] Streaming Google on-device Timeline import
- [x] Persistent GPS background tracking service
- [x] Google Maps integration with scalable sampling
- [x] Offline country and city attribution
- [x] Country day counting and transit classification
- [x] AES-256 encrypted export and restore
- [x] Location-aware flight reminders with live traffic/status links
- [ ] Foldable screen optimizations

## Troubleshooting

**Build fails with "SDK location not found"**
- Create `local.properties` with `sdk.dir=/path/to/android/sdk`

**Map shows blank or error**
- Add valid Google Maps API key to `local.properties`
- Enable "Maps SDK for Android" in Google Cloud Console

**Room schema export errors**
- Ensure `app/schemas/` directory exists

## Author

© Hardian Suprapto

## License

Private - All rights reserved
