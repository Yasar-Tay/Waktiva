package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
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
    lateinit var settingsManager: SettingsManagerInterface
    
    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    lateinit var prayerRepository: PrayerRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prayerName = intent.getStringExtra(NotificationHelper.EXTRA_PRAYER_NAME) ?: return
        val prayerDate = intent.getStringExtra(NotificationHelper.EXTRA_PRAYER_DATE) ?: LocalDate.now().toString()

        Log.d("PrayerAlarmReceiver", "onReceive: action=$action, prayer=$prayerName, date=$prayerDate")
        
        if (action == NotificationHelper.ACTION_SKIP_ADHAN) {
            runBlocking {
                settingsManager.muteNextPrayer(prayerName, prayerDate)
                Log.d("PrayerAlarmReceiver", "SKIP SUCCESS: $prayerName muted for $prayerDate")
                
                // Update notification immediately to show muted state
                val settings = settingsManager.settingsFlow.first()
                notificationHelper.showPreAdhanWarning(
                    prayerName = prayerName,
                    prayerDate = prayerDate,
                    minutes = settings.preAdhanWarningMinutes,
                    isMuted = true
                )
            }
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_PRE_ADHAN_NOTIFICATION -> {
                        val settings = settingsManager.settingsFlow.first()
                        
                        // If adhan audio is disabled, do not show the skip notification
                        if (!settings.playAdhanAudio) {
                            Log.d("PrayerAlarmReceiver", "PRE-ADHAN SUPPRESSED: playAdhanAudio is disabled.")
                            return@launch
                        }

                        val isMuted = settings.mutedPrayerName.equals(prayerName, ignoreCase = true) && 
                                     settings.mutedPrayerDate == prayerDate
                        
                        notificationHelper.showPreAdhanWarning(
                            prayerName = prayerName,
                            prayerDate = prayerDate,
                            minutes = settings.preAdhanWarningMinutes,
                            isMuted = isMuted
                        )
                    }
                    AlarmScheduler.ACTION_PRAYER_ALARM -> {
                        handleAdhanTrigger(context, prayerName, prayerDate)
                        // Only reschedule when a prayer time is actually reached
                        rescheduleNextPrayer()
                    }
                    Intent.ACTION_BOOT_COMPLETED -> {
                        rescheduleNextPrayer()
                    }
                }
            } catch (e: Exception) {
                Log.e("PrayerAlarmReceiver", "Error in onReceive", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun rescheduleNextPrayer() {
        val settings = settingsManager.settingsFlow.first()
        val prayerDays = prayerRepository.getPrayerDays().first()
        if (prayerDays.isNotEmpty()) {
            alarmScheduler.scheduleNextAlarm(
                prayerDays, 
                settings.enablePreAdhanWarning, 
                settings.preAdhanWarningMinutes
            )
        }
    }

    private suspend fun handleAdhanTrigger(context: Context, prayerName: String, prayerDate: String) {
        val settings = settingsManager.settingsFlow.first()
        
        // If it's a test alarm, we ignore some settings
        val isTest = settings.testAlarmEndTime != null
        
        if (!settings.playAdhanAudio && !isTest) {
            Log.d("PrayerAlarmReceiver", "ADHAN SUPPRESSED: playAdhanAudio is disabled.")
            settingsManager.clearMutedPrayer()
            return
        }

        if (settings.mutedPrayerName.equals(prayerName, ignoreCase = true) && settings.mutedPrayerDate == prayerDate) {
            Log.d("PrayerAlarmReceiver", "ADHAN SKIPPED: Logic recognized skip for $prayerName.")
            settingsManager.clearMutedPrayer()
            notificationHelper.cancelWarningNotification()
            return
        }

        // Always clear muted prayer state when an alarm triggers (and is not skipped)
        settingsManager.clearMutedPrayer()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vaktiva:AdhanWakeLock")
        wakeLock.acquire(5 * 60 * 1000L)

        try {
            notificationHelper.cancelWarningNotification()

            val prayerType = PrayerType.fromString(prayerName)
            val audioPath = if (settings.useSpecificAdhanForEachPrayer && prayerType != null) {
                settings.prayerSpecificAdhanPaths[prayerType] ?: settings.selectedAdhanPath
            } else settings.selectedAdhanPath

            val finalPath = audioPath ?: "android.resource://${context.packageName}/${R.raw.ezan}"
            val serviceIntent = Intent(context, AdhanService::class.java).apply {
                putExtra(NotificationHelper.EXTRA_PRAYER_NAME, prayerName)
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
