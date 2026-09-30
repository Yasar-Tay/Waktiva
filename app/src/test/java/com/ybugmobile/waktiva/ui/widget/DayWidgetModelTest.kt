package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.data.local.CachedWeather
import com.ybugmobile.waktiva.domain.model.DayForecast
import com.ybugmobile.waktiva.domain.model.HourForecast
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.WeatherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class DayWidgetModelTest {

    private val today = LocalDate.of(2026, 9, 30)
    private val day = day(today)
    private val nextDay = day(today.plusDays(1))

    @Test
    fun currentPrayerIsTheLastToHaveBegun() {
        assertEquals(PrayerType.DHUHR, DayWidgetModel.currentPrayer(day, LocalTime.of(15, 0)))
        assertEquals(PrayerType.ASR, DayWidgetModel.currentPrayer(day, LocalTime.of(16, 30)))
    }

    @Test
    fun currentPrayerBeforeFajrIsTheNightsIsha() {
        assertEquals(PrayerType.ISHA, DayWidgetModel.currentPrayer(day, LocalTime.of(3, 0)))
    }

    @Test
    fun cellsFollowThePrayerLog() {
        val cells = DayWidgetModel.cells(
            day, nextDay, LoggedPrayers,
            prayed = setOf(PrayerType.FAJR),
            now = today.atTime(15, 0),
            logStart = today.minusDays(3),
            logEnabled = true
        ).associateBy { it.type }

        assertEquals(PrayerLogStatus.PRAYED, cells.getValue(PrayerType.FAJR).status)
        assertEquals(PrayerLogStatus.ACTIVE, cells.getValue(PrayerType.DHUHR).status)
        assertEquals(PrayerLogStatus.UPCOMING, cells.getValue(PrayerType.ASR).status)
        assertTrue(cells.getValue(PrayerType.DHUHR).isCurrent)
        assertTrue(cells.getValue(PrayerType.FAJR).canMark)
        assertTrue(cells.getValue(PrayerType.DHUHR).canMark)
        assertFalse(cells.getValue(PrayerType.ASR).canMark)
    }

    @Test
    fun anUnmarkedPrayerWhoseTimeWentIsMissedAndCanStillBeMarked() {
        val fajr = DayWidgetModel.cells(day, nextDay, LoggedPrayers, emptySet(), today.atTime(15, 0), today.minusDays(3), true)
            .first { it.type == PrayerType.FAJR }

        assertEquals(PrayerLogStatus.MISSED, fajr.status)
        assertTrue(fajr.isPassed)
        assertTrue(fajr.canMark)
    }

    @Test
    fun sunriseAndEveryTimeWithTheLogOffCarryNoStatus() {
        val withLog = DayWidgetModel.cells(day, nextDay, PrayerType.entries, emptySet(), today.atTime(15, 0), null, true)
        assertNull(withLog.first { it.type == PrayerType.SUNRISE }.status)
        assertFalse(withLog.first { it.type == PrayerType.SUNRISE }.canMark)

        val withoutLog = DayWidgetModel.cells(day, nextDay, PrayerType.entries, setOf(PrayerType.FAJR), today.atTime(15, 0), null, false)
        assertTrue(withoutLog.all { it.status == null && !it.canMark })
    }

    @Test
    fun freshWeatherIsTheWeatherReported() {
        val now = today.atTime(15, 0)
        val weather = DayWidgetModel.weatherNow(cache(fetchedAt = millis(now) - 30 * 60_000), now, millis(now))

        assertEquals(WeatherCondition.RAINY, weather?.condition)
        assertEquals(18.0, weather!!.temperature, 0.0)
        assertEquals(today, weather.forecast?.date)
    }

    @Test
    fun oldWeatherFallsBackToTheForecastForTheHour() {
        val now = today.atTime(15, 0)
        val weather = DayWidgetModel.weatherNow(cache(fetchedAt = millis(now) - 5 * 60 * 60_000), now, millis(now))

        assertEquals(WeatherCondition.PARTLY_CLOUDY, weather?.condition)
        assertEquals(15.0, weather!!.temperature, 0.0)
    }

    @Test
    fun weatherFromAnotherDayIsNone() {
        val now = today.plusDays(3).atTime(15, 0)
        assertNull(DayWidgetModel.weatherNow(cache(fetchedAt = millis(now) - 3 * 24 * 60 * 60_000L), now, millis(now)))
        assertNull(DayWidgetModel.weatherNow(null, now, millis(now)))
    }

    private fun millis(at: java.time.LocalDateTime) = at.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun cache(fetchedAt: Long) = CachedWeather(
        WeatherInfo(
            temperature = 18.0,
            condition = WeatherCondition.RAINY,
            isDay = true,
            forecast = listOf(
                DayForecast(
                    date = today,
                    minTemp = 12.0,
                    maxTemp = 21.0,
                    hours = (0 until 24).map { hour ->
                        HourForecast(hour, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY, hour.toDouble(), hour in 7..18)
                    }
                )
            )
        ),
        fetchedAt
    )

    private fun day(date: LocalDate) = PrayerDay(
        date = date,
        hijriDate = null,
        timings = mapOf(
            PrayerType.FAJR to LocalTime.of(5, 20),
            PrayerType.SUNRISE to LocalTime.of(6, 45),
            PrayerType.DHUHR to LocalTime.of(12, 55),
            PrayerType.ASR to LocalTime.of(16, 10),
            PrayerType.MAGHRIB to LocalTime.of(18, 50),
            PrayerType.ISHA to LocalTime.of(20, 10)
        )
    )
}
