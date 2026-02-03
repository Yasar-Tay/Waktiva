package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import java.time.LocalDate
import java.util.Locale

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
    onResetDate: () -> Unit = {},
    showIdleState: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        if ((selectedDate == LocalDate.now()) && nextPrayer != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                val remainingSeconds = nextPrayer.remainingDuration.seconds
                val remainingTime = formatRemainingTime(remainingSeconds)

                // 1. "REMAINING TIME" Label
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
                }
                
                // 2. Countdown Timer
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ResponsiveCountdownText(
                        text = remainingTime,
                        targetFontSize = 72.sp,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Skip/Mute Adhan Button
                if (playAdhanAudio) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        onClick = { onSkipAudio(nextPrayer.type.name) },
                        color = contentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (if (isMuted) stringResource(R.string.home_unmute_adhan) else stringResource(R.string.home_skip_adhan)).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        } else if (selectedDate.isAfter(LocalDate.now())) {
            // Fallback element for future dates
            Surface(
                onClick = onResetDate,
                color = contentColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EventRepeat,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.home_return_to_today).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        } else if (showIdleState) {
            IdleState(contentColor, accentColor)
        }
    }
}

@Composable
private fun ResponsiveCountdownText(
    text: String,
    targetFontSize: TextUnit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var fontSize by remember { mutableStateOf(targetFontSize) }
    var readyToDraw by remember { mutableStateOf(false) }

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraLight,
        color = accentColor,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth && fontSize.value > 12f) {
                fontSize = (fontSize.value * 0.9f).sp
            } else {
                readyToDraw = true
            }
        },
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        }
    )
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
    return String.format(Locale.US, "%02d : %02d : %02d", hours, minutes, secs)
}
