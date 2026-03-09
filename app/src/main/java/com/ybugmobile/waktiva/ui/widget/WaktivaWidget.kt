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
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.usecase.GetNextPrayerUseCase
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
        
        // Get dynamic start and end colors for the gradient
        val (startColor, endColor) = getWidgetGradientColors(currentTime, today)
        
        // Determine content color (for text) based on the average brightness
        val isBackgroundLight = startColor.luminance() > 0.5f
        val contentColor = if (isBackgroundLight) Color.Black else Color.White
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(endColor) // The bottom color of the gradient
                .cornerRadius(32.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            // Gradient Overlay
            Image(
                provider = ImageProvider(R.drawable.mask_gradient),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(ColorProvider(day = startColor, night = startColor))
            )
            
            // Subtle dark overlay for depth
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.1f))
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
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Icon now uses its own prayer-specific color
                        val iconColor = getPrayerColor(nextPrayer.type)
                        Image(
                            provider = ImageProvider(getPrayerIconRes(nextPrayer.type)),
                            contentDescription = null,
                            modifier = GlanceModifier.size(28.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(day = iconColor, night = iconColor))
                        )
                        
                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // Texts strictly follow the dark/light content color for legibility
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
                                color = ColorProvider(day = contentColor, night = contentColor),
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
                        val baseTime = SystemClock.elapsedRealtime() + nextPrayer.remainingDuration.toMillis()
                        val availableWidth = size.width.value - sidebarWidth.value - 16
                        val dynamicFontSize = (availableWidth / 5.5f).coerceIn(24f, 64f)

                        AndroidRemoteViews(
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

    private fun getWidgetGradientColors(currentTime: LocalDateTime, day: PrayerDay?): Pair<Color, Color> {
        if (day == null) return Color(0xFF0F172A) to Color(0xFF1E293B)

        val timings = day.timings
        val localTime = currentTime.toLocalTime()
        val fajr = timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
        val sunrise = timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
        val dhuhur = timings[PrayerType.DHUHR] ?: LocalTime.of(13, 0)
        val asr = timings[PrayerType.ASR] ?: LocalTime.of(17, 0)
        val maghrib = timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
        val isha = timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)

        return when {
            localTime.isBefore(fajr) -> Color(0xFF020617) to Color(0xFF0F141E)    // Pre-Dawn
            localTime.isBefore(sunrise) -> Color(0xFF0A1024) to Color(0xFF1E1B4B) // Fajr/Dawn
            localTime.isBefore(dhuhur) -> Color(0xFF1E5DA8) to Color(0xFF4FA3C7)  // Morning
            localTime.isBefore(asr) -> Color(0xFF1E5DA8) to Color(0xFF68B298)     // Noon
            localTime.isBefore(maghrib.minusMinutes(45)) -> Color(0xFF123E7C) to Color(0xFF3F8FD2) // Afternoon
            localTime.isBefore(maghrib) -> Color(0xFF050E36) to Color(0xFF8D3E0D) // Maghrib/Sunset
            localTime.isBefore(isha) -> Color(0xFF020617) to Color(0xFF310F1A)    // Dusk
            else -> Color(0xFF020617) to Color(0xFF0F141E)                        // Isha/Night
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
