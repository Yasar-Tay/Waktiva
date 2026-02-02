package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalAnimationApi::class)
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
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val layoutDirection = LocalLayoutDirection.current

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Selection State
    var selectedInfo by remember { mutableStateOf<DetailedInfo?>(null) }
    
    // Auto-dismiss selection after some time
    LaunchedEffect(selectedInfo) {
        if (selectedInfo != null) {
            delay(4000)
            selectedInfo = null
        }
    }

    // Pulse Animation for Active Prayer
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Smooth rotation for the clock handle
    val rotationAngle by animateFloatAsState(
        targetValue = if (isSelectedDayToday) {
            val totalMinutes = currentTime.hour * 60 + currentTime.minute
            val progressAngle = (totalMinutes.toFloat() / (24 * 60)) * 360f
            if (layoutDirection == LayoutDirection.Rtl) -progressAngle + 180f else progressAngle + 180f
        } else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

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
    val fajrIcon = ImageVector.vectorResource(R.drawable.water_lux_rotated)
    val fajrPainter = rememberVectorPainter(fajrIcon)
    val sunrisePainter = rememberVectorPainter(Icons.Default.WbTwilight)
    
    val prayers = remember(day) {
        listOf(
            PrayerInfo(PrayerType.FAJR, day.timings[PrayerType.FAJR] ?: LocalTime.MIN, Color(0xFF81D4FA), fajrPainter),
            PrayerInfo(PrayerType.SUNRISE, day.timings[PrayerType.SUNRISE] ?: LocalTime.MIN, Color(0xFFFFE082), sunrisePainter),
            PrayerInfo(PrayerType.DHUHR, day.timings[PrayerType.DHUHR] ?: LocalTime.MIN, Color(0xFFFFF59D), sunPainter),
            PrayerInfo(PrayerType.ASR, day.timings[PrayerType.ASR] ?: LocalTime.MIN, Color(0xFFFFCC80), sunPainter),
            PrayerInfo(PrayerType.MAGHRIB, day.timings[PrayerType.MAGHRIB] ?: LocalTime.MIN, Color(0xFFCE93D8), sunrisePainter),
            PrayerInfo(PrayerType.ISHA, day.timings[PrayerType.ISHA] ?: LocalTime.MIN, Color(0xFF9FA8DA), moonPainter)
        )
    }

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

    // Animated scales for prayer markers
    val prayerScales = prayers.map { prayer ->
        animateFloatAsState(
            targetValue = if (selectedInfo?.id == prayer.type.name) 1.25f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "prayerScale_${prayer.type.name}"
        )
    }

    // Animated scale for current time dot
    val currentTimeDotScale by animateFloatAsState(
        targetValue = if (selectedInfo?.id == "CURRENT") 1.4f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "currentTimeDotScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp),
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
                        
                        var hit: DetailedInfo? = null
                        
                        // Check Current Time Dot
                        if (isSelectedDayToday) {
                            val currentPos = getPosition(currentTime, radius, center)
                            if ((tapOffset - currentPos).getDistance() <= 32.dp.toPx()) {
                                hit = DetailedInfo(
                                    id = "CURRENT",
                                    title = context.getString(R.string.adhan_sounding),
                                    time = currentTime.format(formatter),
                                    color = currentPrayerColor,
                                    icon = Icons.Default.AccessTime
                                )
                            }
                        }

                        // Check Prayers
                        if (hit == null) {
                            for (prayer in prayers) {
                                val pos = getPosition(prayer.time, radius, center)
                                if ((tapOffset - pos).getDistance() <= 28.dp.toPx()) {
                                    hit = DetailedInfo(
                                        id = prayer.type.name,
                                        title = prayer.type.getDisplayName(context),
                                        time = prayer.time.format(formatter),
                                        color = prayer.color,
                                        icon = when(prayer.type) {
                                            PrayerType.FAJR -> fajrIcon
                                            PrayerType.SUNRISE -> Icons.Default.WbTwilight
                                            PrayerType.DHUHR -> Icons.Default.WbSunny
                                            PrayerType.ASR -> Icons.Default.WbSunny
                                            PrayerType.MAGHRIB -> Icons.Default.WbTwilight
                                            PrayerType.ISHA -> Icons.Default.NightsStay
                                        }
                                    )
                                    break
                                }
                            }
                        }
                        
                        selectedInfo = hit
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 20.dp.toPx()

            // 1. Background Track
            drawCircle(
                color = contentColor.copy(alpha = 0.08f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Hour ticks
            for (i in 0 until 24) {
                val angle = i * 15f + 90f
                val angleRad = Math.toRadians(angle.toDouble())
                val isMajor = i % 6 == 0
                val tickLen = if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                val inner = radius - tickLen / 2
                val outer = radius + tickLen / 2
                
                drawLine(
                    color = contentColor.copy(alpha = if (isMajor) 0.3f else 0.15f),
                    start = Offset(center.x + inner * cos(angleRad).toFloat(), center.y + inner * sin(angleRad).toFloat()),
                    end = Offset(center.x + outer * cos(angleRad).toFloat(), center.y + outer * sin(angleRad).toFloat()),
                    strokeWidth = (if (isMajor) 1.5.dp else 1.dp).toPx()
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
            
            val sweepAngle = if (layoutDirection == LayoutDirection.Rtl) {
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
                    0.0f to Color(0xFFFFE082).copy(alpha = 0.25f),
                    0.5f to Color(0xFFFFB74D).copy(alpha = 0.25f),
                    1.0f to Color(0xFFCE93D8).copy(alpha = 0.25f),
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3. Current Time Clock Handle
            if (isSelectedDayToday) {
                withTransform({
                    rotate(rotationAngle, center)
                }) {
                    drawLine(
                        brush = Brush.verticalGradient(
                            colors = listOf(currentPrayerColor, currentPrayerColor.copy(alpha = 0f)),
                            startY = center.y - radius,
                            endY = center.y
                        ),
                        start = Offset(center.x, center.y),
                        end = Offset(center.x, center.y - radius + 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 4. Prayer Markers
            prayers.forEachIndexed { index, (type, time, color, painter) ->
                val pos = getPosition(time, radius, center)
                val isCurrent = type == currentPrayerType && isSelectedDayToday
                
                val scale = prayerScales[index].value

                withTransform({ scale(scale, scale, pos) }) {
                    val markerRadius = if (isCurrent) 15.dp.toPx() else 13.dp.toPx()
                    val iconSize = if (isCurrent) 18.dp.toPx() else 15.dp.toPx()

                    if (isCurrent) {
                        drawCircle(
                            color = color.copy(alpha = 0.15f),
                            radius = markerRadius * 2.2f * pulseScale,
                            center = pos
                        )
                    }
                    
                    if (scale > 1f) {
                        drawCircle(
                            color = color.copy(alpha = 0.3f),
                            radius = markerRadius * 1.8f,
                            center = pos
                        )
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color, color.darken(0.3f)),
                            center = pos,
                            radius = markerRadius
                        ),
                        radius = markerRadius,
                        center = pos
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = markerRadius,
                        center = pos,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    translate(pos.x - iconSize / 2, pos.y - iconSize / 2) {
                        with(painter) {
                            draw(
                                size = Size(iconSize, iconSize),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }

                }
            }

            // 5. Current Time Dot
            if (isSelectedDayToday) {
                val currentPos = getPosition(currentTime, radius, center)
                val scale = currentTimeDotScale

                withTransform({ scale(scale, scale, currentPos) }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(currentPrayerColor.copy(alpha = 0.5f), Color.Transparent),
                            center = currentPos,
                            radius = 18.dp.toPx()
                        ),
                        radius = 18.dp.toPx(),
                        center = currentPos
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = currentPos
                    )
                    
                    drawCircle(
                        color = currentPrayerColor,
                        radius = 7.dp.toPx(),
                        center = currentPos,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // 6. Prayer Time Text
            prayers.forEach { prayer ->
                val prayerTime = prayer.time.format(formatter)
                val pos = getPosition(prayer.time, radius, center)
                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(prayerTime),
                    style = TextStyle(
                        color = contentColor.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                val textOffset = Offset(
                    x = pos.x - textLayoutResult.size.width / 2,
                    y = pos.y + 18.dp.toPx()
                )

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = textOffset
                )
            }
        }

        // Center Content Overlay
        centerContent(currentPrayerColor)

        // 7. Modern Sleek Info Overlay (Bottom Centered) - High Z-Index to avoid being hidden
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedContent(
                targetState = selectedInfo,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(300)))
                        .togetherWith(slideOutVertically { it / 2 } + fadeOut(tween(200)))
                },
                label = "info_card"
            ) { info ->
                if (info != null) {
                    InfoGlassCard(info)
                }
            }
        }
    }
}

@Composable
fun InfoGlassCard(info: DetailedInfo) {
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
        modifier = Modifier
            .wrapContentWidth()
            .height(52.dp)
            .drawWithContent {
                drawContent()
                // Subtle top highlight border
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    size = size,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            },
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(info.color.copy(alpha = 0.25f), CircleShape)
                    .drawWithContent {
                        drawContent()
                        drawCircle(info.color, radius = size.minDimension / 2, style = Stroke(2.dp.toPx()))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = info.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = info.title,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = info.time,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
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

data class DetailedInfo(
    val id: String,
    val title: String,
    val time: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
