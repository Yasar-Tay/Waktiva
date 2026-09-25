package com.ybugmobile.waktiva.ui.home.composables.gear

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.ybugmobile.waktiva.ui.home.composables.CurrentPrayerHeader
import com.ybugmobile.waktiva.ui.home.composables.DetailedInfo
import com.ybugmobile.waktiva.ui.home.composables.FlippableCalendarCard
import com.ybugmobile.waktiva.ui.home.composables.InfoGlassCard
import com.ybugmobile.waktiva.ui.home.composables.ReligiousBadge
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.darken
import com.ybugmobile.waktiva.ui.theme.desaturate
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Visual styles for the clockwork day dial. */
enum class GearDayCircleStyle {
    /** A spoked brass wheel; prayer gears mesh on its outside. */
    BRASS,

    /** A fixed steel bezel with an internal ring gear; prayer gears run inside it. */
    STEEL,

    /** A hairline skeleton wheel over a faint gear train; prayers are cog badges. */
    SKELETON
}

/**
 * Clockwork take on PrayerCircleVisualization: a brass day wheel with prayer gears
 * meshing on its outside. Drop-in replacement with the same parameters.
 */
@Composable
fun BrassGearDayCircle(
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
) = GearDayCircle(
    GearDayCircleStyle.BRASS, day, currentTime, nextPrayer, currentPrayer, isSelectedDayToday,
    isHijriVisible, onToggleHijri, contentColor, isMuted, playAdhanAudio, onSkipAudio
)

/**
 * Clockwork take on PrayerCircleVisualization: a steel bezel holding the prayer track,
 * with prayer gears running inside a turning ring gear. Drop-in replacement.
 */
@Composable
fun SteelGearDayCircle(
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
) = GearDayCircle(
    GearDayCircleStyle.STEEL, day, currentTime, nextPrayer, currentPrayer, isSelectedDayToday,
    isHijriVisible, onToggleHijri, contentColor, isMuted, playAdhanAudio, onSkipAudio
)

/**
 * Clockwork take on PrayerCircleVisualization: a hairline skeleton wheel over a faint
 * gear train, with cog-shaped prayer badges. Closest to the current layout. Drop-in replacement.
 */
@Composable
fun SkeletonGearDayCircle(
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
) = GearDayCircle(
    GearDayCircleStyle.SKELETON, day, currentTime, nextPrayer, currentPrayer, isSelectedDayToday,
    isHijriVisible, onToggleHijri, contentColor, isMuted, playAdhanAudio, onSkipAudio
)

