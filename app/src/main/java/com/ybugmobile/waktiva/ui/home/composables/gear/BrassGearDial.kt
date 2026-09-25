package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Brass movement: a brass wheel with five straight spokes turns with the day, and the
 * prayer gears mesh on its outside. The date sits as a disc in the wheel's hub.
 */
internal class BrassGearDial(private val s: Float, private val dp: Float) : GearDial {
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
    override val hubOuterRadius = hubOut
    override val bridge = BridgeSpec(innerRadius = hubOut + 8 * dp, freeRadius = bandIn, maxSpan = 2.3f)

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

        frame.bridge?.draw(this)

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
            planet(p, at, rPlanet, rotation, planetPath, BrassStops, dp, isCurrent = p.type == frame.current.type)
            timeLabel(frame, p.label, pointOn(c, r + 2 * rPlanet + add + s * 0.04f, theta))
        }
    }
}
