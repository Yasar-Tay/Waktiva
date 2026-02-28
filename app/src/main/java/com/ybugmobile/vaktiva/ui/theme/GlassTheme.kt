package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.model.WeatherCondition
import java.time.LocalTime

data class GlassTheme(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val secondaryContentColor: Color,
    val isLightMode: Boolean,
    val weatherCondition: WeatherCondition = WeatherCondition.CLEAR
)

val LocalGlassTheme = staticCompositionLocalOf<GlassTheme> {
    GlassTheme(
        containerColor = Color.Black.copy(alpha = 0.15f),
        contentColor = Color.White,
        borderColor = Color.White.copy(alpha = 0.1f),
        secondaryContentColor = Color.White.copy(alpha = 0.5f),
        isLightMode = false
    )
}

val LocalBackgroundGradient = staticCompositionLocalOf<Brush> {
    error("No BackgroundGradient provided")
}

fun getGlassTheme(
    currentTime: LocalTime,
    day: PrayerDay?,
    weatherCondition: WeatherCondition = WeatherCondition.CLEAR
): GlassTheme {
    val isLightMode = isLightGlassMode(currentTime, day)
    
    val baseTheme = if (isLightMode) {
        GlassTheme(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            borderColor = Color.White.copy(alpha = 0.15f),
            secondaryContentColor = Color.White.copy(alpha = 0.6f),
            isLightMode = true,
            weatherCondition = weatherCondition
        )
    } else {
        GlassTheme(
            containerColor = Color.Black.copy(alpha = 0.15f),
            contentColor = Color.White,
            borderColor = Color.White.copy(alpha = 0.1f),
            secondaryContentColor = Color.White.copy(alpha = 0.5f),
            isLightMode = false,
            weatherCondition = weatherCondition
        )
    }

    // Apply weather adjustments to the theme
    return baseTheme.applyWeatherAdjustments()
}

private fun GlassTheme.applyWeatherAdjustments(): GlassTheme {
    val isCloudy = weatherCondition != WeatherCondition.CLEAR && weatherCondition != WeatherCondition.UNKNOWN
    val isSevere = weatherCondition == WeatherCondition.RAINY || 
                  weatherCondition == WeatherCondition.THUNDERSTORM || 
                  weatherCondition == WeatherCondition.SNOWY

    if (!isCloudy) return this

    val desaturateAmount = if (isSevere) 0.3f else 0.15f
    val darkenAmount = if (isSevere) 0.15f else 0.05f

    return copy(
        containerColor = containerColor.desaturate(desaturateAmount).darken(darkenAmount),
        borderColor = borderColor.desaturate(desaturateAmount).darken(darkenAmount),
        secondaryContentColor = secondaryContentColor.desaturate(desaturateAmount)
    )
}

private fun isLightGlassMode(currentTime: LocalTime, day: PrayerDay?): Boolean {
    if (day == null) return true
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 0)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 0)
    val sunsetStart = maghrib.minusMinutes(45)
    return currentTime.isBefore(sunrise) || currentTime.isAfter(sunsetStart)
}

fun Color.desaturate(amount: Float): Color { 
    val r = red; val g = green; val b = blue
    val gray = (r * 0.299f + g * 0.587f + b * 0.114f) // Rec. 601 luminance
    return Color(
        red = r + (gray - r) * amount,
        green = g + (gray - g) * amount,
        blue = b + (gray - b) * amount,
        alpha = alpha
    ) 
}

fun Color.darken(amount: Float): Color { 
    return Color(
        red = red * (1f - amount),
        green = green * (1f - amount),
        blue = blue * (1f - amount),
        alpha = alpha
    )
}
