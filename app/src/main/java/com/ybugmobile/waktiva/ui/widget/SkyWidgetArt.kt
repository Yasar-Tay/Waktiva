package com.ybugmobile.waktiva.ui.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.prayerlog.paleColor
import com.ybugmobile.waktiva.ui.prayerlog.prayerStar
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The prayer log's night for the widgets, painted as the screen paints it: the night itself, and
 * the day's five prayers on the sun's path, each its own star as on the screen, two in a row both
 * lit joined, or, the day made full, Cassiopeia's W.
 */
internal object SkyWidgetArt {

    /** Where a prayer's star stands. */
    enum class Star {
        /** Marked: lit. */
        LIT,

        /** Its time is on and it isn't marked: ringed, waiting. */
        NOW,

        /** Its time went by unmarked: a dark hole ringed in coral. */
        MISSED,

        /** Its time is still to come: a faint outline. */
        LATER,

        /** The log is off: only a star, neither lit nor waiting. */
        PLAIN
    }

    /** A prayer in the sky: its [type], its time in minutes into the day if kept, and its [star]. */
    class SkyStar(val type: PrayerType, val minutes: Int?, val star: Star)

    private val Night = intArrayOf(0xFF03050D.toInt(), 0xFF070B22.toInt(), 0xFF141142.toInt(), 0xFF1E1A52.toInt())
    private val Lilac = Color(0xFFC5CBF0)
    private const val STAR_WHITE = 0xFFFFFDF4.toInt()

    /** Cassiopeia's five stars on a sky 390 by 320, and how bright each is. */
    private val Cassiopeia = listOf(
        Triple(70f, 129.5f, 1f),
        Triple(140f, 218.6f, 1.05f),
        Triple(195f, 139.4f, 1.12f),
        Triple(250f, 205.4f, 0.9f),
        Triple(320f, 119.6f, 0.78f)
    )

