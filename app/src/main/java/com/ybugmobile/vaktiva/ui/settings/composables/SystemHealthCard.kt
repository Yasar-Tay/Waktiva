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
import com.ybugmobile.vaktiva.utils.PermissionUtils

@Composable
fun SystemHealthCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isGpsOff by remember { mutableStateOf(!PermissionUtils.isLocationEnabled(context)) }
    var isDndActive by remember { mutableStateOf(PermissionUtils.isDoNotDisturbActive(context)) }
    var areChannelsMuted by remember { mutableStateOf(PermissionUtils.areNotificationChannelsMuted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGpsOff = !PermissionUtils.isLocationEnabled(context)
                isDndActive = PermissionUtils.isDoNotDisturbActive(context)
                areChannelsMuted = PermissionUtils.areNotificationChannelsMuted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val issues = mutableListOf<HealthIssue>()
    if (isGpsOff) issues.add(HealthIssue(
        stringResource(R.string.health_gps_off),
        Icons.Rounded.LocationOff,
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    ))
    if (areChannelsMuted) issues.add(HealthIssue(
        stringResource(R.string.health_channels_muted),
        Icons.Rounded.NotificationsPaused,
        PermissionUtils.getChannelSettingsIntent(context, NotificationHelper.CHANNEL_ID_ADHAN)
    ))
    if (isDndActive) issues.add(HealthIssue(
        stringResource(R.string.health_dnd_active),
        Icons.Rounded.DoNotDisturbOn,
        Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
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
                .background(Color(0xFFFF5252).copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFF5252), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.health_issues_detected).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFFFF5252)
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
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(issue.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { context.startActivity(issue.intent) },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
            ) {
                Text(stringResource(R.string.health_fix_now), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class HealthIssue(
    val message: String,
    val icon: ImageVector,
    val intent: Intent
)
