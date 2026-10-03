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
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.local.WeatherCache
import com.ybugmobile.waktiva.data.notification.PrayerLogGameNotifier
import com.ybugmobile.waktiva.data.worker.WidgetWeatherWorker
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.waktiva.domain.manager.TimeManager
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerLogRepository
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * iOS-style 2×2 home-screen widget: the next prayer, its time, a live countdown and a
 * six-segment day timeline. The wider sizes are their own widgets ([WaktivaCountdownWidget],
 * [WaktivaDayRingWidget], [WaktivaMyDayWidget]); a widget still placed short from the earlier
 * design (COMPACT) gets the countdown bar.
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
 * [WaktivaCountdownWidget], the small countdown tile, the day circle widgets
 * ([WaktivaDayRingWidget], [WaktivaMyDayWidget]) and the prayer log widget
 * ([WaktivaPrayerLogWidget]) share this data and refresh path: [updateAll] renders them all.
 */
class WaktivaWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun prayerRepository(): PrayerRepository
        fun getNextPrayerUseCase(): GetNextPrayerUseCase
        fun timeManager(): TimeManager
        fun settingsManager(): SettingsManagerInterface
        fun prayerLogRepository(): PrayerLogRepository
        fun weatherCache(): WeatherCache
        fun prayerLogGame(): PrayerLogGameNotifier
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
    internal class Snapshot(
        val days: List<PrayerDay>,
        val now: LocalDateTime,
        val nextPrayer: NextPrayer?,
        val rows: List<WidgetPrayerRow>,
        val chronometer: WidgetChronometerState?,
        val background: Bitmap,
        /** The sky of the hour, top to bottom, which the countdown widget lights in its own way. */
        val skyColors: List<Int>,
        val today: LocalDate,
        /** The weather now, from the cache the app's fetches fill; null without one. */
        val weather: WidgetWeather?,
        /** The clouds or stars the backgrounds draw over the sky. */
        val atmosphere: Atmosphere
    )

    companion object {

        internal val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        private val segmentIds = intArrayOf(
            R.id.widget_seg_1, R.id.widget_seg_2, R.id.widget_seg_3,
            R.id.widget_seg_4, R.id.widget_seg_5, R.id.widget_seg_6
        )

        private const val ALPHA_OPAQUE = 255
        private const val ALPHA_UPCOMING_SEGMENT = 64

        @Volatile private var cachedGradientKey: String? = null
        @Volatile private var cachedBitmap: Bitmap? = null
        /** The lit backgrounds of the hour, by sky, colour and [Backdrop]; a handful at most. */
        private val litCache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 8
        }

        // Keep the same Chronometer base for the same prayer so re-renders don't jitter.
        @Volatile private var cachedPrayerKey: String = ""
        @Volatile private var cachedBaseTime: Long = 0L

        /**
         * Push a fresh frame to every instance of this widget, of the countdown widget and of the
         * day circle widgets ([DayWidgets]).
         */
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WaktivaWidget::class.java))
            val countdownIds = manager.getAppWidgetIds(ComponentName(context, WaktivaCountdownWidget::class.java))
            val ringIds = manager.getAppWidgetIds(ComponentName(context, WaktivaDayRingWidget::class.java))
            val dayIds = manager.getAppWidgetIds(ComponentName(context, WaktivaMyDayWidget::class.java))
            val logIds = manager.getAppWidgetIds(ComponentName(context, WaktivaPrayerLogWidget::class.java))
            if (ids.isEmpty() && countdownIds.isEmpty() && ringIds.isEmpty() && dayIds.isEmpty() && logIds.isEmpty()) return
            val snapshot = loadSnapshot(context)
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, manager, id, snapshot)) }
            countdownIds.forEach { id -> manager.updateAppWidget(id, buildCountdown(context, snapshot)) }
            DayWidgets.render(context, manager, ringIds, dayIds, snapshot)
            PrayerLogWidget.render(context, manager, logIds, snapshot)
        }

        /** Renders the prayer log widget instances [ids]; used by [WaktivaPrayerLogWidget]. */
        internal suspend fun renderPrayerLog(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            PrayerLogWidget.render(context, manager, ids, loadSnapshot(context))
        }

        /** Renders the day circle widget instances; used by their providers. */
        internal suspend fun renderDayWidgets(context: Context, manager: AppWidgetManager, ringIds: IntArray, dayIds: IntArray) {
            if (ringIds.isEmpty() && dayIds.isEmpty()) return
            DayWidgets.render(context, manager, ringIds, dayIds, loadSnapshot(context))
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

        internal fun entryPoint(context: Context): WidgetEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )

        private suspend fun loadSnapshot(context: Context): Snapshot {
            val ep = entryPoint(context)

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
            val cache = ep.weatherCache().load()
            WidgetWeatherWorker.requestIfStale(context, cache?.fetchedAtMillis)
            val weather = DayWidgetModel.weatherNow(cache, now, System.currentTimeMillis())
            val sunrise = today?.timings?.get(PrayerType.SUNRISE)
            val maghrib = today?.timings?.get(PrayerType.MAGHRIB)
            val isDay = weather?.isDay
                ?: (sunrise != null && maghrib != null && !now.toLocalTime().isBefore(sunrise) && now.toLocalTime().isBefore(maghrib))
            return Snapshot(
                days = days,
                now = now,
                nextPrayer = nextPrayer,
                rows = WidgetDayModel.rows(days, nextPrayer, now),
                chronometer = chronometer,
                background = gradientBitmap(skyColors),
                skyColors = skyColors,
                today = now.toLocalDate(),
                weather = weather,
                atmosphere = Atmosphere(weather?.condition, isDay)
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
        }

        private fun buildFamily(context: Context, size: WidgetSize, snapshot: Snapshot): RemoteViews {
            if (size == WidgetSize.COMPACT) return buildCountdown(context, snapshot)
            val views = RemoteViews(context.packageName, R.layout.widget_small)

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

            views.setImageViewBitmap(R.id.widget_bg, litBackground(snapshot.skyColors, accentFor(next.type), Backdrop.SQUARE, snapshot.atmosphere))
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

            bindTimeline(views, snapshot.rows)

            return views
        }

        /**
         * The countdown widget, a 4×1 bar: the next prayer's name, time and a stroke of its colour
         * on the left, the countdown at the bar's full height on the right, on the sky of the hour
         * lit by that colour (see [litBackground]).
         */
        private fun buildCountdown(context: Context, snapshot: Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown_bar)
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
            views.setImageViewBitmap(R.id.widget_bg, litBackground(snapshot.skyColors, accent, Backdrop.BAR, snapshot.atmosphere))
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

        // ── Helpers ───────────────────────────────────────────────────────────

        internal fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        @DrawableRes
        internal fun iconFor(type: PrayerType): Int = when (type) {
            PrayerType.FAJR -> R.drawable.haze_day_rotated
            PrayerType.SUNRISE -> R.drawable.sunrise
            PrayerType.DHUHR -> R.drawable.clear_day
            PrayerType.ASR -> R.drawable.clear_day
            PrayerType.MAGHRIB -> R.drawable.sunset
            PrayerType.ISHA -> R.drawable.clear_night
        }

        /** Each prayer's colour, as on the day circle, which lights the countdown widget. */
        internal fun accentFor(type: PrayerType): Int = when (type) {
            PrayerType.FAJR -> 0xFF81D4FA.toInt()
            PrayerType.SUNRISE -> 0xFFFFE082.toInt()
            PrayerType.DHUHR -> 0xFFFFF59D.toInt()
            PrayerType.ASR -> 0xFFFFCC80.toInt()
            PrayerType.MAGHRIB -> 0xFFCE93D8.toInt()
            PrayerType.ISHA -> 0xFF9FA8DA.toInt()
        }

        /**
         * A widget's background, as the countdown bar first had it: the sky of the hour with the
         * home screen's clouds or stars ([atmosphere]), shaded away from the text, and a glow of the
         * prayer's [accent] spreading behind it, laid out for the widget's [backdrop]. Cached per
         * sky, weather, colour and backdrop.
         */
        internal fun litBackground(sky: List<Int>, accent: Int, backdrop: Backdrop, atmosphere: Atmosphere): Bitmap {
            val key = sky.joinToString(",") + "|" + accent + "|" + backdrop + "|" + atmosphere
            synchronized(litCache) { litCache[key] }?.let { return it }

            val width = backdrop.width.toFloat()
            val height = backdrop.height.toFloat()
            val bitmap = Bitmap.createBitmap(backdrop.width, backdrop.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawBitmap(Bitmap.createScaledBitmap(gradientBitmap(sky), backdrop.width, backdrop.height, true), 0f, 0f, null)
            WidgetArt.drawAtmosphere(canvas, backdrop.width, backdrop.height, atmosphere.condition, atmosphere.isDay)
            canvas.drawRect(0f, 0f, width, height, Paint().apply {
                shader = LinearGradient(
                    backdrop.shadeFrom.first * width, backdrop.shadeFrom.second * height,
                    backdrop.shadeTo.first * width, backdrop.shadeTo.second * height,
                    intArrayOf(0x00000000, 0x14000000, 0x47000000), floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            })
            canvas.drawRect(0f, 0f, width, height, Paint().apply {
                shader = RadialGradient(
                    width * backdrop.glowX, height * backdrop.glowY, width * backdrop.reach,
                    intArrayOf(withAlpha(accent, 0x8C), withAlpha(accent, 0x26), withAlpha(accent, 0)),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            })

            synchronized(litCache) { litCache[key] = bitmap }
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

/**
 * Where a widget's main text sits, which its background is lit behind: the bitmap's size in
 * the widget's proportions, the glow's centre and reach, and the line the shade deepens
 * along, away from the glow, so white figures read on a bright day. Fractions of the size;
 * the reach is of the width.
 */
internal enum class Backdrop(
    val width: Int,
    val height: Int,
    val glowX: Float,
    val glowY: Float,
    val reach: Float,
    val shadeFrom: Pair<Float, Float>,
    val shadeTo: Pair<Float, Float>
) {
    /** 4×1: the prayer's name on the left, the countdown on the right. */
    BAR(400, 100, 0.08f, 0.5f, 0.5f, 0f to 0f, 1f to 0f),

    /** 2×2: the next prayer at the top left. */
    SQUARE(200, 200, 0.12f, 0.2f, 0.95f, 0.12f to 0.2f, 1f to 1f),

    /** 4×2: the prayer and its countdown on the left. */
    WIDE(400, 200, 0.08f, 0.4f, 0.6f, 0.08f to 0.4f, 1f to 1f),

    /** 4×4: the prayer and its countdown along the top. */
    LARGE(400, 400, 0.1f, 0.14f, 0.75f, 0.1f to 0.14f, 1f to 1f)
}

/** The weather a widget's sky shows: clouds for [condition], or stars on a clear night. */
internal data class Atmosphere(val condition: WeatherCondition?, val isDay: Boolean)
