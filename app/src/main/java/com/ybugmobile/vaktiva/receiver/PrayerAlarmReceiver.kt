package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.service.AdhanService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsManager: SettingsManager
    
    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prayerName = intent.getStringExtra(NotificationHelper.EXTRA_PRAYER_NAME) ?: return

        Log.d("PrayerAlarmReceiver", "onReceive: action=$action, prayer=$prayerName")
        
        if (action == NotificationHelper.ACTION_SKIP_ADHAN) {
            runBlocking {
                val today = LocalDate.now().toString()
                settingsManager.muteNextPrayer(prayerName, today)
                Log.d("PrayerAlarmReceiver", "SKIP SUCCESS: $prayerName muted for $today")
            }
            notificationHelper.showPreAdhanWarning(prayerName, isMuted = true)
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_PRE_ADHAN_NOTIFICATION -> {
                        notificationHelper.showPreAdhanWarning(prayerName, isMuted = false)
                    }
                    AlarmScheduler.ACTION_PRAYER_ALARM -> {
                        handleAdhanTrigger(context, prayerName)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAdhanTrigger(context: Context, prayerName: String) {
        val settings = settingsManager.settingsFlow.first()
        val today = LocalDate.now().toString()

        // If it was muted, don't start the service and clean up
        if (settings.mutedPrayerName.equals(prayerName, ignoreCase = true) && settings.mutedPrayerDate == today) {
            Log.d("PrayerAlarmReceiver", "ADHAN SKIPPED: Logic recognized skip for $prayerName. Resetting mute for tomorrow.")
            settingsManager.clearMutedPrayer()
            notificationHelper.cancelWarningNotification()
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vaktiva:AdhanWakeLock")
        wakeLock.acquire(5 * 60 * 1000L)

        try {
            // Dismiss warning notification as we are starting the real adhan
            notificationHelper.cancelWarningNotification()

            val prayerType = PrayerType.fromString(prayerName)
            val audioPath = if (settings.useSpecificAdhanForEachPrayer && prayerType != null) {
                settings.prayerSpecificAdhanPaths[prayerType] ?: settings.selectedAdhanPath
            } else settings.selectedAdhanPath

            val finalPath = audioPath ?: "android.resource://${context.packageName}/${R.raw.ezan}"
            val serviceIntent = Intent(context, AdhanService::class.java).apply {
                putExtra("PRAYER_NAME", prayerName)
                putExtra("AUDIO_PATH", finalPath)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
