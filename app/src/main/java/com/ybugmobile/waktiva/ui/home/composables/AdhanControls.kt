package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import java.util.Locale

/**
 * A specialized UI component that appears when an Adhan (call to prayer) is actively playing.
 * It provides visual feedback through a pulsing icon and a clear action to stop the audio.
 * Uses the global GlassTheme for consistent styling.
 *
 * @param isAdhanPlaying Whether the Adhan audio is currently active.
 * @param playingPrayerName The name of the prayer currently being announced (e.g., "Fajr").
 * @param isTest Indicates if the current playing state is a user-initiated test.
 * @param onStopAdhan Callback invoked when the user requests to stop the Adhan playback.
 * @param onStopTest Callback specifically for stopping a test simulation.
 * @param modifier Modifier for layout adjustments.
 */
@Composable
fun AdhanControls(
    isAdhanPlaying: Boolean,
    playingPrayerName: String?,
    isTest: Boolean,
    onStopAdhan: () -> Unit,
    onStopTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glassTheme = LocalGlassTheme.current
    val prayerType = playingPrayerName?.let { PrayerType.fromString(it) }
    
    // Select appropriate icon based on the prayer type
    val prayerIcon = when (prayerType) {
        PrayerType.FAJR -> ImageVector.vectorResource(R.drawable.haze_day_rotated)
        PrayerType.SUNRISE -> ImageVector.vectorResource(R.drawable.sunrise)
        PrayerType.DHUHR -> ImageVector.vectorResource(R.drawable.clear_day)
        PrayerType.ASR -> ImageVector.vectorResource(R.drawable.clear_day)
        PrayerType.MAGHRIB -> ImageVector.vectorResource(R.drawable.sunset)
        PrayerType.ISHA -> ImageVector.vectorResource(R.drawable.clear_night)
        else -> Icons.Rounded.VolumeUp
    }

    // Pulse animation for the playback indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    AnimatedVisibility(
        visible = isAdhanPlaying,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = glassTheme.containerColor,
            border = BorderStroke(1.dp, glassTheme.borderColor)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Pulsing Icon container
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(pulseScale)
                            .background(glassTheme.contentColor.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = prayerIcon,
                            contentDescription = null,
                            tint = glassTheme.contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val locale = Locale.getDefault()
                        val title = when {
                            isTest -> stringResource(R.string.adhan_test_alarm).uppercase(locale)
                            prayerType != null -> prayerType.displayName.uppercase(locale)
                            else -> stringResource(R.string.adhan_playing).uppercase(locale)
                        }
                        
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = glassTheme.contentColor,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stop Button with high-contrast styling
                val isLightGlass = glassTheme.containerColor.red > 0.5f
                val buttonBgColor = if (isLightGlass) Color.Red.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.4f)
                val buttonContentColor = if (isLightGlass) Color.White else Color.Black.copy(0.7f)
                val iconContentColor = if (isLightGlass) Color.White else Color.Red.copy(0.4f)

                Surface(
                    onClick = {
                        onStopAdhan()
                        if (isTest) onStopTest()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = buttonBgColor,
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.adhan_stop).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = buttonContentColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
