package com.ybugmobile.waktiva.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.home.composables.MissedRed
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.theme.clouds.buildCloudScene
import kotlin.math.min
import kotlin.random.Random

/**
 * The widgets' painted parts: the "I prayed" button in the prayer's colour, the day's tally in
 * gold once a prayer is marked, the marks on the day's prayers, the level's gold diamond, and the
 * home screen's clouds and stars over the widget's sky. RemoteViews can't hold a composable, nor
 * tint a shape on every Android version, so these are drawn on an Android canvas.
 */
internal object WidgetArt {

    private const val MAX_PX = 420

    private val GoldLight = Color(0xFFFFECB3)
    private val GoldDeep = Color(0xFFE0A800)
    private val GoldInk = Color(0xFF3B2A00)

    /** How a prayer's line in the "my day" widget is marked. */
    enum class Mark { PRAYED, MISSED, ACTIVE, UPCOMING, QUIET }

    // ── The moment's button ───────────────────────────────────────────────

    /** The 4×2's tile, [TILE_W] × [TILE_H] dp: what the moment offers beside its countdown. */
    sealed interface Button {
        /** "I prayed" in [color]; [filled] in full while its time is ending. */
        class MarkIt(val color: Color, val label: String, val filled: Boolean) : Button

        /** The day's tally in gold: [done] of [total] prayed. */
        class Tally(val done: Int, val total: Int) : Button

        /** Nothing to mark: the sign and [name] of the prayer the moment is about, in its [color]. */
        class Sign(val color: Color, @DrawableRes val icon: Int, val name: String) : Button
    }

    private const val TILE_W = 84
    private const val TILE_H = 104
    private const val DISC = 56

    /** [button] as the 4×2's tile: a rounded pane washed in its colour. */
    fun tile(context: Context, button: Button): Bitmap {
        val dp = density(context, TILE_H)
        val w = TILE_W * dp
        val h = TILE_H * dp
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pane = RectF(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp)
        val corner = 18f * dp
        val cx = w / 2f
        when (button) {
            is Button.MarkIt -> {
                val ink = inkOn(button.color)
                if (button.filled) {
                    canvas.drawRoundRect(pane, corner, corner, fill(button.color))
                    drawIcon(context, canvas, R.drawable.ic_widget_check, cx, h * 0.4f, 30f * dp, ink)
                    drawText(context, canvas, button.label, cx, h * 0.76f, 12.5f * dp, ink, R.font.inter_bold, w - 14f * dp)
                } else {
                    canvas.drawRoundRect(pane, corner, corner, fill(button.color, 0.18f))
                    canvas.drawRoundRect(pane, corner, corner, stroke(button.color, 0.6f, 1f * dp))
                    canvas.drawCircle(cx, h * 0.4f, 21f * dp, fill(button.color))
                    drawIcon(context, canvas, R.drawable.ic_widget_check, cx, h * 0.4f, 22f * dp, ink)
                    drawText(context, canvas, button.label, cx, h * 0.8f, 12.5f * dp, button.color, R.font.inter_bold, w - 14f * dp)
                }
            }
            is Button.Tally -> {
                canvas.drawRoundRect(pane, corner, corner, fill(PrayedGold, 0.16f))
                canvas.drawRoundRect(pane, corner, corner, stroke(PrayedGold, 0.4f, 1f * dp))
                drawIcon(context, canvas, R.drawable.ic_widget_check, cx, h * 0.3f, 18f * dp, PrayedGold)
                drawText(context, canvas, "${button.done}/${button.total}", cx, h * 0.62f, 28f * dp, PrayedGold, R.font.inter_bold, w - 12f * dp)
            }
            is Button.Sign -> {
                canvas.drawRoundRect(pane, corner, corner, fill(button.color, 0.14f))
                canvas.drawRoundRect(pane, corner, corner, stroke(button.color, 0.4f, 1f * dp))
                canvas.drawCircle(cx, h * 0.4f, 21f * dp, fill(button.color, 0.28f))
                drawIcon(context, canvas, button.icon, cx, h * 0.4f, 24f * dp, Color.White)
                drawText(context, canvas, button.name, cx, h * 0.8f, 12f * dp, Color.White.copy(alpha = 0.9f), R.font.inter_medium, w - 12f * dp)
            }
        }
        return bitmap
    }

    /** [button] as the 4×4 card's disc, [sizeDp] across; drawn to the proportions of a [DISC] dp one. */
    fun disc(context: Context, button: Button, sizeDp: Int): Bitmap {
        val dp = density(context, sizeDp) * sizeDp / DISC
        val s = DISC * dp
        val bitmap = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = s / 2f
        val r = c - 1f * dp
        when (button) {
            is Button.MarkIt -> {
                val ink = inkOn(button.color)
                canvas.drawCircle(c, c, r, fill(button.color))
                drawIcon(context, canvas, R.drawable.ic_widget_check, c, s * 0.4f, 20f * dp, ink)
                drawText(context, canvas, button.label, c, s * 0.72f, 9.5f * dp, ink, R.font.inter_bold, s - 12f * dp)
            }
            is Button.Tally -> {
                canvas.drawCircle(c, c, r, fill(PrayedGold, 0.16f))
                canvas.drawCircle(c, c, r, stroke(PrayedGold, 0.5f, 1f * dp))
                drawText(context, canvas, "${button.done}/${button.total}", c, c, 17f * dp, PrayedGold, R.font.inter_bold, s - 12f * dp)
            }
            is Button.Sign -> {
                canvas.drawCircle(c, c, r, fill(button.color, 0.22f))
                canvas.drawCircle(c, c, r, stroke(button.color, 0.6f, 1f * dp))
                drawIcon(context, canvas, button.icon, c, c, 26f * dp, Color.White)
            }
        }
        return bitmap
    }

