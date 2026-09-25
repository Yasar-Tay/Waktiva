package com.ybugmobile.waktiva.ui.qibla.composables

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/**
 * The Kaaba, upright and seen a little from above and to the left, centred on [at] and about
 * [size] across: black kiswa on a front and a shaded side face, the lit roof, the gold hizam
 * band running round both faces with its calligraphy, and the gold door. Shared by all the
 * compasses so the Qibla looks the same whichever is shown.
 */
internal fun DrawScope.kaabaIcon(at: Offset, size: Float) {
    val u = size / 2f
    fun p(x: Float, y: Float) = Offset(at.x + x * u, at.y + y * u)
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }

    // The side face rises towards the back, so every point on it is lifted by its depth.
    val lift = 0.5f
    fun side(x: Float, y: Float) = p(x, y - (x - FrontRight) * lift)

    val frontTopLeft = p(FrontLeft, Top)
    val frontTopRight = p(FrontRight, Top)
    val backTopRight = side(BackRight, Top)
    val backTopLeft = p(FrontLeft + (BackRight - FrontRight), Top - (BackRight - FrontRight) * lift)

    // A soft shadow on the ground, so the building stands on the medallion.
    scale(1f, 0.28f, pivot = p(0f, Bottom)) {
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0f)), center = p(0f, Bottom), radius = u * 0.95f),
            u * 0.95f,
            p(0f, Bottom)
        )
    }

    // Kiswa: the front catches the light, the side falls into shade, the roof is lit.
    drawPath(
        quad(frontTopLeft, frontTopRight, p(FrontRight, Bottom), p(FrontLeft, Bottom)),
        Brush.verticalGradient(listOf(Color(0xFF2B2B2E), Color(0xFF0E0E10)), startY = frontTopLeft.y, endY = p(0f, Bottom).y)
    )
    drawPath(quad(frontTopRight, backTopRight, side(BackRight, Bottom), p(FrontRight, Bottom)), Color(0xFF050506))
    drawPath(quad(frontTopLeft, backTopLeft, backTopRight, frontTopRight), Color(0xFF3A3A3E))

    // Hizam: the gold band round the upper walls, with its lines of calligraphy.
    val bandTop = Top + 0.3f
    val bandBottom = bandTop + 0.2f
    drawPath(quad(p(FrontLeft, bandTop), p(FrontRight, bandTop), p(FrontRight, bandBottom), p(FrontLeft, bandBottom)), goldBrush(p(0f, bandTop).y, p(0f, bandBottom).y))
    drawPath(quad(side(FrontRight, bandTop), side(BackRight, bandTop), side(BackRight, bandBottom), side(FrontRight, bandBottom)), GoldShade)
    val script = Stroke(maxOf(0.6f, u * 0.035f))
    var x = FrontLeft + 0.08f
    while (x < FrontRight - 0.1f) {
        drawLine(ScriptInk, p(x, bandTop + 0.1f), p(x + 0.1f, bandTop + 0.1f), script.width)
        x += 0.17f
    }

    // The gold door on the front, raised above the ground as the real one is.
    val door = quad(p(-0.2f, 0.18f), p(0.02f, 0.18f), p(0.02f, 0.68f), p(-0.2f, 0.68f))
    drawPath(door, goldBrush(p(0f, 0.18f).y, p(0f, 0.68f).y))
    drawLine(ScriptInk, p(-0.09f, 0.22f), p(-0.09f, 0.64f), maxOf(0.6f, u * 0.03f))

    // Light along the roof's front edge and the corner facing the light.
    val edge = maxOf(0.6f, u * 0.035f)
    drawLine(Color.White.copy(alpha = 0.45f), frontTopLeft, frontTopRight, edge)
    drawLine(Color.White.copy(alpha = 0.2f), frontTopLeft, p(FrontLeft, Bottom), edge)
}

private const val FrontLeft = -0.7f
private const val FrontRight = 0.2f
private const val BackRight = 0.66f
private const val Top = -0.42f
private const val Bottom = 0.8f

private fun goldBrush(fromY: Float, toY: Float) = Brush.verticalGradient(
    listOf(Color(0xFFFFEDB0), Color(0xFFE0B64A), Color(0xFFA67A25)),
    startY = fromY,
    endY = toY
)

private val GoldShade = Color(0xFF8C6620)
private val ScriptInk = Color(0xB35A3A0A)

/** Deep emerald enamel behind the Kaaba, as in the medallions of Islamic art. */
internal val KaabaEnamel = listOf(Color(0xFF2A8A62), Color(0xFF0E4A33), Color(0xFF072A1D))

/** The gold of the Kaaba's glow and of its medallion's ring. */
internal val KaabaGold = Color(0xFFFFD678)
