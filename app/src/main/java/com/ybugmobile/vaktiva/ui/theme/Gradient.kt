package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalTime

/**
 * Provides an enriched, modern gradient based on the current time of day.
 * Designed for glassmorphic UI with high contrast and luminous depth.
 */
fun getGradientForTime(currentTime: LocalTime, day: PrayerDay?): Brush {
    if (day == null) return Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))

    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)

    // Transitions
    val dawnStart = fajr
    val dayStart = sunrise.plusMinutes(45)
    val sunsetStart = maghrib.minusMinutes(45)

    return when {
        // Deep Night: Midnight Slate with Indigo Depth
        currentTime.isBefore(dawnStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617), // Deepest Black-Blue
                0.5f to Color(0xFF0F141E), // Original Anchor
                1.0f to Color(0xFF1C2533)  // Original Anchor
            )
        )

        // Dawn: Muted Indigo transitioning to Steel
        currentTime.isBefore(sunrise) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0F141E), // Original Anchor
                0.6f to Color(0xFF1E293B), // Intermediate Slate
                1.0f to Color(0xFF374D6B)  // Original Anchor
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
        
        // Full Day: Enriched Spectral Azure (Luminous & Deep)
        currentTime.isBefore(sunsetStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0F172A), // Deep Slate for status bar contrast
                0.3f to Color(0xFF1E3A8A), // Rich Indigo-Blue
                0.7f to Color(0xFF0284C7), // Luminous Azure
                1.0f to Color(0xFF22D3EE)  // Vibrant Cyan Glow
            )
        )

        // Sunset: Warm Orange to Crimson Rose
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF7C2D12), // Deep Burnt Orange
                0.4f to Color(0xFF9D592B), // Original Anchor
                0.8f to Color(0xFFA43653), // Original Anchor
                1.0f to Color(0xFFFDBA74)  // Sunset Glow
            )
        )

        // Evening / Dusk: Deepest Oceanic Blue with a hint of Rose
        currentTime.isBefore(isha) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF310F1A), // Original Anchor
                0.5f to Color(0xFF1E1B4B), // Indigo Depth
                1.0f to Color(0xFF161D26)  // Original Anchor
            )
        )

        // Late Night: Return to Midnight Slate
        else -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617),
                0.5f to Color(0xFF0F141E),
                1.0f to Color(0xFF1C2533)
            )
        )
    }
}
