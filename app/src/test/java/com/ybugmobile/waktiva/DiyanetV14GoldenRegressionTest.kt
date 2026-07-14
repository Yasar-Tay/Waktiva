package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.DiyanetRoutingDiagnosticCode
import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Golden regression runner for the production Kotlin integration.
 *
 * Set DIYANET_GLOBAL_AUDIT_DIR to a directory containing cities.csv and
 * official_2026.csv. Unlike the legacy audit, this runner calls
 * [LocalPrayerCalculator] with method 13, so the same routing, real Adhan Asr,
 * SunCalc astronomy, rounding and southern-hemisphere fallback used by the app
 * are covered.
 */
class DiyanetV14GoldenRegressionTest {

    @Test
    fun `compare production method 13 against official 2026 golden data`() {
        val auditDirValue = System.getenv(AUDIT_DIR_ENV)
        assumeTrue("Set $AUDIT_DIR_ENV to run the V14 golden regression", !auditDirValue.isNullOrBlank())

        val auditDir = Path.of(auditDirValue!!)
        val citiesPath = auditDir.resolve("cities.csv")
        val officialPath = auditDir.resolve("official_2026.csv")
        assertTrue("City fixture is missing: $citiesPath", Files.isRegularFile(citiesPath))
        assertTrue("Official fixture is missing: $officialPath", Files.isRegularFile(officialPath))

        val cities = loadCities(citiesPath)
        val calculator = LocalPrayerCalculator()
        val eventMetrics = Event.entries.associateWith { ErrorMetrics() }
        val allMetrics = ErrorMetrics()
        val evaluatedCityIds = mutableSetOf<Int>()
        var southernFallbackDiagnostics = 0L
        var currentMonth: MonthKey? = null
        var predictions: Map<String, PrayerDayEntity> = emptyMap()

        Files.newBufferedReader(officialPath).use { reader ->
            val lines = reader.lineSequence().iterator()
            require(lines.hasNext()) { "Official fixture is empty: $officialPath" }
            val headers = headerIndex(parseCsvLine(lines.next()))

            while (lines.hasNext()) {
                val line = lines.next()
                if (line.isBlank()) continue
                val fields = parseCsvLine(line)
                val cityId = fields[headers.getValue("city_id")].toInt()
                evaluatedCityIds += cityId
                val city = requireNotNull(cities[cityId]) { "Missing metadata for city $cityId" }
                val dateText = fields[headers.getValue("date")]
                val date = LocalDate.parse(dateText)
                val month = MonthKey(cityId, date.year, date.monthValue)

                if (month != currentMonth) {
                    predictions = calculator.calculateMonthlyPrayerTimes(
                        year = date.year,
                        month = date.monthValue,
                        latitude = city.latitude,
                        longitude = city.longitude,
                        methodId = LocalPrayerCalculator.DIYANET_METHOD_ID,
                        zoneId = city.zoneId,
                        diagnosticSink = { diagnostic ->
                            if (diagnostic.code == DiyanetRoutingDiagnosticCode.SOUTHERN_HEMISPHERE_V14_DISABLED) {
                                southernFallbackDiagnostics++
                            }
                        }
                    ).associateBy(PrayerDayEntity::date)
                    currentMonth = month
                }

                val predicted = requireNotNull(predictions[dateText]) {
                    "Production engine did not return $dateText for city $cityId"
                }
                Event.entries.forEach { event ->
                    val actual = event.read(predicted)
                    val official = fields[headers.getValue(event.csvName)]
                    val error = circularClockDelta(actual, official)
                    eventMetrics.getValue(event).add(error)
                    allMetrics.add(error)
                }
            }
        }

        val rows = Event.entries.map { event ->
            SummaryRow(event.csvName, eventMetrics.getValue(event).summary())
        } + SummaryRow("all", allMetrics.summary())
        writeSummary(auditDir.resolve("v14_kotlin_golden_summary.csv"), rows)

        println(
            "DIYANET_V14_KOTLIN_GOLDEN|fixture_cities=${cities.size}|evaluated_cities=${evaluatedCityIds.size}|" +
                "days=${eventMetrics.getValue(Event.FAJR).count}|" +
                "all_mae=${format(allMetrics.meanAbsoluteError)}|all_p99=${allMetrics.percentile(0.99)}|" +
                "south_fallback_diagnostics=$southernFallbackDiagnostics|" +
                "report=${auditDir.resolve("v14_kotlin_golden_summary.csv")}" 
        )
        assertTrue("No official rows were evaluated", allMetrics.count > 0)
        assertEquals("Unexpected number of cities in official_2026.csv", EXPECTED_CITY_COUNT, evaluatedCityIds.size)
        assertFrozen3040Guardrails(eventMetrics, allMetrics)
    }

