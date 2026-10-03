package com.ybugmobile.waktiva.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.prayerlog.LogColors
import com.ybugmobile.waktiva.ui.theme.clouds.buildCloudScene
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The widgets' painted parts, in the home screen's own materials: glass spheres holding the sky
 * of their hour in the prayer's colour, gold hairlines, a gold sphere once a prayer is marked, and
 * the home screen's clouds and stars over the widget's sky. RemoteViews can't hold a composable,
 * so these are drawn on an Android canvas.
 */
internal object WidgetArt {

    /** The light falls from the upper left, as on the home screen's dials. */
    private const val LIGHT_X = -0.7071f
    private const val LIGHT_Y = -0.7071f

    private val Gold = Color(0xFFDEBE78)

    /** What a glass sphere shows: its prayer's [color], the [sky] inside it and its [icon]. */
    class Orb(
        val color: Color,
        val sky: Color,
        val icon: Int,
        /** Marked in the prayer log: a brass sphere with a tick, as the prayer log draws it. */
        val prayed: Boolean,
        /** Faded back, for a prayer whose time has gone by. */
        val dim: Boolean = false,
        /** Raised in a halo of its colour, for the prayer whose time it is. */
        val raised: Boolean = false,
        /** Written across the sphere's lower half, as on a button: "I PRAYED". */
        val caption: String? = null
    )

