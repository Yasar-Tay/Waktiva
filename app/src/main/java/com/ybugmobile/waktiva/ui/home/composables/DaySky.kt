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
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import kotlin.math.abs
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

/**
 * A spell of one kind of weather in the circle's sky: [startMinute] to [endMinute] of the dial (the
 * end past 1440 when it runs on over midnight), with the weather its icon shows.
 */
@Immutable
class WeatherSpell internal constructor(val startMinute: Float, val endMinute: Float, val weather: PrayerWeather)

/**
 * The day's weather as spells, one icon each: the hours from midnight, grouped by what their icon
 * shows (clear, a few clouds, overcast, fog, rain, snow, a storm) and, where that icon has a sun
 * and a moon, by day and night. Spells run on over midnight, as the dial joins its ends. A dry
 * spell shorter than [MinSpellHours] is taken into the longer of its neighbours, so a passing
 * cloud doesn't crowd the dial; rain, snow and storms always keep an icon of their own.
 *
 * Each hour has the weather the sky shows in it (the reported weather in today's current hour,
 * [now]); a spell's icon shows the most frequent of its own hours. On today's circle a spell that
 * has ended is gone by. Empty without a forecast.
 */
internal fun weatherSpells(day: PrayerDay, weather: DayCircleWeather?, now: LocalTime?): List<WeatherSpell> {
    val forecast = weather?.forecast ?: return emptyList()
    val conditions = (0 until 24).map { h ->
        if (now != null && now.hour == h && weather.nowEffect != WeatherCondition.UNKNOWN) weather.nowEffect
        else forecast.hours.firstOrNull { it.hour == h }?.effectCondition ?: return emptyList()
    }
    val sunrise = day.timings[PrayerType.SUNRISE]?.minutes()
    val maghrib = day.timings[PrayerType.MAGHRIB]?.minutes()
    fun isDay(minute: Float): Boolean {
        val m = ((minute % 1440f) + 1440f) % 1440f
        return if (sunrise != null && maghrib != null) m >= sunrise && m < maghrib else m >= 360f && m < 1080f
    }
    val own = (0 until 24).map { h ->
        val group = conditions[h].spellGroup
        SpellKind(group, if (group.hasSunAndMoon) isDay(h * 60f + 30f) else null)
    }

    // Each hour's kind as the dial shows it, short dry spells taken into their neighbours.
    val kinds = own.toMutableList()
    var runs = spellRuns(kinds)
    while (runs.size > 1) {
        val short = runs.indices
            .filter { runs[it].length < MinSpellHours && !kinds[runs[it].start].group.falls }
            .minByOrNull { runs[it].length } ?: break
        val before = runs[(short - 1 + runs.size) % runs.size]
        val after = runs[(short + 1) % runs.size]
        val into = if (after.length > before.length) after else before
        val run = runs[short]
        for (k in 0 until run.length) kinds[(run.start + k) % 24] = kinds[into.start]
        runs = spellRuns(kinds)
    }

    val nowMinute = now?.minutes()
    return runs.map { run ->
        val kind = kinds[run.start]
        val hours = (0 until run.length).map { (run.start + it) % 24 }
        val condition = hours.filter { own[it] == kind }.ifEmpty { hours }
            .groupingBy { conditions[it] }.eachCount().maxBy { it.value }.key
        val start = run.start * 60f
        val end = start + run.length * 60f
        WeatherSpell(
            start,
            end,
            PrayerWeather(
                condition = condition,
                effectCondition = condition,
                isDay = kind.isDay ?: isDay((start + end) / 2f),
                temperature = null,
                isPast = nowMinute != null && end <= 1440f && end <= nowMinute
            )
        )
    }
}

/** What an hour's icon shows: its [group] of weather and, for a sun or a moon, [isDay]. */
private data class SpellKind(val group: SpellGroup, val isDay: Boolean?)

private class SpellRun(val start: Int, val length: Int)

/**
 * The runs of hours of one kind in [kinds], round the clock: a run over midnight is one run. A day
 * of one kind all through is a single run from midnight.
 */
private fun spellRuns(kinds: List<SpellKind>): List<SpellRun> {
    val first = (0 until 24).firstOrNull { kinds[it] != kinds[(it + 23) % 24] } ?: return listOf(SpellRun(0, 24))
    val runs = mutableListOf<SpellRun>()
    var h = first
    while (h < first + 24) {
        val start = h
        while (h < first + 24 && kinds[h % 24] == kinds[start % 24]) h++
        runs += SpellRun(start % 24, h - start)
    }
    return runs
}

/** The weathers the dial gives one icon, whatever their strength. */
private enum class SpellGroup(val hasSunAndMoon: Boolean = false, val falls: Boolean = false) {
    CLEAR(hasSunAndMoon = true),
    FEW_CLOUDS(hasSunAndMoon = true),
    OVERCAST,
    FOG,
    RAIN(falls = true),
    SNOW(falls = true),
    STORM(falls = true)
}

