package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Lighting for the metal parts of the gear dials. There is one light; round parts reflect it
// anisotropically, the way turned and brushed metal does: a bright streak towards the light, a
// weaker one opposite it and dark bands in between. A sweep gradient draws that and, being
// fixed to the screen rather than the part, stays put while the part turns.

/**
 * Direction the light comes from, as a screen angle in radians (0 = from the right,
 * increasing clockwise, as sweep gradients and canvas angles run).
 */
internal class GearLight(val angle: Float) {
    /** Unit vector from a part towards the light. */
    val towards = Offset(cos(angle), sin(angle))

    companion object {
        /** Up and to the left, the classic studio light, used when the sun gives no direction. */
        const val DEFAULT_ANGLE = 3.926991f // 225°
        val Default = GearLight(DEFAULT_ANGLE)
    }
}

/**
 * Screen angle of the sunlight falling on the dials, from the sun's position and the device's
 * compass heading (all in degrees; azimuths clockwise from north).
 *
 * The phone is taken as held upright facing [heading], as the lens flare used to assume: the
 * sun's direction is projected onto the screen, so a high sun lights the dials from above and
 * a sun off to one side lights them from that side. When the sun is down, or so nearly straight
 * in front of or behind the phone that the projection has no clear direction, the light eases
 * back to [GearLight.DEFAULT_ANGLE].
 */
internal fun sunLightAngle(sunAzimuth: Float, sunAltitude: Float, heading: Float): Float {
    if (sunAltitude <= 0f) return GearLight.DEFAULT_ANGLE
    val relative = Math.toRadians((sunAzimuth - heading).toDouble())
    val altitude = Math.toRadians(sunAltitude.toDouble())
    val right = (cos(altitude) * sin(relative)).toFloat()
    val up = sin(altitude).toFloat()
    val sunAngle = atan2(-up, right)
    val clarity = ((hypot(right, up) - 0.2f) / 0.3f).coerceIn(0f, 1f)
    return blendAngle(GearLight.DEFAULT_ANGLE, sunAngle, clarity)
}

/**
 * Whether the gear dials' metal follows the real sun (see [sunLightAngle]); off, it is lit from
 * the upper left. While it is off, the home screen neither computes the sun's position nor
 * listens to the compass, since nothing else there uses them.
 */
internal const val SunLitDials = false

/** Angle [t] of the way from [from] to [to], turning the short way round. */
internal fun blendAngle(from: Float, to: Float, t: Float): Float {
    var delta = (to - from) % TAU
    if (delta > PI) delta -= TAU
    if (delta < -PI) delta += TAU
    return from + delta * t
}

/**
 * A metal's reflections around a round part, as sweep-gradient stops laid out for light from
 * [GearLight.DEFAULT_ANGLE] (its brightest stop sits at 0.625 of the turn), and turned to any
 * other light.
 *
 * Turning parts ask for the light turned back by their rotation every frame, so both the stops
 * and the brushes are cached per whole degree (and per centre): after the first turn nothing
 * is allocated, and each brush keeps its shader.
 */
internal class MetalSheen(private val base: List<Pair<Float, Color>>) {
    private val stopsByDegree = arrayOfNulls<Array<Pair<Float, Color>>>(360)
    private val brushes = HashMap<Long, Brush>()

    fun stops(light: GearLight): Array<Pair<Float, Color>> {
        val degree = degreeOf(light)
        return stopsByDegree[degree] ?: run {
            val shift = (degree - 225) / 360f
            Array(SAMPLES + 1) { i ->
                val at = i / SAMPLES.toFloat()
                at to colourAt(((at - shift) % 1f + 1f) % 1f)
            }.also { stopsByDegree[degree] = it }
        }
    }

    /** Sweep brush around [center] for this light. */
    fun brush(center: Offset, light: GearLight): Brush {
        val degree = degreeOf(light)
        val key = (center.x.toRawBits().toLong() shl 32) xor (center.y.toRawBits().toLong() shl 9) xor degree.toLong()
        return brushes.getOrPut(key) { Brush.sweepGradient(*stops(light), center = center) }
    }

    private fun degreeOf(light: GearLight) = Math.floorMod(Math.round(Math.toDegrees(light.angle.toDouble())).toInt(), 360)

    private fun colourAt(t: Float): Color {
        for (k in 1 until base.size) {
            val (end, endColour) = base[k]
            if (t <= end) {
                val (start, startColour) = base[k - 1]
                return lerp(startColour, endColour, if (end > start) (t - start) / (end - start) else 0f)
            }
        }
        return base.last().second
    }

    private companion object {
        const val SAMPLES = 48
    }
}

/** Linear light across a part of [radius] around [center]: [lit] on the side facing the light. */
private fun edgeLight(center: Offset, radius: Float, light: GearLight, lit: Color, dark: Color): Brush {
    val reach = light.towards * (radius * 0.7f)
    return Brush.linearGradient(
        0f to lit,
        0.5f to Color.Transparent,
        1f to dark,
        start = center + reach,
        end = center - reach
    )
}

/**
 * Chamfers along [outline]: bright where the edge faces the light, shaded where it faces away.
 * [reversed] is for the edges of holes and internal teeth, whose chamfers face the other way.
 * The outline is assumed to lie around [center] within [radius]. Drawn as a plain stroke on the
 * edge, without clipping, which is expensive every frame.
 */
internal fun DrawScope.bevel(
    outline: Path,
    center: Offset,
    radius: Float,
    light: GearLight,
    width: Float,
    strength: Float = 1f,
    reversed: Boolean = false
) {
    drawPath(outline, chamferLight(center, radius, light, strength, reversed), style = Stroke(width, join = StrokeJoin.Round))
}

