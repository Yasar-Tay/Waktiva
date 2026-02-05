package com.ybugmobile.vaktiva.ui.settings.composables

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.vaktiva.utils.PermissionUtils

@Composable
fun SystemHealthCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val glassTheme = LocalGlassTheme.current

    var isGpsOff by remember { mutableStateOf(!PermissionUtils.isLocationEnabled(context)) }
    var isDndActive by remember { mutableStateOf(PermissionUtils.isDoNotDisturbActive(context)) }
    var areChannelsMuted by remember { mutableStateOf(PermissionUtils.areNotificationChannelsMuted(context)) }
    var isNetworkOffline by remember { mutableStateOf(!PermissionUtils.isNetworkAvailable(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGpsOff = !PermissionUtils.isLocationEnabled(context)
                isDndActive = PermissionUtils.isDoNotDisturbActive(context)
                areChannelsMuted = PermissionUtils.areNotificationChannelsMuted(context)
                isNetworkOffline = !PermissionUtils.isNetworkAvailable(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val issues = mutableListOf<HealthIssue>()
    
    // Critical System Issues
    if (isGpsOff) issues.add(HealthIssue(
        stringResource(R.string.health_gps_off),
        Icons.Rounded.LocationOff,
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        Color(0xFFFF5252)
    ))
    if (areChannelsMuted) issues.add(HealthIssue(
        stringResource(R.string.health_channels_muted),
        Icons.Rounded.NotificationsPaused,
        PermissionUtils.getChannelSettingsIntent(context, NotificationHelper.CHANNEL_ID_ADHAN),
        Color(0xFFFF5252)
    ))
    if (isDndActive) issues.add(HealthIssue(
        stringResource(R.string.health_dnd_active),
        Icons.Rounded.DoNotDisturbOn,
        Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
        Color(0xFFFF5252)
    ))

    // Connectivity Warning
    if (isNetworkOffline) issues.add(HealthIssue(
        stringResource(R.string.health_no_internet),
        Icons.Rounded.WifiOff,
        Intent(Settings.ACTION_WIFI_SETTINGS),
        Color(0xFFFACC15)
    ))

    AnimatedVisibility(
        visible = issues.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .background(
                    color = glassTheme.containerColor.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.HealthAndSafety, 
                    contentDescription = null, 
                    tint = glassTheme.contentColor, 
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.health_title).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = glassTheme.contentColor
                )
            }

            Spacer(Modifier.height(16.dp))

            issues.forEach { issue ->
                HealthIssueItem(issue)
            }
        }
    }
}

@Composable
private fun HealthIssueItem(issue: HealthIssue) {
    val context = LocalContext.current
    val glassTheme = LocalGlassTheme.current
    
    Surface(
        color = issue.accentColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(bottom = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, issue.accentColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(issue.icon, null, tint = issue.accentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodySmall,
                color = glassTheme.contentColor.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f),
                lineHeight = 16.sp
            )
            TextButton(
                onClick = { context.startActivity(issue.intent) },
                colors = ButtonDefaults.textButtonColors(contentColor = issue.accentColor)
            ) {
                Text(
                    text = if (issue.accentColor == Color(0xFFFACC15)) stringResource(R.string.nav_settings) else stringResource(R.string.health_fix_now), 
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private data class HealthIssue(
    val message: String,
    val icon: ImageVector,
    val intent: Intent,
    val accentColor: Color
)
