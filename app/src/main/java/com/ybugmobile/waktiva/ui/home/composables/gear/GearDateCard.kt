package com.ybugmobile.waktiva.ui.home.composables.gear

import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.HijriUtils
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import java.time.format.DateTimeFormatter
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath

/** One side of the date card: the month's name and the day of the month. */
internal class DateSide(val month: String, val day: String)

/**
 * The date in the dial's own material, in place of the generic card:
 * - brass: an ivory enamel dial with the month engraved round the top and the day in a
 *   framed date window;
 * - steel: a midnight sub-dial in a screwed steel bezel, with glowing lume numerals;
 * - skeleton: a smoked crystal in a beaded gold bezel, with gold lettering and a small stone.
 *
 * Tapping flips the face between the Gregorian and Hijri dates; the bezel stays put.
 * [accent] is the current prayer, whose colour marks the face.
 */
@Composable
internal fun GearDateCard(
    style: DayCircleStyle,
    day: PrayerDay,
    isHijriVisible: Boolean,
    onFlip: () -> Unit,
    accent: GearPrayer,
    palette: GearPalette,
    diameter: Dp,
    modifier: Modifier = Modifier
) {
    val gregorian = rememberGregorianSide(day)
    val hijri = rememberHijriSide(day)
    val textMeasurer = rememberTextMeasurer()
    val rotation by animateFloatAsState(
        targetValue = if (isHijriVisible) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "dateFlip"
    )

    Box(
        modifier = modifier
            .size(diameter)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFlip() },
        contentAlignment = Alignment.Center
    ) {
        if (style != DayCircleStyle.BRASS) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind { if (style == DayCircleStyle.STEEL) steelBezel(palette) else skeletonBezel(palette) }
                    }
            )
        }
        Spacer(
            Modifier
                .size(diameter * faceRatio(style))
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .drawWithCache {
                    val front = faceFor(style, gregorian, textMeasurer, palette)
                    val back = faceFor(style, hijri, textMeasurer, palette)
                    onDrawBehind {
                        // Past halfway the back is showing; mirror it so it doesn't read reversed.
                        if (rotation <= 90f) {
                            front.draw(this, accent)
                        } else {
                            scale(-1f, 1f) { back.draw(this, accent) }
                        }
                    }
                }
        )
    }
}

/** How much of the card the flipping face covers; the rest is the fixed bezel. */
private fun faceRatio(style: DayCircleStyle) = when (style) {
    DayCircleStyle.STEEL -> 0.84f
    DayCircleStyle.SKELETON -> 0.86f
    else -> 1f
}

@Composable
private fun rememberGregorianSide(day: PrayerDay): DateSide {
    val locale = LocalConfiguration.current.locales[0]
    return remember(day.date, locale) {
        DateSide(
            month = day.date.format(DateTimeFormatter.ofPattern("MMMM", locale)).uppercase(locale),
            day = day.date.format(DateTimeFormatter.ofPattern("dd", locale))
        )
    }
}

@Composable
private fun rememberHijriSide(day: PrayerDay): DateSide {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(day, locale) {
        val hijri = day.hijriDate ?: HijriUtils.calculateFallbackHijri(day.date)
        val monthRes = hijri?.let {
            context.resources.getIdentifier("hijri_month_${it.monthNumber}", "string", context.packageName)
        } ?: 0
        val month = if (monthRes != 0) context.getString(monthRes) else hijri?.monthEn.orEmpty()
        DateSide(month = month.uppercase(locale), day = hijri?.day?.toString().orEmpty())
    }
}

private fun CacheDrawScope.faceFor(
    style: DayCircleStyle,
    side: DateSide,
    measurer: TextMeasurer,
    palette: GearPalette
): DateFace = when (style) {
    DayCircleStyle.STEEL -> SteelDateFace(this, side, measurer, palette)
    DayCircleStyle.SKELETON -> SkeletonDateFace(this, side, measurer, palette)
    else -> BrassDateFace(this, side, measurer, palette)
}

/** A face laid out once for its size and text, then drawn every frame. */
private interface DateFace {
    fun draw(scope: DrawScope, accent: GearPrayer)
}

// ---------------------------------------------------------------------------
// Brass: ivory enamel dial with a date window
// ---------------------------------------------------------------------------

