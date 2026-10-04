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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The prayer log's night for the widgets, painted as the screen paints it: the night itself, and
 * the day's five prayers as stars on the sun's path, the lit ones joined into the day's
 * constellation, or, the day made full, Cassiopeia's W.
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
    private val Coral = Color(0xFFFFB4B4)
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
            // The lit ones joined.
            val lit = stars.indices.filter { stars[it].star == Star.LIT }
            if (lit.size > 1) {
                val path = Path().apply { lit.forEachIndexed { n, i -> if (n == 0) moveTo(onArc[i][0], onArc[i][1]) else lineTo(onArc[i][0], onArc[i][1]) } }
                line.color = argb(Color(0xFFFFFAE6), 0.25f)
                line.strokeWidth = 5f * dp
                canvas.drawPath(path, line)
                line.color = argb(Color(0xFFFFFAE6), 0.9f)
                line.strokeWidth = 1.3f * dp
                canvas.drawPath(path, line)
            }
            // A dotted line on to the one waiting.
            val now = stars.indexOfFirst { it.star == Star.NOW }
            val before = lit.lastOrNull { it < now }
            if (now >= 0 && before != null) {
                line.color = Lilac.toArgb()
                line.strokeWidth = 1.3f * dp
                line.pathEffect = DashPathEffect(floatArrayOf(2f * dp, 4f * dp), 0f)
                canvas.drawLine(onArc[before][0], onArc[before][1], onArc[now][0], onArc[now][1], line)
                line.pathEffect = null
            }
        }

        stars.forEachIndexed { i, s ->
            val x = at[i][0]
            val y = at[i][1]
            val bright = if (queen) Cassiopeia[i].third else 1f
            drawStar(canvas, s, x, y, 12f * unit * bright, dp)
        }
        return bitmap
    }

    /** One prayer's star, of radius [r], at ([x], [y]). */
    private fun drawStar(canvas: Canvas, s: SkyStar, x: Float, y: Float, r: Float, dp: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (s.star) {
            Star.LIT -> {
                paint.shader = RadialGradient(x, y, r * 2.2f, intArrayOf(argb(s.type.accentColor, 0.6f), 0), null, Shader.TileMode.CLAMP)
                canvas.drawCircle(x, y, r * 2.2f, paint)
                paint.shader = null
                paint.color = STAR_WHITE
                canvas.drawPath(sparkle(x, y, r), paint)
            }
            Star.NOW -> {
                paint.color = argb(Color(0xFF9FA8DA), 0.2f)
                canvas.drawCircle(x, y, r * 0.95f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * dp
                paint.color = argb(Lilac, 0.85f)
                canvas.drawCircle(x, y, r * 0.95f, paint)
                paint.color = argb(Lilac, 0.35f)
                canvas.drawCircle(x, y, r * 1.45f, paint)
                paint.color = 0xFFDDE1FF.toInt()
                paint.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(sparkle(x, y, r * 0.6f), paint)
            }
            Star.MISSED -> {
                paint.color = argb(Color(0xFF03050D), 0.9f)
                canvas.drawCircle(x, y, r * 0.5f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * dp
                paint.color = argb(Coral, 0.75f)
                canvas.drawCircle(x, y, r * 0.5f, paint)
            }
            Star.LATER -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * dp
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = argb(Color.White, 0.32f)
                canvas.drawPath(sparkle(x, y, r * 0.55f), paint)
            }
            Star.PLAIN -> {
                paint.color = argb(Color.White, 0.7f)
                canvas.drawPath(sparkle(x, y, r * 0.6f), paint)
            }
        }
    }

    /** A four-pointed star of radius [r] at ([x], [y]), its arms drawn in with soft curves. */
    private fun sparkle(x: Float, y: Float, r: Float) = Path().apply {
        val a = 0.137f * r
        val b = 0.158f * r
        moveTo(x, y - r)
        cubicTo(x + a, y - b, x + b, y - a, x + r, y)
        cubicTo(x + b, y + a, x + a, y + b, x, y + r)
        cubicTo(x - a, y + b, x - b, y + a, x - r, y)
        cubicTo(x - b, y - a, x - a, y - b, x, y - r)
        close()
    }

    private fun argb(color: Color, alpha: Float): Int = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
}
