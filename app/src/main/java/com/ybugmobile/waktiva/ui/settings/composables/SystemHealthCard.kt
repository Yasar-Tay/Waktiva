package com.ybugmobile.waktiva.ui.settings.composables

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.utils.PermissionUtils

@Composable
fun SystemHealthCard(
    hasPrayerData: Boolean = true,
    showBackground: Boolean = true,
    showTitle: Boolean = true,
    contentColor: Color = Color.Unspecified,
    onIssuesChanged: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val glassTheme = LocalGlassTheme.current

    val isLightMode = glassTheme.isLightMode
    val resolvedContentColor = if (contentColor != Color.Unspecified) contentColor 
        else if (isLightMode) Color.White else Color.White

    var isGpsOff by remember { mutableStateOf(!PermissionUtils.isLocationEnabled(context)) }
    var isDndActive by remember { mutableStateOf(PermissionUtils.isDoNotDisturbActive(context)) }
    var areChannelsMuted by remember { mutableStateOf(PermissionUtils.areNotificationChannelsMuted(context)) }
    var isNetworkOffline by remember { mutableStateOf(!PermissionUtils.isNetworkAvailable(context)) }
    var isLocationPermissionMissing by remember { mutableStateOf(!PermissionUtils.isLocationPermissionGranted(context)) }
    var isNotificationPermissionMissing by remember { mutableStateOf(!PermissionUtils.isNotificationPermissionGranted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGpsOff = !PermissionUtils.isLocationEnabled(context)
                isDndActive = PermissionUtils.isDoNotDisturbActive(context)
                areChannelsMuted = PermissionUtils.areNotificationChannelsMuted(context)
                isNetworkOffline = !PermissionUtils.isNetworkAvailable(context)
                isLocationPermissionMissing = !PermissionUtils.isLocationPermissionGranted(context)
                isNotificationPermissionMissing = !PermissionUtils.isNotificationPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val issues = mutableListOf<HealthIssue>()

    val criticalColor = Color(0xFFFF5252)
    val warningColor = Color(0xFFFACC15)

    if (isLocationPermissionMissing) issues.add(HealthIssue(
        stringResource(R.string.home_permissions_required),
        Icons.Rounded.GpsFixed,
        PermissionUtils.getAppSettingsIntent(context),
        criticalColor
    ))

    if (isNotificationPermissionMissing) issues.add(HealthIssue(
        stringResource(R.string.health_channels_muted),
        Icons.Rounded.Notifications,
        PermissionUtils.getAppSettingsIntent(context),
        criticalColor
    ))

    if (isGpsOff) issues.add(HealthIssue(
        stringResource(R.string.health_gps_off),
        Icons.Rounded.LocationOff,
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        criticalColor
    ))

    if (areChannelsMuted) issues.add(HealthIssue(
        stringResource(R.string.health_channels_muted),
        Icons.Rounded.NotificationsPaused,
        PermissionUtils.getChannelSettingsIntent(context, NotificationHelper.CHANNEL_ID_ADHAN),
        criticalColor
    ))
    
    if (isDndActive) {
        val dndIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        issues.add(HealthIssue(
            stringResource(R.string.health_dnd_active),
            Icons.Rounded.DoNotDisturbOn,
            dndIntent,
            criticalColor
        ))
    }

    if (isNetworkOffline) {
        val connectivityIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        
        val networkMessage = if (hasPrayerData) {
            stringResource(R.string.health_no_internet)
        } else {
            stringResource(R.string.health_no_internet_no_data)
        }

        issues.add(HealthIssue(
            networkMessage,
            Icons.Rounded.CloudOff,
            connectivityIntent,
            if (hasPrayerData) warningColor else criticalColor
        ))
    }

    LaunchedEffect(issues.isNotEmpty()) {
        onIssuesChanged(issues.isNotEmpty())
    }

    AnimatedVisibility(
        visible = issues.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .then(
                    if (showBackground) {
                        val cardBackgroundColor = if (isLightMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.2f)
                        val cardBorderColor = if (isLightMode) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.1f)
                        
                        Modifier
                            .background(
                                color = cardBackgroundColor,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .border(1.dp, cardBorderColor, RoundedCornerShape(24.dp))
                            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                            .padding(20.dp)
                    } else {
                        Modifier
                    }
                )
        ) {
            if (showTitle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.HealthAndSafety, 
                        contentDescription = null, 
                        tint = resolvedContentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.health_title).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = resolvedContentColor
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            issues.forEach { issue ->
                HealthIssueItem(issue, resolvedContentColor)
            }
        }
    }
}

@Composable
private fun HealthIssueItem(issue: HealthIssue, textColor: Color) {
    val context = LocalContext.current

    Surface(
        onClick = { context.startActivity(issue.intent) },
        color = issue.accentColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(bottom = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, issue.accentColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                issue.icon, 
                null, 
                tint = issue.accentColor, 
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = issue.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.9f),
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = (if (issue.accentColor == Color(0xFFFACC15)) 
                        stringResource(R.string.nav_settings) 
                    else stringResource(R.string.health_fix_now)).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = issue.accentColor,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = issue.accentColor.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class HealthIssue(
    val message: String,
    val icon: ImageVector,
    val intent: Intent,
    val accentColor: Color
)
