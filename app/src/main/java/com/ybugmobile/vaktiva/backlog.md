🏗️ EPIC 0 – Project Foundation & Architecture
Goal: Establish a scalable, modern Android environment using Clean Architecture.

Feature 0.1 – Project Infrastructure
[x] Setup Kotlin, Jetpack Compose, and Hilt (Dependency Injection).
[x] Configure libs.versions.toml for version management.
[x] Define minSdk 24 (Android 7.0) to balance modern features and reach.
[x] Configure Java 17 toolchain and JVM target.
[x] Resolve Gradle version catalog deprecation warnings (multi-string notation).

Feature 0.2 – Multi-Module Core Logic
[x] Core-Data: Room DB for 14-day storage, DataStore for settings.
[x] Core-Location: Wrapper for FusedLocationProvider.
[x] Core-Audio: ExoPlayer implementation for internal file playback.
[x] Core-UI: Material 3 theme, weather-style design tokens, and English string resources.

📅 EPIC 1 – Prayer Times & Data Lifecycle
Goal: Ensure the app always has 2 weeks of accurate data, even offline.

Feature 1.1 – Hybrid Data Retrieval
[x] Primary Source: Integrate Aladhan API for 14-day range fetching.
[x] Secondary Source: Implement UmmahAPI as a network fallback.
[x] Offline Logic: Integrate Adhan-Java library for local coordinate-based calculation if APIs are unreachable.

Feature 1.2 – 14-Day Room Cache
[x] Create Room Entity PrayerDay (Fields: Date, Hijri Date string, times for all 5 prayers + Sunrise).
[x] Implement WorkManager to check cache every 24 hours.
[x] Logic: If local records < 3 days remaining, fetch the next 14-day block.
[x] Configure Room Schema Export location for migration testing.

Feature 1.3 – Location-Aware Updates
[x] Trigger data refresh if the user moves > 50km from the last stored location.
[x] Strategy: Use Foreground Service or "While in Use" check to avoid strict Background Location permission rejection on Play Store.

🔊 EPIC 2 – Adhan Audio & Internal Storage
Goal: Reliable, user-customizable audio delivery.

Feature 2.1 – Custom Audio Management
[x] Implement System File Picker (MP3, WAV, OGG).
[x] Internal Copying: When a user selects a file, copy it to the app's files/audio directory.
[x] Management UI: List custom sounds with a "Delete" option (removing the file from internal storage).

Feature 2.2 – Playback Engine
[x] Integrate ExoPlayer with AudioAttributes for "Alarm" usage.
[x] Handle Audio Focus (lower volume of other apps during Adhan).

🚨 EPIC 3 – Alarms & Full-Screen Alerts
Goal: Ensure the user never misses a prayer, regardless of device state.

Feature 3.1 – Exact Alarm Scheduling
[x] Use AlarmManager.setAlarmClock() for precision.
[x] Register a BroadcastReceiver to reschedule alarms after a device reboot.
[x] Handle SCHEDULE_EXACT_ALARM permission and revocation logic (Android 12+).

Feature 3.2 – Full-Screen Intent (The "Call" Experience)
[x] Implement a High-Priority Notification Channel.
[x] Design a Full-Screen Activity that launches on top of the lock screen when the Adhan starts.
[ ] UI: Beautiful "Weather-style" background, name of the prayer, current time, and a large "Stop" button.

🧭 EPIC 4 – Qibla & Map Integration
... (rest of the file remains same)
