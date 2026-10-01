package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.data.local.CachedWeather
import com.ybugmobile.waktiva.domain.model.DayForecast
import com.ybugmobile.waktiva.domain.model.HourForecast
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.WeatherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class DayWidgetModelTest {

    private val today = LocalDate.of(2026, 9, 30)
    private val day = day(today)

    @Test
    fun currentPrayerIsTheLastToHaveBegun() {
        assertEquals(PrayerType.DHUHR, DayWidgetModel.currentPrayer(day, LocalTime.of(15, 0)))
        assertEquals(PrayerType.ASR, DayWidgetModel.currentPrayer(day, LocalTime.of(16, 30)))
        assertEquals(PrayerType.ISHA, DayWidgetModel.currentPrayer(day, LocalTime.of(3, 0)))
    }

    @Test
    fun anUnmarkedPrayerIsOpenWithIPrayedOffered() {
        val now = today.atTime(15, 0)
        val moment = DayWidgetModel.moment(day, now, next(PrayerType.ASR, today, 16, 10, now), emptyMap(), logEnabled = true)

        assertEquals(MomentState.OPEN, moment.state)
        assertEquals(PrayerType.DHUHR, moment.prayer)
        assertEquals(today, moment.logDate)
        assertEquals(today.atTime(16, 10), moment.endsAt)
        assertTrue(moment.canMark)
    }

    @Test
    fun theLastHalfHourIsEnding() {
        val now = today.atTime(15, 45)
        val moment = DayWidgetModel.moment(day, now, next(PrayerType.ASR, today, 16, 10, now), emptyMap(), logEnabled = true)

        assertEquals(MomentState.ENDING, moment.state)
        assertTrue(moment.canMark)
    }

    @Test
    fun aMarkedPrayerIsPrayedWithNothingToMark() {
        val now = today.atTime(15, 45)
        val prayed = mapOf(today to setOf(PrayerType.DHUHR))
        val moment = DayWidgetModel.moment(day, now, next(PrayerType.ASR, today, 16, 10, now), prayed, logEnabled = true)

        assertEquals(MomentState.PRAYED, moment.state)
        assertFalse(moment.canMark)
    }

    @Test
    fun afterSunriseItsTheWaitForDhuhr() {
        val now = today.atTime(9, 0)
        val moment = DayWidgetModel.moment(day, now, next(PrayerType.DHUHR, today, 12, 55, now), emptyMap(), logEnabled = true)

        assertEquals(MomentState.WAITING, moment.state)
        assertFalse(moment.canMark)
    }

    @Test
    fun beforeDawnTheNightsIshaIsYesterdays() {
        val now = today.atTime(3, 0)
        val next = next(PrayerType.FAJR, today, 5, 20, now)

        val open = DayWidgetModel.moment(day, now, next, emptyMap(), logEnabled = true)
        assertEquals(PrayerType.ISHA, open.prayer)
        assertEquals(today.minusDays(1), open.logDate)
        assertTrue(open.canMark)

        val prayed = DayWidgetModel.moment(day, now, next, mapOf(today.minusDays(1) to setOf(PrayerType.ISHA)), logEnabled = true)
        assertEquals(MomentState.PRAYED, prayed.state)
    }

    @Test
    fun withTheLogOffTheTimeStillCountsButNothingIsMarked() {
        val now = today.atTime(15, 45)
        val moment = DayWidgetModel.moment(day, now, next(PrayerType.ASR, today, 16, 10, now), mapOf(today to setOf(PrayerType.DHUHR)), logEnabled = false)

        assertEquals(MomentState.ENDING, moment.state)
        assertFalse(moment.canMark)
    }

    @Test
    fun timesAreTheFivePrayersWithTheCurrentOneAndThosePrayed() {
        val times = DayWidgetModel.times(day, today.atTime(15, 0), PrayerType.DHUHR, setOf(PrayerType.FAJR))

        assertEquals(listOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB, PrayerType.ISHA), times.map { it.type })
        assertTrue(times.first { it.type == PrayerType.DHUHR }.isCurrent)
        assertTrue(times.first { it.type == PrayerType.FAJR }.let { it.isPassed && it.isPrayed })
        assertFalse(times.first { it.type == PrayerType.ASR }.isPassed)
    }

    @Test
    fun freshWeatherIsTheWeatherReported() {
        val now = today.atTime(15, 0)
        val weather = DayWidgetModel.weatherNow(cache(fetchedAt = millis(now) - 30 * 60_000), now, millis(now))

        assertEquals(WeatherCondition.RAINY, weather?.condition)
        assertEquals(18.0, weather!!.temperature, 0.0)
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

    private fun next(type: PrayerType, date: LocalDate, hour: Int, minute: Int, now: LocalDateTime) =
        NextPrayer(type, LocalTime.of(hour, minute), date, Duration.between(now, date.atTime(hour, minute)))

    private fun millis(at: LocalDateTime) = at.toInstant(ZoneOffset.UTC).toEpochMilli()

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
