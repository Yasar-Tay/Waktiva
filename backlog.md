# Vaktiva Project Backlog

## 1. Core Reliability
- [x] Implement exact alarms using `AlarmManager.setExactAndAllowWhileIdle()`. (Using `setAlarmClock` for maximum reliability)
- [x] Handle `SCHEDULE_EXACT_ALARM` permission for Android 14+ (API 34+).
- [x] Add logic to request battery optimization exclusion (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- [x] Implement `BOOT_COMPLETED` receiver to reschedule alarms after device reboot.

## 2. Media & Service Enhancements
- [x] Implement Audio Focus handling in `AdhanService`.
- [x] Handle `ACTION_AUDIO_BECOMING_NOISY` to pause or lower volume when headphones are unplugged.
- [x] Ensure `WakeLock` usage in `AdhanService` or use ExoPlayer's built-in wake-link support to prevent CPU sleep during playback.

## 3. Architecture & Data
- [x] Implement offline-first prayer time calculation with **Room** caching (cache at least 30 days).
- [ ] Use `Kotlin Flow` to observe settings changes (calculation methods, etc.) and update alarms/UI in real-time.
- [ ] Abstract `SettingsManager` and `PrayerRepository` behind interfaces for unit testing.

## 4. UI/UX
- [x] Enable Edge-to-Edge UI using `enableEdgeToEdge()` and handle `WindowInsets` correctly.
- [ ] Test and optimize layouts for RTL (Right-to-Left) mirroring (Arabic, Urdu, etc.).
- [x] Implement Material You (Dynamic Color) support.

## 5. Privacy & Permissions
- [x] Optimize location logic to prefer "Coarse Location" (only use "Fine Location" if required for Qibla accuracy).
- [x] Implement a pre-permission UI for Notification Permission on Android 13+ to explain why it's needed.

## 6. Background Tasks
- [ ] Use `WorkManager` for non-urgent background tasks (e.g., pre-fetching times, updating location).
- [x] Verify `foregroundServiceType="mediaPlayback"` is correctly declared in `AndroidManifest.xml`.

## 7. Testing
- [ ] Add Compose screenshot tests to ensure UI consistency across screen sizes.
- [ ] Implement "Time-Travel" testing in a debug menu to verify alarm triggering for future prayers.
