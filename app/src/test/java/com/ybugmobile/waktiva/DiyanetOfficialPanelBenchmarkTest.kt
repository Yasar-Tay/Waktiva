package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.diyanet.AdaptiveDiyanetCalculator
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
import com.ybugmobile.waktiva.data.local.diyanet.resolveDiyanetProfile
import com.ybugmobile.waktiva.data.local.diyanet.roundForDisplay
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil

class DiyanetOfficialPanelBenchmarkTest {

    private val calculator = LocalPrayerCalculator()
    private val adaptiveCalculator = AdaptiveDiyanetCalculator()

    @Test
    fun `official panel fixture is complete and unique`() {
        val rows = loadOfficialRows()

        assertEquals(14 * 365, rows.size)
        assertEquals(rows.size, rows.map { it.city to it.date }.toSet().size)
        assertEquals(PANEL_CITIES.map { it.key }.toSet(), rows.map { it.city }.toSet())
        rows.groupBy { it.city }.forEach { (_, cityRows) ->
            assertEquals(365, cityRows.size)
            assertEquals(LocalDate.of(2026, 1, 1), cityRows.minOf { it.date })
            assertEquals(LocalDate.of(2026, 12, 31), cityRows.maxOf { it.date })
        }
    }

    @Test
    fun `print v9 official panel benchmark`() {
        val officialByCity = loadOfficialRows().groupBy { it.city }
        val allErrors = mutableListOf<BenchmarkError>()
        val unavailable = mutableListOf<UnavailableMonth>()

        PANEL_CITIES.forEach { city ->
            val officialRows = requireNotNull(officialByCity[city.key]).associateBy { it.date }
            val zoneId = ZoneId.of(city.timezone)
            val location = PrayerLocation(city.latitude, city.longitude, zoneId)
            val profile = resolveDiyanetProfile(city.latitude)
            val actualRows = (1..12).flatMap { month ->
                runCatching {
                    calculator.calculateMonthlyPrayerTimes(
                            year = 2026,
                            month = month,
                            latitude = city.latitude,
                            longitude = city.longitude,
                            methodId = 13,
                            zoneId = zoneId
                        )
                    }.getOrElse { error ->
                        unavailable += UnavailableMonth(
                            city = city.key,
                            month = month,
                            reason = error::class.simpleName ?: "unknown"
                        )
                        emptyList()
                    }
                }
                .associateBy { LocalDate.parse(it.date) }

            officialRows.forEach { (date, official) ->
                val actual = actualRows[date]
                val adaptive = adaptiveCalculator.calculate(date, location, profile)
                actual?.let { assertEquals(date.toString(), it.date) }
                PrayerEvent.entries.forEach { event ->
                    val actualTime = when (event) {
                        PrayerEvent.FAJR -> adaptive.fajr?.let(::formatAdaptive)
                        PrayerEvent.ISHA -> adaptive.isha?.let(::formatAdaptive)
                        else -> actual?.let(event.actual)
                    } ?: return@forEach
                    allErrors += BenchmarkError(
                        city = city,
                        date = date,
                        event = event,
                        signedMinutes = signedClockDifference(
                            actual = parseMinutes(actualTime),
                            official = parseMinutes(event.official(official))
                        )
                    )
                }
            }
        }

        unavailable.forEach { failure ->
            println(
                "DIYANET_BASELINE_UNAVAILABLE|city=${failure.city}|" +
                    "month=${failure.month}|reason=${failure.reason}"
            )
        }

        val metricsByKey = allErrors
            .groupBy { Triple(it.city.group, it.city.key, it.event) }
            .mapValues { (_, errors) -> BenchmarkMetrics.from(errors) }

        metricsByKey
            .toSortedMap(compareBy({ it.first.name }, { it.second }, { it.third.ordinal }))
            .forEach { (key, metrics) ->
                println(
                    "DIYANET_BASELINE|group=${key.first}|city=${key.second}|" +
                        "event=${key.third}|count=${metrics.count}|" +
                        "mae=${formatDecimal(metrics.mae)}|p95=${metrics.p95}|" +
                        "max=${metrics.maxAbsolute}|max_date=${metrics.maxDate}|" +
                        "within_2=${formatDecimal(metrics.withinTwoPercent)}"
                )
            }

        PrayerEvent.entries.forEach { event ->
            assertEquals(14 * 365, allErrors.count { it.event == event })
        }
        assertV9Guardrails(metricsByKey, unavailable)
    }

