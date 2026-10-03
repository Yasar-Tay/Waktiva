package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * What the app actually shows for method 13 against both official Diyanet panels, every city and
 * every day.
 *
 * Around midsummer above ~64°N the sun sets after midnight. V14 used to take the previous
 * evening's sunset for the day's, read a 22-hour day as a negative one and put it on the short
 * winter axis: Oulu's Fajr came out at 07:05 against Diyanet's 02:53, some 5.5 hours off, in
 * Oulu, Rovaniemi, Reykjavik and Tromso, in both years. And before the classic almanac, Asr in
 * the days around the polar night was up to two hours off (Tromso, 18 January 2026).
 */
class DiyanetProductionPanelTest {

    private val calculator = LocalPrayerCalculator()

    @Test
    fun `method 13 stays within 8 minutes of both official panels`() {
        val failures = offPanel(CITIES.keys, limitMinutes = 8)
        assertTrue(failures.take(20).joinToString("\n", "${failures.size} times off by more than 8 minutes:\n"), failures.isEmpty())
    }

    /**
     * Where no high-latitude rule applies, Diyanet's tables are its classic formula and nothing
     * else: every prayer, every day of both years, within a minute (the rounding of the last digit).
     */
    @Test
    fun `method 13 gives the tables to the minute where no high-latitude rule applies`() {
        val failures = offPanel(setOf("istanbul", "toronto", "sydney"), limitMinutes = 1)
        assertTrue(failures.take(20).joinToString("\n", "${failures.size} times off by more than a minute:\n"), failures.isEmpty())
    }

    /**
     * From 45°N into the polar regions Fajr and Isha follow the tables' summer rule
     * (DiyanetHighLatitudeTwilight): within 8 minutes every day of both years, under a minute and a
     * half on average. V14 was 1 to 2.4 minutes off on average in these cities, up to 10.
     */
    @Test
    fun `method 13 follows the summer rule from 45 degrees north into the polar regions`() {
        val cities = setOf(
            "stockholm", "gothenburg", "umea", "oslo", "trondheim", "helsinki", "oulu", "copenhagen", "reykjavik", "tromso", "rovaniemi"
        )
        val failures = offPanel(cities, limitMinutes = 8, events = setOf("fajr", "isha"))
        assertTrue(failures.take(20).joinToString("\n", "${failures.size} times off by more than 8 minutes:\n"), failures.isEmpty())
        for (year in listOf(2026, 2027)) {
            val official = loadRows(year).groupBy { it[0] }
            for (key in cities) {
                val actual = monthsOf(year, key)
                for ((event, column, value) in EVENTS.filter { it.first == "fajr" || it.first == "isha" }) {
                    val rows = official.getValue(key)
                    val mean = rows.sumOf { abs(clockDifference(value(actual.getValue(LocalDate.parse(it[1]))), it[column])) }.toDouble() / rows.size
                    assertTrue("$key $year $event is %.2f minutes off on average".format(mean), mean < 1.5)
                }
            }
        }
    }

    private fun monthsOf(year: Int, key: String): Map<LocalDate, PrayerDayEntity> {
        val city = CITIES.getValue(key)
        return (1..12)
            .flatMap { calculator.calculateMonthlyPrayerTimes(year, it, city.first, city.second, 13, zoneId = ZoneId.of(city.third)) }
            .associateBy { LocalDate.parse(it.date) }
    }

    private fun offPanel(cities: Set<String>, limitMinutes: Int, events: Set<String>? = null): List<String> {
        val failures = mutableListOf<String>()
        for (year in listOf(2026, 2027)) {
            val official = loadRows(year).groupBy { it[0] }
            for (key in cities) {
                val actual = monthsOf(year, key)
                for (row in requireNotNull(official[key]) { "$key is missing from the $year panel" }) {
                    val day = actual.getValue(LocalDate.parse(row[1]))
                    for ((event, column, value) in EVENTS.filter { events == null || it.first in events }) {
                        val off = clockDifference(value(day), row[column])
                        if (abs(off) > limitMinutes) failures += "$key ${row[1]} $event: ${value(day)} against ${row[column]}"
                    }
                }
            }
        }
        return failures
    }

    private fun loadRows(year: Int): List<List<String>> =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("diyanet/official_panel_$year/official_panel_$year.csv"))
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines -> lines.drop(1).filter { it.isNotBlank() }.map { it.split(',') }.toList() }

    private fun clockDifference(actual: String, official: String): Int {
        fun minutes(value: String) = value.substring(0, 2).toInt() * 60 + value.substring(3, 5).toInt()
        var difference = minutes(actual) - minutes(official)
        if (difference > 720) difference -= 1440
        if (difference < -720) difference += 1440
        return difference
    }

    private companion object {
        /** Each event with its column in the panel's CSV. */
        val EVENTS = listOf<Triple<String, Int, (PrayerDayEntity) -> String>>(
            Triple("fajr", 2, PrayerDayEntity::fajr),
            Triple("sunrise", 3, PrayerDayEntity::sunrise),
            Triple("dhuhr", 4, PrayerDayEntity::dhuhr),
            Triple("asr", 5, PrayerDayEntity::asr),
            Triple("maghrib", 6, PrayerDayEntity::maghrib),
            Triple("isha", 7, PrayerDayEntity::isha)
        )

        /** The panels' cities: latitude, longitude and time zone (see their city_metadata.json). */
        val CITIES = mapOf(
            "stockholm" to Triple(59.3293, 18.0686, "Europe/Stockholm"),
            "gothenburg" to Triple(57.7089, 11.9746, "Europe/Stockholm"),
            "umea" to Triple(63.8258, 20.2630, "Europe/Stockholm"),
            "oslo" to Triple(59.9139, 10.7522, "Europe/Oslo"),
            "trondheim" to Triple(63.4305, 10.3951, "Europe/Oslo"),
            "tromso" to Triple(69.6492, 18.9553, "Europe/Oslo"),
            "helsinki" to Triple(60.1699, 24.9384, "Europe/Helsinki"),
            "oulu" to Triple(65.0121, 25.4651, "Europe/Helsinki"),
            "rovaniemi" to Triple(66.5039, 25.7294, "Europe/Helsinki"),
            "copenhagen" to Triple(55.6761, 12.5683, "Europe/Copenhagen"),
            "reykjavik" to Triple(64.1466, -21.9426, "Atlantic/Reykjavik"),
            "toronto" to Triple(43.6532, -79.3832, "America/Toronto"),
            "istanbul" to Triple(41.0082, 28.9784, "Europe/Istanbul"),
            "sydney" to Triple(-33.8688, 151.2093, "Australia/Sydney")
        )
    }
}
