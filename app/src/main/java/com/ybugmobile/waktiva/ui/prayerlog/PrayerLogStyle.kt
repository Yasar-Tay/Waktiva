package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.darken
import com.ybugmobile.waktiva.ui.theme.liquidGlass
import kotlin.math.max

/**
 * The prayer log's colours: the day's spectrum. Each prayer has the colour of its hour's sky,
 * from the dawn's blue through the noon's sand, the afternoon's peach and the dusk's rose to the
 * night's violet ([logColor]); a prayer prayed is a sphere of its colour, and a full day is the
 * whole spectrum round. The game (XP, levels, the streak, badges, the full day's bonus) has a
 * colour of its own, a warm coral apart from every prayer's. A missed prayer is a quiet grey.
 * The spheres and grooves are drawn as on the home screen's dial.
 */
internal object LogColors {
    /** The game's colour. */
    val Accent = Color(0xFFF0997B)

    /** The game's colour for words and small marks: paler, so it holds on the glass. */
    val AccentText = Color(0xFFF7B9A2)

    /** Ink on the game's colour. */
    val AccentInk = Color(0xFF4A1B0C)

    /** A missed prayer: grey, not alarm red. */
    val Missed = Color(0xFFA6A4B4)

    /** The deep glass of a sphere with nothing in it yet. */
    val Night = Color(0xFF0F1A33)
}

/** Each prayer's colour in the log: its hour's sky, in a spectrum across the day. */
internal val PrayerType.logColor: Color
    get() = when (this) {
        PrayerType.FAJR -> Color(0xFF7FB2E5)
        PrayerType.SUNRISE -> Color(0xFFF2D49B)
        PrayerType.DHUHR -> Color(0xFFE8D9A8)
        PrayerType.ASR -> Color(0xFFF2B48A)
        PrayerType.MAGHRIB -> Color(0xFFE28F9B)
        PrayerType.ISHA -> Color(0xFF9A8FD8)
    }

/** Ink on a sphere of this colour: its own hue, nearly black. */
internal val Color.ink: Color get() = lerp(this, Color.Black, 0.72f)

/** The game's colour for small marks on the glass. */
@Composable
@ReadOnlyComposable
internal fun accentInk(): Color = LogColors.AccentText

/**
 * [base] in the game's colour, over a soft shade as the dial's times have, deeper by day so it
 * holds against the pale day sky (the glass theme is "light" at night).
 */
@Composable
@ReadOnlyComposable
internal fun accentText(base: TextStyle): TextStyle {
    val night = LocalGlassTheme.current.isLightMode
    return base.copy(
        color = LogColors.AccentText,
        shadow = Shadow(Color.Black.copy(alpha = if (night) 0.35f else 0.6f), Offset(0f, 1f), if (night) 4f else 8f)
    )
}

/** The light falls from the upper left, as on the home screen's dials. */
private val Towards = Offset(-0.7071f, -0.7071f)

internal fun Color.lighten(amount: Float) =
    Color(red + (1f - red) * amount, green + (1f - green) * amount, blue + (1f - blue) * amount, alpha)

/**
 * A glass sphere as the dial's prayer badges are drawn: a soft shadow under it, a rim in [rim]
 * bright where it faces the light, [fill] inside, paler towards the light, a gleam across that
 * side, and a fine [edge] around it.
 */
