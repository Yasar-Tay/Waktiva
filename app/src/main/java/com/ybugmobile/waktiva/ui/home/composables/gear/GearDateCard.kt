package com.ybugmobile.waktiva.ui.home.composables.gear

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
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

/** One side of the date card: the month's short name and the day of the month. */
internal class DateSide(val month: String, val day: String)

/**
 * The date on a glass gem set at the dial's centre, cut like the prayer stones (see [GlassGemFace]),
 * in the dial's own setting:
 * - brass: set straight into the wheel's hub;
 * - steel: in a polished steel bezel with four screws;
 * - skeleton: in a gold bezel with milgrain beads.
 *
 * Tapping flips the gem between the Gregorian and Hijri dates; the bezel stays put.
 * [accent] is the current prayer, whose colour tints the glass; [light] is read while drawing,
 * so the metal and the glass follow the light without recomposing.
 */
@Composable
internal fun GearDateCard(
    style: DayCircleStyle,
    day: PrayerDay,
    isHijriVisible: Boolean,
    onFlip: () -> Unit,
    accent: GearPrayer,
    palette: GearPalette,
    light: () -> GearLight,
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
                        val edge = Path().apply { addOval(Rect(size.center, size.minDimension / 2f)) }
                        onDrawBehind {
                            if (style == DayCircleStyle.STEEL) steelBezel(palette, light(), edge) else skeletonBezel(palette, light(), edge)
                        }
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
                    val front = faceFor(style, gregorian, textMeasurer)
                    val back = faceFor(style, hijri, textMeasurer)
                    onDrawBehind {
                        // Past halfway the back is showing; mirror it so it doesn't read reversed.
                        if (rotation <= 90f) {
                            front.draw(this, accent, light())
                        } else {
                            scale(-1f, 1f) { back.draw(this, accent, light()) }
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
            // Abbreviated, so the lettering stays large; some locales end abbreviations with a dot.
            month = day.date.format(DateTimeFormatter.ofPattern("MMM", locale)).replace(".", "").uppercase(locale),
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
        // The first three letters, as the calendar strip abbreviates Hijri months.
        DateSide(month = month.take(3).uppercase(locale), day = hijri?.day?.toString().orEmpty())
    }
}

private fun CacheDrawScope.faceFor(
    style: DayCircleStyle,
    side: DateSide,
    measurer: TextMeasurer
): DateFace = GlassGemFace(this, side, measurer, style)

/** A face laid out once for its size and text, then drawn every frame. */
private interface DateFace {
    fun draw(scope: DrawScope, accent: GearPrayer, light: GearLight)
}

// ---------------------------------------------------------------------------
// The face: a glass gem, the same in every style
// ---------------------------------------------------------------------------

/**
 * The date on a gem of smoked glass, cut like the prayer stones: an octagonal table carrying the
 * month and the day, star facets running out to the girdle, and a body tinted by the current
 * prayer that glows on the side away from the light, as light passing through a stone does. A
 * highlight and a bright rim on the lit side make it read as glass, like the liquid glass cards.
 * The smoke keeps the white lettering readable over any sky showing through.
 */
private class GlassGemFace(scope: CacheDrawScope, side: DateSide, measurer: TextMeasurer, style: DayCircleStyle) : DateFace {
    private val r = scope.size.minDimension / 2f
    private val c = scope.size.center
    private val dp = scope.density
    private val table = r * 0.62f

    private val tablePath = Path()
    private val litFacets = Path()
    private val shadedFacets = Path()
    private val edges = Path()

    init {
        // Flat-topped table, so the lettering sits square on it.
        val corners = List(FACETS) { k -> pointOn(c, table, k * TAU / FACETS - TAU / (2 * FACETS)) }
        tablePath.moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { tablePath.lineTo(it.x, it.y) }
        tablePath.close()
        for (k in 0 until FACETS) {
            val from = corners[k]
            val to = corners[(k + 1) % FACETS]
            val tip = pointOn(c, r * 0.97f, k * TAU / FACETS)
            (if (k % 2 == 1) litFacets else shadedFacets).apply {
                moveTo(from.x, from.y)
                lineTo(tip.x, tip.y)
                lineTo(to.x, to.y)
                close()
            }
            edges.moveTo(from.x, from.y)
            edges.lineTo(tip.x, tip.y)
            edges.lineTo(to.x, to.y)
        }
    }

    // The lettering keeps a hint of each style: lume sans on steel, serif on brass and gold.
    private val family = if (style == DayCircleStyle.STEEL) IBMPlexArabic else FontFamily.Serif
    private val monthText = with(scope) {
        fitted(
            measurer,
            side.month,
            TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.12.em,
                color = Color.White.copy(alpha = 0.8f),
                shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = r * 0.05f)
            ),
            r * 0.24f,
            table * 1.3f
        )
    }
    private val dayText = with(scope) {
        fitted(
            measurer,
            side.day,
            TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, r * 0.02f), r * 0.06f)
            ),
            r * 0.6f,
            table * 1.5f
        )
    }

    override fun draw(scope: DrawScope, accent: GearPrayer, light: GearLight) = with(scope) {
        val tint = accent.color
        drawCircle(GemSmoke, r, c)
        // Light enters on the lit side and gathers on the far one.
        drawCircle(
            Brush.radialGradient(
                0f to lerp(tint, Color.White, 0.35f).copy(alpha = 0.6f),
                0.55f to tint.copy(alpha = 0.28f),
                1f to tint.copy(alpha = 0.08f),
                center = c - light.towards * (r * 0.35f),
                radius = r * 1.35f
            ),
            r, c
        )
        // The girdle falls into shade.
        drawCircle(Brush.radialGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f), center = c, radius = r), r, c)
        drawPath(litFacets, Color.White.copy(alpha = 0.07f))
        drawPath(shadedFacets, Color.Black.copy(alpha = 0.1f))
        drawPath(tablePath, Color.White.copy(alpha = 0.05f))
        drawPath(edges, Color.White.copy(alpha = 0.2f), style = Stroke(0.6f * dp))

        drawCentred(monthText, c + Offset(0f, -table * 0.52f))
        drawCentred(dayText, c + Offset(0f, table * 0.12f))

        // Highlight on the side facing the light, over the lettering as on real glass.
        val glint = c + light.towards * (r * 0.58f)
        withTransform({
            translate(glint.x, glint.y)
            rotate(Math.toDegrees(light.angle.toDouble()).toFloat() + 90f, Offset.Zero)
            scale(1f, 0.45f, Offset.Zero)
        }) {
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), center = Offset.Zero, radius = r * 0.34f),
                r * 0.34f,
                Offset.Zero
            )
        }
        // Rim: bright where it faces the light, like the liquid glass cards.
        drawCircle(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.85f),
                0.5f to Color.White.copy(alpha = 0.12f),
                1f to Color.White.copy(alpha = 0.4f),
                start = c + light.towards * r,
                end = c - light.towards * r
            ),
            r - 0.6f * dp, c,
            style = Stroke(1.2f * dp)
        )
    }

    private companion object {
        const val FACETS = 8

        /** Smoked glass, so the white lettering reads over any sky behind the gem. */
        val GemSmoke = Color(0x8C0B1020)
    }
}

