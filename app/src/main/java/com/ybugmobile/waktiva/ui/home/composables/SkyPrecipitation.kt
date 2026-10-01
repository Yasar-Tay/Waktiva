package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.home.composables.gear.dayAngle
import com.ybugmobile.waktiva.ui.theme.LocalLightningFlash
import com.ybugmobile.waktiva.ui.theme.clouds.rememberSceneClock
import com.ybugmobile.waktiva.ui.theme.drawSnowflake
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rain and snow falling in the circle's sky, in the hours whose weather has them (see
 * [DaySky.hours]), faded out at the sky's edge from [fadeFrom] of [radius] like the sky itself.
 *
 * They are the screen's own drops and flakes (WeatherBackgroundLayer): the same slanting white
 * streaks and turning snowflakes, but further off, so smaller, fainter and slower, and the two
 * read as one fall of rain. Storm hours light up with the screen's lightning
 * ([LocalLightningFlash]), never on their own.
 *
 * Draws nothing, and runs no frames, when no hour of the day has rain or snow.
 */
@Composable
internal fun SkyPrecipitation(sky: DaySky, radius: Float, fadeFrom: Float = 0.78f) {
    val hours = remember(sky) { sky.hours.map { it?.takeIf { w -> w.isRain || w.isSnow } } }
    if (hours.all { it == null }) return

    val seconds = rememberSceneClock()
    val lightning = LocalLightningFlash.current
    // One painter for the small flakes and one for the large, each kept at a single size
    // (see drawSnowflake).
    val flake = ImageVector.vectorResource(R.drawable.ic_snowflake)
    val smallFlake = rememberVectorPainter(flake)
    val largeFlake = rememberVectorPainter(flake)

    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val c = size.center
                val dp = density
                val span = 2f * radius
                val left = c.x - radius
                val top = c.y - radius
                val drops = drops(hours, span / dp)
                val stormHours = hours.indices.filter { hours[it]?.isThunder == true }
                val edge = Brush.radialGradient(fadeFrom to Color.Black, 1f to Color.Transparent, center = c, radius = radius)

                onDrawBehind {
                    val t = seconds.value

                    val flash = lightning?.value ?: 0f
                    if (flash > 0.005f) {
                        stormHours.forEach { h ->
                            val from = dayAngle(h * 60f, sky.rtl)
                            drawArc(
                                color = StormLight.copy(alpha = 0.3f * flash),
                                startAngle = from.toDegrees(),
                                sweepAngle = (dayAngle((h + 1) * 60f, sky.rtl) - from).toDegrees(),
                                useCenter = true,
                                topLeft = Offset(left, top),
                                size = Size(span, span)
                            )
                        }
                    }

                    val travel = span + 30f * dp
                    for (d in drops) {
                        val speed = (if (d.snow) SnowSpeed else RainSpeed) * dp
                        val y = top - 15f * dp + ((d.y + t * speed / travel) % 1f) * travel
                        val x = left + d.x * span + if (d.snow) {
                            sin(t / SnowSeconds * TWO_PI + d.x * 10f) * 15f * dp * FarSize
                        } else {
                            // The slow sway of the screen's rain.
                            0.05f * sin(t / 12f * PI.toFloat()) * span
                        }
                        val weather = hours[hourAt(Offset(x, y), c, sky.rtl)] ?: continue
                        if (d.snow != weather.isSnow || d.rank >= weather.density) continue

                        if (d.snow) {
                            val small = d.index % 2 == 0
                            val baseScale = if (small) 0.30f else 0.60f
                            val scale = (baseScale + d.x * 0.14f) * FarSize
                            drawCircle(
                                Color.White.copy(alpha = 0.12f * FarAlpha),
                                radius = (if (small) 2.8f else 4.9f) * dp * FarSize,
                                center = Offset(x, y)
                            )
                            drawSnowflake(
                                flake = if (small) smallFlake else largeFlake,
                                center = Offset(x, y),
                                size = 14f * dp * scale,
                                // The largest of its kind, so the flakes only ever scale down.
                                baseSize = 14f * dp * (baseScale + 0.14f) * FarSize,
                                degrees = t / SnowSeconds * 360f * (if (d.index % 3 == 0) 1.5f else -1f),
                                alpha = (if (small) 0.60f else 0.80f) * FarAlpha
                            )
                        } else {
                            // The screen's three sizes of drop, by the same rules.
                            val kind = d.index % 3
                            val heavy = weather.isHeavyRain
                            val drizzle = weather.isDrizzle
                            val alpha = when (kind) {
                                0 -> if (drizzle) 0.12f else 0.18f
                                1 -> if (drizzle) 0.20f else 0.30f
                                else -> if (heavy) 0.42f else if (drizzle) 0.26f else 0.36f
                            }
                            val length = when (kind) {
                                0 -> if (drizzle) 3f else 6f
                                1 -> if (drizzle) 6f else 10f
                                else -> if (heavy) 16f else if (drizzle) 7f else 13f
                            }
                            val thickness = when (kind) {
                                0 -> 0.45f
                                1 -> 0.65f
                                else -> if (heavy) 0.95f else 0.78f
                            }
                            val slant = (if (heavy) 2f else 1f) * dp * FarSize
                            drawLine(
                                Color.White.copy(alpha = alpha * FarAlpha),
                                Offset(x, y),
                                Offset(x - slant, y + length * dp * FarSize),
                                thickness * dp * max(0.8f, FarSize),
                                StrokeCap.Round
                            )
                        }
                    }

                    drawCircle(edge, radius, c, blendMode = BlendMode.DstIn)
                }
            }
    )
}

