package com.ybugmobile.waktiva.domain.usecase

import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class GetWidgetNextPrayerUseCaseTest {

    private val useCase = GetWidgetNextPrayerUseCase()

    @Test
    fun returnsSunriseBeforeSunrise() {
        val today = prayerDay(LocalDate.of(2026, 6, 1))
        val nextPrayer = useCase(today, null, LocalDateTime.of(today.date, LocalTime.of(5, 20)))

        assertNotNull(nextPrayer)
        assertEquals(PrayerType.SUNRISE, nextPrayer?.type)
        assertEquals(LocalTime.of(5, 30), nextPrayer?.time)
    }

    @Test
    fun returnsDhuhrAtSunriseBoundary() {
        val today = prayerDay(LocalDate.of(2026, 6, 1))
        val nextPrayer = useCase(today, null, LocalDateTime.of(today.date, LocalTime.of(5, 30)))

        assertNotNull(nextPrayer)
        assertEquals(PrayerType.DHUHR, nextPrayer?.type)
        assertEquals(LocalTime.of(13, 0), nextPrayer?.time)
    }

    @Test
    fun returnsDhuhrAfterSunrise() {
        val today = prayerDay(LocalDate.of(2026, 6, 1))
        val nextPrayer = useCase(today, null, LocalDateTime.of(today.date, LocalTime.of(5, 31)))

        assertNotNull(nextPrayer)
        assertEquals(PrayerType.DHUHR, nextPrayer?.type)
    }

    @Test
    fun rollsToTomorrowFajrAfterIsha() {
        val today = prayerDay(LocalDate.of(2026, 6, 1))
        val tomorrow = prayerDay(LocalDate.of(2026, 6, 2))
        val nextPrayer = useCase(today, tomorrow, LocalDateTime.of(today.date, LocalTime.of(23, 45)))

        assertNotNull(nextPrayer)
        assertEquals(PrayerType.FAJR, nextPrayer?.type)
        assertEquals(tomorrow.date, nextPrayer?.date)
        assertEquals(LocalTime.of(4, 15), nextPrayer?.time)
    }

    private fun prayerDay(date: LocalDate): PrayerDay = PrayerDay(
        date = date,
        hijriDate = null,
        timings = mapOf(
            PrayerType.FAJR to LocalTime.of(4, 15),
            PrayerType.SUNRISE to LocalTime.of(5, 30),
            PrayerType.DHUHR to LocalTime.of(13, 0),
            PrayerType.ASR to LocalTime.of(17, 15),
            PrayerType.MAGHRIB to LocalTime.of(20, 45),
            PrayerType.ISHA to LocalTime.of(22, 15)
        )
    )
}
