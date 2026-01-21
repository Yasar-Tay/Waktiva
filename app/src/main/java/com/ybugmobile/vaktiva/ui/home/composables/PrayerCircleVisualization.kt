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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
    contentColor: Color = Color.White
) {
    val textMeasurer = rememberTextMeasurer()
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    
    // Tooltip & Animation State
    var tooltipData by remember { mutableStateOf<Pair<String, Offset>?>(null) }
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
        PrayerInfo(stringResource(R.string.prayer_fajr), day.timings[PrayerType.FAJR] ?: LocalTime.MIN, Color(0xFF64B5F6), moonPainter),
        PrayerInfo(stringResource(R.string.prayer_sunrise), sunrise, Color(0xFFFFB74D), sunrisePainter),
        PrayerInfo(stringResource(R.string.prayer_dhuhr), day.timings[PrayerType.DHUHR] ?: LocalTime.MIN, Color(0xFFFFD54F), sunPainter),
        PrayerInfo(stringResource(R.string.prayer_asr), day.timings[PrayerType.ASR] ?: LocalTime.MIN, Color(0xFFFF8A65), sunPainter),
        PrayerInfo(stringResource(R.string.prayer_maghrib), sunset, Color(0xFFBA68C8), sunsetPainter),
        PrayerInfo(stringResource(R.string.prayer_isha), day.timings[PrayerType.ISHA] ?: LocalTime.MIN, Color(0xFF9FA8DA), moonPainter)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
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
                        var newTooltipData: Pair<String, Offset>? = null
                        var newClickedId: String? = null

                        // Hit detection for Current Time
                        if (isSelectedDayToday) {
                            val currentPos = getPosition(currentTime, radius, center)
                            val hitRadius = with(density) { 32.dp.toPx() } // Generous hit area
                            if ((tapOffset - currentPos).getDistance() <= hitRadius) {
                                newTooltipData = "Current: ${currentTime.format(formatter)}" to currentPos
                                newClickedId = "current"
                                clicked = true
                            }
                        }

                        // Hit detection for Prayer Markers
                        if (!clicked) {
                            val hitRadius = with(density) { 28.dp.toPx() }
                            for (prayer in prayers) {
                                val (name, time, _, _) = prayer
                                val pos = getPosition(time, radius, center)
                                if ((tapOffset - pos).getDistance() <= hitRadius) {
                                    newTooltipData = "$name: ${time.format(formatter)}" to pos
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
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 24.dp.toPx()

            // 0. Background Track
            drawCircle(
                color = contentColor.copy(alpha = 0.1f),
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 1. Day/Night Arcs
            val sunriseMinutes = sunrise.hour * 60 + sunrise.minute
            val sunsetMinutes = sunset.hour * 60 + sunset.minute
            val startAngleDay = (sunriseMinutes.toFloat() / (24 * 60)) * 360f + 90f
            val endAngleDay = (sunsetMinutes.toFloat() / (24 * 60)) * 360f + 90f
            var sweepAngleDay = endAngleDay - startAngleDay
            if (sweepAngleDay < 0) sweepAngleDay += 360f

            // Glowy Day Arc
            drawArc(
                color = Color(0xFFFFF176).copy(alpha = 0.4f),
                startAngle = startAngleDay,
                sweepAngle = sweepAngleDay,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 4.dp.toPx())
            )

            // 2. Prayer Markers (Redesigned: Bold & Tactile)
            prayers.forEach { (name, time, markerColor, iconPainter) ->
                val pos = getPosition(time, radius, center)
                val isNext = nextPrayer?.time == time && isSelectedDayToday
                val pScale = if (clickedItemId == name) bounceAnim.value else 1f

                withTransform({ scale(pScale, pScale, pos) }) {
                    if (isNext) {
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

                    // Draw Icon in the center
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

            // 3. Current Time Handle
            if (isSelectedDayToday) {
                val currentPos = getPosition(currentTime, radius, center)
                val hScale = if (clickedItemId == "current") bounceAnim.value else 1f
                
                withTransform({ scale(hScale, hScale, currentPos) }) {
                    // Glow
                    drawCircle(
                        color = Color.Cyan.copy(alpha = 0.3f),
                        radius = 20.dp.toPx(),
                        center = currentPos
                    )
                    // Core
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = currentPos
                    )
                    // Ring
                    drawCircle(
                        color = Color.Cyan,
                        radius = 8.dp.toPx(),
                        center = currentPos,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // 4. Tooltip System
            if (tooltipAlpha.value > 0f && tooltipData != null) {
                val (text, pos) = tooltipData!!
                val alpha = tooltipAlpha.value
                
                val textStyle = TextStyle(
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    shadow = Shadow(color = Color.Black, blurRadius = 8f)
                )
                val textLayout = textMeasurer.measure(text, textStyle)
                
                val paddingH = 16.dp.toPx()
                val paddingV = 10.dp.toPx()
                val boxWidth = textLayout.size.width + paddingH * 2
                val boxHeight = textLayout.size.height + paddingV * 2

                var tooltipX = (pos.x - boxWidth / 2).coerceIn(12.dp.toPx(), size.width - boxWidth - 12.dp.toPx())
                var tooltipY = if (pos.y < size.height / 2) pos.y + 30.dp.toPx() else pos.y - boxHeight - 30.dp.toPx()

                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.9f * alpha),
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

        // Center Content (Date/Refresh/etc)
        Box(modifier = Modifier.align(Alignment.Center)) {
            centerContent()
        }
    }
}

private data class PrayerInfo(
    val name: String,
    val time: LocalTime,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.VectorPainter
)
