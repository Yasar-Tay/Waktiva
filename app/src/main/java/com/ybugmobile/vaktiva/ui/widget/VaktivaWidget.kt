package com.ybugmobile.vaktiva.ui.widget

import android.content.Context
import android.content.Intent
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
                    // Left side Icon
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(64.dp)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(getPrayerIconRes(nextPrayer.type)),
                            contentDescription = null,
                            modifier = GlanceModifier.size(24.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(day = onAccent, night = onAccent))
                        )
                    }

                    // Prayer Name and Time Column
                    Column(
                        modifier = GlanceModifier
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = nextPrayer.type.getDisplayName(context).uppercase(),
                            style = TextStyle(
                                color = ColorProvider(day = Color.White, night = Color.White),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = nextPrayer.time.format(timeFormatter),
                            style = TextStyle(
                                color = ColorProvider(day = Color.White.copy(alpha = 0.6f), night = Color.White.copy(alpha = 0.6f)),
                                fontSize = 12.sp
                            )
                        )
                    }

                    // Countdown side - Responsive font size
                    val remaining = formatDuration(nextPrayer.remainingDuration)
                    
                    // Responsive calculation: height-based but capped for reasonable widget sizes
                    // Using 40% of widget height as a baseline, but limiting it between 24 and 48 sp.
                    val dynamicFontSize = (size.height.value * 0.4f).coerceIn(24f, 48f).sp

                    Column(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight()
                            .padding(end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = remaining,
                            style = TextStyle(
                                color = ColorProvider(day = Color.White, night = Color.White),
                                fontSize = dynamicFontSize,
                                fontWeight = FontWeight.Bold
                            )
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

    private fun formatDuration(duration: Duration): String {
        val secondsTotal = duration.seconds
        val hours = secondsTotal / 3600
        val minutes = (secondsTotal % 3600) / 60
        val seconds = secondsTotal % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
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
