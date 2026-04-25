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
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.usecase.GetNextPrayerUseCase
import com.ybugmobile.waktiva.ui.theme.getGradientColorsForTime
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
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
            
            // Get colors from Gradient.kt and use the first two for the widget background as requested
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
        val contentColor = Color.White
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(backgroundProvider)
                .cornerRadius(28.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            // Glass Sheen Layer (Highlight and Border) for premium feel
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_glass_sheen))
            ) {}

            if (nextPrayer != null) {
                // Creative Watermark: Large faint icon in the background
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Image(
                        provider = ImageProvider(getPrayerIconRes(nextPrayer.type)),
                        contentDescription = null,
                        modifier = GlanceModifier
                            .size(160.dp)
                            .padding(end = (-40).dp), // Partially bleed off the edge
                        colorFilter = ColorFilter.tint(ColorProvider(day = Color.White.copy(alpha = 0.05f), night = Color.White.copy(alpha = 0.05f)))
                    )
                }

                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Prayer Info
                    val sidebarWidth = 88.dp
                    Column(
                        modifier = GlanceModifier
                            .width(sidebarWidth)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = nextPrayer.type.getDisplayName(context).uppercase(),
                            style = TextStyle(
                                color = ColorProvider(day = contentColor.copy(alpha = 0.7f), night = contentColor.copy(alpha = 0.7f)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        Text(
                            text = nextPrayer.time.format(timeFormatter),
                            style = TextStyle(
                                color = ColorProvider(day = contentColor, night = contentColor),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // Right Side: Countdown Hero
                    Column(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.End
                    ) {
                        val remainingDuration = nextPrayer.remainingDuration
                        val baseTime = SystemClock.elapsedRealtime() + remainingDuration.toMillis()
                        
                        // Bold countdown that "pops" against the watermark
                        val availableWidth = size.width.value - sidebarWidth.value - 32
                        val dynamicFontSize = (availableWidth / 3.5f).coerceIn(32f, 72f)

                        AndroidRemoteViews(
                            modifier = GlanceModifier.wrapContentWidth(),
                            remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                                setChronometer(R.id.prayer_chronometer, baseTime, null, true)
                                setChronometerCountDown(R.id.prayer_chronometer, true)
                                setTextViewTextSize(R.id.prayer_chronometer, TypedValue.COMPLEX_UNIT_SP, dynamicFontSize)
                                setTextColor(R.id.prayer_chronometer, contentColor.toArgb())
                            }
                        )
                    }
                }
            } else {
                // Branded Empty State
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "WAKTIVA",
                            style = TextStyle(
                                color = ColorProvider(day = contentColor.copy(alpha = 0.4f), night = contentColor.copy(alpha = 0.4f)),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Awaiting Prayer Times",
                            style = TextStyle(
                                color = ColorProvider(day = contentColor.copy(alpha = 0.2f), night = contentColor.copy(alpha = 0.2f)),
                                fontSize = 11.sp
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
        } else {
            null
        }

        val paint = Paint().apply {
            if (shader != null) {
                this.shader = shader
            } else {
                this.color = intColors.firstOrNull() ?: android.graphics.Color.BLACK
            }
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun getPrayerIconRes(type: PrayerType): Int {
        return when (type) {
            PrayerType.FAJR -> R.drawable.haze_day_rotated
            PrayerType.SUNRISE -> R.drawable.sunrise
            PrayerType.DHUHR -> R.drawable.clear_day
            PrayerType.ASR -> R.drawable.clear_day
            PrayerType.MAGHRIB -> R.drawable.sunset
            PrayerType.ISHA -> R.drawable.clear_night
        }
    }
}

class WaktivaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaktivaWidget()
}
