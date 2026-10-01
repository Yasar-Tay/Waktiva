package com.ybugmobile.waktiva.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.gear.dayAngle
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.home.composables.skyLight
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the 4×4 widget's day circle shows: [day]'s prayers round a 24-hour dial (midnight at the
 * bottom, noon at the top, as on the home screen), the [current] prayer's stretch of the ring lit
 * up to [now], the [prayed] ones in gold, and the moment's sphere ([hub]) at its heart.
 */
internal class DayRing(
    val day: PrayerDay,
    val now: LocalTime,
    val current: PrayerType,
    val prayed: Set<PrayerType>,
    val hub: WidgetArt.Orb,
    val rtl: Boolean
)

/**
 * Paints a [DayRing] in the classic circle's materials: an enamel channel between gold hairlines,
 * each prayer's stretch a faint wash of its colour and the current one in full up to now, the
 * prayers as glass spheres holding the sky of their hour, a point of light at now, and the
 * moment's sphere in the middle, which the widget makes the "I prayed" button.
 */
internal object DayRingPainter {

    fun render(context: Context, ring: DayRing, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dp = context.resources.displayMetrics.density
        val s = sizePx.toFloat()
        val c = s / 2f
        val badge = s * 0.062f
        val badgeCurrent = badge * 1.2f
        val track = c - badgeCurrent * 1.25f - 2f * dp
        val width = s * 0.034f
        val oval = RectF(c - track, c - track, c + track, c + track)

        fun point(radius: Float, angle: Float) = floatArrayOf(c + radius * cos(angle), c + radius * sin(angle))
        fun minutes(t: LocalTime) = t.hour * 60f + t.minute

        val prayers = PrayerType.entries.mapNotNull { type -> ring.day.timings[type]?.let { type to it } }.sortedBy { it.second }
        val currentColor = ring.current.accentColor

        // A warm halo off the ring, and its dark enamel channel.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val haloOuter = track + s * 0.09f
        paint.shader = RadialGradient(
            c, c, haloOuter,
            intArrayOf(0, argb(currentColor, 0.16f), 0),
            floatArrayOf((track - s * 0.09f) / haloOuter, track / haloOuter, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(c, c, haloOuter, paint)
        paint.shader = null

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        stroke.color = 0xB3101426.toInt()
        stroke.strokeWidth = width + 2f * dp
        canvas.drawCircle(c, c, track, stroke)

        // Each prayer's stretch: a wash of its colour, the current one in full up to now.
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
        }
        prayers.forEachIndexed { i, (type, time) ->
            val start = dayAngle(minutes(time), ring.rtl)
            val sweep = sweep(start, dayAngle(minutes(prayers[(i + 1) % prayers.size].second), ring.rtl), ring.rtl)
            val gap = 0.025f * Math.signum(sweep)
            arc.color = argb(type.accentColor, if (type == ring.current) 0.32f else 0.2f)
            canvas.drawArc(oval, deg(start + gap), deg(sweep - 2 * gap), false, arc)
            if (type == ring.current) {
                val elapsed = sweep(start, dayAngle(minutes(ring.now), ring.rtl), ring.rtl)
                    .let { if (Math.abs(it) > Math.abs(sweep)) sweep else it }
                if (Math.abs(elapsed) > Math.abs(gap)) {
                    arc.color = type.accentColor.toArgb()
                    canvas.drawArc(oval, deg(start + gap), deg(elapsed - gap), false, arc)
                }
            }
        }

        stroke.color = argb(Gold, 0.6f)
        stroke.strokeWidth = 0.8f * dp
        canvas.drawCircle(c, c, track + width / 2f + 0.5f * dp, stroke)
        canvas.drawCircle(c, c, track - width / 2f - 0.5f * dp, stroke)

        // Now: a point of light on the track.
        val (nx, ny) = point(track, dayAngle(minutes(ring.now), ring.rtl))
        paint.shader = RadialGradient(nx, ny, width * 1.8f, intArrayOf(argb(currentColor, 0.75f), 0), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(nx, ny, width * 1.8f, paint)
        paint.shader = null
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(nx, ny, width * 0.5f, paint)

        // The prayers, as glass spheres holding the sky of their hour.
        prayers.forEach { (type, time) ->
            val isCurrent = type == ring.current
            val (x, y) = point(track, dayAngle(minutes(time), ring.rtl))
            val orb = WidgetArt.Orb(
                color = type.accentColor,
                sky = skyLight(minutes(time), ring.day),
                icon = type.iconRes,
                prayed = type in ring.prayed,
                dim = !isCurrent && !time.isAfter(ring.now),
                raised = isCurrent
            )
            WidgetArt.drawOrb(context, canvas, x, y, if (isCurrent) badgeCurrent else badge, orb, dp)
        }

        // The moment's sphere at the heart of the ring.
        WidgetArt.drawOrb(context, canvas, c, c, s * 0.2f, ring.hub, dp)
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

    private val Gold = Color(0xFFDEBE78)

    private const val TWO_PI = (2 * Math.PI).toFloat()
}
