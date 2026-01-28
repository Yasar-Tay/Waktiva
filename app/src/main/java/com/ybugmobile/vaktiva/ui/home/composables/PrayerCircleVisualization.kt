package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    centerContent: @Composable () -> Unit,
    contentColor: Color = Color.White,
    isMuted: Boolean = false,
    playAdhanAudio: Boolean = false,
    onSkipAudio: (String) -> Unit = {}
) {
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
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(day, currentTime, isSelectedDayToday, canvasSize, layoutDirection) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
                        val radius = canvasSize.width / 2 - 20.dp.toPx()
                        
                        var clicked = false
                        for (prayer in prayers) {
                            val pos = getPosition(prayer.time, radius, center)
                            if ((tapOffset - pos).getDistance() <= 30.dp.toPx()) {
                                tooltipData = Triple("${prayer.type.name}: ${prayer.time.format(formatter)}", pos, prayer.color)
                                clickedItemId = prayer.type.name
                                clicked = true
                                break
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
            val radius = size.width / 2 - 20.dp.toPx()

            // 1. Draw elegant dashed background track
            drawCircle(
                color = contentColor.copy(alpha = 0.05f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
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
                
                drawCircle(
                    color = currentPrayerColor,
                    radius = 4.dp.toPx(),
                    center = currentPos
                )
            }
        }

        // Center Content
        centerContent()

        // 6. Mute/Skip Button - Remarkable Floating Placement
        if (playAdhanAudio && isSelectedDayToday && nextPrayer != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 12.dp)
            ) {
                IconButton(
                    onClick = { onSkipAudio(nextPrayer.type.name) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isMuted) 
                                    listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.4f))
                                else 
                                    listOf(currentPrayerColor.copy(alpha = 0.8f), currentPrayerColor.copy(alpha = 0.4f))
                            )
                        )
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        contentDescription = "Mute Adhan",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Tooltip
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (tooltipAlpha.value > 0f && tooltipData != null) {
                val (text, pos, color) = tooltipData!!
                val alpha = tooltipAlpha.value
                
                val textStyle = TextStyle(
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                val textLayout = textMeasurer.measure(text, textStyle)
                
                val bgWidth = textLayout.size.width + 24.dp.toPx()
                val bgHeight = textLayout.size.height + 12.dp.toPx()
                
                val tooltipX = (pos.x - bgWidth / 2).coerceIn(8.dp.toPx(), size.width - bgWidth - 8.dp.toPx())
                val tooltipY = if (pos.y < size.height / 2) pos.y + 20.dp.toPx() else pos.y - bgHeight - 20.dp.toPx()

                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.7f * alpha),
                    topLeft = Offset(tooltipX, tooltipY),
                    size = Size(bgWidth, bgHeight),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
                
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(tooltipX + 12.dp.toPx(), tooltipY + 6.dp.toPx())
                )
            }
        }
    }
}

// Helper to darken colors for depth
private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1f - factor)).coerceIn(0f, 1f),
        green = (green * (1f - factor)).coerceIn(0f, 1f),
        blue = (blue * (1f - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private data class PrayerInfo(
    val type: PrayerType,
    val time: LocalTime,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.VectorPainter
)
