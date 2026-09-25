package com.ybugmobile.waktiva.ui.qibla.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Nur yağmuru, the rain of light over the compass while it points to the Qibla:
 * - a soft beam of light falling onto the compass from above, breathing slowly;
 * - god rays inside it that sway and shimmer;
 * - motes of light at three depths that drift down, sway, twinkle and widen as they fall, then
 *   gather towards the centre, the nearest ones glinting;
 * - light gathered at the centre of the compass;
 * - at the moment of alignment, a wave of light spreading out from the centre with a flash.
 *
 * It fades in over 1.2 s and out over 0.8 s. Everything is added to what lies beneath, so it
 * brightens the compass rather than covering it. One frame clock drives it all and is read
 * while drawing, so the effect never recomposes, and it stops when the effect has faded out.
 */
@Composable
fun QiblaAlignmentEffect(
    isAligned: Boolean,
    alignmentColor: Color,
    modifier: Modifier = Modifier
) {
    val presence = remember { Animatable(0f) }
    val now = remember { mutableLongStateOf(0L) }
    val alignedAt = remember { mutableLongStateOf(0L) }

    LaunchedEffect(isAligned) {
        if (isAligned) {
            val t = withFrameNanos { it }
            alignedAt.longValue = t
            now.longValue = t
        }
        presence.animateTo(if (isAligned) 1f else 0f, tween(if (isAligned) 1200 else 800))
    }

    val showing by remember { derivedStateOf { presence.value > 0f } }
    if (!showing) return

    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now.longValue = it }
    }

    // Its own layer, so redrawing the light every frame doesn't redraw the compass beneath it.
    // The layer isn't offscreen, so the light still adds onto the compass.
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer()
            .drawWithCache {
                val light = NurLight(size.center, nurRadius(size.minDimension, density), density, alignmentColor)
                onDrawBehind {
                    val t = now.longValue
                    light.draw(this, presence.value, seconds(t), seconds(t - alignedAt.longValue))
                }
            }
    )
}

/** [nanos] as seconds, kept small enough for float precision in the long run. */
private fun seconds(nanos: Long): Float = (nanos % 3_600_000_000_000L) / 1e9f

/** Radius of the compass card the light falls on, matching the compasses' geometry. */
private fun nurRadius(minDimension: Float, density: Float): Float =
    (min(minDimension, 340f * density) / 2f - 20f * density) * 0.87f

private class Mote(
    val depth: Int,
    val x: Float,
    val offset: Float,
    val speed: Float,
    val size: Float,
    val sway: Float,
    val frequency: Float,
    val twinkle: Float
)

private val Motes: List<Mote> = run {
    val random = Random(97)
    List(70) { i ->
        val depth = i % 3
        Mote(
            depth = depth,
            x = random.nextFloat() * 2f - 1f,
            offset = random.nextFloat(),
            speed = floatArrayOf(0.07f, 0.1f, 0.14f)[depth] * (0.8f + 0.4f * random.nextFloat()),
            size = floatArrayOf(0.6f, 1f, 1.6f)[depth] * (0.7f + 0.6f * random.nextFloat()),
            sway = 0.03f + 0.05f * random.nextFloat(),
            frequency = 0.6f + 1.2f * random.nextFloat(),
            twinkle = random.nextFloat() * 6.2831855f
        )
    }
}

private val DepthBrightness = floatArrayOf(0.45f, 0.7f, 1f)

/**
 * The light of the effect for a compass card of radius [r] centred on [c], warmed towards
 * [tint]. Brushes are built once here and faded with the draw's alpha, so a frame allocates
 * nothing but the paths of the swaying rays.
 */
private class NurLight(private val c: Offset, private val r: Float, private val dp: Float, private val tint: Color) {
    private fun warm(color: Color) = lerp(color, tint, 0.25f)

    private val top = c.y - r * 2.4f
    private val bottom = c.y + r * 0.25f
    private val beamWidth = r * 0.95f

    // The beam is two soft glows squashed sideways: a tall, narrow column falling from the sky and
    // a wider pool where it lands on the compass. Each fades out to nothing at its edge, so the
    // beam has no outline, least of all where it reaches the middle of the compass.
    private class Glow(val centre: Offset, val reach: Float, val squash: Float, val brush: Brush)

    private val beamColor = warm(Color(0xFFFFECBE))
    private fun glow(centre: Offset, reach: Float, width: Float, alpha: Float) = Glow(
        centre, reach, width / reach,
        Brush.radialGradient(
            0f to beamColor.copy(alpha = alpha),
            0.35f to beamColor.copy(alpha = alpha * 0.55f),
            0.7f to beamColor.copy(alpha = alpha * 0.18f),
            1f to beamColor.copy(alpha = 0f),
            center = centre,
            radius = reach
        )
    )
    private val beam = listOf(
        glow(Offset(c.x, c.y - r * 1.05f), reach = r * 1.5f, width = beamWidth * 0.32f, alpha = 0.13f),
        glow(Offset(c.x, c.y - r * 0.25f), reach = r * 1.05f, width = beamWidth * 0.55f, alpha = 0.12f)
    )