    /**
     * Paints [orb] as a glass sphere of radius [r] at ([x], [y]): a shadow on what's below, a rim
     * in the prayer's colour bright towards the light, the sky inside deeper away from it, the
     * prayer's sign in white over a glow of its colour, and a gleam across the glass. A prayed
     * sphere is brass, with a tick and a bloom of gold around it, as in the prayer log.
     */
    fun drawOrb(context: Context, canvas: Canvas, x: Float, y: Float, r: Float, orb: Orb, dp: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val lx = LIGHT_X
        val ly = LIGHT_Y
        val alpha = if (orb.dim && !orb.prayed) 0.62f else 1f
        val color = if (orb.prayed) LogColors.Brass else orb.color

        if (orb.raised || orb.prayed) {
            val outer = r * 2f
            val glow = if (orb.prayed) LogColors.Gold else orb.color
            paint.shader = RadialGradient(
                x, y, outer,
                intArrayOf(argb(glow, 0.5f), argb(glow, 0.18f), 0),
                floatArrayOf(r / outer, 1.35f * r / outer, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, outer, paint)
        }

        // Shadow under the sphere.
        val shade = r + 3f * dp
        paint.shader = RadialGradient(
            x, y + 1.5f * dp, shade,
            intArrayOf(argb(Color.Black, 0.45f * alpha), 0),
            floatArrayOf(0.6f * r / shade, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y + 1.5f * dp, shade, paint)

        // The rim, bright where it faces the light.
        paint.shader = LinearGradient(
            x + lx * r, y + ly * r, x - lx * r, y - ly * r,
            intArrayOf(argb(lighten(color, 0.45f), alpha), argb(color, alpha), argb(darken(color, 0.35f), alpha)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, r, paint)

        // The inside: the sky of the hour, or gold once prayed.
        val inner = r - max(1.6f * dp, r * 0.09f)
        val face = if (orb.prayed) LogColors.Gold else orb.sky
        paint.shader = RadialGradient(
            x + lx * inner * 0.45f, y + ly * inner * 0.45f, inner * 1.25f,
            intArrayOf(argb(lighten(face, 0.38f), alpha), argb(lighten(face, 0.1f), alpha), argb(darken(face, 0.3f), alpha)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, inner, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = argb(Color.Black, 0.35f * alpha)
        canvas.drawCircle(x, y, inner, paint)
        paint.style = Paint.Style.FILL

        // A glow of the prayer's colour behind its sign.
        if (!orb.prayed) {
            paint.shader = RadialGradient(
                x, y, inner * 0.95f,
                intArrayOf(argb(orb.color, (if (orb.raised) 0.6f else 0.45f) * alpha), 0),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, inner, paint)
            paint.shader = null
        }

        // The sign, lifted when a caption shares the sphere.
        val iconRes = if (orb.prayed) R.drawable.ic_widget_check else orb.icon
        val iconSize = inner * if (orb.caption != null) 0.85f else 1.15f
        val iconY = if (orb.caption != null) y - inner * 0.2f else y
        val ink = if (orb.prayed) LogColors.Ink.toArgb() else argb(Color.White, 0.96f * alpha)
        ContextCompat.getDrawable(context, iconRes)?.mutate()?.let { icon ->
            val half = iconSize / 2f
            if (!orb.prayed) {
                icon.setTint(argb(Color.Black, 0.35f * alpha))
                icon.setBounds((x - half).toInt(), (iconY - half + 0.8f * dp).toInt(), (x + half).toInt(), (iconY + half + 0.8f * dp).toInt())
                icon.draw(canvas)
            }
            icon.setTint(ink)
            icon.setBounds((x - half).toInt(), (iconY - half).toInt(), (x + half).toInt(), (iconY + half).toInt())
            icon.draw(canvas)
        }

        orb.caption?.let { caption -> drawCaption(context, canvas, caption, x, y + inner * 0.48f, inner * 1.45f, inner * 0.26f, dp) }

        // The gleam across the side facing the light.
        val save = canvas.save()
        canvas.clipPath(Path().apply { addCircle(x, y, inner, Path.Direction.CW) })
        val gx = x + lx * inner * 0.45f
        val gy = y + ly * inner * 0.45f
        paint.shader = LinearGradient(
            gx, gy - inner * 0.4f, gx, gy + inner * 0.4f,
            argb(Color.White, 0.42f * alpha), 0, Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(gx - inner * 0.62f, gy - inner * 0.38f, gx + inner * 0.62f, gy + inner * 0.38f), paint)
        canvas.restoreToCount(save)
        paint.shader = null

        // Gold hairline, and the double rim of a prayed sphere.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.7f * dp
        paint.color = argb(Gold, 0.6f * alpha)
        canvas.drawCircle(x, y, r + 0.35f * dp, paint)
        if (orb.prayed) {
            paint.strokeWidth = 1.4f * dp
            paint.color = argb(Color.White, 0.9f)
            canvas.drawCircle(x, y, r + 1.4f * dp, paint)
            paint.strokeWidth = 1.1f * dp
            paint.color = argb(lerp(orb.color, LogColors.Gold, 0.6f), 1f)
            canvas.drawCircle(x, y, r + 3.4f * dp, paint)
        }
    }

    /** [text] in white capitals across [width] at most, centred on ([x], [baseline]). */
    private fun drawCaption(context: Context, canvas: Canvas, text: String, x: Float, baseline: Float, width: Float, size: Float, dp: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
            textSize = size
            letterSpacing = 0.08f
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            setShadowLayer(2f * dp, 0f, 0.6f * dp, argb(Color.Black, 0.55f))
        }
        val measured = paint.measureText(text)
        if (measured > width) paint.textSize = size * width / measured
        val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        canvas.drawText(text, x, baseline + bounds.height() / 2f, paint)
    }

    /**
     * The sphere of the moment, [sizePx] across, for the 4×2 widget: [orb] inside a slim enamel
     * track whose bright arc is the share of the prayer's time [remaining], shrinking from the top
     * like a sand glass, ending in a point of light, between gold hairlines.
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
        paint.color = argb(Gold, 0.65f)
        paint.strokeWidth = 0.8f * dp
        canvas.drawCircle(c, c, track + width / 2f + 0.5f * dp, paint)
        canvas.drawCircle(c, c, track - width / 2f - 0.5f * dp, paint)

        drawOrb(context, canvas, c, c, track - width / 2f - max(5f * dp, s * 0.07f), orb, dp)
        return bitmap
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

    private fun darken(color: Color, amount: Float) =
        Color(color.red * (1f - amount), color.green * (1f - amount), color.blue * (1f - amount), color.alpha)
}
