package com.ybugmobile.waktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.CurrentPrayer
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.theme.*
import java.time.LocalDate
import java.util.Locale

/**
 * Component that displays a high-visibility countdown timer to the next prayer event.
 * Includes interactive controls for muting/unmuting the next adhan and provides
 * fallbacks for future date views.
 *
 * @param nextPrayer Information about the upcoming prayer event.
 * @param currentPrayer Information about the active prayer period.
 * @param selectedDate The date currently being viewed.
 * @param contentColor Base color for secondary text elements.
 * @param accentColor Primary color for the countdown timer.
 * @param playAdhanAudio General preference for whether adhan should play.
 * @param isMuted Whether the next adhan is specifically silenced for this occurrence.
 * @param onSkipAudio Callback to toggle the mute state of the next adhan.
 * @param onResetDate Callback to return the view to today.
 * @param showIdleState Whether to show the app logo/idle state when no prayer is pending.
 * @param modifier Root layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Active Countdown State (Only for Today)
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
                        text = stringResource(R.string.home_remaining_time).uppercase(Locale.getDefault()),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.5f),
                        letterSpacing = 2.sp
                    )
                }
                
                // 2. Countdown Timer - Forced LTR for numerical consistency across locales
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ResponsiveCountdownText(
                        text = remainingTime,
                        targetFontSize = 72.sp,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Skip/Mute Adhan Control (Pill-shaped glass button)
                if (playAdhanAudio) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val glassTheme = LocalGlassTheme.current
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    
                    val containerColor = remember(glassTheme.isLightMode) {
                        if (glassTheme.isLightMode) Color.White.copy(0.18f) else Color.Black.copy(0.42f)
                    }
                    val borderColor = remember(glassTheme.isLightMode) {
                        if (glassTheme.isLightMode) Color.White.copy(0.45f) else Color.White.copy(0.1f)
                    }
                    val cardShape = RoundedCornerShape(18.dp)

                    // Target logic: If next event is Sunrise (no adhan), target Dhuhr instead.
                    val targetPrayerType = if (nextPrayer.type == PrayerType.SUNRISE) PrayerType.DHUHR else nextPrayer.type

                    // Dynamic button accent color with weather-aware desaturation
                    val buttonColor = remember(targetPrayerType, glassTheme.weatherCondition) {
                        val baseColor = when (targetPrayerType) {
                            PrayerType.FAJR -> Color(0xFF81D4FA)
                            PrayerType.SUNRISE -> Color(0xFFFFE082)
                            PrayerType.DHUHR -> Color(0xFFFFF59D)
                            PrayerType.ASR -> Color(0xFFFFCC80)
                            PrayerType.MAGHRIB -> Color(0xFFCE93D8)
                            PrayerType.ISHA -> Color(0xFF9FA8DA)
                        }
                        
                        val isCloudy = glassTheme.weatherCondition != com.ybugmobile.waktiva.domain.model.WeatherCondition.CLEAR &&
                                       glassTheme.weatherCondition != com.ybugmobile.waktiva.domain.model.WeatherCondition.UNKNOWN
                        val isSevere = glassTheme.weatherCondition == com.ybugmobile.waktiva.domain.model.WeatherCondition.RAINY ||
                                      glassTheme.weatherCondition == com.ybugmobile.waktiva.domain.model.WeatherCondition.THUNDERSTORM ||
                                      glassTheme.weatherCondition == com.ybugmobile.waktiva.domain.model.WeatherCondition.SNOWY

                        if (isCloudy) {
                            val desaturateAmount = if (isSevere) 0.35f else 0.2f
                            val darkenAmount = if (isSevere) 0.2f else 0.1f
                            baseColor.desaturate(desaturateAmount).darken(darkenAmount)
                        } else {
                            baseColor
                        }
                    }

                    // Button press animations
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1f,
                        animationSpec = tween(durationMillis = 80),
                        label = "buttonScale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isPressed) 0.7f else 1f,
                        animationSpec = tween(durationMillis = 80),
                        label = "buttonAlpha"
                    )

                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides 0.dp,
                        LocalContentColor provides Color.White
                    ) {
                        Box(
                            modifier = Modifier
                                .height(if (isLandscape) 36.dp else 50.dp)
                                .wrapContentWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .clip(cardShape)
                                .background(containerColor)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onSkipAudio(targetPrayerType.name) }
                                )
                                .drawWithContent {
                                    drawContent()
                                    val cornerRadius = CornerRadius(18.dp.toPx())
                                    // Soft color wash from left
                                    drawRoundRect(
                                        brush = Brush.horizontalGradient(
                                            0f to buttonColor.copy(alpha = 0.18f),
                                            0.45f to Color.Transparent
                                        ),
                                        size = size,
                                        cornerRadius = cornerRadius
                                    )
                                    // Hair-line border
                                    drawRoundRect(
                                        color = borderColor,
                                        size = size,
                                        cornerRadius = cornerRadius,
                                        style = Stroke(0.75.dp.toPx())
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thin vertical accent bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(2.5.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                listOf(Color.Transparent, buttonColor.copy(0.85f), Color.Transparent)
                                            ),
                                            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                                        )
                                )

                                Spacer(Modifier.width(if (isLandscape) 10.dp else 12.dp))

                                // Icon tinted in prayer color, no background box
                                Icon(
                                    imageVector = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = buttonColor,
                                    modifier = Modifier.size(if (isLandscape) 13.dp else 15.dp)
                                )

                                Spacer(Modifier.width(if (isLandscape) 8.dp else 10.dp))

                                // Prayer name label + action text stacked
                                Column(
                                    modifier = Modifier.padding(end = if (isLandscape) 14.dp else 18.dp),
                                    verticalArrangement = Arrangement.spacedBy((-1).dp, Alignment.CenterVertically)
                                ) {
                                    Text(
                                        text = targetPrayerType.getDisplayName(context).uppercase(),
                                        style = TextStyle(
                                            fontSize = if (isLandscape) 7.sp else 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(0.42f),
                                            letterSpacing = 1.5.sp
                                        )
                                    )
                                    Text(
                                        text = (if (isMuted) stringResource(R.string.home_unmute_adhan) else stringResource(R.string.home_skip_adhan)).uppercase(Locale.getDefault()),
                                        style = TextStyle(
                                            fontSize = if (isLandscape) 13.sp else 18.sp,
                                            fontFamily = IBMPlexArabic,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                            letterSpacing = (-0.5).sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedDate.isAfter(LocalDate.now())) {
            // Future Date View: Show a prompt to return to the current day
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
            // Idle state / Logo display
            IdleState(contentColor, accentColor)
        }
    }
}

/**
 * Text component that automatically reduces its font size to fit within the available width.
 */
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

/**
 * Visual representation of the app's idle state (logo and branding).
 */
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
            text = "WAKTIVA",
            color = contentColor.copy(alpha = 0.2f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 8.sp
        )
    }
}

/** Formats a duration in seconds into a 'HH : MM : SS' string. */
private fun formatRemainingTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return String.format(Locale.US, "%02d : %02d : %02d", hours, minutes, secs)
}
