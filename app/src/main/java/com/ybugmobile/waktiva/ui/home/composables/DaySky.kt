package com.ybugmobile.waktiva.ui.home.composables

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.DayForecast
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.home.composables.gear.WeatherTone
import com.ybugmobile.waktiva.ui.home.composables.gear.dayAngle
import com.ybugmobile.waktiva.ui.home.composables.gear.pointOn
import java.time.LocalTime
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The day's weather as the day circle shows it: the selected day's forecast, the weather right
 * now (for today's current hour, so the circle agrees with the sky above it) and whether weather
 * effects are on, which decides whether the sky inside the circle takes on the weather.
 */
@Immutable
data class DayCircleWeather(
    val forecast: DayForecast?,
    /** The weather as reported now, which the icon at the top of the screen shows. */
    val nowCondition: WeatherCondition,
    /** The weather the screen's sky shows now. */
    val nowEffect: WeatherCondition,
    val effectsOn: Boolean
)

/** A prayer's weather: what it's like in the hour its time begins. */
@Immutable
data class PrayerWeather(
    val condition: WeatherCondition,
    val effectCondition: WeatherCondition,
    val isDay: Boolean,
    val temperature: Double?,
    /** Today's prayers whose time has gone by, whose icons fade back. */
    val isPast: Boolean
)

// ---------------------------------------------------------------------------
// The sky inside the circle
// ---------------------------------------------------------------------------

/**
 * The colour of the sky at [minute] of [day], from the home screen's own sky colours around the
 * prayer times (see getGradientColorsForTime): night blue, indigo at Fajr, peach as the sun
 * rises, blue through the day, a red glow at Maghrib, indigo again at Isha.
 */
internal fun skyLight(minute: Float, day: PrayerDay): Color {
    fun at(type: PrayerType, fallback: Int) = day.timings[type]?.let { it.hour * 60f + it.minute } ?: fallback.toFloat()
    val fajr = at(PrayerType.FAJR, 300)
    val sunrise = at(PrayerType.SUNRISE, 390)
    val dhuhr = at(PrayerType.DHUHR, 780)
    val asr = at(PrayerType.ASR, 1020)
    val maghrib = at(PrayerType.MAGHRIB, 1110)
    val isha = at(PrayerType.ISHA, 1200)
    val keys = listOf(
        0f to Night,
        fajr - 40 to Color(0xFF131A33),
        fajr to Color(0xFF1E1B4B),
        sunrise - 20 to Color(0xFF2B2A6F),
        sunrise + 10 to Color(0xFFE8955A),
        sunrise + 50 to Color(0xFF4FA3C7),
        dhuhr to Color(0xFF3E86CF),
        asr to Color(0xFF3F8FD2),
        maghrib - 45 to Color(0xFF86C3EC),
        maghrib - 5 to Color(0xFFC8683A),
        maghrib + 25 to Color(0xFF6B2A3A),
        isha to Color(0xFF1E1B4B),
        isha + 90 to Color(0xFF121A2E),
        1440f to Night
    ).map { (m, c) -> m.coerceIn(0f, 1440f) to c }.sortedBy { it.first }

    val m = minute.coerceIn(0f, 1440f)
    val upper = keys.indexOfFirst { it.first >= m }.coerceAtLeast(1)
    val (m0, c0) = keys[upper - 1]
    val (m1, c1) = keys[upper]
    return if (m1 <= m0) c1 else lerp(c0, c1, (m - m0) / (m1 - m0))
}

private val Night = Color(0xFF121A2B)

/**
 * The circle's sky as a sweep of colour, one stop every quarter hour, placed on the dial's own
 * clock (midnight at the bottom, noon at the top, counter-clockwise in RTL). Each hour takes the
 * tone the screen's sky would have in its weather ([hourWeather], null for untoned).
 */
internal fun skySweep(day: PrayerDay, hourWeather: (Int) -> WeatherCondition?, rtl: Boolean): Array<Pair<Float, Color>> =
    Array(SweepStops + 1) { k ->
        val fraction = k / SweepStops.toFloat()
        // A sweep starts at 3 o'clock, which is 18:00 on the dial (06:00 in RTL), and runs clockwise.
        val turn = if (rtl) 0.25f - fraction else fraction - 0.25f
        val minute = (((turn % 1f) + 1f) % 1f) * 1440f
        val weather = hourWeather((minute / 60f).toInt().coerceIn(0, 23))
        val light = skyLight(minute, day)
        fraction to (weather?.let { WeatherTone.forScenery(it)(light) } ?: light)
    }

private const val SweepStops = 96

/** Everything the circle's sky needs, worked out once per hour, forecast and layout direction. */
@Immutable
class DaySky internal constructor(
    internal val sweep: Array<Pair<Float, Color>>,
    internal val starryHours: List<Int>,
    /** Each hour's weather as the sky shows it, from midnight; null where the sky is untoned. */
    internal val hours: List<WeatherCondition?>,
    internal val rtl: Boolean
)

/**
 * The sky for [day]. With weather effects off, or without a forecast, it only has the day's
 * light. [now] is the time of day when [day] is today, whose hour follows the sky on screen.
 */
internal fun daySky(day: PrayerDay, weather: DayCircleWeather?, now: LocalTime?, rtl: Boolean): DaySky {
    val hourWeather: (Int) -> WeatherCondition? = { hour ->
        when {
            weather == null || !weather.effectsOn || weather.forecast == null -> null
            now != null && now.hour == hour && weather.nowEffect != WeatherCondition.UNKNOWN -> weather.nowEffect
            else -> weather.forecast.hours.firstOrNull { it.hour == hour }?.effectCondition
        }
    }
    val sunrise = day.timings[PrayerType.SUNRISE]
    val maghrib = day.timings[PrayerType.MAGHRIB]
    val starry = (0 until 24).filter { hour ->
        val middle = LocalTime.of(hour, 30)
        val isNight = sunrise != null && maghrib != null && (middle.isBefore(sunrise) || !middle.isBefore(maghrib))
        isNight && hourWeather(hour) in ClearSkies
    }
    return DaySky(skySweep(day, hourWeather, rtl), starry, List(24, hourWeather), rtl)
}

private val ClearSkies = setOf(WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR)

/**
 * Paints [sky] as a disc of [radius] pixels at the centre: the day's colours round the dial,
 * a dome of shade over the middle (the sky overhead is deeper, and the date and the prayer name
 * read over it), a few stars in clear night hours, and an edge that fades into the screen's sky.
 */
internal fun Modifier.daySky(sky: DaySky, radius: Float, fadeFrom: Float = 0.78f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val c = size.center
        val sweep = Brush.sweepGradient(*sky.sweep, center = c)
        val dome = Brush.radialGradient(
            0f to DomeShade.copy(alpha = 0.55f),
            0.55f to DomeShade.copy(alpha = 0.12f),
            0.9f to Color.White.copy(alpha = 0.07f),
            1f to Color.White.copy(alpha = 0.02f),
            center = c,
            radius = radius
        )
        val edge = Brush.radialGradient(fadeFrom to Color.Black, 1f to Color.Transparent, center = c, radius = radius)
        val stars = sky.starryHours.flatMap { hour ->
            val rnd = Random(hour * 7919 + 17)
            List(3) {
                val minute = hour * 60f + rnd.nextFloat() * 60f
                val at = pointOn(c, radius * (0.4f + 0.45f * rnd.nextFloat()), dayAngle(minute, sky.rtl))
                Triple(at, (0.5f + 0.7f * rnd.nextFloat()) * density, 0.35f + 0.45f * rnd.nextFloat())
            }
        }
        onDrawBehind {
            drawCircle(sweep, radius, c)
            drawCircle(dome, radius, c)
            stars.forEach { (at, r, alpha) -> drawCircle(Color.White.copy(alpha = alpha), r, at) }
            drawCircle(edge, radius, c, blendMode = BlendMode.DstIn)
        }
    }

private val DomeShade = Color(0xFF050816)

// ---------------------------------------------------------------------------
// The prayers' weather
// ---------------------------------------------------------------------------

/**
 * Each prayer's weather on [day]: the forecast for the hour its time begins, except that the
 * current hour of today ([now]) takes the weather reported now, as the top of the screen shows.
 * Empty without a forecast.
 */
internal fun prayerWeather(
    day: PrayerDay,
    weather: DayCircleWeather?,
    now: LocalTime?,
    currentPrayer: PrayerType?
): Map<PrayerType, PrayerWeather> {
    val forecast = weather?.forecast ?: return emptyMap()
    val sunrise = day.timings[PrayerType.SUNRISE]
    val maghrib = day.timings[PrayerType.MAGHRIB]
    val currentStart = currentPrayer?.let { day.timings[it] }
    return day.timings.mapNotNull { (type, time) ->
        val hour = forecast.at(time) ?: return@mapNotNull null
        val isNow = now != null && now.hour == time.hour
        val condition = if (isNow && weather.nowCondition != WeatherCondition.UNKNOWN) weather.nowCondition else hour.condition
        val effect = if (isNow && weather.nowEffect != WeatherCondition.UNKNOWN) weather.nowEffect else hour.effectCondition
        type to PrayerWeather(
            condition = condition,
            effectCondition = effect,
            isDay = sunrise != null && maghrib != null && !time.isBefore(sunrise) && time.isBefore(maghrib),
            temperature = hour.temperature,
            isPast = now != null && currentStart != null && time.isBefore(currentStart)
        )
    }.toMap()
}

/** The day's range of temperatures, as the date card shows it, e.g. "15° – 26°". */
internal fun DayForecast.temperatureRange(): String =
    String.format(Locale.US, "%d° – %d°", minTemp.roundToInt(), maxTemp.roundToInt())

/** A temperature as the top of the screen shows it: whole degrees. */
internal fun Double.degrees(): String = String.format(Locale.US, "%d°", roundToInt())

/** The icon for [condition], day or night: the same set as the weather at the top of the screen. */
@DrawableRes
fun weatherIconRes(condition: WeatherCondition, isDay: Boolean): Int = when (condition) {
    WeatherCondition.CLEAR -> if (isDay) R.drawable.clear_day else R.drawable.clear_night
    WeatherCondition.MAINLY_CLEAR,
    WeatherCondition.PARTLY_CLOUDY -> if (isDay) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night
    WeatherCondition.OVERCAST -> R.drawable.cloudy_day_night
    WeatherCondition.FOGGY -> R.drawable.fog_day_night
    WeatherCondition.DRIZZLE -> R.drawable.drizzle_day_night
    WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.FREEZING_RAIN -> R.drawable.sleet_day_night
    WeatherCondition.RAINY, WeatherCondition.HEAVY_RAIN, WeatherCondition.RAIN_SHOWERS -> R.drawable.rain_day_night
    WeatherCondition.SNOWY, WeatherCondition.HEAVY_SNOW, WeatherCondition.SNOW_SHOWERS -> R.drawable.snow_day_night
    WeatherCondition.SNOW_GRAINS -> R.drawable.ic_snowflake
    WeatherCondition.THUNDERSTORM -> R.drawable.thunderstorm_day_night
    WeatherCondition.THUNDERSTORM_HAIL -> R.drawable.hail_day_night
    WeatherCondition.UNKNOWN -> R.drawable.cloudy_day_night
}

// ---------------------------------------------------------------------------
// Where the icons go
// ---------------------------------------------------------------------------

/**
 * Where a prayer's weather icon sits, as an offset from the dial's centre: beside its time label
 * on the ring of labels, on the side with more room before the next prayer, so it keeps clear of
 * the prayer name above the centre, the special-day bridge below it and the markers outside.
 * When neither side has room it sits just inside the label instead.
 *
 * [minutes] are all the prayers' times on the dial; sizes are in pixels.
 */
internal fun weatherIconOffset(
    minutes: List<Float>,
    index: Int,
    labelRadius: Float,
    labelHalfWidth: Float,
    labelHalfHeight: Float,
    iconSize: Float,
    gap: Float,
    rtl: Boolean
): Offset {
    val sorted = minutes.sorted()
    val m = minutes[index]
    val at = sorted.indexOf(m)
    val before = (m - sorted[(at - 1 + sorted.size) % sorted.size] + 1440f) % 1440f
    val after = (sorted[(at + 1) % sorted.size] - m + 1440f) % 1440f
    // How far round the ring the icon's centre is from the label's, in minutes of the dial.
    val shift = (labelHalfWidth + gap + iconSize / 2f) / labelRadius / TWO_PI * 1440f
    // The icon plus the neighbour's label half must fit in the gap.
    val needed = 2f * shift + gap / labelRadius / TWO_PI * 1440f
    return if (max(before, after) >= needed) {
        val towards = if (after >= before) m + shift else m - shift
        pointOn(Offset.Zero, labelRadius, dayAngle(towards, rtl))
    } else {
        pointOn(Offset.Zero, labelRadius - labelHalfHeight - gap - iconSize / 2f, dayAngle(m, rtl))
    }
}

private const val TWO_PI = (2 * Math.PI).toFloat()

/** How long the weather icons' clock takes to go round once. */
private const val WEATHER_LOOP_MS = 6_000

/**
 * The prayers' weather icons over the dial, at [offsets] from its centre, in the same colours as
 * the weather at the top of the screen. Icons of prayers already gone by fade back.
 */
@Composable
internal fun BoxScope.PrayerWeatherIcons(
    weather: Map<PrayerType, PrayerWeather>,
    offsets: Map<PrayerType, Offset>,
    size: Dp
) {
    // One slow clock for every icon; each icon reads it only while drawing.
    val clock = rememberInfiniteTransition(label = "weather icons").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WEATHER_LOOP_MS, easing = LinearEasing)),
        label = "weather icon clock"
    )
    weather.forEach { (type, w) ->
        val offset = offsets[type] ?: return@forEach
        key(type) {
            AnimatedWeatherIcon(
                condition = w.condition,
                isDay = w.isDay,
                clock = clock,
                phase = type.ordinal / 6f,
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(3f)
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y
                        alpha = if (w.isPast) 0.45f else 1f
                    }
                    .size(size)
                    // A soft shadow, so an icon keeps its edge over any colour of the sky.
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                0.3f to Color.Black.copy(alpha = 0.28f),
                                1f to Color.Transparent,
                                center = center,
                                radius = this.size.minDimension * 0.75f
                            ),
                            this.size.minDimension * 0.75f
                        )
                    }
            )
        }
    }
}
