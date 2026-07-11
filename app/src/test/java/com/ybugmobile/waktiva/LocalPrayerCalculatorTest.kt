package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

private fun String.toMinutes(): Int {
    val (hours, minutes) = split(":").map { it.toInt() }
    return hours * 60 + minutes
}

private fun clockDifferenceMinutes(first: String, second: String): Int {
    return ((second.toMinutes() - first.toMinutes()) % 1440 + 1440) % 1440
}

class LocalPrayerCalculatorTest {

    private val calculator = LocalPrayerCalculator()
    private val timeRegex = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")

    @Test
    fun `calculateMonthlyPrayerTimes returns correct number of days`() {
        val prayerDays = calculator.calculateMonthlyPrayerTimes(
            year = 2024,
            month = 2,
            latitude = 41.0082,
            longitude = 28.9784,
            methodId = 0,
            madhabId = 0
        )

        assertEquals(29, prayerDays.size)
    }

    @Test
    fun `prayer times are in correct format HH mm`() {
        val prayerDays = calculator.calculateMonthlyPrayerTimes(
            year = 2024,
            month = 1,
            latitude = 51.5074,
            longitude = -0.1278,
            methodId = 2,
            madhabId = 0,
            zoneId = ZoneId.of("Europe/London")
        )

        val firstDay = prayerDays.first()
        assertTrue(firstDay.fajr.matches(timeRegex))
        assertTrue(firstDay.sunrise.matches(timeRegex))
        assertTrue(firstDay.isha.matches(timeRegex))
    }

    @Test
    fun `explicit zoneId changes local output for same coordinates`() {
        val torontoZone = ZoneId.of("America/Toronto")
        val berlinZone = ZoneId.of("Europe/Berlin")
        val latitude = 43.6532
        val longitude = -79.3832

        val torontoDays = calculator.calculateMonthlyPrayerTimes(
            year = 2026,
            month = 5,
            latitude = latitude,
            longitude = longitude,
            methodId = 13,
            zoneId = torontoZone
        )
        val berlinDays = calculator.calculateMonthlyPrayerTimes(
            year = 2026,
            month = 5,
            latitude = latitude,
            longitude = longitude,
            methodId = 13,
            zoneId = berlinZone
        )

        val torontoSunrise = torontoDays[14].sunrise
        val berlinSunrise = berlinDays[14].sunrise
        assertEquals(360, clockDifferenceMinutes(torontoSunrise, berlinSunrise))
    }

    @Test
    fun `hanafi madhab changes asr time`() {
        val latitude = 41.0082
        val longitude = 28.9784
        val zoneId = ZoneId.of("Europe/Istanbul")

        val shafiDays = calculator.calculateMonthlyPrayerTimes(
            2024,
            5,
            latitude,
            longitude,
            0,
            0,
            zoneId
        )
        val hanafiDays = calculator.calculateMonthlyPrayerTimes(
            2024,
            5,
            latitude,
            longitude,
            0,
            1,
            zoneId
        )

        assertTrue(hanafiDays.first().asr != shafiDays.first().asr)
    }
}
