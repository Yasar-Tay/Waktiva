package com.ybugmobile.waktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
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
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val layoutDirection = LocalLayoutDirection.current
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

    /**
     * Maps a given time to coordinates on the circle's circumference.
     */
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
            PrayerNodeInfo(PrayerType.FAJR, day.timings[PrayerType.FAJR] ?: LocalTime.MIN, Color(0xFF81D4FA), fajrPainter),
            PrayerNodeInfo(PrayerType.SUNRISE, day.timings[PrayerType.SUNRISE] ?: LocalTime.MIN, Color(0xFFFFE082), sunrisePainter),
            PrayerNodeInfo(PrayerType.DHUHR, day.timings[PrayerType.DHUHR] ?: LocalTime.MIN, Color(0xFFFFF59D), dhuhrPainter),
            PrayerNodeInfo(PrayerType.ASR, day.timings[PrayerType.ASR] ?: LocalTime.MIN, Color(0xFFFFCC80), asrPainter),
            PrayerNodeInfo(PrayerType.MAGHRIB, day.timings[PrayerType.MAGHRIB] ?: LocalTime.MIN, Color(0xFFCE93D8), maghribPainter),
            PrayerNodeInfo(PrayerType.ISHA, day.timings[PrayerType.ISHA] ?: LocalTime.MIN, Color(0xFF9FA8DA), ishaPainter)
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

    // Pre-measure time labels to avoid layout phase overhead in draw loop
    val measuredTimeLabels = remember(prayers, contentColor, isLandscape) {
        prayers.map { prayer ->
            textMeasurer.measure(
                text = AnnotatedString(prayer.time.format(formatter)),
                style = TextStyle(
                    color = contentColor.copy(alpha = 0.5f),
                    fontSize = (if (isLandscape) 8.sp else 10.sp),
                    fontFamily = IBMPlexArabic,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    // Individual animations for tapped nodes
    val prayerScales = prayers.map { prayer ->
        animateFloatAsState(
            targetValue = if (selectedInfo?.id == prayer.type.name) 1.25f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "pScale"
        )
    }

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
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(day, currentTime, isSelectedDayToday, canvasSize, layoutDirection) {
                    // Interaction: Detect taps on current time indicator or prayer nodes
                    detectTapGestures { tapOffset ->
                        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
                        val radius = canvasSize.width / 2 - 24.dp.toPx()
                        var hit: DetailedInfo? = null
                        
                        if (isSelectedDayToday) {
                            val currentPos = getPosition(currentTime, radius, center)
                            if ((tapOffset - currentPos).getDistance() <= 36.dp.toPx()) {
                                hit = DetailedInfo(
                                    "CURRENT", context.getString(R.string.home_current_time),
                                    currentTime.format(formatter), currentPrayerColor, Icons.Default.AccessTime
                                )
                            }
                        }

                        if (hit == null) {
                            for (prayer in prayers) {
                                val pos = getPosition(prayer.time, radius, center)
                                if ((tapOffset - pos).getDistance() <= 32.dp.toPx()) {
                                    hit = DetailedInfo(
                                        prayer.type.name, prayer.type.getDisplayName(context),
                                        prayer.time.format(formatter), prayer.color, 
                                        when(prayer.type) {
                                            PrayerType.FAJR -> fajrIcon
                                            PrayerType.SUNRISE -> sunriseIcon
                                            PrayerType.DHUHR -> dhuhrIcon
                                            PrayerType.ASR -> asrIcon
                                            PrayerType.MAGHRIB -> maghribIcon
                                            PrayerType.ISHA -> ishaIcon
                                        }
                                    )
                                    break
                                }
                            }
                        }
                        selectedInfo = hit
                    }
                }
                .drawWithCache {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2 - 24.dp.toPx()
                    val sunriseMinutes = sunrise.hour * 60 + sunrise.minute
                    val sunsetMinutes = sunset.hour * 60 + sunset.minute
                    
                    // Arc angles derived from sunrise and sunset
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
                        // Background Decor: Ripples
                        val rippleProgress = (gravityFlux) % 1f
                        drawCircle(
                            color = contentColor.copy(alpha = 0.04f * (1f - rippleProgress)),
                            radius = radius * (0.4f + rippleProgress * 1.4f),
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        // Stellar rotation ring
                        withTransform({ rotate(stellarRotation, center) }) {
                            drawCircle(
                                brush = Brush.sweepGradient(listOf(contentColor.copy(0f), contentColor.copy(0.12f), contentColor.copy(0f))),
                                radius = radius + (if (isLandscape) 10.dp else 14.dp).toPx(),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 12f), 0f))
                            )
                        }

                        // Static clock rings and day arc
                        drawCircle(contentColor.copy(0.05f), radius, center, style = Stroke(1.dp.toPx()))
                        drawArc(arcBrush, startAngle, sweepAngle, false, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2), style = Stroke((if (isLandscape) 3.dp else 4.dp).toPx(), cap = StrokeCap.Round))
                        
                        // Hourly ticks
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

            // Time hand indicator
            if (isSelectedDayToday) {
                withTransform({ rotate(rotationAngle, center) }) {
                    drawLine(Brush.verticalGradient(listOf(currentPrayerColor.copy(0.3f), Color.Transparent), startY = center.y - radius, endY = center.y), Offset(center.x, center.y), Offset(center.x, center.y - radius + 10.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
                    drawLine(Brush.verticalGradient(listOf(Color.White.copy(0.7f), Color.Transparent), startY = center.y - radius, endY = center.y - radius * 0.4f), Offset(center.x, center.y - radius * 0.4f), Offset(center.x, center.y - radius + 14.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                }
            }

            // Draw individual prayer nodes (markers)
            prayers.forEachIndexed { index, prayer ->
                // Apply subtle breathing to node radius
                val pullRadius = radius * (1f - (0.02f * gravityFlux))
                val pos = getPosition(prayer.time, pullRadius, center)
                
                val isCurrent = prayer.type == currentPrayerType && isSelectedDayToday
                val scale = prayerScales[index].value

                withTransform({ scale(scale, scale, pos) }) {
                    val markerRadius = if (isCurrent) (if (isLandscape) 11.dp else 14.dp).toPx() else (if (isLandscape) 9.dp else 12.dp).toPx()
                    val iconSize = if (isCurrent) (if (isLandscape) 13.dp else 16.dp).toPx() else (if (isLandscape) 11.dp else 14.dp).toPx()

                    // Glow effect for currently active prayer marker
                    if (isCurrent) {
                        drawCircle(
                            color = prayer.color.copy(alpha = 0.15f * pulseScale),
                            radius = markerRadius * 1.8f,
                            center = pos
                        )
                    }

                    // Base circle for the marker
                    drawCircle(
                        color = prayer.color,
                        radius = markerRadius,
                        center = pos
                    )

                    // Icon placement within the marker
                    val iconTint = if (prayer.color.luminance() > 0.5f) Color.Black.copy(0.7f) else Color.White
                    translate(pos.x - iconSize / 2, pos.y - iconSize / 2) {
                        with(prayer.painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(iconTint)) }
                    }
                    
                    // Pre-measured time labels rendered below markers
                    val textResult = measuredTimeLabels[index]
                    drawText(textResult, topLeft = Offset(pos.x - textResult.size.width / 2, pos.y + (if (isLandscape) 14.dp else 18.dp).toPx()))
                }
            }

            // Current time dot/indicator
            if (isSelectedDayToday) {
                val currentPos = getPosition(currentTime, radius, center)
                drawCircle(Brush.radialGradient(listOf(currentPrayerColor.copy(0.3f), Color.Transparent), currentPos, 12.dp.toPx()), 12.dp.toPx(), currentPos)
                val spikeLen = 6.dp.toPx()
                drawLine(Color.White.copy(0.6f), Offset(currentPos.x - spikeLen, currentPos.y), Offset(currentPos.x + spikeLen, currentPos.y), 1.2.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(0.6f), Offset(currentPos.x, currentPos.y - spikeLen), Offset(currentPos.x, currentPos.y + spikeLen), 1.2.dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White, (if (isLandscape) 2.5.dp else 3.5.dp).toPx(), currentPos)
                drawCircle(currentPrayerColor, (if (isLandscape) 4.5.dp else 5.5.dp).toPx(), currentPos, style = Stroke(1.5.dp.toPx()))
            }
        }

        // Center Content: Calendar Card and Religious Badge
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

        // Top Content: Header for current prayer status
        CurrentPrayerHeader(currentPrayer, contentColor, currentPrayerColor)

        // Interactive Tooltip: Appears when a prayer node is tapped
        Box(modifier = Modifier.fillMaxSize().zIndex(10f), contentAlignment = Alignment.BottomCenter) {
            AnimatedContent(
                targetState = selectedInfo,
                transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.8f)) },
                label = "info",
                modifier = Modifier.padding(bottom = 20.dp)
            ) { info -> if (info != null) InfoGlassCard(info) }
        }
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
    val containerColor = remember(glassTheme.isLightMode) { if (glassTheme.isLightMode) Color.White.copy(0.22f) else Color.Black.copy(0.25f) }
    val borderColor = remember(glassTheme.isLightMode) { if (glassTheme.isLightMode) Color.White.copy(0.45f) else Color.White.copy(0.15f) }
    val cardShape = RoundedCornerShape(percent = 50)

    Surface(
        color = containerColor,
        shape = cardShape,
        modifier = Modifier
            .wrapContentSize()
            .height(if (isLandscape) 40.dp else 48.dp)
            .clip(cardShape) 
            .drawWithContent {
                drawContent()
                // Card accent gradient
                drawRoundRect(Brush.horizontalGradient(listOf(info.color.copy(0.15f), Color.Transparent), endX = size.width * 0.4f), size = size, cornerRadius = CornerRadius(size.height / 2), blendMode = BlendMode.Screen)
                drawRoundRect(borderColor, size = size, cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx()))
            },
        contentColor = Color.White
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon Pill on the left
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .background(info.color.copy(0.9f)), 
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    info.icon, 
                    null, 
                    tint = if (info.color.luminance() > 0.5f) Color.Black else Color.White, 
                    modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                )
            }
            
            // Info text on the right
            Column(
                modifier = Modifier.padding(start = if (isLandscape) 12.dp else 16.dp, end = if (isLandscape) 16.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy((-4).dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = info.title, 
                    style = TextStyle(
                        fontSize = if (isLandscape) 11.sp else 13.sp,
                        lineHeight = if (isLandscape) 11.sp else 13.sp,
                        fontWeight = FontWeight.Bold, 
                        color = Color.White.copy(0.6f), 
                        letterSpacing = 0.4.sp
                    )
                )
                Text(
                    text = info.time, 
                    style = TextStyle(
                        fontSize = if (isLandscape) 15.sp else 17.sp,
                        lineHeight = if (isLandscape) 15.sp else 17.sp,
                        fontFamily = IBMPlexArabic, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White, 
                        letterSpacing = (-0.3).sp
                    )
                )
            }
        }
    }
}

/** Metadata for a single prayer point on the circle. */
data class PrayerNodeInfo(val type: PrayerType, val time: LocalTime, val color: Color, val painter: androidx.compose.ui.graphics.vector.VectorPainter)

/** State for the interactive information card. */
data class DetailedInfo(val id: String, val title: String, val time: String, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)
