package com.ybugmobile.vaktiva.ui.theme

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    if (day == null) return Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))

    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val dhuhur = day.timings[PrayerType.DHUHR] ?: LocalTime.of(13, 0)
    val asr = day.timings[PrayerType.ASR] ?: LocalTime.of(17, 0)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)

    val dawnStart = fajr
    val dayStart = sunrise.plusMinutes(45)
    val sunsetStart = maghrib.minusMinutes(45)

    // Weather impact factors
    val isCloudy = weatherCondition == WeatherCondition.CLOUDY || 
                   weatherCondition == WeatherCondition.FOGGY || 
                   weatherCondition == WeatherCondition.RAINY || 
                   weatherCondition == WeatherCondition.THUNDERSTORM ||
                   weatherCondition == WeatherCondition.SNOWY ||
                   weatherCondition == WeatherCondition.PARTLY_CLOUDY
    
    val isSevere = weatherCondition == WeatherCondition.RAINY || 
                   weatherCondition == WeatherCondition.THUNDERSTORM ||
                   weatherCondition == WeatherCondition.SNOWY

    fun Color.adjustForWeather(): Color {
        return when {
            isSevere -> this.desaturate(0.4f).darken(0.3f)
            isCloudy -> this.desaturate(0.2f).darken(0.15f)
            else -> this
        }
    }

    return when {
        currentTime.isBefore(dawnStart) -> Brush.verticalGradient(listOf(Color(0xFF020617).adjustForWeather(), Color(0xFF0F141E).adjustForWeather(), Color(0xFF1E1B4B).adjustForWeather()))
        currentTime.isBefore(sunrise) -> Brush.verticalGradient(listOf(Color(0xFF020617).adjustForWeather(), Color(0xFF0A1024).adjustForWeather(), Color(0xFF1E1B4B).adjustForWeather(), Color(0xFF2B2A6F).adjustForWeather()))
        currentTime.isBefore(dayStart) -> Brush.verticalGradient(listOf(Color(0xFF1D405B).adjustForWeather(), Color(0xFF3674A4).adjustForWeather(), Color(0xFFB45309).adjustForWeather(), Color(0xFFFCD34D).adjustForWeather()))
        currentTime.isBefore(dhuhur) -> Brush.verticalGradient(listOf(Color(0xFF0F2A44).adjustForWeather(), Color(0xFF1E5DA8).adjustForWeather(), Color(0xFF68B298).adjustForWeather(), Color(0xFF4593E5).adjustForWeather()))
        currentTime.isBefore(asr) -> Brush.verticalGradient(listOf(Color(0xFF0F2A44).adjustForWeather(), Color(0xFF1E5DA8).adjustForWeather(), Color(0xFF4FA3C7).adjustForWeather(), Color(0xFFE8D5A7).adjustForWeather()))
        currentTime.isBefore(sunsetStart) -> Brush.verticalGradient(listOf(Color(0xFF081A2F).adjustForWeather(), Color(0xFF123E7C).adjustForWeather(), Color(0xFF3F8FD2).adjustForWeather(), Color(0xFF9FD3F6).adjustForWeather()))
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(listOf(Color(0xFF0B1D33).adjustForWeather(), Color(0xFF134E8A).adjustForWeather(), Color(0xFFB45309).adjustForWeather(), Color(0xFFB91C1C).adjustForWeather()))
        currentTime.isBefore(isha) -> Brush.verticalGradient(listOf(Color(0xFF020617).adjustForWeather(), Color(0xFF1E1B4B).adjustForWeather(), Color(0xFF310F1A).adjustForWeather()))
        else -> Brush.verticalGradient(listOf(Color(0xFF020617).adjustForWeather(), Color(0xFF0F141E).adjustForWeather(), Color(0xFF1C2533).adjustForWeather()))
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
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (condition == WeatherCondition.SNOWY) 6000 else 2500, easing = LinearEasing), 
            repeatMode = RepeatMode.Restart
        ),
        label = "fall"
    )
    
    // Wind/Drift animation
    val driftProgress by infiniteTransition.animateFloat(
        initialValue = -0.05f, targetValue = 0.05f,
        animationSpec = infiniteRepeatable(animation = tween(12000, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "drift"
    )

    val cloudCount = when(condition) {
        WeatherCondition.CLOUDY, WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM, WeatherCondition.SNOWY -> 12
        WeatherCondition.PARTLY_CLOUDY -> 5
        else -> 0
    }

    val cloudElements = remember(condition) { 
        List(cloudCount) { Offset(Random.nextFloat(), Random.nextFloat() * 0.3f) } 
    }
    
    val precipElements = remember(condition) {
        if (condition == WeatherCondition.CLOUDY || condition == WeatherCondition.PARTLY_CLOUDY) emptyList()
        else List(45) { Offset(Random.nextFloat(), Random.nextFloat()) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (condition == WeatherCondition.THUNDERSTORM) {
            ThunderLayer()
        }
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            
            // 1. Draw Clouds first (Top 30%)
            val cloudColor = if (isDay) {
                when(condition) {
                    WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM -> Color(0xFF4B5563) // Dark Gray
                    WeatherCondition.SNOWY -> Color(0xFFD1D5DB) // Slate Gray
                    else -> Color.White // Standard clouds
                }
            } else {
                // Night time: Clouds are very deep, almost invisible silhouettes
                Color(0xFF020617) 
            }

            val cloudAlpha = if (isDay) {
                if(condition == WeatherCondition.FOGGY) 0.12f else 0.04f
            } else {
                // Night clouds are extremely subtle
                if(condition == WeatherCondition.FOGGY) 0.08f else 0.02f
            }

            cloudElements.forEach { pos ->
                val x = ((pos.x + driftProgress) % 1f) * w
                val y = pos.y * h
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(cloudColor.copy(alpha = cloudAlpha), Color.Transparent),
                        center = Offset(x, y),
                        radius = w * 0.4f
                    ),
                    radius = w * 0.4f,
                    center = Offset(x, y)
                )
            }

            // 2. Draw Precipitation
            when (condition) {
                WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM -> {
                    precipElements.forEach { pos ->
                        val x = pos.x * w + (driftProgress * w)
                        val y = ((pos.y + fallProgress) % 1f) * h
                        drawLine(Color.White.copy(alpha = 0.15f), Offset(x, y), Offset(x - 2.dp.toPx(), y + 8.dp.toPx()), 0.8.dp.toPx(), StrokeCap.Round)
                    }
                }
                WeatherCondition.SNOWY -> {
                    precipElements.forEach { pos ->
                        val x = pos.x * w + (kotlin.math.sin(fallProgress.toDouble() * Math.PI * 2 + pos.x * 10).toFloat() * 15.dp.toPx())
                        val y = ((pos.y + fallProgress) % 1f) * h
                        drawCircle(Color.White.copy(alpha = 0.4f), (pos.x * 1.5.dp.toPx() + 0.5.dp.toPx()), Offset(x, y))
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun ThunderLayer() {
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(5000, 20000))
            repeat(Random.nextInt(1, 3)) {
                alphaAnim.animateTo(Random.nextFloat() * 0.15f + 0.05f, tween(60))
                alphaAnim.animateTo(0f, tween(Random.nextInt(200, 600)))
                delay(Random.nextLong(100, 300))
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = alphaAnim.value)))
}

@Composable
fun StarryBackgroundLayer(currentTime: LocalTime, day: PrayerDay?) {
    if (day == null) return
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)
    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    if (!(currentTime.isAfter(isha) || currentTime.isBefore(fajr))) return

    val configuration = LocalConfiguration.current
    val topCoverage = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 0.3f else 0.4f
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val twinkleAlpha by infiniteTransition.animateFloat(0.3f, 0.9f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "twinkleAlpha")

    var meteorStartPos by remember { mutableStateOf(Offset.Zero) }
    val meteorAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(15000, 30000))
            meteorStartPos = Offset(Random.nextFloat() * 0.8f + 0.1f, Random.nextFloat() * 0.2f)
            meteorAnim.snapTo(0f)
            meteorAnim.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
        }
    }

    val stars = remember(topCoverage) { List(50) { Triple(Random.nextFloat(), Random.nextFloat() * topCoverage, Random.nextFloat() * 0.5f + 0.5f) } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawCircle(Brush.radialGradient(listOf(Color(0xFF6366F1).copy(alpha = 0.08f), Color.Transparent), Offset(w * 0.7f, h * 0.2f), w * 0.6f), w * 0.6f, Offset(w * 0.7f, h * 0.2f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFA855F7).copy(alpha = 0.05f), Color.Transparent), Offset(w * 0.2f, h * 0.1f), w * 0.4f), w * 0.4f, Offset(w * 0.2f, h * 0.1f))
        stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = twinkleAlpha * (s * 0.8f)), s * 1.2.dp.toPx(), Offset(x * w, y * h)) }
        if (meteorAnim.value > 0f && meteorAnim.value < 1f) {
            val progress = meteorAnim.value
            val startX = meteorStartPos.x * w; val startY = meteorStartPos.y * h
            val currentX = startX + (progress * 200.dp.toPx()); val currentY = startY + (progress * 100.dp.toPx())
            val tailAlpha = (1f - progress).coerceAtLeast(0f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = tailAlpha * 0.6f)), Offset(startX, startY), Offset(currentX, currentY)), Offset(startX + (progress * 0.85f * 200.dp.toPx()), startY + (progress * 0.85f * 100.dp.toPx())), Offset(currentX, currentY), 1.5.dp.toPx())
        }
    }
}

