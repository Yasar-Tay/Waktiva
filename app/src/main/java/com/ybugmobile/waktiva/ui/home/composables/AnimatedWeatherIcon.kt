package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * A weather icon that is alive inside: the sun turns its rays, clouds drift, rain and snow fall,
 * fog slides, lightning flashes. Drawn on a 100 x 100 canvas in the palette of the static icons.
 *
 * [clock] runs 0..1 once per loop and is read only while drawing, so a ticking clock redraws the
 * icon without recomposing anything. Every motion is a whole number of cycles per loop, which
 * keeps the loop seamless. [phase] offsets this icon's clock so a row of icons never moves in step.
 */
@Composable
internal fun AnimatedWeatherIcon(
    condition: WeatherCondition,
    isDay: Boolean,
    clock: State<Float>,
    phase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val t = frac(clock.value + phase)
        val s = size.minDimension / UNITS
        scale(s, s, pivot = Offset.Zero) {
            when (condition) {
                WeatherCondition.CLEAR ->
                    if (isDay) sun(Offset(50f, 50f), 21f, t) else moon(Offset(50f, 50f), 1f, t)

                WeatherCondition.MAINLY_CLEAR, WeatherCondition.PARTLY_CLOUDY -> {
                    if (isDay) sun(Offset(63f, 36f), 16f, t) else moon(Offset(63f, 36f), 0.6f, t)
                    cloud(dx = 4f + 2f * wave(t, 1), dy = 14f, size = 0.82f)
                }

                WeatherCondition.OVERCAST, WeatherCondition.UNKNOWN -> {
                    cloud(dx = 17f - 3f * wave(t, 1), dy = -10f, size = 0.66f, tint = BACK_CLOUD, alpha = 0.85f)
                    cloud(dx = 1f + 3f * wave(t, 1), dy = 6f, size = 0.9f)
                }

                WeatherCondition.FOGGY -> {
                    cloud(dx = 7f + 2f * wave(t, 1), dy = -12f, size = 0.8f)
                    mist(t)
                }

                WeatherCondition.DRIZZLE -> {
                    cloud(dx = 7f, dy = -9f, size = 0.84f)
                    rain(t, drops = 3, length = 7f, width = 2.4f, speed = 1)
                }

                WeatherCondition.RAINY, WeatherCondition.RAIN_SHOWERS -> {
                    cloud(dx = 7f, dy = -9f, size = 0.84f)
                    rain(t, drops = 4, length = 10f, width = 3f, speed = 2)
                }

                WeatherCondition.HEAVY_RAIN -> {
                    cloud(dx = 7f, dy = -9f, size = 0.84f, tint = STORM_CLOUD)
                    rain(t, drops = 6, length = 12f, width = 3.2f, speed = 2)
                }

                WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.FREEZING_RAIN -> {
                    cloud(dx = 7f, dy = -9f, size = 0.84f)
                    rain(t, drops = 2, length = 9f, width = 3f, speed = 2, xs = floatArrayOf(30f, 66f))
                    snow(t, flakes = 2, xs = floatArrayOf(48f, 82f), phaseShift = 0.4f)
                }

                WeatherCondition.SNOWY, WeatherCondition.SNOW_SHOWERS -> {
                    cloud(dx = 7f, dy = -9f, size = 0.84f)
                    snow(t, flakes = 4)
                }

                WeatherCondition.HEAVY_SNOW -> {
                    cloud(dx = 7f, dy = -9f, size = 0.84f, tint = STORM_CLOUD)
                    snow(t, flakes = 6)
                }

                WeatherCondition.SNOW_GRAINS -> snow(t, flakes = 6, top = 8f, bottom = 92f, radius = 6f)

                WeatherCondition.THUNDERSTORM -> {
                    cloud(dx = 7f, dy = -11f, size = 0.84f, tint = STORM_CLOUD)
                    rain(t, drops = 2, length = 9f, width = 3f, speed = 2, xs = floatArrayOf(26f, 78f))
                    bolt(t)
                }

                WeatherCondition.THUNDERSTORM_HAIL -> {
                    cloud(dx = 7f, dy = -11f, size = 0.84f, tint = STORM_CLOUD)
                    hail(t, xs = floatArrayOf(24f, 80f))
                    bolt(t)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Palette: the static icons' gradients
// ---------------------------------------------------------------------------

private const val UNITS = 100f

private val SUN_BRUSH = Brush.linearGradient(
    listOf(Color(0xFFFFC400), Color(0xFFFF6F00)),
    start = Offset(30f, 30f), end = Offset(72f, 72f)
)
private val SUN_RAY = Color(0xFFFFB300)
private val CLOUD_BRUSH = Brush.verticalGradient(
    listOf(Color(0xFFECEFF1), Color(0xFFB0BEC5)), startY = 26f, endY = 78f
)
private val BACK_CLOUD = Brush.verticalGradient(
    listOf(Color(0xFFB0BEC5), Color(0xFF78909C)), startY = 26f, endY = 78f
)
private val STORM_CLOUD = Brush.verticalGradient(
    listOf(Color(0xFFB0BEC5), Color(0xFF607D8B)), startY = 26f, endY = 78f
)
private val MOON_BRUSH = Brush.linearGradient(
    listOf(Color(0xFFFFF59D), Color(0xFFFFCA28)),
    start = Offset(20f, 20f), end = Offset(70f, 80f)
)
private val DROP = Color(0xFF81D4FA)
private val FLAKE = Color(0xFFE3F2FD)
private val HAIL = Color(0xFFF5FBFF)
private val BOLT = Brush.verticalGradient(
    listOf(Color(0xFFFFEE58), Color(0xFFFFA000)), startY = 50f, endY = 94f
)
private val MIST = Color(0xFFCFD8DC)

/** A crescent: a full disc with another, offset up and to the right, bitten out of it. */
private val MOON_PATH: Path = Path.combine(
    PathOperation.Difference,
    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(46f, 50f), 30f)) },
    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(60f, 40f), 26f)) }
)