    private fun assertFrozen3040Guardrails(
        eventMetrics: Map<Event, ErrorMetrics>,
        allMetrics: ErrorMetrics
    ) {
        val expectedDays = EXPECTED_CITY_COUNT.toLong() * 365L
        Event.entries.forEach { event ->
            assertEquals("Unexpected ${event.csvName} sample count", expectedDays, eventMetrics.getValue(event).count)
        }
        assertEquals("Unexpected all-event sample count", expectedDays * Event.entries.size, allMetrics.count)

        // Frozen from the first production-Kotlin run. The small tolerances detect
        // integration regressions while allowing harmless numerical-library drift.
        assertGuardrail("fajr", eventMetrics.getValue(Event.FAJR), expectedMae = 2.12759, expectedP99 = 47, expectedGt10 = 26_110, expectedMax = 634)
        assertGuardrail("isha", eventMetrics.getValue(Event.ISHA), expectedMae = 2.65982, expectedP99 = 44, expectedGt10 = 26_593, expectedMax = 608)
        assertGuardrail("all", allMetrics, expectedMae = 2.11994, expectedP99 = 46, expectedGt10 = 158_083, expectedMax = 634)
    }

    private fun assertGuardrail(
        event: String,
        metrics: ErrorMetrics,
        expectedMae: Double,
        expectedP99: Int,
        expectedGt10: Long,
        expectedMax: Int
    ) {
        val maeLimit = expectedMae + 0.03
        val p99Limit = expectedP99 + 1
        val gt10Limit = expectedGt10 + 1_000
        val maximumLimit = expectedMax + 10
        assertTrue("$event MAE regressed: ${metrics.meanAbsoluteError} > $maeLimit", metrics.meanAbsoluteError <= maeLimit)
        assertTrue("$event P99 regressed: ${metrics.percentile(0.99)} > $p99Limit", metrics.percentile(0.99) <= p99Limit)
        assertTrue("$event >10 count regressed: ${metrics.greaterThanTen} > $gt10Limit", metrics.greaterThanTen <= gt10Limit)
        assertTrue("$event maximum error regressed: ${metrics.maximum} > $maximumLimit", metrics.maximum <= maximumLimit)
    }

    private fun loadCities(path: Path): Map<Int, City> = Files.newBufferedReader(path).use { reader ->
        val lines = reader.lineSequence().iterator()
        require(lines.hasNext()) { "City fixture is empty: $path" }
        val headers = headerIndex(parseCsvLine(lines.next()))
        buildMap {
            while (lines.hasNext()) {
                val line = lines.next()
                if (line.isBlank()) continue
                val fields = parseCsvLine(line)
                val city = City(
                    cityId = fields[headers.getValue("city_id")].toInt(),
                    latitude = fields[headers.getValue("latitude")].toDouble(),
                    longitude = fields[headers.getValue("longitude")].toDouble(),
                    zoneId = ZoneId.of(fields[headers.getValue("timezone")])
                )
                put(city.cityId, city)
            }
        }
    }

    private fun writeSummary(path: Path, rows: List<SummaryRow>) {
        Files.newBufferedWriter(path).use { writer ->
            writer.appendLine("event,n,mae,p90,p95,p99,p999,within10,gt10,gt10_rate,max")
            rows.forEach { row ->
                val metrics = row.metrics
                writer.appendLine(
                    listOf(
                        row.event,
                        metrics.count,
                        format(metrics.meanAbsoluteError),
                        metrics.p90,
                        metrics.p95,
                        metrics.p99,
                        metrics.p999,
                        metrics.withinTen,
                        metrics.greaterThanTen,
                        format(metrics.greaterThanTen.toDouble() / metrics.count),
                        metrics.maximum
                    ).joinToString(",")
                )
            }
        }
    }

