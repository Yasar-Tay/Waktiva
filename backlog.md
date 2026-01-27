# 🕌 Vaktiva - Professional Backlog & Architecture

## 🏛️ Architectural Overview
The project follows **Professional Clean Architecture** with **Uni-directional Data Flow (UDF)** and is fully **Testable**.

### Layers:
- **Domain Layer:** Pure Kotlin. Contains Models (`PrayerDay`, `PrayerType`, `NextPrayer`), Use Cases, and Repository/Manager Interfaces.
- **Data Layer:** Room (Local DB), Retrofit (API), DataStore (Settings), and Mappers. Implements Domain interfaces.
- **UI Layer:** Jetpack Compose with Material 3. State-Driven UI using `HomeViewModel` and `HomeViewState`.
- **DI:** Hilt for dependency injection across all layers.

---

## 🏗️ EPIC 0 – Project Foundation & Architecture
- [x] Setup Kotlin, Jetpack Compose, and Hilt.
- [x] Configure `libs.versions.toml` and Java 17 toolchain.
- [x] **Clean Architecture:** Decoupled Data and UI layers via robust Domain interfaces.
- [x] **UDF:** Implemented centralized `HomeViewState`.
- [x] **Testability:** Abstracted `SettingsManager` and `PrayerRepository` for Unit Testing.

---

## 📅 EPIC 1 – Prayer Times & Data Lifecycle
- [x] **Hybrid Retrieval:** Aladhan API (Primary), UmmahAPI (Fallback), Adhan-Java (Local Fallback).
- [x] **Proactive 30-Day Cache:** Proactively fetches 30-60 days of data when < 15 days remain.
- [x] **Location Privacy:** Optimized to prefer "Coarse Location" (Balanced Power) for prayer times.
- [x] **Location Persistence:** Saves last known location to settings for offline startup.

---

## 🔊 EPIC 2 – Adhan Audio & Service
- [x] **Custom Audio:** System File Picker with MIME validation and internal storage copying.
- [x] **Prayer-Specific Sounds:** Allow different Adhans for each prayer.
- [x] **Audio Focus:** Handles focus loss (ducking/pausing) professionally.
- [x] **Noise Handling:** Pauses automatically on headphone disconnection (`Becoming Noisy`).
- [x] **Stability:** ExoPlayer `WakeMode` and Foreground Service Type (`mediaPlayback`) configured.

---

## 🚨 EPIC 3 – Alarms & Reliability
- [x] **Precision:** Uses `AlarmManager.setAlarmClock()` to bypass Doze mode.
- [x] **Resilience:** `BootReceiver` reschedules alarms after device reboot.
- [x] **Android 14 Ready:** Handles `SCHEDULE_EXACT_ALARM` permission logic.
- [x] **Battery Optimization:** UI logic to request `IGNORE_BATTERY_OPTIMIZATIONS`.
- [x] **Full-Screen Alert:** High-priority notification with `fullScreenIntent` for lock screen.

---

## 🧭 EPIC 4 – Qibla & Map Integration
- [x] **MapLibre:** OSM tiles and Geodesic line to Kaaba.
- [x] **Compass:** Rotation Vector sensor for high precision.
- [x] **Calibration:** Figure-8 calibration UI.

---

## 🎨 EPIC 5 – UI/UX & Experience
- [x] **Edge-to-Edge:** Full status/navigation bar transparency with proper inset handling.
- [x] **Material You:** Dynamic Color support based on user wallpaper (Android 12+).
- [x] **RTL Support:** Mirrored layouts and counter-clockwise 24h visualization for Arabic/Urdu.
- [x] **Dynamic Theming:** Adaptive gradients synchronized with prayer times.
- [x] **Professional Onboarding:** Step-by-step welcome screen with permission rationales.

---

## 🧪 EPIC 6 – Quality Assurance
- [x] **Unit Testing:** Implemented `HomeViewModelTest` using MockK and Turbine.
- [ ] **Screenshot Testing:** Add Compose screenshot tests for UI consistency.
- [ ] **Debug Menu:** Implement "Time-Travel" menu for testing future alarms.

---

## 🏗️ EPIC 7 – Monetization & Localization
- [ ] Integrate Google Play Billing for donations.
- [ ] Community translation support (Arabic, Turkish, etc.).
