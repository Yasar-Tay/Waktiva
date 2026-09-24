package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.ybugmobile.waktiva.domain.model.PrayerType
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

/** One prayer as the gear dials draw it. */
internal class GearPrayer(
    val type: PrayerType,
    val minutes: Float,
    val color: Color,
    val painter: VectorPainter,
    val icon: ImageVector,
    val label: String
)

/** Everything a dial needs for one frame. */
internal class GearFrame(
    val prayers: List<GearPrayer>,
    val current: GearPrayer,
    val nowMinutes: Float,
    val showNow: Boolean,
    /** Ambient rotation in radians, looping over [0, 2π). */
    val phase: Float,
    val rtl: Boolean,
    val labelStyle: TextStyle,
    val textMeasurer: TextMeasurer
) {
    val direction get() = if (rtl) -1f else 1f
    val dayTurn get() = nowMinutes / 1440f * TAU
}

/**
 * A gear-styled day dial. Implementations cache their paths for one canvas size,
 * so build a new one whenever the size changes.
 */
internal interface GearDial {
    /** Distance from the centre to each prayer marker. */
    val markerDistance: Float

    /** Radius of a prayer marker, used for its tap target. */
    val markerRadius: Float

    /**
     * Radius of the central circle the date card should fill as a disc,
     * or null to show the standard card.
     */
    val hubRadius: Float?

    fun draw(scope: DrawScope, frame: GearFrame)
}

internal fun createGearDial(style: GearDayCircleStyle, sizePx: Float, pxPerDp: Float): GearDial = when (style) {
    GearDayCircleStyle.BRASS -> BrassGearDial(sizePx, pxPerDp)
    GearDayCircleStyle.STEEL -> SteelGearDial(sizePx, pxPerDp)
    GearDayCircleStyle.SKELETON -> SkeletonGearDial(sizePx, pxPerDp)
}

// ---------------------------------------------------------------------------
// Shared drawing helpers
// ---------------------------------------------------------------------------

private val BrassStops = arrayOf(
    0f to Color(0xFFF6E1A2),
    0.35f to Color(0xFFD4AB5A),
    0.7f to Color(0xFFA77C34),
    1f to Color(0xFF6F5020)
)

private val SteelStops = arrayOf(
    0f to Color(0xFFE3E9F3),
    0.4f to Color(0xFFA6B1C4),
    0.75f to Color(0xFF66728A),
    1f to Color(0xFF394257)
)

/**
 * Diagonal metal gradient lit from the top-left. When drawn inside a rotation of
 * [rotation] radians around [pivot], the light stays put while the part turns.
 */
private fun metalBrush(
    stops: Array<Pair<Float, Color>>,
    pivot: Offset,
    radius: Float,
    rotation: Float = 0f,
    flip: Boolean = false
): Brush {
    val topLeft = pivot + Offset(-radius, -radius)
    val bottomRight = pivot + Offset(radius, radius)
    val (from, to) = if (flip) bottomRight to topLeft else topLeft to bottomRight
    return Brush.linearGradient(
        *stops,
        start = rotateAround(from, pivot, -rotation),
        end = rotateAround(to, pivot, -rotation)
    )
}

private fun annulus(center: Offset, outer: Float, inner: Float) = Path().apply {
    addOval(Rect(center, outer))
    addOval(Rect(center, inner))
    fillType = PathFillType.EvenOdd
}

private fun DrawScope.softShadow(center: Offset, radius: Float) {
    val c = center + Offset(0f, radius * 0.04f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.45f),
            0.88f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Transparent,
            center = c,
            radius = radius * 1.08f
        ),
        radius = radius * 1.08f,
        center = c
    )
}