@Composable
fun AtmosphericBackgroundLayer(currentTime: LocalTime, day: PrayerDay?, sunAzimuth: Float = 0f, sunAltitude: Float = 0f, compassAzimuth: Float = 0f) {
    if (day == null) return
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30); val dhuhur = day.timings[PrayerType.DHUHR] ?: LocalTime.of(13, 0); val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    if (!(currentTime.isAfter(sunrise) && currentTime.isBefore(maghrib))) return

    val isMorning = currentTime.isBefore(dhuhur)
    val infiniteTransition = rememberInfiniteTransition(label = "atmosphere")
    val bloomPulse by infiniteTransition.animateFloat(0.7f, 1.0f, infiniteRepeatable(tween(if (isMorning) 5000 else 4000, easing = LinearOutSlowInEasing), RepeatMode.Reverse), label = "bloomPulse")
    val particles = remember(isMorning) { List(if (isMorning) 15 else 25) { MutableParticle(Random.nextFloat(), Random.nextFloat(), if (isMorning) Random.nextFloat() * 1.5f + 0.5f else Random.nextFloat() * 2f + 1f, if (isMorning) Random.nextFloat() * 0.0006f + 0.0003f else Random.nextFloat() * 0.001f + 0.0005f) } }
    val step by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart), label = "step")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val relativeAzimuth = (sunAzimuth - compassAzimuth + 540) % 360 - 180
        if (sunAltitude > 0 && kotlin.math.abs(relativeAzimuth) < 90) {
            val flareX = w / 2 + (relativeAzimuth / 90f) * (w / 2); val flareY = h / 2 - (sunAltitude / 90f) * (h / 2); val flareScale = (sunAltitude / 90f).coerceAtLeast(0.5f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f * bloomPulse), Color.Transparent), Offset(flareX, flareY), w * 0.4f * flareScale), w * 0.4f * flareScale, Offset(flareX, flareY))
            drawCircle(Color.White.copy(alpha = 0.05f), 10.dp.toPx(), Offset(w - flareX, h - flareY))
        }
        particles.forEach { p ->
            val yPos = (p.y - (step * p.speed * 100)).let { if (it < 0) it + 1f else it % 1f }
            drawCircle((if (isMorning) Color(0xFFFFF9C4) else Color.White).copy(alpha = 0.15f), p.size.dp.toPx(), Offset(p.x * w, yPos * h))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, (if (isMorning) Color(0xFFFFECB3) else Color.White).copy(alpha = 0.06f)), startY = h * 0.7f, endY = h), topLeft = Offset(0f, h * 0.7f), size = Size(w, h * 0.3f))
    }
}

private class MutableParticle(val x: Float, var y: Float, val size: Float, val speed: Float)
private fun Color.desaturate(amount: Float): Color { val r = red; val g = green; val b = blue; val gray = (r + g + b) / 3f; return Color(r + (gray - r) * amount, g + (gray - g) * amount, b + (gray - b) * amount, alpha) }
private fun Color.darken(amount: Float): Color { return Color(red * (1f - amount), green * (1f - amount), blue * (1f - amount), alpha) }
