package com.ybugmobile.waktiva.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.HijriUtils
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.isLogged
import com.ybugmobile.waktiva.ui.home.composables.MissedRed
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.degrees
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.home.composables.weatherIconRes
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The two day circle widgets. A widget is read in a glance, so they don't show the whole day at
 * once: they show the moment ([Moment]) and change with it.
 *
 *   OPEN    – "Dhuhr time", the time left for it, the bar of its time and "I prayed"
 *   ENDING  – the same in amber in the last half hour of its time, the button filled
 *   PRAYED  – "✓ Dhuhr prayed", the day's tally in gold and its strip of five, the countdown calm
 *   WAITING – after sunrise, the countdown to the next prayer
 *
 * Both sit on the sky of the hour, lit by the moment's prayer. In the 4×2 ([WaktivaDayRingWidget])
 * the button is a tile beside the countdown; in the 4×4 ([WaktivaMyDayWidget]) the moment is a
 * card in the prayer's colour over the day's prayers, a line each, which mark with a tap.
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

    private const val REQUEST_TICK = 7_303

    /** The half-hourly tick the widgets had before the quarter-hourly one; cancelled once seen. */
    private const val REQUEST_OLD_TICK = 7_300
    private const val REQUEST_ENDING = 7_302
    private const val REQUEST_TOGGLE = 7_310

    /** The "my day" widget's lines, one request code per prayer from here. */
    private const val REQUEST_DAY_ROW = 7_340

    private const val COLOR_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_SECONDARY = 0x99FFFFFF.toInt()
    private const val COLOR_ENDING_COUNT = 0xFFFDE68A.toInt()

    /** The green of a prayer whose time is on, as on the prayer log's chips. */
    private val Open = Color(0xFF6EE7B7)

    /** The amber of a prayer whose time is ending. */
    private val Ending = Color(0xFFFBBF24)

    private val SEGMENTS = intArrayOf(R.id.widget_seg_0, R.id.widget_seg_1, R.id.widget_seg_2, R.id.widget_seg_3, R.id.widget_seg_4)

    /** What both widgets show, read once per redraw and shared by every instance. */
    private class Today(
        val day: PrayerDay,
        val moment: Moment,
        val times: List<DayTime>,
        val logEnabled: Boolean,
        val remaining: Float,
        /** Where today's five prayers stand in the log, for the "my day" widget's lines. */
        val statuses: Map<PrayerType, PrayerLogStatus>,
        /** Where the five prayers of the moment's day ([Moment.logDate]) stand, for its tally and strip. */
        val momentStatuses: Map<PrayerType, PrayerLogStatus>,
        val button: WidgetArt.Button
    ) {
        private var tileBitmap: Bitmap? = null
        private var discBitmap: Bitmap? = null

        fun tile(context: Context): Bitmap = tileBitmap ?: WidgetArt.tile(context, button).also { tileBitmap = it }

        fun disc(context: Context): Bitmap = discBitmap ?: WidgetArt.disc(context, button).also { discBitmap = it }
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
        val log = ep.prayerLogRepository()
        val prayed = if (logEnabled) {
            mapOf(
                date to log.getPrayedPrayers(date).first(),
                date.minusDays(1) to log.getPrayedPrayers(date.minusDays(1)).first()
            )
        } else {
            emptyMap()
        }
        val trackedSince = if (logEnabled) PrayerLog.trackedSince(log.getStartDate().first(), date) else date

        val now = snapshot.now
        val moment = DayWidgetModel.moment(day, now, next, prayed, logEnabled)
        val current = DayWidgetModel.currentPrayer(day, now.toLocalTime())

        fun statuses(of: LocalDate): Map<PrayerType, PrayerLogStatus> {
            val logDay = snapshot.days.find { it.date == of } ?: return emptyMap()
            val nextDay = snapshot.days.find { it.date == of.plusDays(1) }
            val marked = prayed[of].orEmpty()
            return LoggedPrayers.associateWith { type ->
                PrayerLog.status(
                    date = of,
                    type = type,
                    prayed = type in marked,
                    now = now,
                    start = logDay.timings[type]?.atDate(of),
                    end = PrayerLog.windowEnd(type, logDay, nextDay),
                    trackedSince = trackedSince
                )
            }
        }
        val statuses = statuses(date)
        val momentStatuses = if (moment.logDate == date) statuses else statuses(moment.logDate)

        return Today(
            day = day,
            moment = moment,
            times = DayWidgetModel.times(day, now, current, prayed[date].orEmpty()),
            logEnabled = logEnabled,
            remaining = moment.remaining(now),
            statuses = statuses,
            momentStatuses = momentStatuses,
            button = button(context, moment, logEnabled, momentStatuses)
        )
    }

    /**
     * What the moment offers beside its countdown: "I prayed" while its prayer waits, the day's
     * tally once it's marked (and while no prayer's time is on), or, with the log off, the sign
     * of the prayer the moment is about.
     */
    private fun button(
        context: Context,
        moment: Moment,
        logEnabled: Boolean,
        statuses: Map<PrayerType, PrayerLogStatus>
    ): WidgetArt.Button {
        val hero = hero(moment)
        return when {
            moment.canMark -> WidgetArt.Button.MarkIt(
                color = arcColor(moment),
                label = context.getString(R.string.prayer_log_mark),
                filled = moment.state == MomentState.ENDING
            )
            logEnabled -> WidgetArt.Button.Tally(
                done = statuses.values.count { it == PrayerLogStatus.PRAYED },
                total = LoggedPrayers.size
            )
            else -> WidgetArt.Button.Sign(hero.accentColor, hero.iconRes, hero.getDisplayName(context))
        }
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

        bindMoment(context, views, snapshot, today, countSp = if (tall) 40f else 32f, showCaption = true)
        views.setImageViewBitmap(R.id.widget_button, today.tile(context))
        return views
    }

    // ── My day (4×4) ──────────────────────────────────────────────────────

    private fun buildMyDay(context: Context, snapshot: WaktivaWidget.Snapshot, today: Today?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_my_day)
        views.setOnClickPendingIntent(android.R.id.background, WaktivaWidget.openAppIntent(context))
        if (today == null) return views.showEmpty(snapshot)
        views.showContent()
        views.setImageViewBitmap(R.id.widget_bg, background(snapshot, today.moment, Backdrop.LARGE))

        val locale = locale(context)
        val dateFormat = runCatching { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale) }
            .getOrElse { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
        views.setTextViewText(R.id.widget_date, today.day.date.format(dateFormat))
        val hijri = hijri(context, today.day.date, snapshot.days)
        views.setViewVisibility(R.id.widget_hijri, if (hijri != null) View.VISIBLE else View.GONE)
        hijri?.let { views.setTextViewText(R.id.widget_hijri, it) }

        bindMoment(context, views, snapshot, today, countSp = 30f, showCaption = false)
        val card = if (today.moment.state == MomentState.PRAYED) PrayedGold else arcColor(today.moment)
        views.setInt(R.id.widget_hero_bg, "setColorFilter", card.toArgb())
        views.setImageViewBitmap(R.id.widget_button, today.disc(context))

        views.removeAllViews(R.id.widget_times)
        today.times.forEach { time ->
            views.addView(R.id.widget_times, dayRow(context, today, time))
            if (time.type == PrayerType.FAJR) sunRow(context, today.day)?.let { views.addView(R.id.widget_times, it) }
        }
        return views
    }

    /** The Hijri date of [date], as the home screen writes it: "23 Rabi‘ al-Thani 1448 AH". */
    private fun hijri(context: Context, date: LocalDate, days: List<PrayerDay>): String? {
        val hijri = HijriUtils.getEffectiveHijriDate(date, days) ?: return null
        val res = context.resources.getIdentifier("hijri_month_${hijri.monthNumber}", "string", context.packageName)
        val month = if (res != 0) context.getString(res) else hijri.monthEn
        if (month.isBlank()) return null
        val locale = locale(context)
        return String.format(locale, "%d %s %d %s", hijri.day, month, hijri.year, context.getString(R.string.hijri_suffix))
    }

    /**
     * One of the day's prayers as a line. With the log on, a prayer whose time has come (the
     * current one or an earlier one) is marked or unmarked with a tap: a gold tick once prayed,
     * a red cross once its time went by unmarked, a ring in its colour while its time is on.
     */
    private fun dayRow(context: Context, today: Today, time: DayTime): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_day_row)
        row.setTextViewText(R.id.widget_row_name, time.type.getDisplayName(context))
        row.setTextViewText(R.id.widget_row_time, time.time.format(WaktivaWidget.timeFormatter))

        val status = today.statuses[time.type]
        val accent = if (time.isCurrent) arcColor(today.moment) else time.type.accentColor
        val mark = when {
            !today.logEnabled -> if (time.isCurrent) WidgetArt.Mark.ACTIVE else WidgetArt.Mark.QUIET
            status == PrayerLogStatus.PRAYED -> WidgetArt.Mark.PRAYED
            status == PrayerLogStatus.MISSED -> WidgetArt.Mark.MISSED
            status == PrayerLogStatus.ACTIVE || time.isCurrent -> WidgetArt.Mark.ACTIVE
            else -> WidgetArt.Mark.UPCOMING
        }
        row.setImageViewBitmap(R.id.widget_row_mark, WidgetArt.mark(context, mark, if (mark == WidgetArt.Mark.QUIET) Color.White else accent))

        val (nameColor, clockColor) = when {
            mark == WidgetArt.Mark.PRAYED -> PrayedGold.toArgb() to PrayedGold.toArgb()
            time.isCurrent -> COLOR_PRIMARY to COLOR_PRIMARY
            time.isPassed -> COLOR_SECONDARY to COLOR_SECONDARY
            else -> COLOR_SECONDARY to COLOR_PRIMARY
        }
        row.setTextColor(R.id.widget_row_name, nameColor)
        row.setTextColor(R.id.widget_row_time, clockColor)
        if (time.isCurrent) row.setInt(R.id.widget_row, "setBackgroundResource", R.drawable.widget_row_current)

        val markable = today.logEnabled && (time.isCurrent || time.isPassed)
        if (markable) {
            val prayerName = time.type.getPrayerName(context)
            row.setOnClickPendingIntent(R.id.widget_row, toggleIntent(context, today.day.date, time.type, REQUEST_DAY_ROW))
            row.setContentDescription(
                R.id.widget_row,
                if (time.isPrayed) "$prayerName. ${context.getString(R.string.prayer_log_unmark)}"
                else "${context.getString(R.string.prayer_log_mark)}: $prayerName"
            )
        }
        return row
    }

    /** Sunrise, quietly, between Fajr and Dhuhr: the end of Fajr's time, never marked. */
    private fun sunRow(context: Context, day: PrayerDay): RemoteViews? {
        val time = day.timings[PrayerType.SUNRISE] ?: return null
        return RemoteViews(context.packageName, R.layout.widget_day_sun_row).apply {
            setImageViewResource(R.id.widget_sun_icon, PrayerType.SUNRISE.iconRes)
            setTextViewText(R.id.widget_sun_name, PrayerType.SUNRISE.getDisplayName(context))
            setTextViewText(R.id.widget_sun_time, time.format(WaktivaWidget.timeFormatter))
        }
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
     * The moment: its label, the live countdown to the end of the prayer's time (the next
     * prayer's start, so one figure serves both), the bar of the prayer's time so far (the day's
     * strip once it's prayed, or while none is on), with [showCaption] when it began and what
     * comes next, the weather now, and the button's tap: "I prayed", or taking the mark back.
     */
    private fun bindMoment(
        context: Context,
        views: RemoteViews,
        snapshot: WaktivaWidget.Snapshot,
        today: Today,
        countSp: Float,
        showCaption: Boolean
    ) {
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
        views.setTextColor(
            R.id.widget_label,
            when (moment.state) {
                MomentState.ENDING -> Ending.toArgb()
                MomentState.PRAYED -> PrayedGold.toArgb()
                else -> 0xE6FFFFFF.toInt()
            }
        )
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

        // The prayer's time so far while it's on; the day's five once it's prayed or none is on.
        views.setViewVisibility(R.id.widget_window, if (counting) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_segments, if (counting) View.GONE else View.VISIBLE)
        if (counting) {
            views.setProgressBar(R.id.widget_window, 1000, ((1f - today.remaining) * 1000).toInt(), false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.widget_window, "setProgressTintList", ColorStateList.valueOf(arcColor(moment).toArgb()))
            }
        } else {
            LoggedPrayers.forEachIndexed { i, type -> views.setInt(SEGMENTS[i], "setColorFilter", segmentColor(today, type)) }
        }

        if (showCaption) {
            val from = moment.startsAt?.takeIf { counting }?.toLocalTime()?.format(WaktivaWidget.timeFormatter)
            views.setTextViewText(R.id.widget_from, from.orEmpty())
            views.setImageViewResource(R.id.widget_next_icon, moment.next.type.iconRes)
            views.setInt(R.id.widget_next_icon, "setColorFilter", moment.next.type.accentColor.toArgb())
            views.setTextViewText(
                R.id.widget_sub,
                "${moment.next.type.getDisplayName(context)}  ${moment.next.time.format(WaktivaWidget.timeFormatter)}"
            )
        }

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

    /**
     * A prayer's piece of the day's strip: gold once prayed, red once missed, the prayer's colour
     * while its time is on; with the log off, bright once its time has come.
     */
    private fun segmentColor(today: Today, type: PrayerType): Int {
        if (!today.logEnabled) {
            val come = today.times.find { it.type == type }?.let { it.isCurrent || it.isPassed } == true
            return if (come) 0x99FFFFFF.toInt() else 0x2EFFFFFF
        }
        return when (today.momentStatuses[type]) {
            PrayerLogStatus.PRAYED -> PrayedGold.toArgb()
            PrayerLogStatus.MISSED -> MissedRed.copy(alpha = 0.75f).toArgb()
            PrayerLogStatus.ACTIVE -> type.accentColor.toArgb()
            else -> 0x2EFFFFFF
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
     * Redraws the widgets every quarter hour, so the bar of the time left and the day's lines
     * keep up with the day. The alarm doesn't wake the phone: it waits for the screen to be on,
     * when the widgets are seen.
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
