package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The glow a prayer's badge on the day circle wears once the prayer is marked as prayed, drawn
 * like a skill in a game's skill tree coming alive: a rune circle turning slowly round the badge
 * with four node gems on it, two motes orbiting the rim, a rim of light running round it, a
 * breathing gold bloom, and the badge lit from within under a twinkling glint.
 *
 * Marking it plays the unlock: motes of light gather into the badge, then it bursts in a white
 * flash with a shockwave, rays and a spray of sparks while the rune circle swells in. Taking the
 * mark back lets it all fade. Over the badge the light stays faint in the middle, so its sign reads.
 *
 * [prayed] is null until the log has loaded, so a prayer already marked lights up without the
 * unlock. [badgeRadius] is the badge's radius in pixels; the glow centres on this composable,
 * which sizes itself to hold it.
 */
@Composable
fun PrayedGlow(
    prayed: Boolean?,
    color: Color,
    badgeRadius: Float,
    modifier: Modifier = Modifier
) {
    val glow = remember { Animatable(if (prayed == true) 1f else 0f) }
    val charge = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    // What the glow shows now, so only a change plays: coming back to the screen doesn't.
    var shown by remember { mutableStateOf(prayed) }

    LaunchedEffect(prayed) {
        if (prayed == null || prayed == shown) return@LaunchedEffect
        val wasLoaded = shown != null
        shown = prayed
        if (!wasLoaded) {
            glow.snapTo(if (prayed) 1f else 0f)
        } else if (prayed) {
            charge.snapTo(0f)
            charge.animateTo(1f, tween(CHARGE_MILLIS, easing = FastOutSlowInEasing))
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(1300, easing = LinearEasing))
            }
            glow.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessLow))
        } else {
            // An unlock cut short mustn't hang frozen on the badge.
            charge.snapTo(1f)
            burst.snapTo(1f)
            glow.animateTo(0f, tween(450))
        }
    }

    // The idle motion's clock, in seconds; it only ticks while something shows.
    val active by remember { derivedStateOf { glow.value > 0.01f || charge.value < 1f || burst.value < 1f } }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val from = clock.floatValue
        val start = withFrameNanos { it }
        var ticked = start
        while (true) {
            withFrameNanos { now ->
                // The unlock plays at the full frame rate. Once it has settled the idle motion
                // only moves on about every IDLE_FRAME_NANOS, so the glow isn't redrawn on every
                // frame of a fast screen.
                val unlocking = glow.isRunning || charge.value < 1f || burst.value < 1f
                if (unlocking || now - ticked >= IDLE_FRAME_NANOS) {
                    clock.floatValue = from + (now - start) / 1_000_000_000f
                    ticked = now
                }
            }
        }
    }

    val size = with(LocalDensity.current) { (badgeRadius * 2 * GLOW_REACH).toDp() }
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val tone = lerp(color, PrayedGold, 0.6f)
                val hot = lerp(tone, Color.White, 0.55f)
                val parts = GlowParts(badgeRadius, this.size.center, tone, hot, density)
                onDrawBehind {
                    val g = glow.value
                    val c = charge.value
                    val b = burst.value
                    val t = clock.floatValue
                    if (c < 1f) drawCharge(badgeRadius, c, hot)
                    if (g > 0.01f) drawActive(badgeRadius, g, t, parts)
                    if (b < 1f) drawBurst(badgeRadius, b, tone, hot)
                }
            }
    )
}

/**
 * How long the idle glow holds each step of its motion: about 30 a second, smooth for its slow
 * turning and breathing, where a 120 Hz screen would otherwise redraw it four times as often.
 */
private const val IDLE_FRAME_NANOS = 30_000_000L

/**
 * The parts of the active glow that stay the same from frame to frame, for a badge of radius [r]
 * centred on [center]: the glint's and gems' shapes and glows at unit size, which are moved and
 * scaled into place as they're drawn, and, once the glow has swollen in, the rune circles' dashes
 * and the rim's spark. Built once per size and colour instead of on every frame.
 */
