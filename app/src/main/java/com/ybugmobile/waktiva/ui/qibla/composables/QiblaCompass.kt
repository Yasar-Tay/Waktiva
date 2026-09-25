package com.ybugmobile.waktiva.ui.qibla.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.ui.home.composables.gear.GearLight
import com.ybugmobile.waktiva.ui.home.composables.gear.GearPalette
import com.ybugmobile.waktiva.ui.home.composables.gear.GemCut
import com.ybugmobile.waktiva.ui.home.composables.gear.MetalSheen
import com.ybugmobile.waktiva.ui.home.composables.gear.RingGrain
import com.ybugmobile.waktiva.ui.home.composables.gear.TAU
import com.ybugmobile.waktiva.ui.home.composables.gear.WeatherTone
import com.ybugmobile.waktiva.ui.home.composables.gear.addGearOutline
import com.ybugmobile.waktiva.ui.home.composables.gear.annulus
import com.ybugmobile.waktiva.ui.home.composables.gear.bevel
import com.ybugmobile.waktiva.ui.home.composables.gear.elevation
import com.ybugmobile.waktiva.ui.home.composables.gear.gemStone
import com.ybugmobile.waktiva.ui.home.composables.gear.haloRing
import com.ybugmobile.waktiva.ui.home.composables.gear.holeBevel
import com.ybugmobile.waktiva.ui.home.composables.gear.pointOn
import com.ybugmobile.waktiva.ui.home.composables.gear.ringFinish
import com.ybugmobile.waktiva.ui.home.composables.gear.screw
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * The Qibla compass in the style of the chosen day circle: the original compass for
 * [DayCircleStyle.CLASSIC], and a compass built like the gear dials for the others.
 */
@Composable
fun QiblaCompass(
    style: DayCircleStyle,
    azimuth: Float,
    qiblaAngle: Float,
    alignmentColor: Color,
    isAligned: Boolean,
    contentColor: Color = Color.White
) {
    if (style == DayCircleStyle.CLASSIC) {
        ProfessionalCompass(azimuth, qiblaAngle, alignmentColor, isAligned, contentColor)
    } else {
        GearCompass(style, azimuth, qiblaAngle, isAligned)
    }
}

/**
 * A compass made like the gear dials, in brass, night steel or skeleton gold:
 * - a fixed metal bezel with a halo of light round it, and a lubber mark at the top where the
 *   phone points;
 * - a card that turns with the heading: ivory enamel with a guilloché for brass, a midnight
 *   face with azurage rings and lume for steel, smoked crystal with the movement showing
 *   through for the skeleton; with degree marks, an eight-point rose and upright letters;
 * - the Kaaba as a medallion set on the card at the Qibla bearing, glowing when aligned;
 * - a fixed needle: a brass spade, a lumed steel blade, or a gold outline capped with a ruby.
 *
 * Drawn in four layers so that turning is cheap: the bezel and needle don't change with the
 * heading, the card turns as a whole in its graphics layer without being redrawn, and only the
 * upright letters and the medallion are redrawn as it turns. The heading and the alignment glow
 * are read while drawing, so neither recomposes.
 */
