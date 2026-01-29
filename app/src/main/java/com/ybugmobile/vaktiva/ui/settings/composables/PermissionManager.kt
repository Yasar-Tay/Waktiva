package com.ybugmobile.vaktiva.ui.settings.composables

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.utils.PermissionUtils

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionManager() {
    val context = LocalContext.current
    val permissions = mutableListOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        ModernPermissionItem(
            title = stringResource(R.string.settings_location_access),
            isGranted = permissionState.allPermissionsGranted,
            icon = Icons.Rounded.LocationOn
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = permissionState.permissions.find { it.permission == android.Manifest.permission.POST_NOTIFICATIONS }
            ModernPermissionItem(
                title = stringResource(R.string.settings_notifications),
                isGranted = notificationPermission?.status?.isGranted == true,
                icon = Icons.Rounded.NotificationsActive
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ModernPermissionItem(
                title = stringResource(R.string.settings_exact_alarm_title),
                isGranted = PermissionUtils.canScheduleExactAlarms(context) ?: false,
                icon = Icons.Rounded.Alarm
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_manage_system), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}
