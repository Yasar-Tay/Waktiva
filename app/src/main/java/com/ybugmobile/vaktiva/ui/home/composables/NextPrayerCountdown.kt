package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    @Suppress("UNUSED_PARAMETER") preAdhanWarningMinutes: Int = 5,
    playAdhanAudio: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(260.dp)
    ) {
        if ((selectedDate == LocalDate.now()) && nextPrayer != null) {
            val prayerNameRes = when (nextPrayer.type) {
                PrayerType.FAJR -> R.string.prayer_fajr
                PrayerType.SUNRISE -> R.string.prayer_sunrise
                PrayerType.DHUHR -> R.string.prayer_dhuhr
                PrayerType.ASR -> R.string.prayer_asr
                PrayerType.MAGHRIB -> R.string.prayer_maghrib
                PrayerType.ISHA -> R.string.prayer_isha
            }

            val remainingSeconds = nextPrayer.remainingDuration.seconds
            val isUrgent = remainingSeconds < 30 * 60 // 30 minutes
            
            // Animation for urgency
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isUrgent) 0.5f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Label: "NEXT PRAYER" or specific prayer name
                Text(
                    text = stringResource(prayerNameRes).uppercase(),
                    color = contentColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // The Countdown Timer with distinct segments for better readability
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = pulseAlpha }
                ) {
                    val time = formatRemainingTimeParts(remainingSeconds)
                    TimeSegment(time.first, "h", contentColor)
                    TimeSeparator(contentColor)
                    TimeSegment(time.second, "m", contentColor)
                    TimeSeparator(contentColor)
                    TimeSegment(time.third, "s", contentColor)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Modern Mute/Skip Toggle Chip
                if (playAdhanAudio) {
                    AudioStatusChip(
                        isMuted = isMuted,
                        isTest = nextPrayer.isTest,
                        contentColor = contentColor,
                        onClick = { onSkipAudio(nextPrayer.type.name) }
                    )
                }
            }
        } else {
            IdleState(contentColor)
        }
    }
}

@Composable
private fun TimeSegment(value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 44.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp
            )
        )
        Text(
            text = unit,
            color = color.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TimeSeparator(color: Color) {
    Text(
        text = ":",
        color = color.copy(alpha = 0.3f),
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
    )
}

@Composable
private fun AudioStatusChip(
    isMuted: Boolean,
    isTest: Boolean,
    contentColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isMuted) Color.Black.copy(alpha = 0.4f) else contentColor.copy(alpha = 0.15f),
        label = "chipBg"
    )
    
    val tintColor by animateColorAsState(
        targetValue = if (isMuted) contentColor.copy(alpha = 0.5f) else contentColor,
        label = "chipTint"
    )

    Surface(
        onClick = onClick,
        enabled = !isMuted,
        color = backgroundColor,
        shape = CircleShape,
        border = if (isMuted) null else androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.1f)),
        modifier = Modifier
            .height(44.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = isMuted,
                transitionSpec = {
                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                },
                label = "iconAnim"
            ) { muted ->
                Icon(
                    imageVector = if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column {
                Text(
                    text = if (isMuted) "ADHAN MUTED" else "ADHAN ON",
                    color = tintColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isMuted) "Skipped for this prayer" else if (isTest) "Tap to skip test" else "Tap to skip next",
                    color = tintColor.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun IdleState(contentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.2f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "VAKTIVA",
            color = contentColor.copy(alpha = 0.3f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 8.sp
        )
    }
}

private fun formatRemainingTimeParts(totalSeconds: Long): Triple<String, String, String> {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return Triple(
        String.format("%02d", hours),
        String.format("%02d", minutes),
        String.format("%02d", secs)
    )
}
