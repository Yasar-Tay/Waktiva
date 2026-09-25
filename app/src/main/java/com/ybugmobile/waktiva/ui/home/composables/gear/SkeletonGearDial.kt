package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
 * Skeleton: a hairline wheel with five straight spokes over a faint background gear train.
 * The prayers are coloured stones set on a solid gold ring, whose enamel inlay carries the
 * prayer colours. The date sits as a disc in the hub.
 */
internal class SkeletonGearDial(private val s: Float, private val dp: Float) : GearDial {
    private class TrainGear(val center: Offset, val radius: Float, val teeth: Int, val outline: Path)

    private val c = Offset(s / 2f, s / 2f)
    private val r = 0.96f * s / 2f
    private val ded = gearDedendum(r, MAIN_TEETH)
    private val track = r - ded - 0.077f * s / 2f
    private val innerRim = track - 0.055f * s / 2f
    private val badge = max(11f * dp, s * 0.04f)
    private val badgeCurrent = badge * 1.18f

    // The gold ring the stones sit on, with a recessed enamel channel down its middle.
    private val ringHalf = max(5f * dp, s * 0.021f)
    private val channelHalf = ringHalf * 0.42f
    private val ringOuter = track + ringHalf
    private val ringInner = track - ringHalf
    private val ring = annulus(c, ringOuter, ringInner)
    private val channel = annulus(c, track + channelHalf + 0.8f * dp, track - channelHalf - 0.8f * dp)
    private val hub = 0.30f * r

    override val markerDistance = track
    override val markerRadius = badgeCurrent
    override val dateRadius = hub * 0.8f
    override val hubOuterRadius = hub
    override val bridge = BridgeSpec(innerRadius = hub + 8 * dp, freeRadius = innerRim - 2 * dp, maxSpan = 2.3f)

    private val wheel = Path().apply { addGearOutline(this, c, r, MAIN_TEETH) }
    private val stone = GemCut(badge)
    private val stoneCurrent = GemCut(badgeCurrent)

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
        val hair = Stroke(dp)
        drawTrain(frame, hair)

        val wheelRot = dir * frame.phase * 0.25f
        rotate(wheelRot.toDegrees(), c) {
            drawPath(wheel, frame.palette.gold.copy(alpha = 0.55f), style = hair)
            drawCircle(frame.palette.gold.copy(alpha = 0.25f), r - ded - 3 * dp, c, style = hair)
            drawCircle(frame.palette.gold.copy(alpha = 0.25f), innerRim, c, style = hair)
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
                        frame.palette.gold.copy(alpha = 0.22f),
                        hubPoint + across * (side * hubHalfWidth),
                        rimPoint + across * (side * rimHalfWidth),
                        dp
                    )
                }
            }
            drawCircle(frame.palette.gold.copy(alpha = 0.22f), hub, c, style = hair)
            drawCircle(frame.palette.gold.copy(alpha = 0.22f), hub * 0.8f, c, style = hair)
        }

        drawRing(frame)

        frame.bridge?.draw(this)

        if (frame.showNow) {
            val handAngle = dayAngle(frame.nowMinutes, frame.rtl)
            val tip = pointOn(c, track - s * 0.03f, handAngle)
            drawLine(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.75f)), start = c, end = tip),
                c, tip, 1.5f * dp, StrokeCap.Round
            )
            nowIndicator(c, track, handAngle, frame.current.color, dp)
        }

        frame.prayers.forEach { p ->
            val theta = dayAngle(p.minutes, frame.rtl)
            val at = pointOn(c, track, theta)
            val isCurrent = p.type == frame.current.type
            val radius = if (isCurrent) badgeCurrent else badge
            if (isCurrent) halo(at, radius * 0.6f, radius * 2.2f, p.color, 0.35f)
            val sparkle = if (isCurrent) 0.55f + 0.45f * abs(sin(frame.phase * 9)) else 0f
            gemStone(p, at, if (isCurrent) stoneCurrent else stone, frame.palette.brass, frame.palette.gold, dp, sparkle)
            timeLabel(frame, p.label, pointOn(c, track - radius - s * 0.05f, theta))
        }
    }

    /**
     * The solid gold ring: bevelled edges, engraved hour marks either side of the channel and
     * the prayer colours as an enamel inlay, glazed so it reads as set into the metal.
     */
    private fun DrawScope.drawRing(frame: GearFrame) {
        val palette = frame.palette
        elevation(ring, 3f * dp)
        drawPath(ring, metalBrush(palette.brass, c, ringOuter))
        drawCircle(
            Brush.linearGradient(
                listOf(Color(0xD9FFF6D6), Color(0x1AFFF6D6)),
                start = c - Offset(ringOuter, ringOuter),
                end = c + Offset(ringOuter, ringOuter)
            ),
            ringOuter - 0.6f * dp, c, style = Stroke(dp)
        )
        drawCircle(
            Brush.linearGradient(
                listOf(Color(0x263C280A), Color(0xBF3C280A)),
                start = c - Offset(ringInner, ringInner),
                end = c + Offset(ringInner, ringInner)
            ),
            ringInner + 0.6f * dp, c, style = Stroke(dp)
        )
        drawCircle(Color(0xB33C280A), ringOuter, c, style = Stroke(0.8f * dp))
        drawCircle(Color(0xB33C280A), ringInner, c, style = Stroke(0.8f * dp))

        val bevel = 0.6f * dp / track
        for (i in 0 until 24) {
            val a = TAU / 4 + i * TAU / 24
            val major = i % 6 == 0
            val reach = if (major) ringHalf - dp else channelHalf + (ringHalf - channelHalf) * 0.55f
            for (side in floatArrayOf(1f, -1f)) {
                val from = track + side * (channelHalf + dp)
                val to = track + side * reach
                drawLine(
                    Color(0xFF3C2608).copy(alpha = if (major) 0.75f else 0.45f),
                    pointOn(c, from, a),
                    pointOn(c, to, a),
                    (if (major) 1.4f else 0.8f) * dp
                )
                drawLine(palette.tone(Color(0x59FFF0C8)), pointOn(c, from, a + bevel), pointOn(c, to, a + bevel), 0.6f * dp)
            }
        }

        drawPath(channel, Color(0xCC1E1406))
        prayerTrack(frame, c, track, channelHalf * 2, 0.95f)
        drawCircle(
            Brush.radialGradient(
                ringInner / ringOuter to Color.White.copy(alpha = 0.28f),
                (track - channelHalf * 0.1f) / ringOuter to Color.White.copy(alpha = 0.05f),
                (track + channelHalf) / ringOuter to Color.Black.copy(alpha = 0.25f),
                center = c,
                radius = ringOuter
            ),
            track, c, style = Stroke(channelHalf * 2)
        )
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
}