private class BrassDateFace(scope: CacheDrawScope, side: DateSide, measurer: TextMeasurer, private val palette: GearPalette) : DateFace {
    private val d = scope.size.minDimension
    private val r = d / 2f
    private val c = scope.size.center
    private val dp = scope.density
    private val disc = Path().apply { addOval(Rect(c, r)) }
    private val ink = palette.tone(Color(0xFF5A3E12))
    private val month = ArcLabel(side.month, c, r * 0.64f, d * 0.12f, ink, SerifBold, 0.08f, 2f, dp)
    private val window = Rect(Offset(c.x - d * 0.21f, c.y + d * 0.04f - d * 0.16f), Size(d * 0.42f, d * 0.32f))
    private val dayText = with(scope) {
        measurer.measure(side.day, TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = (d * 0.25f).toSp(), color = Color(0xFF1D1A14)))
    }
    private val pip = Path().apply {
        val y = c.y + d * 0.34f
        val s = d * 0.05f
        moveTo(c.x, y - s)
        lineTo(c.x + s, y)
        lineTo(c.x, y + s)
        lineTo(c.x - s, y)
        close()
    }

    override fun draw(scope: DrawScope, accent: GearPrayer) = with(scope) {
        clipPath(disc) {
            drawCircle(
                Brush.radialGradient(
                    0f to palette.tone(Color(0xFFFBF5E4)),
                    0.7f to palette.tone(Color(0xFFEFE3C4)),
                    1f to palette.tone(Color(0xFFD9C79C)),
                    center = c + Offset(-r * 0.25f, -r * 0.3f),
                    radius = r * 1.25f
                ),
                r, c
            )
            // Sunburst guilloché
            for (k in 0 until 90) {
                val a = k * TAU / 90
                drawLine(Color(0x12785A28), pointOn(c, r * 0.12f, a), pointOn(c, r, a), 0.6f * dp)
            }
        }
        // Date ring: 31 fine marks inside the rim
        for (k in 0 until 31) {
            val a = -TAU / 4 + k * TAU / 31
            val major = k % 5 == 0
            drawLine(
                Color(0xFF5A3E12).copy(alpha = if (major) 0.55f else 0.28f),
                pointOn(c, r * 0.9f, a),
                pointOn(c, r * if (major) 0.8f else 0.85f, a),
                (if (major) 1f else 0.6f) * dp
            )
        }
        drawCircle(Color(0x735A3E12), r * 0.93f, c, style = Stroke(0.8f * dp))
        month.draw(this)

        // Date window in a bevelled brass frame
        val frame = window.inflate(d * 0.025f)
        translate(0f, d * 0.01f) {
            drawRoundRect(Color.Black.copy(alpha = 0.25f), frame.topLeft, frame.size, CornerRadius(d * 0.05f))
        }
        drawRoundRect(metalBrush(palette.brass, frame.center, frame.width / 2f), frame.topLeft, frame.size, CornerRadius(d * 0.05f))
        drawRoundRect(
            Brush.verticalGradient(
                0f to Color(0xFFE9E4D6),
                0.18f to Color(0xFFFFFDF6),
                1f to Color(0xFFF4EFE2),
                startY = window.top,
                endY = window.bottom
            ),
            window.topLeft, window.size, CornerRadius(d * 0.03f)
        )
        drawRoundRect(Color(0x993C280A), window.topLeft, window.size, CornerRadius(d * 0.03f), style = Stroke(0.8f * dp))
        drawCentred(dayText, window.center + Offset(0f, d * 0.012f))

        // Enamel pip in the current prayer's colour
        drawPath(pip, accent.color)
        drawPath(pip, Color(0xB33C280A), style = Stroke(0.8f * dp))
        drawCircle(Color(0xB33C280A), r, c, style = Stroke(dp))
    }
}

// ---------------------------------------------------------------------------
// Steel: midnight sub-dial with lume numerals
// ---------------------------------------------------------------------------