/** Colored prayer-to-prayer arcs, as in PrayerCircleVisualization. */
private fun DrawScope.prayerTrack(f: GearFrame, center: Offset, radius: Float, width: Float, alpha: Float) {
    val sorted = f.prayers.sortedBy { it.minutes }
    sorted.forEachIndexed { i, from ->
        val to = sorted[(i + 1) % sorted.size]
        val start = dayAngle(from.minutes, f.rtl)
        var sweep = dayAngle(to.minutes, f.rtl) - start
        if (f.rtl) {
            if (sweep > 0f) sweep -= TAU
        } else if (sweep < 0f) {
            sweep += TAU
        }
        val gap = 0.01f * sign(sweep)
        drawArc(
            brush = Brush.linearGradient(
                listOf(from.color.copy(alpha = alpha), to.color.copy(alpha = alpha)),
                start = pointOn(center, radius, start),
                end = pointOn(center, radius, start + sweep)
            ),
            startAngle = (start + gap).toDegrees(),
            sweepAngle = (sweep - 2 * gap).toDegrees(),
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.nowIndicator(center: Offset, radius: Float, angle: Float, color: Color, pxPerDp: Float) {
    val s = size.minDimension
    val p = pointOn(center, radius, angle)
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0f)), center = p, radius = s * 0.035f),
        radius = s * 0.035f,
        center = p
    )
    drawCircle(Color.White, s * 0.009f, p)
    drawCircle(color, s * 0.015f, p, style = Stroke(1.5f * pxPerDp))
}

private fun DrawScope.timeLabel(f: GearFrame, text: String, at: Offset) {
    val layout = f.textMeasurer.measure(text, f.labelStyle)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

private fun DrawScope.prayerIcon(p: GearPrayer, at: Offset, iconSize: Float) {
    val ink = if (p.color.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White
    translate(at.x - iconSize / 2f, at.y - iconSize / 2f) {
        with(p.painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(ink)) }
    }
}

private fun DrawScope.halo(at: Offset, inner: Float, outer: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            inner / outer to color.copy(alpha = alpha),
            1f to color.copy(alpha = 0f),
            center = at,
            radius = outer
        ),
        radius = outer,
        center = at
    )
}

/** Planet gear outline centred on the origin, with an axle hole. */
private fun planetOutline(radius: Float) = Path().apply {
    addGearOutline(this, Offset.Zero, radius, PLANET_TEETH)
    addOval(Rect(Offset.Zero, radius * 0.30f))
    fillType = PathFillType.EvenOdd
}

/** A metal prayer gear with an upright enamel face showing the prayer icon. */
private fun DrawScope.planet(
    f: GearFrame,
    p: GearPrayer,
    at: Offset,
    radius: Float,
    rotation: Float,
    outline: Path,
    stops: Array<Pair<Float, Color>>,
    pxPerDp: Float
) {
    val isCurrent = p.type == f.current.type
    if (isCurrent) halo(at, radius * 0.5f, radius * 2.3f, p.color, 0.42f)

    withTransform({
        translate(at.x, at.y)
        rotate(rotation.toDegrees(), Offset.Zero)
    }) {
        drawPath(outline, metalBrush(stops, Offset.Zero, radius, rotation))
        drawPath(outline, Color.Black.copy(alpha = 0.45f), style = Stroke(0.8f * pxPerDp))
        for (i in 0 until 4) {
            val a = i * TAU / 4 + TAU / 8
            drawLine(
                Color.Black.copy(alpha = 0.25f),
                pointOn(Offset.Zero, radius * 0.66f, a),
                pointOn(Offset.Zero, radius * 0.78f, a),
                0.8f * pxPerDp
            )
        }
    }

    val face = radius * if (isCurrent) 0.66f else 0.6f
    drawCircle(p.color, face, at)
    drawCircle(Color.Black.copy(alpha = 0.35f), face, at, style = Stroke(pxPerDp))
    val inset = face - 1.2f * pxPerDp
    drawArc(
        color = Color.White.copy(alpha = 0.45f),
        startAngle = 189f,
        sweepAngle = 117f,
        useCenter = false,
        topLeft = Offset(at.x - inset, at.y - inset),
        size = Size(inset * 2, inset * 2),
        style = Stroke(0.8f * pxPerDp)
    )
    prayerIcon(p, at, face * 1.2f)
}

// ---------------------------------------------------------------------------
// A: Brass movement. A spoked brass wheel turns; prayer gears mesh on its outside.
// ---------------------------------------------------------------------------

private class BrassGearDial(private val s: Float, private val dp: Float) : GearDial {
    private val c = Offset(s / 2f, s / 2f)
    private val r = 0.62f * s / 2f
    private val rPlanet = r * PLANET_TEETH / MAIN_TEETH
    private val add = gearAddendum(r, MAIN_TEETH)
    private val ded = gearDedendum(r, MAIN_TEETH)
    private val bandIn = r - ded - 0.14f * r
    private val hubOut = 0.40f * r
    private val hubIn = 0.30f * r

