package com.ybugmobile.waktiva.data.mapper

import com.ybugmobile.waktiva.data.remote.dto.ForecastDayDto
import com.ybugmobile.waktiva.data.remote.dto.ForecastDaySummaryDto
import com.ybugmobile.waktiva.data.remote.dto.ForecastHourDto
import com.ybugmobile.waktiva.data.remote.dto.WeatherConditionDto
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WeatherMapperTest {

    private fun hour(time: String, code: Int, precip: Double = 0.0, cloud: Int = 0, isDay: Int = 1) =
        ForecastHourDto(time, temperatureCelsius = 21.4, isDay = isDay, precipitationMillimeters = precip, cloudCoverPercent = cloud, condition = WeatherConditionDto(code))

    @Test
    fun readsTheHourFromTheLocalTime() {
        val forecast = hour("2026-09-30 16:00", 1000).toHourForecast()!!
        assertEquals(16, forecast.hour)
        assertEquals(WeatherCondition.CLEAR, forecast.condition)
        assertEquals(21.4, forecast.temperature, 0.0)
    }

    @Test
    fun rainTooLightToSeeShowsAsCloudInTheSky() {
        // Patchy rain reported, but next to none falling: the icon says rain, the sky shows cloud.
        val forecast = hour("2026-09-30 15:00", 1183, precip = 0.0, cloud = 85).toHourForecast()!!
        assertEquals(WeatherCondition.RAINY, forecast.condition)
        assertEquals(WeatherCondition.OVERCAST, forecast.effectCondition)
    }

    @Test
    fun keepsNight() {
        assertFalse(hour("2026-09-30 22:00", 1000, isDay = 0).toHourForecast()!!.isDay)
    }

    @Test
    fun skipsHoursItCannotRead() {
        assertNull(hour("2026-09-30", 1000).toHourForecast())
        assertNull(hour("2026-09-30 25:00", 1000).toHourForecast())
    }

    @Test
    fun aDayHasItsRangeAndItsHoursInOrder() {
        val day = ForecastDayDto(
            date = "2026-09-30",
            day = ForecastDaySummaryDto(maxTemperatureCelsius = 26.0, minTemperatureCelsius = 15.0),
            hours = listOf(hour("2026-09-30 13:00", 1003), hour("2026-09-30 01:00", 1000))
        ).toDayForecast()!!
        assertEquals(LocalDate.of(2026, 9, 30), day.date)
        assertEquals(15.0, day.minTemp, 0.0)
        assertEquals(listOf(1, 13), day.hours.map { it.hour })
        assertEquals(13, day.at(LocalTime.of(13, 5))?.hour)
        assertNull(day.at(LocalTime.of(9, 0)))
    }

    @Test
    fun aDayWithoutADateIsDropped() {
        assertNull(ForecastDayDto("soon", ForecastDaySummaryDto(1.0, 0.0), emptyList()).toDayForecast())
    }
}
