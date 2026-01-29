package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun NextPrayerCountdown(
    nextPrayer: NextPrayer?,
    currentPrayer: CurrentPrayer?,
    selectedDate: LocalDate,
    contentColor: Color = Color.White,
    accentColor: Color = Color.White,
    playAdhanAudio: Boolean = false,
    isMuted: Boolean = false,
    onSkipAudio: (String) -> Unit = {},
    showIdleState: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        if ((selectedDate == LocalDate.now()) && nextPrayer != null) {
            val remainingSeconds = nextPrayer.remainingDuration.seconds
            val remainingTime = formatRemainingTime(remainingSeconds)

            // 1. Next Prayer Name (Label style)
            Text(
                text = nextPrayer.type.getDisplayName(context).uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.5f),
                letterSpacing = 4.sp
            )
            
            // 2. Time Remaining (Large Countdown)
            Text(
                text = remainingTime,
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraLight,
                color = accentColor,
                letterSpacing = (-1).sp
            )

            // 3. Next Prayer Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = nextPrayer.time.format(timeFormatter),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }

            // Skip Button
            if (playAdhanAudio) {
                Spacer(modifier = Modifier.height(12.dp))
                IconButton(
                    onClick = { onSkipAudio(nextPrayer.type.name) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        contentDescription = "Skip Adhan",
                        tint = contentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else if (showIdleState) {
            IdleState(contentColor, accentColor)
        }
    }
}

@Composable
private fun IdleState(contentColor: Color, accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.2f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "VAKTIVA",
            color = contentColor.copy(alpha = 0.2f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 8.sp
        )
    }
}

private fun formatRemainingTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}
