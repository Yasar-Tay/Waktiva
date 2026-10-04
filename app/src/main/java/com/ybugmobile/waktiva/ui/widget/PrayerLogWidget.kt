package com.ybugmobile.waktiva.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Locale

/**
 * The prayer log widget ([WaktivaPrayerLogWidget]): the çetele's night on the home screen. On the
 * left, today's prayers as stars on the sun's path, lit once marked and joined into the day's
 * constellation, with how many are lit; once the day is full, Cassiopeia's W for the rest of it.
 * On the right, the prayer whose time is on, the level and the streak, and "I prayed" for that
 * prayer, which marks it without opening the app.
 */
internal object PrayerLogWidget {

    /** The sky's bitmap, in dp, and in pixels at the most whatever the density. */
    private const val SKY_W_DP = 180
    private const val SKY_H_DP = 150
    private const val SKY_MAX_PX = 540

    private const val REQUEST_TOGGLE = 7_320
    private const val REQUEST_OPEN = 7_330

    /** What every instance shows, read once per redraw. */
    private class LogToday(
        val progress: PrayerLogProgress,
        val streak: Int,
        val date: LocalDate,
        val prayers: List<Triple<PrayerType, PrayerLogStatus, Int?>>
    ) {
        val prayed get() = prayers.count { it.second == PrayerLogStatus.PRAYED }
        val isFull get() = prayed == prayers.size
        val active get() = prayers.firstOrNull { it.second == PrayerLogStatus.ACTIVE }?.first
    }

    suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray, snapshot: WaktivaWidget.Snapshot) {
        if (ids.isEmpty()) return
        val ep = WaktivaWidget.entryPoint(context)
        val enabled = ep.settingsManager().settingsFlow.first().prayerLogEnabled
        val today = if (enabled) load(context, snapshot) else null
        ids.forEach { id -> manager.updateAppWidget(id, build(context, enabled, today)) }
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
            val time = day.timings[type]
            Triple(
                type,
                PrayerLog.status(
                    date = date,
                    type = type,
                    prayed = type in marked,
                    now = snapshot.now,
                    start = time?.atDate(date),
                    end = PrayerLog.windowEnd(type, day, next),
                    trackedSince = trackedSince
                ),
                time?.let { it.hour * 60 + it.minute }
            )
        }
        return LogToday(PrayerLogProgress.of(prayed), PrayerLog.streak(prayed, date), date, prayers)
    }

    private fun build(context: Context, enabled: Boolean, today: LogToday?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer_log)
        val density = context.resources.displayMetrics.density
        views.setOnClickPendingIntent(android.R.id.background, openLogIntent(context))
        views.setImageViewBitmap(R.id.widget_bg, SkyWidgetArt.night(400, 200))
        views.setViewVisibility(R.id.widget_content, if (today != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_log_off, if (!enabled) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_empty, if (enabled && today == null) View.VISIBLE else View.GONE)
        if (today == null) return views

        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val scale = (SKY_MAX_PX / (SKY_W_DP * density)).coerceAtMost(1f)
        val stars = today.prayers.map { (type, status, minutes) -> SkyWidgetArt.SkyStar(type, minutes, status.star) }
        views.setImageViewBitmap(
            R.id.widget_log_sky,
            SkyWidgetArt.daySky(
                stars,
                queen = today.isFull,
                width = (SKY_W_DP * density * scale).toInt(),
                height = (SKY_H_DP * density * scale).toInt(),
                density = density * scale,
                rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            )
        )
        views.setViewVisibility(R.id.widget_log_count, if (today.isFull) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_log_count, "${today.prayed}/${today.prayers.size}")

        val active = today.active
        views.setTextViewText(
            R.id.widget_log_label,
            when {
                today.isFull -> context.getString(R.string.prayer_log_cassiopeia_line)
                active != null -> context.getString(R.string.prayer_log_now, active.getPrayerName(context))
                else -> context.getString(R.string.prayer_log_sky_title)
            }.uppercase(locale)
        )
        views.setTextViewText(
            R.id.widget_log_title,
            if (today.isFull) context.getString(R.string.prayer_log_cassiopeia)
            else context.getString(R.string.prayer_log_level, today.progress.level)
        )
        views.setTextViewText(R.id.widget_log_sub, "${today.streak} ${context.getString(R.string.prayer_log_streak)}")

        views.setViewVisibility(R.id.widget_log_mark, if (active != null) View.VISIBLE else View.GONE)
        if (active != null) {
            val name = active.getPrayerName(context)
            views.setTextViewText(R.id.widget_log_mark_text, context.getString(R.string.prayer_log_mark))
            views.setOnClickPendingIntent(R.id.widget_log_mark, DayWidgets.toggleIntent(context, today.date, active, REQUEST_TOGGLE))
            views.setContentDescription(R.id.widget_log_mark, "${context.getString(R.string.prayer_log_mark)}: $name")
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

    /** How the widgets' sky draws a prayer in [this] state. */
    private val PrayerLogStatus.star: SkyWidgetArt.Star
        get() = when (this) {
            PrayerLogStatus.PRAYED -> SkyWidgetArt.Star.LIT
            PrayerLogStatus.ACTIVE -> SkyWidgetArt.Star.NOW
            PrayerLogStatus.MISSED -> SkyWidgetArt.Star.MISSED
            PrayerLogStatus.UPCOMING, PrayerLogStatus.UNTRACKED -> SkyWidgetArt.Star.LATER
        }
}
