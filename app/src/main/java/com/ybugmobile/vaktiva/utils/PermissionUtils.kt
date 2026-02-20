package com.ybugmobile.vaktiva.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ybugmobile.vaktiva.data.notification.NotificationHelper

/**
 * Utility object for checking and requesting system permissions and settings.
 *
 * This object provides helper methods to check for:
 * - Exact alarm permissions (Android 12+).
 * - Battery optimization status.
 * - Global location services (GPS) status.
 * - Notification channel and DND (Do Not Disturb) status.
 * - Network availability.
 * - Basic runtime permissions (Location, Notifications).
 */
object PermissionUtils {

    /**
     * Checks if the app is permitted to schedule exact alarms.
     * Required for Android 12 (API 31) and above to ensure Adhan sounds exactly on time.
     *
     * @param context The application context.
     * @return True if the app can schedule exact alarms, false otherwise.
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
     * Creates an Intent to open the system's "Alarms & Reminders" settings page for this app.
     *
     * @param context The application context.
     * @return The [Intent] to launch the settings, or null if the API version is below Android 12.
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
     * Checks if the app has been excluded from battery optimizations.
     * This is critical for background tasks like Adhan scheduling.
     *
     * @param context The application context.
     * @return True if the app is ignoring battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Creates an Intent to request the user to allow the app to ignore battery optimizations.
     *
     * @param context The application context.
     * @return The [Intent] to prompt the user.
     */
    fun getIgnoreBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    
    /**
     * Creates an Intent to open the general battery optimization settings page.
     *
     * @return The [Intent] to open settings.
     */
    fun getBatteryOptimizationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    /**
     * Checks if Global Location Services (GPS or Network) are enabled on the device.
     *
     * @param context The application context.
     * @return True if location services are enabled.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Checks if important notification channels have been disabled by the user in system settings.
     *
     * @param context The application context.
     * @return True if either the Adhan or Warning channel is disabled.
     */
    fun areNotificationChannelsMuted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val adhanChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID_ADHAN)
            val warningChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID_WARNING)
            
            val isAdhanMuted = adhanChannel?.importance == NotificationManager.IMPORTANCE_NONE
            val isWarningMuted = warningChannel?.importance == NotificationManager.IMPORTANCE_NONE
            
            return isAdhanMuted || isWarningMuted
        }
        return false
    }

    /**
     * Checks if "Do Not Disturb" (DND) mode is currently active and potentially blocking audio.
     *
     * @param context The application context.
     * @return True if DND is active and filtering notifications.
     */
    fun isDoNotDisturbActive(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = notificationManager.currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /**
     * Creates an Intent to open the notification settings for a specific channel.
     * Fallbacks to general application details on older Android versions.
     *
     * @param context The application context.
     * @param channelId The ID of the notification channel.
     * @return The [Intent] to open the settings page.
     */
    fun getChannelSettingsIntent(context: Context, channelId: String): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }

    /**
     * Checks for an active internet connection (WiFi, Cellular, or Ethernet).
     *
     * @param context The application context.
     * @return True if the network is available and capable of data transfer.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    /**
     * Checks if the app has been granted any level of location permission (Fine or Coarse).
     */
    fun isLocationPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the app has permission to post notifications (Required for Android 13+).
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Returns an Intent to open the general system settings page for the app.
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
}
