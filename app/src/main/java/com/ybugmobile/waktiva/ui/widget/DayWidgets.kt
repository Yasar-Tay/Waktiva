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
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.worker.WidgetWeatherWorker
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.isLogged
import com.ybugmobile.waktiva.ui.home.composables.degrees
import com.ybugmobile.waktiva.ui.home.composables.weatherIconRes
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * The two day circle widgets. A widget is read in a glance, so they don't show the whole day at
 * once: they show the moment ([Moment]) and change with it.
 *
 *   OPEN    – "Dhuhr time", the time left for it, and "I prayed" beside it
 *   ENDING  – the same in amber in the last half hour of its time
 *   PRAYED  – "✓ Dhuhr prayed" (tap to take the mark back), a calm countdown to the next prayer
 *   WAITING – after sunrise, the countdown to the next prayer
 *
 * The 4×2 ([WaktivaDayRingWidget]) is the moment alone; once the prayer is marked, the weather and
 * today's five marks take the button's place. The 4×4 ([WaktivaMyDayWidget]) adds the day circle,
 * with only the current prayer's stretch lit, and the day's five times.
 *
 * "I prayed" marks the prayer without opening the app ([DayWidgetActionReceiver]). The circle
 * moves with a half-hourly tick that doesn't wake the phone, and a redraw is set for when a
 * prayer's time starts ending ([ensureTicking]); prayer boundaries come from the app's alarms.
 */
internal object DayWidgets {

    const val ACTION_TOGGLE_PRAYER = "com.ybugmobile.waktiva.ACTION_WIDGET_TOGGLE_PRAYER"
    const val ACTION_TICK = "com.ybugmobile.waktiva.ACTION_WIDGET_TICK"
    const val EXTRA_DATE = "date"
    const val EXTRA_PRAYER = "prayer"

    /** The 4×2 widget is short below this height and its countdown smaller. */
    private const val TALL_MIN_HEIGHT_DP = 150f
    private const val RING_MIN_WIDTH_DP = 250f
    private const val SHORT_MIN_HEIGHT_DP = 100f

    /** The 4×4 circle's bitmap, in dp at the most, and in pixels at the most whatever the density. */
    private const val RING_DP = 220
    private const val RING_MAX_PX = 680

    private const val REQUEST_TICK = 7_300
    private const val REQUEST_ENDING = 7_302
    private const val REQUEST_TOGGLE = 7_310

    private const val COLOR_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_SECONDARY = 0xB3FFFFFF.toInt()
    private const val COLOR_TERTIARY = 0x73FFFFFF
    private const val COLOR_OPEN = 0xFF6EE7B7.toInt()
    private const val COLOR_ENDING = 0xFFFBBF24.toInt()
    private const val COLOR_ENDING_COUNT = 0xFFFDE68A.toInt()
    private const val COLOR_PRAYED = 0xFFFFD54F.toInt()
    private const val COLOR_UNMARKED = 0x40FFFFFF

    private val markIds = intArrayOf(R.id.widget_mark_1, R.id.widget_mark_2, R.id.widget_mark_3, R.id.widget_mark_4, R.id.widget_mark_5)

    /** What both widgets show, read once per redraw and shared by every instance. */
    private class Today(
        val day: PrayerDay,
        val moment: Moment,
        val times: List<DayTime>,
        val prayedToday: Set<PrayerType>,
        val logEnabled: Boolean,
        val weather: WidgetWeather?,
        private val ring: DayRing
    ) {
        private var ringBitmap: Bitmap? = null

        fun ring(context: Context): Bitmap = ringBitmap ?: DayRingPainter.render(
            context,
            ring,
            (RING_DP * context.resources.displayMetrics.density).toInt().coerceIn(1, RING_MAX_PX)
        ).also { ringBitmap = it }
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
        today?.let { scheduleEnding(context, it.moment) }
        ringIds.forEach { id -> manager.updateAppWidget(id, buildNowViews(context, manager, id, snapshot, today)) }
        dayIds.forEach { id -> manager.updateAppWidget(id, buildMyDay(context, snapshot, today)) }
    }