    private fun assertV9Guardrails(
        metricsByKey: Map<Triple<EvaluationGroup, String, PrayerEvent>, BenchmarkMetrics>,
        unavailable: List<UnavailableMonth>
    ) {
        assertEquals(emptySet<String>(), unavailable.map { "${it.city}:${it.month}" }.toSet())

        PANEL_CITIES.forEach { city ->
            val fajr = requireNotNull(metricsByKey[Triple(city.group, city.key, PrayerEvent.FAJR)])
            val isha = requireNotNull(metricsByKey[Triple(city.group, city.key, PrayerEvent.ISHA)])
            assertEquals(365, fajr.count)
            assertEquals(365, isha.count)

            if (city.key == "tromso") {
                assertEquals(88, fajr.maxAbsolute)
                assertEquals(16, isha.maxAbsolute)
            } else {
                require(fajr.maxAbsolute <= 7) {
                    "Fajr regression for ${city.key}: ${fajr.maxAbsolute} minutes"
                }
                require(isha.maxAbsolute <= 8) {
                    "Isha regression for ${city.key}: ${isha.maxAbsolute} minutes"
                }
            }

            val sunrise = requireNotNull(metricsByKey[Triple(city.group, city.key, PrayerEvent.SUNRISE)])
            val dhuhr = requireNotNull(metricsByKey[Triple(city.group, city.key, PrayerEvent.DHUHR)])
            val maghrib = requireNotNull(metricsByKey[Triple(city.group, city.key, PrayerEvent.MAGHRIB)])
            require(sunrise.maxAbsolute <= 15) {
                "Sunrise regression for ${city.key}: ${sunrise.maxAbsolute} minutes"
            }
            require(dhuhr.maxAbsolute <= 1) {
                "Dhuhr regression for ${city.key}: ${dhuhr.maxAbsolute} minutes"
            }
            require(maghrib.maxAbsolute <= 15) {
                "Maghrib regression for ${city.key}: ${maghrib.maxAbsolute} minutes"
            }
        }
    }

    private fun loadOfficialRows(): List<OfficialRow> {
        val stream = DiyanetOfficialPanelBenchmarkTest::class.java.classLoader!!.getResourceAsStream(
            "diyanet/official_panel_2026/official_panel_2026.csv"
        )
        assertNotNull("Official Diyanet panel fixture is missing", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map(::parseOfficialRow).toList()
        }
    }

    private fun parseOfficialRow(line: String): OfficialRow {
        val columns = line.split(',')
        require(columns.size >= 9) { "Invalid official panel row: $line" }
        return OfficialRow(
            city = columns[0],
            date = LocalDate.parse(columns[1]),
            fajr = columns[2],
            sunrise = columns[3],
            dhuhr = columns[4],
            asr = columns[5],
            maghrib = columns[6],
            isha = columns[7]
        )
    }

