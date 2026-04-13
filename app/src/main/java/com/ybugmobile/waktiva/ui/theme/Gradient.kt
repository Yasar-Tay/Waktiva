package com.ybugmobile.waktiva.ui.theme

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlinx.coroutines.delay

// --- Weather Logic Extensions ---

private val WeatherCondition.isCloudy: Boolean
    get() = this != WeatherCondition.CLEAR && this != WeatherCondition.UNKNOWN

private val WeatherCondition.isSevere: Boolean
    get() = this == WeatherCondition.RAINY || 
            this == WeatherCondition.THUNDERSTORM || 
            this == WeatherCondition.SNOWY

private val WeatherCondition.cloudCount: Int
    get() = when (this) {
        WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM, WeatherCondition.SNOWY -> 20
        WeatherCondition.OVERCAST, WeatherCondition.FOGGY -> 12
        WeatherCondition.PARTLY_CLOUDY -> 5
        else -> 0
    }

private fun WeatherCondition.getCloudColor(isDay: Boolean): Color {
    if (!isDay) return Color(0xFF020617)
    return when (this) {
        WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM, WeatherCondition.SNOWY -> Color(0xFF4B5563)
        else -> Color.White
    }
}

private fun WeatherCondition.getCloudAlpha(isDay: Boolean): Float {
    return when {
        isDay && this == WeatherCondition.FOGGY -> 0.12f
        isDay && isSevere -> 0.15f
        isDay -> 0.04f
        !isDay && this == WeatherCondition.FOGGY -> 0.08f
        !isDay && isSevere -> 0.06f
        else -> 0.02f
    }
}

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

fun getGradientColorsForTime(
    currentTime: LocalTime,
    day: PrayerDay?,
    weatherCondition: WeatherCondition = WeatherCondition.CLEAR
): List<Color> {
    if (day == null) return listOf(Color(0xFF0F172A), Color(0xFF1E293B))

    val timings = day.timings
    val fajr = timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val sunrise = timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val dhuhur = timings[PrayerType.DHUHR] ?: LocalTime.of(13, 0)
    val asr = timings[PrayerType.ASR] ?: LocalTime.of(17, 0)
    val maghrib = timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    val isha = timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)

    val dawnStart = fajr
    val dayStart = sunrise.plusMinutes(45)
    val sunsetStart = maghrib.minusMinutes(45)

    fun Color.adjustForWeather(): Color {
        return when {
            weatherCondition.isSevere -> this.desaturate(0.3f).darken(0.2f)
            weatherCondition.isCloudy -> this.desaturate(0.2f).darken(0.1f)
            else -> this
        }
    }

    val colors = when {
        currentTime.isBefore(dawnStart) -> listOf(
            Color(0xFF020617), 
            Color(0xFF0F141E), 
            Color(0xFF1E1B4B)
        )
        currentTime.isBefore(sunrise) -> listOf(
            Color(0xFF020617), 
            Color(0xFF0A1024), 
            Color(0xFF1E1B4B), 
            Color(0xFF2B2A6F)
        )
        currentTime.isBefore(dayStart) -> listOf(
            Color(0xFF2773CE),
            Color(0xFF1F5588),
            Color(0xFFFA930C),
            Color(0xFFE8D5A7),
        )
        currentTime.isBefore(dhuhur) -> listOf(
            Color(0xFF1E5DA8),
            Color(0xFF4FA3C7),
            Color(0xFFE8D5A7)
        )
        currentTime.isBefore(asr) -> listOf(
            Color(0xFF1E5DA8),
            Color(0xFF68B298),
            Color(0xFF4593E5)
        )
        currentTime.isBefore(sunsetStart) -> listOf(
            Color(0xFF123E7C), 
            Color(0xFF3F8FD2), 
            Color(0xFF9FD3F6)
        )
        currentTime.isBefore(maghrib) -> listOf(
            Color(0xFF050E36),
            Color(0xFF123E7C),
            Color(0xFF8D3E0D),
            Color(0xFF310F1A),
        )
        currentTime.isBefore(isha) -> listOf(
            Color(0xFF020617), 
            Color(0xFF1E1B4B), 
            Color(0xFF310F1A)
        )
        else -> listOf(
            Color(0xFF020617), 
            Color(0xFF0F141E), 
            Color(0xFF1C2533)
        )
    }

    return colors.map { it.adjustForWeather() }
}

fun getGradientForTime(
    currentTime: LocalTime, 
    day: PrayerDay?, 
    weatherCondition: WeatherCondition = WeatherCondition.CLEAR
): Brush {
    return Brush.verticalGradient(getGradientColorsForTime(currentTime, day, weatherCondition))
}

