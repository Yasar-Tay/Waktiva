package com.ybugmobile.waktiva.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.waktiva.domain.manager.TimeManager
import com.ybugmobile.waktiva.domain.model.HijriData
import com.ybugmobile.waktiva.domain.model.HijriUtils
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.usecase.GetNextPrayerUseCase
import com.ybugmobile.waktiva.ui.theme.getGradientColorsForTime
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * iOS-style home-screen widget in four families:
 *   COMPACT (4×1) – single row: icon, next prayer and time, live countdown
 *   SMALL   (2×2) – next prayer, its time, a live countdown and a six-segment day timeline
 *   MEDIUM  (4×2) – the small block plus the whole day's list
 *   LARGE   (4×4) – location and dates header, hero countdown, timeline and the day's list
 *
 * This is a plain [AppWidgetProvider] (no Glance). The update path is:
 *   external trigger (PrayerAlarmReceiver / MainActivity / onUpdate)
 *       → [updateAll] (suspend, Dispatchers.IO)
 *           → Room one-shot read
 *           → RemoteViews assembly
 *           → AppWidgetManager.updateAppWidget()   ← DEAD END, no feedback loop
 *
 * AppWidgetManager.updateAppWidget() does NOT call onUpdate(), so the ~10 Hz loop seen
 * with Glance on Android 15 is structurally impossible here.
 *
 * On Android 12+ every family is sent at once and the launcher picks the one that fits;
 * older versions get the family matching the reported size.
 *
 * [WaktivaCountdownWidget], the small countdown tile, shares this data and refresh path:
 * [updateAll] renders both.
 */
class WaktivaWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun prayerRepository(): PrayerRepository
        fun getNextPrayerUseCase(): GetNextPrayerUseCase
        fun timeManager(): TimeManager
        fun settingsManager(): SettingsManagerInterface
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                render(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    /** Below Android 12 the layout family depends on the size, so re-render on resize. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                render(context, appWidgetManager, intArrayOf(appWidgetId))
            } finally {
                pending.finish()
            }
        }
    }

    /** Everything a render needs, read once and shared by every widget instance. */
    private class Snapshot(
        val nextPrayer: NextPrayer?,
        val rows: List<WidgetPrayerRow>,
        val chronometer: WidgetChronometerState?,
        val background: Bitmap,
        /** The sky of the hour, top to bottom, which the countdown widget lights in its own way. */
        val skyColors: List<Int>,
        val locationName: String,
        val today: LocalDate,
        val hijri: HijriData?
    )

    companion object {

        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        private val segmentIds = intArrayOf(
            R.id.widget_seg_1, R.id.widget_seg_2, R.id.widget_seg_3,
            R.id.widget_seg_4, R.id.widget_seg_5, R.id.widget_seg_6
        )

        // Text colours for the day list: iOS primary / secondary / tertiary labels on dark glass.
        private const val COLOR_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_SECONDARY = 0xE6FFFFFF.toInt()
        private const val COLOR_TERTIARY = 0x73FFFFFF

        private const val ALPHA_OPAQUE = 255
        private const val ALPHA_PASSED_ICON = 115
        private const val ALPHA_UPCOMING_SEGMENT = 64

        private const val DENSE_ROW_TEXT_SP = 12.5f

        @Volatile private var cachedGradientKey: String? = null
        @Volatile private var cachedBitmap: Bitmap? = null
        @Volatile private var cachedCountdownKey: String? = null
        @Volatile private var cachedCountdownBitmap: Bitmap? = null

        // Keep the same Chronometer base for the same prayer so re-renders don't jitter.
        @Volatile private var cachedPrayerKey: String = ""
        @Volatile private var cachedBaseTime: Long = 0L

        /** Push a fresh frame to every instance of this widget and of the countdown widget. */
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WaktivaWidget::class.java))
            val countdownIds = manager.getAppWidgetIds(ComponentName(context, WaktivaCountdownWidget::class.java))
            if (ids.isEmpty() && countdownIds.isEmpty()) return
            val snapshot = loadSnapshot(context)
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, manager, id, snapshot)) }
            countdownIds.forEach { id -> manager.updateAppWidget(id, buildCountdown(context, snapshot)) }
        }

        /** Renders the countdown widget instances [ids]; used by [WaktivaCountdownWidget]. */
        internal suspend fun renderCountdown(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val snapshot = loadSnapshot(context)
            ids.forEach { id -> manager.updateAppWidget(id, buildCountdown(context, snapshot)) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val snapshot = loadSnapshot(context)
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, manager, id, snapshot)) }
        }

        private suspend fun loadSnapshot(context: Context): Snapshot {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )

            val days = ep.prayerRepository().getPrayerDays().first()
            val now = ep.timeManager().now()
            val today = days.find { it.date == now.toLocalDate() }
            val tomorrow = days.find { it.date == now.toLocalDate().plusDays(1) }
            val nextPrayer = ep.getNextPrayerUseCase()(today, tomorrow, now)

            val chronometer = WidgetChronometerResolver.resolve(
                nextPrayer = nextPrayer,
                nowEpochMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                elapsedRealtime = SystemClock.elapsedRealtime(),
                cachedPrayerKey = cachedPrayerKey,
                cachedBaseTime = cachedBaseTime
            )
            cachedPrayerKey = chronometer?.cacheKey ?: ""
            cachedBaseTime = chronometer?.baseTime ?: 0L

            val skyColors = getGradientColorsForTime(now.toLocalTime(), today).map { it.toArgb() }
            return Snapshot(
                nextPrayer = nextPrayer,
                rows = WidgetDayModel.rows(days, nextPrayer, now),
                chronometer = chronometer,
                background = gradientBitmap(skyColors),
                skyColors = skyColors,
                locationName = ep.settingsManager().settingsFlow.first().locationName,
                today = now.toLocalDate(),
                hijri = HijriUtils.getEffectiveHijriDate(now.toLocalDate(), days)
            )
        }

        // ── RemoteViews assembly ───────────────────────────────────────────────

        private fun buildViews(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            snapshot: Snapshot
        ): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return RemoteViews(
                    WidgetSize.entries.associate { size ->
                        size.minSize() to buildFamily(context, size, snapshot)
                    }
                )
            }
            // Portrait cell size: min width × max height (AppWidgetManager docs).
            val options = manager.getAppWidgetOptions(widgetId)
            val size = WidgetSize.from(
                widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            )
            return buildFamily(context, size, snapshot)
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun WidgetSize.minSize(): SizeF = when (this) {
            WidgetSize.COMPACT -> SizeF(WidgetSize.MIN_WIDTH_DP, WidgetSize.COMPACT_MIN_HEIGHT_DP)
            WidgetSize.SMALL -> SizeF(WidgetSize.MIN_WIDTH_DP, WidgetSize.SQUARE_MIN_HEIGHT_DP)
            WidgetSize.MEDIUM -> SizeF(WidgetSize.WIDE_MIN_WIDTH_DP, WidgetSize.SQUARE_MIN_HEIGHT_DP)
            WidgetSize.LARGE -> SizeF(WidgetSize.WIDE_MIN_WIDTH_DP, WidgetSize.LARGE_MIN_HEIGHT_DP)
        }

        private fun buildFamily(context: Context, size: WidgetSize, snapshot: Snapshot): RemoteViews {
            @LayoutRes val layout = when (size) {
                WidgetSize.COMPACT -> R.layout.widget_compact
                WidgetSize.SMALL -> R.layout.widget_small
                WidgetSize.MEDIUM -> R.layout.widget_medium
                WidgetSize.LARGE -> R.layout.widget_large
            }
            val views = RemoteViews(context.packageName, layout)

            views.setImageViewBitmap(R.id.widget_bg, snapshot.background)
            views.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))

            val next = snapshot.nextPrayer
            if (next == null) {
                views.setViewVisibility(R.id.widget_content, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                return views
            }
            views.setViewVisibility(R.id.widget_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)

            views.setTextViewText(R.id.widget_name, next.type.getDisplayName(context))
            views.setTextViewText(R.id.widget_time, next.time.format(timeFormatter))
            views.setImageViewResource(R.id.widget_icon, iconFor(next.type))

            val chronometer = snapshot.chronometer
            views.setChronometer(
                R.id.widget_chrono,
                chronometer?.baseTime ?: SystemClock.elapsedRealtime(),
                null,
                chronometer?.isRunning == true
            )
            views.setChronometerCountDown(R.id.widget_chrono, true)

            if (size != WidgetSize.COMPACT) {
                bindTimeline(views, snapshot.rows)
            }

            if (size == WidgetSize.MEDIUM || size == WidgetSize.LARGE) {
                val dense = size == WidgetSize.MEDIUM
                views.removeAllViews(R.id.widget_rows)
                snapshot.rows.forEach { row ->
                    views.addView(R.id.widget_rows, buildRow(context, row, dense))
                }
            }

            if (size == WidgetSize.LARGE) {
                views.setTextViewText(
                    R.id.widget_location,
                    snapshot.locationName.ifBlank { context.getString(R.string.app_name) }
                )
                views.setTextViewText(R.id.widget_date, dateLine(context, snapshot.today, snapshot.hijri))
            }

            return views
        }

        /**
         * The countdown widget: the next prayer's name and time in a small top line, the countdown
         * as large as the cell allows, and a stroke of the prayer's colour, on the sky of the hour
         * lit by that colour (see [countdownBackground]).
         */
        private fun buildCountdown(context: Context, snapshot: Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown_small)
            views.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))

            val next = snapshot.nextPrayer
            if (next == null) {
                views.setImageViewBitmap(R.id.widget_bg, snapshot.background)
                views.setViewVisibility(R.id.widget_content, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                return views
            }
            views.setViewVisibility(R.id.widget_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)

            val accent = accentFor(next.type)
            views.setImageViewBitmap(R.id.widget_bg, countdownBackground(snapshot.skyColors, accent))
            views.setTextViewText(R.id.widget_name, next.type.getDisplayName(context))
            views.setTextViewText(R.id.widget_time, next.time.format(timeFormatter))
            views.setImageViewResource(R.id.widget_icon, iconFor(next.type))
            views.setInt(R.id.widget_accent, "setColorFilter", accent)

            val chronometer = snapshot.chronometer
            views.setChronometer(
                R.id.widget_chrono,
                chronometer?.baseTime ?: SystemClock.elapsedRealtime(),
                null,
                chronometer?.isRunning == true
            )
            views.setChronometerCountDown(R.id.widget_chrono, true)
            return views
        }

        private fun bindTimeline(views: RemoteViews, rows: List<WidgetPrayerRow>) {
            segmentIds.forEachIndexed { index, id ->
                val row = rows.getOrNull(index)
                if (row == null) {
                    views.setViewVisibility(id, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(id, View.VISIBLE)
                val isNext = row.status == WidgetRowStatus.NEXT
                views.setImageViewResource(
                    id,
                    if (isNext) R.drawable.widget_segment_active else R.drawable.widget_segment
                )
                views.setInt(
                    id,
                    "setImageAlpha",
                    if (row.status == WidgetRowStatus.UPCOMING) ALPHA_UPCOMING_SEGMENT else ALPHA_OPAQUE
                )
            }
        }

        private fun buildRow(context: Context, row: WidgetPrayerRow, dense: Boolean): RemoteViews {
            val isNext = row.status == WidgetRowStatus.NEXT
            val views = RemoteViews(
                context.packageName,
                if (isNext) R.layout.widget_row_next else R.layout.widget_row
            )
            views.setTextViewText(R.id.widget_row_name, row.type.getDisplayName(context))
            views.setTextViewText(R.id.widget_row_time, row.time.format(timeFormatter))

            val color = when (row.status) {
                WidgetRowStatus.PASSED -> COLOR_TERTIARY
                WidgetRowStatus.NEXT -> COLOR_PRIMARY
                WidgetRowStatus.UPCOMING -> COLOR_SECONDARY
            }
            views.setTextColor(R.id.widget_row_name, color)
            views.setTextColor(R.id.widget_row_time, color)

            if (dense) {
                // The 4×2 list has ~20 dp per row: drop the icon and tighten the type.
                views.setViewVisibility(R.id.widget_row_icon, View.GONE)
                views.setTextViewTextSize(R.id.widget_row_name, TypedValue.COMPLEX_UNIT_SP, DENSE_ROW_TEXT_SP)
                views.setTextViewTextSize(R.id.widget_row_time, TypedValue.COMPLEX_UNIT_SP, DENSE_ROW_TEXT_SP)
            } else {
                views.setImageViewResource(R.id.widget_row_icon, iconFor(row.type))
                views.setInt(
                    R.id.widget_row_icon,
                    "setImageAlpha",
                    if (row.status == WidgetRowStatus.PASSED) ALPHA_PASSED_ICON else ALPHA_OPAQUE
                )
            }
            return views
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** "Thursday, 25 September · 3 Rabi‘ al-Awwal 1448" in the app's current language. */
        private fun dateLine(context: Context, today: LocalDate, hijri: HijriData?): String {
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val gregorian = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
            val hijriMonth = hijri?.let { hijriMonthRes(it.monthNumber) } ?: return gregorian
            return "$gregorian · ${hijri.day} ${context.getString(hijriMonth)} ${hijri.year}"
        }

        private fun hijriMonthRes(month: Int): Int? = when (month) {
            1 -> R.string.hijri_month_1
            2 -> R.string.hijri_month_2
            3 -> R.string.hijri_month_3
            4 -> R.string.hijri_month_4
            5 -> R.string.hijri_month_5
            6 -> R.string.hijri_month_6
            7 -> R.string.hijri_month_7
            8 -> R.string.hijri_month_8
            9 -> R.string.hijri_month_9
            10 -> R.string.hijri_month_10
            11 -> R.string.hijri_month_11
            12 -> R.string.hijri_month_12
            else -> null
        }

        @DrawableRes
        private fun iconFor(type: PrayerType): Int = when (type) {
            PrayerType.FAJR -> R.drawable.haze_day_rotated
            PrayerType.SUNRISE -> R.drawable.sunrise
            PrayerType.DHUHR -> R.drawable.clear_day
            PrayerType.ASR -> R.drawable.clear_day
            PrayerType.MAGHRIB -> R.drawable.sunset
            PrayerType.ISHA -> R.drawable.clear_night
        }

        /** Each prayer's colour, as on the day circle, which lights the countdown widget. */
        private fun accentFor(type: PrayerType): Int = when (type) {
            PrayerType.FAJR -> 0xFF81D4FA.toInt()
            PrayerType.SUNRISE -> 0xFFFFE082.toInt()
            PrayerType.DHUHR -> 0xFFFFF59D.toInt()
            PrayerType.ASR -> 0xFFFFCC80.toInt()
            PrayerType.MAGHRIB -> 0xFFCE93D8.toInt()
            PrayerType.ISHA -> 0xFF9FA8DA.toInt()
        }

        /**
         * The countdown widget's background: the sky of the hour, shaded towards the left where the
         * countdown sits so white figures read on a bright day, and a glow of the next prayer's
         * [accent] rising from the top right corner. Cached per sky and prayer.
         */
        private fun countdownBackground(sky: List<Int>, accent: Int): Bitmap {
            val key = sky.joinToString(",") + "|" + accent
            cachedCountdownBitmap?.takeIf { key == cachedCountdownKey }?.let { return it }

            val width = 240f
            val height = 120f
            val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawBitmap(gradientBitmap(sky).let { Bitmap.createScaledBitmap(it, width.toInt(), height.toInt(), true) }, 0f, 0f, null)
            canvas.drawRect(0f, 0f, width, height, Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(0x47000000, 0x14000000, 0x00000000), floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            })
            canvas.drawRect(0f, 0f, width, height, Paint().apply {
                shader = RadialGradient(
                    width * 0.92f, height * 0.05f, width * 0.75f,
                    intArrayOf(withAlpha(accent, 0x8C), withAlpha(accent, 0x26), withAlpha(accent, 0)),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            })

            cachedCountdownBitmap = bitmap
            cachedCountdownKey = key
            return bitmap
        }

        private fun withAlpha(color: Int, alpha: Int) = (alpha shl 24) or (color and 0x00FFFFFF)

        /** Vertical sky gradient; cached because the palette only changes at day-phase boundaries. */
        private fun gradientBitmap(colors: List<Int>): Bitmap {
            val key = colors.joinToString(",")
            cachedBitmap?.takeIf { key == cachedGradientKey }?.let { return it }

            val width = 96
            val height = 192
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply {
                if (colors.size > 1) {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        colors.toIntArray(), null, Shader.TileMode.CLAMP
                    )
                } else {
                    color = colors.firstOrNull() ?: android.graphics.Color.BLACK
                }
            }
            Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            cachedBitmap = bitmap
            cachedGradientKey = key
            return bitmap
        }
    }
}
