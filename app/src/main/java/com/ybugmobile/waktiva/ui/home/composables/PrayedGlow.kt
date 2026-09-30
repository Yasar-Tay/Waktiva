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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
 * with four node gems on it, sparks orbiting the rim, a rim of light running round it and a
 * breathing gold bloom.
 *
 * Marking it plays the unlock: motes of light gather into the badge, then it bursts in a white
 * flash with a shockwave, rays and a spray of sparks while the rune circle swells in. Taking the
 * mark back lets it all fade. The glow only draws around the badge, which shows through its middle.
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
        while (true) {
            withFrameNanos { clock.floatValue = from + (it - start) / 1_000_000_000f }
        }
    }

    val size = with(LocalDensity.current) { (badgeRadius * 2 * GLOW_REACH).toDp() }
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val g = glow.value
                val c = charge.value
                val b = burst.value
                val t = clock.floatValue
                val tone = lerp(color, PrayedGold, 0.6f)
                val hot = lerp(tone, Color.White, 0.55f)
                if (c < 1f) drawCharge(badgeRadius, c, hot)
                if (g > 0.01f) drawActive(badgeRadius, g, t, tone, hot)
                if (b < 1f) drawBurst(badgeRadius, b, tone, hot)
            }
    )
}

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
 * [t] is the clock, in seconds, that turns and breathes it.
 */
private fun DrawScope.drawActive(r: Float, g: Float, t: Float, tone: Color, hot: Color) {
    val alpha = g.coerceIn(0f, 1f)
    val breath = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 2.6f)
    val outer = r * 2.4f * (0.6f + 0.4f * g) * (0.95f + 0.07f * breath)

    // The bloom starts at the badge's edge, breathes, and leaves the badge itself clear.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            (r * 0.92f / outer) to Color.Transparent,
            (r * 1.04f / outer) to tone.copy(alpha = (0.75f + 0.2f * breath) * alpha),
            (r * 1.45f / outer) to tone.copy(alpha = (0.22f + 0.14f * breath) * alpha),
            1f to Color.Transparent,
            center = center,
            radius = outer
        ),
        radius = outer,
        center = center
    )

    // The rune circle: a dashed ring of runes turning slowly, a hairline under it and four
    // node gems, as on a skill tree. It grows into place as the glow swells.
    val rune = r * RUNE_RING * (0.75f + 0.25f * g)
    val spin = t * 18f
    drawCircle(tone.copy(alpha = 0.35f * alpha), rune, center, style = Stroke(0.6.dp.toPx()))
    rotate(spin) {
        val dash = rune * 2f * PI.toFloat() / 36f
        drawCircle(
            tone.copy(alpha = 0.8f * alpha),
            rune,
            center,
            style = Stroke(
                width = 1.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 0.28f, dash * 0.22f, dash * 0.12f, dash * 0.38f))
            )
        )
        for (i in 0 until 4) {
            val a = (i * PI / 2).toFloat()
            gem(center + Offset(cos(a), sin(a)) * rune, 2.6.dp.toPx() * (0.8f + 0.2f * breath), hot, alpha)
        }
    }
    // A finer ring turning the other way just inside it.
    rotate(-spin * 1.6f) {
        val inner = rune - 3.dp.toPx()
        val dash = inner * 2f * PI.toFloat() / 60f
        drawCircle(
            hot.copy(alpha = 0.45f * alpha),
            inner,
            center,
            style = Stroke(0.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 0.5f, dash * 0.5f)))
        )
    }

    // The rim: white and gold, with a spark of light running round it.
    drawCircle(Color.White.copy(alpha = 0.95f * alpha), r + 1.dp.toPx(), center, style = Stroke(1.6.dp.toPx()))
    drawCircle(tone.copy(alpha = alpha), r + 3.2.dp.toPx(), center, style = Stroke(1.2.dp.toPx()))
    rotate(t * 150f) {
        drawCircle(
            Brush.sweepGradient(
                0f to Color.Transparent,
                0.72f to Color.Transparent,
                0.97f to Color.White.copy(alpha = 0.95f * alpha),
                1f to Color.Transparent,
                center = center
            ),
            r + 2.1.dp.toPx(),
            center,
            style = Stroke(2.2.dp.toPx())
        )
    }

    // Motes orbiting just outside the rim, each with a fading trail.
    val orbit = r * 1.22f
    for (i in 0 until 3) {
        val speed = 1.1f + 0.35f * i
        val a = t * speed + i * 2.1f
        for (k in 4 downTo 0) {
            val back = a - k * 0.11f
            val at = center + Offset(cos(back), sin(back)) * (orbit + i * 1.2.dp.toPx())
            val fade = 1f - k / 5f
            drawCircle(
                (if (k == 0) Color.White else hot).copy(alpha = 0.9f * fade * alpha),
                (1.5f * fade + 0.3f).dp.toPx(),
                at
            )
        }
    }
}

/** A node gem on the rune circle: a small lit diamond with a glow. */
private fun DrawScope.gem(at: Offset, size: Float, hot: Color, alpha: Float) {
    drawCircle(
        Brush.radialGradient(listOf(hot.copy(alpha = 0.7f * alpha), Color.Transparent), at, size * 2.4f),
        size * 2.4f,
        at
    )
    val diamond = Path().apply {
        moveTo(at.x, at.y - size)
        lineTo(at.x + size * 0.7f, at.y)
        lineTo(at.x, at.y + size)
        lineTo(at.x - size * 0.7f, at.y)
        close()
    }
    drawPath(diamond, hot.copy(alpha = alpha))
    drawPath(diamond, Color.White.copy(alpha = 0.9f * alpha), style = Stroke(0.7.dp.toPx()))
}