private val WeatherCondition.spellGroup: SpellGroup
    get() = when (this) {
        WeatherCondition.CLEAR -> SpellGroup.CLEAR
        WeatherCondition.MAINLY_CLEAR, WeatherCondition.PARTLY_CLOUDY -> SpellGroup.FEW_CLOUDS
        WeatherCondition.OVERCAST, WeatherCondition.UNKNOWN -> SpellGroup.OVERCAST
        WeatherCondition.FOGGY -> SpellGroup.FOG
        WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.RAINY,
        WeatherCondition.HEAVY_RAIN, WeatherCondition.RAIN_SHOWERS, WeatherCondition.FREEZING_RAIN -> SpellGroup.RAIN
        WeatherCondition.SNOWY, WeatherCondition.HEAVY_SNOW, WeatherCondition.SNOW_SHOWERS,
        WeatherCondition.SNOW_GRAINS -> SpellGroup.SNOW
        WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_HAIL -> SpellGroup.STORM
    }

/** The shortest dry spell that keeps an icon of its own, in hours. */
private const val MinSpellHours = 2

private fun LocalTime.minutes() = hour * 60f + minute

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
 * Where each spell's icon sits, as offsets from the dial's centre, on a ring just inside the time
 * labels at [labelRadius]: as far out as the most a label can reach towards the centre at that
 * angle allows. Each icon goes as near the middle of its spell as it can without touching another
 * icon, any of [keepOut] (the prayer's name over the hub, a special day's plate) or the hub within
 * [innerLimit], trying every five minutes out to either end of the spell; rain, snow and storms
 * are placed first, then the longer spells. A spell with no room anywhere along it is left
 * without an icon.
 *
 * Sizes are in pixels; [keepOut] is relative to the centre.
 */
internal fun spellIconOffsets(
    spells: List<WeatherSpell>,
    labelRadius: Float,
    labelHalfWidth: Float,
    labelHalfHeight: Float,
    iconSize: Float,
    gap: Float,
    keepOut: List<Rect>,
    innerLimit: Float,
    rtl: Boolean
): List<Pair<PrayerWeather, Offset>> {
    fun at(minute: Float): Offset {
        val direction = pointOn(Offset.Zero, 1f, dayAngle(((minute % 1440f) + 1440f) % 1440f, rtl))
        // A label reaches its half width towards the centre at 3 and 9 o'clock, its half height at 12 and 6.
        val reach = abs(direction.x) * labelHalfWidth + abs(direction.y) * labelHalfHeight
        return direction * (labelRadius - reach - gap - iconSize / 2f)
    }
    val placed = mutableListOf<Offset>()
    fun free(o: Offset): Boolean {
        val half = iconSize / 2f
        if (o.getDistance() - half < innerLimit) return false
        if (placed.any { (it - o).getDistance() < iconSize + gap }) return false
        val box = Rect(o.x - half, o.y - half, o.x + half, o.y + half)
        return keepOut.none { it.overlaps(box) }
    }
    val order = spells.indices.sortedWith(
        compareByDescending<Int> { spells[it].weather.condition.spellGroup.falls }
            .thenByDescending { spells[it].endMinute - spells[it].startMinute }
    )
    val offsets = arrayOfNulls<Offset>(spells.size)
    for (i in order) {
        val spell = spells[i]
        val middle = (spell.startMinute + spell.endMinute) / 2f
        val reach = (spell.endMinute - spell.startMinute) / 2f
        var step = 0f
        search@ while (step <= reach) {
            for (minute in listOf(middle + step, middle - step)) {
                val o = at(minute)
                if (free(o)) {
                    offsets[i] = o
                    placed += o
                    break@search
                }
            }
            step += 5f
        }
    }
    return spells.indices.mapNotNull { i -> offsets[i]?.let { spells[i].weather to it } }
}

/** How long the weather icons' clock takes to go round once. */
private const val WEATHER_LOOP_MS = 6_000

/**
 * The day's weather icons over the dial, one for each spell, at its offset from the centre (see
 * [spellIconOffsets]), in the same colours as the weather at the top of the screen. Icons of
 * spells already gone by fade back.
 */
@Composable
internal fun BoxScope.WeatherSpellIcons(icons: List<Pair<PrayerWeather, Offset>>, size: Dp) {
    // One slow clock for every icon; each icon reads it only while drawing.
    val clock = rememberInfiniteTransition(label = "weather icons").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WEATHER_LOOP_MS, easing = LinearEasing)),
        label = "weather icon clock"
    )
    icons.forEachIndexed { i, (w, offset) ->
        // Each in its own phase, so the icons never move in step.
        key(i) { DialWeatherIcon(w, offset, size, clock, phase = i * 0.31f) }
    }
}

@Composable
private fun BoxScope.DialWeatherIcon(w: PrayerWeather, offset: Offset, size: Dp, clock: State<Float>, phase: Float) {
    AnimatedWeatherIcon(
        condition = w.condition,
        isDay = w.isDay,
        clock = clock,
        phase = phase,
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
