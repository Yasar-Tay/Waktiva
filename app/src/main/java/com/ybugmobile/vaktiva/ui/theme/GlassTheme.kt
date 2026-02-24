package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalTime

data class GlassTheme(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val secondaryContentColor: Color,
    val isLightMode: Boolean
)

val LocalGlassTheme = staticCompositionLocalOf<GlassTheme> {
    // Default fallback theme to avoid crashes when not provided (e.g., in Previews)
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

/**
 * Pure function to calculate glass theme based on time and prayer timings.
 * Removed @Composable as it's a stateless factory function.
 */
fun getGlassTheme(
    currentTime: LocalTime,
    day: PrayerDay?
): GlassTheme {
    val isLightMode = isLightGlassMode(currentTime, day)

    return if (isLightMode) {
        // Light semi-transparent for Maghrib, Isha, Fajr (Night-time gradients)
        GlassTheme(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            borderColor = Color.White.copy(alpha = 0.15f),
            secondaryContentColor = Color.White.copy(alpha = 0.6f),
            isLightMode = true
        )
    } else {
        // Dark semi-transparent for Sunrise, Dhuhur, Asr (Day-time gradients)
        GlassTheme(
            containerColor = Color.Black.copy(alpha = 0.15f),
            contentColor = Color.White,
            borderColor = Color.White.copy(alpha = 0.1f),
            secondaryContentColor = Color.White.copy(alpha = 0.5f),
            isLightMode = false
        )
    }
}

private fun isLightGlassMode(currentTime: LocalTime, day: PrayerDay?): Boolean {
    if (day == null) return true // Default to light glass for night/undefined

    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 0)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 0)
    val sunsetStart = maghrib.minusMinutes(45)

    // Dark mode (Dark glass) during daylight: between sunrise and maghrib
    // Light mode (Light glass) during night: before sunrise or after maghrib
    return currentTime.isBefore(sunrise) || currentTime.isAfter(sunsetStart)
}