private class GlowParts(r: Float, private val center: Offset, val tone: Color, val hot: Color, private val dp: Float) {
    /** A four-pointed star reaching 1 from its centre to each point. */
    val star = Path().apply {
        val waist = 0.12f
        moveTo(0f, -1f)
        lineTo(waist, -waist)
        lineTo(1f, 0f)
        lineTo(waist, waist)
        lineTo(0f, 1f)
        lineTo(-waist, waist)
        lineTo(-1f, 0f)
        lineTo(-waist, -waist)
        close()
    }
    val starGlow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), Offset.Zero, STAR_GLOW)

    /** A gem's diamond, reaching 1 from its centre to its top and bottom. */
    val diamond = Path().apply {
        moveTo(0f, -1f)
        lineTo(0.7f, 0f)
        lineTo(0f, 1f)
        lineTo(-0.7f, 0f)
        close()
    }
    val gemGlow = Brush.radialGradient(listOf(hot.copy(alpha = 0.7f), Color.Transparent), Offset.Zero, GEM_GLOW)

    private val settledRune = buildRuneStroke(r * RUNE_RING)
    private val settledInnerRune = buildInnerRuneStroke(r * RUNE_RING - 3f * dp)
    private val settledRimSpark = buildRimSpark(1f)

    /**
     * The badge's light from within, steady and breathing: the breathing part, drawn at the
     * breath's strength over the steady one, makes up the light at any breath. Both badge-sized.
     */
    val innerLight = Brush.radialGradient(
        0f to hot.copy(alpha = 0.16f),
        0.55f to tone.copy(alpha = 0.1f),
        0.82f to tone.copy(alpha = 0.32f),
        1f to hot.copy(alpha = 0.65f),
        center = center,
        radius = r
    )
    val innerBreath = Brush.radialGradient(
        0f to hot.copy(alpha = 0.1f),
        0.55f to tone.copy(alpha = 0f),
        0.82f to tone.copy(alpha = 0.14f),
        1f to hot.copy(alpha = 0.2f),
        center = center,
        radius = r
    )

    /** The rune circle's dashes at [rune]; the same ones each frame once the glow has [settled]. */
    fun runeStroke(rune: Float, settled: Boolean) = if (settled) settledRune else buildRuneStroke(rune)

    /** The finer ring's dashes at [inner]; the same ones each frame once the glow has [settled]. */
    fun innerRuneStroke(inner: Float, settled: Boolean) = if (settled) settledInnerRune else buildInnerRuneStroke(inner)

    /** The spark running round the rim at [alpha]; the same one each frame at full strength. */
    fun rimSpark(alpha: Float, settled: Boolean) = if (settled) settledRimSpark else buildRimSpark(alpha)

    private fun buildRuneStroke(rune: Float): Stroke {
        val dash = rune * 2f * PI.toFloat() / 36f
        return Stroke(
            width = 1.8f * dp,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 0.28f, dash * 0.22f, dash * 0.12f, dash * 0.38f))
        )
    }

    private fun buildInnerRuneStroke(inner: Float): Stroke {
        val dash = inner * 2f * PI.toFloat() / 60f
        return Stroke(0.8f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 0.5f, dash * 0.5f)))
    }

    private fun buildRimSpark(alpha: Float) = Brush.sweepGradient(
        0f to Color.Transparent,
        0.72f to Color.Transparent,
        0.97f to Color.White.copy(alpha = 0.95f * alpha),
        1f to Color.Transparent,
        center = center
    )
}

/** Radii of the glint's and a gem's glows, against a glint and a gem of size 1. */
private const val STAR_GLOW = 0.45f
private const val GEM_GLOW = 2.4f

/** How far the glow reaches, in badge radii, including the sparks at their widest. */
private const val GLOW_REACH = 3.6f

/** How long the motes take to gather before the unlock bursts. */
private const val CHARGE_MILLIS = 320

/** Where the rune circle turns round the badge, in badge radii; inside the time labels. */
private const val RUNE_RING = 1.52f

/** The motes gathering into the badge, [c] from 0 to 1, before it bursts. */
private fun DrawScope.drawCharge(r: Float, c: Float, hot: Color) {
    val motes = 8
    val reach = r * (2.9f - 1.9f * c)
    val swirl = c * 1.4f
    for (i in 0 until motes) {
        val a = (i * 2f * PI / motes).toFloat() + swirl
        val at = center + Offset(cos(a), sin(a)) * reach
        val tail = center + Offset(cos(a - 0.35f), sin(a - 0.35f)) * (reach + r * 0.35f * (1f - c))
        drawLine(hot.copy(alpha = 0.55f * c), tail, at, 1.4.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * c), 1.6.dp.toPx(), at)
    }
    // The rim tightens and brightens as it charges.
    drawCircle(hot.copy(alpha = 0.8f * c), r * (1.9f - 0.8f * c), center, style = Stroke((0.8f + 1.4f * c).dp.toPx()))
}

