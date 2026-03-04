package com.ybugmobile.vaktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.vaktiva.ui.theme.LocalGlassTheme
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
            .height(200.dp),
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

                // 3. Skip/Mute Adhan Button (Redesigned as InfoGlassCard)
                if (playAdhanAudio) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val glassTheme = LocalGlassTheme.current
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    
                    val containerColor = remember(glassTheme.isLightMode) { 
                        if (glassTheme.isLightMode) Color.White.copy(0.22f) else Color.Black.copy(0.25f) 
                    }
                    val borderColor = remember(glassTheme.isLightMode) { 
                        if (glassTheme.isLightMode) Color.White.copy(0.45f) else Color.White.copy(0.15f) 
                    }
                    val cardShape = RoundedCornerShape(percent = 50)

                    Surface(
                        onClick = { onSkipAudio(nextPrayer.type.name) },
                        color = containerColor,
                        shape = cardShape,
                        modifier = Modifier
                            .wrapContentSize()
                            .height(if (isLandscape) 40.dp else 48.dp)
                            .clip(cardShape)
                            .drawWithContent {
                                drawContent()
                                // Subtle card gradient matching InfoGlassCard style
                                drawRoundRect(
                                    Brush.horizontalGradient(
                                        listOf(accentColor.copy(0.15f), Color.Transparent), 
                                        endX = size.width * 0.4f
                                    ), 
                                    size = size, 
                                    cornerRadius = CornerRadius(size.height / 2), 
                                    blendMode = BlendMode.Screen
                                )
                                drawRoundRect(
                                    borderColor, 
                                    size = size, 
                                    cornerRadius = CornerRadius(size.height / 2), 
                                    style = Stroke(1.dp.toPx())
                                )
                            },
                        contentColor = Color.White
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Icon container covering the left side like a toggle
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .background(accentColor.copy(0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = if (accentColor.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.padding(
                                    start = if (isLandscape) 12.dp else 16.dp, 
                                    end = if (isLandscape) 16.dp else 20.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy((-4).dp, Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = stringResource(R.string.adhan_playing).uppercase(),
                                    style = TextStyle(
                                        fontSize = if (isLandscape) 11.sp else 13.sp,
                                        lineHeight = if (isLandscape) 11.sp else 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(0.6f),
                                        letterSpacing = 0.4.sp
                                    )
                                )
                                Text(
                                    text = (if (isMuted) stringResource(R.string.home_unmute_adhan) else stringResource(R.string.home_skip_adhan)).uppercase(),
                                    style = TextStyle(
                                        fontSize = if (isLandscape) 15.sp else 17.sp,
                                        lineHeight = if (isLandscape) 15.sp else 17.sp,
                                        fontFamily = IBMPlexArabic,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = (-0.3).sp
                                    )
                                )
                            }
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
        style = TextStyle(
            fontSize = fontSize,
            fontFamily = IBMPlexArabic,
            fontWeight = FontWeight.ExtraLight,
            fontFeatureSettings = "tnum",
            letterSpacing = (-2).sp,
            color = accentColor,
            textAlign = TextAlign.Center
        ),
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
