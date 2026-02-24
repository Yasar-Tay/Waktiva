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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.model.WeatherCondition
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Provides an enriched, modern gradient based on the current time of day and weather.
 * Designed for glassmorphic UI with high contrast and luminous depth.
 */
@Composable
fun dynamicTimeGradient(
    currentTime: LocalTime,
    prayerDays: List<PrayerDay>,
    weatherCondition: WeatherCondition = WeatherCondition.CLEAR
): Brush {
    val currentDay = prayerDays.find { it.date == LocalDate.now() }
    return getGradientForTime(currentTime, currentDay, weatherCondition)
}

fun getGradientForTime(
    currentTime: LocalTime, 
    day: PrayerDay?, 
    weatherCondition: WeatherCondition = WeatherCondition.CLEAR
): Brush {
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

    // Weather impact factors
    val isCloudy = weatherCondition == WeatherCondition.CLOUDY || 
                   weatherCondition == WeatherCondition.FOGGY || 
                   weatherCondition == WeatherCondition.RAINY || 
                   weatherCondition == WeatherCondition.THUNDERSTORM
    
    val isSevere = weatherCondition == WeatherCondition.RAINY || 
                   weatherCondition == WeatherCondition.THUNDERSTORM

    fun Color.adjustForWeather(): Color {
        return when {
            isSevere -> this.desaturate(0.4f).darken(0.3f)
            isCloudy -> this.desaturate(0.2f).darken(0.15f)
            else -> this
        }
    }

    return when {
        // Deep Night
        currentTime.isBefore(dawnStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617).adjustForWeather(),
                0.6f to Color(0xFF0F141E).adjustForWeather(),
                1.0f to Color(0xFF1E1B4B).adjustForWeather()
            )
        )

        // Dawn
        currentTime.isBefore(sunrise) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617).adjustForWeather(),
                0.45f to Color(0xFF0A1024).adjustForWeather(),
                0.75f to Color(0xFF1E1B4B).adjustForWeather(),
                1.0f to Color(0xFF2B2A6F).adjustForWeather()
            )
        )

        // Sunrise
        currentTime.isBefore(dayStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF1D405B).adjustForWeather(),
                0.4f to Color(0xFF3674A4).adjustForWeather(),
                0.8f to Color(0xFFA26B19).adjustForWeather(),
                1.0f to Color(0xFFFCD34D).adjustForWeather()
            )
        )

        // Full Day
        currentTime.isBefore(dhuhur) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0F2A44).adjustForWeather(),
                0.3f to Color(0xFF1E5DA8).adjustForWeather(),
                0.7f to Color(0xFF68B298).adjustForWeather(),
                1.0f to Color(0xFF4593E5).adjustForWeather(),
            )
        )

        // High Afternoon
        currentTime.isBefore(asr) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0F2A44).adjustForWeather(),
                0.3f to Color(0xFF1E5DA8).adjustForWeather(),
                0.65f to Color(0xFF4FA3C7).adjustForWeather(),
                1.0f to Color(0xFFE8D5A7).adjustForWeather()
            )
        )

        // Late Afternoon
        currentTime.isBefore(sunsetStart) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF081A2F).adjustForWeather(),
                0.35f to Color(0xFF123E7C).adjustForWeather(),
                0.7f to Color(0xFF3F8FD2).adjustForWeather(),
                1.0f to Color(0xFF9FD3F6).adjustForWeather()
            )
        )

        // Sunset
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0B1D33).adjustForWeather(),
                0.3f to Color(0xFF134E8A).adjustForWeather(),
                0.7f to Color(0xFFA26B19).adjustForWeather(),
                1.0f to Color(0xFFA43653).adjustForWeather()
            )
        )

        // Evening / Dusk
        currentTime.isBefore(isha) -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617).adjustForWeather(),
                0.5f to Color(0xFF1E1B4B).adjustForWeather(),
                1.0f to Color(0xFF310F1A).adjustForWeather()
            )
        )

        // Late Night
        else -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF020617).adjustForWeather(),
                0.6f to Color(0xFF0F141E).adjustForWeather(),
                1.0f to Color(0xFF1C2533).adjustForWeather()
            )
        )
    }
}

