package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.home.NextPrayerInfo
import java.time.LocalDate

@Composable
fun NextPrayerCountdown(
    nextPrayer: NextPrayerInfo?,
    selectedDate: LocalDate
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if ((selectedDate == LocalDate.now()) && nextPrayer != null) {
            val prayerName = stringResource(
                when (nextPrayer.name) {
                    "Fajr" -> R.string.prayer_fajr
                    "Sunrise" -> R.string.prayer_sunrise
                    "Dhuhr" -> R.string.prayer_dhuhr
                    "Asr" -> R.string.prayer_asr
                    "Maghrib" -> R.string.prayer_maghrib
                    "Isha" -> R.string.prayer_isha
                    else -> R.string.app_name
                }
            )

            val isUrgent = nextPrayer.remainingMillis < 45 * 60 * 1000
            val infiniteTransition = rememberInfiniteTransition(label = "timerPulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isUrgent) 0.6f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "timerAlpha"
            )

            Text(
                prayerName,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                formatRemainingTime(nextPrayer.remainingMillis),
                color = Color.White.copy(alpha = if (isUrgent) alpha else 1f),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            )
        } else {
            Icon(
                Icons.Default.Schedule,
                null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                "VAKTIVA",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }
    }
}

fun formatRemainingTime(millis: Long): String {
    val totalSeconds = millis / 1000
    if (totalSeconds < 0) return "00:00:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}