/** The sub-dial's fixed bezel: brushed steel with four screws. */
private fun DrawScope.steelBezel(palette: GearPalette) {
    val d = size.minDimension
    val r = d / 2f
    val ri = r * 0.84f
    val dp = density
    translate(0f, 2f * dp) { drawCircle(Color.Black.copy(alpha = 0.3f), r + dp, center) }
    drawCircle(metalBrush(palette.steel, center, r), r, center)
    drawCircle(Color(0xB30A0E16), r, center, style = Stroke(dp))
    drawArc(
        Color.White.copy(alpha = 0.4f), 180f, 144f, false,
        topLeft = center - Offset(r - 0.8f * dp, r - 0.8f * dp),
        size = Size((r - 0.8f * dp) * 2, (r - 0.8f * dp) * 2),
        style = Stroke(0.8f * dp)
    )
    for (k in 0 until 4) {
        val at = pointOn(center, (r + ri) / 2f, TAU / 8 + k * TAU / 4)
        drawCircle(palette.tone(Color(0xFF2A3346)), d * 0.022f, at)
        drawLine(Color(0xB3DCE4F0), pointOn(at, d * 0.016f, 0.6f), pointOn(at, d * 0.016f, 0.6f + TAU / 2), 0.7f * dp)
    }
}

private class SteelDateFace(scope: CacheDrawScope, side: DateSide, measurer: TextMeasurer, private val palette: GearPalette) : DateFace {
    private val ri = scope.size.minDimension / 2f
    private val d = ri * 2 / 0.84f // the whole card, bezel included
    private val c = scope.size.center
    private val dp = scope.density
    private val disc = Path().apply { addOval(Rect(c, ri)) }
    private val monthText = with(scope) {
        fitted(measurer, side.month, TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em, color = Color(0xFFAEB9CF)), d * 0.1f, ri * 1.35f)
    }
    private val dayText = with(scope) {
        measurer.measure(
            side.day,
            TextStyle(
                fontFamily = IBMPlexArabic,
                fontWeight = FontWeight.SemiBold,
                fontSize = (d * 0.36f).toSp(),
                color = Color(0xFFE9F7E6),
                shadow = Shadow(Color(0x8CAAFFC8), blurRadius = d * 0.06f)
            )
        )
    }

    override fun draw(scope: DrawScope, accent: GearPrayer) = with(scope) {
        clipPath(disc) {
            drawCircle(
                Brush.radialGradient(
                    listOf(palette.tone(Color(0xFF24324F)), palette.tone(Color(0xFF0D1322))),
                    center = c + Offset(0f, -ri * 0.3f),
                    radius = ri * 1.3f
                ),
                ri, c
            )
            // Azurage: fine concentric grooves
            var groove = 2f * dp
            while (groove < ri) {
                drawCircle(Color.White.copy(alpha = 0.05f), groove, c, style = Stroke(0.6f * dp))
                groove += 2.2f * dp
            }
        }
        drawCircle(Color.Black.copy(alpha = 0.6f), ri, c, style = Stroke(1.2f * dp))
        drawCentred(monthText, c + Offset(0f, -ri * 0.52f))
        drawCentred(dayText, c + Offset(0f, d * 0.04f))
        val arc = ri * 0.8f
        drawArc(
            accent.color, 90f - 25.8f, 51.6f, false,
            topLeft = c - Offset(arc, arc),
            size = Size(arc * 2, arc * 2),
            style = Stroke(maxOf(1.5f * dp, d * 0.025f), cap = StrokeCap.Round)
        )
    }
}

// ---------------------------------------------------------------------------
// Skeleton: smoked crystal in a beaded gold bezel
// ---------------------------------------------------------------------------

/** The crystal's fixed bezel: gold with a ring of milgrain beads, like the prayer stones. */
private fun DrawScope.skeletonBezel(palette: GearPalette) {
    val r = size.minDimension / 2f
    val ri = r * 0.86f
    val dp = density
    translate(0f, 2f * dp) { drawCircle(Color.Black.copy(alpha = 0.3f), r + dp, center) }
    drawCircle(metalBrush(palette.brass, center, r), r, center)
    drawCircle(Color(0xBF3C280A), r, center, style = Stroke(0.8f * dp))
    val bead = palette.tone(Color(0xFFE9CF8A))
    for (k in 0 until 28) {
        val at = pointOn(center, (r + ri) / 2f, k * TAU / 28)
        drawCircle(bead, (r - ri) * 0.32f, at)
        drawCircle(Color.White.copy(alpha = 0.6f), (r - ri) * 0.12f, at + Offset(-0.4f * dp, -0.5f * dp))
    }
}