/**
 * The unlock bursting, [b] from 0 to 1: a white flash over the badge, a shockwave, rays turning
 * out from it and sparks flying off.
 */
private fun DrawScope.drawBurst(r: Float, b: Float, tone: Color, hot: Color) {
    val fade = 1f - b

    // The flash: a white bloom over the whole badge that dies away fast.
    val flash = (1f - b / 0.3f).coerceIn(0f, 1f)
    if (flash > 0f) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.95f * flash),
                0.55f to hot.copy(alpha = 0.7f * flash),
                1f to Color.Transparent,
                center = center,
                radius = r * 2.2f
            ),
            r * 2.2f,
            center
        )
    }

    // Rays: long thin wedges of light, turning a little as they fade.
    val rays = (1f - b / 0.65f).coerceIn(0f, 1f)
    if (rays > 0f) {
        rotate(b * 40f) {
            val count = 12
            for (i in 0 until count) {
                val a = (i * 2f * PI / count).toFloat()
                val long = if (i % 2 == 0) 3.3f else 2.4f
                val dir = Offset(cos(a), sin(a))
                val side = Offset(-dir.y, dir.x) * (r * 0.16f)
                val base = center + dir * (r * 1.05f)
                val tip = center + dir * (r * (1.2f + (long - 1.2f) * (0.4f + 0.6f * b)))
                val ray = Path().apply {
                    moveTo(base.x + side.x, base.y + side.y)
                    lineTo(tip.x, tip.y)
                    lineTo(base.x - side.x, base.y - side.y)
                    close()
                }
                drawPath(
                    ray,
                    Brush.linearGradient(listOf(hot.copy(alpha = 0.85f * rays), Color.Transparent), base, tip),
                    blendMode = BlendMode.Plus
                )
            }
        }
    }

    // The shockwave: a bright ring racing out, with a thinner white one inside it.
    drawCircle(
        color = tone.copy(alpha = 0.95f * fade),
        radius = r * (1.05f + 2.3f * FastOutSlowInEasing.transform(b)),
        center = center,
        style = Stroke((0.6f + 3.4f * fade).dp.toPx())
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.7f * fade * fade),
        radius = r * (1.05f + 1.6f * FastOutSlowInEasing.transform(b)),
        center = center,
        style = Stroke((0.5f + 1.6f * fade).dp.toPx())
    )

    // Sparks: short streaks flung out at uneven angles and speeds, slowing as they go.
    val fly = FastOutSlowInEasing.transform(b)
    for (i in 0 until SPARKS) {
        val a = (i * 2f * PI / SPARKS).toFloat() + SPARK_JITTER[i % SPARK_JITTER.size]
        val speed = SPARK_SPEED[i % SPARK_SPEED.size]
        val dir = Offset(cos(a), sin(a))
        val head = center + dir * (r * (1.1f + 2.2f * speed * fly))
        val tail = center + dir * (r * (1.1f + 2.2f * speed * fly * 0.72f))
        val life = (fade * 1.3f - (1f - speed) * 0.3f).coerceIn(0f, 1f)
        if (life <= 0f) continue
        drawLine(hot.copy(alpha = life), tail, head, (1.8f * life + 0.4f).dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = life), (1.1f * life + 0.3f).dp.toPx(), head)
    }
}

private const val SPARKS = 16
private val SPARK_JITTER = floatArrayOf(0.05f, -0.12f, 0.1f, -0.04f, 0.14f, -0.09f, 0.02f, -0.15f)
private val SPARK_SPEED = floatArrayOf(1f, 0.72f, 0.9f, 0.6f, 0.95f, 0.78f, 0.66f, 0.86f)

/**
 * The skill once it's active, [g] from 0 (none) to 1 (full), briefly above 1 as it swells in;
 * [t] is the clock, in seconds, that turns and breathes it. What stays the same between frames
 * comes from [parts].
 */
