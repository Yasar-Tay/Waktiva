# Official Diyanet 2026 Panel

This directory contains immutable test fixtures copied from the user-provided
`diyanet_high_latitude_v2.zip` research delivery.

## Files

- `official_panel_2026.csv`: 5,110 official city-day rows extracted from 14
  Diyanet PDF tables. Every city has exactly 365 unique dates.
- `city_metadata.json`: coordinates, timezone, criteria profile, and grouping
  metadata used by the reference Python benchmark.
- `BASELINE_2a9ef24.md`: immutable v4 benchmark and confirmed defects.
- `BENCHMARK_V5_AXIS.md`: full-day axis benchmark after wiring the Diyanet
  Sunrise, Dhuhr, and Maghrib values into production output.

## Integrity

- `official_panel_2026.csv` SHA-256:
  `C8D2FB0866296E74EFD448E8B79D00D049B6E567FCE90CBDD203C66020F33118`
- `city_metadata.json` SHA-256:
  `DC83BA18E99FB9485727DD872A0AE70F3EE47661988925E0B5F03F27E798350F`

## Evaluation Split

- Discovery: Stockholm, Oslo, Helsinki
- City holdout: Gothenburg, Umea, Trondheim, Oulu, Copenhagen, Reykjavik
- Polar holdout: Rovaniemi, Tromso
- Direct-regime controls: Toronto, Istanbul, Sydney

Paris is intentionally not part of this panel. It is discovery data for the
delayed-Isha branch. Brussels and Amsterdam must be used as blind city
holdouts before that branch can be treated as a general rule.

## Coordinate Caveat

The coordinates are documented city-center coordinates. Diyanet's internal
reference coordinates are not published. Therefore two evaluations must stay
separate:

1. Source parity: reproduce the official city table using a documented fixed
   coordinate.
2. User-location behavior: calculate for the user's actual GPS coordinate.

Do not tune parameters to hide coordinate uncertainty, and do not modify the
raw CSV to match Waktiva output.
