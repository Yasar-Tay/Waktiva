package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Unknown"
        Log.d("PrayerAlarmReceiver", "Alarm received for $prayerName")
        
        // TODO: Trigger Audio Service or Full Screen Intent (Feature 3.2)
    }
}