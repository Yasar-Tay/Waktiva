package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Header (Icon + Name)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = prayerIcon,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.9f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = prayerName.uppercase(),
                            color = contentColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_remaining_time).uppercase(),
                        color = contentColor.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 2. The Countdown Timer
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

                // 3. Redesigned Skip/Unmute Button with Dynamic Width but Bounds
                if (playAdhanAudio) {
                    OutlinedButton(
                        onClick = { onSkipAudio(nextPrayer.type.name) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = contentColor
                        ),
                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .widthIn(min = 100.dp, max = 220.dp) // Dynamic but constrained
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (if (isMuted) stringResource(R.string.home_unmute_adhan) else stringResource(R.string.home_skip_adhan)).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(64.dp))
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
    Text(
        text = ":",
        color = color.copy(alpha = 0.2f),
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 32.sp,
            fontWeight = FontWeight.Thin
        ),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
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
