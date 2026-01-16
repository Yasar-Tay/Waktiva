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

[ ] Secondary Source: Implement UmmahAPI as a network fallback.

[ ] Offline Logic: Integrate Adhan-Java library for local coordinate-based calculation if APIs are unreachable.

Feature 1.2 – 14-Day Room Cache

[x] Create Room Entity PrayerDay (Fields: Date, Hijri Date string, times for all 5 prayers + Sunrise).

[x] Implement WorkManager to check cache every 24 hours.

[x] Logic: If local records < 3 days remaining, fetch the next 14-day block.

[x] Configure Room Schema Export location for migration testing.

Feature 1.3 – Location-Aware Updates

[ ] Trigger data refresh if the user moves > 50km from the last stored location.

[ ] Strategy: Use Foreground Service or "While in Use" check to avoid strict Background Location permission rejection on Play Store.

🔊 EPIC 2 – Adhan Audio & Internal Storage
Goal: Reliable, user-customizable audio delivery.

Feature 2.1 – Custom Audio Management

[ ] Implement System File Picker (MP3, WAV, OGG).

[ ] Internal Copying: When a user selects a file, copy it to the app's files/audio directory.

[ ] Management UI: List custom sounds with a "Delete" option (removing the file from internal storage).

Feature 2.2 – Playback Engine

[ ] Integrate ExoPlayer with AudioAttributes for "Alarm" usage.

[ ] Handle Audio Focus (lower volume of other apps during Adhan).

🚨 EPIC 3 – Alarms & Full-Screen Alerts
Goal: Ensure the user never misses a prayer, regardless of device state.

Feature 3.1 – Exact Alarm Scheduling

[ ] Use AlarmManager.setAlarmClock() for precision.

[ ] Register a BroadcastReceiver to reschedule alarms after a device reboot.

[ ] Handle SCHEDULE_EXACT_ALARM permission and revocation logic (Android 12+).

Feature 3.2 – Full-Screen Intent (The "Call" Experience)

[ ] Implement a High-Priority Notification Channel.

[ ] Design a Full-Screen Activity that launches on top of the lock screen when the Adhan starts.

[ ] UI: Beautiful "Weather-style" background, name of the prayer, current time, and a large "Stop" button.

🧭 EPIC 4 – Qibla & Map Integration
Goal: Free, open-source mapping and high-accuracy orientation.

Feature 4.1 – MapLibre & OSM

[ ] Integrate MapLibre GL SDK.

[ ] Load OpenStreetMap tiles (Free usage).

[ ] Draw a Geodesic line (Great Circle) from user coordinates to Kaaba.

Feature 4.2 – Sensor-Based Compass

[ ] Implement Rotation Vector sensor logic for the needle.

[ ] Calibration Prompt: Detect low sensor accuracy (SENSOR_STATUS_UNRELIABLE) and show a "Figure-8" calibration pop-up.

🎨 EPIC 5 – Weather-Style UI & Experience
Goal: A high-end, immersive interface that changes with the day.

Feature 5.1 – Dynamic Home Screen

[ ] Display: City/Country, Gregorian Date, and API-fetched Hijri Date.

[ ] Dynamic Theming: Change UI gradients based on the next prayer (e.g., Deep purple for Isha, Soft Orange for Fajr).

Feature 5.2 – Prayer List & Countdown

[ ] Show all 5 prayer times in a frosted-glass (Glassmorphism) list.

[ ] Active countdown timer (Hours:Minutes:Seconds) to the next Adhan.

⚙️ EPIC 6 – Settings & Monetization
Goal: User control and project sustainability.

Feature 6.1 – Calculation Settings

[ ] Selection of Madhab (Hanafi/Shafi).

[ ] Selection of Calculation Authority (MWL, ISNA, Egypt, etc.).

Feature 6.2 – Support & Donation

[ ] Integrate Google Play Billing for "Buy me a coffee" style donations.

[ ] Integrate In-App Review API for smart feedback prompts.

🧪 EPIC 7 – Quality Assurance & Localization
Goal: Reliability and global readiness.

Feature 7.1 – Testing Suite

[ ] Unit tests for Adhan-Java integration and time parsing.

[ ] UI Tests for the Full-Screen Alarm activity.

Feature 7.2 – Localization

[ ] Primary Language: English.

[ ] Structure the strings.xml to allow easy community translation for Arabic, Turkish, etc., in future updates.
