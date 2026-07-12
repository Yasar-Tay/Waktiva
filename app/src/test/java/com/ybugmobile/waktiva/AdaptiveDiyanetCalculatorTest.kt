package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.diyanet.AdaptiveDiyanetCalculator
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetRegime
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
import com.ybugmobile.waktiva.data.local.diyanet.resolveDiyanetProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private fun String.testMinutes(): Int {
    val (hours, minutes) = split(":").map { it.toInt() }
    return hours * 60 + minutes
}

class AdaptiveDiyanetCalculatorTest {

    private val adaptiveCalculator = AdaptiveDiyanetCalculator()
    private val localCalculator = LocalPrayerCalculator()
    private val timeRegex = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")

    @Test
    fun `istanbul stays on direct regime`() {
        val profile = resolveDiyanetProfile(41.0082)
        val location = PrayerLocation(
            latitude = 41.0082,
            longitude = 28.9784,
            zoneId = ZoneId.of("Europe/Istanbul")
        )
        val annualProfile = adaptiveCalculator.inspectAnnualProfile(
            date = LocalDate.of(2026, 5, 15),
            location = location,
            profile = profile
        )
        val result = adaptiveCalculator.calculate(
            date = LocalDate.of(2026, 5, 15),
            location = location,
            profile = profile
        )

        assertEquals(DiyanetRegime.DIRECT_ANGLES, annualProfile.regime)
        assertNotNull(result.fajr)
        assertNotNull(result.isha)
    }

    @Test
    fun `basel uses solstice one third gradual regime`() {
        val profile = resolveDiyanetProfile(47.498)
        val location = PrayerLocation(
            latitude = 47.498,
            longitude = 7.745,
            zoneId = ZoneId.of("Europe/Zurich")
        )

        val annualProfile = adaptiveCalculator.inspectAnnualProfile(
            date = LocalDate.of(2026, 6, 15),
            location = location,
            profile = profile
        )

        assertEquals(DiyanetRegime.SOLSTICE_ONE_THIRD_GRADUAL, annualProfile.regime)
    }

    @Test
    fun `basel july 11 direct calculator matches reference window`() {
        val date = LocalDate.of(2026, 7, 11)
        val location = PrayerLocation(47.498, 7.745, ZoneId.of("Europe/Zurich"))
        val profile = resolveDiyanetProfile(location.latitude)
        val result = adaptiveCalculator.calculate(date, location, profile)

        assertEquals(DiyanetRegime.SOLSTICE_ONE_THIRD_GRADUAL, result.regime)
        assertEquals(LocalDate.of(2026, 6, 21), result.diagnostics.anchor)
        assertEquals(18.0, result.diagnostics.fajrAngle)
        assertEquals(16.0, result.diagnostics.ishaAngle)
        assertTimeNear(LocalTime.of(3, 45), result.fajr!!.toLocalTime(), 2)
        assertTimeNear(LocalTime.of(23, 4), result.isha!!.toLocalTime(), 1)

        val displayed = localCalculator.calculateMonthlyPrayerTimes(
            2026, 7, location.latitude, location.longitude, 13, zoneId = location.zoneId
        )[10]
        assertTrue(displayed.fajr.testMinutes() in (3 * 60 + 43)..(3 * 60 + 47))
        assertTrue(displayed.isha.testMinutes() in (23 * 60 + 3)..(23 * 60 + 5))
    }

    @Test
    fun `basel diyanet city coordinates match july 11 reference`() {
        val date = LocalDate.of(2026, 7, 11)
        val location = PrayerLocation(47.5596, 7.5886, ZoneId.of("Europe/Zurich"))
        val profile = resolveDiyanetProfile(location.latitude)
        val result = adaptiveCalculator.calculate(date, location, profile)

        assertTimeNear(LocalTime.of(3, 47), result.fajr!!.toLocalTime(), 1)
        assertTimeNear(LocalTime.of(23, 4), result.isha!!.toLocalTime(), 1)

        val displayed = localCalculator.calculateMonthlyPrayerTimes(
            2026, 7, location.latitude, location.longitude, 13, zoneId = location.zoneId
        )[10]
        assertTrue(displayed.fajr.testMinutes() in (3 * 60 + 46)..(3 * 60 + 48))
        assertTrue(displayed.isha.testMinutes() in (23 * 60 + 3)..(23 * 60 + 5))
    }