    private suspend fun loadToday(context: Context, snapshot: WaktivaWidget.Snapshot): Today? {
        val date = snapshot.now.toLocalDate()
        val day = snapshot.days.find { it.date == date } ?: return null
        val next = snapshot.nextPrayer ?: return null

        val ep = WaktivaWidget.entryPoint(context)
        val settings = ep.settingsManager().settingsFlow.first()
        val prayed = if (settings.prayerLogEnabled) {
            val log = ep.prayerLogRepository()
            mapOf(
                date to log.getPrayedPrayers(date).first(),
                date.minusDays(1) to log.getPrayedPrayers(date.minusDays(1)).first()
            )
        } else {
            emptyMap()
        }

        val cache = ep.weatherCache().load()
        WidgetWeatherWorker.requestIfStale(context, cache?.fetchedAtMillis)

        val moment = DayWidgetModel.moment(day, snapshot.now, next, prayed, settings.prayerLogEnabled)
        val prayedToday = prayed[date].orEmpty()
        val current = DayWidgetModel.currentPrayer(day, snapshot.now.toLocalTime())
        return Today(
            day = day,
            moment = moment,
            times = DayWidgetModel.times(day, snapshot.now, current, prayedToday),
            prayedToday = prayedToday,
            logEnabled = settings.prayerLogEnabled,
            weather = DayWidgetModel.weatherNow(cache, snapshot.now, System.currentTimeMillis()),
            ring = DayRing(
                day = day,
                now = snapshot.now.toLocalTime(),
                current = current,
                prayed = prayedToday,
                rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            )
        )
    }

    // ── Prayer now (4×2) ──────────────────────────────────────────────────