@Composable
internal fun GearCompass(
    style: DayCircleStyle,
    azimuth: Float,
    qiblaAngle: Float,
    isAligned: Boolean,
    modifier: Modifier = Modifier
) {
    val weather = LocalGlassTheme.current.weatherCondition
    val palette = remember(weather) { GearPalette(WeatherTone.forScenery(weather)) }
    val look = remember(style, palette) { CompassLook(style, palette) }
    val measurer = rememberTextMeasurer()
    val heading = rememberHeading(azimuth)

    // 0..1 as the compass comes into line with the Qibla, and a clock for the medallion's pulse
    // that only runs while aligned.
    val glow = remember { Animatable(0f) }
    val pulseTime = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isAligned) {
        if (isAligned) {
            launch { glow.animateTo(1f, tween(800)) }
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { pulseTime.floatValue = (it - start) / 1e9f }
            }
        } else {
            glow.animateTo(0f, tween(800))
        }
    }

    Box(modifier.size(CompassSize)) {
        // Bezel and halo. Each part has its own layer, so the parts redrawn as the compass
        // turns don't make the others redraw with them.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val g = CompassGeometry(size, density)
                    val light = GearLight.Default
                    val bezel = annulus(g.c, g.r, g.rb)
                    val bezelEdge = Path().apply { addOval(Rect(g.c, g.r)) }
                    val grain = RingGrain(g.c, g.r, g.rb, 1.2f * g.dp, seed = 9)
                    val notches = if (style == DayCircleStyle.STEEL) SteelNotches(g) else null
                    onDrawBehind {
                        val a = glow.value
                        haloRing(g.c, (g.r + g.rb) / 2f, g.r * 0.22f, lerp(look.halo, AlignedGold, 0.5f * a), 0.16f + 0.14f * a)
                        elevation(bezel, 3f * g.dp, light)
                        drawPath(bezel, look.metal.brush(g.c, light))
                        ringFinish(g.c, g.r, g.rb, light, grain, g.dp, round = style != DayCircleStyle.STEEL)
                        bevel(bezelEdge, g.c, g.r, light, 1.2f * g.dp)
                        holeBevel(g.c, g.rb, light, 1.2f * g.dp)
                        drawBezelDetail(g, look, light, notches)
                    }
                }
        )

        // The card, turned by the heading
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = -heading.value }
                .drawWithCache {
                    val card = CompassCard(CompassGeometry(size, density), look)
                    onDrawBehind { card.draw(this) }
                }
        )

        // Upright letters and the Kaaba medallion, redrawn as the card turns
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val g = CompassGeometry(size, density)
                    val letters = look.letters.map { (text, bearing) ->
                        val cardinal = text.length == 1
                        val layout = measurer.measure(text, look.letterStyle(text, cardinal, g.rc, this))
                        Letter(layout, bearing, if (cardinal) g.rc * 0.7f else g.rc * 0.72f, cardinal)
                    }
                    val medallion = Path().apply { addOval(Rect(Offset.Zero, g.rc * MedallionShare)) }
                    val ruby = GemCut(g.rc * 0.045f)
                    onDrawBehind {
                        val h = heading.value
                        letters.forEach { letter ->
                            // A letter next to the medallion makes way for it.
                            if (abs(angleBetween(letter.bearing, qiblaAngle)) < if (letter.cardinal) 9f else 16f) return@forEach
                            val at = pointOn(g.c, letter.radius, screenAngle(letter.bearing, h))
                            drawText(letter.layout, topLeft = at - Offset(letter.layout.size.width / 2f, letter.layout.size.height / 2f))
                        }
                        if (style == DayCircleStyle.SKELETON) {
                            gemStone(RubyColor, pointOn(g.c, g.rc * 0.88f, screenAngle(0f, h)), ruby, palette.brassSheen, GearLight.Default, palette.gold, g.dp)
                        }
                        kaabaMedallion(
                            pointOn(g.c, g.rc * 0.86f, screenAngle(qiblaAngle, h)), g.rc * MedallionShare, medallion,
                            look.metal, glow.value, pulseTime.floatValue, g.dp
                        )
                    }
                }
        )

        // Rim of the card, lubber mark and the fixed needle
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val g = CompassGeometry(size, density)
                    val needle = CompassNeedle(g, look)
                    val lubber = Path().apply {
                        moveTo(g.c.x, g.c.y - g.rb + g.dp)
                        lineTo(g.c.x - g.r * 0.035f, g.c.y - g.r - 3f * g.dp)
                        lineTo(g.c.x + g.r * 0.035f, g.c.y - g.r - 3f * g.dp)
                        close()
                    }
                    onDrawBehind {
                        drawCircle(Color.Black.copy(alpha = 0.5f), g.rc, g.c, style = Stroke(g.dp))
                        drawPath(lubber, lerp(look.lubber, AlignedGold, glow.value))
                        needle.draw(this)
                    }
                }
        )
    }
}

/**
 * The heading in degrees, smoothed with a spring and unwrapped so that crossing north turns the
 * card the short way instead of spinning it round. Read [Animatable.value] while drawing.
 */