    @Test
    fun `basel anchor uses one fixed solstice third`() {
        val location = PrayerLocation(47.498, 7.745, ZoneId.of("Europe/Zurich"))
        val profile = resolveDiyanetProfile(location.latitude)
        val anchor = adaptiveCalculator.inspectAnnualProfile(LocalDate.of(2026, 6, 21), location, profile)
        val july = adaptiveCalculator.inspectAnnualProfile(LocalDate.of(2026, 7, 11), location, profile)

        assertEquals(anchor.anchor, july.anchor)
        assertEquals(anchor.solsticeOneThird, july.solsticeOneThird)
        assertTimeNear(LocalTime.of(3, 37), anchor.anchorEstimatedFajr!!.toLocalTime(), 2)
        assertTimeNear(LocalTime.of(23, 13), anchor.anchorEstimatedIsha!!.toLocalTime(), 2)
    }

    private fun assertTimeNear(expected: LocalTime, actual: LocalTime, toleranceMinutes: Long) {
        val difference = abs(ChronoUnit.MINUTES.between(expected, actual))
        assertTrue("Expected $expected +/- $toleranceMinutes min, got $actual", difference <= toleranceMinutes)
    }

    @Test
    fun `stockholm uses missing fajr full year regime`() {
        val profile = resolveDiyanetProfile(59.329)
        val location = PrayerLocation(
            latitude = 59.329,
            longitude = 18.069,
            zoneId = ZoneId.of("Europe/Stockholm")
        )

        val annualProfile = adaptiveCalculator.inspectAnnualProfile(
            date = LocalDate.of(2026, 6, 15),
            location = location,
            profile = profile
        )

        assertEquals(DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR, annualProfile.regime)
        assertNotNull(annualProfile.dominantMissingRun)
        assertTrue(annualProfile.dominantMissingRun!!.lengthDays >= 10)
        assertEquals(LocalDate.of(2026, 6, 15), annualProfile.anchor.minusDays(6))
    }

    @Test
    fun `annual ratios stay close to reference values`() {
        data class RatioCase(
            val latitude: Double,
            val longitude: Double,
            val zoneId: String,
            val expectedRatio: Double,
            val expectedMinimumNightMinutes: Int
        )

        val cases = listOf(
            RatioCase(59.329, 18.069, "Europe/Stockholm", 0.184684, 0),
            RatioCase(59.9139, 10.7522, "Europe/Oslo", 0.187689, 0),
            RatioCase(60.1699, 24.9384, "Europe/Helsinki", 0.176248, 300)
        )

        cases.forEach { case ->
            val profile = resolveDiyanetProfile(case.latitude)
            val location = PrayerLocation(
                latitude = case.latitude,
                longitude = case.longitude,
                zoneId = ZoneId.of(case.zoneId)
            )
            val annualProfile = adaptiveCalculator.inspectAnnualProfile(
                date = LocalDate.of(2026, 6, 15),
                location = location,
                profile = profile
            )

            assertEquals(DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR, annualProfile.regime)
            assertTrue(abs((annualProfile.summerRatio ?: 0.0) - case.expectedRatio) < 0.002)
            assertEquals(case.expectedMinimumNightMinutes, annualProfile.minimumNightMinutes)
        }
    }

    @Test
    fun `helsinki activates five hour bounded night family`() {
        val location = PrayerLocation(60.1699, 24.9384, ZoneId.of("Europe/Helsinki"))
        val profile = resolveDiyanetProfile(location.latitude)

        val annualProfile = adaptiveCalculator.inspectAnnualProfile(
            date = LocalDate.of(2026, 6, 21),
            location = location,
            profile = profile
        )

        assertEquals(DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR, annualProfile.regime)
        assertTrue(annualProfile.usesFiveHourBounds)
        assertEquals(300, annualProfile.minimumNightMinutes)
        assertEquals("five_hour_family", annualProfile.adaptiveShoulderRegime)
    }