private fun DrawScope.drawActive(r: Float, g: Float, t: Float, parts: GlowParts) {
    val tone = parts.tone
    val hot = parts.hot
    val alpha = g.coerceIn(0f, 1f)
    // Swollen into place: the rune circle and the rim's spark are at their full size and strength.
    val settled = g == 1f
    val breath = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 2.6f)
    val outer = r * 2.4f * (0.6f + 0.4f * g) * (0.95f + 0.07f * breath)

    // The bloom starts at the badge's edge and breathes.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            (r * 0.92f / outer) to Color.Transparent,
            (r * 1.04f / outer) to tone.copy(alpha = (0.82f + 0.18f * breath) * alpha),
            (r * 1.5f / outer) to tone.copy(alpha = (0.28f + 0.12f * breath) * alpha),
            1f to Color.Transparent,
            center = center,
            radius = outer
        ),
        radius = outer,
        center = center
    )

    // The badge itself lit from within, as the burst left it: light pooled under the glass,
    // strongest round its edge, breathing with the bloom. Faint at the middle so the sign reads.
    // Added light, so it's the steady part plus the breathing part at the breath's strength.
    drawCircle(parts.innerLight, r, center, alpha = alpha, blendMode = BlendMode.Plus)
    drawCircle(parts.innerBreath, r, center, alpha = breath * alpha, blendMode = BlendMode.Plus)
    // A star glint on the glass that flares and fades, a little out of step with the breath.
    val twinkle = (0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 1.9f + 1.3f)).let { it * it }
    glint(parts, center + Offset(-0.42f, -0.42f) * r, r * (0.38f + 0.22f * twinkle), (0.35f + 0.65f * twinkle) * alpha)

    // The rune circle: a dashed ring of runes turning slowly, a hairline under it and four
    // node gems, as on a skill tree. It grows into place as the glow swells.
    val rune = r * RUNE_RING * (0.75f + 0.25f * g)
    val spin = t * 18f
    drawCircle(tone.copy(alpha = 0.35f * alpha), rune, center, style = Stroke(0.6.dp.toPx()))
    rotate(spin) {
        drawCircle(tone.copy(alpha = 0.8f * alpha), rune, center, style = parts.runeStroke(rune, settled))
        val gemSize = 2.6.dp.toPx() * (0.8f + 0.2f * breath)
        for (i in 0 until 4) {
            val a = (i * PI / 2).toFloat()
            gem(parts, center + Offset(cos(a), sin(a)) * rune, gemSize, alpha)
        }
    }
    // A finer ring turning the other way just inside it.
    rotate(-spin * 1.6f) {
        val inner = rune - 3.dp.toPx()
        drawCircle(hot.copy(alpha = 0.45f * alpha), inner, center, style = parts.innerRuneStroke(inner, settled))
    }

    // The rim: white and gold, with a spark of light running round it.
    drawCircle(Color.White.copy(alpha = 0.95f * alpha), r + 1.dp.toPx(), center, style = Stroke(1.6.dp.toPx()))
    drawCircle(tone.copy(alpha = alpha), r + 3.2.dp.toPx(), center, style = Stroke(1.2.dp.toPx()))
    rotate(t * 150f) {
        drawCircle(parts.rimSpark(alpha, settled), r + 2.1.dp.toPx(), center, style = Stroke(2.2.dp.toPx()))
    }

    // Two motes orbiting just outside the rim, each with a fading trail.
    val orbit = r * 1.22f
    for (i in 0 until 2) {
        val speed = 1.1f + 0.45f * i
        val a = t * speed + i * PI.toFloat()
        for (k in 3 downTo 0) {
            val back = a - k * 0.12f
            val at = center + Offset(cos(back), sin(back)) * (orbit + i * 1.2.dp.toPx())
            val fade = 1f - k / 4f
            drawCircle(
                (if (k == 0) Color.White else hot).copy(alpha = 0.9f * fade * alpha),
                (1.5f * fade + 0.3f).dp.toPx(),
                at
            )
        }
    }
}

/** A four-pointed star of light [size] across from its centre to a point, at [at]. */
private fun DrawScope.glint(parts: GlowParts, at: Offset, size: Float, alpha: Float) {
    withTransform({
        translate(at.x, at.y)
        scale(size, size, pivot = Offset.Zero)
    }) {
        drawCircle(parts.starGlow, STAR_GLOW, Offset.Zero, alpha = alpha)
        drawPath(parts.star, Color.White.copy(alpha = alpha))
    }
}

/** A node gem on the rune circle, [size] from its centre to its top: a small lit diamond with a glow. */
private fun DrawScope.gem(parts: GlowParts, at: Offset, size: Float, alpha: Float) {
    withTransform({
        translate(at.x, at.y)
        scale(size, size, pivot = Offset.Zero)
    }) {
        drawCircle(parts.gemGlow, GEM_GLOW, Offset.Zero, alpha = alpha)
        drawPath(parts.diamond, parts.hot.copy(alpha = alpha))
        // The outline's width is set against the scale, so it stays a fixed hairline.
        drawPath(parts.diamond, Color.White.copy(alpha = 0.9f * alpha), style = Stroke(0.7.dp.toPx() / size))
    }
}
