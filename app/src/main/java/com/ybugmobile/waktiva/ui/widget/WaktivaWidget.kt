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
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.manager.TimeManager
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.usecase.GetWidgetNextPrayerUseCase
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-screen widget that shows the next prayer name, time, and a live countdown.
 *
 * This is a plain [AppWidgetProvider] (no Glance).  The update path is:
 *   external trigger (PrayerAlarmReceiver / onUpdate)
 *       → [updateAll] (suspend, Dispatchers.IO)
 *           → Room one-shot read
 *           → [buildViews] assembles RemoteViews
 *           → AppWidgetManager.updateAppWidget()   ← DEAD END, no feedback loop
 *
 * AppWidgetManager.updateAppWidget() does NOT call onUpdate(). There is no way
 * for an update to schedule another update, so the ~10 Hz loop seen with Glance
 * on Android 15 is structurally impossible here.
 */
class WaktivaWidget : AppWidgetProvider() {

    // ── Hilt EntryPoint ────────────────────────────────────────────────────────
    // AppWidgetProvider is a BroadcastReceiver; we use EntryPointAccessors rather
    // than @AndroidEntryPoint because we need the deps inside a suspend context.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun prayerRepository(): PrayerRepository
        fun getWidgetNextPrayerUseCase(): GetWidgetNextPrayerUseCase
        fun timeManager(): TimeManager
    }

    // ── AppWidgetProvider callbacks ────────────────────────────────────────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // goAsync() extends the BroadcastReceiver deadline while we query Room.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                appWidgetIds.forEach { id ->
                    buildAndApply(context, appWidgetManager, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Re-render whenever the user resizes the widget so the font scales correctly. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                buildAndApply(context, appWidgetManager, appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    // ── Public update entry-point (called from PrayerAlarmReceiver) ────────────

    companion object {

        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        // Gradient bitmap cache — a new Bitmap is expensive; reuse when colours are unchanged.
        @Volatile private var cachedGradientKey: String? = null
        @Volatile private var cachedBitmap: Bitmap?      = null

        // Chronometer base-time cache — SystemClock.elapsedRealtime() advances every ms;
        // keep the same Long for the same prayer so consecutive renders are identical.
        @Volatile private var cachedPrayerKey:  String = ""
        @Volatile private var cachedBaseTime:   Long   = 0L

        /**
         * Push a fresh frame to every widget instance.
         * Suspend — call from a coroutine (PrayerAlarmReceiver already does this).
         */
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WaktivaWidget::class.java)
            )
            ids.forEach { id -> buildAndApply(context, manager, id) }
        }

        // ── Core build function ────────────────────────────────────────────────

        private suspend fun buildAndApply(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )

            // One-shot reads — no Flow subscription, no recomposition, no loop.
            val prayerDays = ep.prayerRepository().getPrayerDays().first()
            val now        = ep.timeManager().currentTime.value
            val today      = prayerDays.find { it.date == now.toLocalDate() }
            val tomorrow   = prayerDays.find { it.date == now.toLocalDate().plusDays(1) }
            val nextPrayer = ep.getWidgetNextPrayerUseCase()(today, tomorrow, now)

            // Gradient bitmap — reuse the cached instance when the colour palette is unchanged.
            val colors      = getGradientColorsForTime(now.toLocalTime(), today).take(2)
            val gradientKey = colors.joinToString(",") { it.value.toString() }
            val bitmap = if (gradientKey == cachedGradientKey && cachedBitmap != null) {
                cachedBitmap!!
            } else {
                buildGradientBitmap(colors).also {
                    cachedBitmap      = it
                    cachedGradientKey = gradientKey
                }
            }

            // Chronometer base-time — recomputed only when the prayer identity changes.
            if (nextPrayer != null) {
                val key = "${nextPrayer.type}@${nextPrayer.date}@${nextPrayer.time}"
                if (key != cachedPrayerKey) {
                    val targetEpochMillis = nextPrayer.date.atTime(nextPrayer.time)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    val remainingMillis = targetEpochMillis - System.currentTimeMillis()
                    cachedBaseTime = SystemClock.elapsedRealtime() + remainingMillis
                    cachedPrayerKey = key
                }
            }

            // Read the actual allocated width so we can scale the countdown font to match.
            val options  = manager.getAppWidgetOptions(widgetId)
            val widthDp  = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)

            val views = buildViews(context, nextPrayer, bitmap, widthDp)
            manager.updateAppWidget(widgetId, views)
        }

        // ── RemoteViews assembly ───────────────────────────────────────────────

        private fun buildViews(
            context: Context,
            nextPrayer: NextPrayer?,
            gradientBitmap: Bitmap,
            widthDp: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_main)

            // Gradient background
            views.setImageViewBitmap(R.id.widget_background, gradientBitmap)

            // Tap → open app
            val tapIntent = Intent(context, MainActivity::class.java)
            val tapPi = PendingIntent.getActivity(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, tapPi)

            if (nextPrayer != null) {
                views.setViewVisibility(R.id.widget_content, View.VISIBLE)
                views.setViewVisibility(R.id.widget_idle,    View.GONE)

                // Left panel — prayer identity
                views.setTextViewText(
                    R.id.widget_prayer_name,
                    nextPrayer.type.getDisplayName(context).uppercase(Locale.getDefault())
                )
                views.setTextViewText(
                    R.id.widget_prayer_time,
                    nextPrayer.time.format(timeFormatter)
                )

                // Ghost icon — white tint at 15 % opacity (38 / 255 ≈ 0.15)
                views.setImageViewResource(R.id.widget_ghost_icon, getPrayerIconRes(nextPrayer.type))
                views.setInt(R.id.widget_ghost_icon, "setColorFilter", android.graphics.Color.WHITE)
                views.setInt(R.id.widget_ghost_icon, "setImageAlpha", 38)

                // Right panel — live countdown
                // Font size mirrors the original Glance formula:
                //   available width = total − left panel (104) − divider (1) − h-padding (24)
                //   font sp = availableWidth / 4.8, clamped to [22, 64]
                val availableWidth  = widthDp - 104 - 1 - 24
                val dynamicFontSize = (availableWidth / 4.8f).coerceIn(22f, 64f)
                // If the prayer time has already passed (alarm fired late), freeze at 00:00
                // rather than letting the Chronometer tick into negative territory.
                val elapsedNow = SystemClock.elapsedRealtime()
                val (chronometerBase, chronometerRunning) = if (cachedBaseTime > elapsedNow) {
                    cachedBaseTime to true
                } else {
                    elapsedNow to false
                }
                views.setChronometer(R.id.widget_chronometer, chronometerBase, null, chronometerRunning)
                views.setChronometerCountDown(R.id.widget_chronometer, true)
                views.setTextViewTextSize(
                    R.id.widget_chronometer,
                    TypedValue.COMPLEX_UNIT_SP,
                    dynamicFontSize
                )
            } else {
                views.setViewVisibility(R.id.widget_content, View.GONE)
                views.setViewVisibility(R.id.widget_idle,    View.VISIBLE)
            }

            return views
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private fun buildGradientBitmap(colors: List<Color>): Bitmap {
            val width  = 100
            val height = 200
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val ints   = colors.map { it.toArgb() }.toIntArray()
            val shader = if (ints.size > 1) {
                LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    ints, null, Shader.TileMode.CLAMP
                )
            } else null
            val paint = Paint().apply {
                if (shader != null) this.shader = shader
                else color = ints.firstOrNull() ?: android.graphics.Color.BLACK
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            return bitmap
        }

        private fun getPrayerIconRes(type: PrayerType): Int = when (type) {
            PrayerType.FAJR    -> R.drawable.haze_day_rotated
            PrayerType.SUNRISE -> R.drawable.sunrise
            PrayerType.DHUHR   -> R.drawable.clear_day
            PrayerType.ASR     -> R.drawable.clear_day
            PrayerType.MAGHRIB -> R.drawable.sunset
            PrayerType.ISHA    -> R.drawable.clear_night
        }
    }
}