    override val markerDistance = r + rPlanet
    override val markerRadius = rPlanet + gearAddendum(rPlanet, PLANET_TEETH)
    override val hubRadius = hubIn

    private val wheel = Path().apply {
        addGearOutline(this, c, r, MAIN_TEETH)
        addOval(Rect(c, bandIn))
        fillType = PathFillType.EvenOdd
    }

    // Five evenly spaced straight spokes, tapering slightly towards the rim.
    private val spokes = Path().apply {
        val hubHalfWidth = 0.075f * hubOut
        val rimHalfWidth = 0.8f * hubHalfWidth
        for (i in 0 until 5) {
            val a = i * TAU / 5 - TAU / 4
            val along = Offset(cos(a), sin(a))
            val across = Offset(-along.y, along.x)
            val hubPoint = c + along * (hubOut - 2 * dp)
            val rimPoint = c + along * (bandIn + 2 * dp)
            val p0 = hubPoint - across * hubHalfWidth
            val p1 = rimPoint - across * rimHalfWidth
            val p2 = rimPoint + across * rimHalfWidth
            val p3 = hubPoint + across * hubHalfWidth
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }
    }

    private val hub = annulus(c, hubOut, hubIn)
    private val planetPath = planetOutline(rPlanet)

    override fun draw(scope: DrawScope, frame: GearFrame) = with(scope) {
        val wheelRot = frame.direction * (frame.dayTurn + frame.phase)

        softShadow(c, r + add)
        rotate(wheelRot.toDegrees(), c) {
            val brass = metalBrush(BrassStops, c, r, wheelRot)
            drawPath(wheel, brass)
            drawPath(wheel, Color(0xB33C280A), style = Stroke(0.8f * dp))
            // Hour engraving on the rim, turning with the wheel.
            for (i in 0 until 24) {
                val a = i * TAU / 24
                val major = i % 6 == 0
                drawLine(
                    Color(0xFF462D0A).copy(alpha = if (major) 0.6f else 0.32f),
                    pointOn(c, bandIn + 2 * dp, a),
                    pointOn(c, bandIn + (if (major) 7 else 4) * dp, a),
                    (if (major) 1.4f else 0.8f) * dp
                )
            }
            drawCircle(Color(0x59FFF0C8), bandIn + 0.8f * dp, c, style = Stroke(0.8f * dp))
            drawPath(spokes, brass)
            drawPath(hub, brass)
            drawCircle(Color(0x993C280A), hubOut, c, style = Stroke(0.8f * dp))
            drawCircle(Color(0x993C280A), hubIn, c, style = Stroke(0.8f * dp))
            for (i in 0 until 3) {
                drawCircle(Color(0xFF7A5A26), s * 0.006f, pointOn(c, (hubOut + hubIn) / 2f, i * TAU / 3 + 0.5f))
            }
        }

        // Stationary enamel track laid over the turning rim.
        prayerTrack(frame, c, (bandIn + r - ded) / 2f + dp, max(2.5f * dp, s * 0.008f), 0.9f)

        if (frame.showNow) {
            val handAngle = dayAngle(frame.nowMinutes, frame.rtl)
            val length = bandIn - 4 * dp
            rotate(handAngle.toDegrees(), c) {
                val hand = Path().apply {
                    moveTo(c.x - s * 0.05f, c.y - s * 0.006f)
                    lineTo(c.x + length * 0.78f, c.y - s * 0.004f)
                    lineTo(c.x + length, c.y)
                    lineTo(c.x + length * 0.78f, c.y + s * 0.004f)
                    lineTo(c.x - s * 0.05f, c.y + s * 0.006f)
                    close()
                }
                drawPath(hand, Brush.linearGradient(*BrassStops, start = c + Offset(0f, -6 * dp), end = c + Offset(length, 6 * dp)))
                drawCircle(Color(0xFFE8C97E), s * 0.012f, c + Offset(length * 0.72f, 0f), style = Stroke(s * 0.004f))
                drawCircle(Color(0xFFB8903F), s * 0.012f, c + Offset(-s * 0.05f, 0f))
            }
            nowIndicator(c, r, handAngle, frame.current.color, dp)
        }

        frame.prayers.forEach { p ->
            val theta = dayAngle(p.minutes, frame.rtl)
            val at = pointOn(c, r + rPlanet, theta)
            val rotation = meshExternal(wheelRot, MAIN_TEETH, theta, PLANET_TEETH)
            planet(frame, p, at, rPlanet, rotation, planetPath, BrassStops, dp)
            timeLabel(frame, p.label, pointOn(c, r + 2 * rPlanet + add + s * 0.04f, theta))
        }
    }
}

