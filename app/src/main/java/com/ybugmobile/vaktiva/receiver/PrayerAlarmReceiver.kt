package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.PrayerType
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
        val isWarning = intent.getBooleanExtra("IS_WARNING", false)
        
        Log.d("PrayerAlarmReceiver", "Alarm received for $prayerName (Warning: $isWarning)")
        
        if (isWarning) {
            handleWarningAlarm(context, prayerName)
        } else {
            handleAdhanAlarm(context, prayerName)
        }
    }

    private fun handleWarningAlarm(context: Context, prayerName: String) {
        // Show a "soft" notification or a minor sound
        // For now, we use a simple notification via notificationHelper with a special flag
        notificationHelper.showPreAdhanWarning(prayerName)
    }

    private fun handleAdhanAlarm(context: Context, prayerName: String) {
        // Show high-priority notification with full-screen intent
        notificationHelper.showAdhanNotification(prayerName)
        
        // Start background audio service
        CoroutineScope(Dispatchers.IO).launch {
            val settings = settingsManager.settingsFlow.first()
            if (!settings.playAdhanAudio) return@launch

            val prayerType = try { PrayerType.valueOf(prayerName.uppercase()) } catch (e: Exception) { null }
            
            val audioPath = if (settings.useSpecificAdhanForEachPrayer && prayerType != null) {
                settings.prayerSpecificAdhanPaths[prayerType] ?: settings.selectedAdhanPath
            } else {
                settings.selectedAdhanPath
            }
            
            if (!audioPath.isNullOrEmpty()) {
                val serviceIntent = Intent(context, AdhanService::class.java).apply {
                    putExtra("AUDIO_PATH", audioPath)
                    putExtra("PRAYER_NAME", prayerName)
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
