package com.ybugmobile.waktiva.ui.home.composables.gear

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.CurrentPrayer
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.provider.ReligiousDaysProvider
import com.ybugmobile.waktiva.ui.home.composables.CurrentPrayerHeader
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.hypot

/**
 * The clockwork day circle in [style] (anything but [DayCircleStyle.CLASSIC]).
 * Use it through DayCircle, which picks between this and the classic circle.
 * [sunLight] is the screen angle the sunlight falls from (see [sunLightAngle]), or null
 * for the default light.
 */
@Composable
internal fun GearDayCircle(
    style: DayCircleStyle,
    day: PrayerDay,
    currentTime: LocalTime,
    currentPrayer: CurrentPrayer?,
    isSelectedDayToday: Boolean,
    isHijriVisible: Boolean,
    onToggleHijri: () -> Unit,
    contentColor: Color,
    sunLight: Float? = null
) {
    val density = LocalDensity.current
    val lightAngle = rememberEasedAngle(sunLight ?: GearLight.DEFAULT_ANGLE)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val locale = configuration.locales[0]
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val weather = LocalGlassTheme.current.weatherCondition
    val palette = remember(weather) { GearPalette(WeatherTone.forScenery(weather)) }
    val prayers = rememberGearPrayers(day, weather)
    val currentType = remember(day, currentTime) { currentPrayerType(day, currentTime) }
    val current = prayers.firstOrNull { it.type == currentType } ?: prayers.last()
    val nowMinutes = currentTime.hour * 60 + currentTime.minute + currentTime.second / 60f

    // One ambient turn every 90 s. Read only while drawing, so it redraws without recomposing.
    val phase = rememberInfiniteTransition(label = "gearPhase").animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart),
        label = "gearPhase"
    )

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
    // How far a time label reaches from its centre, so dials can keep labels clear of other parts.
    val labelReach = remember(labelStyle, textMeasurer) {
        val size = textMeasurer.measure("00:00", labelStyle).size
        hypot(size.width / 2f, size.height / 2f)
    }

    val specialDayRes = remember(day.date) { ReligiousDaysProvider.getReligiousDay(day.date)?.nameResId }
    val specialDay = specialDayRes?.let { stringResource(it).uppercase(locale) }

    var selected by remember { mutableStateOf<PrayerType?>(null) }
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(4000)
            selected = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        val sizePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val dial = remember(style, sizePx, density.density, labelReach) {
            createGearDial(style, sizePx, density.density, labelReach)
        }
        val bridge = remember(dial, specialDay, palette) {
            specialDay?.let {
                SpecialDayBridge(
                    text = it,
                    spec = dial.bridge,
                    center = Offset(sizePx / 2f, sizePx / 2f),
                    palette = palette,
                    finish = palette.finish(style),
                    dp = density.density,
                    sizePx = sizePx
                )
            }
        }

        // The dial redraws every frame as it turns, so it has its own layer: the rest of the screen
        // isn't redrawn with it. Its still parts are recorded once per minute (or whenever what they
        // show changes) and replayed; only the turning parts are drawn every frame.
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .pointerInput(Unit) { detectTapGestures { selected = null } }
                .drawWithCache {
                    val still = StillParts(List(StillParts.MAX_PARTS) { obtainGraphicsLayer() })
                    val light = GearLight(lightAngle.value)
                    onDrawBehind {
                        dial.draw(
                            this,
                            GearFrame(
                                prayers = prayers,
                                current = current,
                                nowMinutes = nowMinutes,
                                showNow = isSelectedDayToday,
                                phase = phase.value,
                                rtl = isRtl,
                                labelStyle = labelStyle,
                                textMeasurer = textMeasurer,
                                bridge = bridge,
                                palette = palette,
                                light = light,
                                still = still
                            )
                        )
                    }
                }
        )

        PrayerMarkers(
            prayers = prayers,
            dial = dial,
            style = style,
            palette = palette,
            dialSizePx = sizePx,
            selected = selected,
            onSelect = { selected = it },
            phase = phase,
            isRtl = isRtl,
            compact = isLandscape
        )

        // The date in the dial's own material: brass and skeleton fill their hub, steel has a sub-dial.
        GearDateCard(
            style = style,
            day = day,
            isHijriVisible = isHijriVisible,
            onFlip = onToggleHijri,
            accent = current,
            palette = palette,
            light = { GearLight(lightAngle.value) },
            diameter = with(density) { (dial.dateRadius * 2).toDp() } - 2.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(5f)
        )

        // The prayer name sits just above the ring around the date.
        val headerOffset = -(with(density) { dial.hubOuterRadius.toDp() } + 18.dp)
        CurrentPrayerHeader(currentPrayer, contentColor, current.color, verticalOffset = headerOffset)
    }
}

