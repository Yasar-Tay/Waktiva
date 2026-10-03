package com.ybugmobile.waktiva.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.MissedRed
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate

/**
 * The prayer log widget ([WaktivaPrayerLogWidget]): the çetele as a game, as on the prayer log
 * screen. The level's gold medal ringed by how far the level has come, the XP bar with what the
 * next level takes, the streak and the badges earned, and today's five prayers: gold once
 * marked, ringed in their colour while their time is on, red once missed. A tap on one whose
 * time has come marks it or takes the mark back, as the day circle widgets do.
 */
internal object PrayerLogWidget {

    private const val MEDAL_DP = 56
    private const val DOT_DP = 30
    private const val MAX_PX = 300

    private const val REQUEST_TOGGLE = 7_320
    private const val REQUEST_OPEN = 7_330

    private val GoldLight = Color(0xFFFFECB3)
    private val GoldDeep = Color(0xFFE0A800)
    private val Ink = Color(0xFF3B2A00)

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
            WaktivaWidget.litBackground(snapshot.skyColors, PrayedGold.toArgb(), Backdrop.WIDE, snapshot.atmosphere)
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
                    PrayerLogStatus.PRAYED -> PrayedGold.toArgb()
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

    private fun px(context: Context, dp: Int) =
        (dp * context.resources.displayMetrics.density).toInt().coerceIn(1, MAX_PX)

    /** The level in a gold coin, ringed by how far the level has come. */
    private fun medal(context: Context, level: Int, fraction: Float): Bitmap {
        val size = px(context, MEDAL_DP)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()
        val c = s / 2f
        val stroke = s * 0.075f
        val arc = RectF(stroke / 2f, stroke / 2f, s - stroke / 2f, s - stroke / 2f)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        ring.color = 0x33FFFFFF
        canvas.drawArc(arc, 0f, 360f, false, ring)
        ring.color = PrayedGold.toArgb()
        canvas.drawArc(arc, -90f, 360f * fraction.coerceIn(0f, 1f), false, ring)

        val r = c - stroke - s * 0.05f
        val coin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                c - r, c - r, c + r, c + r,
                intArrayOf(GoldLight.toArgb(), PrayedGold.toArgb(), GoldDeep.toArgb()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(c, c, r, coin)
        canvas.drawCircle(c, c, r - s * 0.03f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.015f
            color = 0x59FFFFFF
        })

        val text = level.toString()
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ink.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = r * if (text.length > 2) 0.72f else 0.95f
        }
        canvas.drawText(text, c, c - (label.ascent() + label.descent()) / 2f, label)
        return bitmap
    }

    /** A prayer's mark: a gold coin with a tick once prayed, else its icon in a ring of its state. */
    private fun dot(context: Context, type: PrayerType, status: PrayerLogStatus): Bitmap {
        val size = px(context, DOT_DP)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()
        val c = s / 2f
        val stroke = s * 0.06f
        val r = c - stroke
        val accent = type.accentColor

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val (icon, tint) = when (status) {
            PrayerLogStatus.PRAYED -> {
                fill.shader = LinearGradient(
                    0f, 0f, s, s,
                    intArrayOf(GoldLight.toArgb(), PrayedGold.toArgb(), GoldDeep.toArgb()),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(c, c, r, fill)
                R.drawable.ic_widget_check to Ink
            }
            PrayerLogStatus.ACTIVE -> {
                fill.color = accent.copy(alpha = 0.22f).toArgb()
                ring.color = accent.toArgb()
                canvas.drawCircle(c, c, r, fill)
                canvas.drawCircle(c, c, r, ring)
                type.iconRes to accent
            }
            PrayerLogStatus.MISSED -> {
                fill.color = MissedRed.copy(alpha = 0.16f).toArgb()
                ring.color = MissedRed.copy(alpha = 0.75f).toArgb()
                canvas.drawCircle(c, c, r, fill)
                canvas.drawCircle(c, c, r, ring)
                type.iconRes to MissedRed.copy(alpha = 0.85f)
            }
            PrayerLogStatus.UPCOMING, PrayerLogStatus.UNTRACKED -> {
                ring.color = 0x40FFFFFF
                canvas.drawCircle(c, c, r, ring)
                type.iconRes to Color.White.copy(alpha = 0.4f)
            }
        }
        ContextCompat.getDrawable(context, icon)?.mutate()?.let { drawable ->
            val half = (r * 0.55f).toInt()
            drawable.setBounds((c - half).toInt(), (c - half).toInt(), (c + half).toInt(), (c + half).toInt())
            drawable.setTint(tint.toArgb())
            drawable.draw(canvas)
        }
        return bitmap
    }
}
