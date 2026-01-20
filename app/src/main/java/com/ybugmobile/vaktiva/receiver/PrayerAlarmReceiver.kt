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
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val prayerName = intent.getStringExtra("PRAYER_NAME") 
            ?: intent.getStringExtra(NotificationHelper.EXTRA_PRAYER_NAME) 
            ?: "Unknown"
            
        if (action == NotificationHelper.ACTION_SKIP_PRAYER) {
            handleSkipAction(prayerName)
            return
        }

        val isWarning = intent.getBooleanExtra("IS_WARNING", false)
        
        Log.d("PrayerAlarmReceiver", "Alarm received for $prayerName (Warning: $isWarning)")
        
        if (isWarning) {
            handleWarningAlarm(context, prayerName)
        } else {
            handleAdhanAlarm(context, prayerName)
        }
    }

    private fun handleSkipAction(prayerName: String) {
        Log.d("PrayerAlarmReceiver", "Skipping audio for $prayerName")
        CoroutineScope(Dispatchers.IO).launch {
            settingsManager.muteNextPrayer(prayerName, LocalDate.now().toString())
            notificationHelper.cancelNotification(NotificationHelper.WARNING_NOTIFICATION_ID)
        }
    }

    private fun handleWarningAlarm(context: Context, prayerName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = settingsManager.settingsFlow.first()
            if (settings.enablePreAdhanWarning) {
                notificationHelper.showPreAdhanWarning(prayerName)
            }
        }
    }

    private fun handleAdhanAlarm(context: Context, prayerName: String) {
        // Show high-priority notification with full-screen intent
        notificationHelper.showAdhanNotification(prayerName)
        
        // Start background audio service
        CoroutineScope(Dispatchers.IO).launch {
            val settings = settingsManager.settingsFlow.first()
            
            // Check if this prayer is muted (skipped via notification or UI)
            if (settings.mutedPrayerName == prayerName && settings.mutedPrayerDate == LocalDate.now().toString()) {
                Log.d("PrayerAlarmReceiver", "Adhan for $prayerName is muted by user.")
                settingsManager.clearMutedPrayer()
                return@launch
            }
            
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
            
            // Clear mute state after the prayer time has passed/handled
            settingsManager.clearMutedPrayer()
        }
    }
}