/** A drop or flake, placed across the sky's square from 0 to 1; [rank] decides the weathers it falls in. */
private class Drop(val snow: Boolean, val index: Int, val rank: Float, val x: Float, val y: Float)

/**
 * The drops and flakes for a sky [spanDp] across: a little denser than the screen's heaviest
 * rain and snow, so a slice of sky still reads as rain; each weather shows its share by [density].
 */
private fun drops(hours: List<WeatherCondition?>, spanDp: Float): List<Drop> {
    val random = Random(7)
    val perDp2 = spanDp * spanDp / (ScreenWidthDp * ScreenHeightDp) * 1.6f
    val list = mutableListOf<Drop>()
    if (hours.any { it?.isRain == true }) {
        repeat((96 * perDp2).roundToInt()) { list += Drop(false, it, random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    if (hours.any { it?.isSnow == true }) {
        repeat((70 * perDp2).roundToInt()) { list += Drop(true, it, random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    return list
}

/** The hour of the dial under [point]: midnight at the bottom, noon at the top. */
private fun hourAt(point: Offset, center: Offset, rtl: Boolean): Int {
    var turn = (atan2(point.y - center.y, point.x - center.x) - TWO_PI / 4f) / TWO_PI
    if (rtl) turn = -turn
    return (((turn % 1f) + 1f) % 1f * 24f).toInt().coerceIn(0, 23)
}

private val WeatherCondition.isRain: Boolean
    get() = this == WeatherCondition.DRIZZLE || this == WeatherCondition.FREEZING_DRIZZLE ||
        this == WeatherCondition.RAINY || this == WeatherCondition.HEAVY_RAIN ||
        this == WeatherCondition.RAIN_SHOWERS || this == WeatherCondition.FREEZING_RAIN || isThunder

private val WeatherCondition.isSnow: Boolean
    get() = this == WeatherCondition.SNOWY || this == WeatherCondition.HEAVY_SNOW ||
        this == WeatherCondition.SNOW_SHOWERS || this == WeatherCondition.SNOW_GRAINS

private val WeatherCondition.isThunder: Boolean
    get() = this == WeatherCondition.THUNDERSTORM || this == WeatherCondition.THUNDERSTORM_HAIL

private val WeatherCondition.isHeavyRain: Boolean
    get() = this == WeatherCondition.HEAVY_RAIN || isThunder

private val WeatherCondition.isDrizzle: Boolean
    get() = this == WeatherCondition.DRIZZLE || this == WeatherCondition.FREEZING_DRIZZLE

/** Share of the drops a weather lets fall, heaviest first, as the screen's drop counts rank them. */
private val WeatherCondition.density: Float
    get() = when {
        isHeavyRain || this == WeatherCondition.HEAVY_SNOW || this == WeatherCondition.SNOW_SHOWERS -> 1f
        isDrizzle -> 0.4f
        else -> 0.7f
    }

private fun Float.toDegrees() = this * 180f / PI.toFloat()

private const val TWO_PI = (2 * PI).toFloat()

/** Size, opacity and speed of the circle's drops against the screen's: the same fall, further off. */
private const val FarSize = 0.6f
private const val FarAlpha = 0.7f

/** A typical phone screen, which the screen's drop counts and speeds are for. */
private const val ScreenWidthDp = 392f
private const val ScreenHeightDp = 850f

/** How fast the drops fall, in dp a second: the screen's pace (a phone's height in about 3 s for rain, 6.6 s for snow), slowed for distance. */
private const val RainSpeed = ScreenHeightDp / 3.1f * 0.6f
private const val SnowSeconds = 6.6f
private const val SnowSpeed = ScreenHeightDp / SnowSeconds * 0.6f

/** Lightning's tint on the storm hours of the sky. */
private val StormLight = Color(0xFFD7E2FF)
