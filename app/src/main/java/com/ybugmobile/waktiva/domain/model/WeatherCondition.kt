package com.ybugmobile.waktiva.domain.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ybugmobile.waktiva.R

enum class WeatherCondition {
    CLEAR,
    MAINLY_CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOGGY,
    DRIZZLE,
    FREEZING_DRIZZLE,
    RAINY,
    HEAVY_RAIN,
    FREEZING_RAIN,
    SNOWY,
    HEAVY_SNOW,
    SNOW_GRAINS,
    RAIN_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    THUNDERSTORM_HAIL,
    UNKNOWN;

    val displayName: String
        @Composable
        get() = when (this) {
            CLEAR -> stringResource(id = R.string.weather_clear)
            MAINLY_CLEAR -> stringResource(id = R.string.weather_mainly_clear)
            PARTLY_CLOUDY -> stringResource(id = R.string.weather_partly_cloudy)
            OVERCAST -> stringResource(id = R.string.weather_overcast)
            FOGGY -> stringResource(id = R.string.weather_foggy)
            DRIZZLE -> stringResource(id = R.string.weather_drizzle)
            FREEZING_DRIZZLE -> stringResource(id = R.string.weather_freezing_drizzle)
            RAINY -> stringResource(id = R.string.weather_rainy)
            HEAVY_RAIN -> stringResource(id = R.string.weather_heavy_rain)
            FREEZING_RAIN -> stringResource(id = R.string.weather_freezing_rain)
            SNOWY -> stringResource(id = R.string.weather_snowy)
            HEAVY_SNOW -> stringResource(id = R.string.weather_heavy_snow)
            SNOW_GRAINS -> stringResource(id = R.string.weather_snow_grains)
            RAIN_SHOWERS -> stringResource(id = R.string.weather_rain_showers)
            SNOW_SHOWERS -> stringResource(id = R.string.weather_snow_showers)
            THUNDERSTORM -> stringResource(id = R.string.weather_thunderstorm)
            THUNDERSTORM_HAIL -> stringResource(id = R.string.weather_thunderstorm_hail)
            UNKNOWN -> stringResource(id = R.string.weather_unknown)
        }

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition {
            return when (code) {
                0 -> CLEAR
                1 -> MAINLY_CLEAR
                2 -> PARTLY_CLOUDY
                3 -> OVERCAST
                45, 48 -> FOGGY
                51, 53, 55 -> DRIZZLE
                56, 57 -> FREEZING_DRIZZLE
                61, 63 -> RAINY
                65 -> HEAVY_RAIN
                66, 67 -> FREEZING_RAIN
                71, 73 -> SNOWY
                75 -> HEAVY_SNOW
                77 -> SNOW_GRAINS
                80, 81, 82 -> RAIN_SHOWERS
                85, 86 -> SNOW_SHOWERS
                95 -> THUNDERSTORM
                96, 99 -> THUNDERSTORM_HAIL
                else -> UNKNOWN
            }
        }
    }
}
