package com.ybugmobile.waktiva.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/**
 * A liquid-glass surface in the style of recent iOS, drawn around the content:
 * - a clear body, lightly tinted with [tint] and a little brighter at the top;
 * - a soft sheen across the top and a faint shade along the bottom, as a thick lens shows;
 * - a thin refraction band just inside the edge;
 * - a bright specular rim, strongest on the upper-left edge that faces the light and again
 *   on the opposite corner, drawn over the content so it always reads as the glass edge;
 * - a soft shadow outside the shape only, so it doesn't darken the glass itself.
 *
 * [emphasis] (0..1) makes the glass denser and its rim brighter, for a selected item.
 * [accent], if given, rings the edge in that colour, for an item that needs to stand out.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.White,
    emphasis: Float = 0f,
    accent: Color? = null
): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val e = emphasis.coerceIn(0f, 1f)
    val rimWidth = (1f + 0.5f * e).dp.toPx()
    val shadowOffset = 3.dp.toPx()

    val body = Brush.verticalGradient(
        0f to tint.copy(alpha = 0.18f + 0.10f * e),
        0.45f to tint.copy(alpha = 0.07f + 0.07f * e),
        1f to tint.copy(alpha = 0.11f + 0.08f * e)
    )
    val sheen = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.26f + 0.12f * e), Color.Transparent),
        center = Offset(size.width * 0.35f, 0f),
        radius = size.width * 0.65f
    )
    val bottomShade = Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f))
    val refraction = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.03f),
        0.5f to Color.White.copy(alpha = 0.12f),
        1f to Color.White.copy(alpha = 0.03f),
        start = Offset(0f, size.height),
        end = Offset(size.width, 0f)
    )
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.9f),
        0.28f to Color.White.copy(alpha = 0.2f + 0.1f * e),
        0.62f to Color.White.copy(alpha = 0.08f + 0.08f * e),
        1f to Color.White.copy(alpha = 0.5f + 0.2f * e),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )

    onDrawWithContent {
        clipPath(path, ClipOp.Difference) {
            translate(0f, shadowOffset) { drawPath(path, Color.Black.copy(alpha = 0.12f)) }
        }
        drawPath(path, body)
        clipPath(path) {
            drawRect(sheen)
            drawRect(bottomShade)
            drawPath(path, refraction, style = Stroke(4.dp.toPx()))
        }
        drawContent()
        accent?.let { drawPath(path, it, style = Stroke(2.dp.toPx())) }
        drawPath(path, rim, style = Stroke(rimWidth))
    }
}