    @Test
    fun `paris uses independent missing runs and keeps july isha continuous`() {
        val location = PrayerLocation(48.8566, 2.3522, ZoneId.of("Europe/Paris"), 35.0)
        val profile = resolveDiyanetProfile(location.latitude)
        val annualProfile = adaptiveCalculator.inspectAnnualProfile(
            date = LocalDate.of(2026, 7, 21),
            location = location,
            profile = profile
        )

        assertEquals(DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR, annualProfile.regime)
        assertEquals(
            LocalDate.of(2026, 6, 13),
            annualProfile.dominantMissingRun?.start
        )
        assertEquals(
            LocalDate.of(2026, 6, 30),
            annualProfile.dominantMissingRun?.end
        )
        assertEquals(
            LocalDate.of(2026, 5, 26),
            annualProfile.dominantMissingIshaRun?.start
        )
        assertEquals(
            LocalDate.of(2026, 7, 20),
            annualProfile.dominantMissingIshaRun?.end
        )
        assertTrue(annualProfile.delayedIshaAutumnTransition)

        val july20 = adaptiveCalculator.calculate(LocalDate.of(2026, 7, 20), location, profile)
        val july21 = adaptiveCalculator.calculate(LocalDate.of(2026, 7, 21), location, profile)
        val july20Isha = requireNotNull(july20.isha)
        val july21Isha = requireNotNull(july21.isha)

        assertTimeNear(LocalTime.of(23, 16), july20Isha.toLocalTime(), 2)
        assertTimeNear(LocalTime.of(23, 16), july21Isha.toLocalTime(), 2)
        assertTrue(
            abs(ChronoUnit.MINUTES.between(july20Isha.toLocalTime(), july21Isha.toLocalTime())) <= 2
        )
        assertEquals(LocalDate.of(2026, 5, 26), july21.diagnostics.ishaFirstMissing)
        assertEquals(LocalDate.of(2026, 7, 20), july21.diagnostics.ishaLastMissing)
        assertEquals(
            "linear_fajr_quadratic_delayed_isha_autumn",
            july21.diagnostics.transitionCurve
        )

        val officialIsha = mapOf(
            LocalDate.of(2026, 7, 20) to LocalTime.of(23, 17),
            LocalDate.of(2026, 7, 21) to LocalTime.of(23, 16),
            LocalDate.of(2026, 7, 23) to LocalTime.of(23, 15),
            LocalDate.of(2026, 7, 29) to LocalTime.of(23, 12),
            LocalDate.of(2026, 8, 5) to LocalTime.of(23, 9),
            LocalDate.of(2026, 8, 13) to LocalTime.of(23, 5),
            LocalDate.of(2026, 8, 14) to LocalTime.of(23, 2),
            LocalDate.of(2026, 8, 15) to LocalTime.of(22, 59),
            LocalDate.of(2026, 8, 16) to LocalTime.of(22, 57)
        )
        officialIsha.forEach { (date, expected) ->
            val result = requireNotNull(adaptiveCalculator.calculate(date, location, profile).isha)
            assertTimeNear(expected, result.toLocalTime(), 3)
        }

        val transitionValues = generateSequence(LocalDate.of(2026, 7, 20)) { date ->
            date.plusDays(1).takeUnless { it.isAfter(LocalDate.of(2026, 8, 16)) }
        }.map { date ->
            requireNotNull(adaptiveCalculator.calculate(date, location, profile).isha)
        }.toList()
        transitionValues.zipWithNext { previous, current ->
            assertTrue(
                "Paris Isha increased from $previous to $current",
                !current.toLocalTime().isAfter(previous.toLocalTime())
            )
        }
    }