@Composable
private fun rememberHeading(azimuth: Float): Animatable<Float, AnimationVector1D> {
    val heading = remember { Animatable(azimuth) }
    LaunchedEffect(azimuth) {
        val target = heading.targetValue
        heading.animateTo(
            target + angleBetween(azimuth, target),
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }
    return heading
}

/** Signed difference from [b] to [a] in degrees, in -180..180. */
private fun angleBetween(a: Float, b: Float): Float = ((a - b) % 360f + 540f) % 360f - 180f

/** Screen angle in radians of a compass [bearing] on a card turned to [heading] (both degrees). */
private fun screenAngle(bearing: Float, heading: Float): Float = ((bearing - heading - 90f) * PI / 180f).toFloat()

private val CompassSize = 340.dp
private val AlignedGold = Color(0xFFFFC861)
private val RubyColor = Color(0xFFE0115F)

/** Radii of the compass, for a canvas of [size]. */
private class CompassGeometry(size: Size, val dp: Float) {
    val c = size.center
    /** Outer and inner edge of the bezel, and the card inside it. */
    val r = size.minDimension / 2f - 20f * dp
    val rb = r * 0.87f
    val rc = rb - 1.5f * dp
}

/** Colours, metal and lettering of one style. */
private class CompassLook(val style: DayCircleStyle, val palette: GearPalette) {
    val metal: MetalSheen = if (style == DayCircleStyle.STEEL) palette.steelSheen else palette.brassSheen
    val halo = if (style == DayCircleStyle.STEEL) palette.coolHalo else palette.warmHalo

    /** Ink of the card's marks and letters. */
    val ink = when (style) {
        DayCircleStyle.STEEL -> Color(0xFFE2F6E4)
        DayCircleStyle.SKELETON -> palette.gold
        else -> palette.tone(Color(0xFF5A3E12))
    }
    val north = if (style == DayCircleStyle.STEEL) Color(0xFFFF7676) else Color(0xFFC0392B)
    val lubber = when (style) {
        DayCircleStyle.STEEL -> Color(0xFFE9F7E6)
        else -> palette.tone(Color(0xFFFFF1C4))
    }

    val letters = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f, "NE" to 45f, "SE" to 135f, "SW" to 225f, "NW" to 315f)

    /** Serif capitals engraved on brass and gold, glowing lume sans on steel. */
    fun letterStyle(text: String, cardinal: Boolean, rc: Float, density: Density): TextStyle {
        val steel = style == DayCircleStyle.STEEL
        val px = if (cardinal) rc * 0.14f else rc * 0.07f
        return TextStyle(
            color = if (text == "N") north else ink.copy(alpha = if (cardinal) 0.95f else 0.6f),
            fontFamily = if (steel) FontFamily.SansSerif else FontFamily.Serif,
            fontWeight = if (cardinal) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = with(density) { px.toSp() },
            shadow = if (steel) Shadow(Color(0x99AAFFC8), blurRadius = rc * 0.05f) else null
        )
    }
}

private class Letter(val layout: TextLayoutResult, val bearing: Float, val radius: Float, val cardinal: Boolean)

/** Engraved notches every 5° round the steel bezel, bolder every 30°. */
private class SteelNotches(g: CompassGeometry) {
    val minor = Path()
    val major = Path()

    init {
        for (d in 0 until 360 step 5) {
            val a = d * TAU / 360f
            val isMajor = d % 30 == 0
            val from = pointOn(g.c, g.r - 2f * g.dp, a)
            val to = pointOn(g.c, g.r - (if (isMajor) 7f else 4f) * g.dp, a)
            (if (isMajor) major else minor).apply {
                moveTo(from.x, from.y)
                lineTo(to.x, to.y)
            }
        }
    }
}

