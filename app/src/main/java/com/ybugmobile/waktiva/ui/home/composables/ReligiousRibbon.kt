package com.ybugmobile.waktiva.ui.home.composables

import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.provider.ReligiousDaysProvider
import com.ybugmobile.waktiva.ui.home.composables.gear.GearPalette
import com.ybugmobile.waktiva.ui.home.composables.gear.pointOn
import java.time.LocalDate
import kotlin.math.PI
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath

/**
 * The classic circle's special-day name: a thin glass ribbon hugging the lower arc of the date's
 * gold bezel, from [innerRadius] out, gold-edged, with the name running along it. Eid is tinted
 * gold and Ramadan green, as on the calendar strip. Nothing is drawn on an ordinary day; on a
 * religious day the ribbon fades in.
 *
 * The name is drawn with the platform's text-on-path, so Arabic, Persian and Urdu names keep their
 * joined letterforms.
 */
@Composable
internal fun ReligiousRibbon(
    date: LocalDate,
    innerRadius: Float,
    palette: GearPalette,
    modifier: Modifier = Modifier
) {
    val day = remember(date) { ReligiousDaysProvider.getReligiousDay(date) } ?: return
    val locale = LocalConfiguration.current.locales[0]
    val name = stringResource(day.nameResId).uppercase(locale)
    val accent = ribbonAccent(day.nameResId)?.let { palette.tone(it) }
    val gold = palette.gold

    val appear = remember(date) { Animatable(0f) }
    LaunchedEffect(date) { appear.animateTo(1f, tween(600)) }

    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .drawWithCache {
                val c = size.center
                val thickness = 15.dp.toPx()
                val mid = innerRadius + thickness / 2f
                val outer = innerRadius + thickness

                val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = Color.White.copy(alpha = 0.95f).toArgb()
                    setShadowLayer(2.dp.toPx(), 0f, 0.5.dp.toPx(), Color.Black.copy(alpha = 0.6f).toArgb())
                }
                // Shrink the lettering until the ribbon fits the lower part of the bezel.
                val endPadding = thickness * 0.9f
                var textSize = 9.sp.toPx()
                var spacing = 0.2f // em
                for (attempt in 0 until 10) {
                    paint.textSize = textSize
                    paint.letterSpacing = spacing
                    if ((paint.measureText(name) + 2 * endPadding) / mid <= MaxSpan || textSize <= 7.sp.toPx()) break
                    textSize *= 0.93f
                    spacing *= 0.9f
                }
                val textSpan = paint.measureText(name) / mid
                val span = (textSpan + 2 * endPadding / mid).coerceAtMost(MaxSpan)
                val start = Bottom - span / 2f
                val end = Bottom + span / 2f

                val body = ribbonOutline(c, innerRadius, outer, start, end)
                val lowerEdge = Path().apply {
                    arcTo(Rect(c, outer - 1.2.dp.toPx()), (start + 0.08f).toDegrees(), (span - 0.16f).toDegrees(), true)
                }
                // Left to right along the bottom of the bezel, so the name stands upright.
                val textPath = NativePath().apply {
                    addArc(RectF(c.x - mid, c.y - mid, c.x + mid, c.y + mid), (Bottom + textSpan / 2f).toDegrees(), -textSpan.toDegrees())
                }
                val centreOffset = -(paint.ascent() + paint.descent()) / 2f
                val glass = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f)),
                    startY = c.y + innerRadius,
                    endY = c.y + outer
                )

                onDrawBehind {
                    // Smoke under the glass, so the name reads over any sky
                    drawPath(body, Color.Black.copy(alpha = 0.34f))
                    accent?.let { drawPath(body, it.copy(alpha = 0.22f)) }
                    drawPath(body, glass)
                    // A light line along the lower edge, as the glass catches the light
                    drawPath(lowerEdge, Color.White.copy(alpha = 0.22f), style = Stroke(0.6.dp.toPx()))
                    drawPath(body, gold.copy(alpha = 0.75f), style = Stroke(0.8.dp.toPx()))
                    drawIntoCanvas { it.nativeCanvas.drawTextOnPath(name, textPath, 0f, centreOffset, paint) }
                }
            }
    )
}

/** An arc band from [start] to [end] (radians) between [inner] and [outer], with rounded ends. */
private fun ribbonOutline(c: Offset, inner: Float, outer: Float, start: Float, end: Float) = Path().apply {
    val capCentre = (inner + outer) / 2f
    val cap = (outer - inner) / 2f
    arcTo(Rect(c, outer), start.toDegrees(), (end - start).toDegrees(), true)
    arcTo(Rect(pointOn(c, capCentre, end), cap), end.toDegrees(), 180f, false)
    arcTo(Rect(c, inner), end.toDegrees(), -(end - start).toDegrees(), false)
    arcTo(Rect(pointOn(c, capCentre, start), cap), start.toDegrees() + 180f, 180f, false)
    close()
}

/** The calendar strip's Eid gold and Ramadan green; null for the other days, whose glass is clear. */
private fun ribbonAccent(nameResId: Int): Color? = when (nameResId) {
    R.string.rel_day_ramadan_eid, R.string.rel_day_sacrifice_eid, R.string.rel_day_eid_eve -> Color(0xFFFBBF24)
    R.string.rel_day_first_tarawih, R.string.rel_day_ramadan_start, R.string.rel_day_kadir -> Color(0xFF4ADE80)
    else -> null
}

private fun Float.toDegrees() = this * 180f / PI.toFloat()

/** The bottom of the dial, where the ribbon is centred. */
private const val Bottom = (PI / 2).toFloat()

/** The widest the ribbon may reach round the bezel, so it stays below the date. */
private val MaxSpan = (PI * 0.85).toFloat()