@Composable
fun WeatherBackgroundLayer(condition: WeatherCondition, isDay: Boolean) {
    if (condition == WeatherCondition.UNKNOWN || condition == WeatherCondition.CLEAR) return

    val infiniteTransition = rememberInfiniteTransition(label = "weather")
    
    val fallProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (condition == WeatherCondition.SNOWY) 6000 else 2500, easing = LinearEasing), 
            repeatMode = RepeatMode.Restart
        ),
        label = "fall"
    )
    
    val driftProgress by infiniteTransition.animateFloat(
        initialValue = -0.05f, targetValue = 0.05f,
        animationSpec = infiniteRepeatable(animation = tween(12000, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "drift"
    )

    val cloudElements = remember(condition) {
        List(condition.cloudCount) { Offset(Random.nextFloat(), Random.nextFloat() * 0.3f) }
    }
    
    val precipElements = remember(condition) {
        val count = when (condition) {
            WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM -> 120 
            WeatherCondition.SNOWY -> 50 
            else -> 0
        }
        if (count == 0) emptyList()
        else List(count) { Offset(Random.nextFloat(), Random.nextFloat()) }
    }

    val snowflakePainter = if (condition == WeatherCondition.SNOWY) {
        rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_snowflake))
    } else null

    Box(modifier = Modifier.fillMaxSize()) {
        if (condition == WeatherCondition.THUNDERSTORM) ThunderLayer()
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cloudColor = condition.getCloudColor(isDay)
            val cloudAlpha = condition.getCloudAlpha(isDay)

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

            when (condition) {
                WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM -> {
                    precipElements.forEachIndexed { index, pos ->
                        val x = pos.x * w + (driftProgress * w)
                        val y = ((pos.y + fallProgress) % 1f) * h
                        
                        val isSmall = index % 2 == 0
                        val alpha = if (isSmall) 0.07f else 0.15f
                        val length = (if (isSmall) 4.dp else 8.dp).toPx()
                        val thickness = (if (isSmall) 0.5.dp else 0.8.dp).toPx()
                        val slant = (if (isSmall) 1.dp else 2.dp).toPx()
                        
                        drawLine(
                            Color.White.copy(alpha = alpha), 
                            Offset(x, y), 
                            Offset(x - slant, y + length), 
                            thickness, 
                            StrokeCap.Round
                        )
                    }
                }
                WeatherCondition.SNOWY -> {
                    precipElements.forEachIndexed { index, pos ->
                        val x = pos.x * w + (kotlin.math.sin(fallProgress.toDouble() * Math.PI * 2 + pos.x * 10).toFloat() * 15.dp.toPx())
                        val y = ((pos.y + fallProgress) % 1f) * h
                        
                        val isSmall = index % 2 == 0
                        val baseScale = if (isSmall) 0.245f else 0.525f 
                        val scale = baseScale + (pos.x * 0.14f) 
                        val alpha = if (isSmall) 0.4f else 0.7f
                        
                        drawCircle(
                            Color.White.copy(alpha = 0.08f),
                            radius = (if (isSmall) 2.8.dp else 4.9.dp).toPx(), 
                            center = Offset(x, y)
                        )

                        snowflakePainter?.let { painter ->
                            val sizePx = 14.dp.toPx() * scale
                            withTransform({
                                translate(x - sizePx / 2, y - sizePx / 2)
                                rotate(degrees = fallProgress * 360f * (if (index % 3 == 0) 1.5f else -1f), pivot = Offset(sizePx / 2, sizePx / 2))
                            }) {
                                with(painter) {
                                    draw(size = Size(sizePx, sizePx), alpha = alpha)
                                }
                            }
                        }
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
    val timings = day.timings
    val isha = timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)
    val fajr = timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val sunrise = timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val maghrib = timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)

    val isFullNight = !currentTime.isBefore(isha) || currentTime.isBefore(fajr)
    val isDawn = !isFullNight && currentTime.isBefore(sunrise)
    val isDusk = !isFullNight && !currentTime.isBefore(maghrib) && currentTime.isBefore(isha)

    val isTransition = isDawn || isDusk

    if (!isFullNight && !isTransition) return

    val starCount = if (isTransition) 15 else 50
    val topCoverage = if (isTransition) 0.10f else 0.20f
    val starAlphaMultiplier = if (isTransition) 0.5f else 1.0f

    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = Math.PI.toFloat() * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val stars = remember(isTransition, topCoverage) {
        val starColors = listOf(
            Color(0xFFFFFFFF), // Pure White
            Color(0xFFFFF9C4), // Warm White
            Color(0xFFE3F2FD), // Cool Blue
            Color(0xFFFFFDE7), // Soft Yellow
            Color(0xFFF3E5F5), // Soft Purple
            Color(0xFFE0F2F1)  // Minty White
        )
        List(starCount) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat() * topCoverage,
                size = Random.nextFloat() * 1.5f + 0.4f,
                color = starColors.random(),
                twinkleSpeed = Random.nextFloat() * 2.5f + 0.5f,
                twinklePhase = Random.nextFloat() * Math.PI.toFloat() * 2f
            )
        }
    }

    val distantStars = remember(isTransition, topCoverage) {
        if (isTransition) emptyList() else List(80) {
            Offset(Random.nextFloat(), Random.nextFloat() * topCoverage)
        }
    }

    var meteorStartPos by remember { mutableStateOf(Offset.Zero) }
    val meteorAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(15000, 30000))
            meteorStartPos = Offset(Random.nextFloat() * 0.8f + 0.1f, Random.nextFloat() * 0.10f)
            meteorAnim.snapTo(0f)
            meteorAnim.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        
        if (isFullNight) {
            // Enhanced Deep Space Nebulas/Galactic Dust
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(w * 0.7f, h * 0.10f),
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = Offset(w * 0.7f, h * 0.10f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE91EC7).copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(w * 0.2f, h * 0.08f),
                    radius = w * 0.3f
                ),
                radius = w * 0.3f,
                center = Offset(w * 0.2f, h * 0.08f)
            )

            // Distant Static Stars (Gives depth)
            distantStars.forEach { pos ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = 0.4.dp.toPx(),
                    center = Offset(pos.x * w, pos.y * h)
                )
            }
        }
        
        stars.forEach { star ->
            val twinkle = ((kotlin.math.sin((time * star.twinkleSpeed + star.twinklePhase).toDouble()).toFloat() + 1f) / 2f)
            val alpha = (0.2f + twinkle * 0.8f) * starAlphaMultiplier
            
            // Soft radiant glow
            drawCircle(
                color = star.color.copy(alpha = alpha * 0.1f),
                radius = star.size.dp.toPx() * 3.5f,
                center = Offset(star.x * w, star.y * h)
            )
            
            drawCircle(
                color = star.color.copy(alpha = alpha),
                radius = star.size.dp.toPx(),
                center = Offset(star.x * w, star.y * h)
            ) 
        }
        
        if (isFullNight && meteorAnim.value > 0f && meteorAnim.value < 1f) {
            val progress = meteorAnim.value
            val startX = meteorStartPos.x * w; val startY = meteorStartPos.y * h
            val currentX = startX + (progress * 200.dp.toPx()); val currentY = startY + (progress * 100.dp.toPx())
            val tailAlpha = (1f - progress).coerceAtLeast(0f)
            
            // Meteor Trail
            drawLine(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = tailAlpha * 0.6f)),
                    Offset(startX + (progress * 0.85f * 200.dp.toPx()), startY + (progress * 0.85f * 100.dp.toPx())),
                    Offset(currentX, currentY)
                ),
                Offset(startX + (progress * 0.85f * 200.dp.toPx()), startY + (progress * 0.85f * 100.dp.toPx())),
                Offset(currentX, currentY),
                1.5.dp.toPx(),
                StrokeCap.Round
            )
            
            // Meteor Head
            drawCircle(
                color = Color.White.copy(alpha = tailAlpha * 0.3f),
                radius = 3.dp.toPx(),
                center = Offset(currentX, currentY)
            )
        }
    }
}