/** Chamfer on the inner edge of a ring of [radius]: lit on the side facing away from the light. */
internal fun DrawScope.holeBevel(center: Offset, radius: Float, light: GearLight, width: Float, strength: Float = 1f) {
    drawCircle(chamferLight(center, radius, light, strength, reversed = true), radius, center, style = Stroke(width))
}

private fun chamferLight(center: Offset, radius: Float, light: GearLight, strength: Float, reversed: Boolean): Brush {
    val lit = Color(0xFFFFFCEE).copy(alpha = min(1f, 0.95f * strength))
    val dark = Color(0xFF1E1405).copy(alpha = 0.4f * strength)
    return if (reversed) edgeLight(center, radius, light, dark, lit) else edgeLight(center, radius, light, lit, dark)
}

/**
 * Fine circular graining for a ring from [inner] to [outer]: concentric hairlines of random
 * strength, grouped into a few paths so the whole grain draws in four calls. Rotationally
 * symmetric, so it never needs turning with its part. Build once per size.
 */
internal class RingGrain(center: Offset, outer: Float, inner: Float, spacing: Float, seed: Int) {
    private val layers = List(4) { Path() }

    init {
        val random = Random(seed)
        var r = inner + spacing / 2f
        while (r < outer) {
            layers[random.nextInt(4)].addOval(Rect(center, r))
            r += spacing
        }
    }

    fun draw(scope: DrawScope, hairline: Float) = with(scope) {
        drawPath(layers[0], Color.White.copy(alpha = 0.03f), style = Stroke(hairline))
        drawPath(layers[1], Color.White.copy(alpha = 0.06f), style = Stroke(hairline))
        drawPath(layers[2], Color.Black.copy(alpha = 0.03f), style = Stroke(hairline))
        drawPath(layers[3], Color.Black.copy(alpha = 0.06f), style = Stroke(hairline))
    }
}

/**
 * Finish for a metal ring from [inner] to [outer] around [center]: its [grain], an optional
 * rounded profile ([round]: darker towards both edges, like a bar of round section) and a sharp
 * glint where it faces the light ([glint]).
 */
internal fun DrawScope.ringFinish(
    center: Offset,
    outer: Float,
    inner: Float,
    light: GearLight,
    grain: RingGrain?,
    pxPerDp: Float,
    round: Boolean = false,
    glint: Boolean = true
) {
    // Everything here stays within the ring by construction, so nothing needs clipping.
    grain?.draw(this, 0.5f * pxPerDp)
    if (round) {
        val from = inner / outer
        val span = 1f - from
        drawCircle(
            Brush.radialGradient(
                from to Color(0x38140C00),
                from + span * 0.3f to Color.White.copy(alpha = 0.16f),
                from + span * 0.55f to Color.Transparent,
                from + span * 0.85f to Color.Black.copy(alpha = 0.05f),
                1f to Color(0x42140C00),
                center = center,
                radius = outer
            ),
            (outer + inner) / 2f, center,
            style = Stroke(outer - inner)
        )
    }
    if (glint) {
        val mid = (outer + inner) / 2f
        val at = center + light.towards * mid
        val length = max(6f * pxPerDp, mid * 0.36f)
        val thickness = max(1.5f * pxPerDp, (outer - inner) * 0.35f)
        withTransform({
            translate(at.x, at.y)
            rotate(Math.toDegrees(light.angle.toDouble()).toFloat() + 90f, Offset.Zero)
            scale(1f, thickness / length, Offset.Zero)
        }) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xB3FFFAEB), Color(0x00FFFAEB)), center = Offset.Zero, radius = length),
                length, Offset.Zero,
                blendMode = BlendMode.Plus
            )
        }
    }
}

/**
 * A soft ring of light around a dial's main ring at [radius], fading out [width] either side:
 * the halo that lifts the metal off the sky and keeps the dial bright.
 */
internal fun DrawScope.haloRing(center: Offset, radius: Float, width: Float, color: Color, strength: Float) {
    val outer = radius + width
    drawCircle(
        Brush.radialGradient(
            max(0f, radius - width) / outer to color.copy(alpha = 0f),
            radius / outer to color.copy(alpha = strength),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = outer
        ),
        outer, center
    )
}

/** Radial fill for a domed part, brightest on the side facing the light. */
internal fun domeBrush(center: Offset, radius: Float, light: GearLight, high: Color, mid: Color, low: Color): Brush =
    Brush.radialGradient(
        0f to high,
        0.45f to mid,
        1f to low,
        center = center + light.towards * (radius * 0.5f),
        radius = radius * 1.4f
    )

/** A domed screw head with its slot turned to [slot] radians. */
internal fun DrawScope.screw(at: Offset, radius: Float, light: GearLight, high: Color, mid: Color, low: Color, slot: Float) {
    drawCircle(domeBrush(at, radius, light, high, mid, low), radius, at)
    drawLine(
        Color.Black.copy(alpha = 0.55f),
        pointOn(at, radius * 0.75f, slot),
        pointOn(at, radius * 0.75f, slot + TAU / 2),
        max(0.6f, radius * 0.28f)
    )
}

/** A glossy, slightly domed enamel face of [radius]: light on the side facing the light, shade opposite. */
internal fun DrawScope.enamelGloss(at: Offset, radius: Float, light: GearLight) {
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.35f),
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.3f),
            center = at + light.towards * (radius * 0.55f),
            radius = radius * 1.5f
        ),
        radius, at
    )
}
