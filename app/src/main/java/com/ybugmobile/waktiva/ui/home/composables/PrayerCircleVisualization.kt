package com.ybugmobile.waktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.CurrentPrayer
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.desaturate
import com.ybugmobile.waktiva.ui.theme.darken
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * A sophisticated circular visualization of the day's prayer times.
 * Features interactive nodes, dynamic weather-based color adjustments,
 * and high-fidelity astronomical animations.
 *
 * @param day The prayer data for the selected day.
 * @param currentTime Current system time for accurate indicator placement.
 * @param nextPrayer Information about the upcoming prayer event.
 * @param currentPrayer Information about the active prayer period.
 * @param isSelectedDayToday Flag indicating if the view is focused on the current date.
 * @param isHijriVisible Toggle for Hijri calendar display.
 * @param onToggleHijri Callback for calendar switch.
 * @param contentColor Base color for text and icons.
 * @param isMuted Whether the audio for the next prayer is silenced.
 * @param playAdhanAudio General preference for adhan playback.
 * @param onSkipAudio Callback for muting the next specific prayer audio.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PrayerCircleVisualization(
    day: PrayerDay,
    currentTime: LocalTime,
    nextPrayer: NextPrayer?,
    currentPrayer: CurrentPrayer?,
    isSelectedDayToday: Boolean,
    isHijriVisible: Boolean = false,
    onToggleHijri: () -> Unit = {},
    contentColor: Color = Color.White,
    isMuted: Boolean = false,
    playAdhanAudio: Boolean = false,
    onSkipAudio: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val glassTheme = LocalGlassTheme.current
    val weatherCondition = glassTheme.weatherCondition

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var selectedInfo by remember { mutableStateOf<DetailedInfo?>(null) }

    // Auto-dismiss interaction card
    LaunchedEffect(selectedInfo) {
        if (selectedInfo != null) {
            delay(4000)
            selectedInfo = null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "celestial")

    // Gravitational Flux: Subtle periodic movement of prayer markers (breathing effect)
    val gravityFlux by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gravityFlux"
    )

    // Slow background rotation to simulate stellar movement
    val stellarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stellarRotation"
    )

    // Pulse effect applied to the entire container if viewing "Today"
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSelectedDayToday) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Smooth indicator rotation synchronized with current time
    val rotationAngle by animateFloatAsState(
        targetValue = if (isSelectedDayToday) {
            val totalMinutes = currentTime.hour * 60 + currentTime.minute
            val progressAngle = (totalMinutes.toFloat() / (24 * 60)) * 360f
            if (layoutDirection == LayoutDirection.Rtl) -progressAngle + 180f else progressAngle + 180f
        } else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

    val sunrise = remember(day) { day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 0) }
    val sunset = remember(day) { day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 0) }

    // Load painters once to avoid re-allocation in the draw loop
    val fajrIcon = ImageVector.vectorResource(R.drawable.haze_day_rotated)
    val sunriseIcon = ImageVector.vectorResource(R.drawable.sunrise)
    val dhuhrIcon = ImageVector.vectorResource(R.drawable.clear_day)
    val asrIcon = ImageVector.vectorResource(R.drawable.clear_day)
    val maghribIcon = ImageVector.vectorResource(R.drawable.sunset)
    val ishaIcon = ImageVector.vectorResource(R.drawable.clear_night)

    val fajrPainter = rememberVectorPainter(fajrIcon)
    val sunrisePainter = rememberVectorPainter(sunriseIcon)
    val dhuhrPainter = rememberVectorPainter(dhuhrIcon)
    val asrPainter = rememberVectorPainter(asrIcon)
    val maghribPainter = rememberVectorPainter(maghribIcon)
    val ishaPainter = rememberVectorPainter(ishaIcon)

    // Process prayer nodes with adaptive styling based on weather
    val prayers = remember(day, fajrPainter, sunrisePainter, dhuhrPainter, asrPainter, maghribPainter, ishaPainter, weatherCondition) {
        val basePrayers = listOf(
            PrayerNodeInfo(PrayerType.FAJR, day.timings[PrayerType.FAJR] ?: LocalTime.MIN, Color(0xFF81D4FA), fajrPainter, fajrIcon),
            PrayerNodeInfo(PrayerType.SUNRISE, day.timings[PrayerType.SUNRISE] ?: LocalTime.MIN, Color(0xFFFFE082), sunrisePainter, sunriseIcon),
            PrayerNodeInfo(PrayerType.DHUHR, day.timings[PrayerType.DHUHR] ?: LocalTime.MIN, Color(0xFFFFF59D), dhuhrPainter, dhuhrIcon),
            PrayerNodeInfo(PrayerType.ASR, day.timings[PrayerType.ASR] ?: LocalTime.MIN, Color(0xFFFFCC80), asrPainter, asrIcon),
            PrayerNodeInfo(PrayerType.MAGHRIB, day.timings[PrayerType.MAGHRIB] ?: LocalTime.MIN, Color(0xFFCE93D8), maghribPainter, maghribIcon),
            PrayerNodeInfo(PrayerType.ISHA, day.timings[PrayerType.ISHA] ?: LocalTime.MIN, Color(0xFF9FA8DA), ishaPainter, ishaIcon)
        )

        val isCloudy = weatherCondition != WeatherCondition.CLEAR && weatherCondition != WeatherCondition.UNKNOWN
        val isSevere = weatherCondition == WeatherCondition.RAINY ||
                weatherCondition == WeatherCondition.THUNDERSTORM ||
                weatherCondition == WeatherCondition.SNOWY

        if (isCloudy) {
            val desaturateAmount = if (isSevere) 0.35f else 0.2f
            val darkenAmount = if (isSevere) 0.2f else 0.1f
            basePrayers.map { it.copy(color = it.color.desaturate(desaturateAmount).darken(darkenAmount)) }
        } else {
            basePrayers
        }
    }

    // Determine currently active prayer period
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

    val currentPrayerColor = remember(currentPrayerType, prayers) {
        prayers.find { it.type == currentPrayerType }?.color ?: Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { selectedInfo = null }
                }
                .drawWithCache {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2 - 24.dp.toPx()
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

                    val arcBrush = Brush.sweepGradient(
                        0.0f to Color(0xFFFFE082).copy(alpha = 0.4f),
                        0.5f to Color(0xFFFFB74D).copy(alpha = 0.4f),
                        1.0f to Color(0xFFCE93D8).copy(alpha = 0.4f),
                        center = center
                    )

                    onDrawBehind {
                        val rippleProgress = (gravityFlux) % 1f
                        drawCircle(
                            color = contentColor.copy(alpha = 0.04f * (1f - rippleProgress)),
                            radius = radius * (0.4f + rippleProgress * 1.4f),
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        withTransform({ rotate(stellarRotation, center) }) {
                            drawCircle(
                                brush = Brush.sweepGradient(listOf(contentColor.copy(0f), contentColor.copy(0.12f), contentColor.copy(0f))),
                                radius = radius + (if (isLandscape) 10.dp else 14.dp).toPx(),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 12f), 0f))
                            )
                        }

                        drawCircle(contentColor.copy(0.05f), radius, center, style = Stroke(1.dp.toPx()))
                        drawArc(arcBrush, startAngle, sweepAngle, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke((if (isLandscape) 3.dp else 4.dp).toPx(), cap = StrokeCap.Round))

                        for (i in 0 until 24) {
                            val angle = i * 15f + 90f
                            val angleRad = Math.toRadians(angle.toDouble())
                            val isMajor = i % 6 == 0
                            val tickLen = if (isMajor) (if (isLandscape) 8.dp else 10.dp).toPx() else 4.dp.toPx()
                            val inner = radius - tickLen / 2
                            val outer = radius + tickLen / 2
                            drawLine(if (isMajor) contentColor.copy(0.4f) else contentColor.copy(0.1f), Offset(center.x + inner * cos(angleRad).toFloat(), center.y + inner * sin(angleRad).toFloat()), Offset(center.x + outer * cos(angleRad).toFloat(), center.y + outer * sin(angleRad).toFloat()), (if (isMajor) 1.5.dp else 1.dp).toPx())
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 24.dp.toPx()

            if (isSelectedDayToday) {
                withTransform({ rotate(rotationAngle, center) }) {
                    drawLine(Brush.verticalGradient(listOf(currentPrayerColor.copy(0.3f), Color.Transparent), startY = center.y - radius, endY = center.y), Offset(center.x, center.y), Offset(center.x, center.y - radius + 10.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
                    drawLine(Brush.verticalGradient(listOf(Color.White.copy(0.7f), Color.Transparent), startY = center.y - radius, endY = center.y - radius * 0.4f), Offset(center.x, center.y - radius * 0.4f), Offset(center.x, center.y - radius + 14.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                }

                val currentPos = getPosition(currentTime, radius, center, layoutDirection)
                drawCircle(Brush.radialGradient(listOf(currentPrayerColor.copy(0.3f), Color.Transparent), currentPos, 12.dp.toPx()), 12.dp.toPx(), currentPos)
                val spikeLen = 6.dp.toPx()
                drawLine(Color.White.copy(0.6f), Offset(currentPos.x - spikeLen, currentPos.y), Offset(currentPos.x + spikeLen, currentPos.y), 1.2.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(0.6f), Offset(currentPos.x, currentPos.y - spikeLen), Offset(currentPos.x, currentPos.y + spikeLen), 1.2.dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White, (if (isLandscape) 2.5.dp else 3.5.dp).toPx(), currentPos)
                drawCircle(currentPrayerColor, (if (isLandscape) 4.5.dp else 5.5.dp).toPx(), currentPos, style = Stroke(1.5.dp.toPx()))
            }
        }

        if (canvasSize != Size.Zero) {
            val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
            val radius = with(density) { canvasSize.width / 2 - 24.dp.toPx() }
            prayers.forEach { prayer ->
                PrayerMarker(
                    prayer = prayer,
                    isSelected = selectedInfo?.id == prayer.type.name,
                    isCurrent = prayer.type == currentPrayerType && isSelectedDayToday,
                    onTap = {
                        selectedInfo = if (selectedInfo?.id == prayer.type.name) null else {
                            DetailedInfo(
                                prayer.type.name, prayer.type.getDisplayName(context),
                                prayer.time.format(formatter), prayer.color, prayer.icon
                            )
                        }
                    },
                    gravityFlux = gravityFlux,
                    center = center,
                    radius = radius,
                    layoutDirection = layoutDirection,
                    isLandscape = isLandscape,
                    contentColor = contentColor,
                    pulseScale = pulseScale,
                    formatter = formatter
                )
            }
        }

        Column(
            modifier = Modifier.zIndex(5f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FlippableCalendarCard(
                day = day,
                isHijriVisible = isHijriVisible,
                onFlip = onToggleHijri,
                contentColor = contentColor,
                accentColor = currentPrayerColor,
                currentTime = currentTime,
                isSelectedDayToday = isSelectedDayToday,
                pulseScale = pulseScale
            )
            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))
            ReligiousBadge(day.date, contentColor, hijriDate = day.hijriDate)
        }

        CurrentPrayerHeader(currentPrayer, contentColor, currentPrayerColor)
    }
}

/**
 * A floating "glass" card providing details about a selected prayer node.
 */
@Composable
fun InfoGlassCard(info: DetailedInfo) {
    val glassTheme = LocalGlassTheme.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val bgColor = if (glassTheme.isLightMode) Color.White.copy(0.18f) else Color.Black.copy(0.42f)
    val borderColor = if (glassTheme.isLightMode) Color.White.copy(0.45f) else Color.White.copy(0.1f)
    val cardShape = RoundedCornerShape(18.dp)

    val cardHeight = if (isLandscape) 36.dp else 50.dp
    val iconSize = if (isLandscape) 12.dp else 15.dp
    val nameFontSize = if (isLandscape) 7.sp else 8.sp
    val timeFontSize = if (isLandscape) 13.sp else 18.sp

    Surface(
        color = bgColor,
        shape = cardShape,
        modifier = Modifier
            .wrapContentWidth()
            .height(cardHeight)
            .drawWithContent {
                drawContent()
                // Soft color wash bleeding from left
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to info.color.copy(alpha = 0.18f),
                        0.45f to Color.Transparent
                    ),
                    size = size,
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
                // Hair-line border
                drawRoundRect(
                    color = borderColor,
                    size = size,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(0.75.dp.toPx())
                )
            },
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thin vertical accent bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.5.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, info.color.copy(0.85f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                    )
            )

            Spacer(Modifier.width(if (isLandscape) 10.dp else 12.dp))

            // Icon tinted in prayer color, no background box
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = info.color,
                modifier = Modifier.size(iconSize)
            )

            Spacer(Modifier.width(if (isLandscape) 8.dp else 10.dp))

            // Prayer name + time stacked
            Column(
                modifier = Modifier.padding(end = if (isLandscape) 14.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy((-1).dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = info.title.uppercase(),
                    style = TextStyle(
                        fontSize = nameFontSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(0.42f),
                        letterSpacing = 1.5.sp
                    )
                )
                Text(
                    text = info.time,
                    style = TextStyle(
                        fontSize = timeFontSize,
                        fontFamily = IBMPlexArabic,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
        }
    }
}

/** Metadata for a single prayer point on the circle. */
data class PrayerNodeInfo(
    val type: PrayerType,
    val time: LocalTime,
    val color: Color,
    val painter: androidx.compose.ui.graphics.vector.VectorPainter,
    val icon: ImageVector
)

/** State for the interactive information card. */
data class DetailedInfo(val id: String, val title: String, val time: String, val color: Color, val icon: ImageVector)

private fun getPosition(time: LocalTime, radius: Float, center: Offset, layoutDirection: LayoutDirection): Offset {
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

@Composable
private fun PrayerMarker(
    prayer: PrayerNodeInfo,
    isSelected: Boolean,
    isCurrent: Boolean,
    onTap: () -> Unit,
    gravityFlux: Float,
    center: Offset,
    radius: Float,
    layoutDirection: LayoutDirection,
    isLandscape: Boolean,
    contentColor: Color,
    pulseScale: Float,
    formatter: DateTimeFormatter
) {
    // Scale animation for tapping
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isSelected) 20f else 5f)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val pullRadius = radius * (1f - (0.02f * gravityFlux))
                    val pos = getPosition(prayer.time, pullRadius, center, layoutDirection)
                    translationX = pos.x - center.x
                    translationY = pos.y - center.y
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            AnimatedContent(
                targetState = isSelected,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.8f))
                },
                label = "marker_transform"
            ) { selected ->
                if (selected) {
                    InfoGlassCard(
                        DetailedInfo(
                            prayer.type.name,
                            prayer.type.getDisplayName(LocalContext.current),
                            prayer.time.format(formatter),
                            prayer.color,
                            prayer.icon
                        )
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .wrapContentSize(unbounded = true)
                            .pointerInput(prayer) {
                                detectTapGestures { onTap() }
                            }
                    ) {
                        val markerSize = if (isCurrent) (if (isLandscape) 22.dp else 28.dp) else (if (isLandscape) 18.dp else 24.dp)

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(markerSize * 1.8f)
                                    .background(prayer.color.copy(alpha = 0.15f * pulseScale), CircleShape)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(markerSize)
                                .background(prayer.color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val iconSize = if (isCurrent) (if (isLandscape) 13.dp else 16.dp) else (if (isLandscape) 11.dp else 14.dp)
                            val iconTint = if (prayer.color.luminance() > 0.5f) Color.Black.copy(0.7f) else Color.White
                            Icon(
                                painter = prayer.painter,
                                contentDescription = null,
                                modifier = Modifier.size(iconSize),
                                tint = iconTint
                            )
                        }

                        Text(
                            text = prayer.time.format(formatter),
                            style = TextStyle(
                                color = contentColor.copy(alpha = 0.5f),
                                fontSize = if (isLandscape) 8.sp else 10.sp,
                                fontFamily = IBMPlexArabic,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val yOffset = (if (isLandscape) 14.dp else 18.dp).toPx().toInt()
                                    layout(placeable.width, 0) {
                                        // Absolute placement to avoid RTL bias
                                        placeable.place(0, yOffset)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}
