package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.ui.theme.Inter
import java.time.LocalDate

@Composable
fun NextPrayerCountdown(
    nextPrayer: NextPrayer?,
    currentPrayer: CurrentPrayer?,
    selectedDate: LocalDate,
    contentColor: Color = Color.White,
    playAdhanAudio: Boolean = false,
    isMuted: Boolean = false,
    onSkipAudio: (String) -> Unit = {},
    accentColor: Color = contentColor
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if ((selectedDate == LocalDate.now()) && nextPrayer != null) {
            val prayerName = currentPrayer?.type?.displayName ?: ""
            val prayerIcon = currentPrayer?.type?.let { getPrayerIcon(it) } ?: Icons.Rounded.Notifications
            val remainingSeconds = nextPrayer.remainingDuration.seconds
            val isUrgent = remainingSeconds < 30 * 60 // 30 minutes
            
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
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxSize().padding(vertical = 44.dp)
            ) {
                // 1. Header Column (Icon above, Name under)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = prayerIcon,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.9f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = prayerName.uppercase(),
                        color = contentColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                // 2. Middle Section (Clock Core + Skip Button to the side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Spacer to keep core centered
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Clock Core (Visual pivot for the clock handle)
                    Surface(
                        modifier = Modifier.size(16.dp),
                        shape = CircleShape,
                        color = accentColor,
                        border = BorderStroke(3.dp, contentColor.copy(alpha = 0.6f)),
                        tonalElevation = 4.dp
                    ) {}
                    
                    // Right Area for Skip Button
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (playAdhanAudio) {
                            IconButton(
                                onClick = { onSkipAudio(nextPrayer.type.name) },
                                modifier = Modifier.padding(start = 24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Bottom Section (Remaining text above, Countdown under)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.home_remaining_time).uppercase(),
                        color = contentColor.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                }
            }
        } else {
            IdleState(contentColor)
        }
    }
}

private fun getPrayerIcon(type: PrayerType): ImageVector {
    return when (type) {
        PrayerType.FAJR -> Icons.Rounded.WbTwilight
        PrayerType.SUNRISE -> Icons.Rounded.WbSunny
        PrayerType.DHUHR -> Icons.Rounded.WbSunny
        PrayerType.ASR -> Icons.Rounded.WbCloudy
        PrayerType.MAGHRIB -> Icons.Rounded.WbTwilight
        PrayerType.ISHA -> Icons.Rounded.NightsStay
    }
}

@Composable
private fun TimeSegment(value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 48.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp
            )
        )
        Text(
            text = unit.uppercase(),
            color = color.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun TimeSeparator(color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Invisible digit to ensure the Box matches the height of digits in TimeSegment
            Text(
                text = "0",
                color = Color.Transparent,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 48.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Light
                )
            )
            // The actual separator centered within that space
            Text(
                text = ":",
                color = color.copy(alpha = 0.8f),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Thin
                ),
                modifier = Modifier.offset(y = (-3).dp) // Slight nudge for visual center
            )
        }
        // Invisible unit label to maintain vertical alignment with TimeSegment labels
        Text(
            text = "H",
            color = Color.Transparent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun IdleState(contentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.15f),
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
