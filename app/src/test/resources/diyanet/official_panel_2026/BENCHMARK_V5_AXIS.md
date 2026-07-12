# Waktiva v5 Full-Day Axis Benchmark

The v5 benchmark uses the same immutable 2026 official panel as the v4
baseline: 14 cities, 365 days per city, and six events per day.

## Result

- Compared values: 30,660 (14 x 365 x 6)
- Unavailable city-months: 0 (v4: 8)
- Sunrise maximum absolute error: 15 minutes (v4: 104)
- Dhuhr maximum absolute error: 1 minute
- Maghrib maximum absolute error: 15 minutes (v4: 95)
- Fajr and Isha results are unchanged by this axis-only revision.

The production method-13 row now takes Fajr, Sunrise, Dhuhr, Maghrib, and
Isha from the same Diyanet astronomy/adaptive pipeline. Asr remains based on
the Adhan shadow calculation when it produces a valid same-day value.

## High-Latitude Holdouts

| City | Sunrise MAE / Max | Dhuhr MAE / Max | Maghrib MAE / Max |
|---|---:|---:|---:|
| Oulu | 1.28 / 9 | 0.44 / 1 | 2.58 / 10 |
| Reykjavik | 1.34 / 7 | 0.39 / 1 | 2.76 / 9 |
| Rovaniemi | 1.81 / 12 | 0.40 / 1 | 2.99 / 12 |
| Tromso | 2.80 / 15 | 0.47 / 1 | 4.02 / 15 |

## Remaining Defects

1. Tromso Fajr is not fixed by this revision. Its maximum error remains 88
   minutes on 2026-01-01 because the polar estimated event can carry the
   following civil date into the transition clamp.
2. Polar Asr is complete and ordered but not source-parity quality. The
   bounded-latitude/midpoint safety fallback has maximum errors of 123 minutes
   for Tromso and 76 minutes for Rovaniemi. A separate Diyanet Asr model is
   required before claiming full six-event parity.
3. The profile resolver still conflates calculation criteria with the
   high-latitude regime. Toronto remains the known control for that revision.

These limitations are explicit follow-up inputs, not accepted accuracy
targets.
