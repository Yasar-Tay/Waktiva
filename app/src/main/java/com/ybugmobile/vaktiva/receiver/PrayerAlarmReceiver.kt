package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Unknown"
        Log.d("PrayerAlarmReceiver", "Alarm received for $prayerName")
        
        // Show high-priority notification with full-screen intent
        notificationHelper.showAdhanNotification(prayerName)
        
        // Audio playback will be handled by the AdhanActivity or a separate Service
        // For now, AdhanActivity is triggered by showAdhanNotification's fullScreenIntent
    }
}
