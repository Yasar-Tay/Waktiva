#!/usr/bin/env python3
"""Fit Diyanet autumn-Isha shoulder parameters against the global audit."""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd


def clock_minutes(series: pd.Series) -> pd.Series:
    parts = series.str.split(":", expand=True).astype(float)
    return parts[0] * 60.0 + parts[1]


def circular_delta(left: pd.Series, right: pd.Series) -> pd.Series:
    return (left - right + 720.0) % 1440.0 - 720.0


def prepare_groups(data: pd.DataFrame) -> list[tuple[np.ndarray, np.ndarray, np.ndarray, int]]:
    groups = []
    for _, frame in data.groupby("city_id", sort=False):
        frame = frame.sort_values("date")
        first_direct = frame["missing_end"].iloc[0] + pd.Timedelta(days=1)
        post = frame[frame["date"] >= first_direct].dropna(subset=["direct_gap", "target_gap"])
        if post.empty:
            continue
        dates = post["date"].to_numpy(dtype="datetime64[D]").astype(np.int64)
        groups.append(
            (
                dates,
                post["direct_gap"].to_numpy(dtype=float),
                post["target_gap"].to_numpy(dtype=float),
                int(first_direct.to_datetime64().astype("datetime64[D]").astype(np.int64)),
            )
        )
    return groups


