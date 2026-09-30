package com.ybugmobile.waktiva.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.home.composables.PrayerWeather
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.gear.GearLight
import com.ybugmobile.waktiva.ui.home.composables.gear.WeatherTone
import com.ybugmobile.waktiva.ui.home.composables.gear.dayAngle
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.home.composables.skySweep
import com.ybugmobile.waktiva.ui.home.composables.weatherIconRes
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * What the widgets' day circle shows: [day]'s prayers round a 24-hour dial (midnight at the
 * bottom, noon at the top, as on the home screen), the hand at [now], the [current] prayer's
 * badge raised, the [prayed] ones glowing gold as the prayer log marks them, and the sky of each
 * hour inside the ring in its weather ([hourWeather], null for the plain light of the day).
 *
 * With [prayerWeather], the large circle also puts each prayer's weather inside the ring, below
 * its badge.
 */
internal class DayRing(
    val day: PrayerDay,
    val now: LocalTime,
    val current: PrayerType?,
    val prayed: Set<PrayerType>,
    val hourWeather: List<WeatherCondition?>,
    val prayerWeather: Map<PrayerType, PrayerWeather>,
    val rtl: Boolean
)

/**
 * Paints a [DayRing] the way the home screen draws its classic circle: the day's sky in a disc,
 * a slim enamel ring in the prayer colours between gold hairlines, lifted off the widget by a
 * warm halo, and each prayer as a flat enamel badge on it. RemoteViews can't hold a composable,
 * so this redraws it on an Android canvas.
 *
 * [large] is the 4×4 widget's circle, with finer badges and the prayers' weather; the 4×2
 * circle's badges are larger so they read at its size. The middle is left clear for the views
 * the widget lays over it.
 */
internal object DayRingPainter {

    fun render(context: Context, ring: DayRing, sizePx: Int, large: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dp = context.resources.displayMetrics.density
        val g = Geometry(sizePx.toFloat(), large)

        val badgeTone: (PrayerType) -> WeatherTone = { type ->
            ring.day.timings[type]?.let { ring.hourWeather.getOrNull(it.hour) }
                ?.let { WeatherTone.forPrayers(it) } ?: WeatherTone.None
        }
        val prayers = PrayerType.entries.mapNotNull { type ->
            ring.day.timings[type]?.let { time -> Badge(type, time, badgeTone(type)(type.accentColor)) }
        }
        val currentColor = prayers.firstOrNull { it.type == ring.current }?.color ?: Color.White

        drawSky(canvas, g, ring)
        drawTrack(canvas, g, prayers, currentColor, ring.rtl, dp)
        drawHand(canvas, g, ring, currentColor, dp)
        prayers.forEach { badge ->
            val at = g.point(g.track, dayAngle(badge.minutes, ring.rtl))
            val isCurrent = badge.type == ring.current
            drawBadge(context, canvas, badge, at, if (isCurrent) g.badgeCurrent else g.badge, isCurrent, badge.type in ring.prayed, dp)
        }
        if (large) drawPrayerWeather(context, canvas, g, prayers, ring)
        return bitmap
    }

    private class Badge(val type: PrayerType, val time: LocalTime, val color: Color) {
        val minutes = time.hour * 60f + time.minute
    }

    /** The ring's proportions for a square [s] pixels across. */
    private class Geometry(val s: Float, large: Boolean) {
        val cx = s / 2f
        val cy = s / 2f
        val badge = s * if (large) 0.056f else 0.072f
        val badgeCurrent = badge * 1.18f
        val track = s / 2f - badgeCurrent - s * 0.035f
        val trackWidth = s * if (large) 0.03f else 0.036f
        val skyRadius = track - trackWidth / 2f

        fun point(radius: Float, angle: Float) = floatArrayOf(cx + radius * cos(angle), cy + radius * sin(angle))
    }

    // ── The sky ─────────────────────────────────────────────────────────────