    @Test
    fun `tromso keeps fajr and isha available through bounded polar summer axis`() {
        val location = PrayerLocation(69.6492, 18.9553, ZoneId.of("Europe/Oslo"))
        val profile = resolveDiyanetProfile(location.latitude)

        val annualProfile = adaptiveCalculator.inspectAnnualProfile(
            date = LocalDate.of(2026, 6, 21),
            location = location,
            profile = profile
        )
        val result = adaptiveCalculator.calculate(
            date = LocalDate.of(2026, 6, 21),
            location = location,
            profile = profile
        )

        assertEquals(DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR, annualProfile.regime)
        assertTrue(annualProfile.usesFiveHourBounds)
        assertEquals(300, annualProfile.minimumNightMinutes)
        assertNotNull(result.fajr)
        assertNotNull(result.isha)
        assertTrue(
            listOf(
                result.diagnostics.previousBoundPhase,
                result.diagnostics.currentBoundPhase,
                result.diagnostics.nextBoundPhase
            ).any { it != null }
        )
    }

    @Test
    fun `full year invariants hold for selected cities`() {
        data class CityCase(
            val name: String,
            val latitude: Double,
            val longitude: Double,
            val zoneId: String
        )

        val cities = listOf(
            CityCase("Istanbul", 41.0082, 28.9784, "Europe/Istanbul"),
            CityCase("Basel", 47.498, 7.745, "Europe/Zurich"),
            CityCase("Stockholm", 59.329, 18.069, "Europe/Stockholm"),
            CityCase("Oslo", 59.9139, 10.7522, "Europe/Oslo"),
            CityCase("Helsinki", 60.1699, 24.9384, "Europe/Helsinki"),
            CityCase("Toronto", 43.6532, -79.3832, "America/Toronto"),
            CityCase("Sydney", -33.8688, 151.2093, "Australia/Sydney")
        )

        cities.forEach { city ->
            val zoneId = ZoneId.of(city.zoneId)
            val fajrValues = mutableListOf<Int>()
            val ishaValues = mutableListOf<Int>()

            (1..12).forEach { month ->
                val yearMonth = YearMonth.of(2026, month)
                val days = localCalculator.calculateMonthlyPrayerTimes(
                    year = 2026,
                    month = month,
                    latitude = city.latitude,
                    longitude = city.longitude,
                    methodId = 13,
                    zoneId = zoneId
                )

                assertEquals(yearMonth.lengthOfMonth(), days.size)

                days.forEachIndexed { index, day ->
                    val expectedDate = LocalDate.of(2026, month, index + 1)
                    assertEquals(expectedDate.toString(), day.date)
                    assertTrue("$city fajr format ${day.fajr}", day.fajr.matches(timeRegex))
                    assertTrue("$city sunrise format ${day.sunrise}", day.sunrise.matches(timeRegex))
                    assertTrue("$city isha format ${day.isha}", day.isha.matches(timeRegex))
                    assertTrue("$city maghrib format ${day.maghrib}", day.maghrib.matches(timeRegex))
                    assertTrue(day.fajr.testMinutes() < day.sunrise.testMinutes())
                    val maghribMinutes = day.maghrib.testMinutes()
                    val normalizedIshaMinutes = day.isha.testMinutes().let { minutes ->
                        if (minutes <= maghribMinutes) minutes + 1440 else minutes
                    }
                    assertTrue(normalizedIshaMinutes > maghribMinutes)

                    fajrValues += day.fajr.testMinutes()
                    ishaValues += day.isha.testMinutes()
                }
            }

            consecutiveDifferencesStayReasonable(fajrValues)
            consecutiveDifferencesStayReasonable(ishaValues)
        }
    }

    private fun consecutiveDifferencesStayReasonable(values: List<Int>) {
        values.zipWithNext { previous, current ->
            val delta = abs(current - previous)
            val wrappedDelta = minOf(delta, 1440 - delta)
            assertTrue(wrappedDelta <= 120)
        }
    }
}
