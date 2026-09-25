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
import kotlin.math.max

/**
 * Night steel: a fixed steel bezel carries the prayer track, an internal ring gear turns
 * inside it and the prayer gears run inside the ring like planets. Keeps the standard date card.
 */
internal class SteelGearDial(private val s: Float, private val dp: Float) : GearDial {
    private val c = Offset(s / 2f, s / 2f)
    private val outer = 0.97f * s / 2f
    private val inner = 0.861f * s / 2f
    private val mid = (outer + inner) / 2f
    private val band = 0.055f * s / 2f
    private val pitch = (inner - band) - gearDedendum(inner - band, MAIN_TEETH)
    private val rPlanet = pitch * PLANET_TEETH / MAIN_TEETH
    private val addPlanet = gearAddendum(rPlanet, PLANET_TEETH)

    override val markerDistance = pitch - rPlanet
    override val markerRadius = rPlanet + addPlanet
    // The date sub-dial stays inside the special-day bridge's inner radius (0.13 s).
    override val dateRadius = s * 0.12f
    override val hubOuterRadius = dateRadius
    override val bridge = BridgeSpec(
        innerRadius = s * 0.13f,
        freeRadius = pitch - 2 * rPlanet - addPlanet - 4 * dp,
        maxSpan = 1.6f
    )

    private val bezel = annulus(c, outer, inner)
    private val groove = annulus(c, mid + s * 0.012f, mid - s * 0.012f)
    private val ring = Path().apply {
        addOval(Rect(c, inner))
        addGearOutline(this, c, pitch, MAIN_TEETH, internal = true)
        fillType = PathFillType.EvenOdd
    }
    private val planetPath = planetOutline(rPlanet)
    private val bezelEdge = Path().apply { addOval(Rect(c, outer)) }
    private val grooveEdge = Path().apply { addOval(Rect(c, mid + s * 0.012f)) }
    private val internalTeeth = Path().apply { addGearOutline(this, c, pitch, MAIN_TEETH, internal = true) }
    private val bezelGrain = RingGrain(c, outer, inner, 1.2f * dp, seed = 4)
    private val ringGrain = RingGrain(c, inner, pitch, 1.2f * dp, seed = 5)

    override fun draw(scope: DrawScope, frame: GearFrame) = with(scope) {
        val palette = frame.palette
        val light = frame.light
        val ringRot = frame.direction * (frame.dayTurn + frame.phase)
        val accent = frame.current.color

        // Still parts are replayed from layers; only the ring gear, its marks and the prayer gears turn.
        frame.still.draw(this, 0) {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0f)), c, outer), outer, c)
            haloRing(c, mid, outer * 0.2f, lerp(palette.coolHalo, accent, 0.3f), 0.16f)
            elevation(bezel, 3f * dp, light)

            drawPath(bezel, palette.steelSheen.brush(c, light))
            ringFinish(c, outer, inner, light, bezelGrain, dp)
            bevel(bezelEdge, c, outer, light, 1.3f * dp)
            holeBevel(c, inner, light, 1.2f * dp)
            drawCircle(Color(0xB30A0E16), inner, c, style = Stroke(dp))
            drawCircle(Color(0xB30A0E16), outer, c, style = Stroke(dp))
            // The enamel track sits in a groove cut into the bezel.
            drawPath(groove, Color(0x8C0C101A))
            holeBevel(c, mid - s * 0.012f, light, 0.8f * dp, 0.8f)
            bevel(grooveEdge, c, mid, light, 0.8f * dp, 0.8f, reversed = true)
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
        }

        elevation(ring, 2f * dp, light, ringRot, c)
        val ringLight = GearLight(light.angle - ringRot)
        rotate(ringRot.toDegrees(), c) {
            drawPath(ring, palette.steelSheen.brush(c, ringLight))
            // A darker, blued finish than the bezel, so the turning ring reads as a separate part.
            drawPath(ring, BluedSteel)
            bevel(internalTeeth, c, pitch, ringLight, dp, reversed = true)
            drawPath(ring, Color(0x8C0A0E16), style = Stroke(0.7f * dp))
        }
        frame.still.draw(this, 1) {
            ringFinish(c, inner, pitch, light, ringGrain, dp, round = true, glint = false)
        }
        // Turning marks on the ring face, over its finish.
        rotate(ringRot.toDegrees(), c) {
            for (i in 0 until 12) {
                drawCircle(Color(0x8C0F1420), s * 0.0045f, pointOn(c, inner - band / 2f, i * TAU / 12))
            }
        }

        frame.still.draw(this, 2) {
            frame.bridge?.draw(this)
            if (frame.showNow) drawNeedle(frame)
            planetHalo(frame.current, pointOn(c, pitch - rPlanet, dayAngle(frame.current.minutes, frame.rtl)), rPlanet)
        }

        frame.prayers.forEach { p ->
            val theta = dayAngle(p.minutes, frame.rtl)
            val rotation = meshInternal(ringRot, MAIN_TEETH, theta, PLANET_TEETH)
            planetGear(pointOn(c, pitch - rPlanet, theta), rPlanet, rotation, planetPath, palette.steelSheen, light, dp)
        }

        frame.still.draw(this, 3) {
            frame.prayers.forEach { p ->
                val theta = dayAngle(p.minutes, frame.rtl)
                planetFace(p, pointOn(c, pitch - rPlanet, theta), rPlanet, light, dp, isCurrent = p.type == frame.current.type)
                timeLabel(frame, p.label, pointOn(c, pitch - 2 * rPlanet - addPlanet - s * 0.035f, theta))
            }
        }
    }

    /** The needle pointing at the time of day, and the marker on the track where it points. */
    private fun DrawScope.drawNeedle(frame: GearFrame) {
        val accent = frame.current.color
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
                    0f to frame.palette.tone(Color(0x40D2DCEC)),
                    0.8f to frame.palette.tone(Color(0xE6E6ECF6)),
                    1f to accent,
                    start = c,
                    end = c + Offset(mid, 0f)
                )
            )
        }
        nowIndicator(c, mid, handAngle, accent, dp)
    }

    private companion object {
        val BluedSteel = Color(0x52122034)
    }
}
