package com.ybugmobile.waktiva.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * One hour of a day's forecast. [condition] is the weather as reported, which the weather icons
 * show; [effectCondition] is what the sky shows for it (see [WeatherCondition.forVisualEffects]).
 */
data class HourForecast(
    val hour: Int,
    val condition: WeatherCondition,
    val effectCondition: WeatherCondition,
    val temperature: Double,
    val isDay: Boolean
)

/** A day's forecast: its range of temperatures and its hours, from midnight on. */
data class DayForecast(
    val date: LocalDate,
    val minTemp: Double,
    val maxTemp: Double,
    val hours: List<HourForecast>
) {
    /** The forecast for the hour [time] falls in, or null when that hour is missing. */
    fun at(time: LocalTime): HourForecast? = hours.firstOrNull { it.hour == time.hour }
}
