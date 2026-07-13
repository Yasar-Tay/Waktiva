#!/usr/bin/env python3
"""Build a reproducible Diyanet city audit fixture from official public tables."""

from __future__ import annotations

import argparse
import csv
import html
import io
import json
import re
import time
import unicodedata
import urllib.parse
import urllib.request
import zipfile
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path


DIYANET_BASE = "https://namazvakitleri.diyanet.gov.tr"
GEONAMES_BASE = "https://download.geonames.org/export/dump"
USER_AGENT = "Waktiva-Diyanet-Audit/1.0"

COUNTRY_ALIASES = {
    "BOSNIA HERZEGOVINA": "BA",
    "CZECH REPUBLIC": "CZ",
    "ESTONYA": "EE",
    "KOSOVA": "XK",
    "MACEDONIA": "MK",
    "NORTH CYPRUS": "CY",
    "SIRBISTAN": "RS",
    "VATIKAN": "VA",
}

MONTHS = {
    "OCAK": 1,
    "SUBAT": 2,
    "MART": 3,
    "NISAN": 4,
    "MAYIS": 5,
    "HAZIRAN": 6,
    "TEMMUZ": 7,
    "AGUSTOS": 8,
    "EYLUL": 9,
    "EKIM": 10,
    "KASIM": 11,
    "ARALIK": 12,
}


@dataclass(frozen=True)
class GeoCity:
    geoname_id: int
    name: str
    latitude: float
    longitude: float
    country_code: str
    population: int
    timezone: str


def normalize(value: str) -> str:
    value = value.replace("ı", "i").replace("İ", "I")
    value = unicodedata.normalize("NFKD", value)
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    return re.sub(r"[^A-Z0-9]+", " ", value.upper()).strip()


def request_bytes(url: str, attempts: int = 4) -> bytes:
    error = None
    for attempt in range(attempts):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=45) as response:
                return response.read()
        except Exception as exc:  # noqa: BLE001 - retry network failures uniformly
            error = exc
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Request failed after {attempts} attempts: {url}") from error


def cached_bytes(cache_dir: Path, relative: str, url: str) -> bytes:
    target = cache_dir / relative
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(request_bytes(url))
    return target.read_bytes()


def load_country_codes(country_info: bytes) -> dict[str, str]:
    result = dict(COUNTRY_ALIASES)
    for raw_line in country_info.decode("utf-8").splitlines():
        if not raw_line or raw_line.startswith("#"):
            continue
        fields = raw_line.split("\t")
        if len(fields) < 5:
            continue
        code, name = fields[0], fields[4]
        result[normalize(name)] = code
    return result


def load_geonames(cities_zip: bytes) -> tuple[dict[tuple[str, str], list[GeoCity]], dict[str, list[GeoCity]]]:
    exact: dict[tuple[str, str], list[GeoCity]] = defaultdict(list)
    by_country: dict[str, list[GeoCity]] = defaultdict(list)
    with zipfile.ZipFile(io.BytesIO(cities_zip)) as archive:
        filename = archive.namelist()[0]
        with archive.open(filename) as stream:
            for raw_line in io.TextIOWrapper(stream, encoding="utf-8"):
                fields = raw_line.rstrip("\n").split("\t")
                if len(fields) < 19:
                    continue
                city = GeoCity(
                    geoname_id=int(fields[0]),
                    name=fields[1],
                    latitude=float(fields[4]),
                    longitude=float(fields[5]),
                    country_code=fields[8],
                    population=int(fields[14] or 0),
                    timezone=fields[17],
                )
                aliases = {fields[1], fields[2], *fields[3].split(",")}
                for alias in aliases:
                    key = normalize(alias)
                    if key:
                        exact[(city.country_code, key)].append(city)
                by_country[city.country_code].append(city)
    return exact, by_country


def city_name_candidates(name: str) -> list[str]:
    values = [name, re.sub(r"\s*\([^)]*\)\s*$", "", name)]
    return list(dict.fromkeys(filter(None, (normalize(value) for value in values))))


def match_city(
    country_code: str,
    city_name: str,
    exact: dict[tuple[str, str], list[GeoCity]],
) -> tuple[GeoCity | None, str, int]:
    matches: dict[int, GeoCity] = {}
    for candidate in city_name_candidates(city_name):
        for city in exact.get((country_code, candidate), []):
            matches[city.geoname_id] = city
    if not matches:
        return None, "unmatched", 0
    ranked = sorted(matches.values(), key=lambda city: city.population, reverse=True)
    quality = "exact_unique" if len(ranked) == 1 else "exact_ambiguous_population"
    return ranked[0], quality, len(ranked)


