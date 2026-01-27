package com.ybugmobile.vaktiva.utils

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object PermissionUtils {

    /**
     * Checks if the app can schedule exact alarms.
     * Required for Android 12 (API 31) and above.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Returns an Intent to open the "Alarms & Reminders" settings page for the app.
     */
    fun getExactAlarmSettingIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            null
        }
    }

    /**
     * Checks if the app is ignoring battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Returns an Intent to request the user to disable battery optimizations for the app.
     * Note: Using ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS might lead to Play Store rejection
     * if not justified. For prayer apps, it is usually justifiable.
     */
    fun getIgnoreBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    
    /**
     * Returns an Intent to open the general battery optimization settings page.
     * Use this as a fallback or if you want to be safer with Play Store policies.
     */
    fun getBatteryOptimizationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