private val BOLT_PATH: Path = Path().apply {
    moveTo(57f, 50f)
    lineTo(40f, 75f)
    lineTo(51f, 75f)
    lineTo(44f, 94f)
    lineTo(68f, 64f)
    lineTo(56f, 64f)
    lineTo(63f, 50f)
    close()
}

// ---------------------------------------------------------------------------
// Parts
// ---------------------------------------------------------------------------

private fun frac(x: Float): Float = x - floor(x)

/** A sine wave that goes round [cycles] times per loop. */
private fun wave(t: Float, cycles: Int): Float = sin(t * cycles * 2f * PI.toFloat())

/** The sun: a disc that breathes, ringed by rays that turn. */
private fun DrawScope.sun(center: Offset, radius: Float, t: Float) {
    // Eight rays are alike every 45 degrees, so turning 90 per loop closes the loop seamlessly.
    rotate(degrees = t * 90f, pivot = center) {
        for (i in 0 until 8) {
            rotate(degrees = i * 45f, pivot = center) {
                drawLine(
                    SUN_RAY,
                    start = Offset(center.x, center.y - radius * 1.38f),
                    end = Offset(center.x, center.y - radius * 1.78f),
                    strokeWidth = radius * 0.27f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
    drawCircle(SUN_BRUSH, radius = radius * (1f + 0.045f * wave(t, 2)), center = center)
}

/** A crescent that rocks a little, with stars twinkling at its side. */
private fun DrawScope.moon(center: Offset, size: Float, t: Float) {
    val from = Offset(50f, 50f)
    translate(left = center.x - from.x, top = center.y - from.y) {
        scale(size, size, pivot = from) {
            rotate(degrees = 5f * wave(t, 1), pivot = from) {
                drawPath(MOON_PATH, MOON_BRUSH)
            }
            val stars = arrayOf(Offset(70f, 70f), Offset(78f, 34f), Offset(34f, 26f))
            stars.forEachIndexed { i, at ->
                val glow = 0.5f + 0.5f * wave(t + i / 3f, 2)
                sparkle(at, 3.4f + 1.2f * glow, alpha = 0.35f + 0.65f * glow)
            }
        }
    }
}

private fun DrawScope.sparkle(at: Offset, radius: Float, alpha: Float) {
    val color = Color(0xFFFFF9C4).copy(alpha = alpha)
    drawLine(color, Offset(at.x, at.y - radius), Offset(at.x, at.y + radius), 1.8f, StrokeCap.Round)
    drawLine(color, Offset(at.x - radius, at.y), Offset(at.x + radius, at.y), 1.8f, StrokeCap.Round)
}

/** A cloud: three lobes on a flat base, [size] times its full 100-unit width, moved by [dx], [dy]. */
private fun DrawScope.cloud(
    dx: Float,
    dy: Float,
    size: Float,
    tint: Brush = CLOUD_BRUSH,
    alpha: Float = 1f
) {
    translate(left = dx, top = dy) {
        scale(size, size, pivot = Offset(50f, 50f)) {
            drawCircle(tint, radius = 15f, center = Offset(30f, 58f), alpha = alpha)
            drawCircle(tint, radius = 21f, center = Offset(50f, 46f), alpha = alpha)
            drawCircle(tint, radius = 16f, center = Offset(71f, 56f), alpha = alpha)
            drawRoundRect(
                tint,
                topLeft = Offset(14f, 56f),
                size = Size(72f, 20f),
                cornerRadius = CornerRadius(10f, 10f),
                alpha = alpha
            )
        }
    }
}

/** Slanted rain streaks that fall out from under the cloud and fade as they land. */
private fun DrawScope.rain(
    t: Float,
    drops: Int,
    length: Float,
    width: Float,
    speed: Int,
    xs: FloatArray? = null
) {
    for (i in 0 until drops) {
        val x = xs?.get(i) ?: (24f + 52f * i / max(1, drops - 1))
        val p = frac(t * speed + (i * 0.37f))
        val y = 64f + 28f * p
        val a = sin(p * PI.toFloat())
        drawLine(
            DROP.copy(alpha = a),
            start = Offset(x, y),
            end = Offset(x - length * 0.28f, y + length),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
}

/** Snowflakes that sway down from under the cloud. */
private fun DrawScope.snow(
    t: Float,
    flakes: Int,
    xs: FloatArray? = null,
    phaseShift: Float = 0f,
    top: Float = 64f,
    bottom: Float = 92f,
    radius: Float = 3.6f
) {
    for (i in 0 until flakes) {
        val x0 = xs?.get(i) ?: (22f + 56f * i / max(1, flakes - 1))
        val p = frac(t + i * 0.29f + phaseShift)
        val x = x0 + 4f * wave(p, 1)
        val y = top + (bottom - top) * p
        val a = sin(p * PI.toFloat())
        val color = FLAKE.copy(alpha = a)
        rotate(degrees = p * 120f, pivot = Offset(x, y)) {
            for (k in 0 until 3) {
                rotate(degrees = k * 60f, pivot = Offset(x, y)) {
                    drawLine(
                        color,
                        Offset(x, y - radius), Offset(x, y + radius),
                        strokeWidth = radius * 0.36f, cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/** Hail: hard white pellets that drop quickly. */
private fun DrawScope.hail(t: Float, xs: FloatArray) {
    xs.forEachIndexed { i, x ->
        val p = frac(t * 2f + i * 0.5f)
        drawCircle(HAIL.copy(alpha = sin(p * PI.toFloat())), radius = 3.4f, center = Offset(x, 66f + 28f * p))
    }
    val p = frac(t * 2f + 0.25f)
    drawCircle(HAIL.copy(alpha = sin(p * PI.toFloat())), radius = 3f, center = Offset(88f, 66f + 28f * p))
}

/** Bands of mist under the cloud, sliding against each other. */
private fun DrawScope.mist(t: Float) {
    val rows = arrayOf(Triple(66f, 60f, 1), Triple(77f, 72f, -1), Triple(88f, 52f, 1))
    rows.forEachIndexed { i, (y, width, dir) ->
        val x = 50f - width / 2f + dir * 6f * wave(t + i * 0.2f, 1)
        drawLine(MIST, Offset(x, y), Offset(x + width, y), strokeWidth = 5.6f, cap = StrokeCap.Round)
    }
}

/** Lightning: dim between strikes, then a stutter of two quick flashes. */
private fun DrawScope.bolt(t: Float) {
    val flash = max(0f, wave(t, 3)).let { it * it * it * it }
    val glow = 0.5f + 0.5f * flash
    drawCircle(
        Color(0xFFFFEE58).copy(alpha = 0.32f * flash),
        radius = 26f * (0.7f + 0.3f * flash),
        center = Offset(54f, 72f)
    )
    drawPath(BOLT_PATH, BOLT, alpha = glow)
}
