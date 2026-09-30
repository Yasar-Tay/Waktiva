package com.ybugmobile.waktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.CurrentPrayer
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.ui.home.composables.gear.GearLight
import com.ybugmobile.waktiva.ui.home.composables.gear.GearPlaque
import com.ybugmobile.waktiva.ui.home.composables.gear.GearPrayer
import com.ybugmobile.waktiva.ui.home.composables.gear.GearPalette
import com.ybugmobile.waktiva.ui.home.composables.gear.WeatherTone
import com.ybugmobile.waktiva.ui.home.composables.gear.dayAngle
import com.ybugmobile.waktiva.ui.home.composables.gear.halo
import com.ybugmobile.waktiva.ui.home.composables.gear.haloRing
import com.ybugmobile.waktiva.ui.home.composables.gear.nowIndicator
import com.ybugmobile.waktiva.ui.home.composables.gear.pointOn
import com.ybugmobile.waktiva.ui.home.composables.gear.rememberEasedAngle
import com.ybugmobile.waktiva.ui.home.composables.gear.goldBezel
import com.ybugmobile.waktiva.ui.home.composables.gear.toDegrees
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sign

/**
 * The classic day circle: a slim enamel ring in the prayer colours, framed by gold hairlines and
 * lifted off the sky by a soft halo. Each prayer is a flat enamel badge on the ring and the date
 * sits on a round glass card in a gold bezel at the centre.
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
 * @param sunLight Screen angle (radians) the sunlight falls from, which the ring's sheen follows,
 * or null for the default light.
 * @param prayedPrayers Prayers marked in the prayer log, whose badges glow; null until it has loaded.
 * @param onPrayerTap Called with a tapped prayer; returns true when it handled the tap, otherwise
 * the badge shows its info card.
 * @param sky The day's sky, painted inside the ring; null for none.
 * @param prayerWeather Each prayer's weather, shown as an icon beside its time and on its card.
 * @param badgeWeather The weather each badge is toned for, where it differs from the screen's.
 * @param temperatureRange The day's range of temperatures, shown on the date card.
 */
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
    onSkipAudio: (String) -> Unit = {},
    sunLight: Float? = null,
    prayedPrayers: Set<PrayerType>? = null,
    onPrayerTap: (PrayerType) -> Boolean = { false },
    sky: DaySky? = null,
    prayerWeather: Map<PrayerType, PrayerWeather> = emptyMap(),
    badgeWeather: Map<PrayerType, WeatherCondition> = emptyMap(),
    temperatureRange: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val weatherCondition = LocalGlassTheme.current.weatherCondition
    val palette = remember(weatherCondition) { GearPalette(WeatherTone.forScenery(weatherCondition)) }
    val lightAngle = rememberEasedAngle(sunLight ?: GearLight.DEFAULT_ANGLE)

    var selected by remember { mutableStateOf<PrayerType?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val weatherLines = prayerWeather.mapValues { (_, w) ->
        context.getString(w.condition.nameRes).uppercase(locale) to w.temperature?.degrees()
    }
    val currentOnPrayerTap by rememberUpdatedState(onPrayerTap)
    // The plaque's gemstone never turns; it only needs the phase the gear medallions take.
    val stillPhase = remember { mutableFloatStateOf(0f) }

    // Auto-dismiss interaction card
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(4000)
            selected = null
        }
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
    val prayers = remember(day, fajrPainter, sunrisePainter, dhuhrPainter, asrPainter, maghribPainter, ishaPainter, weatherCondition, badgeWeather) {
        val basePrayers = listOf(
            PrayerNodeInfo(PrayerType.FAJR, day.timings[PrayerType.FAJR] ?: LocalTime.MIN, Color(0xFF81D4FA), fajrPainter, fajrIcon),
            PrayerNodeInfo(PrayerType.SUNRISE, day.timings[PrayerType.SUNRISE] ?: LocalTime.MIN, Color(0xFFFFE082), sunrisePainter, sunriseIcon),
            PrayerNodeInfo(PrayerType.DHUHR, day.timings[PrayerType.DHUHR] ?: LocalTime.MIN, Color(0xFFFFF59D), dhuhrPainter, dhuhrIcon),
            PrayerNodeInfo(PrayerType.ASR, day.timings[PrayerType.ASR] ?: LocalTime.MIN, Color(0xFFFFCC80), asrPainter, asrIcon),
            PrayerNodeInfo(PrayerType.MAGHRIB, day.timings[PrayerType.MAGHRIB] ?: LocalTime.MIN, Color(0xFFCE93D8), maghribPainter, maghribIcon),
            PrayerNodeInfo(PrayerType.ISHA, day.timings[PrayerType.ISHA] ?: LocalTime.MIN, Color(0xFF9FA8DA), ishaPainter, ishaIcon)
        )

        // Toned for the weather in each prayer's own hour where it's known, else the screen's.
        basePrayers.map { it.copy(color = WeatherTone.forPrayers(badgeWeather[it.type] ?: weatherCondition)(it.color)) }
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

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(contentColor, isLandscape) {
        TextStyle(
            color = contentColor.copy(alpha = 0.62f),
            fontSize = if (isLandscape) 8.sp else 10.sp,
            fontFamily = IBMPlexArabic,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 4f)
        )
    }

    // Square that fits the parent's smaller dimension. fillMaxWidth() here would pin the width
    // and force the square taller than a short parent, overflowing it on wide (tablet) screens.
    BoxWithConstraints(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val sizePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val ring = remember(sizePx, density.density) { HaloRing(sizePx, density.density) }
        val nowMinutes = (currentTime.hour * 60 + currentTime.minute).toFloat()

        // The day's sky fills the ring, under everything else.
        sky?.let {
            Spacer(Modifier.fillMaxSize().daySky(it, radius = ring.track - ring.trackWidth / 2f))
            SkyPrecipitation(it, radius = ring.track - ring.trackWidth / 2f)
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { selected = null } }
                .drawWithCache {
                    val light = GearLight(lightAngle.value)
                    val labels = prayers.map { textMeasurer.measure(it.time.format(formatter), labelStyle) }
                    onDrawBehind {
                        drawRing(ring, prayers, palette, light, currentPrayerColor, isRtl)

                        if (isSelectedDayToday) {
                            // The hand starts at the bezel, so it doesn't show through the glass date card.
                            val handAngle = dayAngle(nowMinutes, isRtl)
                            val root = pointOn(ring.center, ring.bezelRadius, handAngle)
                            val tip = pointOn(ring.center, ring.track - size.minDimension * 0.03f, handAngle)
                            drawLine(
                                Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.75f)),
                                    start = root,
                                    end = tip
                                ),
                                root, tip, 1.5.dp.toPx(), StrokeCap.Round
                            )
                            nowIndicator(ring.center, ring.track, handAngle, currentPrayerColor, density.density)
                        }

                        prayers.forEachIndexed { i, prayer ->
                            val theta = dayAngle(prayer.time.minutes(), isRtl)
                            val isCurrent = isSelectedDayToday && prayer.type == currentPrayerType
                            val radius = if (isCurrent) ring.badgeCurrent else ring.badge
                            prayerBadge(prayer, pointOn(ring.center, ring.track, theta), radius, isCurrent, palette)
                            val label = labels[i]
                            val at = pointOn(ring.center, ring.labelDistance(radius), theta)
                            drawText(label, topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height / 2f))
                        }
                    }
                }
        )

        // Each prayer's weather beside its time.
        if (prayerWeather.isNotEmpty()) {
            val iconSize = if (isLandscape) 13.dp else 17.dp
            val label = remember(textMeasurer, labelStyle) { textMeasurer.measure("00:00", labelStyle).size }
            val offsets = remember(prayers, ring, label, isRtl, iconSize) {
                val minutes = prayers.map { it.time.minutes() }
                prayers.mapIndexed { i, p ->
                    p.type to weatherIconOffset(
                        minutes = minutes,
                        index = i,
                        labelRadius = ring.labelDistance(ring.badge),
                        labelHalfWidth = label.width / 2f,
                        labelHalfHeight = label.height / 2f,
                        iconSize = with(density) { iconSize.toPx() },
                        gap = with(density) { 3.dp.toPx() },
                        rtl = isRtl
                    )
                }.toMap()
            }
            PrayerWeatherIcons(prayerWeather, offsets, iconSize)
        }

        // Invisible tap targets over the badges; a tapped badge turns into its night plaque.
        val plaquePrayers = remember(prayers) {
            prayers.associate { p ->
                p.type to GearPrayer(p.type, p.time.minutes(), p.color, p.painter, p.icon, p.time.format(formatter))
            }
        }
        val targetSize = with(density) { (ring.badgeCurrent * 2).toDp() }.coerceAtLeast(32.dp)
        val edge = with(density) { 4.dp.toPx() }
        prayers.forEach { prayer ->
            key(prayer.type) {
                val isSelected = selected == prayer.type
                val offset = pointOn(Offset.Zero, ring.track, dayAngle(prayer.time.minutes(), isRtl))
                var contentSize by remember { mutableStateOf(IntSize.Zero) }
                val isCurrent = isSelectedDayToday && prayer.type == currentPrayerType
                PrayedGlow(
                    prayed = prayedPrayers?.contains(prayer.type),
                    color = prayer.color,
                    badgeRadius = if (isCurrent) ring.badgeCurrent else ring.badge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(4f)
                        .graphicsLayer {
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(if (isSelected) 20f else 5f)
                        .onSizeChanged { contentSize = it }
                        .graphicsLayer {
                            // Keep an open card inside the dial so it never runs off the screen edge.
                            val maxX = (sizePx - contentSize.width) / 2f - edge
                            val maxY = (sizePx - contentSize.height) / 2f - edge
                            translationX = if (maxX > 0f) offset.x.coerceIn(-maxX, maxX) else offset.x
                            translationY = if (maxY > 0f) offset.y.coerceIn(-maxY, maxY) else offset.y
                        }
                ) {
                    AnimatedContent(
                        targetState = isSelected,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.86f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.86f))
                        },
                        label = "classicMarker"
                    ) { open ->
                        if (open) {
                            GearPlaque(
                                prayer = plaquePrayers.getValue(prayer.type),
                                name = prayer.type.getDisplayName(context).uppercase(locale),
                                style = DayCircleStyle.CLASSIC,
                                palette = palette,
                                phase = stillPhase,
                                compact = isLandscape,
                                weather = weatherLines[prayer.type],
                                modifier = Modifier.pointerInput(Unit) { detectTapGestures { selected = null } }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(targetSize)
                                    .pointerInput(prayer.type) {
                                        detectTapGestures {
                                            if (currentOnPrayerTap(prayer.type)) return@detectTapGestures
                                            selected = prayer.type
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        // The date: the round glass card, held in a fixed gold bezel.
        val cardDiameter = with(density) { (ring.dateRadius * 2).toDp() }
        val bezelDiameter = with(density) { (ring.bezelRadius * 2).toDp() }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(bezelDiameter)
                .zIndex(5f),
            contentAlignment = Alignment.Center
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val light = GearLight(lightAngle.value)
                        onDrawBehind { goldBezel(palette, light, density.density) }
                    }
            )
            FlippableCalendarCard(
                day = day,
                isHijriVisible = isHijriVisible,
                onFlip = onToggleHijri,
                contentColor = contentColor,
                accentColor = currentPrayerColor,
                currentTime = currentTime,
                isSelectedDayToday = isSelectedDayToday,
                pulseScale = 1f,
                temperatureRange = temperatureRange,
                modifier = Modifier.size(cardDiameter)
            )
        }

        ReligiousBadge(
            day.date,
            contentColor,
            hijriDate = day.hijriDate,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = bezelDiameter / 2 + if (isLandscape) 8.dp else 12.dp)
                .zIndex(5f)
        )

        // The prayer name sits just above the bezel.
        CurrentPrayerHeader(currentPrayer, contentColor, currentPrayerColor, verticalOffset = -(bezelDiameter / 2 + 18.dp))
    }
}