/**
 * Weather-reactive layer that renders precipitation or cloud effects.
 */
@Composable
fun WeatherBackgroundLayer(
    condition: WeatherCondition,
    isDay: Boolean
) {
    if (condition == WeatherCondition.UNKNOWN || condition == WeatherCondition.CLEAR) return

    val infiniteTransition = rememberInfiniteTransition(label = "weather")
    
    // Animation progress for precipitation
    val fallProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (condition == WeatherCondition.SNOWY) 4000 else 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fall"
    )

    // Wind/Drift animation
    val driftProgress by infiniteTransition.animateFloat(
        initialValue = -0.1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    val elements = remember(condition) {
        List(if (condition == WeatherCondition.CLOUDY) 5 else 60) {
            Offset(Random.nextFloat(), Random.nextFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (condition) {
            WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM -> {
                elements.forEach { pos ->
                    val x = pos.x * w + (driftProgress * w)
                    val y = ((pos.y + fallProgress) % 1f) * h
                    
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(x, y),
                        end = Offset(x - 5.dp.toPx(), y + 15.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            WeatherCondition.SNOWY -> {
                elements.forEach { pos ->
                    val x = pos.x * w + (kotlin.math.sin(fallProgress.toDouble() * Math.PI * 2 + pos.x).toFloat() * 20.dp.toPx())
                    val y = ((pos.y + fallProgress) % 1f) * h
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = (pos.x * 2.dp.toPx() + 1.dp.toPx()),
                        center = Offset(x, y)
                    )
                }
            }
            WeatherCondition.CLOUDY, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.FOGGY -> {
                elements.forEach { pos ->
                    val x = ((pos.x + driftProgress) % 1f) * w
                    val y = pos.y * h * 0.5f // Keep clouds in top half
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = if(condition == WeatherCondition.FOGGY) 0.15f else 0.05f), Color.Transparent),
                            center = Offset(x, y),
                            radius = w * (if(condition == WeatherCondition.FOGGY) 0.8f else 0.4f)
                        ),
                        center = Offset(x, y),
                        radius = w * (if(condition == WeatherCondition.FOGGY) 0.8f else 0.4f)
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
fun StarryBackgroundLayer(
    currentTime: LocalTime,
    day: PrayerDay?
) {
    if (day == null) return
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)
    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val isNight = currentTime.isAfter(isha) || currentTime.isBefore(fajr)
    if (!isNight) return

    val configuration = LocalConfiguration.current
    val topCoverage = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 0.3f else 0.4f
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "twinkleAlpha"
    )

    var meteorProgress by remember { mutableStateOf(0f) }
    var meteorStartPos by remember { mutableStateOf(Offset.Zero) }
    
    // Use standard animatable for better type inference in this context
    val meteorAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(15000, 30000))
            meteorStartPos = Offset(Random.nextFloat() * 0.8f + 0.1f, Random.nextFloat() * 0.2f)
            meteorAnim.snapTo(0f)
            meteorAnim.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
        }
    }
    meteorProgress = meteorAnim.value

    val stars = remember(topCoverage) { List(50) { Triple(Random.nextFloat(), Random.nextFloat() * topCoverage, Random.nextFloat() * 0.5f + 0.5f) } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawCircle(Brush.radialGradient(listOf(Color(0xFF6366F1).copy(alpha = 0.08f), Color.Transparent), Offset(w * 0.7f, h * 0.2f), w * 0.6f), radius = w * 0.6f, center = Offset(w * 0.7f, h * 0.2f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFA855F7).copy(alpha = 0.05f), Color.Transparent), Offset(w * 0.2f, h * 0.1f), w * 0.4f), radius = w * 0.4f, center = Offset(w * 0.2f, h * 0.1f))
        stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = twinkleAlpha * (s * 0.8f)), radius = s * 1.2.dp.toPx(), center = Offset(x * w, y * h)) }
        if (meteorProgress > 0f && meteorProgress < 1f) {
            val startX = meteorStartPos.x * w; val startY = meteorStartPos.y * h
            val currentX = startX + (meteorProgress * 200.dp.toPx()); val currentY = startY + (meteorProgress * 100.dp.toPx())
            val tailAlpha = (1f - meteorProgress).coerceAtLeast(0f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = tailAlpha * 0.6f)), Offset(startX, startY), Offset(currentX, currentY)), Offset(startX + (meteorProgress * 0.85f * 200.dp.toPx()), startY + (meteorProgress * 0.85f * 100.dp.toPx())), Offset(currentX, currentY), 1.5.dp.toPx())
        }
    }
}