// ---------------------------------------------------------------------------
// B: Night steel. A fixed steel bezel carries the prayer track; an internal ring
// gear turns inside it and the prayer gears run inside the ring like planets.
// ---------------------------------------------------------------------------

private class SteelGearDial(private val s: Float, private val dp: Float) : GearDial {
    private val c = Offset(s / 2f, s / 2f)
    private val outer = 0.88f * s / 2f
    private val inner = 0.78f * s / 2f
    private val mid = (outer + inner) / 2f
    private val band = 0.05f * s / 2f
    private val pitch = (inner - band) - gearDedendum(inner - band, MAIN_TEETH)
    private val rPlanet = pitch * PLANET_TEETH / MAIN_TEETH
    private val addPlanet = gearAddendum(rPlanet, PLANET_TEETH)

    override val markerDistance = pitch - rPlanet
    override val markerRadius = rPlanet + addPlanet
    override val hubRadius: Float? = null

    private val bezel = annulus(c, outer, inner)
    private val groove = annulus(c, mid + s * 0.012f, mid - s * 0.012f)
    private val ring = Path().apply {
        addOval(Rect(c, inner))
        addGearOutline(this, c, pitch, MAIN_TEETH, internal = true)
        fillType = PathFillType.EvenOdd
    }
    private val planetPath = planetOutline(rPlanet)

    override fun draw(scope: DrawScope, frame: GearFrame) = with(scope) {
        val ringRot = frame.direction * (frame.dayTurn + frame.phase)
        val accent = frame.current.color

        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0f)), c, outer), outer, c)
        softShadow(c, outer)

        drawPath(bezel, metalBrush(SteelStops, c, outer))
        val sheen = outer - 0.8f * dp
        drawArc(
            color = Color.White.copy(alpha = 0.35f),
            startAngle = 180f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(c.x - sheen, c.y - sheen),
            size = Size(sheen * 2, sheen * 2),
            style = Stroke(0.8f * dp)
        )
        drawCircle(Color(0xB30A0E16), inner, c, style = Stroke(dp))
        drawCircle(Color(0xB30A0E16), outer, c, style = Stroke(dp))
        drawPath(groove, Color(0x8C0C101A))
        prayerTrack(frame, c, mid, max(3f * dp, s * 0.011f), 0.95f)
        for (i in 0 until 24) {
            val a = TAU / 4 + i * TAU / 24
            val major = i % 6 == 0
            drawLine(
                Color(0xFF141A28).copy(alpha = if (major) 0.85f else 0.45f),
                pointOn(c, outer - 1.5f * dp, a),
                pointOn(c, outer - if (major) s * 0.024f else s * 0.012f, a),
                (if (major) 1.6f else 0.9f) * dp
            )
        }

        rotate(ringRot.toDegrees(), c) {
            drawPath(ring, metalBrush(SteelStops, c, inner, ringRot, flip = true))
            drawPath(ring, Color(0x8C0A0E16), style = Stroke(0.7f * dp))
            for (i in 0 until 12) {
                drawCircle(Color(0x8C0F1420), s * 0.0045f, pointOn(c, inner - band / 2f, i * TAU / 12))
            }
        }

        if (frame.showNow) {
            val handAngle = dayAngle(frame.nowMinutes, frame.rtl)
            rotate(handAngle.toDegrees(), c) {
                val needle = Path().apply {
                    moveTo(c.x, c.y - s * 0.004f)
                    lineTo(c.x + mid - s * 0.02f, c.y - s * 0.002f)
                    lineTo(c.x + mid, c.y)
                    lineTo(c.x + mid - s * 0.02f, c.y + s * 0.002f)
                    lineTo(c.x, c.y + s * 0.004f)
                    close()
                }
                drawPath(
                    needle,
                    Brush.linearGradient(
                        0f to Color(0x40D2DCEC),
                        0.8f to Color(0xE6E6ECF6),
                        1f to accent,
                        start = c,
                        end = c + Offset(mid, 0f)
                    )
                )
            }
            nowIndicator(c, mid, handAngle, accent, dp)
        }

        frame.prayers.forEach { p ->
            val theta = dayAngle(p.minutes, frame.rtl)
            val at = pointOn(c, pitch - rPlanet, theta)
            val rotation = meshInternal(ringRot, MAIN_TEETH, theta, PLANET_TEETH)
            planet(frame, p, at, rPlanet, rotation, planetPath, SteelStops, dp)
            timeLabel(frame, p.label, pointOn(c, pitch - 2 * rPlanet - addPlanet - s * 0.035f, theta))
        }
    }
}

