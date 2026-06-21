package com.ybugmobile.waktiva.domain.model

data class WeatherInfo(
    val temperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val precipitationMillimeters: Double = 0.0,
    val cloudCoverPercent: Int = 0,
    val effectCondition: WeatherCondition = condition
)
