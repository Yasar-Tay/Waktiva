package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.drawText
import kotlin.math.sign

// Shared drawing helpers for the gear dials, the prayer plaque and the special-day bridge.
// Colours come from GearPalette.

/**
 * Diagonal metal gradient lit from the top-left. When drawn inside a rotation of
 * [rotation] radians around [pivot], the light stays put while the part turns.
 */
internal fun metalBrush(
    stops: Array<Pair<Float, Color>>,
    pivot: Offset,
    radius: Float,
    rotation: Float = 0f,
    flip: Boolean = false
): Brush {
    val topLeft = pivot + Offset(-radius, -radius)
    val bottomRight = pivot + Offset(radius, radius)
    val (from, to) = if (flip) bottomRight to topLeft else topLeft to bottomRight
    return Brush.linearGradient(
        *stops,
        start = rotateAround(from, pivot, -rotation),
        end = rotateAround(to, pivot, -rotation)
    )
}

internal fun annulus(center: Offset, outer: Float, inner: Float) = Path().apply {
    addOval(Rect(center, outer))
    addOval(Rect(center, inner))
    fillType = PathFillType.EvenOdd
}

/**
 * Lifts a metal part off the dial: a soft, light shadow of [path], cast [depth] away from the
 * [light]. A faint silhouette under several widening, fainter strokes keeps it soft without blur
 * filters, which older Android versions don't render. Pass the part's [rotation]
 * (radians, around [pivot]) so the shadow turns with the part but still falls away from the light.
 */
internal fun DrawScope.elevation(
    path: Path,
    depth: Float,
    light: GearLight,
    rotation: Float = 0f,
    pivot: Offset = center
) {
    translate(-light.towards.x * depth, -light.towards.y * depth) {
        rotate(rotation.toDegrees(), pivot) {
            for (i in 4 downTo 1) {
                drawPath(path, Color.Black.copy(alpha = 0.035f), style = Stroke(depth * i * 1.3f, join = StrokeJoin.Round))
            }
            drawPath(path, Color.Black.copy(alpha = 0.12f))
        }
    }
}

/** Coloured prayer-to-prayer arcs, as in PrayerCircleVisualization. */
internal fun DrawScope.prayerTrack(f: GearFrame, center: Offset, radius: Float, width: Float, alpha: Float) {
    val sorted = f.prayers.sortedBy { it.minutes }
    sorted.forEachIndexed { i, from ->
        val to = sorted[(i + 1) % sorted.size]
        val start = dayAngle(from.minutes, f.rtl)
        var sweep = dayAngle(to.minutes, f.rtl) - start
        if (f.rtl) {
            if (sweep > 0f) sweep -= TAU
        } else if (sweep < 0f) {
            sweep += TAU
        }
        val gap = 0.01f * sign(sweep)
        drawArc(
            brush = Brush.linearGradient(
                listOf(from.color.copy(alpha = alpha), to.color.copy(alpha = alpha)),
                start = pointOn(center, radius, start),
                end = pointOn(center, radius, start + sweep)
            ),
            startAngle = (start + gap).toDegrees(),
            sweepAngle = (sweep - 2 * gap).toDegrees(),
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width, cap = StrokeCap.Round)
        )
    }
}

internal fun DrawScope.nowIndicator(center: Offset, radius: Float, angle: Float, color: Color, pxPerDp: Float) {
    val s = size.minDimension
    val p = pointOn(center, radius, angle)
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0f)), center = p, radius = s * 0.035f),
        radius = s * 0.035f,
        center = p
    )
    drawCircle(Color.White, s * 0.009f, p)
    drawCircle(color, s * 0.015f, p, style = Stroke(1.5f * pxPerDp))
}

internal fun DrawScope.timeLabel(f: GearFrame, text: String, at: Offset) {
    val layout = f.textMeasurer.measure(text, f.labelStyle)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

internal fun DrawScope.prayerIcon(p: GearPrayer, at: Offset, iconSize: Float, ink: Color = inkOn(p.color)) {
    translate(at.x - iconSize / 2f, at.y - iconSize / 2f) {
        with(p.painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(ink)) }
    }
}

private fun inkOn(color: Color) = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White

internal fun DrawScope.halo(at: Offset, inner: Float, outer: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            inner / outer to color.copy(alpha = alpha),
            1f to color.copy(alpha = 0f),
            center = at,
            radius = outer
        ),
        radius = outer,
        center = at
    )
}

/** Planet gear outline centred on the origin, with an axle hole. */
internal fun planetOutline(radius: Float) = Path().apply {
    addGearOutline(this, Offset.Zero, radius, PLANET_TEETH)
    addOval(Rect(Offset.Zero, radius * 0.30f))
    fillType = PathFillType.EvenOdd
}

/** Cog badge outline centred on the origin, as used by the skeleton dial. */
/**
 * A metal prayer gear with an upright enamel face showing the prayer icon. Its reflections and
 * chamfers follow the [light] while the gear turns by [rotation].
 */
internal fun DrawScope.planet(
    p: GearPrayer,
    at: Offset,
    radius: Float,
    rotation: Float,
    outline: Path,
    sheen: MetalSheen,
    light: GearLight,
    pxPerDp: Float,
    isCurrent: Boolean
) {
    if (isCurrent) halo(at, radius * 0.5f, radius * 2.3f, p.color, 0.42f)

    translate(at.x, at.y) { elevation(outline, 2f * pxPerDp, light, rotation, Offset.Zero) }
    // Drawn in the gear's turning frame, so the light is turned back to stay fixed on screen.
    val localLight = GearLight(light.angle - rotation)
    withTransform({
        translate(at.x, at.y)
        rotate(rotation.toDegrees(), Offset.Zero)
    }) {
        drawPath(outline, sheen.brush(Offset.Zero, localLight))
        bevel(outline, Offset.Zero, radius, localLight, 0.8f * pxPerDp)
        drawPath(outline, Color.Black.copy(alpha = 0.45f), style = Stroke(0.8f * pxPerDp))
        for (i in 0 until 4) {
            val a = i * TAU / 4 + TAU / 8
            drawLine(
                Color.Black.copy(alpha = 0.25f),
                pointOn(Offset.Zero, radius * 0.66f, a),
                pointOn(Offset.Zero, radius * 0.78f, a),
                0.8f * pxPerDp
            )
        }
    }

    val face = radius * if (isCurrent) 0.66f else 0.6f
    drawCircle(p.color, face, at)
    enamelGloss(at, face, light)
    drawCircle(Color.Black.copy(alpha = 0.35f), face, at, style = Stroke(pxPerDp))
    prayerIcon(p, at, face * 1.2f)
}

/** A ruby jewel in a gold setting, as on a watch movement. */
internal fun DrawScope.jewel(at: Offset, radius: Float, palette: GearPalette, pxPerDp: Float) {
    drawCircle(palette.jewelSetting, radius, at)
    drawCircle(Color(0xB33C280A), radius, at, style = Stroke(0.7f * pxPerDp))
    val stone = radius * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            *palette.ruby,
            center = at + Offset(-radius * 0.2f, -radius * 0.25f),
            radius = stone
        ),
        radius = stone,
        center = at
    )
    drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.14f, at + Offset(-radius * 0.2f, -radius * 0.22f))
}
