package com.ybugmobile.waktiva.domain.model

data class WeatherInfo(
    val temperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean
)
