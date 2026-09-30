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
 * The shape of a set gemstone of [radius], centred on the origin: the gold setting and the
 * smooth cabochon stone inside it.
 */
internal class GemCut(val radius: Float) {
    val stone = radius * 0.78f
    val setting = Path().apply { addOval(Rect(Offset.Zero, radius)) }
}

/**
 * A prayer as a polished cabochon in a gold bezel, the prayer's sign on its face: the medallion
 * of the night plaque the classic circle opens on a tapped prayer.
 */
internal fun DrawScope.gemStone(
    p: GearPrayer,
    at: Offset,
    cut: GemCut,
    metal: MetalSheen,
    light: GearLight,
    bead: Color,
    pxPerDp: Float
) {
    val r = cut.radius
    val rs = cut.stone

    translate(at.x, at.y) {
        // The gold setting.
        elevation(cut.setting, 1.5f * pxPerDp, light, pivot = Offset.Zero)
        drawPath(cut.setting, metal.brush(Offset.Zero, light))
        bevel(cut.setting, Offset.Zero, r, light, max(0.6f * pxPerDp, r * 0.06f))
        // A dark inset for the setting's depth, hugging the stone.
        drawCircle(Color(0xB33C280A), rs, Offset.Zero, style = Stroke(0.8f * pxPerDp))

        // The stone: light gathers on the side away from the light, as it does in a cabochon.
        drawCircle(
            brush = Brush.radialGradient(
                0f to lerp(p.color, Color.White, 0.35f),
                0.5f to p.color,
                0.85f to lerp(p.color, Color.Black, 0.3f),
                1f to lerp(p.color, Color.Black, 0.6f),
                center = light.towards * (-rs * 0.35f),
                radius = rs * 1.3f
            ),
            radius = rs,
            center = Offset.Zero
        )
        // Shade along the girdle, and a fine gold line round it.
        drawCircle(
            brush = Brush.radialGradient(0.72f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f), center = Offset.Zero, radius = rs),
            radius = rs,
            center = Offset.Zero
        )
        drawCircle(bead.copy(alpha = 0.85f), rs, Offset.Zero, style = Stroke(0.7f * pxPerDp))
    }

    prayerIcon(p, at, r, ink = Color.White.copy(alpha = 0.92f))

    // The highlight facing the light, over the sign as on a real stone.
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
}
