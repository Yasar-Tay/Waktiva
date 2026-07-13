param(
    [string]$MetadataPath = "build/diyanet-global-audit/cities.csv",
    [string]$CacheDir = "build/diyanet-global-audit/cache/pages/2026",
    [int]$Workers = 12
)

$ErrorActionPreference = "Stop"
$rows = Import-Csv $MetadataPath
$absoluteCache = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $CacheDir))
New-Item -ItemType Directory -Force $absoluteCache | Out-Null

$batches = @()
for ($i = 0; $i -lt $Workers; $i++) {
    $batches += ,@()
}
for ($i = 0; $i -lt $rows.Count; $i++) {
    $batches[$i % $Workers] += [string]$rows[$i].city_id
}

$jobs = for ($i = 0; $i -lt $Workers; $i++) {
    Start-Job -ArgumentList ($batches[$i] -join ","), $absoluteCache -ScriptBlock {
        param($CityIdText, $TargetDir)
        $CityIds = $CityIdText -split ","
        $failures = @()
        foreach ($cityId in $CityIds) {
            $target = Join-Path $TargetDir "$cityId.html"
            if ((Test-Path $target) -and (Get-Item $target).Length -gt 1000) {
                continue
            }
            $success = $false
            $lastError = ""
            for ($attempt = 1; $attempt -le 4 -and -not $success; $attempt++) {
                $temp = "$target.tmp"
                try {
                    Invoke-WebRequest -UseBasicParsing -Uri "https://namazvakitleri.diyanet.gov.tr/tr-TR/$cityId/waktiva-audit" -OutFile $temp -TimeoutSec 45
                    if (-not (Select-String -Path $temp -Pattern 'id="yourTable"' -Quiet)) {
                        throw "year table missing"
                    }
                    Move-Item -Force $temp $target
                    $success = $true
                } catch {
                    $lastError = $_.Exception.Message
                    Remove-Item $temp -ErrorAction SilentlyContinue
                    Start-Sleep -Seconds $attempt
                }
            }
            if (-not $success) {
                $failures += [pscustomobject]@{ city_id = $cityId; error = $lastError }
            }
        }
        $failures
    }
}

while (($jobs | Where-Object State -eq "Running").Count -gt 0) {
    $count = (Get-ChildItem $absoluteCache -Filter *.html -ErrorAction SilentlyContinue).Count
    Write-Output "pages=$count/$($rows.Count)"
    Start-Sleep -Seconds 10
}

$failures = @($jobs | Receive-Job)
$jobs | Remove-Job
$failures | Export-Csv (Join-Path $absoluteCache "download_failures.csv") -NoTypeInformation -Encoding UTF8
$finalCount = (Get-ChildItem $absoluteCache -Filter *.html -ErrorAction SilentlyContinue).Count
Write-Output "pages=$finalCount/$($rows.Count) failures=$($failures.Count)"
if ($failures.Count -gt 0) {
    exit 1
}
