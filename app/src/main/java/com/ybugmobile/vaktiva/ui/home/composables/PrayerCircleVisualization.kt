package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.ui.home.NextPrayerInfo
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PrayerCircleVisualization(
    day: PrayerDayEntity,
    currentTime: LocalTime,
    nextPrayer: NextPrayerInfo?,
    isSelectedDayToday: Boolean,
    centerContent: @Composable () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseTime(timeStr: String): LocalTime {
        val cleaned = timeStr.split(" ")[0]
        return LocalTime.parse(cleaned, formatter)
    }

    val sunrise = parseTime(day.sunrise)
    val sunset = parseTime(day.maghrib)

    // Icons and Colors for each prayer
    val prayers = listOf(
        Triple(stringResource(R.string.prayer_fajr), parseTime(day.fajr), Color(0xFF90CAF9)),
        Triple(stringResource(R.string.prayer_sunrise), sunrise, Color(0xFFFFB74D)),
        Triple(stringResource(R.string.prayer_dhuhr), parseTime(day.dhuhr), Color(0xFFFFF176)),
        Triple(stringResource(R.string.prayer_asr), parseTime(day.asr), Color(0xFFFF8A65)),
        Triple(stringResource(R.string.prayer_maghrib), sunset, Color(0xFFBA68C8)),
        Triple(stringResource(R.string.prayer_isha), parseTime(day.isha), Color(0xFF7986CB))
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
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 50.dp.toPx()

            // Mapping: 12:00 (Noon) at TOP (270 degrees)
            fun getOffset(time: LocalTime, r: Float): Offset {
                val totalMinutes = time.hour * 60 + time.minute
                val angle = (totalMinutes.toFloat() / (24 * 60)) * 360f + 90f
                val angleRad = Math.toRadians(angle.toDouble())
                return Offset(
                    center.x + r * cos(angleRad).toFloat(),
                    center.y + r * sin(angleRad).toFloat()
                )
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

                // Add a glowing precision point
                drawCircle(
                    Color.Cyan.copy(alpha = 0.4f),
                    radius = 18.dp.toPx(),
                    center = currentPos
                )
                drawCircle(Color.White, radius = 2.5.dp.toPx(), center = currentPos)
            }

            // 3. Prayer Markers & Labels
            prayers.forEach { (name, time, markerColor) ->
                val pos = getOffset(time, radius)
                val isNext = nextPrayer?.time == time.format(formatter) && isSelectedDayToday

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
        // Center Content
        Box(modifier = Modifier.align(Alignment.Center)) {
            centerContent()
        }
    }
}