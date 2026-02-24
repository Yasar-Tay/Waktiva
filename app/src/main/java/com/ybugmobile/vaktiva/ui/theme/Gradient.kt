package com.ybugmobile.vaktiva.ui.theme

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlinx.coroutines.delay

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
 * Animates twinkling stars, occasional meteors, and a deep space nebula.
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
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    // Shooting Star State
    var meteorProgress by remember { mutableStateOf(0f) }
    var meteorStartPos by remember { mutableStateOf(Offset.Zero) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(15000, 30000)) // Wait 15-30s between meteors
            meteorStartPos = Offset(Random.nextFloat() * 0.8f + 0.1f, Random.nextFloat() * 0.2f)
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(1200, easing = LinearOutSlowInEasing)
            ) { value, _ -> meteorProgress = value }
            meteorProgress = 0f
        }
    }

    // Stable star map
    val stars = remember(topCoverage) {
        List(50) {
            Triple(
                Random.nextFloat(), // x
                Random.nextFloat() * topCoverage, // y
                Random.nextFloat() * 0.5f + 0.5f // size variation
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Deep Space Nebula (Purplish glow)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF6366F1).copy(alpha = 0.08f), Color.Transparent),
                center = Offset(w * 0.7f, h * 0.2f),
                radius = w * 0.6f
            ),
            center = Offset(w * 0.7f, h * 0.2f),
            radius = w * 0.6f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFA855F7).copy(alpha = 0.05f), Color.Transparent),
                center = Offset(w * 0.2f, h * 0.1f),
                radius = w * 0.4f
            ),
            center = Offset(w * 0.2f, h * 0.1f),
            radius = w * 0.4f
        )

        // 2. Stars
        stars.forEach { (x, y, s) ->
            drawCircle(
                color = Color.White.copy(alpha = twinkleAlpha * (s * 0.8f)),
                radius = (s * 1.2.dp.toPx()),
                center = Offset(x * w, y * h)
            )
        }

        // 3. Shooting Star (Meteor)
        if (meteorProgress > 0f) {
            val startX = meteorStartPos.x * w
            val startY = meteorStartPos.y * h
            val currentX = startX + (meteorProgress * 200.dp.toPx())
            val currentY = startY + (meteorProgress * 100.dp.toPx())
            
            val tailAlpha = (1f - meteorProgress).coerceAtLeast(0f)
            
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = tailAlpha * 0.6f)),
                    start = Offset(startX, startY),
                    end = Offset(currentX, currentY)
                ),
                start = Offset(
                    startX + (meteorProgress * 0.85f * 200.dp.toPx()),
                    startY + (meteorProgress * 0.85f * 100.dp.toPx())
                ),
                end = Offset(currentX, currentY),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

/**
 * Atmospheric layer for daytime, providing sun bloom, lens flare, 
 * and floating luminous particles.
 */
@Composable
fun AtmosphericBackgroundLayer(
    currentTime: LocalTime,
    day: PrayerDay?
) {
    if (day == null) return

    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val dhuhur = day.timings[PrayerType.DHUHR] ?: LocalTime.of(13, 0)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)

    // Active between sunrise and sunset
    val isDaytime = currentTime.isAfter(sunrise) && currentTime.isBefore(maghrib)
    if (!isDaytime) return

    // Identify if we are in the "Sunrise to Noon" phase
    val isMorning = currentTime.isBefore(dhuhur)

    val infiniteTransition = rememberInfiniteTransition(label = "atmosphere")
    
    // Sun Bloom Pulse
    val bloomPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isMorning) 5000 else 4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bloom"
    )

    // Floating Particles State
    val particleCount = if (isMorning) 15 else 25
    val particles = remember(isMorning) {
        List(particleCount) {
            MutableParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = if (isMorning) Random.nextFloat() * 1.5f + 0.5f else Random.nextFloat() * 2f + 1f,
                speed = if (isMorning) Random.nextFloat() * 0.0006f + 0.0003f else Random.nextFloat() * 0.001f + 0.0005f
            )
        }
    }

    // Animate particles
    val step by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isMorning) 15000 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Dynamic Sun Bloom (Positioned based on morning progress)
        // Sun starts lower and left in morning, moves to top right by noon
        val sunProgress = if (isMorning) {
            val totalMinutes = java.time.Duration.between(sunrise, dhuhur).toMinutes().toFloat()
            val currentMinutes = java.time.Duration.between(sunrise, currentTime).toMinutes().toFloat()
            (currentMinutes / totalMinutes).coerceIn(0f, 1f)
        } else 1f

        val bloomX = w * (0.6f + (sunProgress * 0.3f))
        val bloomY = h * (0.3f - (sunProgress * 0.2f))
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isMorning) Color(0xFFFFF9C4).copy(alpha = 0.12f * bloomPulse) else Color.White.copy(alpha = 0.15f * bloomPulse),
                    Color.Transparent
                ),
                center = Offset(bloomX, bloomY),
                radius = w * (if (isMorning) 0.4f else 0.5f)
            ),
            center = Offset(bloomX, bloomY),
            radius = w * (if (isMorning) 0.4f else 0.5f)
        )

        // 2. Dynamic God Rays
        val rayAlpha = if (isMorning) 0.04f * (1f - sunProgress) else 0.03f
        if (rayAlpha > 0.005f) {
            val rayPath = Path().apply {
                moveTo(bloomX - (w * 0.2f), -50f)
                lineTo(bloomX + (w * 0.2f), -50f)
                lineTo(w * 0.2f, h * 1.2f)
                lineTo(-w * 0.1f, h * 1.2f)
                close()
            }
            drawPath(
                path = rayPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        (if (isMorning) Color(0xFFFFE082) else Color.White).copy(alpha = rayAlpha * bloomPulse), 
                        Color.Transparent
                    ),
                    start = Offset(bloomX, bloomY),
                    end = Offset(0f, h)
                )
            )
        }

        // 3. Morning Center Luminosity (Midday Sky Depth)
        if (sunProgress > 0.5f) {
            val azureAlpha = ((sunProgress - 0.5f) * 2f * 0.06f).coerceIn(0f, 0.06f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF4593E5).copy(alpha = azureAlpha), Color.Transparent),
                    center = center,
                    radius = w * 0.8f
                ),
                radius = w * 0.8f
            )
        }

        // 4. Floating Particles (Dust Motes)
        particles.forEach { p ->
            val currentY = (p.y - (step * p.speed * 100)) % 1f
            val yPos = if (currentY < 0) currentY + 1f else currentY
            
            drawCircle(
                color = (if (isMorning) Color(0xFFFFF9C4) else Color.White).copy(alpha = 0.15f),
                radius = p.size.dp.toPx(),
                center = Offset(p.x * w, yPos * h)
            )
        }

        // 5. Horizon Haze (Luminous Bottom)
        val hazeColor = if (isMorning) Color(0xFFFFECB3) else Color.White
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, hazeColor.copy(alpha = 0.06f)),
                startY = h * 0.7f,
                endY = h
            ),
            topLeft = Offset(0f, h * 0.7f),
            size = Size(w, h * 0.3f)
        )
    }
}

private class MutableParticle(
    val x: Float,
    var y: Float,
    val size: Float,
    val speed: Float
)
