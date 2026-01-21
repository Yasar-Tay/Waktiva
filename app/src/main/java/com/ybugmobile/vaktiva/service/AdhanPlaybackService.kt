package com.ybugmobile.vaktiva.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ybugmobile.vaktiva.MainActivity
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.audio.AudioPlayer
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.domain.model.PrayerType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdhanPlaybackService : Service() {

    @Inject
    lateinit var audioPlayer: AudioPlayer

    @Inject
    lateinit var settingsManager: SettingsManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val CHANNEL_ID = "adhan_playback_channel"
    private val NOTIFICATION_ID = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: return START_NOT_STICKY

        // 1. Create Notification Channel (Required for Android O+)
        createNotificationChannel()

        // 2. Start Foreground immediately (Crucial for background playback)
        startForeground(NOTIFICATION_ID, buildNotification(prayerName))

        // 3. Play Audio
        serviceScope.launch {
            playAdhan(prayerName)
        }

        return START_NOT_STICKY
    }

    private suspend fun playAdhan(prayerName: String) {
        try {
            val settings = settingsManager.settingsFlow.first()
            if (!settings.playAdhanAudio) {
                stopSelf()
                return
            }

            val prayerType = PrayerType.fromString(prayerName)
            val audioPath = if (settings.useSpecificAdhanForEachPrayer && prayerType != null && prayerType != PrayerType.SUNRISE) {
                settings.prayerSpecificAdhanPaths[prayerType] ?: settings.selectedAdhanPath
            } else {
                settings.selectedAdhanPath
            }

            val finalAudioPath = audioPath ?: "android.resource://${packageName}/${R.raw.ezan}"
            audioPlayer.play(Uri.parse(finalAudioPath))

            // Note: Ideally, AudioPlayer should expose a completion listener to call stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun buildNotification(prayerName: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Adhan: $prayerName")
            .setContentText("Time for prayer")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this icon exists
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Adhan Playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notification when Adhan is playing"
                setSound(null, null) // Audio is handled by ExoPlayer, not the notification sound
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
