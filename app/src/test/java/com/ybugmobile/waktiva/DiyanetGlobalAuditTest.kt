package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.diyanet.AdaptiveDiyanetCalculator
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetProfiles
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
import com.ybugmobile.waktiva.data.local.diyanet.roundForDisplay
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class DiyanetGlobalAuditTest {

    @Test
    fun `export collected Diyanet annual profiles`() {
        val auditDirValue = System.getenv("DIYANET_GLOBAL_AUDIT_DIR")
        assumeTrue("Set DIYANET_GLOBAL_AUDIT_DIR to run the global audit", !auditDirValue.isNullOrBlank())
        val auditDir = Path.of(auditDirValue!!)
        val cities = loadCities(auditDir.resolve("cities.csv"))
        val calculator = AdaptiveDiyanetCalculator()

        Files.newBufferedWriter(auditDir.resolve("global_annual_profiles.csv")).use { writer ->
            writer.appendLine(
                "city_id,regime,five_hour_bounds,delayed_isha,fajr_missing_start,fajr_missing_end," +
                    "fajr_missing_days,isha_missing_start,isha_missing_end,isha_missing_days," +
                    "missing_end_lag_days,summer_ratio,isha_transition_end,isha_autumn_margin"
            )
            cities.values.sortedBy { it.cityId }.forEach { city ->
                val profile = DiyanetProfiles.resolve(city.latitude)
                val annual = calculator.inspectAnnualProfile(LocalDate.of(2026, 7, 1), city.location, profile)
                val fajrRun = annual.dominantMissingRun
                val ishaRun = annual.dominantMissingIshaRun
                val endLag = if (fajrRun != null && ishaRun != null) {
                    java.time.temporal.ChronoUnit.DAYS.between(fajrRun.end, ishaRun.end)
                } else {
                    null
                }
                writer.appendLine(
                    listOf(
                        city.cityId,
                        annual.regime,
                        annual.usesFiveHourBounds,
                        annual.delayedIshaAutumnTransition,
                        fajrRun?.start?.toString().orEmpty(),
                        fajrRun?.end?.toString().orEmpty(),
                        fajrRun?.lengthDays?.toString().orEmpty(),
                        ishaRun?.start?.toString().orEmpty(),
                        ishaRun?.end?.toString().orEmpty(),
                        ishaRun?.lengthDays?.toString().orEmpty(),
                        endLag?.toString().orEmpty(),
                        annual.summerRatio?.toString().orEmpty(),
                        annual.ishaTransitionEnd?.toString().orEmpty(),
                        annual.autumnIshaMarginMinutes?.toString().orEmpty()
                    ).joinToString(",")
                )
            }
        }
    }

    @Test
    fun `audit all collected Diyanet high latitude cities`() {
        val auditDirValue = System.getenv("DIYANET_GLOBAL_AUDIT_DIR")
        assumeTrue("Set DIYANET_GLOBAL_AUDIT_DIR to run the global audit", !auditDirValue.isNullOrBlank())
        val auditDir = Path.of(auditDirValue!!)
        val cities = loadCities(auditDir.resolve("cities.csv"))
        val officialFile = auditDir.resolve("official_2026.csv")
        assertTrue("Global official fixture is missing: $officialFile", Files.exists(officialFile))

        val calculator = AdaptiveDiyanetCalculator()
        val reports = mutableListOf<CityReport>()
        val dailyIshaRows = mutableListOf<DailyIshaRow>()
        var currentCityId: Int? = null
        var accumulator: CityAccumulator? = null

        Files.newBufferedReader(officialFile).useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).forEach { line ->
                val fields = parseCsvLine(line)
                val cityId = fields[0].toInt()
                if (currentCityId != cityId) {
                    accumulator?.let {
                        reports += it.toReport()
                        dailyIshaRows += it.summerIshaRows()
                    }
                    val city = requireNotNull(cities[cityId]) { "Missing metadata for city $cityId" }
                    accumulator = CityAccumulator(city, calculator)
                    currentCityId = cityId
                }
                accumulator!!.add(
                    date = LocalDate.parse(fields[1]),
                    officialFajr = fields[2],
                    officialIsha = fields[7]
                )
            }
        }
        accumulator?.let {
            reports += it.toReport()
            dailyIshaRows += it.summerIshaRows()
        }

        writeReport(auditDir.resolve("global_audit_report.csv"), reports)
        writeDailyIsha(auditDir.resolve("global_audit_isha_summer.csv"), dailyIshaRows)
        val severeIsha = reports.count { it.ishaCenteredMaxAbsolute > 8 || it.ishaMaxFourteenDayDrift > 5 }
        val severeFajr = reports.count { it.fajrCenteredMaxAbsolute > 8 || it.fajrMaxFourteenDayDrift > 5 }
        println(
            "DIYANET_GLOBAL_AUDIT|cities=${reports.size}|" +
                "severe_fajr=$severeFajr|severe_isha=$severeIsha|" +
                "report=${auditDir.resolve("global_audit_report.csv")}"
        )
        assertTrue("No city reports were produced", reports.isNotEmpty())
        assertV9Guardrails(auditDir, reports)
    }

    private fun assertV9Guardrails(auditDir: Path, reports: List<CityReport>) {
        val baselinePath = auditDir.resolve("v8_centered_baseline.csv")
        if (!Files.exists(baselinePath)) return

        val baseline = Files.newBufferedReader(baselinePath).useLines { lines ->
            val iterator = lines.iterator()
            val headers = parseCsvLine(iterator.next()).withIndex().associate { it.value to it.index }
            iterator.asSequence().filter(String::isNotBlank).associate { line ->
                val fields = parseCsvLine(line)
                fields[headers.getValue("city_id")].toInt() to BaselineIshaMetrics(
                    centeredMaxAbsolute = fields[headers.getValue("isha_centered_max_abs")].toInt(),
                    maxFourteenDayDrift = fields[headers.getValue("isha_summer_14d_drift")].toInt()
                )
            }
        }
        val paired = reports.filter { it.city.matchQuality == "exact_unique" && it.city.cityId in baseline }
        val oldSevere = paired.count { baseline.getValue(it.city.cityId).centeredMaxAbsolute > 8 }
        val newSevere = paired.count { it.ishaCenteredMaxAbsolute > 8 }
        val oldDrift = paired.count { baseline.getValue(it.city.cityId).maxFourteenDayDrift > 5 }
        val newDrift = paired.count { it.ishaMaxFourteenDayDrift > 5 }
        val improved = paired.count {
            it.ishaCenteredMaxAbsolute < baseline.getValue(it.city.cityId).centeredMaxAbsolute
        }
        val worsened = paired.count {
            it.ishaCenteredMaxAbsolute > baseline.getValue(it.city.cityId).centeredMaxAbsolute
        }
        val newlySevere = paired.count {
            val old = baseline.getValue(it.city.cityId).centeredMaxAbsolute
            old <= 8 && it.ishaCenteredMaxAbsolute > 8
        }
        val changedFiveHourCities = paired.count {
            it.fiveHourBounds &&
                it.ishaCenteredMaxAbsolute != baseline.getValue(it.city.cityId).centeredMaxAbsolute
        }

        assertTrue("v9 must reduce centered Isha outliers: $oldSevere -> $newSevere", newSevere < oldSevere)
        assertTrue("v9 must reduce Isha summer drift: $oldDrift -> $newDrift", newDrift < oldDrift)
        assertTrue("v9 improvements ($improved) must exceed regressions ($worsened)", improved > worsened)
        assertTrue("v9 introduced $newlySevere new centered Isha outliers", newlySevere <= 5)
        assertTrue("v9 changed $changedFiveHourCities five-hour-bound cities", changedFiveHourCities == 0)
    }

    private fun loadCities(path: Path): Map<Int, City> {
        return Files.newBufferedReader(path).useLines { lines ->
            val iterator = lines.iterator()
            val headers = parseCsvLine(iterator.next()).withIndex().associate { it.value to it.index }
            iterator.asSequence().filter(String::isNotBlank).associate { line ->
                val fields = parseCsvLine(line)
                val city = City(
                    cityId = fields[headers.getValue("city_id")].toInt(),
                    country = fields[headers.getValue("country")],
                    name = fields[headers.getValue("city")],
                    latitude = fields[headers.getValue("latitude")].toDouble(),
                    longitude = fields[headers.getValue("longitude")].toDouble(),
                    zoneId = ZoneId.of(fields[headers.getValue("timezone")]),
                    matchQuality = fields[headers.getValue("match_quality")]
                )
                city.cityId to city
            }
        }
    }

    private fun writeReport(path: Path, reports: List<CityReport>) {
        Files.newBufferedWriter(path).use { writer ->
            writer.appendLine(
                "city_id,country,city,latitude,longitude,timezone,match_quality,regime," +
                    "five_hour_bounds,delayed_isha,days,fajr_mae,fajr_max_abs,fajr_max_date," +
                    "fajr_median_offset,fajr_centered_max_abs,fajr_centered_max_date,fajr_summer_14d_drift," +
                    "isha_mae,isha_max_abs,isha_max_date,isha_median_offset,isha_centered_max_abs," +
                    "isha_centered_max_date,isha_summer_14d_drift"
            )
            reports.sortedWith(compareByDescending<CityReport> { it.ishaMaxAbsolute }
                .thenByDescending { it.ishaMaxFourteenDayDrift })
                .forEach { report ->
                    writer.appendLine(
                        listOf(
                            report.city.cityId,
                            csvEscape(report.city.country),
                            csvEscape(report.city.name),
                            report.city.latitude,
                            report.city.longitude,
                            report.city.zoneId.id,
                            report.city.matchQuality,
                            report.regime,
                            report.fiveHourBounds,
                            report.delayedIsha,
                            report.days,
                            "%.3f".format(java.util.Locale.ROOT, report.fajrMae),
                            report.fajrMaxAbsolute,
                            report.fajrMaxDate,
                            report.fajrMedianOffset,
                            report.fajrCenteredMaxAbsolute,
                            report.fajrCenteredMaxDate,
                            report.fajrMaxFourteenDayDrift,
                            "%.3f".format(java.util.Locale.ROOT, report.ishaMae),
                            report.ishaMaxAbsolute,
                            report.ishaMaxDate,
                            report.ishaMedianOffset,
                            report.ishaCenteredMaxAbsolute,
                            report.ishaCenteredMaxDate,
                            report.ishaMaxFourteenDayDrift
                        ).joinToString(",")
                    )
                }
        }
    }

    private fun writeDailyIsha(path: Path, rows: List<DailyIshaRow>) {
        Files.newBufferedWriter(path).use { writer ->
            writer.appendLine(
                "city_id,date,official_isha,actual_isha,signed_error,direct_isha,estimated_isha," +
                    "phase,transition_curve,missing_end,transition_end,delayed_isha,five_hour_bounds"
            )
            rows.forEach { row ->
                writer.appendLine(
                    listOf(
                        row.cityId,
                        row.date,
                        row.officialIsha,
                        row.actualIsha,
                        row.signedError,
                        row.directIsha.orEmpty(),
                        row.estimatedIsha.orEmpty(),
                        row.phase.orEmpty(),
                        row.transitionCurve.orEmpty(),
                        row.missingEnd.orEmpty(),
                        row.transitionEnd.orEmpty(),
                        row.delayedIsha,
                        row.fiveHourBounds
                    ).joinToString(",")
                )
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        fields += current.toString()
        return fields
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private class CityAccumulator(
        private val city: City,
        private val calculator: AdaptiveDiyanetCalculator
    ) {
        private val profile = DiyanetProfiles.resolve(city.latitude)
        private val annual = calculator.inspectAnnualProfile(
            LocalDate.of(2026, 7, 1),
            city.location,
            profile
        )
        private val fajrErrors = mutableListOf<DatedError>()
        private val ishaErrors = mutableListOf<DatedError>()
        private val summerIsha = mutableListOf<DailyIshaRow>()

        fun add(date: LocalDate, officialFajr: String, officialIsha: String) {
            val result = calculator.calculate(date, city.location, profile)
            result.fajr?.let { fajrErrors += DatedError(date, clockDelta(format(it), officialFajr)) }
            result.isha?.let { value ->
                val actual = format(value)
                val error = clockDelta(actual, officialIsha)
                ishaErrors += DatedError(date, error)
                if (!date.isBefore(LocalDate.of(2026, 5, 1)) && !date.isAfter(LocalDate.of(2026, 9, 15))) {
                    summerIsha += DailyIshaRow(
                        cityId = city.cityId,
                        date = date,
                        officialIsha = officialIsha,
                        actualIsha = actual,
                        signedError = error,
                        directIsha = result.diagnostics.directIsha?.let(::format),
                        estimatedIsha = result.diagnostics.estimatedIsha?.let(::format),
                        phase = result.diagnostics.phase,
                        transitionCurve = result.diagnostics.transitionCurve,
                        missingEnd = result.diagnostics.ishaLastMissing?.toString(),
                        transitionEnd = result.diagnostics.ishaTransitionEnd?.toString(),
                        delayedIsha = annual.delayedIshaAutumnTransition,
                        fiveHourBounds = annual.usesFiveHourBounds
                    )
                }
            }
        }

        fun summerIshaRows(): List<DailyIshaRow> = summerIsha

        fun toReport(): CityReport {
            val fajr = metrics(fajrErrors)
            val isha = metrics(ishaErrors)
            return CityReport(
                city = city,
                regime = annual.regime.name,
                fiveHourBounds = annual.usesFiveHourBounds,
                delayedIsha = annual.delayedIshaAutumnTransition,
                days = maxOf(fajrErrors.size, ishaErrors.size),
                fajrMae = fajr.mae,
                fajrMaxAbsolute = fajr.maxAbsolute,
                fajrMaxDate = fajr.maxDate,
                fajrMedianOffset = fajr.medianOffset,
                fajrCenteredMaxAbsolute = fajr.centeredMaxAbsolute,
                fajrCenteredMaxDate = fajr.centeredMaxDate,
                fajrMaxFourteenDayDrift = fajr.maxFourteenDayDrift,
                ishaMae = isha.mae,
                ishaMaxAbsolute = isha.maxAbsolute,
                ishaMaxDate = isha.maxDate,
                ishaMedianOffset = isha.medianOffset,
                ishaCenteredMaxAbsolute = isha.centeredMaxAbsolute,
                ishaCenteredMaxDate = isha.centeredMaxDate,
                ishaMaxFourteenDayDrift = isha.maxFourteenDayDrift
            )
        }

        private fun metrics(errors: List<DatedError>): Metrics {
            if (errors.isEmpty()) return Metrics(Double.NaN, 0, null, 0, 0, null, 0)
            val worst = errors.maxBy { abs(it.minutes) }
            val sortedMinutes = errors.map { it.minutes }.sorted()
            val medianOffset = sortedMinutes[sortedMinutes.size / 2]
            val centeredWorst = errors.maxBy { abs(it.minutes - medianOffset) }
            val byDate = errors.associateBy { it.date }
            val summerStart = LocalDate.of(2026, 5, 1)
            val summerEnd = LocalDate.of(2026, 9, 15)
            val drift = errors.filter { !it.date.isBefore(summerStart) && !it.date.plusDays(14).isAfter(summerEnd) }
                .maxOfOrNull { start ->
                val end = byDate[start.date.plusDays(14)] ?: return@maxOfOrNull 0
                abs(end.minutes - start.minutes)
            } ?: 0
            return Metrics(
                mae = errors.sumOf { abs(it.minutes) }.toDouble() / errors.size,
                maxAbsolute = abs(worst.minutes),
                maxDate = worst.date,
                medianOffset = medianOffset,
                centeredMaxAbsolute = abs(centeredWorst.minutes - medianOffset),
                centeredMaxDate = centeredWorst.date,
                maxFourteenDayDrift = drift
            )
        }

        private fun format(value: java.time.ZonedDateTime): String =
            roundForDisplay(value).toLocalTime().toString().substring(0, 5)

        private fun clockMinutes(value: String): Int {
            val (hour, minute) = value.split(':').map(String::toInt)
            return hour * 60 + minute
        }

        private fun clockDelta(actual: String, official: String): Int {
            var delta = clockMinutes(actual) - clockMinutes(official)
            if (delta > 720) delta -= 1440
            if (delta < -720) delta += 1440
            return delta
        }
    }

    private data class City(
        val cityId: Int,
        val country: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val zoneId: ZoneId,
        val matchQuality: String
    ) {
        val location = PrayerLocation(latitude, longitude, zoneId)
    }

    private data class DatedError(val date: LocalDate, val minutes: Int)
    private data class DailyIshaRow(
        val cityId: Int,
        val date: LocalDate,
        val officialIsha: String,
        val actualIsha: String,
        val signedError: Int,
        val directIsha: String?,
        val estimatedIsha: String?,
        val phase: String?,
        val transitionCurve: String?,
        val missingEnd: String?,
        val transitionEnd: String?,
        val delayedIsha: Boolean,
        val fiveHourBounds: Boolean
    )
    private data class Metrics(
        val mae: Double,
        val maxAbsolute: Int,
        val maxDate: LocalDate?,
        val medianOffset: Int,
        val centeredMaxAbsolute: Int,
        val centeredMaxDate: LocalDate?,
        val maxFourteenDayDrift: Int
    )

    private data class CityReport(
        val city: City,
        val regime: String,
        val fiveHourBounds: Boolean,
        val delayedIsha: Boolean,
        val days: Int,
        val fajrMae: Double,
        val fajrMaxAbsolute: Int,
        val fajrMaxDate: LocalDate?,
        val fajrMedianOffset: Int,
        val fajrCenteredMaxAbsolute: Int,
        val fajrCenteredMaxDate: LocalDate?,
        val fajrMaxFourteenDayDrift: Int,
        val ishaMae: Double,
        val ishaMaxAbsolute: Int,
        val ishaMaxDate: LocalDate?,
        val ishaMedianOffset: Int,
        val ishaCenteredMaxAbsolute: Int,
        val ishaCenteredMaxDate: LocalDate?,
        val ishaMaxFourteenDayDrift: Int
    )

    private data class BaselineIshaMetrics(
        val centeredMaxAbsolute: Int,
        val maxFourteenDayDrift: Int
    )
}