def extract_year_rows(page: bytes, year: int) -> list[list[str]]:
    text = page.decode("utf-8", errors="replace")
    table_match = re.search(
        r'<table[^>]+id="yourTable"[^>]*>(?P<table>.*?)</table>',
        text,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if not table_match:
        return []
    rows = []
    for row_html in re.findall(r"<tr[^>]*>(.*?)</tr>", table_match.group("table"), re.DOTALL | re.IGNORECASE):
        cells = [
            re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", cell))).strip()
            for cell in re.findall(r"<td[^>]*>(.*?)</td>", row_html, re.DOTALL | re.IGNORECASE)
        ]
        if len(cells) != 8:
            continue
        date_parts = normalize(cells[0]).split()
        if len(date_parts) < 3 or int(date_parts[2]) != year:
            continue
        month = MONTHS.get(date_parts[1])
        if month is None:
            continue
        date = f"{year:04d}-{month:02d}-{int(date_parts[0]):02d}"
        rows.append([date, *cells[2:]])
    return rows


def fetch_city_year(
    cache_dir: Path,
    city_id: int,
    year: int,
    cached_only: bool = False,
) -> tuple[int, list[list[str]], str | None]:
    relative = f"pages/{year}/{city_id}.html"
    url = f"{DIYANET_BASE}/tr-TR/{city_id}/waktiva-audit"
    try:
        target = cache_dir / relative
        if cached_only and not target.exists():
            return city_id, [], "page missing from cache"
        page = target.read_bytes() if cached_only else cached_bytes(cache_dir, relative, url)
        rows = extract_year_rows(page, year)
        if len(rows) not in (365, 366):
            return city_id, rows, f"expected full year, found {len(rows)} rows"
        return city_id, rows, None
    except Exception as exc:  # noqa: BLE001 - report all per-city failures
        return city_id, [], str(exc)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("build/diyanet-global-audit"))
    parser.add_argument("--year", type=int, default=2026)
    parser.add_argument("--min-abs-latitude", type=float, default=45.0)
    parser.add_argument("--workers", type=int, default=6)
    parser.add_argument("--fetch-tables", action="store_true")
    parser.add_argument("--cached-only", action="store_true")
    parser.add_argument("--limit", type=int)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output: Path = args.output.resolve()
    cache = output / "cache"
    output.mkdir(parents=True, exist_ok=True)

    countries = json.loads(cached_bytes(cache, "diyanet/countries.json", f"{DIYANET_BASE}/assets/locations/countries.json"))
    country_info = cached_bytes(cache, "geonames/countryInfo.txt", f"{GEONAMES_BASE}/countryInfo.txt")
    cities_zip = cached_bytes(cache, "geonames/cities500.zip", f"{GEONAMES_BASE}/cities500.zip")
    country_codes = load_country_codes(country_info)
    exact, _ = load_geonames(cities_zip)

    matched = []
    unmatched = []
    for country in countries:
        country_name = country["CountryName"]
        country_code = country_codes.get(normalize(country_name))
        if country_code is None:
            unmatched.append([country_name, "", "", "country_unmatched", "0"])
            continue
        encoded_name = urllib.parse.quote(country_name, safe="")
        try:
            locations = json.loads(cached_bytes(
                cache,
                f"diyanet/locations/{country['CountryID']}.json",
                f"{DIYANET_BASE}/assets/locations/{encoded_name}.json",
            ))
        except Exception as exc:  # noqa: BLE001
            unmatched.append([country_name, "", "", f"location_list_error:{exc}", "0"])
            continue
        for location in locations:
            city, quality, candidate_count = match_city(country_code, location["City"], exact)
            if city is None:
                unmatched.append([country_name, location["City"], location["CityID"], quality, candidate_count])
                continue
            if abs(city.latitude) < args.min_abs_latitude:
                continue
            matched.append({
                "city_id": int(location["CityID"]),
                "country": country_name,
                "city": location["City"],
                "latitude": city.latitude,
                "longitude": city.longitude,
                "timezone": city.timezone,
                "geoname_id": city.geoname_id,
                "population": city.population,
                "match_quality": quality,
                "match_candidates": candidate_count,
            })

    matched.sort(key=lambda row: (row["country"], row["city"], row["city_id"]))
    if args.limit:
        matched = matched[: args.limit]

    metadata_path = output / "cities.csv"
    with metadata_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(matched[0].keys()) if matched else ["city_id"])
        writer.writeheader()
        writer.writerows(matched)
    with (output / "unmatched.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["country", "city", "city_id", "reason", "candidate_count"])
        writer.writerows(unmatched)

    print(f"matched_high_latitude={len(matched)} unmatched_all_latitudes={len(unmatched)}")
    if not args.fetch_tables:
        return

    official_path = output / f"official_{args.year}.csv"
    failures = []
    all_rows = []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [
            pool.submit(fetch_city_year, cache, row["city_id"], args.year, args.cached_only)
            for row in matched
        ]
        for index, future in enumerate(as_completed(futures), start=1):
            city_id, rows, error = future.result()
            if error:
                failures.append([city_id, error])
            else:
                all_rows.extend([[city_id, *row] for row in rows])
            if index % 100 == 0 or index == len(futures):
                print(f"tables={index}/{len(futures)} failures={len(failures)}")

    all_rows.sort(key=lambda row: (int(row[0]), row[1]))
    with official_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["city_id", "date", "fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"])
        writer.writerows(all_rows)
    with (output / "fetch_failures.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["city_id", "error"])
        writer.writerows(failures)
    print(f"official_rows={len(all_rows)} failed_cities={len(failures)}")


if __name__ == "__main__":
    main()
