package com.ybugmobile.waktiva.domain.usecase

import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class GetNextPrayerUseCaseTest {

    private val useCase = GetNextPrayerUseCase()

    @Test
    fun returnsSunriseBeforeSunrise() {
        val today = prayerDay(LocalDate.of(2026, 6, 1))

        val nextPrayer = useCase(
            today = today,
            tomorrow = prayerDay(LocalDate.of(2026, 6, 2)),
            now = LocalDateTime.of(today.date, LocalTime.of(5, 29))
        )

        assertPrayer(nextPrayer, PrayerType.SUNRISE, today.date, LocalTime.of(5, 30))
    }

    @Test
    fun returnsDhuhrAtSunriseBoundary() {
        val today = prayerDay(LocalDate.of(2026, 6, 1))

        val nextPrayer = useCase(
            today = today,
            tomorrow = prayerDay(LocalDate.of(2026, 6, 2)),
            now = LocalDateTime.of(today.date, LocalTime.of(5, 30))
        )

        assertPrayer(nextPrayer, PrayerType.DHUHR, today.date, LocalTime.of(13, 0))
    }

    @Test
    fun returnsDhuhrAfterSunrise() {
        val today = prayerDay(LocalDate.of(2026, 6, 1))

        val nextPrayer = useCase(
            today = today,
            tomorrow = prayerDay(LocalDate.of(2026, 6, 2)),
            now = LocalDateTime.of(today.date, LocalTime.of(5, 31))
        )

        assertPrayer(nextPrayer, PrayerType.DHUHR, today.date, LocalTime.of(13, 0))
    }

    @Test
    fun rollsToTomorrowFajrAfterIsha() {
        val today = prayerDay(LocalDate.of(2026, 6, 1))
        val tomorrow = prayerDay(LocalDate.of(2026, 6, 2), fajr = LocalTime.of(4, 12))

        val nextPrayer = useCase(
            today = today,
            tomorrow = tomorrow,
            now = LocalDateTime.of(today.date, LocalTime.of(23, 45))
        )

        assertPrayer(nextPrayer, PrayerType.FAJR, tomorrow.date, LocalTime.of(4, 12))
    }

    @Test
    fun fallsBackToNextDayUsingTodayFajrWhenTomorrowMissing() {
        val today = prayerDay(LocalDate.of(2026, 6, 1), fajr = LocalTime.of(4, 15))

        val nextPrayer = useCase(
            today = today,
            tomorrow = null,
            now = LocalDateTime.of(today.date, LocalTime.of(23, 45))
        )

        assertPrayer(nextPrayer, PrayerType.FAJR, today.date.plusDays(1), LocalTime.of(4, 15))
    }

    private fun assertPrayer(
        nextPrayer: NextPrayer?,
        type: PrayerType,
        date: LocalDate,
        time: LocalTime
    ) {
        assertNotNull(nextPrayer)
        assertEquals(type, nextPrayer?.type)
        assertEquals(date, nextPrayer?.date)
        assertEquals(time, nextPrayer?.time)
    }

    private fun prayerDay(
        date: LocalDate,
        fajr: LocalTime = LocalTime.of(4, 15)
    ): PrayerDay = PrayerDay(
        date = date,
        hijriDate = null,
        timings = mapOf(
            PrayerType.FAJR to fajr,
            PrayerType.SUNRISE to LocalTime.of(5, 30),
            PrayerType.DHUHR to LocalTime.of(13, 0),
            PrayerType.ASR to LocalTime.of(17, 15),
            PrayerType.MAGHRIB to LocalTime.of(20, 45),
            PrayerType.ISHA to LocalTime.of(22, 15)
        )
    )
}