    // ── Marks ─────────────────────────────────────────────────────────────

    private const val MARK = 20

    /**
     * A prayer's mark on its line of the "my day" widget, in [color] while its time is on,
     * [sizeDp] across; drawn to the proportions of a [MARK] dp one.
     */
    fun mark(context: Context, mark: Mark, color: Color, sizeDp: Int): Bitmap {
        val dp = density(context, sizeDp) * sizeDp / MARK
        val s = MARK * dp
        val bitmap = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = s / 2f
        val width = 1.5f * dp
        val r = c - width
        when (mark) {
            Mark.PRAYED -> {
                canvas.drawCircle(c, c, c - 0.5f * dp, fill(PrayedGold))
                drawIcon(context, canvas, R.drawable.ic_widget_check, c, c, 13f * dp, GoldInk)
            }
            Mark.MISSED -> {
                canvas.drawCircle(c, c, r, stroke(MissedRed, 0.85f, width))
                drawIcon(context, canvas, R.drawable.ic_widget_cross, c, c, 11f * dp, MissedRed)
            }
            Mark.ACTIVE -> {
                canvas.drawCircle(c, c, r, fill(color, 0.2f))
                canvas.drawCircle(c, c, r, stroke(color, 1f, width))
            }
            Mark.UPCOMING -> canvas.drawCircle(c, c, r, stroke(Color.White, 0.34f, width).apply {
                pathEffect = DashPathEffect(floatArrayOf(2.6f * dp, 2.2f * dp), 0f)
            })
            Mark.QUIET -> canvas.drawCircle(c, c, 3f * dp, fill(color, 0.75f))
        }
        return bitmap
    }

    /**
     * The level in a gold diamond, as the prayer log widget's badge: a rounded square turned 45°,
     * as large as [sizeDp] holds corner to corner.
     */
    fun diamond(context: Context, level: Int, sizeDp: Int): Bitmap {
        val dp = density(context, sizeDp)
        val s = sizeDp * dp
        val bitmap = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = s / 2f
        val half = s * 0.35f
        val square = RectF(c - half, c - half, c + half, c + half)
        val corner = s * 0.17f
        val save = canvas.save()
        canvas.rotate(45f, c, c)
        canvas.drawRoundRect(square, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                c - half, c - half, c + half, c + half,
                intArrayOf(GoldLight.toArgb(), PrayedGold.toArgb(), GoldDeep.toArgb()),
                null,
                Shader.TileMode.CLAMP
            )
        })
        square.inset(s * 0.035f, s * 0.035f)
        canvas.drawRoundRect(square, corner * 0.8f, corner * 0.8f, stroke(Color.White, 0.35f, s * 0.015f))
        canvas.restoreToCount(save)
        val text = level.toString()
        drawText(context, canvas, text, c, c, s * if (text.length > 2) 0.25f else 0.33f, GoldInk, R.font.inter_bold, s * 0.56f)
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

    // ── Helpers ───────────────────────────────────────────────────────────

    /** The pixels per dp for a part [largestDp] across, held to [MAX_PX] whatever the density. */
    private fun density(context: Context, largestDp: Int): Float =
        min(context.resources.displayMetrics.density, MAX_PX.toFloat() / largestDp)

    /** The dark of [color], for what's written on it in full. */
    private fun inkOn(color: Color) = Color(color.red * 0.22f, color.green * 0.2f, color.blue * 0.16f, 1f)

    private fun fill(color: Color, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb(color, alpha) }

    private fun stroke(color: Color, alpha: Float, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = argb(color, alpha)
    }

    private fun drawIcon(context: Context, canvas: Canvas, @DrawableRes icon: Int, x: Float, y: Float, size: Float, tint: Color) {
        ContextCompat.getDrawable(context, icon)?.mutate()?.let { drawable ->
            val half = size / 2f
            drawable.setBounds((x - half).toInt(), (y - half).toInt(), (x + half).toInt(), (y + half).toInt())
            drawable.setTint(tint.toArgb())
            drawable.draw(canvas)
        }
    }

    /** [text] centred on ([x], [y]), shrunk to fit [maxWidth]. */
    private fun drawText(
        context: Context,
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Color,
        @FontRes font: Int,
        maxWidth: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { ResourcesCompat.getFont(context, font) }.getOrNull() ?: Typeface.DEFAULT_BOLD
            textSize = size
            textAlign = Paint.Align.CENTER
            this.color = color.toArgb()
        }
        val measured = paint.measureText(text)
        if (measured > maxWidth) paint.textSize = size * maxWidth / measured
        val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        canvas.drawText(text, x, y + bounds.height() / 2f - bounds.bottom, paint)
    }

    private fun argb(color: Color, alpha: Float): Int = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
}
