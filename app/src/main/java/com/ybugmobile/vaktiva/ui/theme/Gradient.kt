package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Provides an enriched, modern gradient based on the current time of day.
 * Designed for glassmorphic UI with high contrast and luminous depth.
 */
@Composable
fun dynamicTimeGradient(
    currentTime: LocalTime,
    prayerDays: List<PrayerDay>
): Brush {
    val currentDay = prayerDays.find { it.date == LocalDate.now() }
    return getGradientForTime(currentTime, currentDay)
}

fun getGradientForTime(currentTime: LocalTime, day: PrayerDay?): Brush {
    // Elegant fallback for missing data
    if (day == null) return Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFF0F172A),
            1.0f to Color(0xFF1E293B)
        )
    )

    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)

    // Dynamic Transition Windows
    val dawnStart = fajr
    val dayStart = sunrise.plusMinutes(45)
    val sunsetStart = maghrib.minusMinutes(45)

    return when {
        // Deep Night: Deepest Slate with a subtle Indigo Glow at the horizon
        currentTime.isBefore(dawnStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617), // Deepest Black-Blue
                0.6f to Color(0xFF0F141E), // Original Anchor
                1.0f to Color(0xFF1E1B4B)  // Luminous Night Glow
            )
        )

        // Dawn: Midnight Slate transitioning to an Atmospheric Steel Blue
        currentTime.isBefore(sunrise) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0F141E), // Original Anchor
                0.5f to Color(0xFF1E293B), // Intermediate Slate
                1.0f to Color(0xFF374D6B)  // Steel Blue Glow
            )
        )

        // Sunrise: Deep Blue to Golden Horizon (Enriched)
        currentTime.isBefore(dayStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF1D405B), // Original Anchor
                0.4f to Color(0xFF3674A4), // Original Anchor
                0.8f to Color(0xFFA26B19), // Original Anchor
                1.0f to Color(0xFFFCD34D)  // Golden Flare Highlight
            )
        )

        // Full Day: Enriched Spectral Azure (Maximum Luminosity)
        currentTime.isBefore(sunsetStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF172D69), // Deep Slate for status bar contrast
                0.3f to Color(0xFF1E3A8A), // Rich Indigo-Blue
                0.7f to Color(0xFF0284C7), // Luminous Azure
                1.0f to Color(0xFF22D3EE)  // Vibrant Cyan Glow
            )
        )

        // Sunset: Burnt Orange to a radiant Sunset Glow
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF1E3A8A), // Rich Indigo-Blue
                0.4f to Color(0xFF9D592B), // Original Anchor
                0.7f to Color(0xFFA43653), // Original Anchor
                1.0f to Color(0xFFFDBA74)  // Radiant Sunset Highlight
            )
        )

        // Evening / Dusk: Deep Oceanic Blue with the last Rose light at the horizon
        currentTime.isBefore(isha) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617), // Returning to Deep Night
                0.5f to Color(0xFF1E1B4B), // Indigo Depth
                1.0f to Color(0xFF310F1A)  // Deep Rose Glow (Horizon)
            )
        )

        // Late Night: Return to Midnight Slate
        else -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617),
                0.6f to Color(0xFF0F141E),
                1.0f to Color(0xFF1C2533)
            )
        )
    }
}
