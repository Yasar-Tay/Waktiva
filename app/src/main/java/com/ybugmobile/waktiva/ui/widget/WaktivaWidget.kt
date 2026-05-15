package com.ybugmobile.waktiva.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextAlign
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.manager.TimeManager
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.usecase.GetNextPrayerUseCase
import com.ybugmobile.waktiva.ui.theme.getGradientColorsForTime
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.format.DateTimeFormatter
import java.util.Locale

class WaktivaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun prayerRepository(): PrayerRepository
        fun getNextPrayerUseCase(): GetNextPrayerUseCase
        fun timeManager(): TimeManager
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            val prayerDays by entryPoint.prayerRepository().getPrayerDays().collectAsState(initial = emptyList())
            val currentTime by entryPoint.timeManager().currentTime.collectAsState()

            val now = currentTime
            val today = prayerDays.find { it.date == now.toLocalDate() }

            val colors = getGradientColorsForTime(now.toLocalTime(), today)
            val widgetColors = colors.take(2)

            val backgroundProvider = remember(widgetColors) {
                if (widgetColors.size >= 2) {
                    ImageProvider(createGradientBitmap(widgetColors))
                } else {
                    ImageProvider(R.drawable.widget_background)
                }
            }

            val tomorrow = prayerDays.find { it.date == now.toLocalDate().plusDays(1) }
            val nextPrayer = entryPoint.getNextPrayerUseCase()(today, tomorrow, now)

            WidgetContent(context, nextPrayer, backgroundProvider)
        }
    }

    @Composable
    fun WidgetContent(
        context: Context,
        nextPrayer: NextPrayer?,
        backgroundProvider: ImageProvider
    ) {
        val size = LocalSize.current

        // Colour palette
        val white       = Color.White
        val white80     = Color.White.copy(alpha = 0.80f)
        val white30     = Color.White.copy(alpha = 0.30f)
        val white12     = Color.White.copy(alpha = 0.12f)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(backgroundProvider)
                .cornerRadius(32.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            // ── Glass sheen (border + highlight) ─────────────────────────────
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_glass_sheen))
            ) {}

            if (nextPrayer != null) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ── LEFT PANEL: Prayer identity ───────────────────────────
                    // panelWidth is also used as the ghost-icon container width,
                    // which is exactly N/2 (half of the 2× oversized icon).
                    // Result: icon center lands on the panel's bottom-left corner —
                    // only the top-right quadrant of the icon is visible inside the panel.
                    val panelWidth = 104.dp
                    val ghostIconSize = 130.dp  // 2× panelWidth — center lands on corner
                    Box(
                        modifier = GlanceModifier
                            .width(panelWidth)
                            .fillMaxHeight()
                    ) {
                        // Ghost icon layer
                        // Container = panelWidth wide, BottomEnd alignment →
                        // icon's bottom-right corner lands at container's bottom-right,
                        // left half of icon extends beyond the left panel edge and is clipped.
                        // Only the right half × top half (top-right quadrant) is visible.
                        Box(
                            modifier = GlanceModifier
                                .width(panelWidth)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            // Use AndroidRemoteViews so we can call setColorFilter(WHITE)
                            // + setImageAlpha(38) directly on the ImageView — the only
                            // approach that reliably overrides coloured vector drawables
                            // and applies true transparency in Glance's RemoteViews layer.
                            AndroidRemoteViews(
                                modifier = GlanceModifier.size(ghostIconSize),
                                remoteViews = RemoteViews(
                                    context.packageName,
                                    R.layout.widget_ghost_icon
                                ).apply {
                                    setImageViewResource(
                                        R.id.ghost_icon,
                                        getPrayerIconRes(nextPrayer.type)
                                    )
                                    // Force pure white regardless of the drawable's colours
                                    setInt(
                                        R.id.ghost_icon,
                                        "setColorFilter",
                                        android.graphics.Color.WHITE
                                    )
                                    // 15 % opacity: 0.15 × 255 ≈ 38
                                    setInt(R.id.ghost_icon, "setImageAlpha", 38)
                                }
                            )
                        }

                        // Prayer name + time — prominent, left-aligned, vertically centered
                        Column(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = nextPrayer.type.getDisplayName(context)
                                    .uppercase(Locale.getDefault()),
                                style = TextStyle(
                                    color = ColorProvider(day = white, night = white),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = nextPrayer.time.format(timeFormatter),
                                style = TextStyle(
                                    color = ColorProvider(day = white80, night = white80),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Start
                                )
                            )
                        }
                    }

                    // ── DIVIDER ───────────────────────────────────────────────
                    // Wrapped in a Column with vertical padding to create an inset line
                    Column(
                        modifier = GlanceModifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .width(1.dp)
                                .defaultWeight()
                                .background(white12)
                        ) {}
                    }

                    // ── RIGHT PANEL: Countdown hero ───────────────────────────
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .padding(start = 10.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.End
                    ) {
                        // Countdown chronometer via RemoteViews
                        val baseTime = SystemClock.elapsedRealtime() +
                                nextPrayer.remainingDuration.toMillis()

                        // Available width: total − panel − divider − end padding
                        val availableWidth = size.width.value - 104f - 1f - 24f
                        val dynamicFontSize = (availableWidth / 4.8f).coerceIn(22f, 64f)

                        AndroidRemoteViews(
                            modifier = GlanceModifier.fillMaxWidth(),
                            remoteViews = RemoteViews(
                                context.packageName,
                                R.layout.widget_countdown
                            ).apply {
                                setChronometer(
                                    R.id.prayer_chronometer,
                                    baseTime,
                                    null,
                                    true
                                )
                                setChronometerCountDown(R.id.prayer_chronometer, true)
                                setTextViewTextSize(
                                    R.id.prayer_chronometer,
                                    TypedValue.COMPLEX_UNIT_SP,
                                    dynamicFontSize
                                )
                                setTextColor(R.id.prayer_chronometer, white.toArgb())
                            }
                        )
                    }
                }
            } else {
                // ── IDLE STATE ────────────────────────────────────────────────
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = GlanceModifier
                                .size(40.dp),
                            colorFilter = ColorFilter.tint(
                                ColorProvider(day = white30, night = white30)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = "WAKTIVA",
                            style = TextStyle(
                                color = ColorProvider(day = white30, night = white30),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
    }

    private fun createGradientBitmap(colors: List<Color>): Bitmap {
        val width = 100
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val intColors = colors.map { it.toArgb() }.toIntArray()

        val shader = if (intColors.size > 1) {
            LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intColors,
                null,
                Shader.TileMode.CLAMP
            )
        } else null

        val paint = Paint().apply {
            if (shader != null) this.shader = shader
            else this.color = intColors.firstOrNull() ?: android.graphics.Color.BLACK
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun getPrayerIconRes(type: PrayerType): Int {
        return when (type) {
            PrayerType.FAJR    -> R.drawable.haze_day_rotated
            PrayerType.SUNRISE -> R.drawable.sunrise
            PrayerType.DHUHR   -> R.drawable.clear_day
            PrayerType.ASR     -> R.drawable.clear_day
            PrayerType.MAGHRIB -> R.drawable.sunset
            PrayerType.ISHA    -> R.drawable.clear_night
        }
    }
}

class WaktivaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaktivaWidget()
}
