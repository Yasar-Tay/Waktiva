package com.ybugmobile.vaktiva

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main application class for Waktiva.
 * 
 * This class serves as the entry point for the application and is responsible for:
 * - Initializing Hilt dependency injection ([HiltAndroidApp]).
 * - Providing custom [Configuration] for [WorkManager] to support Hilt-injected Workers.
 * - Setting up system-level notification channels required for Adhan and prayer warnings.
 */
@HiltAndroidApp
class VaktivaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Configures WorkManager with a Hilt-aware worker factory.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Notification channels must be created before notifications are posted
        createNotificationChannels()
    }

    /**
     * Creates the mandatory notification channels for Android O (API 26) and above.
     * 
     * Two primary channels are established:
     * 1. **Prayer Warnings:** High importance for pre-Adhan alerts with "Skip" actions.
     * 2. **Adhan Playback:** High importance for the active Adhan media notification.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. Channel for Pre-Adhan Warning (Skip Notification)
            val warningChannel = NotificationChannel(
                NotificationHelper.CHANNEL_ID_WARNING,
                "Prayer Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders shown before the Adhan plays"
                enableVibration(true)
                setBypassDnd(true)
            }

            // 2. Channel for Adhan Playback (Stop Notification)
            val adhanChannel = NotificationChannel(
                NotificationHelper.CHANNEL_ID_ADHAN,
                "Adhan Playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active Adhan playback and controls"
                setSound(null, null) // Audio handled by ExoPlayer
                enableVibration(false)
            }

            manager?.createNotificationChannel(warningChannel)
            manager?.createNotificationChannel(adhanChannel)
        }
    }
}
