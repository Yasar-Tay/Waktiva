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
                51, 53, 55, 61, 63, 65, 80, 81, 82 -> RAINY
                71, 73, 75, 77, 85, 86 -> SNOWY
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
