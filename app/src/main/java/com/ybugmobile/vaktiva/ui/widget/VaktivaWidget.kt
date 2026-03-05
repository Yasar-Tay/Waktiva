package com.ybugmobile.vaktiva.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ybugmobile.vaktiva.MainActivity
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.manager.SettingsManagerInterface
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
import java.time.LocalTime
import java.util.Locale

class VaktivaWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun prayerRepository(): PrayerRepository
        fun settingsManager(): SettingsManagerInterface
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            val settings by entryPoint.settingsManager().settingsFlow.collectAsState(initial = null)
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

            // Sync this state from your AdhanService in a real implementation
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
        val background = ColorProvider(Color(0xFF1A1C1E))
        val accent = nextPrayer?.type?.let { getPrayerColor(it) } ?: Color(0xFF4CA1AF)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(background)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            if (isAdhanPlaying) {
                AdhanPlayingView(context)
            } else {
                CountdownView(context, nextPrayer, accent)
            }
        }
    }

    @Composable
    private fun CountdownView(context: Context, nextPrayer: NextPrayer?, accent: Color) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (nextPrayer != null) {
                Text(
                    text = nextPrayer.type.getDisplayName(context).uppercase(),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                
                val remaining = formatDuration(nextPrayer.remainingDuration)
                Text(
                    text = remaining,
                    style = TextStyle(
                        color = ColorProvider(accent),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = nextPrayer.time.toString(),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.4f)),
                        fontSize = 14.sp
                    )
                )
            } else {
                Text(
                    text = "VAKTIVA",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.2f)),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    @Composable
    private fun AdhanPlayingView(context: Context) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(R.string.adhan_playing).uppercase(),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Button(
                text = context.getString(R.string.adhan_stop).uppercase(),
                onClick = actionRunCallback<StopAdhanCallback>(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ColorProvider(Color.Red.copy(alpha = 0.6f)),
                    contentColor = ColorProvider(Color.White)
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

    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = (duration.toMinutes() % 60)
        return String.format(Locale.US, "%02d:%02d", hours, minutes)
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

/**
 * Preview for the Vaktiva Widget.
 * Note: Glance components need a special host to be previewed.
 */
@Preview(widthDp = 200, heightDp = 100)
@Composable
fun VaktivaWidgetCountdownPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    VaktivaWidget().WidgetContent(
        context = context,
        nextPrayer = NextPrayer(
            type = PrayerType.ASR,
            time = LocalTime.of(15, 45),
            date = LocalDate.now(),
            remainingDuration = Duration.ofHours(1).plusMinutes(20)
        ),
        isAdhanPlaying = false
    )
}

@Preview(widthDp = 200, heightDp = 100)
@Composable
fun VaktivaWidgetAdhanPlayingPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    VaktivaWidget().WidgetContent(
        context = context,
        nextPrayer = null,
        isAdhanPlaying = true
    )
}
