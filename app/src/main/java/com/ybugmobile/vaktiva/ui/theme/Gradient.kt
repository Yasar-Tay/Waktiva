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
    val dhuhur = day.timings[PrayerType.DHUHR] ?: LocalTime.of(13, 0)
    val asr = day.timings[PrayerType.ASR] ?: LocalTime.of(17, 0)
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
        currentTime.isBefore(dhuhur) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0F2A44), // Deep Steel Navy (anchors glass)
                0.3f to Color(0xFF1E5DA8), // Sunlit Azure
                0.7f to Color(0xFF68B298), // Rich Indigo-Blue
                1.0f to Color(0xFF4593E5), // Luminous Azure
            )
        )

        // High Afternoon: Crisp Azure to a Luminous Sky
        currentTime.isBefore(asr) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0F2A44), // Deep Steel Navy (anchors glass)
                0.3f to Color(0xFF1E5DA8), // Sunlit Azure
                0.65f to Color(0xFF4FA3C7), // Atmospheric Cyan
                1.0f to Color(0xFFE8D5A7)  // Soft Solar Haze (very subtle warmth)
            )
        )

        // Late Afternoon: Transitioning toward Golden Hour
        currentTime.isBefore(sunsetStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF081A2F), // Deep Space Blue
                0.35f to Color(0xFF123E7C), // Saturated Cobalt
                0.7f to Color(0xFF3F8FD2), // Luminous Sky Blue
                1.0f to Color(0xFF9FD3F6)  // Diffused Sunlight
            )
        )

        // Sunset: Burnt Orange to a radiant Sunset Glow
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF4E3D88), // Rich Indigo-Blue
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
