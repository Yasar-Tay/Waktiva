package com.ybugmobile.waktiva.ui.widget

import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.usecase.GetNextPrayerUseCase
import com.ybugmobile.waktiva.ui.theme.getGlassTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
            val tomorrow = prayerDays.find { it.date == now.toLocalDate().plusDays(1) }
            
            val nextPrayer = entryPoint.getNextPrayerUseCase()(today, tomorrow, now)

            WidgetContent(context, nextPrayer, now, today)
        }
    }

    @Composable
    fun WidgetContent(
        context: Context,
        nextPrayer: NextPrayer?,
        currentTime: LocalDateTime,
        today: PrayerDay?
    ) {
        val size = LocalSize.current
        
        // Match the glass effect from GlassTheme.kt
        val glassTheme = getGlassTheme(
            currentTime = currentTime.toLocalTime(),
            day = today,
            weatherCondition = WeatherCondition.CLEAR
        )
        
        val containerColor = glassTheme.containerColor
        val contentColor = glassTheme.contentColor
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(containerColor)
                .cornerRadius(32.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            // Glass Sheen Layer (Highlight and Border) - Provides the edge definition and light play
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
                    val sidebarWidth = 92.dp
                    Column(
                        modifier = GlanceModifier
                            .width(sidebarWidth)
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val iconColor = getPrayerColor(nextPrayer.type)
                        Image(
                            provider = ImageProvider(getPrayerIconRes(nextPrayer.type)),
                            contentDescription = null,
                            modifier = GlanceModifier.size(28.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(day = iconColor, night = iconColor))
                        )
                        
                        Spacer(modifier = GlanceModifier.height(8.dp))

                        Text(
                            text = nextPrayer.type.getDisplayName(context).uppercase(),
                            style = TextStyle(
                                color = ColorProvider(day = contentColor, night = contentColor),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(
                            text = nextPrayer.time.format(timeFormatter),
                            style = TextStyle(
                                color = ColorProvider(day = glassTheme.secondaryContentColor, night = glassTheme.secondaryContentColor),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }

                    // Countdown side
                    Column(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val remainingDuration = nextPrayer.remainingDuration
                        val baseTime = SystemClock.elapsedRealtime() + remainingDuration.toMillis()
                        
                        // Chronometer format hack to show leading zeros for hours (e.g. 09:51:23).
                        // DateUtils.formatElapsedTime returns "H:MM:SS" or "MM:SS", so we prepend zeros as needed.
                        val hours = remainingDuration.toHours()
                        val minutes = remainingDuration.toMinutes() % 60
                        val chronometerFormat = when {
                            hours >= 10 -> null
                            hours >= 1 -> "0%s"
                            minutes >= 10 -> "00:%s"
                            else -> "00:0%s"
                        }

                        val availableWidth = size.width.value - sidebarWidth.value - 16
                        // Adjusted divisor from 5.5f to 6.5f to account for the extra digit in "09:51:23"
                        val dynamicFontSize = (availableWidth / 6.5f).coerceIn(24f, 64f)

                        AndroidRemoteViews(
                            remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                                setChronometer(R.id.prayer_chronometer, baseTime, chronometerFormat, true)
                                setChronometerCountDown(R.id.prayer_chronometer, true)
                                setTextViewTextSize(R.id.prayer_chronometer, TypedValue.COMPLEX_UNIT_SP, dynamicFontSize)
                                setTextColor(R.id.prayer_chronometer, contentColor.toArgb())
                            }
                        )
                    }
                }
            } else {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "WAKTIVA",
                        style = TextStyle(
                            color = ColorProvider(day = contentColor.copy(alpha = 0.3f), night = contentColor.copy(alpha = 0.3f)),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    private fun getPrayerColor(type: PrayerType): Color {
        return when (type) {
            PrayerType.FAJR -> Color(0xFF81D4FA)
            PrayerType.SUNRISE -> Color(0xFFFFE082)
            PrayerType.DHUHR -> Color(0xFFFFF59D)
            PrayerType.ASR -> Color(0xFFFFCC80)
            PrayerType.MAGHRIB -> Color(0xFFCE93D8)
            PrayerType.ISHA -> Color(0xFF9FA8DA)
        }
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
