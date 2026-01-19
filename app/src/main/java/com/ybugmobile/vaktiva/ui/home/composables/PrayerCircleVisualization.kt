package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    centerContent: @Composable () -> Unit
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

    val runningManPainter = rememberVectorPainter(Icons.Default.DirectionsRun)

    // Icons and Colors for each prayer
    val prayers = listOf(
        Triple(stringResource(R.string.prayer_fajr), day.timings[PrayerType.FAJR] ?: LocalTime.MIN, Color(0xFF90CAF9)),
        Triple(stringResource(R.string.prayer_sunrise), sunrise, Color(0xFFFFB74D)),
        Triple(stringResource(R.string.prayer_dhuhr), day.timings[PrayerType.DHUHR] ?: LocalTime.MIN, Color(0xFFFFF176)),
        Triple(stringResource(R.string.prayer_asr), day.timings[PrayerType.ASR] ?: LocalTime.MIN, Color(0xFFFF8A65)),
        Triple(stringResource(R.string.prayer_maghrib), sunset, Color(0xFFBA68C8)),
        Triple(stringResource(R.string.prayer_isha), day.timings[PrayerType.ISHA] ?: LocalTime.MIN, Color(0xFF7986CB))
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
                .padding(16.dp)
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(day, currentTime, isSelectedDayToday, canvasSize) {
                    detectTapGestures { tapOffset ->
                        if (canvasSize == Size.Zero) return@detectTapGestures

                        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
                        val radius = canvasSize.width / 2 - with(density) { 50.dp.toPx() }
                        var clicked = false
                        var newTooltipData: Pair<String, Offset>? = null
                        var newClickedId: String? = null

                        // Check Running Man
                        if (isSelectedDayToday) {
                            val currentPos = getPosition(currentTime, radius, center)
                            val hitRadius = with(density) { 30.dp.toPx() }
                            if ((tapOffset - currentPos).getDistance() <= hitRadius) {
                                newTooltipData = "Time: ${currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}" to currentPos
                                newClickedId = "current"
                                clicked = true
                            }
                        }

                        // Check Prayers
                        if (!clicked) {
                            val labelRadius = radius + with(density) { 35.dp.toPx() }
                            val hitRadius = with(density) { 30.dp.toPx() }

                            for (prayer in prayers) {
                                val (name, time, _) = prayer
                                val pos = getPosition(time, labelRadius, center)
                                if ((tapOffset - pos).getDistance() <= hitRadius) {
                                    newTooltipData = "$name: ${time.format(formatter)}" to pos
                                    newClickedId = name
                                    clicked = true
                                    break
                                }
                            }
                        }

                        if (clicked) {
                            tooltipData = newTooltipData
                            clickedItemId = newClickedId
                            scope.launch {
                                tooltipAlpha.animateTo(1f, tween(200))
                            }
                            scope.launch {
                                bounceAnim.snapTo(0.7f)
                                bounceAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        } else {
                            scope.launch {
                                tooltipAlpha.animateTo(0f, tween(200))
                            }
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 50.dp.toPx()

            // Mapping: 12:00 (Noon) at TOP (270 degrees)
            fun getOffset(time: LocalTime, r: Float): Offset {
                return getPosition(time, r, center)
            }

            // 1. Draw Arcs (Night & Day)
            val sunriseMinutes = sunrise.hour * 60 + sunrise.minute
            val sunsetMinutes = sunset.hour * 60 + sunset.minute
            val startAngleDay = (sunriseMinutes.toFloat() / (24 * 60)) * 360f + 90f
            val endAngleDay = (sunsetMinutes.toFloat() / (24 * 60)) * 360f + 90f
            var sweepAngleDay = endAngleDay - startAngleDay
            if (sweepAngleDay < 0) sweepAngleDay += 360f

            // NIGHT ARC - Improved Visibility (Glowing neon effect)
            drawArc(
                color = Color.White.copy(alpha = 0.3f),
                startAngle = endAngleDay,
                sweepAngle = 360f - sweepAngleDay,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            // Soft glow for night arc
            drawArc(
                color = Color(0xFF81D4FA).copy(alpha = 0.4f),
                startAngle = endAngleDay,
                sweepAngle = 360f - sweepAngleDay,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 2.dp.toPx())
            )

            // DAY ARC
            drawArc(
                color = Color(0xFFFFF176).copy(alpha = 0.4f),
                startAngle = startAngleDay,
                sweepAngle = sweepAngleDay,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 4.dp.toPx())
            )

            // 2. Current Time Indicator (Flow-aware precision pointer)
            if (isSelectedDayToday) {
                val totalMinutes = currentTime.hour * 60 + currentTime.minute
                val angle = (totalMinutes.toFloat() / (24 * 60)) * 360f + 90f
                val currentPos = getOffset(currentTime, radius)

                // Trail - Comet like
                val trailLength = 35f
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        (angle - trailLength) / 360f to Color.Transparent,
                        angle / 360f to Color.Cyan.copy(alpha = 0.7f),
                        1.0f to Color.Transparent,
                        center = center
                    ),
                    startAngle = angle - trailLength,
                    sweepAngle = trailLength,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )

                // Running Man Icon
                val iconSize = 32.dp.toPx()

                // Ghost Trail
                for (i in 1..3) {
                    val ghostAngle = angle - (i * 3f)
                    val ghostRad = Math.toRadians(ghostAngle.toDouble())
                    val ghostX = center.x + radius * cos(ghostRad).toFloat()
                    val ghostY = center.y + radius * sin(ghostRad).toFloat()
                    val ghostFlip = if ((ghostAngle % 360f) in 0f..180f) -1f else 1f

                    val rmScale = if (clickedItemId == "current") bounceAnim.value else 1f
                    withTransform({
                        translate(left = ghostX - iconSize / 2, top = ghostY - iconSize / 2)
                        rotate(degrees = ghostAngle + 90f, pivot = Offset(iconSize / 2, iconSize / 2))
                        scale(scaleX = rmScale, scaleY = ghostFlip * rmScale, pivot = Offset(iconSize / 2, iconSize / 2))
                    }) {
                        with(runningManPainter) {
                            draw(
                                size = Size(iconSize, iconSize),
                                colorFilter = ColorFilter.tint(Color.Cyan.copy(alpha = 0.4f / i))
                            )
                        }
                    }
                }

                val flipScale = if ((angle % 360f) in 0f..180f) -1f else 1f
                val rmScale = if (clickedItemId == "current") bounceAnim.value else 1f
                withTransform({
                    translate(left = currentPos.x - iconSize / 2, top = currentPos.y - iconSize / 2)
                    rotate(degrees = angle + 90f, pivot = Offset(iconSize / 2, iconSize / 2))
                    scale(scaleX = rmScale, scaleY = flipScale * rmScale, pivot = Offset(iconSize / 2, iconSize / 2))
                }) {
                    with(runningManPainter) {
                        draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.Cyan))
                    }
                }
            }

            // 3. Prayer Markers & Labels
            prayers.forEach { (name, time, markerColor) ->
                val pos = getOffset(time, radius)
                val isNext = nextPrayer?.time == time && isSelectedDayToday
                
                val pScale = if (clickedItemId == name) bounceAnim.value else 1f

                withTransform({
                    scale(scaleX = pScale, scaleY = pScale, pivot = pos)
                }) {
                    if (isNext) {
                        drawCircle(markerColor.copy(alpha = 0.3f), radius = 12.dp.toPx(), center = pos)
                        drawCircle(markerColor, radius = 7.dp.toPx(), center = pos)
                        drawCircle(Color.White, radius = 3.dp.toPx(), center = pos)
                    } else {
                        drawCircle(
                            markerColor.copy(alpha = 0.4f),
                            radius = 5.dp.toPx(),
                            center = pos,
                            style = Stroke(2.dp.toPx())
                        )
                        drawCircle(markerColor.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = pos)
                    }


                    // Label Tag + Marker Indicator
                    val labelPos = getOffset(time, radius + 35.dp.toPx())
                    val textStyle = TextStyle(
                        color = if (isNext) markerColor else Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium
                    )
                    val textLayoutResult = textMeasurer.measure(name, textStyle)

                    val indicatorSize = 6.dp.toPx()
                    val spacing = 4.dp.toPx()
                    val totalWidth = indicatorSize + spacing + textLayoutResult.size.width

                    if (isNext) {
                        val tagPadding = 4.dp.toPx()
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.3f),
                            topLeft = Offset(
                                labelPos.x - totalWidth / 2 - tagPadding,
                                labelPos.y - textLayoutResult.size.height / 2 - tagPadding / 2
                            ),
                            size = Size(
                                totalWidth + tagPadding * 2,
                                textLayoutResult.size.height + tagPadding
                            ),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }


                    // Small colored dot before text
                    drawCircle(
                        color = markerColor,
                        radius = indicatorSize / 2,
                        center = Offset(labelPos.x - totalWidth / 2 + indicatorSize / 2, labelPos.y)
                    )

                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            labelPos.x - totalWidth / 2 + indicatorSize + spacing,
                            labelPos.y - textLayoutResult.size.height / 2
                        )
                    )
                }
            }

            // 4. Draw Tooltip
            if (tooltipAlpha.value > 0f && tooltipData != null) {
                val (text, pos) = tooltipData!!
                val alpha = tooltipAlpha.value
                val padding = 8.dp.toPx()
                val textLayout = textMeasurer.measure(text, TextStyle(color = Color.White.copy(alpha = alpha), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                val boxWidth = textLayout.size.width + padding * 2
                val boxHeight = textLayout.size.height + padding * 2

                // Draw above the target position
                val tooltipPos = Offset(pos.x - boxWidth / 2, pos.y - boxHeight - 12.dp.toPx())

                drawRoundRect(
                    color = Color.DarkGray.copy(alpha = 0.95f * alpha),
                    topLeft = tooltipPos,
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(tooltipPos.x + padding, tooltipPos.y + padding)
                )
            }
        }
        // Center Content
        Box(modifier = Modifier.align(Alignment.Center)) {
            centerContent()
        }
    }
}