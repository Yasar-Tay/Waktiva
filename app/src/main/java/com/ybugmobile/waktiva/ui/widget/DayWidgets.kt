package com.ybugmobile.waktiva.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.data.worker.WidgetWeatherWorker
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.isLogged
import com.ybugmobile.waktiva.ui.home.composables.DayCircleWeather
import com.ybugmobile.waktiva.ui.home.composables.MissedRed
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.daySky
import com.ybugmobile.waktiva.ui.home.composables.degrees
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.home.composables.prayerWeather
import com.ybugmobile.waktiva.ui.home.composables.temperatureRange
import com.ybugmobile.waktiva.ui.home.composables.weatherIconRes
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The two day circle widgets, built on the home screen's new day circle, its weather and the
 * prayer log (çetele):
 *   DAY RING (4×2, [WaktivaDayRingWidget]) – the circle with the weather now in its middle; the
 *     next prayer and its countdown, the day's weather and today's prayer log beside it
 *   MY DAY   (4×4, [WaktivaMyDayWidget])   – place, dates and weather on top; the circle with
 *     the countdown at its heart and each prayer's weather; the day's six times below, which are
 *     also today's prayer log
 *
 * A tap on a prayer whose time has come marks it in the log, or takes the mark back, without
 * opening the app ([DayWidgetActionReceiver]). The circle's hand and sky move with a half-hourly
 * tick that doesn't wake the phone ([ensureTicking]), and the weather comes from the cache the
 * app's fetches fill ([WidgetWeatherWorker] fetches it when it grows old).
 */
internal object DayWidgets {

    const val ACTION_TOGGLE_PRAYER = "com.ybugmobile.waktiva.ACTION_WIDGET_TOGGLE_PRAYER"
    const val ACTION_TICK = "com.ybugmobile.waktiva.ACTION_WIDGET_TICK"
    const val EXTRA_DATE = "date"
    const val EXTRA_PRAYER = "prayer"

    /** The 4×2 widget is short below this height and drops its weather line and token times. */
    private const val RING_TALL_MIN_HEIGHT_DP = 150f
    private const val RING_MIN_WIDTH_DP = 250f
    private const val RING_SHORT_MIN_HEIGHT_DP = 100f

    /** The circles' bitmaps, in dp at the most, and in pixels at the most whatever the density. */
    private const val SMALL_RING_DP = 150
    private const val SMALL_RING_MAX_PX = 480
    private const val LARGE_RING_DP = 250
    private const val LARGE_RING_MAX_PX = 760

    private const val REQUEST_TICK = 7_300
    private const val REQUEST_OPEN_LOG = 7_301
    private const val REQUEST_TOGGLE = 7_310

    private const val COLOR_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_SECONDARY = 0xB3FFFFFF.toInt()
    private const val COLOR_TERTIARY = 0x73FFFFFF
    private const val COLOR_UPCOMING_ICON = 0x80FFFFFF.toInt()
    private const val COLOR_UNTRACKED_ICON = 0xB3FFFFFF.toInt()
    private const val COLOR_PRAYED_INK = 0xFF3B2A00.toInt()
    private const val ALPHA_PASSED = 115
    private const val ALPHA_OPAQUE = 255

    /** What both widgets show of today, read once per redraw and shared by every instance. */
    private class Today(
        val day: PrayerDay,
        val cells: List<DayCell>,
        val logEnabled: Boolean,
        val prayedCount: Int,
        val streak: Int,
        val weather: WidgetWeather?,
        val ring: DayRing
    ) {
        private var small: Bitmap? = null
        private var large: Bitmap? = null

        fun smallRing(context: Context): Bitmap = small ?: DayRingPainter.render(context, ring, ringPx(context, SMALL_RING_DP, SMALL_RING_MAX_PX), large = false).also { small = it }
        fun largeRing(context: Context): Bitmap = large ?: DayRingPainter.render(context, ring, ringPx(context, LARGE_RING_DP, LARGE_RING_MAX_PX), large = true).also { large = it }

        private fun ringPx(context: Context, dp: Int, maxPx: Int) =
            (dp * context.resources.displayMetrics.density).toInt().coerceIn(1, maxPx)
    }

