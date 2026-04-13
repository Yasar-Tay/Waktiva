# Waktiva

Waktiva is a privacy-first Android prayer companion built with a modern native stack. The project combines accurate prayer time calculations, Qibla direction tools, customizable Adhan playback, system reliability checks, multilingual support, and a polished Compose UI in a single codebase.

This repository already includes:

- Location-aware prayer times with offline fallback calculation
- Qibla screen with compass and map modes
- Per-prayer Adhan selection and pre-Adhan warnings
- Onboarding flow for permissions and personalization
- Hijri and religious-day context
- Dynamic backgrounds, weather-aware UI hooks, and a Glance widget
- Clean Architecture inspired layering with Hilt, Room, DataStore, and WorkManager

## Feature Highlights

### Accurate Prayer Times

- Hybrid data approach with remote APIs plus local calculation fallback
- Multiple calculation methods including Turkey/Diyanet, MWL, ISNA, Egypt, Makkah, Karachi, and more
- Madhab-aware Asr handling
- Cached prayer data for a more resilient offline experience

### Reliable Adhan Experience

- Exact-alarm based scheduling for timely notifications
- Foreground audio playback with Media3
- Individual Adhan sound selection for each prayer
- Pre-Adhan warning support
- Boot rescheduling and battery optimization guidance

### Qibla Tools

- Real-time compass-based Qibla guidance
- Map-based Qibla view powered by MapLibre and OpenStreetMap tooling
- Sensor calibration guidance for better accuracy

### Modern Muslim Utility App

- Hijri date and religious-day support
- Prayer-focused home screen with countdown and status awareness
- Localized app strings across 20 language packs, including RTL locales
- Jetpack Glance widget for next-prayer visibility from the home screen

## Tech Stack

- Kotlin + Java 17
- Android SDK 36, target SDK 35, min SDK 24
- Jetpack Compose + Material 3
- Hilt for dependency injection
- Room for local persistence
- DataStore for user preferences
- WorkManager for periodic sync/update flows
- Media3 for Adhan playback
- Retrofit + OkHttp for network access
- Adhan Java for prayer calculation support
- MapLibre + osmdroid for Qibla/map features
- Glance for app widgets

## Architecture

The codebase follows a practical layered structure:

- `domain`: business models, contracts, and use cases
- `data`: repositories, local storage, remote services, alarms, workers, and device integrations
- `ui`: Compose screens, view models, navigation, theme, and widgets
- `di`: dependency injection modules

The app is organized around state-driven screens, background workers for refresh flows, and Android services/receivers for reliable Adhan behavior.

## Project Structure

```text
Waktiva/
|- app/
|  |- src/main/java/com/ybugmobile/waktiva/
|  |  |- data/
|  |  |- di/
|  |  |- domain/
|  |  |- receiver/
|  |  |- service/
|  |  |- ui/
|  |  \- utils/
|  \- src/main/res/
|- gradle/
|- build.gradle.kts
\- settings.gradle.kts
```

## Getting Started

### Requirements

- Android Studio with a recent stable build
- JDK 17
- Android device or emulator running Android 7.0+ (API 24+)

### Run Locally

```bash
git clone <your-fork-or-repo-url>
cd Waktiva
./gradlew assembleDebug
```

Open the project in Android Studio, let Gradle sync, then run the `app` configuration on an emulator or physical device.

## Localization

Waktiva already ships with a broad translation base. The repository includes localized resources for languages such as:

- English
- Arabic
- Bengali
- Bosnian
- Dutch
- Finnish
- French
- German
- Indonesian
- Italian
- Malay
- Persian
- Polish
- Portuguese
- Russian
- Albanian
- Spanish
- Swedish
- Turkish
- Urdu

In Android resource terms, the project currently includes these locale folders:

- `values`
- `values-ar`
- `values-bn`
- `values-bs`
- `values-de`
- `values-es`
- `values-fa`
- `values-fi`
- `values-fr`
- `values-in`
- `values-it`
- `values-ms`
- `values-nl`
- `values-pl`
- `values-pt`
- `values-ru`
- `values-sq`
- `values-sv`
- `values-tr`
- `values-ur`

## Testing

The project currently includes unit tests around local prayer time calculation logic.

```bash
./gradlew testDebugUnitTest
```

## Roadmap Direction

Based on the current repository state and backlog, the product direction includes:

- stronger UI test coverage
- richer widget interactions
- deeper moon and weather experiences
- continued polish around reliability, onboarding, and donations

## Contributing

Contributions are welcome. If you want to improve the app:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run relevant checks
5. Open a pull request

## License and Privacy

The repository currently does not include a top-level `LICENSE` file, but the in-app license screen states that:

- Waktiva is released as open-source software under the MIT License
- the app is provided "as is" without warranty
- user data is not stored, resold, or distributed
- the app does not show ads, use tracking SDKs, collect analytics, or sell personal data
- bundled Adhan recordings are used with permission and should not be redistributed outside the app

The same in-app license content also references third-party components such as AndroidX, Jetpack Compose, Kotlin, Hilt, Room, DataStore, WorkManager, Media3, Retrofit, OkHttp, MapLibre, osmdroid, SunCalc, the Adhan library, Google Play Services Location, and Google Play Billing.

## Notes

- Adding a real `LICENSE` file at the repository root would make the project much clearer for GitHub visitors and contributors.
- If you want a stronger storefront presentation, adding real screenshots or a short demo GIF would make this README even more compelling.
