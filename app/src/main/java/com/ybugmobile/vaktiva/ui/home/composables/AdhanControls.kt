package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.home.HomeViewState

@Composable
fun AdhanControls(
    state: HomeViewState,
    onStopAdhan: () -> Unit,
    onStopTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showAdhanControls = state.isAdhanPlaying || state.nextPrayer?.isTest == true

    AnimatedVisibility(
        visible = showAdhanControls,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (state.isAdhanPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isAdhanPlaying) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (state.isAdhanPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.isAdhanPlaying) (state.playingPrayerName ?: stringResource(R.string.adhan_playing))
                        else if (state.nextPrayer?.isTest == true) stringResource(R.string.adhan_test_alarm)
                        else (state.nextPrayer?.type?.displayName ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (state.isAdhanPlaying) stringResource(R.string.adhan_sounding)
                        else if (state.nextPrayer?.isTest == true) stringResource(R.string.adhan_test_desc)
                        else stringResource(R.string.adhan_upcoming),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // STOP BUTTON
                Surface(
                    onClick = if (state.isAdhanPlaying) onStopAdhan else onStopTest,
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "STOP",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
