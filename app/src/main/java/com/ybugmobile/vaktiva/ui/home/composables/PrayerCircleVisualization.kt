package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight

@Composable
fun PrayerCircleVisualization(
    day: PrayerDay,
    currentTime: LocalTime,
    nextPrayer: NextPrayer?,
    isSelectedDayToday: Boolean,
    centerContent: @Composable () -> Unit,
    contentColor: Color = Color.White
) {
    val textMeasurer = rememberTextMeasurer()
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    
    // Tooltip & Animation State
    var tooltipData by remember { mutableStateOf<Triple<String, Offset, Color>?>(null) }
    val tooltipAlpha = remember { Animatable(0f) }
    var clickedItemId by remember { mutableStateOf<String?>(null) }
    val bounceAnim = remember { Animatable(1f) }

    // Ripple Animation State
    val rippleAnim = remember { Animatable(0f) }
    var rippleCenter by remember { mutableStateOf(Offset.Zero) }

    // Pulse Animation for Active Prayer
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Auto-dismiss tooltip after a while
    LaunchedEffect(tooltipData) {
        if (tooltipData != null) {
            delay(3000) // Stay visible for 3 seconds
            tooltipAlpha.animateTo(0f, tween(400))
            tooltipData = null
            clickedItemId = null
        }
    }

    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 0)
    val sunset = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 0)

    fun getPosition(time: LocalTime, radius: Float, center: Offset): Offset {
        val totalMinutes = time.hour * 60 + time.minute
        val angle = (totalMinutes.toFloat() / (24 * 60)) * 360f + 90f
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

    // Colors for each prayer - Vibrant and modern
    val prayers = listOf(
        PrayerInfo(PrayerType.FAJR, day.timings[PrayerType.FAJR] ?: LocalTime.MIN, Color(0xFF64B5F6), moonPainter),
        PrayerInfo(PrayerType.SUNRISE, sunrise, Color(0xFFFFB74D), sunrisePainter),
        PrayerInfo(PrayerType.DHUHR, day.timings[PrayerType.DHUHR] ?: LocalTime.MIN, Color(0xFFFFD54F), sunPainter),
        PrayerInfo(PrayerType.ASR, day.timings[PrayerType.ASR] ?: LocalTime.MIN, Color(0xFFFF8A65), sunPainter),
        PrayerInfo(PrayerType.MAGHRIB, sunset, Color(0xFFBA68C8), sunsetPainter),
        PrayerInfo(PrayerType.ISHA, day.timings[PrayerType.ISHA] ?: LocalTime.MIN, Color(0xFF9FA8DA), moonPainter)
    )

    // Determine current prayer for emphasis
    val currentPrayerType = remember(day, currentTime) {
        val sortedTimings = day.timings.toList().sortedBy { it.second }
        var current: PrayerType? = null
        for (i in sortedTimings.indices) {
            val time = sortedTimings[i].second
            if (currentTime.isAfter(time) || currentTime == time) {
                current = sortedTimings[i].first
            } else break
        }
        // If it's before the first prayer, it's Isha from the previous day, but in this circle we might just not emphasize yet
        current ?: PrayerType.ISHA 
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // 1. Background, Arcs, Markers, and Ripple
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp) // Minimal padding to maximize ring size
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(day, currentTime, isSelectedDayToday, canvasSize) {
                    detectTapGestures { tapOffset ->
                        if (canvasSize == Size.Zero) return@detectTapGestures

                        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
                        val radius = canvasSize.width / 2 - with(density) { 24.dp.toPx() }
                        var clicked = false
                        var newTooltipData: Triple<String, Offset, Color>? = null
                        var newClickedId: String? = null

                        // Hit detection for Current Time
                        if (isSelectedDayToday) {
                            val currentPos = getPosition(currentTime, radius, center)
                            val hitRadius = with(density) { 32.dp.toPx() } // Generous hit area
                            if ((tapOffset - currentPos).getDistance() <= hitRadius) {
                                newTooltipData = Triple("Current: ${currentTime.format(formatter)}", currentPos, Color.Cyan)
                                newClickedId = "current"
                                clicked = true
                            }
                        }

                        // Hit detection for Prayer Markers
                        if (!clicked) {
                            val hitRadius = with(density) { 28.dp.toPx() }
                            for (prayer in prayers) {
                                val (type, time, color, _) = prayer
                                val name = type.name
                                val pos = getPosition(time, radius, center)
                                if ((tapOffset - pos).getDistance() <= hitRadius) {
                                    newTooltipData = Triple("${type.name}: ${time.format(formatter)}", pos, color)
                                    newClickedId = name
                                    clicked = true
                                    
                                    // Trigger Ripple
                                    rippleCenter = pos
                                    scope.launch {
                                        rippleAnim.snapTo(0f)
                                        rippleAnim.animateTo(1f, tween(400))
                                    }
                                    break
                                }
                            }
                        }

                        if (clicked) {
                            tooltipData = newTooltipData
                            clickedItemId = newClickedId
                            scope.launch { tooltipAlpha.animateTo(1f, tween(200)) }
                            scope.launch {
                                bounceAnim.snapTo(0.7f)
                                bounceAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        } else {
                            scope.launch { tooltipAlpha.animateTo(0f, tween(200)) }
                            tooltipData = null
                            clickedItemId = null
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 24.dp.toPx()

            // Background Track
            drawCircle(
                color = contentColor.copy(alpha = 0.1f),
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Day/Night Arcs
            val sunriseMinutes = sunrise.hour * 60 + sunrise.minute
            val sunsetMinutes = sunset.hour * 60 + sunset.minute
            val startAngleDay = (sunriseMinutes.toFloat() / (24 * 60)) * 360f + 90f
            val endAngleDay = (sunsetMinutes.toFloat() / (24 * 60)) * 360f + 90f
            var sweepAngleDay = endAngleDay - startAngleDay
            if (sweepAngleDay < 0) sweepAngleDay += 360f

            drawArc(
                color = Color(0xFFFFF176).copy(alpha = 0.4f),
                startAngle = startAngleDay,
                sweepAngle = sweepAngleDay,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 4.dp.toPx())
            )

            // Prayer Markers
            prayers.forEach { (type, time, markerColor, iconPainter) ->
                val pos = getPosition(time, radius, center)
                val isCurrent = type == currentPrayerType && isSelectedDayToday
                val pScale = if (clickedItemId == type.name) bounceAnim.value else 1f

                withTransform({ scale(pScale, pScale, pos) }) {
                    if (isCurrent) {
                        // High-Priority ACTIVE Marker
                        // Outer pulse/halo
                        drawCircle(
                            color = markerColor.copy(alpha = 0.2f),
                            radius = 24.dp.toPx() * pulseScale,
                            center = pos
                        )
                        drawCircle(
                            color = markerColor.copy(alpha = 0.4f),
                            radius = 18.dp.toPx(),
                            center = pos
                        )
                        // Main body
                        drawCircle(
                            color = markerColor,
                            radius = 14.dp.toPx(),
                            center = pos
                        )
                    } else {
                        // Regular "Glass Bead" Marker
                        drawCircle(
                            color = markerColor.copy(alpha = 0.3f),
                            radius = 16.dp.toPx(),
                            center = pos
                        )
                        drawCircle(
                            color = markerColor.copy(alpha = 0.9f),
                            radius = 12.dp.toPx(),
                            center = pos
                        )
                    }

                    val iconSize = 16.dp.toPx()
                    val iconTint = if (markerColor.luminance() > 0.5f) Color.Black else Color.White

                    translate(left = pos.x - iconSize / 2, top = pos.y - iconSize / 2) {
                        with(iconPainter) {
                            draw(size = Size(iconSize, iconSize), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconTint))
                        }
                    }
                }
            }

            // Ripple Effect
            if (rippleAnim.value > 0f && rippleAnim.value < 1f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f * (1f - rippleAnim.value)),
                    radius = 40.dp.toPx() * rippleAnim.value,
                    center = rippleCenter
                )
            }

            // Current Time Clock Hand
            if (isSelectedDayToday) {
                val totalMinutes = currentTime.hour * 60 + currentTime.minute
                val angle = (totalMinutes.toFloat() / (24 * 60)) * 360f + 90f
                val angleRad = Math.toRadians(angle.toDouble())
                
                val innerRadius = 120.dp.toPx()
                val outerRadius = radius
                
                val start = Offset(
                    center.x + innerRadius * cos(angleRad).toFloat(),
                    center.y + innerRadius * sin(angleRad).toFloat()
                )
                val end = Offset(
                    center.x + outerRadius * cos(angleRad).toFloat(),
                    center.y + outerRadius * sin(angleRad).toFloat()
                )
                
                drawLine(
                    color = Color.Cyan.copy(alpha = 0.8f),
                    start = start,
                    end = end,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                drawLine(
                    color = Color.Cyan.copy(alpha = 0.3f),
                    start = start,
                    end = end,
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 2. Center Content (Date/Refresh/etc) - NextPrayerCountdown
        Box(modifier = Modifier.align(Alignment.Center)) {
            centerContent()
        }

        // 3. Tooltip System - Drawn LAST to be on top of center content
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            if (tooltipAlpha.value > 0f && tooltipData != null) {
                val (text, pos, bgColor) = tooltipData!!
                val alpha = tooltipAlpha.value
                
                val textColor = if (bgColor.luminance() > 0.5f) Color.Black else Color.White
                val textStyle = TextStyle(
                    color = textColor.copy(alpha = alpha),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    shadow = if (textColor == Color.White) Shadow(color = Color.Black, blurRadius = 8f) else null
                )
                val textLayout = textMeasurer.measure(text, textStyle)
                
                val paddingH = 16.dp.toPx()
                val paddingV = 10.dp.toPx()
                val boxWidth = textLayout.size.width + paddingH * 2
                val boxHeight = textLayout.size.height + paddingV * 2

                var tooltipX = (pos.x - boxWidth / 2).coerceIn(12.dp.toPx(), size.width - boxWidth - 12.dp.toPx())
                var tooltipY = if (pos.y < size.height / 2) pos.y + 30.dp.toPx() else pos.y - boxHeight - 30.dp.toPx()

                drawRoundRect(
                    color = bgColor.copy(alpha = 0.9f * alpha),
                    topLeft = Offset(tooltipX, tooltipY),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
                
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(tooltipX + paddingH, tooltipY + paddingV)
                )
            }
        }
    }
}

private data class PrayerInfo(
    val type: PrayerType,
    val time: LocalTime,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.VectorPainter
)
