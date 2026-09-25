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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Skeleton: a hairline wheel with five straight spokes over a faint background gear train;
 * the prayers are cog badges. The date sits as a disc in the hub.
 */
internal class SkeletonGearDial(private val s: Float, private val dp: Float) : GearDial {
    private class TrainGear(val center: Offset, val radius: Float, val teeth: Int, val outline: Path)

    private val c = Offset(s / 2f, s / 2f)
    private val r = 0.96f * s / 2f
    private val ded = gearDedendum(r, MAIN_TEETH)
    private val track = r - ded - 0.077f * s / 2f
    private val innerRim = track - 0.055f * s / 2f
    private val badge = max(9f * dp, s * 0.03f)
    private val badgeCurrent = badge * 1.18f
    private val hub = 0.30f * r

    override val markerDistance = track
    override val markerRadius = badgeCurrent
    override val hubRadius = hub * 0.8f
    override val hubOuterRadius = hub
    override val bridge = BridgeSpec(innerRadius = hub + 8 * dp, freeRadius = innerRim - 2 * dp, maxSpan = 2.3f)

    private val wheel = Path().apply { addGearOutline(this, c, r, MAIN_TEETH) }
    private val badgePath = badgeOutline(badge)
    private val badgeCurrentPath = badgeOutline(badgeCurrent)

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

        frame.prayers.forEachIndexed { i, p ->
            val theta = dayAngle(p.minutes, frame.rtl)
            val at = pointOn(c, track, theta)
            val isCurrent = p.type == frame.current.type
            val radius = if (isCurrent) badgeCurrent else badge
            if (isCurrent) halo(at, radius * 0.6f, radius * 2.2f, p.color, 0.35f)
            val spin = dir * frame.phase * if (i % 2 == 1) -1f else 1f
            cogBadge(p, at, radius, spin, if (isCurrent) badgeCurrentPath else badgePath, dp)
            timeLabel(frame, p.label, pointOn(c, track - radius - s * 0.05f, theta))
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
}
