package com.ybugmobile.waktiva.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.prayerlog.LogColors
import com.ybugmobile.waktiva.ui.prayerlog.lighten
import com.ybugmobile.waktiva.ui.theme.darken
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate

/**
 * The prayer log widget ([WaktivaPrayerLogWidget]): the çetele as a game, as on the prayer log
 * screen and in its materials. The level on a brass sphere in a groove filling with how far the
 * level has come, the XP bar with what the next level takes, the streak and the badges earned,
 * and today's five prayers as glass spheres: brass once marked, ringed in their colour while
 * their time is on, rose once missed. A tap on one whose
 * time has come marks it or takes the mark back, as the day circle widgets do.
 */
internal object PrayerLogWidget {

    private const val MEDAL_DP = 52
    private const val DOT_DP = 42
    private const val MAX_PX = 300

    private const val REQUEST_TOGGLE = 7_320
    private const val REQUEST_OPEN = 7_330

    /** What every instance shows, read once per redraw. */
    private class LogToday(
        val progress: PrayerLogProgress,
        val streak: Int,
        val date: LocalDate,
        val prayers: List<Pair<PrayerType, PrayerLogStatus>>
    )

    suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray, snapshot: WaktivaWidget.Snapshot) {
        if (ids.isEmpty()) return
        val ep = WaktivaWidget.entryPoint(context)
        val enabled = ep.settingsManager().settingsFlow.first().prayerLogEnabled
        val today = if (enabled) load(context, snapshot) else null
        ids.forEach { id -> manager.updateAppWidget(id, build(context, snapshot, enabled, today)) }
    }

    private suspend fun load(context: Context, snapshot: WaktivaWidget.Snapshot): LogToday? {
        val date = snapshot.today
        val day = snapshot.days.find { it.date == date } ?: return null
        val next = snapshot.days.find { it.date == date.plusDays(1) }
        val log = WaktivaWidget.entryPoint(context).prayerLogRepository()
        val prayed = log.getPrayedPrayers().first()
        val trackedSince = PrayerLog.trackedSince(log.getStartDate().first(), date)
        val marked = prayed[date].orEmpty()
        val prayers = LoggedPrayers.map { type ->
            type to PrayerLog.status(
                date = date,
                type = type,
                prayed = type in marked,
                now = snapshot.now,
                start = day.timings[type]?.atDate(date),
                end = PrayerLog.windowEnd(type, day, next),
                trackedSince = trackedSince
            )
        }
        return LogToday(PrayerLogProgress.of(prayed), PrayerLog.streak(prayed, date), date, prayers)
    }

    private fun build(context: Context, snapshot: WaktivaWidget.Snapshot, enabled: Boolean, today: LogToday?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer_log)
        views.setOnClickPendingIntent(android.R.id.background, openLogIntent(context))
        views.setImageViewBitmap(
            R.id.widget_bg,
            WaktivaWidget.litBackground(snapshot.skyColors, LogColors.Gold.toArgb(), Backdrop.WIDE, snapshot.atmosphere)
        )
        views.setViewVisibility(R.id.widget_content, if (today != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_log_off, if (!enabled) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_empty, if (enabled && today == null) View.VISIBLE else View.GONE)
        if (today == null) return views

        val locale = context.resources.configuration.locales[0]
        val progress = today.progress
        val fraction = progress.levelXp.toFloat() / progress.levelSpan
        views.setImageViewBitmap(R.id.widget_log_medal, medal(context, progress.level, fraction))
        views.setTextViewText(R.id.widget_log_level, context.getString(R.string.prayer_log_level, progress.level))
        views.setTextViewText(
            R.id.widget_log_xp,
            context.getString(R.string.prayer_log_xp, NumberFormat.getIntegerInstance(locale).format(progress.xp))
        )
        views.setProgressBar(R.id.widget_log_bar, 1000, (fraction * 1000).toInt(), false)
        views.setTextViewText(
            R.id.widget_log_next,
            context.getString(R.string.prayer_log_xp_to_next, progress.levelSpan - progress.levelXp, progress.level + 1)
        )
        views.setTextViewText(R.id.widget_log_streak, "🔥 ${today.streak}")
        views.setTextViewText(R.id.widget_log_badges, "🏅 ${progress.earned.size}/${progress.badges.size}")

        views.removeAllViews(R.id.widget_log_prayers)
        today.prayers.forEach { (type, status) ->
            val cell = RemoteViews(context.packageName, R.layout.widget_log_prayer)
            cell.setImageViewBitmap(R.id.widget_log_dot, dot(context, type, status))
            cell.setTextViewText(R.id.widget_log_name, type.getDisplayName(context))
            cell.setTextColor(
                R.id.widget_log_name,
                when (status) {
                    PrayerLogStatus.PRAYED -> LogColors.GoldText.toArgb()
                    PrayerLogStatus.UPCOMING -> 0x73FFFFFF
                    else -> 0xCCFFFFFF.toInt()
                }
            )
            val name = type.getPrayerName(context)
            if (status != PrayerLogStatus.UPCOMING) {
                cell.setOnClickPendingIntent(R.id.widget_log_cell, DayWidgets.toggleIntent(context, today.date, type, REQUEST_TOGGLE))
                cell.setContentDescription(
                    R.id.widget_log_cell,
                    if (status == PrayerLogStatus.PRAYED) "$name. ${context.getString(R.string.prayer_log_unmark)}"
                    else "${context.getString(R.string.prayer_log_mark)}: $name"
                )
            }
            views.addView(R.id.widget_log_prayers, cell)
        }
        return views
    }

    private fun openLogIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_OPEN_SCREEN, NotificationHelper.SCREEN_PRAYER_LOG)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // ── Painted parts ─────────────────────────────────────────────────────
    // As the prayer log screen draws them (see PrayerLogStyle): the home dial's brass and gold,
    // glass spheres lit from the upper left, rings lying in a groove.

    private fun px(context: Context, dp: Int) =
        (dp * context.resources.displayMetrics.density).toInt().coerceIn(1, MAX_PX)

    /** The level on a sphere of brass, in a groove that fills with how far the level has come. */
    private fun medal(context: Context, level: Int, fraction: Float): Bitmap {
        val size = px(context, MEDAL_DP)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dp = context.resources.displayMetrics.density
        val s = size.toFloat()
        val c = s / 2f
        val stroke = s * 0.07f
        val inset = stroke / 2f + 0.8f * dp
        val arc = RectF(inset, inset, s - inset, s - inset)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        ring.strokeWidth = stroke + 1.6f * dp
        ring.color = argb(Color.Black, 0.28f)
        canvas.drawArc(arc, 0f, 360f, false, ring)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            ring.strokeWidth = stroke
            ring.strokeCap = Paint.Cap.ROUND
            ring.color = LogColors.Brass.toArgb()
            canvas.drawArc(arc, -90f, 360f * f, false, ring)
            val glaze = inset - stroke * 0.22f
            ring.strokeWidth = maxOf(0.8f * dp, stroke * 0.22f)
            ring.color = argb(Color.White, 0.35f)
            canvas.drawArc(RectF(glaze, glaze, s - glaze, s - glaze), -90f, 360f * f, false, ring)
        }

        val r = c - stroke - 3f * dp
        orb(canvas, c, c, r, LogColors.Brass, LogColors.Gold, LogColors.BrassLight.copy(alpha = 0.7f), shadow = true, dp = dp)

        val text = level.toString()
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LogColors.Ink.toArgb()
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.ibm_plex_arabic_medium) }.getOrNull()
                ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            textSize = r * if (text.length > 2) 0.72f else 0.95f
        }
        canvas.drawText(text, c, c - (label.ascent() + label.descent()) / 2f, label)
        return bitmap
    }

    /**
     * A prayer as a glass sphere: brass with a tick once prayed, deep glass ringed in its own
     * colour with its sign while its time is on, dimmed with a rose rim once missed, faint before.
     */
    private fun dot(context: Context, type: PrayerType, status: PrayerLogStatus): Bitmap {
        val size = px(context, DOT_DP)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dp = context.resources.displayMetrics.density
        val c = size / 2f
        // Room around the sphere for its glow and shadow.
        val r = c * 0.78f
        val accent = type.accentColor
        val edge = LogColors.Gold.copy(alpha = 0.55f)

        val (icon, tint) = when (status) {
            PrayerLogStatus.PRAYED -> {
                glow(canvas, c, c, c, LogColors.Gold, 0.45f)
                orb(canvas, c, c, r, LogColors.Brass, LogColors.Gold, LogColors.BrassLight.copy(alpha = 0.7f), shadow = true, dp = dp)
                R.drawable.ic_widget_check to LogColors.Ink
            }
            PrayerLogStatus.ACTIVE -> {
                glow(canvas, c, c, c, accent, 0.3f)
                orb(canvas, c, c, r, accent, lerp(LogColors.Night, accent, 0.35f), edge, shadow = true, dp = dp)
                type.iconRes to Color.White.copy(alpha = 0.96f)
            }
            PrayerLogStatus.MISSED -> {
                orb(canvas, c, c, r, LogColors.Rose.darken(0.15f), lerp(LogColors.Night, LogColors.Rose, 0.2f), edge, shadow = true, dp = dp)
                type.iconRes to LogColors.Rose.lighten(0.25f)
            }
            PrayerLogStatus.UPCOMING, PrayerLogStatus.UNTRACKED -> {
                orb(canvas, c, c, r, Color.White.copy(alpha = 0.28f), LogColors.Night.copy(alpha = 0.45f), null, shadow = false, dp = dp)
                type.iconRes to Color.White.copy(alpha = 0.4f)
            }
        }
        ContextCompat.getDrawable(context, icon)?.mutate()?.let { drawable ->
            val half = r * 0.5f
            if (status == PrayerLogStatus.ACTIVE) {
                drawable.setTint(argb(Color.Black, 0.35f))
                drawable.setBounds((c - half).toInt(), (c - half + 0.6f * dp).toInt(), (c + half).toInt(), (c + half + 0.6f * dp).toInt())
                drawable.draw(canvas)
            }
            drawable.setTint(tint.toArgb())
            drawable.setBounds((c - half).toInt(), (c - half).toInt(), (c + half).toInt(), (c + half).toInt())
            drawable.draw(canvas)
        }
        return bitmap
    }

    /**
     * A glass sphere of radius [r] at ([x], [y]), as the screen's glassOrb: a shadow under it, a
     * rim in [rim] bright towards the light, [fill] inside paler towards it, a gleam across that
     * side and a fine [edge].
     */
    private fun orb(canvas: Canvas, x: Float, y: Float, r: Float, rim: Color, fill: Color, edge: Color?, shadow: Boolean, dp: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (shadow) {
            val shade = r + 3f * dp
            paint.shader = RadialGradient(
                x, y + 1.5f * dp, shade,
                intArrayOf(argb(Color.Black, 0.4f * rim.alpha), 0),
                floatArrayOf(0.6f * r / shade, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y + 1.5f * dp, shade, paint)
        }
        paint.shader = LinearGradient(
            x + LIGHT_X * r, y + LIGHT_Y * r, x - LIGHT_X * r, y - LIGHT_Y * r,
            intArrayOf(rim.lighten(0.45f).toArgb(), rim.toArgb(), rim.darken(0.35f).toArgb()),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, r, paint)

        val inner = r - maxOf(1.6f * dp, r * 0.09f)
        paint.shader = RadialGradient(
            x + LIGHT_X * inner * 0.45f, y + LIGHT_Y * inner * 0.45f, inner * 1.25f,
            intArrayOf(fill.lighten(0.38f).toArgb(), fill.lighten(0.1f).toArgb(), fill.darken(0.3f).toArgb()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, inner, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = argb(Color.Black, 0.3f * fill.alpha)
        canvas.drawCircle(x, y, inner, paint)
        paint.style = Paint.Style.FILL

        val save = canvas.save()
        canvas.clipPath(Path().apply { addCircle(x, y, inner, Path.Direction.CW) })
        val gx = x + LIGHT_X * inner * 0.45f
        val gy = y + LIGHT_Y * inner * 0.45f
        paint.shader = LinearGradient(
            gx, gy - inner * 0.4f, gx, gy + inner * 0.4f,
            argb(Color.White, 0.42f * maxOf(fill.alpha, 0.5f)), 0, Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(gx - inner * 0.62f, gy - inner * 0.38f, gx + inner * 0.62f, gy + inner * 0.38f), paint)
        canvas.restoreToCount(save)
        paint.shader = null

        edge?.let {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.7f * dp
            paint.color = it.toArgb()
            canvas.drawCircle(x, y, r + 0.35f * dp, paint)
        }
    }

    /** A soft light of [color] around ([x], [y]), out to [radius]. */
    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Color, alpha: Float) {
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                x, y, radius,
                intArrayOf(argb(color, alpha), argb(color, alpha), 0),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        })
    }

    private fun argb(color: Color, alpha: Float): Int = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()

    /** The light falls from the upper left, as on the home screen's dials. */
    private const val LIGHT_X = -0.7071f
    private const val LIGHT_Y = -0.7071f
}