    private fun headerIndex(headers: List<String>): Map<String, Int> =
        headers.withIndex().associate { (index, header) -> header.trim() to index }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when {
                line[index] == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                line[index] == '"' -> quoted = !quoted
                line[index] == ',' && !quoted -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(line[index])
            }
            index++
        }
        fields += current.toString()
        return fields
    }

    private fun circularClockDelta(actual: String, official: String): Int {
        var delta = clockMinutes(actual) - clockMinutes(official)
        while (delta > HALF_DAY_MINUTES) delta -= DAY_MINUTES
        while (delta < -HALF_DAY_MINUTES) delta += DAY_MINUTES
        return delta
    }

    private fun clockMinutes(value: String): Int {
        val separator = value.indexOf(':')
        require(separator > 0) { "Invalid clock value: $value" }
        return value.substring(0, separator).toInt() * 60 + value.substring(separator + 1, separator + 3).toInt()
    }

    private fun format(value: Double): String = "%.5f".format(Locale.ROOT, value)

    private enum class Event(val csvName: String, val read: (PrayerDayEntity) -> String) {
        FAJR("fajr", PrayerDayEntity::fajr),
        SUNRISE("sunrise", PrayerDayEntity::sunrise),
        DHUHR("dhuhr", PrayerDayEntity::dhuhr),
        ASR("asr", PrayerDayEntity::asr),
        MAGHRIB("maghrib", PrayerDayEntity::maghrib),
        ISHA("isha", PrayerDayEntity::isha)
    }

    private class ErrorMetrics {
        private val absoluteHistogram = LongArray(HALF_DAY_MINUTES + 1)
        var count: Long = 0
            private set
        private var absoluteTotal: Long = 0
        var greaterThanTen: Long = 0
            private set
        var maximum: Int = 0
            private set

        val meanAbsoluteError: Double
            get() = if (count == 0L) 0.0 else absoluteTotal.toDouble() / count

        fun add(signedError: Int) {
            val absoluteError = abs(signedError)
            require(absoluteError <= HALF_DAY_MINUTES) { "Non-circular error: $signedError" }
            count++
            absoluteTotal += absoluteError
            absoluteHistogram[absoluteError]++
            if (absoluteError > 10) greaterThanTen++
            if (absoluteError > maximum) maximum = absoluteError
        }

        fun percentile(fraction: Double): Int {
            if (count == 0L) return 0
            val target = ceil(count * fraction).toLong().coerceAtLeast(1L)
            var cumulative = 0L
            absoluteHistogram.forEachIndexed { value, frequency ->
                cumulative += frequency
                if (cumulative >= target) return value
            }
            return maximum
        }

        fun summary(): MetricsSummary = MetricsSummary(
            count = count,
            meanAbsoluteError = meanAbsoluteError,
            p90 = percentile(0.90),
            p95 = percentile(0.95),
            p99 = percentile(0.99),
            p999 = percentile(0.999),
            withinTen = count - greaterThanTen,
            greaterThanTen = greaterThanTen,
            maximum = maximum
        )
    }

    private data class MetricsSummary(
        val count: Long,
        val meanAbsoluteError: Double,
        val p90: Int,
        val p95: Int,
        val p99: Int,
        val p999: Int,
        val withinTen: Long,
        val greaterThanTen: Long,
        val maximum: Int
    )

    private data class SummaryRow(val event: String, val metrics: MetricsSummary)
    private data class MonthKey(val cityId: Int, val year: Int, val month: Int)
    private data class City(val cityId: Int, val latitude: Double, val longitude: Double, val zoneId: ZoneId)

    private companion object {
        const val AUDIT_DIR_ENV = "DIYANET_GLOBAL_AUDIT_DIR"
        const val EXPECTED_CITY_COUNT = 3_040
        const val DAY_MINUTES = 24 * 60
        const val HALF_DAY_MINUTES = DAY_MINUTES / 2
    }
}
