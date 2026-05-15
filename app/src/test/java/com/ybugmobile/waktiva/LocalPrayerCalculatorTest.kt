package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Converts "HH:mm" to total minutes since midnight.
 */
private fun String.toMinutes(): Int {
    val (h, m) = split(":").map { it.toInt() }
    return h * 60 + m
}

class LocalPrayerCalculatorTest {

    private val calculator = LocalPrayerCalculator()

    @Test
    fun `calculateMonthlyPrayerTimes returns correct number of days`() {
        // February 2024 (Leap year)
        val prayerDays = calculator.calculateMonthlyPrayerTimes(
            year = 2024,
            month = 2,
            latitude = 41.0082, // Istanbul
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
            latitude = 51.5074, // London
            longitude = -0.1278,
            methodId = 2,
            madhabId = 0
        )
        
        val firstDay = prayerDays.first()
        val timeRegex = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")
        
        assertTrue("Fajr format incorrect: ${firstDay.fajr}", firstDay.fajr.matches(timeRegex))
        assertTrue("Sunrise format incorrect: ${firstDay.sunrise}", firstDay.sunrise.matches(timeRegex))
        assertTrue("Isha format incorrect: ${firstDay.isha}", firstDay.isha.matches(timeRegex))
    }

    // -------------------------------------------------------------------------
    // Diyanet fraction-based algorithm tests
    //
    // Reference values from Diyanet's official published times (ezanvakti API).
    // Algorithm: Fajr = max(standard_angle, sunrise − night × k_fajr)
    //            Isha = min(standard_angle, maghrib + night × k_isha)
    // Tolerance: ±10 min (fraction constants validated across May–June 2026)
    // -------------------------------------------------------------------------

    /**
     * Amsterdam (52.4°N) – May 15 2026
     * Diyanet: Fajr 04:00, Isha 23:03
     * Fraction constants: k_fajr=0.2116, k_isha=0.1890 → exact match expected
     */
    @Test
    fun `diyanet fraction algorithm Amsterdam May 15`() {
        val lat = 52.374; val lng = 4.890
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Amsterdam May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 04:00  Isha 23:03")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 04:00",
            kotlin.math.abs(day.fajr.toMinutes() - (4*60)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 23:03",
            kotlin.math.abs(day.isha.toMinutes() - (23*60+3)) <= 10)
    }

    /**
     * Berlin (52.5°N) – May 15 2026
     * Diyanet: Fajr 03:24, Isha 22:31
     */
    @Test
    fun `diyanet fraction algorithm Berlin May 15`() {
        val lat = 52.520; val lng = 13.405
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Berlin May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 03:24  Isha 22:31")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 03:24",
            kotlin.math.abs(day.fajr.toMinutes() - (3*60+24)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 22:31",
            kotlin.math.abs(day.isha.toMinutes() - (22*60+31)) <= 10)
    }

    /**
     * Paris (48.9°N) – May 15 2026
     * Diyanet: Fajr 04:21, Isha 23:08
     */
    @Test
    fun `diyanet fraction algorithm Paris May 15`() {
        val lat = 48.857; val lng = 2.352
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Paris May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 04:21  Isha 23:08")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 04:21",
            kotlin.math.abs(day.fajr.toMinutes() - (4*60+21)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 23:08",
            kotlin.math.abs(day.isha.toMinutes() - (23*60+8)) <= 10)
    }

    /**
     * Basel (47.5°N) – May 15 2026
     * Diyanet: Fajr 03:53, Isha 22:55
     */
    @Test
    fun `diyanet fraction algorithm Basel May 15`() {
        val lat = 47.498; val lng = 7.745
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Basel May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 03:53  Isha 22:55")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 03:53",
            kotlin.math.abs(day.fajr.toMinutes() - (3*60+53)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 22:55",
            kotlin.math.abs(day.isha.toMinutes() - (22*60+55)) <= 10)
    }

    /**
     * Winter sanity check: algorithm should NOT over-correct in winter.
     * Standard MWL angle works in December — fraction method must not fire.
     * Istanbul (41°N, below 43° threshold) — fraction not applied at all.
     */
    @Test
    fun `diyanet fraction does not over-correct in winter`() {
        val lat = 52.374; val lng = 4.890  // Amsterdam
        val days = calculator.calculateMonthlyPrayerTimes(2025, 12, lat, lng, methodId = 13)
        val day21 = days[20] // Dec 21
        println("Amsterdam Dec 21 — Fajr: ${day21.fajr}  Isha: ${day21.isha}")
        // In winter, standard MWL angle gives Fajr ~06:40, Isha ~18:20
        // Fraction would give Fajr ~05:21, Isha ~19:34
        // max/min rule: standard wins → Fajr should be ~06:30–07:00, Isha ~17:30–18:30
        assertTrue("Winter Fajr ${day21.fajr} should be after 06:00 (standard angle wins)",
            day21.fajr.toMinutes() >= 6*60)
        assertTrue("Winter Isha ${day21.isha} should be before 19:00 (standard angle wins)",
            day21.isha.toMinutes() <= 19*60)
    }

    @Test
    fun `hanafi madhab changes asr time`() {
        // Istanbul coordinates
        val lat = 41.0082
        val lng = 28.9784
        
        val shafiDays = calculator.calculateMonthlyPrayerTimes(2024, 5, lat, lng, 0, 0)
        val hanafiDays = calculator.calculateMonthlyPrayerTimes(2024, 5, lat, lng, 0, 1)
        
        val shafiAsr = shafiDays.first().asr
        val hanafiAsr = hanafiDays.first().asr
        
        // In most cases, Hanafi Asr is later than Shafi Asr
        assertTrue("Hanafi Asr ($hanafiAsr) should be different/later than Shafi Asr ($shafiAsr)", hanafiAsr != shafiAsr)
    }
}
