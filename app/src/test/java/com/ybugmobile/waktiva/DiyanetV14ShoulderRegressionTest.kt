package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.diyanet.DiyanetReconstructionV14
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
import com.ybugmobile.waktiva.data.local.diyanet.minutesAsDuration
import com.ybugmobile.waktiva.data.local.diyanet.roundForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil

/** Exact V14-versus-V14.1 regression for the trusted 16-degree summer shoulder. */
class DiyanetV14ShoulderRegressionTest {

    @Test
    fun `asymmetric shoulder improves trusted official 2026 rows`() {
        val auditDirValue = System.getenv(AUDIT_DIR_ENV)
        assumeTrue("Set $AUDIT_DIR_ENV to run the shoulder regression", !auditDirValue.isNullOrBlank())
        val auditDir = Path.of(auditDirValue!!)
        val qualityPath = auditDir.resolve("v14_kotlin_city_quality.csv")
        val officialPath = auditDir.resolve("official_2026.csv")
        assumeTrue("Run DiyanetV14SegmentAnalysisTest first to create $qualityPath", Files.isRegularFile(qualityPath))
        assertTrue("Missing official fixture: $officialPath", Files.isRegularFile(officialPath))

        val cities = loadTrustedShoulderCities(qualityPath)
        val official = loadOfficialFajr(officialPath, cities.keys)
        val calculator = DiyanetReconstructionV14()
        val legacy = Metrics()
        val asymmetric = Metrics()
        val legacyByCity = cities.keys.associateWith { Metrics() }
        val asymmetricByCity = cities.keys.associateWith { Metrics() }
        var modeRows = 0L

        cities.values.forEachIndexed { index, city ->
            val location = PrayerLocation(city.latitude, city.longitude, city.zoneId)
            official.getValue(city.id).forEach { (date, expected) ->
                val day = calculator.calculate(date, location)
                val diagnostics = day.diagnostics
                if (diagnostics.fajrShoulderMode != ASYMMETRIC_MODE) return@forEach
                modeRows++
                assertTrue(abs(diagnostics.fajrAsymmetricAdjustmentMinutes) <= MAX_CHANGE_MINUTES + 1e-9)

                val legacyGap = requireNotNull(diagnostics.fajrLegacyV14GapMinutes)
                val legacyCorrection = legacyGap - diagnostics.fajrBaseGapMinutes
                if (abs(legacyCorrection) < MATERIAL_CORRECTION_MINUTES) return@forEach

                val actualMinutes = clockMinutes(roundForDisplay(day.fajr).toLocalTime().toString())
                val legacyFajr = roundForDisplay(day.sunrise.minus(minutesAsDuration(legacyGap)))
                val legacyMinutes = clockMinutes(legacyFajr.toLocalTime().toString())
                val actualError = actualMinutes - expected
                val legacyError = legacyMinutes - expected
                asymmetric.add(actualError)
                legacy.add(legacyError)
                asymmetricByCity.getValue(city.id).add(actualError)
                legacyByCity.getValue(city.id).add(legacyError)
            }
            if ((index + 1) % 100 == 0) println("DIYANET_SHOULDER_PROGRESS|${index + 1}/${cities.size}")
        }

        val regressions = cities.keys.mapNotNull { cityId ->
            val old = legacyByCity.getValue(cityId)
            val new = asymmetricByCity.getValue(cityId)
            if (old.count == 0L) null else new.mae - old.mae
        }
        println(
            "DIYANET_V14_1_SHOULDER|eligible_cities=${cities.size}|evaluated_cities=${regressions.size}|" +
                "mode_rows=$modeRows|material_rows=${asymmetric.count}|" +
                "legacy_mae=${legacy.mae}|new_mae=${asymmetric.mae}|new_bias=${asymmetric.bias}|" +
                "new_p99=${asymmetric.percentile(0.99)}|new_gt10=${asymmetric.greaterThanTen}|" +
                "worsened_cities=${regressions.count { it > 0.0 }}|max_city_regression=${regressions.maxOrNull()}"
        )

        assertEquals(EXPECTED_ELIGIBLE_CITY_COUNT, cities.size)
        assertEquals(EXPECTED_EVALUATED_CITY_COUNT, regressions.size)
        assertEquals(EXPECTED_MODE_ROWS, modeRows)
        assertEquals(EXPECTED_MATERIAL_ROWS, asymmetric.count)
        assertTrue("Shoulder MAE did not improve enough: ${legacy.mae} -> ${asymmetric.mae}", asymmetric.mae <= 1.40)
        assertTrue("Shoulder improvement is too small: ${legacy.mae} -> ${asymmetric.mae}", legacy.mae - asymmetric.mae >= 0.65)
        assertTrue("Shoulder bias regressed: ${asymmetric.bias}", abs(asymmetric.bias) <= 0.30)
        assertTrue("Shoulder P99 regressed: ${asymmetric.percentile(0.99)}", asymmetric.percentile(0.99) <= 6)
        assertEquals("No material shoulder row may exceed ten minutes", 0L, asymmetric.greaterThanTen)
        assertTrue("Too many cities regressed: ${regressions.count { it > 0.0 }}", regressions.count { it > 0.0 } <= 35)
        assertTrue("A city regressed by more than half a minute: ${regressions.maxOrNull()}", regressions.maxOrNull()!! <= 0.5)
    }

