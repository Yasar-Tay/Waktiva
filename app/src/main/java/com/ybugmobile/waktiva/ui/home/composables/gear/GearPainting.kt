package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.drawText
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import kotlin.math.sign

// Shared palette and drawing helpers for the gear dials, the prayer plaque and the special-day bridge.

internal val BrassStops = arrayOf(
    0f to Color(0xFFF6E1A2),
    0.35f to Color(0xFFD4AB5A),
    0.7f to Color(0xFFA77C34),
    1f to Color(0xFF6F5020)
)

internal val SteelStops = arrayOf(
    0f to Color(0xFFE3E9F3),
    0.4f to Color(0xFFA6B1C4),
    0.75f to Color(0xFF66728A),
    1f to Color(0xFF394257)
)

/** Hairline gold used by the skeleton dial. */
internal val SkeletonGold = Color(0xFFDEBE78)

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

/**
 * Surface finish shared by the prayer plaque and the special-day bridge, so both match
 * their dial: engraved brass or steel, or a dark plate with gold hairlines for the skeleton.
 */
internal class PlateFinish(
    private val metal: Array<Pair<Float, Color>>?,
    val edge: Color,
    val inset: Color,
    /** Light line under the engraved inset; null on the skeleton plate. */
    val highlight: Color?,
    val stripe: Color,
    val ink: Color,
    val strongInk: Color,
    /** Screw head colour; null draws no screw. */
    val screw: Color?
) {
    val isMetal get() = metal != null
    val metalStops get() = metal ?: BrassStops

    fun fill(from: Offset, to: Offset): Brush =
        metal?.let { Brush.linearGradient(*it, start = from, end = to) } ?: SolidColor(Color(0xE60C0F18))
}

internal fun plateFinish(style: DayCircleStyle): PlateFinish = when (style) {
    DayCircleStyle.STEEL -> PlateFinish(
        metal = SteelStops,
        edge = Color(0xB30A0E16),
        inset = Color.Black.copy(alpha = 0.28f),
        highlight = Color.White.copy(alpha = 0.25f),
        stripe = Color.White.copy(alpha = 0.13f),
        ink = Color(0xFF1A2130),
        strongInk = Color(0xFF111722),
        screw = Color(0xFF4A5468)
    )
    DayCircleStyle.SKELETON -> PlateFinish(
        metal = null,
        edge = SkeletonGold.copy(alpha = 0.65f),
        inset = SkeletonGold.copy(alpha = 0.28f),
        highlight = null,
        stripe = SkeletonGold.copy(alpha = 0.07f),
        ink = Color(0xFFE9C983),
        strongInk = Color.White,
        screw = null
    )
    else -> PlateFinish(
        metal = BrassStops,
        edge = Color(0xBF3C280A),
        inset = Color.Black.copy(alpha = 0.28f),
        highlight = Color.White.copy(alpha = 0.25f),
        stripe = Color.White.copy(alpha = 0.13f),
        ink = Color(0xFF3F2A0A),
        strongInk = Color(0xFF2A1C06),
        screw = Color(0xFF7A5A26)
    )
}

internal fun annulus(center: Offset, outer: Float, inner: Float) = Path().apply {
    addOval(Rect(center, outer))
    addOval(Rect(center, inner))
    fillType = PathFillType.EvenOdd
}

internal fun DrawScope.softShadow(center: Offset, radius: Float) {
    val c = center + Offset(0f, radius * 0.04f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.45f),
            0.88f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Transparent,
            center = c,
            radius = radius * 1.08f
        ),
        radius = radius * 1.08f,
        center = c
    )
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

internal fun DrawScope.prayerIcon(p: GearPrayer, at: Offset, iconSize: Float) {
    val ink = if (p.color.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White
    translate(at.x - iconSize / 2f, at.y - iconSize / 2f) {
        with(p.painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(ink)) }
    }
}

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
internal fun badgeOutline(radius: Float) = Path().apply { addGearOutline(this, Offset.Zero, radius, 10) }

/** A metal prayer gear with an upright enamel face showing the prayer icon. */
internal fun DrawScope.planet(
    p: GearPrayer,
    at: Offset,
    radius: Float,
    rotation: Float,
    outline: Path,
    stops: Array<Pair<Float, Color>>,
    pxPerDp: Float,
    isCurrent: Boolean
) {
    if (isCurrent) halo(at, radius * 0.5f, radius * 2.3f, p.color, 0.42f)

    withTransform({
        translate(at.x, at.y)
        rotate(rotation.toDegrees(), Offset.Zero)
    }) {
        drawPath(outline, metalBrush(stops, Offset.Zero, radius, rotation))
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
    drawCircle(Color.Black.copy(alpha = 0.35f), face, at, style = Stroke(pxPerDp))
    val inset = face - 1.2f * pxPerDp
    drawArc(
        color = Color.White.copy(alpha = 0.45f),
        startAngle = 189f,
        sweepAngle = 117f,
        useCenter = false,
        topLeft = Offset(at.x - inset, at.y - inset),
        size = Size(inset * 2, inset * 2),
        style = Stroke(0.8f * pxPerDp)
    )
    prayerIcon(p, at, face * 1.2f)
}

/** A cog badge in the prayer's colour, spinning by [rotation]. */
internal fun DrawScope.cogBadge(p: GearPrayer, at: Offset, radius: Float, rotation: Float, outline: Path, pxPerDp: Float) {
    withTransform({
        translate(at.x, at.y)
        rotate(rotation.toDegrees(), Offset.Zero)
    }) {
        drawPath(outline, p.color)
        drawPath(outline, Color.Black.copy(alpha = 0.35f), style = Stroke(0.8f * pxPerDp))
    }
    drawCircle(Color.Black.copy(alpha = 0.18f), radius * 0.72f, at, style = Stroke(0.8f * pxPerDp))
    prayerIcon(p, at, radius * 1.15f)
}

/** A ruby jewel in a gold setting, as on a watch movement. */
internal fun DrawScope.jewel(at: Offset, radius: Float, pxPerDp: Float) {
    drawCircle(Color(0xFFD9B96A), radius, at)
    drawCircle(Color(0xB33C280A), radius, at, style = Stroke(0.7f * pxPerDp))
    val stone = radius * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFFF8A96),
            0.5f to Color(0xFFC2173A),
            1f to Color(0xFF5C0718),
            center = at + Offset(-radius * 0.2f, -radius * 0.25f),
            radius = stone
        ),
        radius = stone,
        center = at
    )
    drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.14f, at + Offset(-radius * 0.2f, -radius * 0.22f))
}