    private val nightCache = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 3
    }

    /** The night, [width] by [height]: deep blue into violet, a lilac glow low down, and stars. */
    fun night(width: Int, height: Int): Bitmap {
        val key = "$width×$height"
        synchronized(nightCache) { nightCache[key] }?.let { return it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, Night, floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP)
        })
        canvas.drawRect(0f, 0f, w, h, Paint().apply {
            shader = RadialGradient(
                w / 2f, h * 1.05f, w * 0.9f,
                intArrayOf(argb(Color(0xFF9FA8DA), 0.3f), 0), null, Shader.TileMode.CLAMP
            )
        })
        val random = Random(11)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = min(w, h) / 200f
        repeat((width * height / 900).coerceIn(30, 120)) {
            dot.color = argb(Color.White, 0.2f + 0.55f * random.nextFloat())
            val y = h * random.nextFloat() * random.nextFloat()
            canvas.drawCircle(random.nextFloat() * w, y, unit * (0.4f + 0.6f * random.nextFloat()), dot)
        }
        synchronized(nightCache) { nightCache[key] = bitmap }
        return bitmap
    }

    /**
     * The day's [stars] on the sun's path across a sky [width] by [height], the horizon near its
     * foot; with [queen], the five in Cassiopeia's W instead. [rtl] runs the day right to left.
     */
    fun daySky(stars: List<SkyStar>, queen: Boolean, width: Int, height: Int, density: Float, rtl: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val dp = density
        val horizon = h * 0.92f
        val cx = w / 2f
        val rx = w * 0.43f
        val ry = horizon * 0.86f
        val unit = min(w / 390f, h / 320f) * 1.35f

        // Minutes into the day, a time past midnight counted on from the day before.
        var last = -1
        val minutes = stars.map { s -> s.minutes?.let { (if (it < last) it + 24 * 60 else it).also { v -> last = v } } }
        val known = minutes.filterNotNull()
        val first = known.minOrNull() ?: 0
        val span = ((known.maxOrNull() ?: 1) - first).coerceAtLeast(1)
        val onArc = stars.indices.map { i ->
            val share = minutes[i]?.let { (it - first).toFloat() / span } ?: (i / 4f)
            val angle = PI.toFloat() * (0.05f + 0.88f * share)
            val dx = rx * cos(angle)
            floatArrayOf(if (rtl) cx + dx else cx - dx, horizon - ry * sin(angle))
        }
        val inW = Cassiopeia.map { (x, y, _) -> floatArrayOf(w * x / 390f, h * y / 320f) }
        val at = if (queen) inW else onArc

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // The horizon, and its glow.
        paint.shader = RadialGradient(cx, horizon, w * 0.6f, intArrayOf(argb(Color(0xFF9FA8DA), 0.45f), 0), null, Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(cx - w * 0.6f, horizon - h * 0.18f, cx + w * 0.6f, horizon + h * 0.18f), paint)
        paint.shader = null
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        line.color = argb(Color.White, 0.2f)
        line.strokeWidth = 1f * dp
        canvas.drawLine(0f, horizon, w, horizon, line)

        if (queen) {
            val path = Path().apply { inW.forEachIndexed { i, p -> if (i == 0) moveTo(p[0], p[1]) else lineTo(p[0], p[1]) } }
            line.color = argb(Color.White, 0.3f)
            line.strokeWidth = 4f * dp
            line.maskFilter = BlurMaskFilter(3f * dp, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, line)
            line.maskFilter = null
            line.color = STAR_WHITE
            line.strokeWidth = 1.4f * dp
            canvas.drawPath(path, line)
        } else {
            // The sun's path, dotted.
            line.color = argb(Color.White, 0.16f)
            line.strokeWidth = 1f * dp
            line.pathEffect = DashPathEffect(floatArrayOf(2f * dp, 6f * dp), 0f)
            canvas.drawArc(RectF(cx - rx, horizon - ry, cx + rx, horizon + ry), 180f, 180f, false, line)
            line.pathEffect = null
            // Two in a row both lit, joined; one gone unmarked breaks the line.
            if (stars.size > 1) {
                line.shader = LinearGradient(0f, 0f, w, 0f, stars.map { it.type.paleColor.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP)
                for (i in 0 until stars.size - 1) {
                    if (stars[i].star != Star.LIT || stars[i + 1].star != Star.LIT) continue
                    line.alpha = 64
                    line.strokeWidth = 5f * dp
                    canvas.drawLine(onArc[i][0], onArc[i][1], onArc[i + 1][0], onArc[i + 1][1], line)
                    line.alpha = 255
                    line.strokeWidth = 1.4f * dp
                    canvas.drawLine(onArc[i][0], onArc[i][1], onArc[i + 1][0], onArc[i + 1][1], line)
                }
                line.shader = null
            }
            // A dotted line on to the one waiting, from the star before it, lit.
            val now = stars.indexOfFirst { it.star == Star.NOW }
            if (now > 0 && stars[now - 1].star == Star.LIT) {
                line.color = Lilac.toArgb()
                line.strokeWidth = 1.3f * dp
                line.pathEffect = DashPathEffect(floatArrayOf(2f * dp, 4f * dp), 0f)
                canvas.drawLine(onArc[now - 1][0], onArc[now - 1][1], onArc[now][0], onArc[now][1], line)
                line.pathEffect = null
            }
        }

        // The screen's stars have a body 17 across; here, 12 of the sky's units.
        val starUnit = 12f * unit / 17f
        stars.forEachIndexed { i, s ->
            val bright = if (queen) Cassiopeia[i].third else 1f
            canvas.drawPrayerStar(s, at[i][0], at[i][1], starUnit, bright)
        }
        return bitmap
    }

    private val markCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 24
    }

    /** A prayer's star alone, [size] pixels square, as a day card's mark. */
    fun mark(type: PrayerType, star: Star, size: Int): Bitmap {
        val key = "$type/$star/$size"
        synchronized(markCache) { markCache[key] }?.let { return it }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Its rays just reach the edge; the body is about half of it.
        Canvas(bitmap).drawPrayerStar(SkyStar(type, null, star), size / 2f, size / 2f, size / 2f / 24f, 1f)
        synchronized(markCache) { markCache[key] = bitmap }
        return bitmap
    }

    /**
     * [s] as the prayer log screen draws it (see prayerStar), at ([x], [y]), [unit] pixels to the
     * screen's dp, lit stars sized by [scale]. A plain star (the log off) is its outline, bright.
     */
    fun Canvas.drawPrayerStar(s: SkyStar, x: Float, y: Float, unit: Float, scale: Float) {
        val status = when (s.star) {
            Star.LIT -> PrayerLogStatus.PRAYED
            Star.NOW -> PrayerLogStatus.ACTIVE
            Star.MISSED -> PrayerLogStatus.MISSED
            Star.LATER, Star.PLAIN -> PrayerLogStatus.UPCOMING
        }
        CanvasDrawScope().draw(Density(unit), LayoutDirection.Ltr, ComposeCanvas(this), Size(width.toFloat(), height.toFloat())) {
            // A still: rays at full shimmer, the calling ring a little way out, no flash.
            prayerStar(s.type, status, Offset(x, y), scale, twinkle = 1f, pulse = 0.35f, ignite = 1f, isDay = s.star == Star.PLAIN)
        }
    }

    private fun argb(color: Color, alpha: Float): Int = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
}
