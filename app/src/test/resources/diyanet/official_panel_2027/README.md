# Official Diyanet 2027 Panel

The same 14 cities as `../official_panel_2026`, for 2027: a second year of
official tables to check the calculation against, never to tune it on.

## Files

- `official_panel_2027.csv`: 5,110 official city-day rows (14 cities, each
  with every date from 2027-01-01 to 2027-12-31). Same columns as the 2026
  panel; the last column is the source page instead of a PDF name.
- `city_metadata.json`: the 2026 panel's metadata with each city's Diyanet
  location id and page slug filled in. Coordinates, timezones and profiles
  are unchanged.
- `build_panel_2027.sh`: the script that fetched and parsed the tables.

## Source

Each city's page on `namazvakitleri.diyanet.gov.tr`
(`/tr-TR/<diyanet_id>/<slug>`), fetched on 2026-10-02, when its yearly table
("Yıllık Namaz Vakti") held 2027. The cells are copied as published: no
rounding, no correction.

## Checks

- Same source as the 2026 panel: each page's monthly table (October 2026),
  434 city-days in all, matches the 2026 panel's rows for those dates
  exactly.
- Every city has 365 unique, consecutive dates and every time is `HH:MM`.

## Integrity

- `official_panel_2027.csv` SHA-256:
  `8F6B23E9A420C62F9EF08D579A2682819AF7277E18D5897C9B8D610BCE6F9A5C`
- `city_metadata.json` SHA-256:
  `F8782AEC4ABA402F7CC45433E342B5BEADAFEB101F5CC92BF30282E65FFE75D1`

The evaluation split, the coordinate caveat and the rule against editing the
raw CSV to match Waktiva output are the 2026 panel's (see its README).