internal fun DrawScope.glassOrb(
    center: Offset,
    radius: Float,
    rim: Color,
    fill: Color,
    edge: Color? = Color.White.copy(alpha = 0.3f),
    shadow: Boolean = true
) {
    if (shadow) {
        val shade = radius + 3.dp.toPx()
        val at = center + Offset(0f, 1.5.dp.toPx())
        drawCircle(
            Brush.radialGradient(
                radius * 0.6f / shade to Color.Black.copy(alpha = 0.4f * rim.alpha),
                1f to Color.Transparent,
                center = at,
                radius = shade
            ),
            shade,
            at
        )
    }
    drawCircle(
        Brush.linearGradient(
            listOf(rim.lighten(0.45f), rim, rim.darken(0.35f)),
            start = center + Towards * radius,
            end = center - Towards * radius
        ),
        radius,
        center
    )
    val orb = radius - max(1.6.dp.toPx(), radius * 0.09f)
    drawCircle(
        Brush.radialGradient(
            0f to fill.lighten(0.38f),
            0.6f to fill.lighten(0.1f),
            1f to fill.darken(0.3f),
            center = center + Towards * (orb * 0.45f),
            radius = orb * 1.25f
        ),
        orb,
        center
    )
    drawCircle(Color.Black.copy(alpha = 0.3f * fill.alpha), orb, center, style = Stroke(0.8.dp.toPx()))
    clipPath(Path().apply { addOval(Rect(center, orb)) }) {
        val gleam = center + Towards * (orb * 0.45f)
        drawOval(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.42f * max(fill.alpha, 0.5f)), Color.Transparent),
                start = center + Towards * orb,
                end = center
            ),
            topLeft = Offset(gleam.x - orb * 0.72f, gleam.y - orb * 0.5f),
            size = Size(orb * 1.44f, orb)
        )
    }
    edge?.let { drawCircle(it, radius + 0.35.dp.toPx(), center, style = Stroke(0.7.dp.toPx())) }
}

/** A sphere of [color] all through: a prayer prayed, a badge earned. */
internal fun DrawScope.colorOrb(center: Offset, radius: Float, color: Color, shadow: Boolean = true) =
    glassOrb(center, radius, rim = color, fill = color, edge = Color.White.copy(alpha = 0.35f), shadow = shadow)

/** A soft light around [center], for what's gold or on. */
internal fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (alpha <= 0f) return
    drawCircle(
        Brush.radialGradient(
            0.45f to color.copy(alpha = alpha),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius,
        center
    )
}

/**
 * A ring's groove, as the dial's track: a dark channel [inset] from the edge, a little wider
 * than the [stroke] that runs in it.
 */
internal fun DrawScope.ringGroove(stroke: Float, inset: Float) {
    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
    drawArc(
        color = Color.Black.copy(alpha = 0.28f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(stroke + 1.6.dp.toPx())
    )
}

/**
 * An arc lying in a [ringGroove]: [color], shaded across its width, with a fine glaze along its
 * outer side where the light catches it.
 */
internal fun DrawScope.glazedArc(color: Color, startAngle: Float, sweepAngle: Float, stroke: Float, inset: Float) {
    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round)
    )
    val glazeInset = inset - stroke * 0.22f
    drawArc(
        color = Color.White.copy(alpha = 0.35f * color.alpha),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(glazeInset, glazeInset),
        size = Size(size.width - glazeInset * 2, size.height - glazeInset * 2),
        style = Stroke(max(0.8.dp.toPx(), stroke * 0.22f), cap = StrokeCap.Round)
    )
}

/**
 * A bar in a groove, filled in the game's colour to [fraction], with a glaze along its top and a
 * light at its end, as the dial's track is lit. Its height is the [modifier]'s.
 */
@Composable
internal fun GrooveBar(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val h = size.height
        val r = CornerRadius(h / 2f)
        drawRoundRect(Color.Black.copy(alpha = 0.3f), cornerRadius = r)
        drawRoundRect(
            Color.White.copy(alpha = 0.1f),
            topLeft = Offset(0f, h * 0.5f),
            size = Size(size.width, h * 0.5f),
            cornerRadius = r,
            style = Stroke(0.6.dp.toPx())
        )
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f) return@Canvas
        val w = max(size.width * f, h)
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(LogColors.Accent.darken(0.2f), LogColors.Accent, LogColors.Accent.lighten(0.3f)),
                endX = w
            ),
            size = Size(w, h),
            cornerRadius = r
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), endY = h * 0.6f),
            topLeft = Offset(h * 0.25f, h * 0.1f),
            size = Size(max(w - h * 0.5f, 0f), h * 0.45f),
            cornerRadius = CornerRadius(h * 0.25f)
        )
        glow(Offset(w - h / 2f, h / 2f), h * 1.8f, LogColors.Accent.lighten(0.4f), 0.45f)
    }
}

/** A round button of the app's liquid glass with [icon] on it: the calendar's arrows. */
@Composable
internal fun GlassIconButton(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val glass = LocalGlassTheme.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .liquidGlass(CircleShape, glass, emphasis = 0.15f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = glass.contentColor.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
    }
}