    private fun loadTrustedShoulderCities(path: Path): Map<Int, City> = Files.newBufferedReader(path).use { reader ->
        val lines = reader.lineSequence().iterator()
        val headers = headerIndex(parseCsvLine(lines.next()))
        buildMap {
            while (lines.hasNext()) {
                val fields = parseCsvLine(lines.next())
                val latitude = fields[headers.getValue("latitude")].toDouble()
                if (
                    fields[headers.getValue("trusted_full_coordinate_axis")].toBoolean() &&
                    fields[headers.getValue("north_v14")].toBoolean() &&
                    latitude in MIN_LATITUDE..MAX_LATITUDE
                ) {
                    val city = City(
                        id = fields[headers.getValue("city_id")].toInt(),
                        latitude = latitude,
                        longitude = fields[headers.getValue("longitude")].toDouble(),
                        zoneId = ZoneId.of(fields[headers.getValue("zone_id")])
                    )
                    put(city.id, city)
                }
            }
        }
    }

    private fun loadOfficialFajr(path: Path, cityIds: Set<Int>): Map<Int, LinkedHashMap<LocalDate, Int>> {
        val rows = cityIds.associateWith { linkedMapOf<LocalDate, Int>() }
        Files.newBufferedReader(path).use { reader ->
            val lines = reader.lineSequence().iterator()
            val headers = headerIndex(parseCsvLine(lines.next()))
            while (lines.hasNext()) {
                val fields = parseCsvLine(lines.next())
                val cityId = fields[headers.getValue("city_id")].toInt()
                rows[cityId]?.put(
                    LocalDate.parse(fields[headers.getValue("date")]),
                    clockMinutes(fields[headers.getValue("fajr")])
                )
            }
        }
        return rows
    }

    private fun headerIndex(headers: List<String>) = headers.withIndex().associate { it.value to it.index }

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

    private fun clockMinutes(value: String): Int {
        val separator = value.indexOf(':')
        return value.substring(0, separator).toInt() * 60 + value.substring(separator + 1, separator + 3).toInt()
    }

    private class Metrics {
        private val histogram = LongArray(721)
        var count = 0L
            private set
        private var absoluteTotal = 0L
        private var signedTotal = 0L
        var greaterThanTen = 0L
            private set
        val mae: Double get() = if (count == 0L) 0.0 else absoluteTotal.toDouble() / count
        val bias: Double get() = if (count == 0L) 0.0 else signedTotal.toDouble() / count

        fun add(error: Int) {
            val absolute = abs(error)
            count++
            signedTotal += error
            absoluteTotal += absolute
            histogram[absolute]++
            if (absolute > 10) greaterThanTen++
        }

        fun percentile(p: Double): Int {
            val target = ceil(count * p).toLong()
            var cumulative = 0L
            histogram.forEachIndexed { error, frequency ->
                cumulative += frequency
                if (cumulative >= target) return error
            }
            return histogram.lastIndex
        }
    }

    private data class City(val id: Int, val latitude: Double, val longitude: Double, val zoneId: ZoneId)

    private companion object {
        const val AUDIT_DIR_ENV = "DIYANET_GLOBAL_AUDIT_DIR"
        const val ASYMMETRIC_MODE = "SIXTEEN_DEGREE_SUMMER_ANCHOR_ASYMMETRIC"
        const val EXPECTED_ELIGIBLE_CITY_COUNT = 1_059
        const val EXPECTED_EVALUATED_CITY_COUNT = 968
        const val EXPECTED_MODE_ROWS = 107_869L
        const val EXPECTED_MATERIAL_ROWS = 61_992L
        const val MATERIAL_CORRECTION_MINUTES = 0.5
        const val MAX_CHANGE_MINUTES = 3.0
        const val MIN_LATITUDE = 45.0
        const val MAX_LATITUDE = 49.07
    }
}
