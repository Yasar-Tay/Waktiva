package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Brass movement: a brass wheel with five straight spokes turns with the day, and the
 * prayer gears mesh on its outside. The date sits as a disc in the wheel's hub.
 *
 * The prayer times ride just inside the rim, so the wheel and its gears can fill the dial.
 * [labelReach] is how far a time label extends from its centre, in pixels.
 */
internal class BrassGearDial(private val s: Float, private val dp: Float, labelReach: Float) : GearDial {
    private val c = Offset(s / 2f, s / 2f)
    private val r = 0.77f * s / 2f
    private val rPlanet = r * PLANET_TEETH / MAIN_TEETH
    private val ded = gearDedendum(r, MAIN_TEETH)
    private val bandIn = r - ded - 0.14f * r
    private val hubOut = 0.34f * r
    private val hubIn = 0.255f * r
    private val labelRing = bandIn - 4 * dp - labelReach

    override val markerDistance = r + rPlanet
    override val markerRadius = rPlanet + gearAddendum(rPlanet, PLANET_TEETH)
    override val dateRadius = hubIn
    override val hubOuterRadius = hubOut
    // The bridge hugs the hub so it keeps a readable thickness inside the ring of time labels.
    override val bridge = BridgeSpec(innerRadius = hubOut + 5 * dp, freeRadius = labelRing - labelReach, maxSpan = 2.3f)

    private val wheel = Path().apply {
        addGearOutline(this, c, r, MAIN_TEETH)
        addOval(Rect(c, bandIn))
        fillType = PathFillType.EvenOdd
    }
    private val teeth = Path().apply { addGearOutline(this, c, r, MAIN_TEETH) }

    private class Spoke(val outline: Path, val hubEnd: Offset, val rimEnd: Offset) {
        val occlusion = Brush.linearGradient(
            0f to SpokeShade,
            0.12f to Color.Transparent,
            0.9f to Color.Transparent,
            1f to SpokeShade,
            start = hubEnd,
            end = rimEnd
        )
    }