/** Screws on the brass bezel, notches on the steel one, milgrain beads on the skeleton's. */
private fun DrawScope.drawBezelDetail(g: CompassGeometry, look: CompassLook, light: GearLight, notches: SteelNotches?) {
    val palette = look.palette
    val mid = (g.r + g.rb) / 2f
    when (look.style) {
        DayCircleStyle.STEEL -> notches?.let {
            drawPath(it.minor, Color(0x66141A28), style = Stroke(0.6f * g.dp))
            drawPath(it.major, Color(0xCC141A28), style = Stroke(1.1f * g.dp))
        }
        DayCircleStyle.SKELETON -> {
            val bead = palette.tone(Color(0xFFE9CF8A))
            val radius = (g.r - g.rb) * 0.16f
            for (k in 0 until 44) {
                val at = pointOn(g.c, mid, k * TAU / 44)
                drawCircle(bead, radius, at)
                drawCircle(Color.White.copy(alpha = 0.6f), radius * 0.38f, at + light.towards * (radius * 0.35f))
            }
        }
        else -> for (k in 0 until 4) {
            screw(
                pointOn(g.c, mid, TAU / 8 + k * TAU / 4), g.r * 0.024f, light,
                palette.tone(Color(0xFFFFF1C4)), palette.tone(Color(0xFFB58D47)), palette.tone(Color(0xFF4D3610)),
                slot = k.toFloat()
            )
        }
    }
    drawCircle(Color(0x803C280A), g.r, g.c, style = Stroke(0.8f * g.dp))
}

/**
 * The turning card, laid out with north up. Drawn in a layer the heading rotates, so it is
 * recorded once per size and never redrawn while the compass turns.
 */
private class CompassCard(private val g: CompassGeometry, private val look: CompassLook) {
    private val rc = g.rc
    private val c = g.c
    private val outline = Path().apply { addOval(Rect(c, rc)) }

    /** Fine lines across the face: guilloché rays on brass, azurage rings on steel, the movement behind the skeleton's crystal. */
    private val texture = Path()
    private val face: Brush

    private val minorTicks = Path()
    private val midTicks = Path()
    private val majorTicks = Path()
    private val roseLit = Path()
    private val roseShade = Path()

    init {
        val palette = look.palette
        when (look.style) {
            DayCircleStyle.STEEL -> {
                face = Brush.radialGradient(
                    listOf(Color(0xFF24324F), Color(0xFF0D1322)),
                    center = c + Offset(0f, -rc * 0.3f),
                    radius = rc * 1.3f
                )
                var rr = 2f * g.dp
                while (rr < rc) {
                    texture.addOval(Rect(c, rr))
                    rr += 1.5f * g.dp
                }
            }
            DayCircleStyle.SKELETON -> {
                face = Brush.radialGradient(listOf(Color(0x73121622), Color(0xC7080A12)), center = c, radius = rc)
                for ((x, y, teeth, turn) in SkeletonGears) {
                    val at = c + Offset(x * rc, y * rc)
                    val pitch = teeth * rc * 0.0163f
                    addGearOutline(texture, at, pitch, teeth.toInt(), turn)
                    texture.addOval(Rect(at, pitch * 0.75f))
                }
            }
            else -> {
                face = Brush.radialGradient(
                    0f to palette.tone(Color(0xFFFBF5E4)),
                    0.7f to palette.tone(Color(0xFFEFE3C4)),
                    1f to palette.tone(Color(0xFFD9C79C)),
                    center = c + Offset(-rc * 0.25f, -rc * 0.3f),
                    radius = rc * 1.25f
                )
                for (k in 0 until 120) {
                    val a = k * TAU / 120
                    val from = pointOn(c, rc * 0.1f, a)
                    val to = pointOn(c, rc, a)
                    texture.moveTo(from.x, from.y)
                    texture.lineTo(to.x, to.y)
                }
            }
        }

        for (d in 0 until 360 step 2) {
            val a = (d - 90) * TAU / 360f
            val (path, length) = when {
                d % 30 == 0 -> majorTicks to rc * 0.1f
                d % 10 == 0 -> midTicks to rc * 0.065f
                else -> minorTicks to rc * 0.035f
            }
            val from = pointOn(c, rc * 0.97f, a)
            val to = pointOn(c, rc * 0.97f - length, a)
            path.moveTo(from.x, from.y)
            path.lineTo(to.x, to.y)
        }

        // Eight slim points, each lit on one flank.
        for (k in 0 until 8) {
            val a = k * TAU / 8 - TAU / 4
            val length = if (k % 2 == 1) rc * 0.36f else rc * 0.58f
            val width = if (k % 2 == 1) rc * 0.035f else rc * 0.05f
            val tip = pointOn(c, length, a)
            val left = pointOn(c, width, a - TAU / 4)
            val right = pointOn(c, width, a + TAU / 4)
            roseLit.apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(left.x, left.y); close() }
            roseShade.apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(right.x, right.y); close() }
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        val ink = look.ink
        val dp = g.dp
        drawPath(outline, face)
        val textureInk = when (look.style) {
            DayCircleStyle.STEEL -> Color.White.copy(alpha = 0.045f)
            DayCircleStyle.SKELETON -> look.palette.gold.copy(alpha = 0.1f)
            else -> Color(0x0F785A28)
        }
        clipPath(outline) { drawPath(texture, textureInk, style = Stroke(if (look.style == DayCircleStyle.SKELETON) 0.8f * dp else 0.5f * dp)) }