// ---------------------------------------------------------------------------
// C: Skeleton. Hairline wheel over a faint gear train; prayers become cog badges.
// ---------------------------------------------------------------------------

private class SkeletonGearDial(private val s: Float, private val dp: Float) : GearDial {
    private class TrainGear(val center: Offset, val radius: Float, val teeth: Int, val outline: Path)

    private val c = Offset(s / 2f, s / 2f)
    private val r = 0.86f * s / 2f
    private val ded = gearDedendum(r, MAIN_TEETH)
    private val track = r - ded - 0.07f * s / 2f
    private val badge = max(9f * dp, s * 0.03f)
    private val badgeCurrent = badge * 1.18f
    private val hub = 0.30f * r

    override val markerDistance = track
    override val markerRadius = badgeCurrent
    override val hubRadius = hub * 0.8f

    private val wheel = Path().apply { addGearOutline(this, c, r, MAIN_TEETH) }
    private val badgePath = Path().apply { addGearOutline(this, Offset.Zero, badge, 10) }
    private val badgeCurrentPath = Path().apply { addGearOutline(this, Offset.Zero, badgeCurrent, 10) }

    // Background train: neighbours share one circular pitch so their teeth mesh.
    private val angle12 = 0.55f
    private val angle23 = -0.9f
    private val angle45 = 3.6f
    private val train: List<TrainGear> = run {
        val pitchUnit = TAU * 0.20f / 36 // in units of the canvas size
        fun radius(teeth: Int) = teeth * pitchUnit / TAU * s
        fun gear(center: Offset, teeth: Int) =
            TrainGear(center, radius(teeth), teeth, Path().apply { addGearOutline(this, Offset.Zero, radius(teeth), teeth) })
        fun next(from: TrainGear, angle: Float, teeth: Int) =
            gear(pointOn(from.center, from.radius + radius(teeth), angle), teeth)

        val g1 = gear(Offset(0.20f * s, 0.22f * s), 36)
        val g2 = next(g1, angle12, 20)
        val g3 = next(g2, angle23, 14)
        val g4 = gear(Offset(0.84f * s, 0.84f * s), 32)
        val g5 = next(g4, angle45, 12)
        listOf(g1, g2, g3, g4, g5)
    }

