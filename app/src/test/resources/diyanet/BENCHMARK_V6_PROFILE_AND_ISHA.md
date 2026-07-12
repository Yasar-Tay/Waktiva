# Waktiva v6 Profile and Delayed-Isha Benchmark

## Criteria and regime separation

The criteria profile now contains only the Fajr and Isha angles. Adaptive
eligibility is a separate latitude policy, after which the annual regime is
selected from astronomical event availability.

Toronto is the direct-regime control:

- Criteria: 18/16
- Regime: DIRECT_ANGLES
- Fajr MAE: 0.53 minutes (v5: 0.92)
- Fajr maximum error: 1 minute (v5: 6)

The remaining 14-city panel guardrails are unchanged.

Basel is the separation boundary control: it has the same 18/16 criteria as
Toronto but remains in `SOLSTICE_ONE_THIRD_GRADUAL`, while Toronto remains
`DIRECT_ANGLES`.

## Delayed-Isha validation

The selected official rows cover the June plateau, July return, and August
convergence.

| City | Role | Delayed branch | MAE | Max |
|---|---|---:|---:|---:|
| Amsterdam | Blind holdout | yes | 1.18 | 4 |
| Brussels | Blind holdout | yes | 1.09 | 3 |
| Berlin | Control | no | 1.82 | 7 |
| London | Control | no | 3.09 | 9 |

The event-driven delayed branch therefore generalizes beyond Paris to both
blind holdouts and remains isolated from the controls. Berlin and London keep
the standard branch; their August residual is a separate standard-shoulder
accuracy issue and is not tuned in this revision.