// ---------------------------------------------------------------------------
// Steel: the gem's polished bezel
// ---------------------------------------------------------------------------

/** The sub-dial's fixed bezel: polished steel with four screws. [edge] is its outline. */
private fun DrawScope.steelBezel(palette: GearPalette, light: GearLight, edge: Path) {
    val d = size.minDimension
    val r = d / 2f
    val ri = r * 0.84f
    val dp = density
    translate(-light.towards.x * 2f * dp, -light.towards.y * 2f * dp) { drawCircle(Color.Black.copy(alpha = 0.3f), r + dp, center) }
    drawCircle(palette.steelSheen.brush(center, light), r, center)
    ringFinish(center, r, ri, light, null, dp)
    bevel(edge, center, r, light, dp)
    holeBevel(center, ri, light, dp)
    drawCircle(Color(0xB30A0E16), r, center, style = Stroke(dp))
    for (k in 0 until 4) {
        screw(
            pointOn(center, (r + ri) / 2f, TAU / 8 + k * TAU / 4), d * 0.022f, light,
            palette.tone(Color(0xFFF4F7FB)), palette.tone(Color(0xFF8E98AA)), palette.tone(Color(0xFF2E3544)),
            slot = 0.6f + k * 0.4f
        )
    }
}

// ---------------------------------------------------------------------------
// Skeleton: the gem's gold bezel
// ---------------------------------------------------------------------------

/** The crystal's fixed bezel: gold with a ring of milgrain beads, like the prayer stones. [edge] is its outline. */
private fun DrawScope.skeletonBezel(palette: GearPalette, light: GearLight, edge: Path) {
    val r = size.minDimension / 2f
    val ri = r * 0.86f
    val dp = density
    translate(-light.towards.x * 2f * dp, -light.towards.y * 2f * dp) { drawCircle(Color.Black.copy(alpha = 0.3f), r + dp, center) }
    drawCircle(palette.brassSheen.brush(center, light), r, center)
    bevel(edge, center, r, light, dp)
    holeBevel(center, ri, light, dp)
    drawCircle(Color(0xBF3C280A), r, center, style = Stroke(0.8f * dp))
    val bead = palette.tone(Color(0xFFE9CF8A))
    for (k in 0 until 28) {
        val at = pointOn(center, (r + ri) / 2f, k * TAU / 28)
        drawCircle(bead, (r - ri) * 0.32f, at)
        drawCircle(Color.White.copy(alpha = 0.6f), (r - ri) * 0.12f, at + light.towards * (0.6f * dp))
    }
}

// ---------------------------------------------------------------------------
// Shared text helpers
// ---------------------------------------------------------------------------

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
