#!/usr/bin/env bash
# Builds official_panel_2027.csv from Diyanet's yearly tables (namazvakitleri.diyanet.gov.tr).
# Usage: build_panel_2027.sh <out_dir>
# Fetched on 2026-10-02, when each page's yearly table held 2027. Its monthly table (that month)
# is kept aside in $WORK/october_2026.csv to check the pages against the 2026 panel.
set -euo pipefail
OUT_DIR="$1"
WORK="${TMPDIR:-/tmp}/diyanet_panel_2027"
mkdir -p "$WORK"
B="https://namazvakitleri.diyanet.gov.tr"

# key|diyanet_id|slug, in the 2026 panel's row order.
CITIES='copenhagen|12618|kopenhag
gothenburg|14320|goteborg
helsinki|12929|helsinki
istanbul|9541|istanbul
oslo|15702|oslo
oulu|12919|oulu
reykjavik|14694|reykjavik
rovaniemi|12923|rovaniemi
stockholm|14351|stockholm
sydney|22182|sydney
toronto|9118|toronto
tromso|15689|tromso
trondheim|15701|trondheim
umea|14401|umea'

CSV="$OUT_DIR/official_panel_2027.csv"
printf 'city,date,imsak,gunes,ogle,ikindi,aksam,yatsi,source_url\n' > "$CSV"
: > "$WORK/october_2026.csv"

echo "$CITIES" | while IFS='|' read -r key id slug; do
  url="$B/tr-TR/$id/$slug-icin-namaz-vakti"
  page="$WORK/$key.html"
  curl -s -m 60 -A "Mozilla/5.0" "$url" -o "$page"
  # Every <td> from a table on, one per line, tagged with the table it is in.
  awk -v key="$key" -v url="$url" -v csv="$CSV" -v oct="$WORK/october_2026.csv" '
    BEGIN {
      split("Ocak Şubat Mart Nisan Mayıs Haziran Temmuz Ağustos Eylül Ekim Kasım Aralık", names, " ")
      for (i = 1; i <= 12; i++) month[names[i]] = sprintf("%02d", i)
      # The page writes September with an HTML entity.
      month["Eyl&#252;l"] = "09"
    }
    /table-caption-monthly/ { table = "monthly" }
    /table-caption-yearly/ { table = "yearly" }
    /<td>/ {
      v = $0; sub(/.*<td>/, "", v); sub(/<\/td>.*/, "", v)
      cell[n++] = v
      if (n == 8) {
        split(cell[0], d, " ")
        date = d[3] "-" month[d[2]] "-" d[1]
        row = key "," date "," cell[2] "," cell[3] "," cell[4] "," cell[5] "," cell[6] "," cell[7]
        if (table == "yearly" && d[3] == "2027") print row "," url >> csv
        if (table == "monthly" && d[3] == "2026") print row >> oct
        n = 0
      }
    }
    /<\/tr>/ { n = 0 }
  ' "$page"
  echo "$key: $(grep -c "^$key,2027-" "$CSV") rows of 2027"
done
