package com.ybugmobile.waktiva.domain.model

import androidx.annotation.StringRes
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

    /** The weather's name, as a string resource. */
    @get:StringRes
    val nameRes: Int
        get() = when (this) {
            CLEAR -> R.string.weather_clear
            MAINLY_CLEAR -> R.string.weather_mainly_clear
            PARTLY_CLOUDY -> R.string.weather_partly_cloudy
            OVERCAST -> R.string.weather_overcast
            FOGGY -> R.string.weather_foggy
            DRIZZLE -> R.string.weather_drizzle
            FREEZING_DRIZZLE -> R.string.weather_freezing_drizzle
            RAINY -> R.string.weather_rainy
            HEAVY_RAIN -> R.string.weather_heavy_rain
            FREEZING_RAIN -> R.string.weather_freezing_rain
            SNOWY -> R.string.weather_snowy
            HEAVY_SNOW -> R.string.weather_heavy_snow
            SNOW_GRAINS -> R.string.weather_snow_grains
            RAIN_SHOWERS -> R.string.weather_rain_showers
            SNOW_SHOWERS -> R.string.weather_snow_showers
            THUNDERSTORM -> R.string.weather_thunderstorm
            THUNDERSTORM_HAIL -> R.string.weather_thunderstorm_hail
            UNKNOWN -> R.string.weather_unknown
        }

    val displayName: String
        @Composable
        get() = stringResource(id = nameRes)

    companion object {
        private const val MIN_VISIBLE_PRECIPITATION_MM = 0.05

        fun fromWeatherApiCode(code: Int): WeatherCondition {
            return when (code) {
                1000 -> CLEAR
                1003 -> MAINLY_CLEAR
                1006 -> PARTLY_CLOUDY
                1009 -> OVERCAST
                1030, 1135, 1147 -> FOGGY
                1063, 1180, 1240 -> RAIN_SHOWERS
                1066, 1210, 1216, 1255, 1258 -> SNOW_SHOWERS
                1069, 1198, 1201, 1204, 1207, 1249, 1252 -> FREEZING_RAIN
                1072, 1168, 1171 -> FREEZING_DRIZZLE
                1087, 1273, 1276 -> THUNDERSTORM
                1114 -> SNOWY
                1117, 1222, 1225 -> HEAVY_SNOW
                1150, 1153 -> DRIZZLE
                1183, 1186, 1189 -> RAINY
                1192, 1195, 1246 -> HEAVY_RAIN
                1213, 1219 -> SNOWY
                1237, 1261, 1264 -> SNOW_GRAINS
                1279, 1282 -> THUNDERSTORM_HAIL
                else -> UNKNOWN
            }
        }

        fun forVisualEffects(
            reportedCondition: WeatherCondition,
            precipitationMillimeters: Double,
            cloudCoverPercent: Int
        ): WeatherCondition {
            if (!reportedCondition.hasPrecipitationEffect()) {
                return reportedCondition
            }

            val hasVisiblePrecipitation = when (reportedCondition) {
                DRIZZLE,
                FREEZING_DRIZZLE -> precipitationMillimeters > 0.0

                else -> precipitationMillimeters >= MIN_VISIBLE_PRECIPITATION_MM
            }
            if (hasVisiblePrecipitation) return reportedCondition

            return when {
                cloudCoverPercent >= 80 -> OVERCAST
                cloudCoverPercent >= 30 -> PARTLY_CLOUDY
                cloudCoverPercent >= 10 -> MAINLY_CLEAR
                else -> CLEAR
            }
        }

        private fun WeatherCondition.hasPrecipitationEffect(): Boolean = when (this) {
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
            THUNDERSTORM_HAIL -> true
            else -> false
        }
    }
}
