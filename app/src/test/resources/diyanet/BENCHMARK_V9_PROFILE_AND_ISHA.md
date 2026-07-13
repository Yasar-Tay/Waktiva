# Waktiva v9 Profile and Isha-Shoulder Benchmark

## Criteria and regime separation

The criteria profile contains only the Fajr and Isha angles. Adaptive
eligibility is a separate latitude policy, after which the annual regime is
selected from astronomical event availability. Every non-empty missing-Fajr
run now selects `ROBUST_MISSING_FAJR_FULL_YEAR`; this includes short runs below
ten days such as Passau.

Toronto is the direct-regime control:

- Criteria: 18/16
- Regime: `DIRECT_ANGLES`
- Fajr MAE: 0.53 minutes
- Fajr maximum error: 1 minute

The remaining 14-city panel guardrails are unchanged. Basel remains the
criteria/regime boundary control: it has the same 18/16 criteria as Toronto,
but remains in `SOLSTICE_ONE_THIRD_GRADUAL` while Toronto remains direct.

## Isha transition validation

The selected official rows cover the June plateau, July return, and August
convergence. Berlin additionally has a complete daily series from 24 July to
11 August, covering the standard autumn shoulder reported in production.

| City | Role | Delayed branch | MAE | Max |
|---|---|---:|---:|---:|
| Amsterdam | Blind holdout | yes | 1.18 | 4 |
| Brussels | Blind holdout | yes | 1.09 | 3 |
| Berlin | Daily standard control | no | 0.85 | 2 |
| London | Standard control | no | 0.82 | 4 |

The normal delayed-Isha branch uses a 13-minute residual amplitude, a
10-minute convergence threshold, and exponent 1.5. The standard branch scales
its autumn amplitude and exponent by the duration of the resolved Isha
missing-event run. Five-hour-bound cities retain their v8 behavior, including
the 20-minute amplitude, 5-minute convergence threshold, and exponent 2 for
their delayed branch.

Global audit details and residual risks are documented in
`docs/DIYANET_GLOBAL_REVERSE_ENGINEERING_V9.md`.
