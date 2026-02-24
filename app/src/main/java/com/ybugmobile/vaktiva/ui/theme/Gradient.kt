package com.ybugmobile.vaktiva.ui.theme

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

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
                0.0f to Color(0xFF020617), // Absolute Night (OLED-friendly)
                0.45f to Color(0xFF0A1024), // Deep Indigo Void
                0.75f to Color(0xFF1E1B4B), // Soft Indigo Presence
                1.0f to Color(0xFF2B2A6F)  // Faint Pre-Dawn Glow
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
                0.0f to Color(0xFF0B1D33),
                0.3f to Color(0xFF134E8A),
                0.7f to Color(0xFFA26B19), // Original Anchor
                1.0f to Color(0xFFA43653)  // Radiant Sunset Highlight
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

/**
 * A specialized star-field layer that only appears during Night/Isha.
 * Animates a twinkling effect for realism.
 */
@Composable
fun StarryBackgroundLayer(
    currentTime: LocalTime,
    day: PrayerDay?
) {
    if (day == null) return
    
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)
    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    
    // Show stars after Isha or before Fajr
    val isNight = currentTime.isAfter(isha) || currentTime.isBefore(fajr)
    if (!isNight) return

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val topCoverage = if (isPortrait) 0.3f else 0.4f

    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    // Seed the random number generator so the stars are stable across recompositions
    val stars = remember(topCoverage) {
        List(40) {
            Offset(
                x = Random.nextFloat(),
                y = Random.nextFloat() * topCoverage
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { pos ->
            val actualX = pos.x * size.width
            val actualY = pos.y * size.height
            
            drawCircle(
                color = Color.White.copy(alpha = twinkleAlpha * Random.nextFloat().coerceAtLeast(0.3f)),
                radius = 1.dp.toPx(),
                center = Offset(actualX, actualY)
            )
        }
    }
}
