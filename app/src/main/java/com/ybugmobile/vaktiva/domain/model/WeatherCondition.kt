package com.ybugmobile.vaktiva.domain.model

enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOGGY,
    RAINY,
    SNOWY,
    THUNDERSTORM,
    UNKNOWN;

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition {
            return when (code) {
                0 -> CLEAR
                1, 2 -> PARTLY_CLOUDY
                3 -> CLOUDY
                45, 48 -> FOGGY
                // Drizzle, Rain, Freezing Rain, and Rain Showers
                51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> RAINY
                // Snow fall, Snow grains, and Snow showers
                71, 73, 75, 77, 85, 86 -> SNOWY
                // Thunderstorms with or without hail
                95, 96, 99 -> THUNDERSTORM
                else -> UNKNOWN
            }
        }
    }
}

data class WeatherInfo(
    val temperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean
)