        drawPath(minorTicks, ink.copy(alpha = 0.3f), style = Stroke(0.6f * dp))
        drawPath(midTicks, ink.copy(alpha = 0.6f), style = Stroke(0.6f * dp))
        drawPath(majorTicks, ink.copy(alpha = 0.9f), style = Stroke(1.1f * dp, cap = StrokeCap.Round))
        drawCircle(ink.copy(alpha = 0.35f), rc * 0.84f, c, style = Stroke(0.6f * dp))
        drawPath(roseLit, ink.copy(alpha = 0.28f))
        drawPath(roseShade, ink.copy(alpha = 0.12f))
    }

    private companion object {
        /** Wheels of the movement behind the skeleton's crystal: offset from the centre (in card radii), teeth, turn. */
        val SkeletonGears = listOf(
            listOf(-0.48f, -0.43f, 30f, 0.2f),
            listOf(0.43f, 0.34f, 22f, -0.3f),
            listOf(0.29f, -0.48f, 14f, 0.5f)
        )
    }
}

/** Radius of the Kaaba medallion, as a share of the card's radius. */
private const val MedallionShare = 0.1f

/**
 * The Kaaba on a medallion of radius [r] at [at], upright: a metal ring round deep emerald
 * enamel, in a warm glow that always marks the Qibla and swells, pulsing, by [glow] as the
 * compass comes into line.
 */
private fun DrawScope.kaabaMedallion(at: Offset, r: Float, outline: Path, metal: MetalSheen, glow: Float, time: Float, dp: Float) {
    val light = GearLight.Default
    translate(at.x, at.y) {
        val reach = r * (2.2f + 1.2f * glow)
        val warmth = 0.28f + (0.3f + 0.12f * sin(time * 3f)) * glow
        drawCircle(
            Brush.radialGradient(listOf(KaabaGold.copy(alpha = warmth), KaabaGold.copy(alpha = 0f)), center = Offset.Zero, radius = reach),
            reach, Offset.Zero
        )
        elevation(outline, 1.5f * dp, light, pivot = Offset.Zero)
        drawPath(outline, metal.brush(Offset.Zero, light))
        bevel(outline, Offset.Zero, r, light, dp)
        drawCircle(Color(0x993C280A), r, Offset.Zero, style = Stroke(0.6f * dp))
        val enamel = r * 0.8f
        drawCircle(
            Brush.radialGradient(KaabaEnamel, center = light.towards * (enamel * 0.35f), radius = enamel * 1.3f),
            enamel, Offset.Zero
        )
        drawCircle(Color.Black.copy(alpha = 0.45f), enamel, Offset.Zero, style = Stroke(0.8f * dp))
        kaabaIcon(Offset(0f, -enamel * 0.04f), enamel * 1.2f)
        // A glint on the enamel, as on glazed glass.
        drawCircle(Color.White.copy(alpha = 0.18f), enamel * 0.22f, light.towards * (enamel * 0.55f))
    }
}

