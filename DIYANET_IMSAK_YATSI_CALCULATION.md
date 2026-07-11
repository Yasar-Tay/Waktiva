# Diyanet Imsak/Yatsi Calculation

This project now uses the `adaptive_full_year_waktiva_v2` candidate for `methodId == 13`.

## Summary

- `methodId != 13` keeps the existing Adhan-based behavior.
- `methodId == 13` now accepts an explicit `ZoneId` in `LocalPrayerCalculator.calculateMonthlyPrayerTimes(...)`.
- The local calculator keeps Adhan for `sunrise`, `dhuhr`, `asr`, and `maghrib`.
- `fajr` and `isha` are produced by the adaptive Diyanet candidate instead of city fraction tables.
- The v2 update keeps the annual astronomy / ratio model, but upgrades the high-latitude prayer axis to solstice-fixed five-hour bounds when required.

## Profile Policy

- `abs(latitude) <= 43.0`
  uses `waktiva_diyanet_direct_18_17_v1`
- `abs(latitude) > 43.0`
  uses `waktiva_diyanet_adaptive_18_16_v1`

The 43-degree split is a Waktiva compatibility policy, not a claim about Diyanet's official internal source code.

## Timezone Rules

- Network corrections prefer the first valid `meta.timezone` from the AlAdhan response.
- Invalid API timezone values safely fall back to `ZoneId.systemDefault()`.
- Local recalculation and network fallback use `ZoneId.systemDefault()` explicitly.
- The calculator never infers timezone from coordinates.

## Prayer Axis

- `prayerSunrise = astronomicalSunrise - 7 minutes`
- `prayerMaghrib = astronomicalSunset + 7 minutes`

These adjusted anchors are used for:

- direct/annual Diyanet fajr and isha estimation
- shari night and urfi night calculations
- summer ratio derivation
- minimum-night floor checks

## Five-Hour High-Latitude Bounds

When `methodId == 13` uses the adaptive high-latitude profile, Waktiva now checks the target year for a Diyanet-style five-hour bounded axis.

- If the shortest prayer day or shortest prayer night at the relevant solstices is below `295 minutes`, or if sunrise/sunset disappears, the year enters the five-hour bounded family.
- `prayerNoon = astronomicalNoon + 5 minutes`
- summer bounds:
  `sunrise = prayerNoon - 9h30`
  `maghrib = prayerNoon + 9h30`
- winter bounds:
  `sunrise = prayerNoon - 2h30`
  `maghrib = prayerNoon + 2h30`
- Sunrise and maghrib are bounded independently.
- Missing sunrise/sunset days fall back to the closer solstice bound instead of producing a null estimate.

The summer ratio still comes from the last real `-18°` fajr before the dominant missing-fajr run; the bounded axis is applied to nightly estimates, not to the ratio source itself.

## Adaptive Regimes

- `DIRECT_ANGLES`
  uses robust `-18°` fajr and `-17°`/`-16°` isha roots
- `SOLSTICE_ONE_THIRD_GRADUAL`
  uses a solstice one-third estimate with linear spring/autumn shoulders
- `ROBUST_MISSING_FAJR_FULL_YEAR`
  derives `summerRatio` from yearly astronomy and applies season-specific shoulders

## Repository Integration

- Successful AlAdhan fetches keep API rows, then override only `fajr` and `isha` for `methodId == 13`.
- If adaptive correction fails, raw API values are preserved.
- Fetch cache keys for `methodId == 13` now include:
  rounded coordinates, method id, `zoneId.id`, and `adaptive_full_year_waktiva_v1`

## Tests

The unit suite now covers:

- explicit `ZoneId` behavior
- direct vs adaptive regime selection
- yearly summer-ratio checks for Stockholm, Oslo, and Helsinki
- five-hour bounded-family activation for Helsinki
- bounded polar-summer continuity for Tromso
- full-year invariants across Istanbul, Basel, Stockholm, Oslo, Helsinki, Toronto, and Sydney
