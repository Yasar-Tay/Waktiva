package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerType
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

            // 1. "REMAINING TIME" Label + Skip Button Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_remaining_time).uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.5f),
                    letterSpacing = 2.sp
                )

                if (playAdhanAudio) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        onClick = { onSkipAudio(nextPrayer.type.name) },
                        color = contentColor.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                contentDescription = "Skip Adhan",
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            
            // 2. Countdown Timer (Bigger, uses whole row)
            Text(
                text = remainingTime,
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraLight,
                color = accentColor,
                letterSpacing = (-2).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Next Prayer Info Row (Icon + Name + Time)
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
                    text = "${nextPrayer.type.getDisplayName(context)}  •  ${nextPrayer.time.format(timeFormatter)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
        } else if (showIdleState) {
            IdleState(contentColor, accentColor)
        }
    }
}

@Composable
fun CurrentPrayerHeader(
    currentPrayer: CurrentPrayer?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (currentPrayer != null) {
            // Icon exactly at the center of the Box (and thus the circle)
            Icon(
                imageVector = when(currentPrayer.type) {
                    PrayerType.FAJR -> Icons.Rounded.NightsStay
                    PrayerType.SUNRISE -> Icons.Rounded.WbTwilight
                    PrayerType.DHUHR -> Icons.Rounded.WbSunny
                    PrayerType.ASR -> Icons.Rounded.WbSunny
                    PrayerType.MAGHRIB -> Icons.Rounded.WbTwilight
                    PrayerType.ISHA -> Icons.Rounded.NightsStay
                    else -> Icons.Rounded.WbSunny
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(56.dp)
            )

            // Name positioned above the icon
            Text(
                text = currentPrayer.type.getDisplayName(context).uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor.copy(alpha = 0.6f),
                letterSpacing = 4.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-52.dp)) // Offset upwards: half icon (28) + spacer (12) + approx text half (12)
            )
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
    return String.format("%02d : %02d : %02d", hours, minutes, secs)
}
