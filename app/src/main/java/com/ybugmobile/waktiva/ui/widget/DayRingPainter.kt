package com.ybugmobile.waktiva.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.gear.dayAngle
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the 4×4 widget's day circle shows: [day]'s prayers round a 24-hour dial (midnight at the
 * bottom, noon at the top, as on the home screen), the [current] prayer's stretch of the ring lit
 * up to [now], and the [prayed] badges ringed in gold.
 */
internal class DayRing(
    val day: PrayerDay,
    val now: LocalTime,
    val current: PrayerType,
    val prayed: Set<PrayerType>,
    val rtl: Boolean
)

/**
 * Paints a [DayRing] as a quiet companion to the widget's countdown: the ring a faint track in
 * the prayer colours, only the current prayer's stretch in full colour, filled up to now, so the
 * eye goes to where the day is. The badges are flat, the current one raised, and a point of light
 * marks now. RemoteViews can't hold a composable, so it's drawn on an Android canvas.
 */
internal object DayRingPainter {

    fun render(context: Context, ring: DayRing, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dp = context.resources.displayMetrics.density
        val s = sizePx.toFloat()
        val c = s / 2f
        val badge = s * 0.06f
        val badgeCurrent = badge * 1.25f
        val track = c - badgeCurrent - 4f * dp
        val width = s * 0.028f
        val oval = RectF(c - track, c - track, c + track, c + track)

        fun point(radius: Float, angle: Float) = floatArrayOf(c + radius * cos(angle), c + radius * sin(angle))
        fun minutes(t: LocalTime) = t.hour * 60f + t.minute

        val prayers = PrayerType.entries.mapNotNull { type -> ring.day.timings[type]?.let { type to it } }.sortedBy { it.second }
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
        }

        // The track: each prayer's stretch faint in its colour, the current one in full, lit to now.
        prayers.forEachIndexed { i, (type, time) ->
            val start = dayAngle(minutes(time), ring.rtl)
            val sweep = sweep(start, dayAngle(minutes(prayers[(i + 1) % prayers.size].second), ring.rtl), ring.rtl)
            val gap = 0.03f * Math.signum(sweep)
            val color = type.accentColor
            arc.color = argb(color, if (type == ring.current) 0.35f else 0.16f)
            canvas.drawArc(oval, deg(start + gap), deg(sweep - 2 * gap), false, arc)
            if (type == ring.current) {
                val elapsed = sweep(start, dayAngle(minutes(ring.now), ring.rtl), ring.rtl)
                    .let { if (Math.abs(it) > Math.abs(sweep)) sweep else it }
                if (Math.abs(elapsed) > Math.abs(gap)) {
                    arc.color = color.toArgb()
                    canvas.drawArc(oval, deg(start + gap), deg(elapsed - gap), false, arc)
                }
            }
        }

        // Now: a point of light on the track.
        val (nx, ny) = point(track, dayAngle(minutes(ring.now), ring.rtl))
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(nx, ny, width * 1.15f, dot.apply { color = android.graphics.Color.WHITE })

        // The badges: flat discs in the prayer colours, a prayed one ringed in gold.
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        prayers.forEach { (type, time) ->
            val isCurrent = type == ring.current
            val r = if (isCurrent) badgeCurrent else badge
            val (x, y) = point(track, dayAngle(minutes(time), ring.rtl))
            val color = type.accentColor
            val isPast = !isCurrent && !time.isAfter(ring.now)

            canvas.drawCircle(x, y, r + 1.5f * dp, dot.apply { this.color = argb(Color.Black, 0.35f) })
            canvas.drawCircle(x, y, r, dot.apply { this.color = argb(color, if (isCurrent || !isPast) 1f else 0.55f) })
            if (type in ring.prayed) {
                canvas.drawCircle(x, y, r + 2.5f * dp, stroke.apply { this.color = PrayedGold.toArgb(); strokeWidth = 1.8f * dp })
            }

            val icon = ContextCompat.getDrawable(context, type.iconRes)?.mutate() ?: return@forEach
            val half = r * 0.55f
            icon.setTint(if (color.luminance() > 0.5f) argb(Color.Black, 0.7f) else android.graphics.Color.WHITE)
            icon.setBounds((x - half).toInt(), (y - half).toInt(), (x + half).toInt(), (y + half).toInt())
            icon.draw(canvas)
        }

        // The current prayer's sign, faint, holding the middle of the ring.
        ContextCompat.getDrawable(context, ring.current.iconRes)?.mutate()?.let { icon ->
            val half = s * 0.11f
            icon.setTint(argb(ring.current.accentColor, 0.22f))
            icon.setBounds((c - half).toInt(), (c - half).toInt(), (c + half).toInt(), (c + half).toInt())
            icon.draw(canvas)
        }
        return bitmap
    }

    /** The turn from [from] to [to] along the dial's direction of time. */
    private fun sweep(from: Float, to: Float, rtl: Boolean): Float {
        var sweep = to - from
        if (rtl) {
            if (sweep > 0f) sweep -= TWO_PI
        } else if (sweep < 0f) {
            sweep += TWO_PI
        }
        return sweep
    }

    private fun deg(radians: Float) = Math.toDegrees(radians.toDouble()).toFloat()

    private fun argb(color: Color, alpha: Float): Int = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()

    private const val TWO_PI = (2 * Math.PI).toFloat()
}
