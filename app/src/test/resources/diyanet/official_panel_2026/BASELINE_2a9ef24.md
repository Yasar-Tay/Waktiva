# Waktiva v4 Baseline at 2a9ef24

Command:

```text
./gradlew.bat :app:testDebugUnitTest \
  --tests "com.ybugmobile.waktiva.DiyanetOfficialPanelBenchmarkTest" --info
```

The benchmark compares 5,110 official city-days. Fajr and Isha are read from
the adaptive Diyanet calculator. The other four events represent the current
hybrid `LocalPrayerCalculator` pipeline.

## Fajr and Isha

| Group | City | Fajr MAE / Max | Isha MAE / Max |
|---|---|---:|---:|
| Discovery | Stockholm | 0.32 / 2 | 1.35 / 4 |
| Discovery | Oslo | 0.57 / 6 | 1.39 / 4 |
| Discovery | Helsinki | 0.53 / 4 | 1.33 / 4 |
| City holdout | Gothenburg | 0.37 / 3 | 1.32 / 4 |
| City holdout | Umea | 0.76 / 5 | 1.66 / 4 |
| City holdout | Trondheim | 0.61 / 5 | 1.59 / 4 |
| City holdout | Oulu | 0.97 / 5 | 1.78 / 5 |
| City holdout | Copenhagen | 0.33 / 3 | 1.32 / 4 |
| City holdout | Reykjavik | 0.94 / 3 | 1.75 / 4 |
| Polar holdout | Rovaniemi | 1.05 / 7 | 1.81 / 8 |
| Polar holdout | Tromso | 7.89 / 88 | 2.45 / 16 |
| Direct control | Toronto | 0.92 / 6 | 1.34 / 3 |
| Direct control | Istanbul | 0.11 / 1 | 0.86 / 2 |
| Direct control | Sydney | 0.21 / 1 | 0.33 / 1 |

## Confirmed Baseline Defects

1. Tromso Fajr has an 88-minute maximum error on 2026-01-01 because an
   estimated morning event can carry the following date into the transition
   clamp.
2. The current full-day pipeline is unavailable for Tromso in January,
   May-July, and November-December, and for Rovaniemi in June-July, because
   Adhan returns a null sunrise and `LocalPrayerCalculator` dereferences it.
3. Published Sunrise/Maghrib do not use the adaptive five-hour prayer axis.
   Maximum errors include Oulu Sunrise 104 minutes, Rovaniemi Sunrise 104
   minutes, Rovaniemi Maghrib 95 minutes, Umea Sunrise 69 minutes, and Umea
   Maghrib 59 minutes.
4. Toronto is a direct 18/16 control in the reference metadata but Waktiva's
   latitude-only resolver classifies it as a high-latitude adaptive city.

These defects are revision inputs, not accepted quality targets.
