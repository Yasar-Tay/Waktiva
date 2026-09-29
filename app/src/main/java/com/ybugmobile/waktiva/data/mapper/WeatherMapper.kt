package com.ybugmobile.waktiva.data.mapper

import com.ybugmobile.waktiva.data.remote.dto.ForecastDayDto
import com.ybugmobile.waktiva.data.remote.dto.ForecastHourDto
import com.ybugmobile.waktiva.domain.model.DayForecast
import com.ybugmobile.waktiva.domain.model.HourForecast
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import java.time.LocalDate

/** A forecast day, its hours in order; null when its date can't be read. */
fun ForecastDayDto.toDayForecast(): DayForecast? {
    val date = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return DayForecast(
        date = date,
        minTemp = day.minTemperatureCelsius,
        maxTemp = day.maxTemperatureCelsius,
        hours = hours.mapNotNull { it.toHourForecast() }.sortedBy { it.hour }
    )
}

/** An hour, with the sky's condition decided by the same rule as the current weather's. */
fun ForecastHourDto.toHourForecast(): HourForecast? {
    // "yyyy-MM-dd HH:mm": the hour is the two digits after the space.
    val hour = time.substringAfter(' ', "").substringBefore(':').toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val reported = WeatherCondition.fromWeatherApiCode(condition.code)
    return HourForecast(
        hour = hour,
        condition = reported,
        effectCondition = WeatherCondition.forVisualEffects(reported, precipitationMillimeters, cloudCoverPercent),
        temperature = temperatureCelsius,
        isDay = isDay == 1
    )
}
