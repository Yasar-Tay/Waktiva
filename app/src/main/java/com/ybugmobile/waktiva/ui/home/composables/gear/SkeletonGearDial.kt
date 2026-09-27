package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Skeleton: a sleek enamel ring with coloured stones, date disc in the hub, and a metallic luster.
 */
internal class SkeletonGearDial(private val s: Float, private val dp: Float) : GearDial {
    private val c = Offset(s / 2f, s / 2f)
    private val r = 0.96f * s / 2f
    private val ded = gearDedendum(r, MAIN_TEETH)
    private val track = r - ded - 0.077f * s / 2f
    private val innerRim = track - 0.055f * s / 2f
    private val badge = max(11f * dp, s * 0.04f)
    private val badgeCurrent = badge * 1.18f

    // The ring the stones sit on: a clean enamel channel 50% thicker than original with metallic framing.
    private val ringHalf = max(5f * dp, s * 0.021f)
    private val channelHalf = ringHalf * 0.63f // 50% thicker than previous 0.42f
    private val hub = 0.30f * r

    override val markerDistance = track
    override val markerRadius = badgeCurrent
    override val dateRadius = hub * 0.8f
    override val hubOuterRadius = hub
    override val bridge = BridgeSpec(innerRadius = hub + 8 * dp, freeRadius = innerRim - 2 * dp, maxSpan = 2.3f)

    private val stone = GemCut(badge)
    private val stoneCurrent = GemCut(badgeCurrent)

    override fun draw(scope: DrawScope, frame: GearFrame) = with(scope) {
        // Everything over the movement stays still, so it is replayed from a layer; only the
        // current stone's twinkle is drawn afresh.
        frame.still.draw(this, 0) {
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
                gemStone(p, at, if (isCurrent) stoneCurrent else stone, frame.palette.brassSheen, frame.light, frame.palette.gold, dp)
                timeLabel(frame, p.label, pointOn(c, track - radius - s * 0.05f, theta))
            }
        }

        val sparkle = 0.55f + 0.45f * abs(sin(frame.phase * 9))
        gemSparkle(pointOn(c, track, dayAngle(frame.current.minutes, frame.rtl)), stoneCurrent, frame.light, sparkle, dp)
    }

    /**
     * Refactored enamel ring: flatter design centered on a 50% thicker enamel track,
     * maintaining vibrant prayer colors while adding a refined metallic gold luster and borders.
     */
    private fun DrawScope.drawRing(frame: GearFrame) {
        val palette = frame.palette
        val light = frame.light
        val trackWidth = channelHalf * 2f

        // Soft warm halo glow behind the ring
        haloRing(c, track, track * 0.22f, lerp(palette.warmHalo, frame.current.color, 0.3f), 0.15f)

        // Dark metallic channel base for depth
        drawCircle(
            color = Color(0xCC1A1205),
            radius = track,
            style = Stroke(trackWidth + 1.6f * dp)
        )

        // Vibrant prayer track (50% thicker)
        prayerTrack(frame, c, track, trackWidth, 0.95f)

        // Metallic sheen overlay: specular reflection from ambient light source to give metallic luster
        val lightCenter = pointOn(c, track, light.angle)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.32f),
                0.45f to Color.White.copy(alpha = 0.08f),
                1.0f to Color.Black.copy(alpha = 0.22f),
                center = lightCenter,
                radius = track * 1.1f
            ),
            radius = track,
            style = Stroke(trackWidth)
        )

        // Sleek metallic gold hairline borders framing the enamel track
        val outerRadius = track + channelHalf
        val innerRadius = track - channelHalf
        drawCircle(palette.gold.copy(alpha = 0.70f), outerRadius, c, style = Stroke(0.9f * dp))
        drawCircle(palette.gold.copy(alpha = 0.70f), innerRadius, c, style = Stroke(0.9f * dp))
        drawCircle(palette.tone(Color(0x66FFF1C8)), outerRadius - 0.5f * dp, c, style = Stroke(0.5f * dp))
        drawCircle(palette.tone(Color(0x66FFF1C8)), innerRadius + 0.5f * dp, c, style = Stroke(0.5f * dp))
    }
}