    suspend fun render(
        context: Context,
        manager: AppWidgetManager,
        ringIds: IntArray,
        dayIds: IntArray,
        snapshot: WaktivaWidget.Snapshot
    ) {
        if (ringIds.isEmpty() && dayIds.isEmpty()) return
        ensureTicking(context)
        val today = loadToday(context, snapshot)
        ringIds.forEach { id -> manager.updateAppWidget(id, buildRingViews(context, manager, id, snapshot, today)) }
        dayIds.forEach { id -> manager.updateAppWidget(id, buildMyDay(context, snapshot, today)) }
    }

    private suspend fun loadToday(context: Context, snapshot: WaktivaWidget.Snapshot): Today? {
        val date = snapshot.now.toLocalDate()
        val day = snapshot.days.find { it.date == date } ?: return null
        val nextDay = snapshot.days.find { it.date == date.plusDays(1) }

        val ep = WaktivaWidget.entryPoint(context)
        val settings = ep.settingsManager().settingsFlow.first()
        val log = ep.prayerLogRepository()
        val allPrayed = if (settings.prayerLogEnabled) log.getPrayedPrayers().first() else emptyMap()
        val prayed = allPrayed[date].orEmpty()
        val logStart = if (settings.prayerLogEnabled) log.getStartDate().first() else null

        val cache = ep.weatherCache().load()
        WidgetWeatherWorker.requestIfStale(context, cache?.fetchedAtMillis)
        val weather = DayWidgetModel.weatherNow(cache, snapshot.now, System.currentTimeMillis())

        val nowTime = snapshot.now.toLocalTime()
        val current = DayWidgetModel.currentPrayer(day, nowTime)
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val circleWeather = weather?.let {
            DayCircleWeather(
                forecast = it.forecast,
                nowCondition = it.condition,
                nowEffect = it.effectCondition,
                effectsOn = settings.showWeatherEffects
            )
        }
        val ring = DayRing(
            day = day,
            now = nowTime,
            current = current,
            prayed = prayed,
            hourWeather = daySky(day, circleWeather, nowTime, rtl).hours,
            prayerWeather = prayerWeather(day, circleWeather, nowTime, current),
            rtl = rtl
        )
        return Today(
            day = day,
            cells = DayWidgetModel.cells(day, nextDay, PrayerType.entries, prayed, snapshot.now, logStart, settings.prayerLogEnabled),
            logEnabled = settings.prayerLogEnabled,
            prayedCount = LoggedPrayers.count { it in prayed },
            streak = PrayerLog.streak(allPrayed, date),
            weather = weather,
            ring = ring
        )
    }

    // ── Day ring (4×2) ────────────────────────────────────────────────────

