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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.isLogged
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.degrees
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.home.composables.skyLight
import com.ybugmobile.waktiva.ui.home.composables.weatherIconRes
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The two day circle widgets. A widget is read in a glance, so they don't show the whole day at
 * once: they show the moment ([Moment]) and change with it.
 *
 *   OPEN    – "Dhuhr time", the time left for it, and the sphere offering "I prayed"
 *   ENDING  – the same in amber in the last half hour of its time
 *   PRAYED  – "✓ Dhuhr prayed", the sphere gold, a calm countdown to the next prayer
 *   WAITING – after sunrise, the countdown to the next prayer
 *
 * The moment's sphere is a glass orb in the home screen's materials, holding the sky of the hour
 * in the prayer's colour; it is the "I prayed" button, and a tap on it once gold takes the mark
 * back. In the 4×2 ([WaktivaDayRingWidget]) it sits in a ring of the time left; in the 4×4
 * ([WaktivaMyDayWidget]) it is the heart of the day circle, whose prayers are glass spheres too.
 *
 * Marks go to the prayer log without opening the app ([DayWidgetActionReceiver]). The widgets
 * redraw every quarter hour without waking the phone and when a prayer's time starts ending
 * ([ensureTicking], [scheduleEnding]); prayer boundaries come from the app's alarms.
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

    /** The painted parts' bitmaps, in dp at the most, and in pixels at the most whatever the density. */
    private const val ORB_DP = 112
    private const val ORB_MAX_PX = 360
    private const val RING_DP = 240
    private const val RING_MAX_PX = 720

    private const val REQUEST_TICK = 7_303

    /** The half-hourly tick the widgets had before the quarter-hourly one; cancelled once seen. */
    private const val REQUEST_OLD_TICK = 7_300
    private const val REQUEST_ENDING = 7_302
    private const val REQUEST_TOGGLE = 7_310

    /** The "my day" widget's cards, one request code per prayer from here. */
    private const val REQUEST_DAY_CARD = 7_340

    private const val COLOR_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_SECONDARY = 0xB3FFFFFF.toInt()
    private const val COLOR_TERTIARY = 0x73FFFFFF
    private const val COLOR_ENDING_COUNT = 0xFFFDE68A.toInt()
    private const val COLOR_PRAYED = 0xFFFFD54F.toInt()

    /** The green of a prayer whose time is on, as on the prayer log's chips. */
    private val Open = Color(0xFF6EE7B7)

    /** The amber of a prayer whose time is ending. */
    private val Ending = Color(0xFFFBBF24)

    /** What both widgets show, read once per redraw and shared by every instance. */
    private class Today(
        val day: PrayerDay,
        val moment: Moment,
        val times: List<DayTime>,
        val logEnabled: Boolean,
        val orb: WidgetArt.Orb,
        val remaining: Float,
        val ring: DayRing
    ) {
        private var orbBitmap: Bitmap? = null
        private var ringBitmap: Bitmap? = null

        fun orb(context: Context): Bitmap = orbBitmap ?: WidgetArt.momentOrb(
            context, orb, remaining, arcColor(moment), px(context, ORB_DP, ORB_MAX_PX)
        ).also { orbBitmap = it }

        fun ring(context: Context): Bitmap = ringBitmap ?: DayRingPainter.render(
            context, ring, px(context, RING_DP, RING_MAX_PX)
        ).also { ringBitmap = it }

        private fun px(context: Context, dp: Int, max: Int) =
            (dp * context.resources.displayMetrics.density).toInt().coerceIn(1, max)
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
        val logEnabled = ep.settingsManager().settingsFlow.first().prayerLogEnabled
        val prayed = if (logEnabled) {
            val log = ep.prayerLogRepository()
            mapOf(
                date to log.getPrayedPrayers(date).first(),
                date.minusDays(1) to log.getPrayedPrayers(date.minusDays(1)).first()
            )
        } else {
            emptyMap()
        }

        val now = snapshot.now
        val moment = DayWidgetModel.moment(day, now, next, prayed, logEnabled)
        val prayedToday = prayed[date].orEmpty()
        val current = DayWidgetModel.currentPrayer(day, now.toLocalTime())
        val hero = hero(moment)
        val orb = WidgetArt.Orb(
            color = hero.accentColor,
            sky = skyLight(now.hour * 60f + now.minute, day),
            icon = hero.iconRes,
            prayed = moment.state == MomentState.PRAYED,
            raised = true,
            caption = if (moment.canMark) context.getString(R.string.prayer_log_mark).uppercase(locale(context)) else null
        )
        return Today(
            day = day,
            moment = moment,
            times = DayWidgetModel.times(day, now, current, prayedToday),
            logEnabled = logEnabled,
            orb = orb,
            remaining = moment.remaining(now),
            ring = DayRing(
                day = day,
                now = now.toLocalTime(),
                current = current,
                prayed = prayedToday,
                hub = orb,
                rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            )
        )
    }

    /** The prayer the moment is about: the one whose time it is, or the next one while none is. */
    private fun hero(moment: Moment): PrayerType =
        if (moment.state == MomentState.WAITING) moment.next.type else moment.prayer

    /** The colour of the time left: the prayer's own, amber while it's ending. */
    private fun arcColor(moment: Moment): Color =
        if (moment.state == MomentState.ENDING) Ending else hero(moment).accentColor

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
        views.setOnClickPendingIntent(android.R.id.background, WaktivaWidget.openAppIntent(context))
        if (today == null) return views.showEmpty(snapshot)
        views.showContent()
        views.setImageViewBitmap(R.id.widget_bg, background(snapshot, today.moment, Backdrop.WIDE))

        bindMoment(context, views, snapshot, today, countSp = if (tall) 44f else 34f)
        views.setImageViewBitmap(R.id.widget_orb, today.orb(context))
        return views
    }

    // ── My day (4×4) ──────────────────────────────────────────────────────

    private fun buildMyDay(context: Context, snapshot: WaktivaWidget.Snapshot, today: Today?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_my_day)
        views.setOnClickPendingIntent(android.R.id.background, WaktivaWidget.openAppIntent(context))
        if (today == null) return views.showEmpty(snapshot)
        views.showContent()
        views.setImageViewBitmap(R.id.widget_bg, background(snapshot, today.moment, Backdrop.LARGE))

        bindMoment(context, views, snapshot, today, countSp = 38f)
        views.setImageViewBitmap(R.id.widget_ring, today.ring(context))

        views.removeAllViews(R.id.widget_times)
        today.times.forEach { time -> views.addView(R.id.widget_times, dayCard(context, today, time)) }
        return views
    }

    /**
     * One of the day's prayers as a card. With the log on, a prayer whose time has come (the
     * current one or an earlier one) is marked or unmarked with a tap: gold with a tick once
     * prayed, an empty ring while it waits.
     */
    private fun dayCard(context: Context, today: Today, time: DayTime): RemoteViews {
        val cell = RemoteViews(context.packageName, R.layout.widget_day_column)
        val name = time.type.getDisplayName(context)
        cell.setTextViewText(R.id.widget_cell_name, name)
        cell.setTextViewText(R.id.widget_cell_time, time.time.format(WaktivaWidget.timeFormatter))

        val prayed = time.isPrayed && today.logEnabled
        val markable = today.logEnabled && (time.isCurrent || time.isPassed)
        val (nameColor, clockColor) = when {
            prayed -> COLOR_PRAYED to COLOR_PRAYED
            time.isCurrent -> COLOR_PRIMARY to COLOR_PRIMARY
            time.isPassed -> if (markable) COLOR_SECONDARY to COLOR_SECONDARY else COLOR_TERTIARY to COLOR_TERTIARY
            else -> COLOR_SECONDARY to COLOR_PRIMARY
        }
        cell.setTextColor(R.id.widget_cell_name, nameColor)
        cell.setTextColor(R.id.widget_cell_time, clockColor)
        when {
            prayed -> cell.setInt(R.id.widget_cell, "setBackgroundResource", R.drawable.widget_day_card_prayed)
            time.isCurrent -> cell.setInt(R.id.widget_cell, "setBackgroundResource", R.drawable.widget_cell_highlight)
        }

        if (prayed || markable) {
            cell.setViewVisibility(R.id.widget_cell_mark, View.VISIBLE)
            cell.setImageViewResource(R.id.widget_cell_mark, if (prayed) R.drawable.ic_widget_check else R.drawable.widget_mark_ring)
            if (prayed) cell.setInt(R.id.widget_cell_mark, "setColorFilter", COLOR_PRAYED)
        }
        if (markable) {
            val prayerName = time.type.getPrayerName(context)
            cell.setOnClickPendingIntent(R.id.widget_cell, toggleIntent(context, today.day.date, time.type, REQUEST_DAY_CARD))
            cell.setContentDescription(
                R.id.widget_cell,
                if (prayed) "$prayerName. ${context.getString(R.string.prayer_log_unmark)}"
                else "${context.getString(R.string.prayer_log_mark)}: $prayerName"
            )
        }
        return cell
    }

    // ── Shared pieces ─────────────────────────────────────────────────────

    private fun RemoteViews.showEmpty(snapshot: WaktivaWidget.Snapshot): RemoteViews = apply {
        setImageViewBitmap(R.id.widget_bg, snapshot.background)
        setViewVisibility(R.id.widget_content, View.GONE)
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
    }

    private fun RemoteViews.showContent() {
        setViewVisibility(R.id.widget_content, View.VISIBLE)
        setViewVisibility(R.id.widget_empty, View.GONE)
    }

    /**
     * The sky of the hour with its clouds or stars, lit by the moment's prayer, amber while its
     * time is ending.
     */
    private fun background(snapshot: WaktivaWidget.Snapshot, moment: Moment, backdrop: Backdrop): Bitmap =
        WaktivaWidget.litBackground(snapshot.skyColors, arcColor(moment).toArgb(), backdrop, snapshot.atmosphere)

    /**
     * The moment: its chip, the live countdown to the end of the prayer's time (the next
     * prayer's start, so one figure serves both), what comes next with the weather now, and the
     * sphere's tap: "I prayed", or taking the mark back once it's gold.
     */
    private fun bindMoment(context: Context, views: RemoteViews, snapshot: WaktivaWidget.Snapshot, today: Today, countSp: Float) {
        val moment = today.moment
        val prayer = moment.prayer.getPrayerName(context)
        val label = when (moment.state) {
            MomentState.OPEN -> context.getString(R.string.widget_prayer_time, prayer)
            MomentState.ENDING -> context.getString(R.string.widget_prayer_time_ending, prayer)
            // With the log's XP for it: the prayer log is a game, on the home screen too.
            MomentState.PRAYED -> context.getString(R.string.widget_prayer_prayed, prayer) + "  " +
                context.getString(R.string.prayer_log_xp_gain, PrayerLogProgress.XP_PER_PRAYER)
            MomentState.WAITING -> context.getString(R.string.widget_next_up)
        }
        views.setTextViewText(R.id.widget_label, label)
        val prayed = moment.state == MomentState.PRAYED
        views.setViewVisibility(R.id.widget_label_check, if (prayed) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_label_dot, if (prayed) View.GONE else View.VISIBLE)
        val dot = when (moment.state) {
            MomentState.OPEN -> Open
            MomentState.ENDING -> Ending
            else -> hero(moment).accentColor
        }
        views.setInt(R.id.widget_label_dot, "setColorFilter", dot.toArgb())

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
        val counting = moment.state == MomentState.OPEN || moment.state == MomentState.ENDING
        views.setViewVisibility(R.id.widget_left, if (counting) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_next_icon, moment.next.type.iconRes)
        views.setInt(R.id.widget_next_icon, "setColorFilter", moment.next.type.accentColor.toArgb())
        views.setTextViewText(
            R.id.widget_sub,
            "${moment.next.type.getDisplayName(context)}  ${moment.next.time.format(WaktivaWidget.timeFormatter)}"
        )

        val weather = snapshot.weather
        views.setViewVisibility(R.id.widget_weather, if (weather != null) View.VISIBLE else View.GONE)
        if (weather != null) {
            views.setImageViewResource(R.id.widget_weather_icon, weatherIconRes(weather.condition, weather.isDay))
            views.setTextViewText(R.id.widget_temp, weather.temperature.degrees())
        }

        if (moment.canMark || prayed) {
            views.setOnClickPendingIntent(R.id.widget_mark, toggleIntent(context, moment.logDate, moment.prayer, REQUEST_TOGGLE))
            views.setContentDescription(
                R.id.widget_mark,
                if (prayed) "$label. ${context.getString(R.string.prayer_log_unmark)}" else "${context.getString(R.string.prayer_log_mark)}: $prayer"
            )
        }
    }

    private fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    /**
     * Marks [type] on [date] or takes the mark back ([togglePrayer]). Each widget kind passes its
     * own [requestBase], so one's taps can't hand another theirs.
     */
    fun toggleIntent(context: Context, date: LocalDate, type: PrayerType, requestBase: Int): PendingIntent {
        val intent = Intent(context, DayWidgetActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_PRAYER
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_PRAYER, type.name)
        }
        return PendingIntent.getBroadcast(
            context,
            requestBase + type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── The prayer log, from a tap ────────────────────────────────────────

    /**
     * Marks [type] on [date] as prayed, or takes the mark back, as the widget asks. Only a prayer
     * whose time has come: one of today's, or the night's Isha (yesterday's) before dawn. A widget
     * drawn earlier can't mark what the app wouldn't. What a mark reaches is cheered for in a
     * notification while the log's game notifications are on.
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

        val prayed = type in ep.prayerLogRepository().getPrayedPrayers(date).first()
        ep.prayerLogGame().setPrayed(date, type, !prayed, today)
    }

    // ── The ticks ─────────────────────────────────────────────────────────

    private fun tickIntent(context: Context, requestCode: Int, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, DayWidgetActionReceiver::class.java).setAction(ACTION_TICK),
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * Redraws the widgets every quarter hour, so the ring of the time left and the circle keep up
     * with the day. The alarm doesn't wake the phone: it waits for the screen to be on, when the
     * widgets are seen.
     */
    private fun ensureTicking(context: Context) {
        tickIntent(context, REQUEST_OLD_TICK, PendingIntent.FLAG_NO_CREATE)?.let {
            context.getSystemService(AlarmManager::class.java)?.cancel(it)
            it.cancel()
        }
        if (tickIntent(context, REQUEST_TICK, PendingIntent.FLAG_NO_CREATE) != null) return
        val intent = tickIntent(context, REQUEST_TICK, 0) ?: return
        context.getSystemService(AlarmManager::class.java)?.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
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

    /** Stops the ticks once neither day circle widget is on the home screen. */
    fun stopTickingIfUnused(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val inUse = listOf(WaktivaDayRingWidget::class.java, WaktivaMyDayWidget::class.java)
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        if (inUse) return
        val alarms = context.getSystemService(AlarmManager::class.java)
        listOf(REQUEST_TICK, REQUEST_OLD_TICK, REQUEST_ENDING).forEach { code ->
            tickIntent(context, code, PendingIntent.FLAG_NO_CREATE)?.let {
                alarms?.cancel(it)
                it.cancel()
            }
        }
    }
}
