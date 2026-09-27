package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.max

/**
 * The shape of a set gemstone of [radius], centred on the origin:
 * the gold setting and the smooth cabochon stone inside it.
 */
internal class GemCut(val radius: Float) {
    val stone = radius * 0.78f
    val setting = Path().apply { addOval(Rect(Offset.Zero, radius)) }
    val litFacets = Path()
    val shadedFacets = Path()
    val edges = Path()
}

/**
 * A prayer as a sleek, polished gemstone in a gold bezel, with the prayer's
 * sign engraved on its face. [sparkle] (0..1) makes it twinkle.
 */
internal fun DrawScope.gemStone(
    p: GearPrayer,
    at: Offset,
    cut: GemCut,
    metal: MetalSheen,
    light: GearLight,
    bead: Color,
    pxPerDp: Float,
    sparkle: Float = 0f
) = gemStone(p.color, at, cut, metal, light, bead, pxPerDp, sparkle) {
    prayerIcon(p, at, cut.radius, ink = Color.White.copy(alpha = 0.92f))
}

/**
 * A sleek, polished cabochon gemstone of [color] in a refined bezel of [metal]; [sign],
 * if given, is drawn on its face under the highlight. [sparkle] (0..1) makes it twinkle.
 */
internal fun DrawScope.gemStone(
    color: Color,
    at: Offset,
    cut: GemCut,
    metal: MetalSheen,
    light: GearLight,
    bead: Color,
    pxPerDp: Float,
    sparkle: Float = 0f,
    sign: (DrawScope.() -> Unit)? = null
) {
    val r = cut.radius
    val rs = cut.stone

    translate(at.x, at.y) {
        // Refactored Gold Bezel Setting
        elevation(cut.setting, 1.5f * pxPerDp, light, pivot = Offset.Zero)
        drawPath(cut.setting, metal.brush(Offset.Zero, light))
        bevel(cut.setting, Offset.Zero, r, light, max(0.6f * pxPerDp, r * 0.06f))
        
        // Inner dark inset for bezel depth
        drawCircle(Color(0xB33C280A), r, Offset.Zero, style = Stroke(0.8f * pxPerDp))

        // Luminous cabochon gemstone body (smooth radial gradient)
        val lightOffset = light.towards * (-rs * 0.35f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to lerp(color, Color.White, 0.35f),
                0.50f to color,
                0.85f to lerp(color, Color.Black, 0.30f),
                1.0f to lerp(color, Color.Black, 0.60f),
                center = lightOffset,
                radius = rs * 1.30f
            ),
            radius = rs,
            center = Offset.Zero
        )

        // Subtle outer shadow on gemstone girdle
        drawCircle(
            brush = Brush.radialGradient(
                0.72f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.30f),
                center = Offset.Zero,
                radius = rs
            ),
            radius = rs,
            center = Offset.Zero
        )

        // Fine gold hairline border framing the gemstone
        drawCircle(
            color = bead.copy(alpha = 0.85f),
            radius = rs,
            style = Stroke(0.7f * pxPerDp)
        )
    }

    // Prayer icon / sign drawn clearly over the smooth gem face
    sign?.invoke(this)

    // Sleek specular highlight facing the light source
    val glint = at + light.towards * (rs * 0.52f)
    withTransform({
        translate(glint.x, glint.y)
        rotate(Math.toDegrees(light.angle.toDouble()).toFloat() + 90f, Offset.Zero)
        scale(1f, 0.45f, Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                center = Offset.Zero,
                radius = rs * 0.35f
            ),
            radius = rs * 0.35f,
            center = Offset.Zero
        )
    }

    if (sparkle > 0f) gemSparkle(at, cut, light, sparkle, pxPerDp)
}

/**
 * The twinkle of a [gemStone] at [at]: a cross of light over its highlight, [sparkle] (0..1) long.
 * Drawable on its own, so a dial can keep the stone still and animate only this.
 */
internal fun DrawScope.gemSparkle(at: Offset, cut: GemCut, light: GearLight, sparkle: Float, pxPerDp: Float) {
    val glint = at + light.towards * (cut.stone * 0.52f)
    val reach = cut.radius * 0.75f * sparkle
    for (arm in listOf(Offset(reach, 0f), Offset(0f, reach))) {
        drawLine(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0f),
                0.5f to Color.White.copy(alpha = 0.95f),
                1f to Color.White.copy(alpha = 0f),
                start = glint - arm,
                end = glint + arm
            ),
            glint - arm,
            glint + arm,
            1.2f * pxPerDp
        )
    }
}
