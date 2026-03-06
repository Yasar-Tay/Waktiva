package com.ybugmobile.waktiva.domain.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ybugmobile.waktiva.R

enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOGGY,
    RAINY,
    SNOWY,
    THUNDERSTORM,
    UNKNOWN;

    val displayName: String
        @Composable
        get() = when (this) {
            CLEAR -> stringResource(id = R.string.weather_clear)
            PARTLY_CLOUDY -> stringResource(id = R.string.weather_partly_cloudy)
            CLOUDY -> stringResource(id = R.string.weather_cloudy)
            FOGGY -> stringResource(id = R.string.weather_foggy)
            RAINY -> stringResource(id = R.string.weather_rainy)
            SNOWY -> stringResource(id = R.string.weather_snowy)
            THUNDERSTORM -> stringResource(id = R.string.weather_thunderstorm)
            UNKNOWN -> stringResource(id = R.string.weather_unknown)
        }

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