    private fun parseMinutes(value: String): Int {
        val parts = value.split(':')
        require(parts.size == 2) { "Invalid prayer time: $value" }
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun signedClockDifference(actual: Int, official: Int): Int {
        var difference = actual - official
        if (difference > 720) difference -= 1440
        if (difference < -720) difference += 1440
        return difference
    }

    private fun formatDecimal(value: Double): String = java.lang.String.format(
        java.util.Locale.ROOT,
        "%.2f",
        value
    )

    private fun formatAdaptive(value: java.time.ZonedDateTime): String {
        return roundForDisplay(value).toLocalTime().toString().substring(0, 5)
    }

    private data class OfficialRow(
        val city: String,
        val date: LocalDate,
        val fajr: String,
        val sunrise: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String
    )

    private data class PanelCity(
        val key: String,
        val latitude: Double,
        val longitude: Double,
        val timezone: String,
        val group: EvaluationGroup
    )

    private data class BenchmarkError(
        val city: PanelCity,
        val date: LocalDate,
        val event: PrayerEvent,
        val signedMinutes: Int
    )

    private data class UnavailableMonth(
        val city: String,
        val month: Int,
        val reason: String
    )

    private data class BenchmarkMetrics(
        val count: Int,
        val mae: Double,
        val p95: Int,
        val maxAbsolute: Int,
        val maxDate: LocalDate,
        val withinTwoPercent: Double
    ) {
        companion object {
            fun from(errors: List<BenchmarkError>): BenchmarkMetrics {
                val absolute = errors.map { abs(it.signedMinutes) }.sorted()
                val worst = errors.maxBy { abs(it.signedMinutes) }
                return BenchmarkMetrics(
                    count = errors.size,
                    mae = absolute.average(),
                    p95 = absolute[ceil(absolute.size * 0.95).toInt() - 1],
                    maxAbsolute = abs(worst.signedMinutes),
                    maxDate = worst.date,
                    withinTwoPercent = absolute.count { it <= 2 } * 100.0 / absolute.size
                )
            }
        }
    }

    private enum class EvaluationGroup {
        DISCOVERY,
        CITY_HOLDOUT,
        POLAR_HOLDOUT,
        DIRECT_CONTROL
    }

    private enum class PrayerEvent(
        val official: (OfficialRow) -> String,
        val actual: (PrayerDayEntity) -> String
    ) {
        FAJR(OfficialRow::fajr, PrayerDayEntity::fajr),
        SUNRISE(OfficialRow::sunrise, PrayerDayEntity::sunrise),
        DHUHR(OfficialRow::dhuhr, PrayerDayEntity::dhuhr),
        ASR(OfficialRow::asr, PrayerDayEntity::asr),
        MAGHRIB(OfficialRow::maghrib, PrayerDayEntity::maghrib),
        ISHA(OfficialRow::isha, PrayerDayEntity::isha)
    }

    private companion object {
        val PANEL_CITIES = listOf(
            PanelCity("stockholm", 59.3293, 18.0686, "Europe/Stockholm", EvaluationGroup.DISCOVERY),
            PanelCity("gothenburg", 57.7089, 11.9746, "Europe/Stockholm", EvaluationGroup.CITY_HOLDOUT),
            PanelCity("umea", 63.8258, 20.2630, "Europe/Stockholm", EvaluationGroup.CITY_HOLDOUT),
            PanelCity("oslo", 59.9139, 10.7522, "Europe/Oslo", EvaluationGroup.DISCOVERY),
            PanelCity("trondheim", 63.4305, 10.3951, "Europe/Oslo", EvaluationGroup.CITY_HOLDOUT),
            PanelCity("tromso", 69.6492, 18.9553, "Europe/Oslo", EvaluationGroup.POLAR_HOLDOUT),
            PanelCity("helsinki", 60.1699, 24.9384, "Europe/Helsinki", EvaluationGroup.DISCOVERY),
            PanelCity("oulu", 65.0121, 25.4651, "Europe/Helsinki", EvaluationGroup.CITY_HOLDOUT),
            PanelCity("rovaniemi", 66.5039, 25.7294, "Europe/Helsinki", EvaluationGroup.POLAR_HOLDOUT),
            PanelCity("copenhagen", 55.6761, 12.5683, "Europe/Copenhagen", EvaluationGroup.CITY_HOLDOUT),
            PanelCity("reykjavik", 64.1466, -21.9426, "Atlantic/Reykjavik", EvaluationGroup.CITY_HOLDOUT),
            PanelCity("toronto", 43.6532, -79.3832, "America/Toronto", EvaluationGroup.DIRECT_CONTROL),
            PanelCity("istanbul", 41.0082, 28.9784, "Europe/Istanbul", EvaluationGroup.DIRECT_CONTROL),
            PanelCity("sydney", -33.8688, 151.2093, "Australia/Sydney", EvaluationGroup.DIRECT_CONTROL)
        )
    }
}
