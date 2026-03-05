package com.ybugmobile.vaktiva.ui.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextAlign
import com.ybugmobile.vaktiva.MainActivity
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.service.AdhanService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class VaktivaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun prayerRepository(): PrayerRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            val prayerDays by entryPoint.prayerRepository().getPrayerDays().collectAsState(initial = emptyList())
            
            val now = LocalDateTime.now()
            val today = prayerDays.find { it.date == LocalDate.now() }
            
            val nextPrayer = today?.let { day ->
                val nowTime = now.toLocalTime()
                val nextReal = day.timings.entries
                    .filter { it.value.isAfter(nowTime) }
                    .minByOrNull { it.value }
                
                nextReal?.let {
                    NextPrayer(it.key, it.value, day.date, Duration.between(now, day.date.atTime(it.value)))
                }
            }

            // In a real app, this would be reactive
            val isAdhanPlaying = false 

            WidgetContent(context, nextPrayer, isAdhanPlaying)
        }
    }

    @Composable
    fun WidgetContent(
        context: Context,
        nextPrayer: NextPrayer?,
        isAdhanPlaying: Boolean
    ) {
        val size = LocalSize.current
        val containerColor = ColorProvider(day = Color.Black.copy(0.25f), night = Color.Black.copy(0.25f))
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(containerColor)
                .cornerRadius(32.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            if (isAdhanPlaying) {
                AdhanPlayingView(context)
            } else if (nextPrayer != null) {
                val accent = getPrayerColor(nextPrayer.type)
                val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
                
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side Column with Icon, Name, and Time
                    Column(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(80.dp)
                            .background(accent),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            provider = ImageProvider(getPrayerIconRes(nextPrayer.type)),
                            contentDescription = null,
                            modifier = GlanceModifier.size(24.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(day = onAccent, night = onAccent))
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = nextPrayer.type.getDisplayName(context).uppercase(),
                            style = TextStyle(
                                color = ColorProvider(day = onAccent, night = onAccent),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(
                            text = nextPrayer.time.format(timeFormatter),
                            style = TextStyle(
                                color = ColorProvider(day = onAccent.copy(alpha = 0.8f), night = onAccent.copy(alpha = 0.8f)),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }

                    // Countdown side - Live Chronometer
                    // Calculation: 8 chars (00:00:00) 
                    // Width check: Monospace font width is roughly 0.6 of its size. 
                    // 8 chars * 0.6 = 4.8. We use 5.5 to be safe with padding.
                    val availableWidth = size.width.value - 80 - 16
                    val fontSizeFromWidth = (availableWidth / 5.5f) 
                    val fontSizeFromHeight = (size.height.value * 0.55f)
                    val dynamicFontSize = minOf(fontSizeFromWidth, fontSizeFromHeight).coerceIn(24f, 64f)

                    Column(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val remainingDuration = nextPrayer.remainingDuration
                        val hours = remainingDuration.toHours()
                        val baseTime = SystemClock.elapsedRealtime() + remainingDuration.toMillis()
                        
                        // Logic to force 00:00:00 format:
                        // If 0 hours, system shows "MM:SS", so we prepend "00:"
                        // If 1-9 hours, system shows "H:MM:SS", so we prepend "0"
                        // If 10+ hours, system shows "HH:MM:SS", so no prepend needed.
                        val format = when {
                            hours == 0L -> "00:%s"
                            hours < 10L -> "0%s"
                            else -> "%s"
                        }

                        AndroidRemoteViews(
                            remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                                setChronometer(R.id.prayer_chronometer, baseTime, format, true)
                                setChronometerCountDown(R.id.prayer_chronometer, true)
                                setTextViewTextSize(R.id.prayer_chronometer, TypedValue.COMPLEX_UNIT_SP, dynamicFontSize)
                            }
                        )
                    }
                }
            } else {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "VAKTIVA",
                        style = TextStyle(
                            color = ColorProvider(day = Color.White.copy(alpha = 0.2f), night = Color.White.copy(alpha = 0.2f)),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun AdhanPlayingView(context: Context) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.adhan_playing).uppercase(),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(day = Color.White, night = Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            
            Button(
                text = context.getString(R.string.adhan_stop).uppercase(),
                onClick = actionRunCallback<StopAdhanCallback>(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ColorProvider(day = Color.Red.copy(alpha = 0.6f), night = Color.Red.copy(alpha = 0.6f)),
                    contentColor = ColorProvider(day = Color.White, night = Color.White)
                )
            )
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
            PrayerType.FAJR -> R.drawable.water_lux_rotated
            PrayerType.SUNRISE -> R.drawable.partly_cloudy_day
            PrayerType.DHUHR -> R.drawable.clear_day
            PrayerType.ASR -> R.drawable.clear_day
            PrayerType.MAGHRIB -> R.drawable.partly_cloudy_day
            PrayerType.ISHA -> R.drawable.clear_night
        }
    }
}

class StopAdhanCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, AdhanService::class.java).apply {
            action = AdhanService.ACTION_STOP_ADHAN
        }
        context.startService(intent)
    }
}

class VaktivaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VaktivaWidget()
}
