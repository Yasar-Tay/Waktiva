package com.ybugmobile.vaktiva

import com.ybugmobile.vaktiva.data.local.LocalPrayerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