    // Five evenly spaced straight spokes, tapering slightly towards the rim.
    private val spokeList = List(5) { i ->
        val hubHalfWidth = 0.075f * hubOut
        val rimHalfWidth = 0.8f * hubHalfWidth
        val a = i * TAU / 5 - TAU / 4
        val along = Offset(cos(a), sin(a))
        val across = Offset(-along.y, along.x)
        val hubPoint = c + along * (hubOut - 2 * dp)
        val rimPoint = c + along * (bandIn + 2 * dp)
        val outline = Path().apply {
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
        Spoke(outline, hubPoint, rimPoint)
    }
    private val spokes = Path().apply { spokeList.forEach { addPath(it.outline) } }

    private class TrainGear(val center: Offset, val radius: Float, val teeth: Int, val outline: Path)

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

    private val hub = annulus(c, hubOut, hubIn)
    private val hubEdge = Path().apply { addOval(Rect(c, hubOut)) }
    private val planetPath = planetOutline(rPlanet)
    private val rimGrain = RingGrain(c, r - ded, bandIn, 1.2f * dp, seed = 1)
    private val hubGrain = RingGrain(c, hubOut, hubIn, 1.2f * dp, seed = 2)

    override fun draw(scope: DrawScope, frame: GearFrame) = with(scope) {
        val palette = frame.palette
        val light = frame.light
        val hair = Stroke(dp)
        drawTrain(frame, hair)

        val wheelRot = frame.direction * (frame.dayTurn + frame.phase)
        // The wheel is drawn in its turning frame; turning the light back keeps it fixed on screen.
        val wheelLight = GearLight(light.angle - wheelRot)

        // Still parts are replayed from layers; only the wheel, its screws and the prayer gears turn.
        frame.still.draw(this, 0) {
            // A thin halo hugging the rim, tinted a little by the current prayer: just enough to
            // lift the wheel off the sky.
            haloRing(c, r, r * 0.16f, lerp(palette.warmHalo, frame.current.color, 0.3f), 0.1f)
            // The hub is round, so its shadow looks the same however the wheel has turned.
            elevation(hub, 2f * dp, light)
        }

        elevation(wheel, 3f * dp, light, wheelRot, c)
        elevation(spokes, 2f * dp, light, wheelRot, c)
        rotate(wheelRot.toDegrees(), c) {
            val brass = palette.brassSheen.brush(c, wheelLight)
            drawPath(wheel, brass)
            bevel(teeth, c, r, wheelLight, 1.1f * dp)
            drawPath(wheel, Color(0xB33C280A), style = Stroke(0.8f * dp))
            // Hour engraving on the rim, turning with the wheel.
            for (i in 0 until 24) {
                val a = i * TAU / 24
                val major = i % 6 == 0
                drawLine(
                    palette.tone(Color(0xFF462D0A)).copy(alpha = if (major) 0.6f else 0.32f),
                    pointOn(c, bandIn + 2 * dp, a),
                    pointOn(c, bandIn + (if (major) 7 else 4) * dp, a),
                    (if (major) 1.4f else 0.8f) * dp
                )
            }
            for (spoke in spokeList) {
                drawPath(spoke.outline, brass)
                // Occlusion where the spoke meets the hub and the rim.
                drawPath(spoke.outline, spoke.occlusion)
                bevel(spoke.outline, (spoke.hubEnd + spoke.rimEnd) / 2f, (bandIn - hubOut) / 2f, wheelLight, 0.9f * dp)
            }
        }

        // Rotationally symmetric finishes, drawn in screen space.
        frame.still.draw(this, 1) {
            // The round hub looks the same however it has turned, so it is drawn still.
            drawPath(hub, palette.brassSheen.brush(c, light))
            ringFinish(c, r - ded, bandIn, light, rimGrain, dp, round = true)
            holeBevel(c, bandIn, light, 1.2f * dp)
            drawCircle(palette.tone(Color(0x59FFF0C8)), bandIn + 0.8f * dp, c, style = Stroke(0.8f * dp))
            ringFinish(c, hubOut, hubIn, light, hubGrain, dp, round = true)
            bevel(hubEdge, c, hubOut, light, dp)
            holeBevel(c, hubIn, light, dp)
            drawCircle(Color(0x993C280A), hubOut, c, style = Stroke(0.8f * dp))
            drawCircle(Color(0x993C280A), hubIn, c, style = Stroke(0.8f * dp))
        }
        for (i in 0 until 3) {
            val a = wheelRot + i * TAU / 3 + 0.5f
            screw(
                pointOn(c, (hubOut + hubIn) / 2f, a), s * 0.007f, light,
                palette.tone(Color(0xFFFFF1C4)), palette.tone(Color(0xFFB58D47)), palette.tone(Color(0xFF4D3610)),
                slot = a + i
            )
        }

        frame.still.draw(this, 2) {
            // Stationary enamel track laid over the turning rim.
            prayerTrack(frame, c, (bandIn + r - ded) / 2f + dp, max(2.5f * dp, s * 0.008f), 0.9f)
            frame.bridge?.draw(this)
            if (frame.showNow) drawHand(frame)
            planetHalo(frame.current, pointOn(c, r + rPlanet, dayAngle(frame.current.minutes, frame.rtl)), rPlanet)
        }

        frame.prayers.forEach { p ->
            val theta = dayAngle(p.minutes, frame.rtl)
            val rotation = meshExternal(wheelRot, MAIN_TEETH, theta, PLANET_TEETH)
            planetGear(pointOn(c, r + rPlanet, theta), rPlanet, rotation, planetPath, palette.brassSheen, light, dp)
        }

        frame.still.draw(this, 3) {
            frame.prayers.forEach { p ->
                val theta = dayAngle(p.minutes, frame.rtl)
                planetFace(p, pointOn(c, r + rPlanet, theta), rPlanet, light, dp, isCurrent = p.type == frame.current.type)
                timeLabel(frame, p.label, pointOn(c, labelRing, theta))
            }
        }
    }

    /**
     * The faint background train. It is laid out left-to-right; in RTL it is drawn flipped,
     * which mirrors both its position and its spin, so rotations are always computed for LTR.
     * Integer multiples of the phase keep every gear seamless when the phase loops.
     */
    private fun DrawScope.drawTrain(frame: GearFrame, hair: Stroke) {
        val r1 = 2 * frame.phase + 2 * frame.dayTurn
        val r2 = meshExternal(r1, train[0].teeth, angle12, train[1].teeth)
        val r3 = meshExternal(r2, train[1].teeth, angle23, train[2].teeth)
        val r4 = -frame.phase
        val r5 = meshExternal(r4, train[3].teeth, angle45, train[4].teeth)
        val trainColor = frame.palette.gold.copy(alpha = 0.13f)

        scale(scaleX = frame.direction, scaleY = 1f, pivot = c) {
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
    }

    /** The hour hand, pointing at the time of day, and the marker on the rim where it points. */
    private fun DrawScope.drawHand(frame: GearFrame) {
        val palette = frame.palette
        val light = frame.light
        val handAngle = dayAngle(frame.nowMinutes, frame.rtl)
        val length = bandIn - 4 * dp
        rotate(handAngle.toDegrees(), c) {
            val tail = c.x - s * 0.05f
            val hand = Path().apply {
                moveTo(tail, c.y - s * 0.006f)
                lineTo(c.x + length * 0.78f, c.y - s * 0.004f)
                lineTo(c.x + length, c.y)
                lineTo(c.x + length * 0.78f, c.y + s * 0.004f)
                lineTo(tail, c.y + s * 0.006f)
                close()
            }
            drawPath(hand, Brush.linearGradient(*palette.brass, start = c + Offset(0f, -6 * dp), end = c + Offset(length, 6 * dp)))
            // A polished ridge down the middle: one flank catches the light, the other falls into shade.
            val facing = cos(handAngle - TAU / 4 - light.angle)
            val lit = Color(0xFFFFF6D6).copy(alpha = 0.55f * abs(facing))
            val shade = Color(0xFF281905).copy(alpha = 0.35f * abs(facing))
            val upper = Path().apply {
                moveTo(tail, c.y)
                lineTo(c.x + length, c.y)
                lineTo(c.x + length * 0.78f, c.y - s * 0.004f)
                lineTo(tail, c.y - s * 0.006f)
                close()
            }
            val lower = Path().apply {
                moveTo(tail, c.y)
                lineTo(c.x + length, c.y)
                lineTo(c.x + length * 0.78f, c.y + s * 0.004f)
                lineTo(tail, c.y + s * 0.006f)
                close()
            }
            drawPath(upper, if (facing > 0f) lit else shade)
            drawPath(lower, if (facing > 0f) shade else lit)
            drawCircle(palette.tone(Color(0xFFE8C97E)), s * 0.012f, c + Offset(length * 0.72f, 0f), style = Stroke(s * 0.004f))
            drawCircle(palette.tone(Color(0xFFB8903F)), s * 0.012f, c + Offset(-s * 0.05f, 0f))
        }
        nowIndicator(c, r, handAngle, frame.current.color, dp)
    }

    private companion object {
        val SpokeShade = Color(0x661E1202)
    }
}
