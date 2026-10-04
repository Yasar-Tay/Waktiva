package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pale of each prayer's colour: the heart of its star, and its stretch of the constellation's
 * line. The same as its lane in the galaxy.
 */
internal val PrayerType.paleColor: Color
    get() = when (this) {
        PrayerType.FAJR -> Color(0xFFD6F0FF)
        PrayerType.DHUHR -> Color(0xFFFFFCE0)
        PrayerType.ASR -> Color(0xFFFFEBD1)
        PrayerType.MAGHRIB -> Color(0xFFF6E0FA)
        PrayerType.ISHA -> Color(0xFFE6E9FF)
        PrayerType.SUNRISE -> StarWhite
    }

/**
 * A prayer of the day as its own star, at [center]. Lit, each has its hour's look: Fajr the morning
 * star with its long level beam, Dhuhr the sun at its height with twelve rays, Asr six points and a
 * shadow growing long, Maghrib a red horizon glowing under it, Isha silver with the crescent beside
 * it. Before it's lit, its shape in outline: in its colour, breathing, while its time is on; faint
 * while it's still to come. One gone unmarked is eclipsed, a dark disc ringed in coral.
 *
 * [scale] sizes the lit star, [twinkle] (0 to 1) sets its rays' shimmer, [pulse] (0 to 1) the ring
 * calling round the one whose time is on, and [ignite] (0 to 1, 1 settled) the flash and the sparks
 * of a star just lit. Sizes are in dp: the lit stars reach about 30 from the centre.
 */
internal fun DrawScope.prayerStar(
    type: PrayerType,
    status: PrayerLogStatus,
    center: Offset,
    scale: Float,
    twinkle: Float,
    pulse: Float,
    ignite: Float,
    isDay: Boolean
) {
    val u = 1.dp.toPx()
    val accent = type.accentColor
    // On a light sky, a soft shade under the star keeps its edge.
    if (isDay) {
        drawCircle(Brush.radialGradient(listOf(Color(0x59060E2C), Color.Transparent), center, 22f * u), 22f * u, center)
    }
    when (status) {
        PrayerLogStatus.PRAYED -> {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center, 40f * u), 40f * u, center)
            withTransform({
                scale(scale, scale, center)
                rotate(-70f * (1f - ignite), center)
            }) {
                litStar(type, center, u, twinkle)
            }
        }
        PrayerLogStatus.ACTIVE -> {
            drawCircle(NowLilac.copy(alpha = 0.9f * (1f - pulse)), radius = 18f * u * (0.7f + 1.2f * pulse), center = center, style = Stroke(1.5f * u))
            val breath = 0.55f + 0.45f * sin(2f * PI.toFloat() * pulse)
            val shape = type.shape(center, 12f * u)
            drawPath(shape, accent.copy(alpha = 0.14f))
            drawPath(shape, accent.copy(alpha = 0.5f + 0.5f * breath), style = Stroke(1.3f * u, join = StrokeJoin.Round))
            drawCircle(accent, 1.8f * u, center)
        }
        PrayerLogStatus.MISSED -> {
            drawCircle(MissedCoral.copy(alpha = 0.22f), 11f * u, center, style = Stroke(3f * u))
            drawCircle(if (isDay) Color(0xD90A1030) else Color(0xEB03050D), 8f * u, center)
            drawCircle(MissedCoral, 8f * u, center, style = Stroke(1.2f * u))
        }
        PrayerLogStatus.UPCOMING, PrayerLogStatus.UNTRACKED -> {
            drawPath(
                type.shape(center, 9f * u),
                Color.White.copy(alpha = if (isDay) 0.85f else 0.38f),
                style = Stroke(0.8f * u, join = StrokeJoin.Round)
            )
        }
    }
    // Just lit: a ring flashing out, and sparks flying off.
    if (ignite < 1f) {
        val fade = 1f - ignite
        drawCircle(accent.copy(alpha = fade), radius = 20f * u * (0.3f + 2.3f * ignite), center = center, style = Stroke(2f * u))
        repeat(8) { k ->
            val a = 2f * PI.toFloat() * k / 8f + 0.3f
            val reach = 26f * u * ignite
            drawCircle(StarWhite.copy(alpha = fade), 1.3f * u, Offset(center.x + cos(a) * reach, center.y + sin(a) * reach))
        }
    }
}