    override fun draw(scope: DrawScope, frame: GearFrame) = with(scope) {
        val dir = frame.direction
        // The train is laid out left-to-right. In RTL it is drawn flipped, which mirrors both
        // its position and its spin, so its rotations are always computed for LTR.
        // Integer multiples of the phase keep every gear seamless when the phase loops.
        val r1 = 2 * frame.phase + 2 * frame.dayTurn
        val r2 = meshExternal(r1, train[0].teeth, angle12, train[1].teeth)
        val r3 = meshExternal(r2, train[1].teeth, angle23, train[2].teeth)
        val r4 = -frame.phase
        val r5 = meshExternal(r4, train[3].teeth, angle45, train[4].teeth)
        val trainColor = Color(0xFFD6B46E).copy(alpha = 0.13f)
        val hair = Stroke(dp)

        scale(scaleX = dir, scaleY = 1f, pivot = c) {
            listOf(r1, r2, r3, r4, r5).forEachIndexed { i, rotation ->
                val g = train[i]
                withTransform({
                    translate(g.center.x, g.center.y)
                    rotate(rotation.toDegrees(), Offset.Zero)
                }) {
                    drawPath(g.outline, trainColor, style = hair)
                    drawCircle(trainColor, g.radius * 0.78f, Offset.Zero, style = hair)
                    drawCircle(trainColor, g.radius * 0.16f, Offset.Zero, style = hair)
                    val spokeCount = if (g.teeth > 20) 5 else 4
                    for (k in 0 until spokeCount) {
                        val a = k * TAU / spokeCount
                        drawLine(trainColor, pointOn(Offset.Zero, g.radius * 0.16f, a), pointOn(Offset.Zero, g.radius * 0.78f, a), dp)
                    }
                }
            }
        }

        val wheelRot = dir * frame.phase * 0.25f
        val brass = Color(0xFFDEBE78)
        val innerRim = track - 0.05f * s / 2f
        rotate(wheelRot.toDegrees(), c) {
            drawPath(wheel, brass.copy(alpha = 0.55f), style = hair)
            drawCircle(brass.copy(alpha = 0.25f), r - ded - 3 * dp, c, style = hair)
            drawCircle(brass.copy(alpha = 0.25f), innerRim, c, style = hair)
            // Five evenly spaced straight spokes, outlined, same proportions as the brass wheel.
            val hubHalfWidth = 0.075f * hub
            val rimHalfWidth = 0.8f * hubHalfWidth
            for (i in 0 until 5) {
                val a = i * TAU / 5 - TAU / 4
                val along = Offset(cos(a), sin(a))
                val across = Offset(-along.y, along.x)
                val hubPoint = c + along * hub
                val rimPoint = c + along * innerRim
                for (side in floatArrayOf(-1f, 1f)) {
                    drawLine(
                        brass.copy(alpha = 0.22f),
                        hubPoint + across * (side * hubHalfWidth),
                        rimPoint + across * (side * rimHalfWidth),
                        dp
                    )
                }
            }
            drawCircle(brass.copy(alpha = 0.22f), hub, c, style = hair)
            drawCircle(brass.copy(alpha = 0.22f), hub * 0.8f, c, style = hair)
        }

        prayerTrack(frame, c, track, max(3f * dp, s * 0.01f), 0.5f)
        for (i in 0 until 24) {
            val a = TAU / 4 + i * TAU / 24
            val major = i % 6 == 0
            val len = if (major) s * 0.024f else s * 0.01f
            drawLine(
                Color.White.copy(alpha = if (major) 0.4f else 0.12f),
                pointOn(c, track - len / 2f, a),
                pointOn(c, track + len / 2f, a),
                (if (major) 1.5f else 1f) * dp
            )
        }

        if (frame.showNow) {
            val handAngle = dayAngle(frame.nowMinutes, frame.rtl)
            val tip = pointOn(c, track - s * 0.03f, handAngle)
            drawLine(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.75f)), start = c, end = tip),
                c, tip, 1.5f * dp, StrokeCap.Round
            )
            nowIndicator(c, track, handAngle, frame.current.color, dp)
        }

        frame.prayers.forEachIndexed { i, p ->
            val theta = dayAngle(p.minutes, frame.rtl)
            val at = pointOn(c, track, theta)
            val isCurrent = p.type == frame.current.type
            val radius = if (isCurrent) badgeCurrent else badge
            if (isCurrent) halo(at, radius * 0.6f, radius * 2.2f, p.color, 0.35f)
            val spin = dir * frame.phase * if (i % 2 == 1) -1f else 1f
            withTransform({
                translate(at.x, at.y)
                rotate(spin.toDegrees(), Offset.Zero)
            }) {
                val outline = if (isCurrent) badgeCurrentPath else badgePath
                drawPath(outline, p.color)
                drawPath(outline, Color.Black.copy(alpha = 0.35f), style = Stroke(0.8f * dp))
            }
            drawCircle(Color.Black.copy(alpha = 0.18f), radius * 0.72f, at, style = Stroke(0.8f * dp))
            prayerIcon(p, at, radius * 1.15f)
            timeLabel(frame, p.label, pointOn(c, track - radius - s * 0.05f, theta))
        }
    }
}
