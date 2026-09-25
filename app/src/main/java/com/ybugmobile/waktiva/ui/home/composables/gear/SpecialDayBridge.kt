package com.ybugmobile.waktiva.ui.home.composables.gear

import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath

/**
 * The special-day name as a movement bridge: a curved plate below the hub, concentric with
 * the dial, with the name engraved along its arc, a jewel at each end and Geneva stripes.
 *
 * Built once per dial size and text. The text is drawn with the platform's text-on-path
 * so Arabic, Persian and Urdu names keep their joined letterforms.
 */
internal class SpecialDayBridge(
    private val text: String,
    spec: BridgeSpec,
    private val center: Offset,
    private val palette: GearPalette,
    private val finish: PlateFinish,
    private val dp: Float,
    sizePx: Float
) {
    private val inner = spec.innerRadius
    private val thickness = min(sizePx * 0.06f, spec.freeRadius - 2 * dp - inner)

    /** False when the dial has no room for a readable bridge. */
    private val fits = thickness >= sizePx * 0.035f
    private val outer = inner + thickness
    private val mid = inner + thickness / 2f
    private val jewelRadius = thickness * 0.26f
    private val jewelInset = thickness * 0.62f
    private val endPadding = jewelInset + jewelRadius + thickness * 0.35f

    private val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Shrink the lettering until the bridge fits within the dial's allowed arc.
    private val textWidth: Float = run {
        var size = max(9 * dp, sizePx * 0.027f)
        var spacing = 0.22f // em
        for (attempt in 0 until 12) {
            paint.textSize = size
            paint.letterSpacing = spacing
            if ((paint.measureText(text) + 2 * endPadding) / mid <= spec.maxSpan || size <= 7 * dp) break
            size *= 0.93f
            spacing *= 0.9f
        }
        paint.measureText(text)
    }

    private val textSpan = textWidth / mid
    private val start = TAU / 4 - textSpan / 2 - endPadding / mid
    private val end = TAU / 4 + textSpan / 2 + endPadding / mid

    private val body = bridgeOutline(inner, outer)
    private val insetLine = bridgeOutline(inner + 2.5f * dp, outer - 2.5f * dp)

    // Runs from the left end to the right end along the bottom of the circle, so the
    // lettering reads left to right and stands upright.
    private val textPath = NativePath().apply {
        addArc(
            RectF(center.x - mid, center.y - mid, center.x + mid, center.y + mid),
            (TAU / 4 + textSpan / 2).toDegrees(),
            -textSpan.toDegrees()
        )
    }

    /** Moves the glyphs from sitting on the path to being centred on it. */
    private val centreOffset = -(paint.ascent() + paint.descent()) / 2f

    fun draw(scope: DrawScope) {
        if (!fits) return
        with(scope) {
            translate(0f, 2 * dp) { drawPath(body, Color.Black.copy(alpha = 0.35f)) }
            drawPath(body, finish.fill(Offset(center.x - outer, center.y), Offset(center.x + outer, center.y + outer)))
            clipPath(body) {
                for (k in 1..3) {
                    val r = inner + k * thickness / 4f
                    drawArc(
                        color = finish.stripe,
                        startAngle = (start - 0.3f).toDegrees(),
                        sweepAngle = (end - start + 0.6f).toDegrees(),
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(thickness / 7f)
                    )
                }
            }
            drawPath(body, finish.edge, style = Stroke(dp))
            drawPath(insetLine, finish.inset, style = Stroke(0.7f * dp))

            jewel(pointOn(center, mid, start + jewelInset / mid), jewelRadius, palette, dp)
            jewel(pointOn(center, mid, end - jewelInset / mid), jewelRadius, palette, dp)

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                if (finish.isMetal) {
                    paint.color = Color.White.copy(alpha = 0.35f).toArgb()
                    native.drawTextOnPath(text, textPath, 0f, centreOffset + 0.8f * dp, paint)
                }
                paint.color = finish.ink.toArgb()
                native.drawTextOnPath(text, textPath, 0f, centreOffset, paint)
            }
        }
    }

    /** Annular sector from [start] to [end] with rounded ends. */
    private fun bridgeOutline(innerEdge: Float, outerEdge: Float) = Path().apply {
        val capCentre = (innerEdge + outerEdge) / 2f
        val cap = (outerEdge - innerEdge) / 2f
        arcTo(Rect(center, outerEdge), start.toDegrees(), (end - start).toDegrees(), true)
        arcTo(Rect(pointOn(center, capCentre, end), cap), end.toDegrees(), 180f, false)
        arcTo(Rect(center, innerEdge), end.toDegrees(), -(end - start).toDegrees(), false)
        arcTo(Rect(pointOn(center, capCentre, start), cap), start.toDegrees() + 180f, 180f, false)
        close()
    }
}