    /**
     * The day's colours round the dial with a dome of shade over the middle, where the widget's
     * text sits, a few stars in clear night hours, rain or snow in the hours that have it, and an
     * edge fading into the widget.
     */
    private fun drawSky(canvas: Canvas, g: Geometry, ring: DayRing) {
        val r = g.skyRadius
        val layer = canvas.saveLayer(0f, 0f, g.s, g.s, null)

        val sweep = skySweep(ring.day, { ring.hourWeather.getOrNull(it) }, ring.rtl)
        canvas.drawCircle(g.cx, g.cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(g.cx, g.cy, sweep.map { it.second.toArgb() }.toIntArray(), sweep.map { it.first }.toFloatArray())
        })
        canvas.drawCircle(g.cx, g.cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                g.cx, g.cy, r,
                intArrayOf(argb(DomeShade, 0.62f), argb(DomeShade, 0.2f), argb(Color.White, 0.06f), argb(Color.White, 0.02f)),
                floatArrayOf(0f, 0.6f, 0.9f, 1f),
                Shader.TileMode.CLAMP
            )
        })

        val sunrise = ring.day.timings[PrayerType.SUNRISE]
        val maghrib = ring.day.timings[PrayerType.MAGHRIB]
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = g.s * 0.006f }
        for (hour in 0 until 24) {
            val weather = ring.hourWeather.getOrNull(hour)
            val middle = LocalTime.of(hour, 30)
            val isNight = sunrise != null && maghrib != null && (middle.isBefore(sunrise) || !middle.isBefore(maghrib))
            val rnd = Random(hour * 7919 + 17)
            when {
                weather in Rain -> repeat(4) {
                    val (x, y) = g.point(r * (0.45f + 0.45f * rnd.nextFloat()), dayAngle(hour * 60f + rnd.nextFloat() * 60f, ring.rtl))
                    val length = g.s * (0.022f + 0.014f * rnd.nextFloat())
                    streak.color = argb(RainColor, 0.4f + 0.3f * rnd.nextFloat())
                    canvas.drawLine(x, y, x - length * 0.3f, y + length, streak)
                }
                weather in Snow -> repeat(5) {
                    val (x, y) = g.point(r * (0.45f + 0.45f * rnd.nextFloat()), dayAngle(hour * 60f + rnd.nextFloat() * 60f, ring.rtl))
                    dot.color = argb(Color.White, 0.55f + 0.35f * rnd.nextFloat())
                    canvas.drawCircle(x, y, g.s * (0.004f + 0.004f * rnd.nextFloat()), dot)
                }
                isNight && (weather == null || weather in ClearSkies) -> repeat(3) {
                    val (x, y) = g.point(r * (0.4f + 0.45f * rnd.nextFloat()), dayAngle(hour * 60f + rnd.nextFloat() * 60f, ring.rtl))
                    dot.color = argb(Color.White, 0.35f + 0.45f * rnd.nextFloat())
                    canvas.drawCircle(x, y, g.s * (0.002f + 0.0028f * rnd.nextFloat()), dot)
                }
            }
        }

        canvas.drawCircle(g.cx, g.cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = RadialGradient(
                g.cx, g.cy, r,
                intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT),
                floatArrayOf(0f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
        })
        canvas.restoreToCount(layer)
    }

    // ── The ring ────────────────────────────────────────────────────────────

    /** A warm halo, the dark channel, the prayer colours as enamel, a sheen and gold hairlines. */
    private fun drawTrack(canvas: Canvas, g: Geometry, prayers: List<Badge>, currentColor: Color, rtl: Boolean, dp: Float) {
        val track = g.track
        val width = g.trackWidth
        val half = width / 2f

        // The halo, fading out on both sides of the ring.
        val haloWidth = track * 0.22f
        val outer = track + haloWidth
        val haloColor = lerp(WarmHalo, currentColor, 0.3f)
        val falloff = listOf(0f to 1f, 0.25f to 0.68f, 0.5f to 0.34f, 0.75f to 0.1f, 1f to 0f)
        val stops = falloff.flatMap { (at, share) ->
            val glow = argb(haloColor, 0.18f * share)
            listOf(max(0f, track - at * haloWidth) / outer to glow, (track + at * haloWidth) / outer to glow)
        }.distinctBy { it.first }.sortedBy { it.first }
        canvas.drawCircle(g.cx, g.cy, outer, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(g.cx, g.cy, outer, stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
        })

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        canvas.drawCircle(g.cx, g.cy, track, stroke.apply { color = 0xCC1A1205.toInt(); strokeWidth = width + 1.6f * dp })

        val oval = RectF(g.cx - track, g.cy - track, g.cx + track, g.cy + track)
        val sorted = prayers.sortedBy { it.minutes }
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
        }
        sorted.forEachIndexed { i, from ->
            val to = sorted[(i + 1) % sorted.size]
            val start = dayAngle(from.minutes, rtl)
            var sweep = dayAngle(to.minutes, rtl) - start
            if (rtl) {
                if (sweep > 0f) sweep -= TWO_PI
            } else if (sweep < 0f) {
                sweep += TWO_PI
            }
            val gap = 0.012f * sign(sweep)
            val (x0, y0) = g.point(track, start)
            val (x1, y1) = g.point(track, start + sweep)
            arc.shader = LinearGradient(x0, y0, x1, y1, argb(from.color, 0.95f), argb(to.color, 0.95f), Shader.TileMode.CLAMP)
            canvas.drawArc(oval, Math.toDegrees((start + gap).toDouble()).toFloat(), Math.toDegrees((sweep - 2 * gap).toDouble()).toFloat(), false, arc)
        }

        // Specular light from the upper left, so the enamel reads as glazed.
        val (lx, ly) = g.point(track, GearLight.DEFAULT_ANGLE)
        canvas.drawCircle(g.cx, g.cy, track, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            shader = RadialGradient(
                lx, ly, track * 1.1f,
                intArrayOf(argb(Color.White, 0.32f), argb(Color.White, 0.08f), argb(Color.Black, 0.22f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        })

        stroke.shader = null
        stroke.color = argb(Gold, 0.7f)
        stroke.strokeWidth = 0.9f * dp
        canvas.drawCircle(g.cx, g.cy, track + half, stroke)
        canvas.drawCircle(g.cx, g.cy, track - half, stroke)
        stroke.color = 0x66FFF1C8
        stroke.strokeWidth = 0.5f * dp
        canvas.drawCircle(g.cx, g.cy, track + half - 0.5f * dp, stroke)
        canvas.drawCircle(g.cx, g.cy, track - half + 0.5f * dp, stroke)
    }

    /** The hand at [DayRing.now], from clear of the text in the middle out to a point of light on the track. */
    private fun drawHand(canvas: Canvas, g: Geometry, ring: DayRing, color: Color, dp: Float) {
        val angle = dayAngle(ring.now.hour * 60f + ring.now.minute, ring.rtl)
        val (rx, ry) = g.point(g.track * 0.62f, angle)
        val (tx, ty) = g.point(g.track - g.s * 0.03f, angle)
        canvas.drawLine(rx, ry, tx, ty, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1.5f * dp
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(rx, ry, tx, ty, argb(Color.White, 0f), argb(Color.White, 0.8f), Shader.TileMode.CLAMP)
        })

        val (px, py) = g.point(g.track, angle)
        val glow = g.s * 0.045f
        canvas.drawCircle(px, py, glow, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(px, py, glow, argb(color, 0.5f), argb(color, 0f), Shader.TileMode.CLAMP)
        })
        canvas.drawCircle(px, py, g.s * 0.011f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
        canvas.drawCircle(px, py, g.s * 0.019f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
            this.color = color.toArgb()
        })
    }

    // ── The badges ──────────────────────────────────────────────────────────

    /**
     * A prayer's flat enamel badge in a gold hairline; the current one raised in a halo of its
     * colour, and a prayed one in the gold bloom and double rim the home screen gives it.
     */
    private fun drawBadge(context: Context, canvas: Canvas, badge: Badge, at: FloatArray, r: Float, isCurrent: Boolean, isPrayed: Boolean, dp: Float) {
        val (x, y) = at
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val tone = lerp(badge.color, PrayedGold, 0.6f)

        if (isCurrent) {
            val outer = r * 2.2f
            canvas.drawCircle(x, y, outer, fill.apply {
                shader = RadialGradient(
                    x, y, outer,
                    intArrayOf(argb(badge.color, 0.4f), argb(badge.color, 0.4f), argb(badge.color, 0f)),
                    floatArrayOf(0f, 0.27f, 1f),
                    Shader.TileMode.CLAMP
                )
            })
            fill.shader = null
        }
        if (isPrayed) {
            val outer = r * 2.3f
            canvas.drawCircle(x, y, outer, fill.apply {
                shader = RadialGradient(
                    x, y, outer,
                    intArrayOf(0, 0, argb(tone, 0.9f), argb(tone, 0.32f), 0),
                    floatArrayOf(0f, 0.92f * r / outer, 1.04f * r / outer, 1.5f * r / outer, 1f),
                    Shader.TileMode.CLAMP
                )
            })
            fill.shader = null
        }

        canvas.drawCircle(x, y, r + 1.2f * dp, fill.apply { color = argb(Color.Black, 0.3f) })
        canvas.drawCircle(x, y, r, fill.apply { color = badge.color.toArgb() })
        canvas.drawCircle(x, y, r, stroke.apply { color = argb(Gold, 0.85f); strokeWidth = 1f * dp })
        canvas.drawCircle(x, y, r - 1f * dp, stroke.apply { color = 0x66FFF1C8; strokeWidth = 0.5f * dp })

        if (isPrayed) {
            canvas.drawCircle(x, y, r + 1f * dp, stroke.apply { color = argb(Color.White, 0.95f); strokeWidth = 1.6f * dp })
            canvas.drawCircle(x, y, r + 3.2f * dp, stroke.apply { color = tone.toArgb(); strokeWidth = 1.2f * dp })
        }

        val icon = ContextCompat.getDrawable(context, badge.type.iconRes)?.mutate() ?: return
        val half = r * (if (isCurrent) 1.15f else 1.1f) / 2f
        icon.setTint(if (badge.color.luminance() > 0.5f) argb(Color.Black, 0.7f) else android.graphics.Color.WHITE)
        icon.setBounds((x - half).toInt(), (y - half).toInt(), (x + half).toInt(), (y + half).toInt())
        icon.draw(canvas)
    }

    /** Each prayer's weather, inside the ring below its badge; those gone by fade back. */
    private fun drawPrayerWeather(context: Context, canvas: Canvas, g: Geometry, prayers: List<Badge>, ring: DayRing) {
        val size = g.s * 0.075f
        val distance = g.track - g.badgeCurrent - g.s * 0.02f - size / 2f
        val shade = Paint(Paint.ANTI_ALIAS_FLAG)
        prayers.forEach { badge ->
            val weather = ring.prayerWeather[badge.type] ?: return@forEach
            val (x, y) = g.point(distance, dayAngle(badge.minutes, ring.rtl))
            val shadow = size * 0.75f
            canvas.drawCircle(x, y, shadow, shade.apply {
                shader = RadialGradient(
                    x, y, shadow,
                    intArrayOf(argb(Color.Black, 0.28f), argb(Color.Black, 0.28f), 0),
                    floatArrayOf(0f, 0.3f, 1f),
                    Shader.TileMode.CLAMP
                )
            })
            val icon = ContextCompat.getDrawable(context, weatherIconRes(weather.condition, weather.isDay))?.mutate() ?: return@forEach
            icon.alpha = if (weather.isPast) 115 else 255
            val half = size / 2f
            icon.setBounds((x - half).toInt(), (y - half).toInt(), (x + half).toInt(), (y + half).toInt())
            icon.draw(canvas)
        }
    }

    private fun argb(color: Color, alpha: Float): Int = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()

    private const val TWO_PI = (2 * Math.PI).toFloat()

    private val DomeShade = Color(0xFF050816)
    private val WarmHalo = Color(0xFFFFE7B0)
    private val Gold = Color(0xFFDEBE78)
    private val RainColor = Color(0xFFBFD9F2)

    private val ClearSkies = setOf(WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR)
    private val Rain = setOf(
        WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.RAINY, WeatherCondition.HEAVY_RAIN,
        WeatherCondition.FREEZING_RAIN, WeatherCondition.RAIN_SHOWERS, WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_HAIL
    )
    private val Snow = setOf(WeatherCondition.SNOWY, WeatherCondition.HEAVY_SNOW, WeatherCondition.SNOW_GRAINS, WeatherCondition.SNOW_SHOWERS)
}