    private fun buildNowViews(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        snapshot: WaktivaWidget.Snapshot,
        today: Today?
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    size(SHORT_MIN_HEIGHT_DP) to buildNow(context, snapshot, today, tall = false),
                    size(TALL_MIN_HEIGHT_DP) to buildNow(context, snapshot, today, tall = true)
                )
            )
        }
        val height = manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return buildNow(context, snapshot, today, tall = height == 0 || height >= TALL_MIN_HEIGHT_DP)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun size(height: Float) = SizeF(RING_MIN_WIDTH_DP, height)

    private fun buildNow(context: Context, snapshot: WaktivaWidget.Snapshot, today: Today?, tall: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_ring)
        views.setImageViewBitmap(R.id.widget_bg, snapshot.background)
        views.setOnClickPendingIntent(android.R.id.background, WaktivaWidget.openAppIntent(context))
        if (today == null) return views.showEmpty()
        views.showContent()

        bindMoment(context, views, snapshot, today.moment, countSp = if (tall) 40f else 32f)

        // Once nothing is asked of the user, the weather and today's marks take the button's place.
        val calm = !today.moment.canMark
        views.setViewVisibility(R.id.widget_calm, if (calm) View.VISIBLE else View.GONE)
        if (calm) {
            bindWeather(views, today.weather)
            bindMarks(views, today)
        }
        return views
    }

    // ── My day (4×4) ──────────────────────────────────────────────────────

    private fun buildMyDay(context: Context, snapshot: WaktivaWidget.Snapshot, today: Today?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_my_day)
        views.setImageViewBitmap(R.id.widget_bg, snapshot.background)
        views.setOnClickPendingIntent(android.R.id.background, WaktivaWidget.openAppIntent(context))
        if (today == null) return views.showEmpty()
        views.showContent()

        bindMoment(context, views, snapshot, today.moment, countSp = 44f)
        bindWeather(views, today.weather)
        views.setImageViewBitmap(R.id.widget_ring, today.ring(context))

        views.removeAllViews(R.id.widget_times)
        today.times.forEach { time ->
            val cell = RemoteViews(context.packageName, R.layout.widget_day_column)
            cell.setTextViewText(R.id.widget_cell_name, time.type.getDisplayName(context))
            cell.setTextViewText(R.id.widget_cell_time, time.time.format(WaktivaWidget.timeFormatter))
            val (name, clock) = when {
                time.isCurrent -> COLOR_PRIMARY to COLOR_PRIMARY
                time.isPrayed && today.logEnabled -> COLOR_PRAYED to COLOR_PRAYED
                time.isPassed -> COLOR_TERTIARY to COLOR_TERTIARY
                else -> COLOR_SECONDARY to COLOR_PRIMARY
            }
            cell.setTextColor(R.id.widget_cell_name, name)
            cell.setTextColor(R.id.widget_cell_time, clock)
            if (time.isCurrent) cell.setInt(R.id.widget_cell, "setBackgroundResource", R.drawable.widget_cell_highlight)
            views.addView(R.id.widget_times, cell)
        }
        return views
    }

    // ── Shared pieces ─────────────────────────────────────────────────────

    private fun RemoteViews.showEmpty(): RemoteViews = apply {
        setViewVisibility(R.id.widget_content, View.GONE)
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
    }

    private fun RemoteViews.showContent() {
        setViewVisibility(R.id.widget_content, View.VISIBLE)
        setViewVisibility(R.id.widget_empty, View.GONE)
    }

    /**
     * The moment: its label, the live countdown to the end of the prayer's time (the next
     * prayer's start, so one figure serves both) and the line under it; and "I prayed".
     */
    private fun bindMoment(context: Context, views: RemoteViews, snapshot: WaktivaWidget.Snapshot, moment: Moment, countSp: Float) {
        val prayer = moment.prayer.getDisplayName(context)
        val next = moment.next.type.getDisplayName(context)
        val nextTime = moment.next.time.format(WaktivaWidget.timeFormatter)

        val (label, labelColor) = when (moment.state) {
            MomentState.OPEN -> context.getString(R.string.widget_prayer_time, prayer) to COLOR_OPEN
            MomentState.ENDING -> context.getString(R.string.widget_prayer_time_ending, prayer) to COLOR_ENDING
            MomentState.PRAYED -> context.getString(R.string.widget_prayer_prayed, prayer) to COLOR_PRIMARY
            MomentState.WAITING -> context.getString(R.string.widget_next_up) to COLOR_SECONDARY
        }
        views.setTextViewText(R.id.widget_label, label)
        views.setTextColor(R.id.widget_label, labelColor)
        views.setViewVisibility(R.id.widget_label_check, if (moment.state == MomentState.PRAYED) View.VISIBLE else View.GONE)

        val counting = moment.state == MomentState.OPEN || moment.state == MomentState.ENDING
        views.setTextViewText(
            R.id.widget_sub,
            if (counting) context.getString(R.string.widget_left_until, next, nextTime) else "$next · $nextTime"
        )

        val chronometer = snapshot.chronometer
        views.setChronometer(
            R.id.widget_chrono,
            chronometer?.baseTime ?: SystemClock.elapsedRealtime(),
            null,
            chronometer?.isRunning == true
        )
        views.setChronometerCountDown(R.id.widget_chrono, true)
        views.setTextColor(R.id.widget_chrono, if (moment.state == MomentState.ENDING) COLOR_ENDING_COUNT else COLOR_PRIMARY)
        views.setTextViewTextSize(R.id.widget_chrono, TypedValue.COMPLEX_UNIT_SP, countSp)

        views.setViewVisibility(R.id.widget_mark, if (moment.canMark) View.VISIBLE else View.GONE)
        if (moment.canMark) {
            views.setOnClickPendingIntent(R.id.widget_mark, toggleIntent(context, moment.logDate, moment.prayer))
            views.setContentDescription(R.id.widget_mark, "${context.getString(R.string.prayer_log_mark)}: $prayer")
        }
        // A mark made by a slip of the finger is taken back from where it shows.
        if (moment.state == MomentState.PRAYED) {
            views.setOnClickPendingIntent(R.id.widget_label_row, toggleIntent(context, moment.logDate, moment.prayer))
            views.setContentDescription(R.id.widget_label_row, "$label. ${context.getString(R.string.prayer_log_unmark)}")
        }
    }

    private fun bindWeather(views: RemoteViews, weather: WidgetWeather?) {
        views.setViewVisibility(R.id.widget_weather, if (weather != null) View.VISIBLE else View.GONE)
        weather ?: return
        views.setImageViewResource(R.id.widget_weather_icon, weatherIconRes(weather.condition, weather.isDay))
        views.setTextViewText(R.id.widget_temp, weather.temperature.degrees())
    }

    /** Today's five prayers as dots, gold once prayed; hidden with the log off. */
    private fun bindMarks(views: RemoteViews, today: Today) {
        views.setViewVisibility(R.id.widget_marks, if (today.logEnabled) View.VISIBLE else View.GONE)
        if (!today.logEnabled) return
        LoggedPrayers.forEachIndexed { i, type ->
            val id = markIds.getOrNull(i) ?: return@forEachIndexed
            views.setInt(id, "setColorFilter", if (type in today.prayedToday) COLOR_PRAYED else COLOR_UNMARKED)
        }
    }

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

    // ── The prayer log, from a tap ────────────────────────────────────────

    /**
     * Marks [type] on [date] as prayed, or takes the mark back, as the widget asks. Only a prayer
     * whose time has come: one of today's, or the night's Isha (yesterday's) before dawn. A widget
     * drawn earlier can't mark what the app wouldn't.
     */
    suspend fun togglePrayer(context: Context, date: LocalDate, type: PrayerType) {
        val ep = WaktivaWidget.entryPoint(context)
        if (!type.isLogged || !ep.settingsManager().settingsFlow.first().prayerLogEnabled) return
        val now = ep.timeManager().now()
        val today = now.toLocalDate()
        val day = ep.prayerRepository().getPrayerDays().first().find { it.date == today } ?: return
        val fajr = day.timings[PrayerType.FAJR]
        val allowed = when (date) {
            today -> day.timings[type]?.let { !it.isAfter(now.toLocalTime()) } == true
            today.minusDays(1) -> type == PrayerType.ISHA && fajr != null && now.toLocalTime().isBefore(fajr)
            else -> false
        }
        if (!allowed) return

        val log = ep.prayerLogRepository()
        val prayed = type in log.getPrayedPrayers(date).first()
        log.setPrayed(date, type, !prayed)
    }

    // ── The tick ──────────────────────────────────────────────────────────

    private fun tickIntent(context: Context, requestCode: Int, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, DayWidgetActionReceiver::class.java).setAction(ACTION_TICK),
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * Redraws the widgets every half hour, so the circle keeps up with the day. The alarm doesn't
     * wake the phone: it waits for the screen to be on, when the widgets are seen.
     */
    private fun ensureTicking(context: Context) {
        if (tickIntent(context, REQUEST_TICK, PendingIntent.FLAG_NO_CREATE) != null) return
        val intent = tickIntent(context, REQUEST_TICK, 0) ?: return
        context.getSystemService(AlarmManager::class.java)?.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR,
            intent
        )
    }

    /**
     * Sets a redraw for when the prayer's time starts ending, so the widgets turn amber on time.
     * Within a minute is close enough, which needs no exact alarm; nor does it wake the phone.
     */
    private fun scheduleEnding(context: Context, moment: Moment) {
        if (moment.state != MomentState.OPEN) return
        val at = moment.endsAt.minusMinutes(DayWidgetModel.ENDING_MINUTES)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at <= System.currentTimeMillis()) return
        val intent = tickIntent(context, REQUEST_ENDING, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        context.getSystemService(AlarmManager::class.java)?.setWindow(AlarmManager.RTC, at, 60_000L, intent)
    }

    /** Stops the tick once neither day circle widget is on the home screen. */
    fun stopTickingIfUnused(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val inUse = listOf(WaktivaDayRingWidget::class.java, WaktivaMyDayWidget::class.java)
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        if (inUse) return
        val alarms = context.getSystemService(AlarmManager::class.java)
        listOf(REQUEST_TICK, REQUEST_ENDING).forEach { code ->
            tickIntent(context, code, PendingIntent.FLAG_NO_CREATE)?.let {
                alarms?.cancel(it)
                it.cancel()
            }
        }
    }
}
