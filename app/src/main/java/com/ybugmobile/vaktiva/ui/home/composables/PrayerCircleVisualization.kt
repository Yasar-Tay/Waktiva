package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PrayerCircleVisualization(
    day: PrayerDay,
    currentTime: LocalTime,
    nextPrayer: NextPrayer?,
    isSelectedDayToday: Boolean,
    centerContent: @Composable (Color) -> Unit,
    contentColor: Color = Color.White,
    isMuted: Boolean = false,
    playAdhanAudio: Boolean = false,
    onSkipAudio: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Tooltip & Animation State
    var tooltipData by remember { mutableStateOf<Triple<String, Offset, Color>?>(null) }
    val tooltipAlpha = remember { Animatable(0f) }
    var clickedItemId by remember { mutableStateOf<String?>(null) }
    val bounceAnim = remember { Animatable(1f) }

    // Pulse Animation for Active Prayer
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (isSelectedDayToday) {
            val totalMinutes = currentTime.hour * 60 + currentTime.minute
            val progressAngle = (totalMinutes.toFloat() / (24 * 60)) * 360f
            if (layoutDirection == LayoutDirection.Rtl) -progressAngle + 180f else progressAngle + 180f
        } else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

    LaunchedEffect(tooltipData) {
        if (tooltipData != null) {
            delay(3000)
            tooltipAlpha.animateTo(0f, tween(400))
            tooltipData = null
            clickedItemId = null
        }
    }

    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 0)
    val sunset = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 0)

    fun getPosition(time: LocalTime, radius: Float, center: Offset): Offset {
        val totalMinutes = time.hour * 60 + time.minute
        val angle = if (layoutDirection == LayoutDirection.Rtl) {
            -(totalMinutes.toFloat() / (24 * 60)) * 360f + 90f
        } else {
            (totalMinutes.toFloat() / (24 * 60)) * 360f + 90f
        }
        val angleRad = Math.toRadians(angle.toDouble())
        return Offset(
            center.x + radius * cos(angleRad).toFloat(),
            center.y + radius * sin(angleRad).toFloat()
        )
    }

    val moonPainter = rememberVectorPainter(Icons.Default.NightsStay)
    val sunPainter = rememberVectorPainter(Icons.Default.WbSunny)
    val sunrisePainter = rememberVectorPainter(Icons.Default.WbTwilight)
    val sunsetPainter = rememberVectorPainter(Icons.Default.WbTwilight)

    val prayers = listOf(
        PrayerInfo(PrayerType.FAJR, day.timings[PrayerType.FAJR] ?: LocalTime.MIN, Color(0xFF81D4FA), moonPainter),
        PrayerInfo(PrayerType.SUNRISE, sunrise, Color(0xFFFFE082), sunrisePainter),
        PrayerInfo(PrayerType.DHUHR, day.timings[PrayerType.DHUHR] ?: LocalTime.MIN, Color(0xFFFFF59D), sunPainter),
        PrayerInfo(PrayerType.ASR, day.timings[PrayerType.ASR] ?: LocalTime.MIN, Color(0xFFFFCC80), sunPainter),
        PrayerInfo(PrayerType.MAGHRIB, sunset, Color(0xFFCE93D8), sunsetPainter),
        PrayerInfo(PrayerType.ISHA, day.timings[PrayerType.ISHA] ?: LocalTime.MIN, Color(0xFF9FA8DA), moonPainter)
    )

    val currentPrayerType = remember(day, currentTime) {
        val sortedTimings = day.timings.toList().sortedBy { it.second }
        var current: PrayerType? = null
        for (i in sortedTimings.indices) {
            val time = sortedTimings[i].second
            if (currentTime.isAfter(time) || currentTime == time) {
                current = sortedTimings[i].first
            } else break
        }
        current ?: PrayerType.ISHA
    }

    val currentPrayerColor = prayers.find { it.type == currentPrayerType }?.color ?: Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp), // Reduced padding to allow the circle to be larger
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(day, currentTime, isSelectedDayToday, canvasSize, layoutDirection) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
                        val radius = canvasSize.width / 2 - 12.dp.toPx() // Adjusted radius calculation
                        
                        var clicked = false
                        
                        // Check Current Time Dot click
                        if (isSelectedDayToday) {
                            val currentPos = getPosition(currentTime, radius, center)
                            if ((tapOffset - currentPos).getDistance() <= 30.dp.toPx()) {
                                tooltipData = Triple(currentTime.format(formatter), currentPos, currentPrayerColor)
                                clickedItemId = "CURRENT_TIME"
                                clicked = true
                            }
                        }

                        // Check Prayer Markers click if current time wasn't clicked
                        if (!clicked) {
                            for (prayer in prayers) {
                                val pos = getPosition(prayer.time, radius, center)
                                if ((tapOffset - pos).getDistance() <= 30.dp.toPx()) {
                                    val localizedName = prayer.type.getDisplayName(context)
                                    tooltipData = Triple("$localizedName: ${prayer.time.format(formatter)}", pos, prayer.color)
                                    clickedItemId = prayer.type.name
                                    clicked = true
                                    break
                                }
                            }
                        }

                        if (clicked) {
                            scope.launch { tooltipAlpha.animateTo(1f, tween(200)) }
                            scope.launch {
                                bounceAnim.snapTo(0.8f)
                                bounceAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        } else {
                            scope.launch { tooltipAlpha.animateTo(0f, tween(200)) }
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 12.dp.toPx() // Increased radius by reducing margin

            // 1. Draw elegant dashed background track
            drawCircle(
                color = contentColor.copy(alpha = 0.05f),
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Hour ticks
            for (i in 0 until 24) {
                val angle = i * 15f + 90f
                val angleRad = Math.toRadians(angle.toDouble())
                val inner = radius - 4.dp.toPx()
                val outer = radius + 4.dp.toPx()
                
                drawLine(
                    color = contentColor.copy(alpha = 0.15f),
                    start = Offset(center.x + inner * cos(angleRad).toFloat(), center.y + inner * sin(angleRad).toFloat()),
                    end = Offset(center.x + outer * cos(angleRad).toFloat(), center.y + outer * sin(angleRad).toFloat()),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2. Daylight Arc
            val sunriseMinutes = sunrise.hour * 60 + sunrise.minute
            val sunsetMinutes = sunset.hour * 60 + sunset.minute
            
            val startAngle = if (layoutDirection == LayoutDirection.Rtl) {
                -(sunriseMinutes.toFloat() / (24 * 60)) * 360f + 90f
            } else {
                (sunriseMinutes.toFloat() / (24 * 60)) * 360f + 90f
            }
            
            var sweepAngle = if (layoutDirection == LayoutDirection.Rtl) {
                val end = -(sunsetMinutes.toFloat() / (24 * 60)) * 360f + 90f
                var diff = end - startAngle
                if (diff > 0) diff -= 360f
                diff
            } else {
                val end = (sunsetMinutes.toFloat() / (24 * 60)) * 360f + 90f
                var diff = end - startAngle
                if (diff < 0) diff += 360f
                diff
            }

            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to Color(0xFFFFE082).copy(alpha = 0.3f),
                    0.5f to Color(0xFFFFB74D).copy(alpha = 0.3f),
                    1.0f to Color(0xFFCE93D8).copy(alpha = 0.3f),
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3. Clock Handle (Current Time)
            if (isSelectedDayToday) {
                withTransform({
                    rotate(rotationAngle, center)
                }) {
                    drawLine(
                        brush = Brush.verticalGradient(
                            colors = listOf(currentPrayerColor, currentPrayerColor.copy(alpha = 0.1f)),
                            startY = center.y - radius,
                            endY = center.y
                        ),
                        start = Offset(center.x, center.y),
                        end = Offset(center.x, center.y - radius + 10.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    drawCircle(
                        color = currentPrayerColor,
                        radius = 2.dp.toPx(),
                        center = Offset(center.x, center.y)
                    )
                }
            }

            // 4. Prayer Markers
            prayers.forEach { (type, time, color, painter) ->
                val pos = getPosition(time, radius, center)
                val isCurrent = type == currentPrayerType && isSelectedDayToday
                val scale = if (clickedItemId == type.name) bounceAnim.value else 1f

                withTransform({ scale(scale, scale, pos) }) {
                    val markerRadius = if (isCurrent) 16.dp.toPx() else 14.dp.toPx()
                    val iconSize = if (isCurrent) 20.dp.toPx() else 16.dp.toPx()
                    
                    if (isCurrent) {
                        drawCircle(
                            color = color.copy(alpha = 0.1f),
                            radius = markerRadius * 2.5f * pulseScale,
                            center = pos
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color, color.copy(alpha = 0.4f)),
                                center = pos,
                                radius = markerRadius * 1.5f
                            ),
                            radius = markerRadius,
                            center = pos
                        )
                    } else {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color, color.darken(0.2f)),
                                center = pos,
                                radius = markerRadius
                            ),
                            radius = markerRadius,
                            center = pos
                        )
                    }

                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = markerRadius,
                        center = pos,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    translate(pos.x - iconSize / 2, pos.y - iconSize / 2) {
                        with(painter) {
                            draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White))
                        }
                    }
                }
            }

            // 5. Current Time Indicator Dot
            if (isSelectedDayToday) {
                val currentPos = getPosition(currentTime, radius, center)
                val scale = if (clickedItemId == "CURRENT_TIME") bounceAnim.value else 1f

                withTransform({ scale(scale, scale, currentPos) }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(currentPrayerColor.copy(alpha = 0.4f), Color.Transparent),
                            center = currentPos,
                            radius = 20.dp.toPx()
                        ),
                        radius = 20.dp.toPx(),
                        center = currentPos
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = currentPos
                    )
                }
            }
        }

        // Center Content Overlay
        centerContent(currentPrayerColor)

        // 6. Tooltip Overlay
        tooltipData?.let { (text, pos, color) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier
                        .offset {
                            val x = pos.x - 50.dp.toPx()
                            val y = pos.y - 60.dp.toPx()
                            IntOffset(x.toInt(), y.toInt())
                        }
                        .graphicsLayer(alpha = tooltipAlpha.value)
                        .clip(RoundedCornerShape(8.dp)),
                    color = Color.Black.copy(alpha = 0.8f),
                    contentColor = Color.White
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

private fun Color.darken(factor: Float): Color {
    return Color(
        red = red * (1 - factor),
        green = green * (1 - factor),
        blue = blue * (1 - factor),
        alpha = alpha
    )
}

data class PrayerInfo(
    val type: PrayerType,
    val time: LocalTime,
    val color: Color,
    val painter: androidx.compose.ui.graphics.vector.VectorPainter
)