    private val rayColor = warm(Color(0xFFFFF4D6))
    private val rayBrush = Brush.verticalGradient(
        0f to rayColor.copy(alpha = 0f),
        0.3f to rayColor,
        0.8f to rayColor.copy(alpha = 0.5f),
        1f to rayColor.copy(alpha = 0f),
        startY = top,
        endY = bottom
    )
    private val ray = Path()

    private val moteRadii = FloatArray(Motes.size) { Motes[it].size * 1.1f * dp }
    private val moteGlows = Array(Motes.size) { i ->
        Brush.radialGradient(
            0f to warm(Color(0xFFFFF8E1)),
            0.25f to warm(Color(0xFFFFE296)).copy(alpha = 0.5f),
            1f to warm(Color(0xFFFFDC8C)).copy(alpha = 0f),
            center = Offset.Zero,
            radius = moteRadii[i] * 5f
        )
    }
    private val glintColor = Color(0xFFFFFCF0)

    private val centreColor = warm(Color(0xFFFFECB4))
    private val centreGlow = Brush.radialGradient(
        listOf(centreColor.copy(alpha = 0.28f), centreColor.copy(alpha = 0f)),
        center = c,
        radius = r * 0.55f
    )
    private val flashColor = warm(Color(0xFFFFF4D6))
    private val flash = Brush.radialGradient(listOf(flashColor.copy(alpha = 0.5f), flashColor.copy(alpha = 0f)), center = c, radius = r)

    fun draw(scope: DrawScope, amount: Float, t: Float, sinceAligned: Float) = with(scope) {
        if (amount <= 0f) return@with
        val add = BlendMode.Plus

        // The beam, breathing
        val breath = 1f + 0.04f * sin(t * 1.3f)
        for (g in beam) {
            scale(g.squash * breath, 1f, pivot = g.centre) {
                drawCircle(g.brush, g.reach, g.centre, alpha = amount, blendMode = add)
            }
        }

        // God rays
        for (k in 0 until 6) {
            val sway = sin(t * (0.5f + k * 0.13f) + k * 1.7f) * r * 0.12f
            val x = c.x + (k - 2.5f) * r * 0.1f + sway
            val width = r * (0.02f + 0.015f * (k % 2))
            val alpha = (0.07f + 0.05f * sin(t * 1.7f + k)) * amount
            val topX = c.x + (x - c.x) * 0.2f
            ray.rewind()
            ray.moveTo(topX - width * 0.3f, top)
            ray.lineTo(topX + width * 0.3f, top)
            ray.lineTo(x + width, bottom)
            ray.lineTo(x - width, bottom)
            ray.close()
            drawPath(ray, rayBrush, alpha = alpha, blendMode = add)
        }

        // Motes: they fall, widen, then gather towards the centre
        val moteTop = c.y - r * 1.6f
        for (i in Motes.indices) {
            val m = Motes[i]
            val p = (m.offset + t * m.speed) % 1f
            val life = min(1f, p / 0.12f) * min(1f, (1f - p) / 0.18f)
            val twinkle = 0.6f + 0.4f * sin(t * (2f + m.frequency) + m.twinkle)
            val alpha = life * twinkle * DepthBrightness[m.depth] * amount
            if (alpha <= 0.01f) continue
            val y = moteTop + (bottom - moteTop) * p
            val spread = r * (0.25f + 0.55f * p) * (1f - max(0f, (p - 0.75f) / 0.25f) * 0.8f)
            val x = c.x + m.x * spread + sin(t * m.frequency + m.twinkle) * r * m.sway
            val radius = moteRadii[i]
            translate(x, y) { drawCircle(moteGlows[i], radius * 5f, Offset.Zero, alpha = alpha, blendMode = add) }
            // The nearest, brightest motes glint.
            if (m.depth == 2 && twinkle > 0.93f) {
                val reach = radius * 6f
                val glint = glintColor.copy(alpha = alpha * 0.9f)
                drawLine(glint, Offset(x - reach, y), Offset(x + reach, y), 0.8f * dp, blendMode = add)
                drawLine(glint, Offset(x, y - reach), Offset(x, y + reach), 0.8f * dp, blendMode = add)
            }
        }

        // The light gathered at the centre
        drawCircle(centreGlow, r * 0.55f, c, alpha = amount, blendMode = add)

        // The wave of light at the moment of alignment
        if (sinceAligned in 0f..WaveSeconds) {
            val w = sinceAligned / WaveSeconds
            val fade = (1f - w) * 0.6f
            drawCircle(
                centreColor.copy(alpha = fade),
                r * (0.2f + 1.1f * w),
                c,
                style = Stroke(r * 0.06f * (1f - w) + dp),
                blendMode = add
            )
            drawCircle(flash, r, c, alpha = fade, blendMode = add)
        }
    }

    private companion object {
        const val WaveSeconds = 1.6f
    }
}