@Composable
fun AtmosphericBackgroundLayer(
    currentTime: LocalTime, 
    day: PrayerDay?, 
    weatherCondition: WeatherCondition = WeatherCondition.CLEAR,
    sunAzimuth: Float = 0f, 
    sunAltitude: Float = 0f, 
    compassAzimuth: Float = 0f
) {
    if (day == null) return
    val timings = day.timings
    val sunrise = timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val dhuhur = timings[PrayerType.DHUHR] ?: LocalTime.of(13, 0)
    val maghrib = timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    
    if (!(currentTime.isAfter(sunrise) && currentTime.isBefore(maghrib))) return

    val isMorning = currentTime.isBefore(dhuhur)
    val infiniteTransition = rememberInfiniteTransition(label = "atmosphere")
    
    val bloomPulse by infiniteTransition.animateFloat(0.7f, 1.0f, infiniteRepeatable(tween(if (isMorning) 5000 else 4000, easing = LinearOutSlowInEasing), RepeatMode.Reverse), label = "bloomPulse")
    val particles = remember(isMorning) { List(if (isMorning) 15 else 25) { MutableParticle(Random.nextFloat(), Random.nextFloat(), if (isMorning) Random.nextFloat() * 1.5f + 0.5f else Random.nextFloat() * 2f + 1f, if (isMorning) Random.nextFloat() * 0.0006f + 0.0003f else Random.nextFloat() * 0.001f + 0.0005f) } }
    val step by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart), label = "step")

    // Birds logic
    val birdProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(35000, easing = LinearEasing), RepeatMode.Restart),
        label = "birds"
    )
    val birdWingFreq by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "wings"
    )

    val canShowBirds = weatherCondition == WeatherCondition.CLEAR || 
                       weatherCondition == WeatherCondition.PARTLY_CLOUDY || 
                       weatherCondition == WeatherCondition.OVERCAST

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        
        // 1. Sun Flare
        val relativeAzimuth = (sunAzimuth - compassAzimuth + 540) % 360 - 180
        if (sunAltitude > 0 && kotlin.math.abs(relativeAzimuth) < 90) {
            val flareX = w / 2 + (relativeAzimuth / 90f) * (w / 2); val flareY = h / 2 - (sunAltitude / 90f) * (h / 2); val flareScale = (sunAltitude / 90f).coerceAtLeast(0.5f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f * bloomPulse), Color.Transparent), Offset(flareX, flareY), w * 0.4f * flareScale), w * 0.4f * flareScale, Offset(flareX, flareY))
            drawCircle(Color.White.copy(alpha = 0.05f), 10.dp.toPx(), Offset(w - flareX, h - flareY))
        }

        // 2. Birds (Improved and conditional)
        if (canShowBirds) {
            val birdY = 0.12f * h
            val birdAlpha = if (weatherCondition == WeatherCondition.OVERCAST) 0.06f else 0.12f
            val birdColor = Color.Black.copy(alpha = birdAlpha)
            
            // Fade-in/out logic based on progress
            val globalAlpha = when {
                birdProgress < 0f -> (birdProgress + 0.3f) / 0.3f
                birdProgress > 1f -> (1.3f - birdProgress) / 0.3f
                else -> 1f
            }.coerceIn(0f, 1f)

            for (i in 0..2) {
                // Individual bird timing offset for flapping
                val birdFlapOffset = (i * 0.2f)
                val flapVal = ((Math.sin((birdWingFreq + birdFlapOffset).toDouble() * Math.PI * 2).toFloat() + 1f) / 2f)

                // V-formation positions with subtle organic swaying
                val birdX = (birdProgress * w) + (i * 45.dp.toPx()) - (Math.sin(birdProgress.toDouble() * 4 + i).toFloat() * 8.dp.toPx())
                val offsetBirdY = birdY + (i * 18.dp.toPx()) + (Math.cos(birdProgress.toDouble() * 2.5 + i).toFloat() * 12.dp.toPx())
                
                val wingSpan = (7.dp + (i.dp * 0.4f)).toPx()
                val wingHeight = (2.5.dp + (i.dp * 0.2f)).toPx() * flapVal
                
                val path = Path().apply {
                    // Start at left wing tip
                    moveTo(birdX - wingSpan, offsetBirdY - wingHeight)
                    // Curve to body center
                    quadraticTo(birdX - wingSpan/2, offsetBirdY + (wingHeight/3), birdX, offsetBirdY + wingHeight/2)
                    // Curve to right wing tip
                    quadraticTo(birdX + wingSpan/2, offsetBirdY + (wingHeight/3), birdX + wingSpan, offsetBirdY - wingHeight)
                }
                drawPath(path, birdColor.copy(alpha = birdColor.alpha * globalAlpha), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        // 3. Atmospheric Particles
        particles.forEach { p ->
            val yPos = (p.y - (step * p.speed * 100)).let { if (it < 0) it + 1f else it % 1f }
            drawCircle((if (isMorning) Color(0xFFFFF9C4) else Color.White).copy(alpha = 0.15f), p.size.dp.toPx(), Offset(p.x * w, yPos * h))
        }

        // 4. Horizon Haze
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, (if (isMorning) Color(0xFFFFECB3) else Color.White).copy(alpha = 0.06f)), startY = h * 0.7f, endY = h), topLeft = Offset(0f, h * 0.7f), size = Size(w, h * 0.3f))
    }
}

private class MutableParticle(val x: Float, var y: Float, val size: Float, val speed: Float)

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val twinkleSpeed: Float,
    val twinklePhase: Float
)
