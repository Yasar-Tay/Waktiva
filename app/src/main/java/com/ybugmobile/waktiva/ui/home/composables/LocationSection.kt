package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.ui.settings.composables.SystemHealthIndicator
import com.ybugmobile.waktiva.utils.PermissionUtils

@Composable
fun LocationSection(
    locationName: String,
    contentColor: Color,
    isNetworkAvailable: Boolean,
    isLocationEnabled: Boolean,
    isLocationPermissionGranted: Boolean,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isArabic = configuration.locales[0].language == "ar"
    
    val unknownStr = stringResource(R.string.home_unknown_location)
    
    // Check all health issues that SystemHealth handles
    val isNotificationGranted = PermissionUtils.isNotificationPermissionGranted(context)
    val areChannelsMuted = PermissionUtils.areNotificationChannelsMuted(context)
    val isDndActive = PermissionUtils.isDoNotDisturbActive(context)
    
    val hasIssue = !isLocationPermissionGranted || !isLocationEnabled || !isNetworkAvailable || 
                   !isNotificationGranted || areChannelsMuted || isDndActive

    val rawCity = locationName.substringBefore(",")
    val city = if (rawCity == "Unknown" || rawCity.isEmpty()) unknownStr else rawCity
    
    val healthIssueTitle = when {
        !isLocationPermissionGranted -> stringResource(R.string.location_health_permission_denied)
        !isLocationEnabled -> stringResource(R.string.location_health_gps_off)
        !isNetworkAvailable -> stringResource(R.string.location_health_offline)
        !isNotificationGranted -> stringResource(R.string.location_health_notifications_off)
        areChannelsMuted -> stringResource(R.string.location_health_channels_muted)
        isDndActive -> stringResource(R.string.location_health_dnd_active)
        else -> ""
    }

    val subTitle = if (hasIssue) {
        if (city != unknownStr && city.isNotEmpty()) {
            stringResource(R.string.home_last_known_location, city)
        } else ""
    } else {
        locationName.substringAfter(", ").ifEmpty { "" }
    }

    Row(
        modifier = modifier.clickable { onStatusClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: Icon / Status Indicator
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasIssue) {
                SystemHealthIndicator(
                    onClick = onStatusClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Column 2: Location Information
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (hasIssue) healthIssueTitle else city,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                letterSpacing = (-0.5).sp,
                maxLines = 1
            )
            
            if (subTitle.isNotEmpty()) {
                Text(
                    text = subTitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isArabic) 14.sp else 11.sp
                    ),
                    color = contentColor.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
            }
        }
    }
}
