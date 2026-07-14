package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.DiyanetRoutingDiagnosticCode
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetAstronomyKernel
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetEngineVersions
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetNightAstronomyKernel
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetReconstructionV14
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetV14Confidence
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetV14Day
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetV14TwilightState
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetProfiles
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
import com.ybugmobile.waktiva.data.local.diyanet.roundForDisplay
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DiyanetReconstructionV14Test {

    private val calculator = DiyanetReconstructionV14()

    @Test
    fun `Arnstorf uses the sixteen degree summer anchor`() {
        val date = LocalDate.of(2026, 8, 5)
        val location = PrayerLocation(
            latitude = 48.5584,
            longitude = 12.81674,
            zoneId = ZoneId.of("Europe/Berlin")
        )

        val result = calculator.calculate(date, location)

        assertEquals(LocalTime.of(3, 50), roundForDisplay(result.fajr).toLocalTime())
        assertEquals("SIXTEEN_DEGREE_SUMMER_ANCHOR", result.diagnostics.fajrShoulderMode)
        assertEquals(DiyanetV14Confidence.CALIBRATED_NORTH_2026, result.confidence)
    }

    @Test
    fun `Iqaluit long missing season remains on the estimated branch`() {
        val date = LocalDate.of(2026, 5, 19)
        val location = PrayerLocation(
            latitude = 63.74697,
            longitude = -68.51727,
            zoneId = ZoneId.of("America/Iqaluit")
        )

        val result = calculator.calculate(date, location)

        assertEquals(LocalTime.of(2, 3), roundForDisplay(result.fajr).toLocalTime())
        assertEquals(DiyanetV14TwilightState.ESTIMATED, result.diagnostics.fajrState)
        assertTrue(result.diagnostics.fajrMissingDays >= 130)
    }

    @Test
    fun `method 13 routes eligible latitudes through V14`() {
        val day = LocalPrayerCalculator().calculateMonthlyPrayerTimes(
            year = 2026,
            month = 8,
            latitude = 48.5584,
            longitude = 12.81674,
            methodId = 13,
            zoneId = ZoneId.of("Europe/Berlin")
        )[4]

        assertEquals("03:50", day.fajr)
    }

    @Test
    fun `southern high latitudes keep V9 and emit a diagnostic`() {
        val diagnostics = mutableListOf<DiyanetRoutingDiagnosticCode>()

        LocalPrayerCalculator().calculateMonthlyPrayerTimes(
            year = 2026,
            month = 6,
            latitude = -53.1638,
            longitude = -70.9171,
            methodId = 13,
            zoneId = ZoneId.of("America/Punta_Arenas"),
            diagnosticSink = { diagnostics += it.code }
        )

        assertEquals(DiyanetEngineVersions.ADAPTIVE_V9, LocalPrayerCalculator.method13EngineVersion(-53.1638))
        assertEquals(
            DiyanetReconstructionV14.CANDIDATE_VERSION,
            LocalPrayerCalculator.method13EngineVersion(53.1638)
        )
        assertEquals(listOf(DiyanetRoutingDiagnosticCode.SOUTHERN_HEMISPHERE_V14_DISABLED), diagnostics)
    }

    @Test
    fun `full prayer ordering holds with only the documented polar-night Asr exception`() {
        data class City(val latitude: Double, val longitude: Double, val zoneId: String)

        val cities = listOf(
            City(48.5584, 12.81674, "Europe/Berlin"),
            City(59.3293, 18.0686, "Europe/Stockholm"),
            City(69.6492, 18.9553, "Europe/Oslo")
        )
        val localCalculator = LocalPrayerCalculator()

        cities.forEach { city ->
            val location = PrayerLocation(city.latitude, city.longitude, ZoneId.of(city.zoneId))
            (1..12).forEach { month ->
                val days = localCalculator.calculateMonthlyPrayerTimes(
                    year = 2026,
                    month = month,
                    latitude = city.latitude,
                    longitude = city.longitude,
                    methodId = 13,
                    zoneId = location.zoneId
                )
                days.forEachIndexed { index, day ->
                    val date = LocalDate.of(2026, month, index + 1)
                    val reconstructed = calculator.calculate(date, location)
                    assertPrayerOrder(day, reconstructed.polarNight, "$city $date")
                }
            }
        }
    }

    @Test
    fun `DST transition days preserve instant ordering and local offsets`() {
        val location = PrayerLocation(48.5584, 12.81674, ZoneId.of("Europe/Berlin"))
        val beforeSpring = calculator.calculate(LocalDate.of(2026, 3, 28), location)
        val spring = calculator.calculate(LocalDate.of(2026, 3, 29), location)
        val autumn = calculator.calculate(LocalDate.of(2026, 10, 25), location)

        assertCoreOrder(beforeSpring)
        assertCoreOrder(spring)
        assertCoreOrder(autumn)
        assertEquals(3600, beforeSpring.sunrise.offset.totalSeconds)
        assertEquals(7200, spring.sunrise.offset.totalSeconds)
        assertEquals(3600, autumn.sunrise.offset.totalSeconds)

        val springGap = Duration.between(beforeSpring.sunrise.toInstant(), spring.sunrise.toInstant()).toHours()
        assertTrue("Unexpected DST sunrise gap: $springGap hours", springGap in 22..25)
    }

    @Test
    fun `prayer-night kernel owns 23xx or 00xx Fajr roots on the physical night`() {
        val location = PrayerLocation(53.1325, 23.1688, ZoneId.of("Europe/Warsaw"))
        val profile = DiyanetProfiles.resolve(location.latitude)
        val solar = DiyanetAstronomyKernel()
        val night = DiyanetNightAstronomyKernel()
        val useFiveHourBounds = solar.usesFiveHourBounds(2026, location, profile)

        val matching = generateSequence(LocalDate.of(2026, 1, 1)) { date ->
            date.plusDays(1).takeIf { it.year == 2026 }
        }.mapNotNull { eveningDate ->
            val maghrib = solar.prayerAxis(
                eveningDate,
                location,
                profile,
                useFiveHourBounds
            ).prayerMaghrib ?: return@mapNotNull null
            val sunrise = solar.prayerAxis(
                eveningDate.plusDays(1),
                location,
                profile,
                useFiveHourBounds
            ).prayerSunrise ?: return@mapNotNull null
            val events = night.events(eveningDate, maghrib, sunrise, location, profile)
            events.takeIf { root -> root.selectedNextFajr?.hour in setOf(23, 0) }
        }.firstOrNull()

        assertNotNull("No 23:xx/00:xx Fajr root found for the 2026 Bialystok fixture", matching)
        val root = requireNotNull(matching?.selectedNextFajr)
        assertTrue(!root.toInstant().isBefore(matching.prayerMaghrib.toInstant()))
        assertTrue(!root.toInstant().isAfter(matching.nextPrayerSunrise.toInstant()))
        assertEquals(root.toLocalDate().isBefore(matching.prayerDate), matching.fajrRootBelongsToPreviousCivilDate())
    }

    @Test
    fun `year boundary uses the correct annual profile on each side`() {
        val location = PrayerLocation(59.3293, 18.0686, ZoneId.of("Europe/Stockholm"))
        val december = calculator.calculate(LocalDate.of(2026, 12, 31), location)
        val january = calculator.calculate(LocalDate.of(2027, 1, 1), location)

        assertCoreOrder(december)
        assertCoreOrder(january)
        val fajrGapHours = Duration.between(december.fajr.toInstant(), january.fajr.toInstant()).toHours()
        assertTrue("Unexpected year-boundary Fajr gap: $fajrGapHours hours", fajrGapHours in 22..26)
        assertEquals(LocalDate.of(2026, 12, 31), december.date)
        assertEquals(LocalDate.of(2027, 1, 1), january.date)
    }

    private fun assertCoreOrder(day: DiyanetV14Day) {
        assertTrue(day.fajr.toInstant().isBefore(day.sunrise.toInstant()))
        assertTrue(day.sunrise.toInstant().isBefore(day.dhuhr.toInstant()))
        assertTrue(day.dhuhr.toInstant().isBefore(day.maghrib.toInstant()))
        assertTrue(day.maghrib.toInstant().isBefore(day.isha.toInstant()))
    }

    private fun assertPrayerOrder(day: PrayerDayEntity, polarNight: Boolean, context: String) {
        val sunrise = day.sunrise.minutes()
        val dhuhr = day.dhuhr.minutes()
        val asr = day.asr.minutes()
        val maghrib = day.maghrib.minutes()
        var fajr = day.fajr.minutes()
        var isha = day.isha.minutes()
        while (fajr >= sunrise) fajr -= MINUTES_PER_DAY
        while (isha <= maghrib) isha += MINUTES_PER_DAY

        assertTrue("$context Fajr/Sunrise: ${day.fajr}/${day.sunrise}", fajr < sunrise)
        assertTrue("$context Sunrise/Dhuhr: ${day.sunrise}/${day.dhuhr}", sunrise < dhuhr)
        if (polarNight) {
            assertEquals("$context polar-night Asr", dhuhr, asr)
        } else {
            assertTrue("$context Dhuhr/Asr: ${day.dhuhr}/${day.asr}", dhuhr < asr)
        }
        assertTrue("$context Asr/Maghrib: ${day.asr}/${day.maghrib}", asr < maghrib)
        assertTrue("$context Maghrib/Isha: ${day.maghrib}/${day.isha}", maghrib < isha)
    }

    private fun String.minutes(): Int {
        val (hours, minutes) = split(':').map(String::toInt)
        return hours * 60 + minutes
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