/** How much of the bezel the glass date card covers: the same share as the bezel's inner edge. */
private const val BEZEL_FACE_RATIO = 0.86f

/** Proportions of the classic ring for a dial [s] pixels across, in the gear dials' measures. */
private class HaloRing(s: Float, dp: Float) {
    val center = Offset(s / 2f, s / 2f)

    /** Radius of the enamel track, the same as the gear dials' prayer track. */
    val track = 0.43f * s

    /** Width of the enamel channel. */
    val trackWidth = 2f * 0.63f * max(5f * dp, s * 0.021f)

    val badge = max(11f * dp, s * 0.04f)
    val badgeCurrent = badge * 1.18f

    /** Radius of the round date card in the hub. */
    val dateRadius = 0.1152f * s

    /** Outer radius of the gold bezel around the date card. */
    val bezelRadius = dateRadius / BEZEL_FACE_RATIO

    private val labelGap = 0.05f * s

    /** Where the time label of a badge of [radius] sits, inside the ring. */
    fun labelDistance(radius: Float) = track - radius - labelGap
}

/** The ring: a warm halo, a dark channel, the prayer colours as enamel, a metallic sheen and gold hairlines. */
private fun DrawScope.drawRing(
    ring: HaloRing,
    prayers: List<PrayerNodeInfo>,
    palette: GearPalette,
    light: GearLight,
    currentColor: Color,
    rtl: Boolean
) {
    val c = ring.center
    val track = ring.track
    val width = ring.trackWidth
    val half = width / 2f

    haloRing(c, track, track * 0.22f, lerp(palette.warmHalo, currentColor, 0.3f), 0.15f)

    drawCircle(Color(0xCC1A1205), track, c, style = Stroke(width + 1.6.dp.toPx()))

    val sorted = prayers.sortedBy { it.time }
    sorted.forEachIndexed { i, from ->
        val to = sorted[(i + 1) % sorted.size]
        val start = dayAngle(from.time.minutes(), rtl)
        var sweep = dayAngle(to.time.minutes(), rtl) - start
        if (rtl) {
            if (sweep > 0f) sweep -= TWO_PI
        } else if (sweep < 0f) {
            sweep += TWO_PI
        }
        val gap = 0.01f * sign(sweep)
        drawArc(
            brush = Brush.linearGradient(
                listOf(from.color.copy(alpha = 0.95f), to.color.copy(alpha = 0.95f)),
                start = pointOn(c, track, start),
                end = pointOn(c, track, start + sweep)
            ),
            startAngle = (start + gap).toDegrees(),
            sweepAngle = (sweep - 2 * gap).toDegrees(),
            useCenter = false,
            topLeft = Offset(c.x - track, c.y - track),
            size = Size(track * 2, track * 2),
            style = Stroke(width, cap = StrokeCap.Round)
        )
    }

    // Specular reflection from the light, so the enamel reads as glazed metal.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.32f),
            0.45f to Color.White.copy(alpha = 0.08f),
            1.0f to Color.Black.copy(alpha = 0.22f),
            center = pointOn(c, track, light.angle),
            radius = track * 1.1f
        ),
        radius = track,
        center = c,
        style = Stroke(width)
    )

    drawCircle(palette.gold.copy(alpha = 0.70f), track + half, c, style = Stroke(0.9.dp.toPx()))
    drawCircle(palette.gold.copy(alpha = 0.70f), track - half, c, style = Stroke(0.9.dp.toPx()))
    drawCircle(palette.tone(Color(0x66FFF1C8)), track + half - 0.5.dp.toPx(), c, style = Stroke(0.5.dp.toPx()))
    drawCircle(palette.tone(Color(0x66FFF1C8)), track - half + 0.5.dp.toPx(), c, style = Stroke(0.5.dp.toPx()))
}

