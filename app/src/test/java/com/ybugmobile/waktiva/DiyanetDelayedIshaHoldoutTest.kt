package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.diyanet.AdaptiveDiyanetCalculator
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetProfiles
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetRegime
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
import com.ybugmobile.waktiva.data.local.diyanet.roundForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class DiyanetDelayedIshaHoldoutTest {

    private val calculator = AdaptiveDiyanetCalculator()

    @Test
    fun `criteria and annual regime are selected independently`() {
        val toronto = city("toronto")
        val basel = City(
            "basel",
            PrayerLocation(47.498, 7.745, ZoneId.of("Europe/Zurich")),
            Group.CONTROL
        )
        val torontoProfile = DiyanetProfiles.resolve(toronto.location.latitude)
        val baselProfile = DiyanetProfiles.resolve(basel.location.latitude)
        val torontoAnnual = calculator.inspectAnnualProfile(
            LocalDate.of(2026, 6, 21),
            toronto.location,
            torontoProfile
        )
        val baselAnnual = calculator.inspectAnnualProfile(
            LocalDate.of(2026, 6, 21),
            basel.location,
            baselProfile
        )

        assertEquals(torontoProfile.fajrAngle, baselProfile.fajrAngle, 0.0)
        assertEquals(torontoProfile.ishaAngle, baselProfile.ishaAngle, 0.0)
        assertEquals(DiyanetRegime.DIRECT_ANGLES, torontoAnnual.regime)
        assertEquals(DiyanetRegime.SOLSTICE_ONE_THIRD_GRADUAL, baselAnnual.regime)
        assertNull(torontoAnnual.dominantMissingRun)
    }

    @Test
    fun `delayed Isha rule passes blind holdouts and stays isolated from controls`() {
        val rows = loadRows()

        CITIES.filter { it.group == Group.HOLDOUT }.forEach { city ->
            val annual = annualProfile(city)
            assertEquals(DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR, annual.regime)
            assertTrue("${city.key} should use delayed Isha", annual.delayedIshaAutumnTransition)
        }
        CITIES.filter { it.group == Group.CONTROL && it.key != "toronto" }.forEach { city ->
            assertFalse("${city.key} should keep standard Isha", annualProfile(city).delayedIshaAutumnTransition)
        }

        CITIES.filter { city -> rows.any { it.city == city.key } }.forEach { city ->
            val errors = rows.filter { it.city == city.key }.map { row ->
                val actual = requireNotNull(
                    calculator.calculate(
                        row.date,
                        city.location,
                        DiyanetProfiles.resolve(city.location.latitude)
                    ).isha
                )
                val actualClock = format(actual)
                Error(
                    date = row.date,
                    actual = actualClock,
                    official = row.isha,
                    signedMinutes = clockMinutes(actualClock) - clockMinutes(row.isha)
                )
            }
            val mae = errors.map { abs(it.signedMinutes) }.average()
            val worst = errors.maxByOrNull { abs(it.signedMinutes) } ?: error("Missing rows for ${city.key}")
            val max = abs(worst.signedMinutes)
            println(
                "DIYANET_DELAYED_ISHA|city=${city.key}|group=${city.group}|" +
                    "delayed=${annualProfile(city).delayedIshaAutumnTransition}|mae=$mae|max=$max|" +
                    "max_date=${worst.date}|actual=${worst.actual}|official=${worst.official}|" +
                    "signed=${worst.signedMinutes}"
            )

            val maxAllowed = when {
                city.group == Group.HOLDOUT -> 4
                city.key == "berlin" -> 3
                else -> 10
            }
            val maeAllowed = when {
                city.group == Group.HOLDOUT -> 1.5
                city.key == "berlin" -> 1.25
                else -> 3.5
            }
            assertTrue("${city.key} Isha MAE $mae", mae <= maeAllowed)
            assertTrue("${city.key} Isha max error $max", max <= maxAllowed)
        }
    }

    private fun annualProfile(city: City) = calculator.inspectAnnualProfile(
        LocalDate.of(2026, 7, 21),
        city.location,
        DiyanetProfiles.resolve(city.location.latitude)
    )

    private fun loadRows(): List<Row> {
        val stream = requireNotNull(javaClass.classLoader!!.getResourceAsStream("diyanet/delayed_isha_holdout_2026.csv"))
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val (city, date, isha) = line.split(',')
                Row(city, LocalDate.parse(date), isha)
            }.toList()
        }
    }

    private fun city(key: String): City = CITIES.first { it.key == key }

    private fun format(value: java.time.ZonedDateTime): String =
        roundForDisplay(value).toLocalTime().toString().substring(0, 5)

    private fun clockMinutes(value: String): Int {
        val (hour, minute) = value.split(':').map(String::toInt)
        return hour * 60 + minute
    }

    private data class Row(val city: String, val date: LocalDate, val isha: String)
    private data class Error(
        val date: LocalDate,
        val actual: String,
        val official: String,
        val signedMinutes: Int
    )
    private data class City(val key: String, val location: PrayerLocation, val group: Group)
    private enum class Group { HOLDOUT, CONTROL }

    private companion object {
        val CITIES = listOf(
            City("amsterdam", PrayerLocation(52.3676, 4.9041, ZoneId.of("Europe/Amsterdam")), Group.HOLDOUT),
            City("brussels", PrayerLocation(50.8503, 4.3517, ZoneId.of("Europe/Brussels")), Group.HOLDOUT),
            City("berlin", PrayerLocation(52.5200, 13.4050, ZoneId.of("Europe/Berlin")), Group.CONTROL),
            City("london", PrayerLocation(51.5074, -0.1278, ZoneId.of("Europe/London")), Group.CONTROL),
            City("toronto", PrayerLocation(43.6532, -79.3832, ZoneId.of("America/Toronto")), Group.CONTROL)
        )
    }
}