/**
 * Shared body of the clockwork day dials. [isMuted], [playAdhanAudio] and [onSkipAudio]
 * are accepted only to keep the signature identical to PrayerCircleVisualization.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun GearDayCircle(
    style: GearDayCircleStyle,
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
    val density = LocalDensity.current
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val weatherCondition = LocalGlassTheme.current.weatherCondition

    var selectedInfo by remember { mutableStateOf<DetailedInfo?>(null) }
    LaunchedEffect(selectedInfo) {
        if (selectedInfo != null) {
            delay(4000)
            selectedInfo = null
        }
    }

    // One ambient turn every 90 s. Read only inside the draw lambda so it redraws without recomposing.
    val phase = rememberInfiniteTransition(label = "gearPhase").animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart),
        label = "gearPhase"
    )

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

    // Same colors and weather desaturation as PrayerCircleVisualization.
    val prayers = remember(day, fajrPainter, sunrisePainter, dhuhrPainter, asrPainter, maghribPainter, ishaPainter, weatherCondition) {
        val isCloudy = weatherCondition != WeatherCondition.CLEAR && weatherCondition != WeatherCondition.UNKNOWN
        val isSevere = weatherCondition == WeatherCondition.RAINY ||
            weatherCondition == WeatherCondition.THUNDERSTORM ||
            weatherCondition == WeatherCondition.SNOWY
        fun tone(color: Color) = if (isCloudy) {
            color.desaturate(if (isSevere) 0.35f else 0.2f).darken(if (isSevere) 0.2f else 0.1f)
        } else {
            color
        }
        listOf(
            Triple(PrayerType.FAJR, Color(0xFF81D4FA), fajrPainter to fajrIcon),
            Triple(PrayerType.SUNRISE, Color(0xFFFFE082), sunrisePainter to sunriseIcon),
            Triple(PrayerType.DHUHR, Color(0xFFFFF59D), dhuhrPainter to dhuhrIcon),
            Triple(PrayerType.ASR, Color(0xFFFFCC80), asrPainter to asrIcon),
            Triple(PrayerType.MAGHRIB, Color(0xFFCE93D8), maghribPainter to maghribIcon),
            Triple(PrayerType.ISHA, Color(0xFF9FA8DA), ishaPainter to ishaIcon)
        ).map { (type, color, art) ->
            val time = day.timings[type] ?: LocalTime.MIN
            GearPrayer(
                type = type,
                minutes = (time.hour * 60 + time.minute).toFloat(),
                color = tone(color),
                painter = art.first,
                icon = art.second,
                label = time.format(formatter)
            )
        }
    }

    val currentPrayerType = remember(day, currentTime) {
        var current: PrayerType? = null
        for ((type, time) in day.timings.toList().sortedBy { it.second }) {
            if (currentTime.isAfter(time) || currentTime == time) current = type else break
        }
        current ?: PrayerType.ISHA
    }
    val current = prayers.firstOrNull { it.type == currentPrayerType } ?: prayers.last()

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
    val nowMinutes = currentTime.hour * 60 + currentTime.minute + currentTime.second / 60f

    BoxWithConstraints(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val sizePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val dial = remember(style, sizePx, density.density) { createGearDial(style, sizePx, density.density) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { selectedInfo = null } }
        ) {
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
                    textMeasurer = textMeasurer
                )
            )
        }

        // Tap targets over the drawn markers; a tapped marker turns into the info card.
        val targetSize = with(density) { (dial.markerRadius * 2).toDp() }.coerceAtLeast(32.dp)
        prayers.forEach { prayer ->
            val isSelected = selectedInfo?.id == prayer.type.name
            val offset = pointOn(Offset.Zero, dial.markerDistance, dayAngle(prayer.minutes, isRtl))
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(if (isSelected) 20f else 5f)
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                AnimatedContent(
                    targetState = isSelected,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
                    },
                    label = "gearMarker"
                ) { selected ->
                    val info = DetailedInfo(
                        prayer.type.name,
                        prayer.type.getDisplayName(context),
                        prayer.label,
                        prayer.color,
                        prayer.icon
                    )
                    if (selected) {
                        InfoGlassCard(info)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(targetSize)
                                .pointerInput(prayer.type) {
                                    detectTapGestures { selectedInfo = if (isSelected) null else info }
                                }
                        )
                    }
                }
            }
        }

        // Brass and skeleton dials frame the date as a disc filling their hub; steel keeps the card.
        val hubDiameter = dial.hubRadius?.let { with(density) { (it * 2).toDp() } - 2.dp }
        val cardSize = hubDiameter ?: 100.dp
        FlippableCalendarCard(
            day = day,
            isHijriVisible = isHijriVisible,
            onFlip = onToggleHijri,
            contentColor = contentColor,
            accentColor = current.color,
            currentTime = currentTime,
            isSelectedDayToday = isSelectedDayToday,
            pulseScale = 1f,
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(5f),
            size = cardSize,
            circular = hubDiameter != null
        )

        // Placed below the card on its own so it never pushes the card off the hub centre.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight / 2 + cardSize / 2 + if (isLandscape) 8.dp else 12.dp)
                .zIndex(5f)
        ) {
            ReligiousBadge(day.date, contentColor, hijriDate = day.hijriDate)
        }

        // Keep the prayer name clear of the hub ring; on phones the default offset already is.
        val defaultHeaderOffset = if (isLandscape) 60.dp else 74.dp
        val headerOffset = dial.hubOuterRadius?.let { ring ->
            -maxOf(defaultHeaderOffset, with(density) { ring.toDp() } + 18.dp)
        }
        CurrentPrayerHeader(currentPrayer, contentColor, current.color, verticalOffset = headerOffset)
    }
}
