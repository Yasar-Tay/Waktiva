package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
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
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.ui.theme.Inter
import java.time.LocalDate

@Composable
fun NextPrayerCountdown(
    nextPrayer: NextPrayer?,
    selectedDate: LocalDate,
    onSkipAudio: (String) -> Unit = {},
    isMuted: Boolean = false,
    contentColor: Color = Color.White,
    preAdhanWarningMinutes: Int = 5 // Default fallback
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(230.dp)
    ) {
        if ((selectedDate == LocalDate.now()) && nextPrayer != null) {
            val currentPrayerRes = when (nextPrayer.type) {
                PrayerType.FAJR -> R.string.prayer_isha
                PrayerType.SUNRISE -> R.string.prayer_fajr
                PrayerType.DHUHR -> R.string.prayer_sunrise
                PrayerType.ASR -> R.string.prayer_dhuhr
                PrayerType.MAGHRIB -> R.string.prayer_asr
                PrayerType.ISHA -> R.string.prayer_maghrib
            }

            val remainingSeconds = nextPrayer.remainingDuration.seconds
            val isUrgent = remainingSeconds < 45 * 60
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

            // 1. Top Labels (Prayer name & "Remaining Time")
           Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-60).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(currentPrayerRes),
                    color = contentColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 24.sp,
                )
            }

            // 2. The Countdown Timer (Absolute center)
            Text(
                formatRemainingTime(remainingSeconds),
                modifier = Modifier.align(Alignment.Center),
                color = contentColor.copy(alpha = if (isUrgent) alpha else 1f),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 48.sp,
                fontFamily = Inter,
                letterSpacing = (-1).sp
            )

            // 3. Bottom Controls (Mute/Skip Icon)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 65.dp),
                contentAlignment = Alignment.Center
            ) {
                // Show Skip button only within the pre-adhan warning window
                val showSkipButton = remainingSeconds <= (preAdhanWarningMinutes * 60)

                if (showSkipButton) {
                    Surface(
                        onClick = { onSkipAudio(nextPrayer.type.name) },
                        color = if (isMuted) Color.Black.copy(alpha = 0.3f) else contentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                                contentDescription = if (isMuted) "Unmute Audio" else "Mute Audio",
                                tint = if (isMuted) contentColor.copy(alpha = 0.6f) else contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMuted) "MUTED" else "MUTE",
                                color = if (isMuted) contentColor.copy(alpha = 0.6f) else contentColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Schedule,
                    null,
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "VAKTIVA",
                    color = contentColor.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
        }
    }
}

fun formatRemainingTime(totalSeconds: Long): String {
    if (totalSeconds < 0) return "00:00:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return String.format("%02d : %02d : %02d", hours, minutes, secs)
}
