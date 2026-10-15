package com.ybugmobile.waktiva.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate

/**
 * The prayer log widget ([WaktivaPrayerLogWidget]): the çetele as a game, as on the prayer log
 * screen. The level in a gold diamond, the XP bar with what the next level takes, the streak and
 * the badges earned, and today's five prayers as tiles: gold once marked, ringed in their colour
 * while their time is on, ringed in red once missed. A tap on one whose time has come marks it or
 * takes the mark back, as the day circle widgets do. It sits on the sky of the hour, lit by the
 * prayer whose time it is, like the other widgets.
 */
internal object PrayerLogWidget {

    private const val REQUEST_TOGGLE = 7_320
    private const val REQUEST_OPEN = 7_330

    /** Below this height the widget is short: its tiles lower than the layout's 58 dp, its level smaller. */
    private const val TALL_MIN_HEIGHT_DP = 150f
    private const val SHORT_MIN_HEIGHT_DP = 100f
    private const val MIN_WIDTH_DP = 220f
    private const val SHORT_TILES_DP = 46f

    /**
     * The level's row at the widget's height: on a tall widget it's larger, to fill the height
     * the tiles leave it. Sizes in sp, the medal and the bar in dp.
     */
    private enum class Level(val medal: Int, val title: Float, val xp: Float, val next: Float, val chip: Float, val bar: Float) {
        SHORT(medal = 48, title = 15f, xp = 12f, next = 11f, chip = 12f, bar = 5f),
        TALL(medal = 60, title = 19f, xp = 14f, next = 13f, chip = 14f, bar = 7f)
    }

    private const val INK = 0xFF3B2A00.toInt()
    private const val FAINT = 0x66FFFFFF
    private const val MISSED_TEXT = 0xFFFCA5A5.toInt()

    /** What every instance shows, read once per redraw. */
    private class LogToday(
        val progress: PrayerLogProgress,
        val streak: Int,
        val date: LocalDate,
        val current: PrayerType,
        val prayers: List<Pair<PrayerType, PrayerLogStatus>>
    )

    suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray, snapshot: WaktivaWidget.Snapshot) {
        if (ids.isEmpty()) return
        val ep = WaktivaWidget.entryPoint(context)
        val enabled = ep.settingsManager().settingsFlow.first().prayerLogEnabled
        val today = if (enabled) load(context, snapshot) else null
        ids.forEach { id -> manager.updateAppWidget(id, sized(context, manager, id, snapshot, enabled, today)) }
    }

    /**
     * The widget at its height: the tiles keep their own height and the level takes the rest,
     * larger on a tall widget. On a short one (from Android 12, which tells a widget its sizes)
     * the tiles are lower too; before it, the widget's largest height picks one or the other.
     */
    private fun sized(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        snapshot: WaktivaWidget.Snapshot,
        enabled: Boolean,
        today: LogToday?
    ): RemoteViews {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val height = manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val level = if (height == 0 || height >= TALL_MIN_HEIGHT_DP) Level.TALL else Level.SHORT
            return build(context, snapshot, enabled, today, level)
        }
        val short = build(context, snapshot, enabled, today, Level.SHORT).apply {
            setViewLayoutHeight(R.id.widget_log_prayers, SHORT_TILES_DP, TypedValue.COMPLEX_UNIT_DIP)
        }
        val tall = build(context, snapshot, enabled, today, Level.TALL).apply {
            setViewLayoutHeight(R.id.widget_log_bar, Level.TALL.bar, TypedValue.COMPLEX_UNIT_DIP)
        }
        return RemoteViews(
            mapOf(
                SizeF(MIN_WIDTH_DP, SHORT_MIN_HEIGHT_DP) to short,
                SizeF(MIN_WIDTH_DP, TALL_MIN_HEIGHT_DP) to tall
            )
        )
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
        val current = DayWidgetModel.currentPrayer(day, snapshot.now.toLocalTime())
        return LogToday(PrayerLogProgress.of(prayed), PrayerLog.streak(prayed, date), date, current, prayers)
    }

    private fun build(context: Context, snapshot: WaktivaWidget.Snapshot, enabled: Boolean, today: LogToday?, level: Level): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer_log)
        views.setOnClickPendingIntent(android.R.id.background, openLogIntent(context))
        // The sky of the hour lit by the prayer whose time it is, as on the other widgets.
        val accent = today?.current?.accentColor?.toArgb() ?: PrayedGold.toArgb()
        views.setImageViewBitmap(R.id.widget_bg, WaktivaWidget.litBackground(snapshot.skyColors, accent, Backdrop.WIDE, snapshot.atmosphere))
        views.setViewVisibility(R.id.widget_content, if (today != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_log_off, if (!enabled) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_empty, if (enabled && today == null) View.VISIBLE else View.GONE)
        if (today == null) return views

        val locale = context.resources.configuration.locales[0]
        val progress = today.progress
        val fraction = progress.levelXp.toFloat() / progress.levelSpan
        views.setImageViewBitmap(R.id.widget_log_medal, WidgetArt.diamond(context, progress.level, level.medal))
        views.setTextViewTextSize(R.id.widget_log_level, TypedValue.COMPLEX_UNIT_SP, level.title)
        views.setTextViewTextSize(R.id.widget_log_xp, TypedValue.COMPLEX_UNIT_SP, level.xp)
        views.setTextViewTextSize(R.id.widget_log_next, TypedValue.COMPLEX_UNIT_SP, level.next)
        views.setTextViewTextSize(R.id.widget_log_streak, TypedValue.COMPLEX_UNIT_SP, level.chip)
        views.setTextViewTextSize(R.id.widget_log_badges, TypedValue.COMPLEX_UNIT_SP, level.chip)
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
        today.prayers.forEach { (type, status) -> views.addView(R.id.widget_log_prayers, tile(context, today.date, type, status)) }
        return views
    }

    /** A prayer's tile: its pane and sign in the colour of where it stands, and its tap. */
    private fun tile(context: Context, date: LocalDate, type: PrayerType, status: PrayerLogStatus): RemoteViews {
        val cell = RemoteViews(context.packageName, R.layout.widget_log_prayer)
        cell.setTextViewText(R.id.widget_log_name, type.getDisplayName(context))
        val accent = type.accentColor.toArgb()
        val (pane, icon, ink) = when (status) {
            PrayerLogStatus.PRAYED -> Triple(R.drawable.widget_pill_solid, R.drawable.ic_widget_check, INK)
            PrayerLogStatus.MISSED -> Triple(R.drawable.widget_pill_outline, R.drawable.ic_widget_cross, MISSED_TEXT)
            PrayerLogStatus.ACTIVE -> Triple(R.drawable.widget_pill_outline, R.drawable.ic_widget_ring, accent)
            PrayerLogStatus.UPCOMING, PrayerLogStatus.UNTRACKED -> Triple(R.drawable.widget_pill_faint, R.drawable.ic_widget_point, FAINT)
        }
        cell.setImageViewResource(R.id.widget_log_tile, pane)
        when (status) {
            PrayerLogStatus.PRAYED -> cell.setInt(R.id.widget_log_tile, "setColorFilter", PrayedGold.toArgb())
            PrayerLogStatus.MISSED -> cell.setInt(R.id.widget_log_tile, "setColorFilter", MissedRed.toArgb())
            PrayerLogStatus.ACTIVE -> cell.setInt(R.id.widget_log_tile, "setColorFilter", accent)
            else -> Unit
        }
        if (status == PrayerLogStatus.MISSED) cell.setInt(R.id.widget_log_tile, "setImageAlpha", 190)
        cell.setImageViewResource(R.id.widget_log_icon, icon)
        cell.setInt(R.id.widget_log_icon, "setColorFilter", ink)
        cell.setTextColor(R.id.widget_log_name, ink)

        val name = type.getPrayerName(context)
        if (status != PrayerLogStatus.UPCOMING) {
            cell.setOnClickPendingIntent(R.id.widget_log_cell, DayWidgets.toggleIntent(context, date, type, REQUEST_TOGGLE))
            cell.setContentDescription(
                R.id.widget_log_cell,
                if (status == PrayerLogStatus.PRAYED) "$name. ${context.getString(R.string.prayer_log_unmark)}"
                else "${context.getString(R.string.prayer_log_mark)}: $name"
            )
        }
        return cell
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
}
