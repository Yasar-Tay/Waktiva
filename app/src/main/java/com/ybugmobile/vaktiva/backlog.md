# 🕌 Vaktiva - Professional Backlog & Architecture

## 🏛️ Architectural Overview (Revised)
The project has been refactored to follow a **Professional Clean Architecture** with **Uni-directional Data Flow (UDF)**.

### Layers:
- **Domain Layer:** Pure Kotlin. Contains Models (`PrayerDay`, `PrayerType`, `NextPrayer`) and Use Cases (`GetNextPrayerUseCase`).
- **Data Layer:** Room (Local DB), Retrofit (API), and Mappers. Implements Repository interfaces and abstracts data sources.
- **UI Layer:** Jetpack Compose with Material 3. Follows the **State-Driven UI** pattern using `HomeViewState` and `HomeViewModel`.
- **DI:** Hilt for dependency injection across all layers.

---

## 🏗️ EPIC 0 – Project Foundation & Architecture
Goal: Establish a scalable, modern Android environment using Clean Architecture.

### Feature 0.1 – Project Infrastructure
- [x] Setup Kotlin, Jetpack Compose, and Hilt (Dependency Injection).
- [x] Configure `libs.versions.toml` for version management.
- [x] Define `minSdk 24` (Android 7.0) to balance modern features and reach.
- [x] Configure Java 17 toolchain and JVM target.
- [x] Resolve Gradle version catalog deprecation warnings.
- [x] Fix `adhan` dependency resolution issue (switched to JitPack `adhan-java`).
- [x] **[NEW]** Refactored to **Clean Architecture**: Decoupled Data and UI layers via a robust Domain layer.
- [x] **[NEW]** Implemented **Uni-directional Data Flow (UDF)** using a centralized `HomeViewState`.

### Feature 0.2 – Multi-Module Core Logic
- [x] Core-Data: Room DB for 14-day storage, DataStore for settings.
- [x] Core-Location: Wrapper for `FusedLocationProvider`.
- [x] Core-Audio: ExoPlayer implementation for internal file playback.
- [x] Core-UI: Material 3 theme, weather-style design tokens.

---

## 📅 EPIC 1 – Prayer Times & Data Lifecycle
Goal: Ensure the app always has 2 weeks of accurate data, even offline.

### Feature 1.1 – Hybrid Data Retrieval
- [x] **Primary Source:** Integrate Aladhan API for 14-day range fetching.
- [x] **Secondary Source:** Implement UmmahAPI as a network fallback.
- [x] **Offline Logic:** Integrate Adhan-Java library for local coordinate-based calculation if APIs are unreachable.
- [x] **[NEW]** Data Mapping: Implemented `PrayerMapper` to convert DTOs/Entities to rich Domain Models.

### Feature 1.2 – 14-Day Room Cache
- [x] Create Room Entity `PrayerDayEntity`.
- [x] Implement `WorkManager` to check cache every 24 hours.
- [x] Logic: If local records < 3 days remaining, fetch the next 14-day block.

### Feature 1.3 – Location-Aware Updates
- [x] Trigger data refresh if the user moves > 50km from the last stored location.
- [x] Foreground "While in Use" location strategy.

---

## 🔊 EPIC 2 – Adhan Audio & Internal Storage
Goal: Reliable, user-customizable audio delivery.

### Feature 2.1 – Custom Audio Management
- [x] Implement System File Picker.
- [x] **[NEW]** Added MIME type validation to ensure only audio files are saved.
- [x] **[NEW]** Enhanced UI to inform users about supported formats (MP3, WAV, OGG, AAC).
- [x] Internal Copying: Copy selected files to app's `files/audio` directory.
- [x] Management UI: List custom sounds with a "Delete" option.
- [ ] **[TODO]** Implement prayer-specific audio selection (e.g., Different Adhan for Fajr).
- [ ] **[TODO]** Implement "Pre-Adhan Warning" system (5 min before with cancel option).

### Feature 2.2 – Playback Engine
- [x] Integrate ExoPlayer with AudioAttributes for "Alarm" usage.
- [x] Handle Audio Focus management.

---

## 🚨 EPIC 3 – Alarms & Full-Screen Alerts
Goal: Ensure the user never misses a prayer.

### Feature 3.1 – Exact Alarm Scheduling
- [x] Use `AlarmManager.setAlarmClock()` for precision.
- [x] Reschedule alarms after device reboot via `BootReceiver`.
- [x] Handle `SCHEDULE_EXACT_ALARM` permission logic.

### Feature 3.2 – Full-Screen Intent
- [x] High-Priority Notification Channel.
- [x] Full-Screen Activity for lock screen alerts.

---

## 🧭 EPIC 4 – Qibla & Map Integration
Goal: High-accuracy orientation and mapping.

### Feature 4.1 – MapLibre & OSM
- [x] Integrate MapLibre GL SDK.
- [x] Load OSM tiles.
- [x] Draw Geodesic line to Kaaba.

### Feature 4.2 – Sensor-Based Compass
- [x] Rotation Vector sensor logic for needle precision.
- [x] Figure-8 calibration UI.

---

## 🎨 EPIC 5 – Weather-Style UI & Experience
Goal: Immersive interface that changes with the day.

### Feature 5.1 – Dynamic Home Screen
- [x] Display: City/Country, Gregorian Date, and Hijri Date.
- [x] **Dynamic Theming:** Adaptive gradients synchronized with prayer windows.
- [x] **[NEW]** Refactored gradient logic to use Domain Models.

### Feature 5.2 – Prayer List & Countdown
- [x] Show all 5 prayer times in a glassmorphism list.
- [x] **[NEW]** Refactored `NextPrayerCountdown` to use `Duration` API and Domain Models.
- [x] **[NEW]** Refactored 24h Circular Visualization for UDF and Domain Models.

---

## ⚙️ EPIC 6 – Settings & Monetization
- [x] Selection of Madhab (Hanafi/Shafi).
- [x] Selection of Calculation Authority.
- [ ] Integrate Google Play Billing for donations.

---

## 🧪 EPIC 7 – Quality Assurance & Localization
- [x] Unit tests for Adhan-Java integration.
- [ ] UI Tests for Full-Screen Alarm activity.
- [ ] Community translation support (Arabic, Turkish, etc.).
