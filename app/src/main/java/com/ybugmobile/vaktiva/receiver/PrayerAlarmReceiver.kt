package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.service.AdhanService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Unknown"
        Log.d("PrayerAlarmReceiver", "Alarm received for $prayerName")
        
        // Show high-priority notification with full-screen intent
        notificationHelper.showAdhanNotification(prayerName)
        
        // Start background audio service if enabled in settings
        CoroutineScope(Dispatchers.IO).launch {
            val settings = settingsManager.settingsFlow.first()
            val audioPath = settings.selectedAdhanPath
            
            if (settings.playAdhanAudio && !audioPath.isNullOrEmpty()) {
                val serviceIntent = Intent(context, AdhanService::class.java).apply {
                    putExtra("AUDIO_PATH", audioPath)
                    putExtra("PRAYER_NAME", prayerName)
                }
                context.startForegroundService(serviceIntent)
            } else {
                Log.d("PrayerAlarmReceiver", "Adhan audio is disabled or path is empty")
            }
        }
    }
}