/** The fixed needle, pointing up to where the phone points. */
private class CompassNeedle(private val g: CompassGeometry, private val look: CompassLook) {
    private val c = g.c
    private val rc = g.rc
    private val length = rc * 0.78f
    private val tail = rc * 0.22f
    private val light = GearLight.Default

    private val body = Path().apply {
        when (look.style) {
            DayCircleStyle.STEEL -> {
                moveTo(c.x, c.y - length)
                lineTo(c.x + rc * 0.03f, c.y)
                lineTo(c.x, c.y + tail)
                lineTo(c.x - rc * 0.03f, c.y)
            }
            DayCircleStyle.SKELETON -> {
                moveTo(c.x, c.y - length)
                lineTo(c.x + rc * 0.04f, c.y - length * 0.7f)
                lineTo(c.x + rc * 0.012f, c.y + tail)
                lineTo(c.x - rc * 0.012f, c.y + tail)
                lineTo(c.x - rc * 0.04f, c.y - length * 0.7f)
            }
            else -> {
                moveTo(c.x, c.y - length)
                lineTo(c.x + rc * 0.045f, c.y - length * 0.72f)
                lineTo(c.x + rc * 0.022f, c.y)
                lineTo(c.x + rc * 0.02f, c.y + tail)
                lineTo(c.x - rc * 0.02f, c.y + tail)
                lineTo(c.x - rc * 0.022f, c.y)
                lineTo(c.x - rc * 0.045f, c.y - length * 0.72f)
            }
        }
        close()
    }

    /** The lit flank of the brass spade. */
    private val flank = Path().apply {
        moveTo(c.x, c.y - length)
        lineTo(c.x - rc * 0.045f, c.y - length * 0.72f)
        lineTo(c.x - rc * 0.022f, c.y)
        lineTo(c.x, c.y)
        close()
    }
    private val tailRing = c + Offset(0f, tail)
    private val cap = GemCut(rc * 0.075f)

    fun draw(scope: DrawScope) = with(scope) {
        val palette = look.palette
        val dp = g.dp
        when (look.style) {
            DayCircleStyle.STEEL -> {
                elevation(body, 2f * dp, light)
                drawPath(body, palette.steelSheen.brush(c, light))
                val from = Offset(c.x, c.y - length * 0.92f)
                val to = Offset(c.x, c.y - rc * 0.12f)
                drawLine(Color(0x59AAFFC8), from, to, rc * 0.04f, StrokeCap.Round)
                drawLine(Color(0xFFE9F7E6), from, to, rc * 0.012f, StrokeCap.Round)
                screw(
                    c, rc * 0.055f, light,
                    palette.tone(Color(0xFFF4F7FB)), palette.tone(Color(0xFF8E98AA)), palette.tone(Color(0xFF2E3544)),
                    slot = 1.2f
                )
            }
            DayCircleStyle.SKELETON -> {
                val gold = palette.gold.copy(alpha = 0.95f)
                drawPath(body, Color.Black.copy(alpha = 0.25f))
                drawPath(body, gold, style = Stroke(1.4f * dp, join = StrokeJoin.Round))
                drawLine(gold, Offset(c.x, c.y - length * 0.95f), Offset(c.x, c.y - rc * 0.1f), 1.4f * dp)
                gemStone(RubyColor, c, cap, palette.brassSheen, light, palette.gold, dp)
            }
            else -> {
                elevation(body, 2f * dp, light)
                drawPath(body, palette.brassSheen.brush(c, light))
                drawPath(flank, Color(0x80FFF6D6))
                drawPath(body, Color(0x803C280A), style = Stroke(0.6f * dp, join = StrokeJoin.Round))
                drawCircle(palette.brassSheen.brush(tailRing, light), rc * 0.05f, tailRing, style = Stroke(rc * 0.02f))
                screw(
                    c, rc * 0.06f, light,
                    palette.tone(Color(0xFFFFF1C4)), palette.tone(Color(0xFFB58D47)), palette.tone(Color(0xFF4D3610)),
                    slot = 0.6f
                )
            }
        }
    }
}
