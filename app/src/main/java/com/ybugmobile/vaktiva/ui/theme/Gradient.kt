package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalTime

/**
 * Provides a sleek, minimalist gradient based on the current time of day.
 * Designed for modern UI with high contrast for white text.
 */
fun getGradientForTime(currentTime: LocalTime, day: PrayerDay?): Brush {
    if (day == null) return Brush.verticalGradient(listOf(Color(0xFF1C1C1C), Color(0xFF2D2D2D)))

    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)

    // Transitions
    val dawnStart = fajr
    val dayStart = sunrise.plusMinutes(45)
    val sunsetStart = maghrib.minusMinutes(45)

    return when {
        // Deep Night: Midnight Slate
        currentTime.isBefore(dawnStart) -> Brush.verticalGradient(
            listOf(Color(0xFF0F141E), Color(0xFF1C2533))
        )

        // Dawn: Muted Indigo to Soft Steel
        currentTime.isBefore(sunrise) -> Brush.verticalGradient(
            listOf(Color(0xFF0F141E), Color(0xFF374D6B))
        )

        // Dawn: Muted Indigo to Soft Steel
        currentTime.isBefore(dayStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF1D405B),
                0.5f to Color(0xFF3674A4),
                1.0f to Color(0xFFA26B19),
            )
        )

        // Midday: Sophisticated Deep Navy (Clean & Modern)
        currentTime.isBefore(sunsetStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF2580C7),
                0.5f to Color(0xFF3674A4),
                1.0f to Color(0xFF027352)
            )
        )

        // Sunset: Warm Orange to Soft Pink (45 mins before Maghrib)
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(
            listOf(Color(0xFF9D592B), Color(0xFFA43653))
        )

        // Evening / Dusk: Deepest Oceanic Blue
        currentTime.isBefore(isha) -> Brush.verticalGradient(
            listOf(Color(0xFF310F1A), Color(0xFF161D26))
        )

        // Late Night: Return to Midnight Slate
        else -> Brush.verticalGradient(
            listOf(Color(0xFF0F141E), Color(0xFF1C2533))
        )
    }
}
