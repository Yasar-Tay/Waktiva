package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalTime

data class GlassTheme(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val secondaryContentColor: Color
)

@Composable
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
            secondaryContentColor = Color.White.copy(alpha = 0.6f)
        )
    } else {
        // Dark semi-transparent for Sunrise, Dhuhur, Asr (Day-time gradients)
        GlassTheme(
            containerColor = Color.Black.copy(alpha = 0.25f),
            contentColor = Color.White,
            borderColor = Color.White.copy(alpha = 0.1f),
            secondaryContentColor = Color.White.copy(alpha = 0.5f)
        )
    }
}

private fun isLightGlassMode(currentTime: LocalTime, day: PrayerDay?): Boolean {
    if (day == null) return true // Default to light glass for night/undefined

    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 0)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 0)

    // Dark mode (Dark glass) during daylight: between sunrise and maghrib
    // Light mode (Light glass) during night: before sunrise or after maghrib
    return currentTime.isBefore(sunrise) || currentTime.isAfter(maghrib)
}