    private fun buildRingViews(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        snapshot: WaktivaWidget.Snapshot,
        today: Today?
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    ringSize(RING_SHORT_MIN_HEIGHT_DP) to buildRing(context, snapshot, today, tall = false),
                    ringSize(RING_TALL_MIN_HEIGHT_DP) to buildRing(context, snapshot, today, tall = true)
                )
            )
        }
        val height = manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return buildRing(context, snapshot, today, tall = height == 0 || height >= RING_TALL_MIN_HEIGHT_DP)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun ringSize(height: Float) = SizeF(RING_MIN_WIDTH_DP, height)

    private fun buildRing(context: Context, snapshot: WaktivaWidget.Snapshot, today: Today?, tall: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_ring)
        views.setImageViewBitmap(R.id.widget_bg, snapshot.background)
        views.setOnClickPendingIntent(android.R.id.background, WaktivaWidget.openAppIntent(context))

        val next = snapshot.nextPrayer
        if (today == null || next == null) return views.showEmpty()

        views.setImageViewBitmap(R.id.widget_ring, today.smallRing(context))

        // The weather now in the ring's middle, or today's date without it.
        val weather = today.weather
        if (weather != null) {
            views.setViewVisibility(R.id.widget_ring_weather, View.VISIBLE)
            views.setViewVisibility(R.id.widget_ring_date, View.GONE)
            views.setImageViewResource(R.id.widget_weather_icon, weatherIconRes(weather.condition, weather.isDay))
            views.setTextViewText(R.id.widget_temp, weather.temperature.degrees())
        } else {
            val locale = locale(context)
            views.setViewVisibility(R.id.widget_ring_weather, View.GONE)
            views.setViewVisibility(R.id.widget_ring_date, View.VISIBLE)
            views.setTextViewText(R.id.widget_day_number, today.day.date.dayOfMonth.toString())
            views.setTextViewText(R.id.widget_month, today.day.date.format(DateTimeFormatter.ofPattern("MMM", locale)))
        }

        bindNextPrayer(context, views, snapshot)
        views.setTextViewTextSize(R.id.widget_chrono, TypedValue.COMPLEX_UNIT_SP, if (tall) 32f else 26f)

        val line = weather?.let { weatherLine(context, it) }
        views.setViewVisibility(R.id.widget_weather_line, if (tall && line != null) View.VISIBLE else View.GONE)
        line?.let { views.setTextViewText(R.id.widget_weather_line, it) }

        // With the log on, its five prayers; off, the day's six times as a small timetable.
        views.removeAllViews(R.id.widget_log)
        today.cells
            .filter { !today.logEnabled || it.type.isLogged }
            .forEach { cell ->
                val token = RemoteViews(context.packageName, R.layout.widget_log_token)
                bindToken(context, token, today.day.date, cell)
                token.setViewVisibility(R.id.widget_cell_time, if (tall) View.VISIBLE else View.GONE)
                token.setTextViewText(R.id.widget_cell_time, cell.time.format(WaktivaWidget.timeFormatter))
                token.setTextColor(R.id.widget_cell_time, if (cell.isCurrent) COLOR_PRIMARY else COLOR_SECONDARY)
                views.addView(R.id.widget_log, token)
            }
        return views
    }

    // ── My day (4×4) ──────────────────────────────────────────────────────

    private fun buildMyDay(context: Context, snapshot: WaktivaWidget.Snapshot, today: Today?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_my_day)
        views.setImageViewBitmap(R.id.widget_bg, snapshot.background)
        views.setOnClickPendingIntent(android.R.id.background, WaktivaWidget.openAppIntent(context))

        val next = snapshot.nextPrayer
        if (today == null || next == null) return views.showEmpty()

        views.setTextViewText(R.id.widget_location, snapshot.locationName.ifBlank { context.getString(R.string.app_name) })
        views.setTextViewText(R.id.widget_date, WaktivaWidget.dateLine(context, snapshot.today, snapshot.hijri))

        val weather = today.weather
        views.setViewVisibility(R.id.widget_weather_now, if (weather != null) View.VISIBLE else View.GONE)
        if (weather != null) {
            views.setImageViewResource(R.id.widget_weather_icon, weatherIconRes(weather.condition, weather.isDay))
            views.setTextViewText(R.id.widget_temp, weather.temperature.degrees())
            views.setTextViewText(
                R.id.widget_weather_range,
                weather.forecast?.temperatureRange() ?: context.getString(weather.condition.nameRes)
            )
        }

        views.setImageViewBitmap(R.id.widget_ring, today.largeRing(context))
        bindNextPrayer(context, views, snapshot)

        // The log's line: today's count, or that the day is complete, and the streak.
        views.setViewVisibility(R.id.widget_log_header, if (today.logEnabled) View.VISIBLE else View.GONE)
        if (today.logEnabled) {
            views.setTextViewText(
                R.id.widget_log_count,
                if (today.prayedCount == LoggedPrayers.size) {
                    context.getString(R.string.prayer_log_all_done)
                } else {
                    context.getString(R.string.prayer_log_today_count, today.prayedCount, LoggedPrayers.size)
                }
            )
            views.setViewVisibility(R.id.widget_streak, if (today.streak > 0) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_streak, "${today.streak} ${context.getString(R.string.prayer_log_streak)}")
            views.setOnClickPendingIntent(R.id.widget_log_header, openPrayerLogIntent(context))
        }

        views.removeAllViews(R.id.widget_log)
        today.cells.forEach { cell ->
            val column = RemoteViews(context.packageName, R.layout.widget_day_column)
            bindToken(context, column, today.day.date, cell)
            column.setTextViewText(R.id.widget_cell_name, cell.type.getDisplayName(context))
            column.setTextViewText(R.id.widget_cell_time, cell.time.format(WaktivaWidget.timeFormatter))
            column.setTextColor(R.id.widget_cell_name, if (cell.isPassed) COLOR_TERTIARY else COLOR_SECONDARY)
            column.setTextColor(R.id.widget_cell_time, if (cell.isPassed) COLOR_TERTIARY else COLOR_PRIMARY)
            if (cell.isCurrent) column.setInt(R.id.widget_cell, "setBackgroundResource", R.drawable.widget_cell_highlight)
            views.addView(R.id.widget_log, column)
        }
        return views
    }

    // ── Shared pieces ─────────────────────────────────────────────────────

    private fun RemoteViews.showEmpty(): RemoteViews = apply {
        setViewVisibility(R.id.widget_content, View.GONE)
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
    }

    /** The next prayer: its icon in its colour, its name and time, and the live countdown to it. */
    private fun bindNextPrayer(context: Context, views: RemoteViews, snapshot: WaktivaWidget.Snapshot) {
        val next = snapshot.nextPrayer ?: return
        views.setViewVisibility(R.id.widget_content, View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setImageViewResource(R.id.widget_icon, next.type.iconRes)
        views.setInt(R.id.widget_icon, "setColorFilter", next.type.accentColor.toArgb())
        views.setTextViewText(R.id.widget_name, next.type.getDisplayName(context))
        views.setTextViewText(R.id.widget_time, next.time.format(WaktivaWidget.timeFormatter))

        val chronometer = snapshot.chronometer
        views.setChronometer(
            R.id.widget_chrono,
            chronometer?.baseTime ?: SystemClock.elapsedRealtime(),
            null,
            chronometer?.isRunning == true
        )
        views.setChronometerCountDown(R.id.widget_chrono, true)
    }

    /**
     * A prayer's token: gold with a tick once prayed, ringed in green while its time is on, in
     * red once its time went unmarked, faint while it's still to come. A time the log doesn't
     * keep shows its icon in its colour. Tapping a token whose time has come marks it.
     */
    private fun bindToken(context: Context, views: RemoteViews, date: LocalDate, cell: DayCell) {
        val name = cell.type.getDisplayName(context)
        val (background, icon, tint) = when (cell.status) {
            PrayerLogStatus.PRAYED -> Triple(R.drawable.widget_token_prayed, R.drawable.ic_widget_check, COLOR_PRAYED_INK)
            PrayerLogStatus.ACTIVE -> Triple(R.drawable.widget_token_active, cell.type.iconRes, COLOR_PRIMARY)
            PrayerLogStatus.MISSED -> Triple(R.drawable.widget_token_missed, cell.type.iconRes, MissedRed.toArgb())
            PrayerLogStatus.UNTRACKED -> Triple(R.drawable.widget_token_upcoming, cell.type.iconRes, COLOR_UNTRACKED_ICON)
            PrayerLogStatus.UPCOMING -> Triple(R.drawable.widget_token_upcoming, cell.type.iconRes, COLOR_UPCOMING_ICON)
            null -> Triple(R.drawable.widget_token_upcoming, cell.type.iconRes, cell.type.accentColor.toArgb())
        }
        views.setInt(R.id.widget_token, "setBackgroundResource", background)
        views.setImageViewResource(R.id.widget_token, icon)
        views.setInt(R.id.widget_token, "setColorFilter", tint)
        views.setInt(R.id.widget_token, "setImageAlpha", if (cell.status == null && cell.isPassed) ALPHA_PASSED else ALPHA_OPAQUE)

        val state = when (cell.status) {
            PrayerLogStatus.PRAYED -> R.string.prayer_log_status_prayed
            PrayerLogStatus.ACTIVE -> R.string.prayer_log_status_active
            PrayerLogStatus.MISSED -> R.string.prayer_log_status_missed
            PrayerLogStatus.UPCOMING -> R.string.prayer_log_status_upcoming
            PrayerLogStatus.UNTRACKED -> R.string.prayer_log_status_untracked
            null -> null
        }
        val time = cell.time.format(WaktivaWidget.timeFormatter)
        views.setContentDescription(
            R.id.widget_cell,
            listOfNotNull(name, time, state?.let { context.getString(it) }).joinToString(", ")
        )
        if (cell.canMark) views.setOnClickPendingIntent(R.id.widget_cell, toggleIntent(context, date, cell.type))
    }

    /** "Partly cloudy · 14° – 23°": the weather now and the day's range. */
    private fun weatherLine(context: Context, weather: WidgetWeather): String {
        val name = context.getString(weather.condition.nameRes)
        return weather.forecast?.let { "$name · ${it.temperatureRange()}" } ?: name
    }

    private fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    private fun toggleIntent(context: Context, date: LocalDate, type: PrayerType): PendingIntent {
        val intent = Intent(context, DayWidgetActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_PRAYER
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_PRAYER, type.name)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE + type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openPrayerLogIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_OPEN_SCREEN, NotificationHelper.SCREEN_PRAYER_LOG)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_LOG,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── The prayer log, from a tap ────────────────────────────────────────

    /**
     * Marks [type] on [date] as prayed, or takes the mark back, as a tap on its token asks. Only
     * today's prayers whose time has come, with the log on: a widget drawn yesterday, or before a
     * prayer's time, can't mark what the app wouldn't.
     */
    suspend fun togglePrayer(context: Context, date: LocalDate, type: PrayerType) {
        val ep = WaktivaWidget.entryPoint(context)
        if (!type.isLogged || !ep.settingsManager().settingsFlow.first().prayerLogEnabled) return
        val now = ep.timeManager().now()
        if (date != now.toLocalDate()) return
        val day = ep.prayerRepository().getPrayerDays().first().find { it.date == date } ?: return
        val time = day.timings[type] ?: return
        if (time.isAfter(now.toLocalTime())) return

        val log = ep.prayerLogRepository()
        val prayed = type in log.getPrayedPrayers(date).first()
        log.setPrayed(date, type, !prayed)
    }

    // ── The tick ──────────────────────────────────────────────────────────

    private fun tickIntent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_TICK,
        Intent(context, DayWidgetActionReceiver::class.java).setAction(ACTION_TICK),
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * Redraws the widgets every half hour, so the circle's hand and sky keep up with the day. The
     * alarm doesn't wake the phone: it waits for the screen to be on, when the widgets are seen.
     */
    fun ensureTicking(context: Context) {
        if (tickIntent(context, PendingIntent.FLAG_NO_CREATE) != null) return
        val intent = tickIntent(context, 0) ?: return
        context.getSystemService(AlarmManager::class.java)?.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR,
            intent
        )
    }

    /** Stops the tick once neither day circle widget is on the home screen. */
    fun stopTickingIfUnused(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val inUse = listOf(WaktivaDayRingWidget::class.java, WaktivaMyDayWidget::class.java)
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        if (inUse) return
        val intent = tickIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(intent)
        intent.cancel()
    }
}