/** The lit star of [type], its rays shimmering by [twinkle]. */
private fun DrawScope.litStar(type: PrayerType, c: Offset, u: Float, twinkle: Float) {
    val accent = type.accentColor
    val rays = Brush.radialGradient(
        0f to Color.White,
        0.22f to accent.copy(alpha = 0.95f),
        1f to accent.copy(alpha = 0f),
        center = c,
        radius = 30f * u
    )
    val body = Brush.radialGradient(
        0f to Color.White,
        0.5f to StarWhite,
        1f to type.paleColor,
        center = c,
        radius = 18f * u
    )
    val shimmer = 0.8f + 0.2f * twinkle
    val glint = 0.72f + 0.28f * twinkle
    fun beams(alpha: Float = 1f, build: Path.() -> Unit) {
        scale(shimmer, shimmer, c) { drawPath(Path().apply(build), rays, alpha = glint * alpha) }
    }
    val up = -PI.toFloat() / 2f
    val slant = -PI.toFloat() / 4f
    when (type) {
        PrayerType.FAJR -> {
            beams {
                addPath(lens(c, 34f * u, 1.7f * u))
                spikes(c, 2, 17f * u, 1.3f * u, up)
                spikes(c, 4, 8f * u, 0.9f * u, slant)
            }
            drawCircle(Color(0x59D6F0FF), 8f * u, c)
            drawCircle(Color.White, 4.8f * u, c)
        }
        PrayerType.DHUHR -> {
            beams {
                spikes(c, 6, 24f * u, 1.2f * u, up)
                spikes(c, 6, 15f * u, 1f * u, up + PI.toFloat() / 6f)
            }
            drawCircle(body, 7f * u, c)
            drawCircle(Color.White, 3.4f * u, c)
        }
        PrayerType.ASR -> {
            withTransform({
                rotate(35f, c)
                translate(16f * u, 0f)
            }) {
                drawPath(lens(c, 24f * u, 1.3f * u), rays, alpha = 0.8f * glint)
            }
            val star = starPolygon(c, 6, 15f * u, 7.5f * u)
            drawPath(star, body)
            drawPath(star, Color.White, style = Stroke(0.7f * u, join = StrokeJoin.Round))
            drawCircle(Color.White, 2.6f * u, c)
        }
        PrayerType.MAGHRIB -> {
            val under = Offset(c.x, c.y + 8f * u)
            scale(1f, 4.5f / 24f, under) {
                drawCircle(Brush.radialGradient(listOf(Color(0x8CFF8064), Color.Transparent), under, 24f * u), 24f * u, under)
            }
            beams { spikes(c, 4, 11f * u, 1f * u, slant) }
            drawPath(sparkle(c, 16f * u), body)
            drawCircle(Color.White, 2.6f * u, c)
        }
        else -> {
            beams(0.8f) { spikes(c, 4, 24f * u, 0.9f * u, up) }
            drawPath(sparkle(c, 15f * u), body)
            drawPath(crescent(Offset(c.x + 16f * u, c.y - 5f * u), 4.5f * u), MoonLight)
            drawCircle(Color.White, 2.4f * u, c)
        }
    }
}

/** The moon's pale light. */
internal val MoonLight = Color(0xFFF6F1DC)

/** A prayer's star in outline at radius [r]: Dhuhr's twelve points, Asr's six, the rest four. */
private fun PrayerType.shape(c: Offset, r: Float): Path = when (this) {
    PrayerType.DHUHR -> starPolygon(c, 12, r, r * 0.55f)
    PrayerType.ASR -> starPolygon(c, 6, r, r * 0.5f)
    else -> sparkle(c, r)
}

/** [n] thin rays out from [c], each a sliver [len] long and [w] wide at its foot, the first at [rot]. */
private fun Path.spikes(c: Offset, n: Int, len: Float, w: Float, rot: Float) {
    repeat(n) { k ->
        val a = rot + 2f * PI.toFloat() * k / n
        val co = cos(a)
        val si = sin(a)
        moveTo(c.x - si * w, c.y + co * w)
        lineTo(c.x + co * len, c.y + si * len)
        lineTo(c.x + si * w, c.y - co * w)
        close()
    }
}

/** A star of [n] points, [r1] out and [r2] in, the first point up. */
private fun starPolygon(c: Offset, n: Int, r1: Float, r2: Float): Path = Path().apply {
    for (k in 0 until 2 * n) {
        val a = PI.toFloat() * k / n - PI.toFloat() / 2f
        val r = if (k % 2 == 0) r1 else r2
        val x = c.x + r * cos(a)
        val y = c.y + r * sin(a)
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A level beam through [c], [len] each way and [h] thick at its middle. */
private fun lens(c: Offset, len: Float, h: Float): Path = Path().apply {
    moveTo(c.x - len, c.y)
    cubicTo(c.x - len / 3f, c.y - h, c.x + len / 3f, c.y - h, c.x + len, c.y)
    cubicTo(c.x + len / 3f, c.y + h, c.x - len / 3f, c.y + h, c.x - len, c.y)
    close()
}

/** A new moon of radius [r] at [center], lit on its outer edge. */
internal fun crescent(center: Offset, r: Float): Path {
    val moon = Path().apply { addOval(Rect(center, r)) }
    val bite = Path().apply { addOval(Rect(Offset(center.x + r * 0.42f, center.y - r * 0.18f), r * 0.86f)) }
    return Path().apply { op(moon, bite, PathOperation.Difference) }
}
