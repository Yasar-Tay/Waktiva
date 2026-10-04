package com.ybugmobile.waktiva.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.theme.clouds.buildCloudScene
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The widgets' painted parts: the moment's star in a pane of the prayer log's night for the 4×2
 * widget, and the home screen's clouds and stars over the widget's sky. RemoteViews can't hold a
 * composable, so these are drawn on an Android canvas.
 */
internal object WidgetArt {

    /** The night inside the moment's pane, its middle to its edge. */
    private val PaneNight = intArrayOf(0xFF141142.toInt(), 0xFF070B22.toInt(), 0xFF03050D.toInt())

    /**
     * What the moment's pane shows: the [type] of prayer, its [star] as the prayer log draws it (its
     * time on: waiting to be lit; marked: lit; the next one while none is on: still to come), and,
     * while it can be marked, a [caption] under it: "I PRAYED".
     */
    class Orb(val type: PrayerType, val star: SkyWidgetArt.Star, val caption: String? = null)

    /**
     * The moment, [sizePx] across, for the 4×2 widget: a slim track whose bright arc is the share of
     * the prayer's time [remaining], shrinking from the top like a sand glass and ending in a point
     * of light, between faint white hairlines; inside it a round pane of the prayer log's night with
     * a few stars, and in it the prayer's own star ([orb]).
     */
    fun momentOrb(context: Context, orb: Orb, remaining: Float, arc: Color, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dp = context.resources.displayMetrics.density
        val s = sizePx.toFloat()
        val c = s / 2f
        val width = s * 0.055f
        val track = c - width / 2f - 2.5f * dp
        val oval = RectF(c - track, c - track, c + track, c + track)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        paint.color = 0xB3101426.toInt()
        paint.strokeWidth = width + 2f * dp
        canvas.drawCircle(c, c, track, paint)
        paint.color = argb(arc, 0.18f)
        paint.strokeWidth = width
        canvas.drawCircle(c, c, track, paint)

        val sweep = 360f * remaining.coerceIn(0f, 1f)
        if (sweep > 1f) {
            paint.strokeCap = Paint.Cap.ROUND
            paint.shader = SweepGradient(c, c, intArrayOf(argb(lighten(arc, 0.35f), 1f), arc.toArgb(), arc.toArgb()), floatArrayOf(0f, 0.5f, 1f))
            val save = canvas.save()
            canvas.rotate(-90f, c, c)
            canvas.drawArc(oval, 0f, sweep, false, paint)
            canvas.restoreToCount(save)
            paint.shader = null
            val end = Math.toRadians((sweep - 90f).toDouble())
            val px = c + track * cos(end).toFloat()
            val py = c + track * sin(end).toFloat()
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.shader = RadialGradient(px, py, width * 1.6f, intArrayOf(argb(arc, 0.7f), 0), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(px, py, width * 1.6f, fill)
            fill.shader = null
            fill.color = android.graphics.Color.WHITE
            canvas.drawCircle(px, py, width * 0.42f, fill)
        }

        paint.strokeCap = Paint.Cap.BUTT
        paint.color = argb(Color.White, 0.22f)
        paint.strokeWidth = 0.8f * dp
        canvas.drawCircle(c, c, track + width / 2f + 0.5f * dp, paint)
        canvas.drawCircle(c, c, track - width / 2f - 0.5f * dp, paint)

        drawPane(context, canvas, c, track - width / 2f - max(5f * dp, s * 0.07f), orb, dp)
        return bitmap
    }

    /**
     * The round pane of night, radius [r], centred in a bitmap whose middle is [c]: deep blue into
     * the dark at its edge, a few stars, a faint white rim, the prayer's star, and the caption.
     */
    private fun drawPane(context: Context, canvas: Canvas, c: Float, r: Float, orb: Orb, dp: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(c, c - r * 0.2f, r * 1.15f, PaneNight, floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(c, c, r, paint)
        paint.shader = null

        val random = Random(29)
        repeat(14) {
            val a = random.nextFloat() * 2f * PI.toFloat()
            val d = r * (0.25f + 0.65f * random.nextFloat())
            paint.color = argb(Color.White, 0.2f + 0.45f * random.nextFloat())
            canvas.drawCircle(c + d * cos(a), c + d * sin(a), (0.5f + 0.6f * random.nextFloat()) * dp, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = argb(Color.White, 0.18f)
        canvas.drawCircle(c, c, r, paint)
        paint.style = Paint.Style.FILL

        // The star, lifted when the caption shares the pane; its lit body about two fifths across.
        val starY = if (orb.caption != null) c - r * 0.16f else c
        val save = canvas.save()
        canvas.clipPath(Path().apply { addCircle(c, c, r, Path.Direction.CW) })
        with(SkyWidgetArt) { canvas.drawPrayerStar(SkyWidgetArt.SkyStar(orb.type, null, orb.star), c, starY, r * 0.4f / 17f, 1f) }
        canvas.restoreToCount(save)

        orb.caption?.let { caption -> drawCaption(context, canvas, caption, c, c + r * 0.5f, r * 1.3f, r * 0.2f, dp) }
    }

    /** [text] in white capitals across [width] at most, centred on ([x], [baseline]). */
    private fun drawCaption(context: Context, canvas: Canvas, text: String, x: Float, baseline: Float, width: Float, size: Float, dp: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
            textSize = size
            letterSpacing = 0.12f
            textAlign = Paint.Align.CENTER
            color = 0xFFF4F1FF.toInt()
            setShadowLayer(2f * dp, 0f, 0.6f * dp, argb(Color.Black, 0.55f))
        }
        val measured = paint.measureText(text)
        if (measured > width) paint.textSize = size * width / measured
        val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        canvas.drawText(text, x, baseline + bounds.height() / 2f, paint)
    }

    // ── The widget's sky ──────────────────────────────────────────────────

    /**
     * The home screen's clouds for [weather] over a sky [width] × [height], drawn still; or, on a
     * clear night, a scatter of stars. Nothing on a clear day.
     */
    fun drawAtmosphere(canvas: Canvas, width: Int, height: Int, weather: WeatherCondition?, isDay: Boolean) {
        val clear = weather == null || weather == WeatherCondition.CLEAR || weather == WeatherCondition.MAINLY_CLEAR
        if (clear) {
            if (!isDay) drawStars(canvas, width, height, sparse = weather == WeatherCondition.MAINLY_CLEAR)
            return
        }
        val scene = runCatching { buildCloudScene(weather!!, isDay, width.toFloat(), height.toFloat()) }.getOrNull() ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (cloud in scene.clouds) {
            val p = cloud.placement
            val sprite = cloud.sprite
            val left = p.startX - sprite.pad
            val top = p.top - sprite.pad
            paint.alpha = (255 * p.alpha * 0.85f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(sprite.image.asAndroidBitmap(), null, RectF(left, top, left + sprite.width, top + sprite.height), paint)
        }
    }

    private fun drawStars(canvas: Canvas, width: Int, height: Int, sparse: Boolean) {
        val rnd = Random(4211)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val count = (width * height / if (sparse) 2600 else 1300).coerceIn(12, 90)
        val unit = min(width, height) / 200f
        repeat(count) {
            val x = rnd.nextFloat() * width
            // More stars high up, where the sky is darkest.
            val y = height * rnd.nextFloat() * rnd.nextFloat()
            paint.color = argb(Color.White, 0.25f + 0.55f * rnd.nextFloat())
            canvas.drawCircle(x, y, unit * (0.35f + 0.7f * rnd.nextFloat()), paint)
        }
    }

    private fun argb(color: Color, alpha: Float): Int = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()

    private fun lighten(color: Color, amount: Float) =
        Color(color.red + (1f - color.red) * amount, color.green + (1f - color.green) * amount, color.blue + (1f - color.blue) * amount, color.alpha)
}
