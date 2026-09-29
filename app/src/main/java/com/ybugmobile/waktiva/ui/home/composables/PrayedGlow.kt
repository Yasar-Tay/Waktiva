package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The glow a prayer's badge on the day circle wears once the prayer is marked as prayed: a warm
 * gold bloom around the badge and a bright double rim. Marking it sends a ring rippling out while
 * the glow swells in with a small overshoot; taking the mark back lets it fade. The glow only draws around the badge, which shows through its middle.
 *
 * [prayed] is null until the log has loaded, so a prayer already marked lights up without the
 * ripple. [badgeRadius] is the badge's radius in pixels; the glow centres on this composable,
 * which sizes itself to hold it.
 */
@Composable
fun PrayedGlow(
    prayed: Boolean?,
    color: Color,
    badgeRadius: Float,
    modifier: Modifier = Modifier
) {
    val glow = remember { Animatable(if (prayed == true) 1f else 0f) }
    val ripple = remember { Animatable(1f) }
    // What the glow shows now, so only a change plays: coming back to the screen doesn't.
    var shown by remember { mutableStateOf(prayed) }

    LaunchedEffect(prayed) {
        if (prayed == null || prayed == shown) return@LaunchedEffect
        val wasLoaded = shown != null
        shown = prayed
        if (!wasLoaded) {
            glow.snapTo(if (prayed) 1f else 0f)
        } else if (prayed) {
            launch {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            }
            glow.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow))
        } else {
            glow.animateTo(0f, tween(450))
        }
    }

    val size = with(LocalDensity.current) { (badgeRadius * 2 * GLOW_REACH).toDp() }
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val g = glow.value
                val t = ripple.value
                val tone = lerp(color, PrayedGold, 0.6f)
                if (t < 1f) drawRipple(badgeRadius, t, tone)
                if (g > 0.01f) drawGlow(badgeRadius, g, tone)
            }
    )
}

/** How far the glow reaches, in badge radii, including the ripple at its widest. */
private const val GLOW_REACH = 3.2f

/** The ring that ripples out once when the prayer is marked, [t] from 0 to 1. */
private fun DrawScope.drawRipple(r: Float, t: Float, tone: Color) {
    val fade = 1f - t
    drawCircle(
        color = tone.copy(alpha = 0.9f * fade),
        radius = r * (1.05f + 1.9f * t),
        center = center,
        style = Stroke((0.6f + 2.8f * fade).dp.toPx())
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.5f * fade * fade),
        radius = r * (1.05f + 1.3f * t),
        center = center,
        style = Stroke((0.5f + 1.2f * fade).dp.toPx())
    )
}

/** The steady glow, [g] from 0 (none) to 1 (full), briefly above 1 as it swells in. */
private fun DrawScope.drawGlow(r: Float, g: Float, tone: Color) {
    val alpha = g.coerceIn(0f, 1f)
    val outer = r * 2.5f * (0.6f + 0.4f * g)

    // The bloom starts at the badge's edge and leaves the badge itself clear.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            (r * 0.92f / outer) to Color.Transparent,
            (r * 1.04f / outer) to tone.copy(alpha = 0.9f * alpha),
            (r * 1.5f / outer) to tone.copy(alpha = 0.32f * alpha),
            1f to Color.Transparent,
            center = center,
            radius = outer
        ),
        radius = outer,
        center = center
    )
    drawCircle(Color.White.copy(alpha = 0.95f * alpha), r + 1.dp.toPx(), center, style = Stroke(1.6.dp.toPx()))
    drawCircle(tone.copy(alpha = alpha), r + 3.2.dp.toPx(), center, style = Stroke(1.2.dp.toPx()))
}
