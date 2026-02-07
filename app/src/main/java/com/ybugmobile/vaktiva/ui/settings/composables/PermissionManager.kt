package com.ybugmobile.vaktiva.ui.settings.composables

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.utils.PermissionUtils

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionManager() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionsList = remember {
        mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val permissionStates = rememberMultiplePermissionsState(permissionsList)

    var isIgnoringBatteryOptimizations by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }
    var canScheduleExactAlarms by remember { mutableStateOf(PermissionUtils.canScheduleExactAlarms(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = PermissionUtils.isIgnoringBatteryOptimizations(context)
                canScheduleExactAlarms = PermissionUtils.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationGranted = permissionStates.permissions
        .filter { it.permission.contains("LOCATION") }
        .all { it.status.isGranted }

    val greenColor = Color(0xFF4CAF50)
    val redColor = Color(0xFFF87171)

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        // Location Access
        SettingsClickItem(
            title = stringResource(R.string.settings_location_access),
            subtitle = if (locationGranted) stringResource(R.string.settings_granted) else stringResource(R.string.settings_location_desc),
            icon = Icons.Rounded.LocationOn,
            iconColor = if (locationGranted) greenColor else redColor,
            onClick = {
                if (locationGranted) {
                    openAppSettings()
                } else {
                    permissionStates.launchMultiplePermissionRequest()
                }
            }
        )

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = permissionStates.permissions.find { it.permission == android.Manifest.permission.POST_NOTIFICATIONS }
            val isNotifGranted = notificationPermission?.status?.isGranted ?: false
            SettingsClickItem(
                title = stringResource(R.string.settings_notifications),
                subtitle = if (isNotifGranted) stringResource(R.string.settings_granted) else stringResource(R.string.settings_notifications_desc),
                icon = Icons.Rounded.NotificationsActive,
                iconColor = if (isNotifGranted) greenColor else redColor,
                onClick = {
                    if (isNotifGranted) {
                        openAppSettings()
                    } else {
                        notificationPermission?.launchPermissionRequest()
                    }
                }
            )
        }

        // Exact Alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsClickItem(
                title = stringResource(R.string.settings_exact_alarm_title),
                subtitle = if (canScheduleExactAlarms) stringResource(R.string.settings_granted) else stringResource(R.string.settings_exact_alarm_desc),
                icon = Icons.Rounded.Alarm,
                iconColor = if (canScheduleExactAlarms) greenColor else redColor,
                onClick = {
                    if (!canScheduleExactAlarms) {
                        PermissionUtils.getExactAlarmSettingIntent(context)?.let {
                            context.startActivity(it)
                        }
                    } else {
                        openAppSettings()
                    }
                }
            )
        }

        // Battery Optimization
        SettingsClickItem(
            title = stringResource(R.string.settings_battery_opt),
            subtitle = if (isIgnoringBatteryOptimizations) stringResource(R.string.settings_battery_opt_enabled) else stringResource(R.string.settings_battery_opt_disabled),
            icon = Icons.Rounded.BatteryChargingFull,
            iconColor = if (isIgnoringBatteryOptimizations) greenColor else redColor,
            onClick = {
                try {
                    if (!isIgnoringBatteryOptimizations) {
                        context.startActivity(PermissionUtils.getIgnoreBatteryOptimizationIntent(context))
                    } else {
                        context.startActivity(PermissionUtils.getBatteryOptimizationSettingsIntent())
                    }
                } catch (e: Exception) {
                    openAppSettings()
                }
            }
        )
    }
}