/** A prayer as a flat enamel badge in a gold hairline, like the ring it sits on. */
private fun DrawScope.prayerBadge(prayer: PrayerNodeInfo, at: Offset, radius: Float, isCurrent: Boolean, palette: GearPalette) {
    if (isCurrent) halo(at, radius * 0.6f, radius * 2.2f, prayer.color, 0.35f)
    drawCircle(Color.Black.copy(alpha = 0.3f), radius + 1.2.dp.toPx(), at)
    drawCircle(prayer.color, radius, at)
    drawCircle(palette.gold.copy(alpha = 0.85f), radius, at, style = Stroke(1.dp.toPx()))
    drawCircle(palette.tone(Color(0x66FFF1C8)), radius - 1.dp.toPx(), at, style = Stroke(0.5.dp.toPx()))

    val iconSize = radius * if (isCurrent) 1.15f else 1.1f
    val ink = if (prayer.color.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White
    translate(at.x - iconSize / 2f, at.y - iconSize / 2f) {
        with(prayer.painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(ink)) }
    }
}

private const val TWO_PI = (2 * Math.PI).toFloat()

private fun LocalTime.minutes() = (hour * 60 + minute).toFloat()

/** Metadata for a single prayer point on the circle. */
data class PrayerNodeInfo(
    val type: PrayerType,
    val time: LocalTime,
    val color: Color,
    val painter: VectorPainter,
    val icon: ImageVector
)