private class SkeletonDateFace(scope: CacheDrawScope, side: DateSide, measurer: TextMeasurer, private val palette: GearPalette) : DateFace {
    private val ri = scope.size.minDimension / 2f
    private val d = ri * 2 / 0.86f // the whole card, bezel included
    private val c = scope.size.center
    private val dp = scope.density
    private val disc = Path().apply { addOval(Rect(c, ri)) }
    private val month = ArcLabel(side.month, c, ri * 0.7f, d * 0.11f, palette.gold.copy(alpha = 0.95f), SerifBold, 0.08f, 2f, dp)
    private val dayText = with(scope) {
        measurer.measure(
            side.day,
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = (d * 0.3f).toSp(),
                brush = Brush.verticalGradient(
                    listOf(palette.tone(Color(0xFFFBE7B0)), palette.tone(Color(0xFFD9B25E)), palette.tone(Color(0xFF9C7430)))
                ),
                shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, d * 0.01f), d * 0.03f)
            )
        )
    }
    private val stone = GemCut(d * 0.075f)

    // A crescent of reflected light on the upper left of the crystal.
    private val reflection = Path().apply {
        arcTo(Rect(c, ri), 171f, 108f, true)
        arcTo(Rect(c + Offset(ri * 0.25f, ri * 0.25f), ri * 1.05f), 279f, -108f, false)
        close()
    }

    override fun draw(scope: DrawScope, accent: GearPrayer) = with(scope) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0x8C121622), Color(0xCC080A12)), center = c, radius = ri),
            ri, c
        )
        drawCircle(Color(0xCC3C280A), ri, c, style = Stroke(dp))
        drawCircle(palette.gold.copy(alpha = 0.35f), ri * 0.9f, c, style = Stroke(0.6f * dp))
        month.draw(this)
        drawCentred(dayText, c + Offset(0f, d * 0.03f))
        gemStone(accent, c + Offset(0f, ri * 0.62f), stone, palette.brass, palette.tone(Color(0xFFE9CF8A)), dp)
        clipPath(disc) {
            drawPath(
                reflection,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0f)),
                    start = c - Offset(ri, ri),
                    end = c
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared text helpers
// ---------------------------------------------------------------------------

private val SerifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

/**
 * Text centred along the top of a circle, reading left to right, shrunk until it fits within
 * [maxSweep] radians. Drawn with the platform's text-on-path so Arabic script stays joined.
 */
private class ArcLabel(
    private val text: String,
    center: Offset,
    radius: Float,
    size: Float,
    color: Color,
    typeface: Typeface,
    spacingEm: Float,
    maxSweep: Float,
    dp: Float
) {
    private val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        letterSpacing = spacingEm
        textAlign = NativePaint.Align.CENTER
        this.color = color.toArgb()
    }

    init {
        while (paint.measureText(text) > radius * maxSweep && paint.textSize > 7f * dp) {
            paint.textSize *= 0.92f
        }
    }

    // Left to right over the top; with centre alignment the text sits in the middle of the arc.
    private val path = NativePath().apply {
        addArc(RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius), 180f, 180f)
    }

    /** Moves the glyphs from sitting on the arc to being centred on it. */
    private val centreOffset = -(paint.ascent() + paint.descent()) / 2f

    fun draw(scope: DrawScope) = scope.drawIntoCanvas { it.nativeCanvas.drawTextOnPath(text, path, 0f, centreOffset, paint) }
}

/** [text] measured at [size] px, or smaller until it is no wider than [maxWidth]. */
private fun CacheDrawScope.fitted(measurer: TextMeasurer, text: String, style: TextStyle, size: Float, maxWidth: Float): TextLayoutResult {
    var px = size
    var layout = measurer.measure(text, style.copy(fontSize = px.toSp()))
    while (layout.size.width > maxWidth && px > 7f * density) {
        px *= 0.92f
        layout = measurer.measure(text, style.copy(fontSize = px.toSp()))
    }
    return layout
}

private fun DrawScope.drawCentred(layout: TextLayoutResult, at: Offset) {
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}