def evaluate(
    groups: list[tuple[np.ndarray, np.ndarray, np.ndarray, int]],
    margin: float,
    exponent: float,
    amplitude: float | None = None,
) -> tuple[float, float, int]:
    resolved_amplitude = margin if amplitude is None else amplitude
    error_parts = []
    for dates, direct_gap, target_gap, first_direct in groups:
        above_indexes = np.flatnonzero(direct_gap > margin)
        if above_indexes.size == 0:
            end_index = min(2, dates.size - 1)
        else:
            converged = np.flatnonzero(
                (np.arange(dates.size) >= above_indexes[0]) & (direct_gap <= margin)
            )
            end_index = int(converged[0]) if converged.size else dates.size - 1
        end = int(dates[end_index])
        total_days = max(1, end - first_direct)
        progress = np.clip((dates - first_direct) / total_days, 0.0, 1.0)
        residual = resolved_amplitude * np.power(progress, exponent)
        prediction = np.where(dates <= end, np.minimum(direct_gap, residual), direct_gap)
        error_parts.append(prediction - target_gap)
    if not error_parts:
        return float("nan"), float("nan"), 0
    values = np.concatenate(error_parts)
    return float(np.mean(np.abs(values))), float(np.quantile(np.abs(values), 0.95)), len(values)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-dir", type=Path, default=Path("build/diyanet-global-audit"))
    args = parser.parse_args()
    audit_dir = args.audit_dir.resolve()

    daily = pd.read_csv(audit_dir / "global_audit_isha_summer.csv")
    report = pd.read_csv(audit_dir / "global_audit_report.csv")
    cities = pd.read_csv(audit_dir / "cities.csv")
    profiles = pd.read_csv(audit_dir / "global_annual_profiles.csv")
    metadata = report[["city_id", "isha_median_offset", "match_quality", "regime"]].merge(
        cities[["city_id", "latitude", "country", "city"]], on="city_id", how="left"
    ).merge(
        profiles[["city_id", "missing_end_lag_days", "fajr_missing_days", "isha_missing_days"]],
        on="city_id",
        how="left",
    )
    data = daily.merge(metadata, on="city_id", how="left")
    data = data[
        (data["match_quality"] == "exact_unique")
        & (~data["five_hour_bounds"])
        & (~data["delayed_isha"])
        & (data["regime"] == "ROBUST_MISSING_FAJR_FULL_YEAR")
        & data["missing_end"].notna()
        & data["estimated_isha"].notna()
    ].copy()
    data["date"] = pd.to_datetime(data["date"])
    data["missing_end"] = pd.to_datetime(data["missing_end"])
    data["official_minutes"] = clock_minutes(data["official_isha"])
    data["estimated_minutes"] = clock_minutes(data["estimated_isha"])
    data["direct_minutes"] = clock_minutes(data["direct_isha"])
    data["target_gap"] = circular_delta(
        data["official_minutes"] + data["isha_median_offset"], data["estimated_minutes"]
    )
    data["direct_gap"] = circular_delta(data["direct_minutes"], data["estimated_minutes"])
    rows = []
    segments = {
        "all": data,
        "lat_45_50": data[data["latitude"].abs().between(45.0, 50.0, inclusive="left")],
        "lat_50_55": data[data["latitude"].abs().between(50.0, 55.0, inclusive="left")],
        "lat_55_60": data[data["latitude"].abs().between(55.0, 60.0, inclusive="left")],
        "lat_60_plus": data[data["latitude"].abs() >= 60.0],
        "missing_0_30": data[data["isha_missing_days"].between(0, 30, inclusive="left")],
        "missing_30_60": data[data["isha_missing_days"].between(30, 60, inclusive="left")],
        "missing_60_90": data[data["isha_missing_days"].between(60, 90, inclusive="left")],
        "missing_90_plus": data[data["isha_missing_days"] >= 90],
        "lag_zero": data[data["missing_end_lag_days"] == 0],
        "lag_neg_1_5": data[data["missing_end_lag_days"].between(-5, -1, inclusive="both")],
        "lag_neg_6_plus": data[data["missing_end_lag_days"] <= -6],
        "missing_30_60_lag_negative": data[
            data["isha_missing_days"].between(30, 60, inclusive="left")
            & (data["missing_end_lag_days"] < 0)
        ],
    }
    for segment, segment_data in segments.items():
        groups = prepare_groups(segment_data)
        if not groups:
            continue
        for margin in np.arange(3.0, 18.1, 1.0):
            for exponent in np.arange(0.5, 4.01, 0.25):
                mae, p95, count = evaluate(groups, float(margin), float(exponent))
                rows.append(
                    {
                        "segment": segment,
                        "margin": margin,
                        "exponent": exponent,
                        "mae": mae,
                        "p95": p95,
                        "count": count,
                    }
                )
    result = pd.DataFrame(rows).sort_values(["mae", "p95"])
    result.to_csv(audit_dir / "isha_standard_transition_fit.csv", index=False)
    best = result.sort_values(["segment", "mae", "p95"]).groupby("segment", as_index=False).first()
    print(best.to_string(index=False))

    delayed = daily.merge(metadata, on="city_id", how="left")
    delayed = delayed[
        (delayed["match_quality"] == "exact_unique")
        & (~delayed["five_hour_bounds"])
        & delayed["delayed_isha"]
        & (delayed["regime"] == "ROBUST_MISSING_FAJR_FULL_YEAR")
        & delayed["missing_end"].notna()
        & delayed["estimated_isha"].notna()
    ].copy()
    delayed["date"] = pd.to_datetime(delayed["date"])
    delayed["missing_end"] = pd.to_datetime(delayed["missing_end"])
    delayed["official_minutes"] = clock_minutes(delayed["official_isha"])
    delayed["estimated_minutes"] = clock_minutes(delayed["estimated_isha"])
    delayed["direct_minutes"] = clock_minutes(delayed["direct_isha"])
    delayed["target_gap"] = circular_delta(
        delayed["official_minutes"] + delayed["isha_median_offset"], delayed["estimated_minutes"]
    )
    delayed["direct_gap"] = circular_delta(delayed["direct_minutes"], delayed["estimated_minutes"])
    delayed_groups = prepare_groups(delayed)
    delayed_rows = []
    for convergence in np.arange(3.0, 10.1, 1.0):
        for amplitude in np.arange(12.0, 22.1, 1.0):
            for exponent in np.arange(0.75, 3.01, 0.25):
                mae, p95, count = evaluate(
                    delayed_groups,
                    margin=float(convergence),
                    exponent=float(exponent),
                    amplitude=float(amplitude),
                )
                delayed_rows.append(
                    {
                        "convergence": convergence,
                        "amplitude": amplitude,
                        "exponent": exponent,
                        "mae": mae,
                        "p95": p95,
                        "count": count,
                    }
                )
    delayed_result = pd.DataFrame(delayed_rows).sort_values(["mae", "p95"])
    delayed_result.to_csv(audit_dir / "isha_delayed_transition_fit.csv", index=False)
    print("\nDelayed-Isha best:\n" + delayed_result.head(20).to_string(index=False))


if __name__ == "__main__":
    main()