/**
 * Invisible tap targets over the drawn prayer markers. A tapped marker turns into its
 * [GearPlaque], kept inside the dial so it never runs off the screen edge.
 */
@Composable
private fun BoxScope.PrayerMarkers(
    prayers: List<GearPrayer>,
    dial: GearDial,
    style: DayCircleStyle,
    palette: GearPalette,
    dialSizePx: Float,
    selected: PrayerType?,
    onSelect: (PrayerType?) -> Unit,
    phase: State<Float>,
    isRtl: Boolean,
    compact: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val targetSize = with(density) { (dial.markerRadius * 2).toDp() }.coerceAtLeast(32.dp)
    val edge = with(density) { 4.dp.toPx() }

    prayers.forEach { prayer ->
        key(prayer.type) {
            val isSelected = selected == prayer.type
            val offset = pointOn(Offset.Zero, dial.markerDistance, dayAngle(prayer.minutes, isRtl))
            var contentSize by remember { mutableStateOf(IntSize.Zero) }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(if (isSelected) 20f else 5f)
                    .onSizeChanged { contentSize = it }
                    .graphicsLayer {
                        val maxX = (dialSizePx - contentSize.width) / 2f - edge
                        val maxY = (dialSizePx - contentSize.height) / 2f - edge
                        translationX = if (maxX > 0f) offset.x.coerceIn(-maxX, maxX) else offset.x
                        translationY = if (maxY > 0f) offset.y.coerceIn(-maxY, maxY) else offset.y
                    }
            ) {
                AnimatedContent(
                    targetState = isSelected,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.86f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.86f))
                    },
                    label = "gearMarker"
                ) { open ->
                    if (open) {
                        GearPlaque(
                            prayer = prayer,
                            name = prayer.type.getDisplayName(context).uppercase(locale),
                            style = style,
                            palette = palette,
                            phase = phase,
                            compact = compact,
                            modifier = Modifier.pointerInput(Unit) { detectTapGestures { onSelect(null) } }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(targetSize)
                                .pointerInput(prayer.type) { detectTapGestures { onSelect(prayer.type) } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * [target] (radians) eased in over a second, turning the short way round, so the light on the
 * metal glides as the phone turns instead of jumping with every compass reading.
 */
@Composable
private fun rememberEasedAngle(target: Float): State<Float> {
    val angle = remember { Animatable(target) }
    LaunchedEffect(target) {
        angle.animateTo(blendAngle(angle.value, target, 1f), tween(1200, easing = FastOutSlowInEasing))
    }
    return angle.asState()
}

/** The day's prayers with the same colours and weather toning as PrayerCircleVisualization. */
@Composable
private fun rememberGearPrayers(day: PrayerDay, weather: WeatherCondition): List<GearPrayer> {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val art = listOf(
        PrayerType.FAJR to R.drawable.haze_day_rotated,
        PrayerType.SUNRISE to R.drawable.sunrise,
        PrayerType.DHUHR to R.drawable.clear_day,
        PrayerType.ASR to R.drawable.clear_day,
        PrayerType.MAGHRIB to R.drawable.sunset,
        PrayerType.ISHA to R.drawable.clear_night
    ).map { (type, res) ->
        val icon = ImageVector.vectorResource(res)
        Triple(type, icon, rememberVectorPainter(icon))
    }

    return remember(day, weather, art.map { it.third }) {
        val tone = WeatherTone.forPrayers(weather)
        art.map { (type, icon, painter) ->
            val time = day.timings[type] ?: LocalTime.MIN
            GearPrayer(
                type = type,
                minutes = (time.hour * 60 + time.minute).toFloat(),
                color = tone(PrayerColors.getValue(type)),
                painter = painter,
                icon = icon,
                label = time.format(formatter)
            )
        }
    }
}

private val PrayerColors = mapOf(
    PrayerType.FAJR to Color(0xFF81D4FA),
    PrayerType.SUNRISE to Color(0xFFFFE082),
    PrayerType.DHUHR to Color(0xFFFFF59D),
    PrayerType.ASR to Color(0xFFFFCC80),
    PrayerType.MAGHRIB to Color(0xFFCE93D8),
    PrayerType.ISHA to Color(0xFF9FA8DA)
)

/** The prayer whose time has most recently passed, wrapping to Isha before Fajr. */
private fun currentPrayerType(day: PrayerDay, now: LocalTime): PrayerType {
    var current: PrayerType? = null
    for ((type, time) in day.timings.toList().sortedBy { it.second }) {
        if (now.isAfter(time) || now == time) current = type else break
    }
    return current ?: PrayerType.ISHA
}
