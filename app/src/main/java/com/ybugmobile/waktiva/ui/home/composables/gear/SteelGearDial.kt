package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
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
    override val hubOuterRadius: Float? = null
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

    override fun draw(scope: DrawScope, frame: GearFrame) = with(scope) {
        val palette = frame.palette
        val ringRot = frame.direction * (frame.dayTurn + frame.phase)
        val accent = frame.current.color

        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0f)), c, outer), outer, c)
        softShadow(c, outer)

        drawPath(bezel, metalBrush(palette.steel, c, outer))
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
            drawPath(ring, metalBrush(palette.steel, c, inner, ringRot, flip = true))
            drawPath(ring, Color(0x8C0A0E16), style = Stroke(0.7f * dp))
            for (i in 0 until 12) {
                drawCircle(Color(0x8C0F1420), s * 0.0045f, pointOn(c, inner - band / 2f, i * TAU / 12))
            }
        }

        frame.bridge?.draw(this)

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
                        0f to palette.tone(Color(0x40D2DCEC)),
                        0.8f to palette.tone(Color(0xE6E6ECF6)),
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
            planet(p, at, rPlanet, rotation, planetPath, palette.steel, dp, isCurrent = p.type == frame.current.type)
            timeLabel(frame, p.label, pointOn(c, pitch - 2 * rPlanet - addPlanet - s * 0.035f, theta))
        }
    }
}