@Composable
fun AtmosphericBackgroundLayer(
    currentTime: LocalTime,
    day: PrayerDay?,
    sunAzimuth: Float = 0f,
    sunAltitude: Float = 0f,
    compassAzimuth: Float = 0f
) {
    if (day == null) return
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val dhuhur = day.timings[PrayerType.DHUHR] ?: LocalTime.of(13, 0)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    val isDaytime = currentTime.isAfter(sunrise) && currentTime.isBefore(maghrib)
    if (!isDaytime) return

    val isMorning = currentTime.isBefore(dhuhur)
    val infiniteTransition = rememberInfiniteTransition(label = "atmosphere")
    val bloomPulse by infiniteTransition.animateFloat(0.7f, 1.0f, infiniteRepeatable(tween(if (isMorning) 5000 else 4000, easing = LinearOutSlowInEasing), RepeatMode.Reverse), label = "bloomPulse")
    
    val particles = remember(isMorning) { List(if (isMorning) 15 else 25) { MutableParticle(Random.nextFloat(), Random.nextFloat(), if (isMorning) Random.nextFloat() * 1.5f + 0.5f else Random.nextFloat() * 2f + 1f, if (isMorning) Random.nextFloat() * 0.0006f + 0.0003f else Random.nextFloat() * 0.001f + 0.0005f) } }
    val step by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart), label = "step")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        
        // Interactive Lens Flare Logic
        val relativeAzimuth = (sunAzimuth - compassAzimuth + 540) % 360 - 180
        val flareVisible = sunAltitude > 0 && kotlin.math.abs(relativeAzimuth) < 90
        if (flareVisible) {
            val flareX = w / 2 + (relativeAzimuth / 90f) * (w / 2)
            val flareY = h / 2 - (sunAltitude / 90f) * (h / 2)
            val flareScale = (sunAltitude / 90f).coerceAtLeast(0.5f)
            
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f * bloomPulse), Color.Transparent), Offset(flareX, flareY), w * 0.4f * flareScale), radius = w * 0.4f * flareScale, center = Offset(flareX, flareY))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = 10.dp.toPx(), center = Offset(w - flareX, h - flareY)) // Chromatic ghost
        }

        particles.forEach { p ->
            val yPos = (p.y - (step * p.speed * 100)).let { if (it < 0) it + 1f else it % 1f }
            drawCircle((if (isMorning) Color(0xFFFFF9C4) else Color.White).copy(alpha = 0.15f), radius = p.size.dp.toPx(), center = Offset(p.x * w, yPos * h))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, (if (isMorning) Color(0xFFFFECB3) else Color.White).copy(alpha = 0.06f)), startY = h * 0.7f, endY = h), topLeft = Offset(0f, h * 0.7f), size = Size(w, h * 0.3f))
    }
}

private class MutableParticle(val x: Float, var y: Float, val size: Float, val speed: Float)

// Extension functions for color manipulation
private fun Color.desaturate(amount: Float): Color {
    val r = this.red
    val g = this.green
    val b = this.blue
    val gray = (r + g + b) / 3f
    return Color(
        red = r + (gray - r) * amount,
        green = g + (gray - g) * amount,
        blue = b + (gray - b) * amount,
        alpha = this.alpha
    )
}

private fun Color.darken(amount: Float): Color {
    return Color(
        red = this.red * (1f - amount),
        green = this.green * (1f - amount),
        blue = this.blue * (1f - amount),
        alpha = this.alpha
    )
}
