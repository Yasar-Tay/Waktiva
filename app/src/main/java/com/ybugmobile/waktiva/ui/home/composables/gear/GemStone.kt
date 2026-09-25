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

/**
 * The shapes of a set brilliant-cut stone of [radius], centred on the origin: the gold
 * setting, the stone inside it and its facets. Built once per size.
 */
internal class GemCut(val radius: Float) {
    val stone = radius * 0.74f
    val setting = Path().apply { addOval(Rect(Offset.Zero, radius)) }

    /** Alternate star facets around the table catch the light or fall into shade. */
    val litFacets = Path()
    val shadedFacets = Path()

    /** The octagonal table and the facet edges running from it to the girdle. */
    val edges = Path()

    init {
        val table = stone * 0.5f
        val corners = List(FACETS) { k -> pointOn(Offset.Zero, table, k * TAU / FACETS - TAU / (2 * FACETS)) }
        for (k in 0 until FACETS) {
            val from = corners[k]
            val to = corners[(k + 1) % FACETS]
            val tip = pointOn(Offset.Zero, stone, k * TAU / FACETS)
            (if (k % 2 == 1) litFacets else shadedFacets).apply {
                moveTo(from.x, from.y)
                lineTo(tip.x, tip.y)
                lineTo(to.x, to.y)
                close()
            }
            edges.moveTo(from.x, from.y)
            edges.lineTo(tip.x, tip.y)
        }
        edges.moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { edges.lineTo(it.x, it.y) }
        edges.close()
    }

    private companion object {
        const val FACETS = 8
    }
}

/**
 * A prayer as a coloured brilliant-cut stone in a gold bezel with milgrain beads, like a set
 * amulet, with the prayer's sign engraved on its table. [sparkle] (0..1) makes it twinkle.
 */
internal fun DrawScope.gemStone(
    p: GearPrayer,
    at: Offset,
    cut: GemCut,
    metal: Array<Pair<Float, Color>>,
    bead: Color,
    pxPerDp: Float,
    sparkle: Float = 0f
) {
    val r = cut.radius
    val rs = cut.stone
    translate(at.x, at.y) {
        // Setting
        elevation(cut.setting, 2f * pxPerDp, pivot = Offset.Zero)
        drawPath(cut.setting, metalBrush(metal, Offset.Zero, r))
        drawCircle(Color(0xBF3C280A), r, Offset.Zero, style = Stroke(0.8f * pxPerDp))
        for (k in 0 until MILGRAIN) {
            val b = pointOn(Offset.Zero, r * 0.9f, k * TAU / MILGRAIN)
            drawCircle(bead, r * 0.07f, b)
            drawCircle(Color.White.copy(alpha = 0.6f), r * 0.028f, b + Offset(-r * 0.02f, -r * 0.025f))
        }

        // Stone: light enters from below right, the girdle falls into shade
        drawCircle(
            Brush.radialGradient(
                0f to lerp(p.color, Color.White, 0.3f),
                0.45f to p.color,
                0.8f to lerp(p.color, Color.Black, 0.35f),
                1f to lerp(p.color, Color.Black, 0.65f),
                center = Offset(rs * 0.18f, rs * 0.28f),
                radius = rs * 1.25f
            ),
            rs,
            Offset.Zero
        )
        drawCircle(
            Brush.radialGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), center = Offset.Zero, radius = rs),
            rs,
            Offset.Zero
        )
        drawPath(cut.litFacets, Color.White.copy(alpha = 0.1f))
        drawPath(cut.shadedFacets, Color.Black.copy(alpha = 0.1f))
        drawPath(cut.edges, Color.White.copy(alpha = 0.28f), style = Stroke(0.6f * pxPerDp))
        drawCircle(lerp(p.color, Color.Black, 0.6f), rs, Offset.Zero, style = Stroke(0.8f * pxPerDp))
    }

    prayerIcon(p, at, r, ink = Color.White.copy(alpha = 0.88f))

    // Specular highlight on the upper left
    val glint = at + Offset(-rs * 0.38f, -rs * 0.42f)
    withTransform({
        translate(glint.x, glint.y)
        rotate(-40f, Offset.Zero)
        scale(1f, 0.55f, Offset.Zero)
    }) {
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), center = Offset.Zero, radius = rs * 0.32f),
            rs * 0.32f,
            Offset.Zero
        )
    }

    if (sparkle > 0f) {
        val reach = r * 0.75f * sparkle
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
}

private const val MILGRAIN = 16
