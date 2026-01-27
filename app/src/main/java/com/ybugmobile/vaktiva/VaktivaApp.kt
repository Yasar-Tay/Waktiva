package com.ybugmobile.vaktiva

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ybugmobile.vaktiva.receiver.PrayerAlarmReceiver
import com.ybugmobile.vaktiva.service.AdhanService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VaktivaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. Channel for Pre-Adhan Warning (Skip Notification)
            val warningChannel = NotificationChannel(
                PrayerAlarmReceiver.CHANNEL_ID_WARNING,
                "Prayer Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders shown before the Adhan plays"
                enableVibration(true)
                setBypassDnd(true)
            }

            // 2. Channel for Adhan Playback (Stop Notification)
            val adhanChannel = NotificationChannel(
                AdhanService.CHANNEL_ID,
                "Adhan Playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active Adhan playback and controls"
                setSound(null, null) // Audio handled by ExoPlayer
                enableVibration(false)
            }

            manager.createNotificationChannel(warningChannel)
            manager.createNotificationChannel(adhanChannel)
        }
    }
}
