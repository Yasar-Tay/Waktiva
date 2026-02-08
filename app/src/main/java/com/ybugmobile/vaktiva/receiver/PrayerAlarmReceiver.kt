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
                
                // Just dismiss the notification immediately
                notificationHelper.cancelWarningNotification()
            }
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_PRE_ADHAN_NOTIFICATION -> {
                        val settings = settingsManager.settingsFlow.first()
                        
                        // If adhan audio or warnings are disabled, do not show the skip notification (even for tests)
                        if (!settings.playAdhanAudio || !settings.enablePreAdhanWarning) {
                            Log.d("PrayerAlarmReceiver", "PRE-ADHAN SUPPRESSED: Setting disabled.")
                            return@launch
                        }

                        val isMuted = settings.mutedPrayerName.equals(prayerName, ignoreCase = true) && 
                                     settings.mutedPrayerDate == prayerDate
                        
                        // If specifically muted/skipped, do not show the warning (even for tests)
                        if (isMuted) {
                            Log.d("PrayerAlarmReceiver", "PRE-ADHAN SUPPRESSED: Prayer is already muted.")
                            return@launch
                        }
                        
                        notificationHelper.showPreAdhanWarning(
                            prayerName = prayerName,
                            prayerDate = prayerDate,
                            minutes = settings.preAdhanWarningMinutes
                        )
                    }
                    AlarmScheduler.ACTION_PRAYER_ALARM -> {
                        handleAdhanTrigger(context, prayerName, prayerDate)
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
        
        // strictly honor the playAdhanAudio switch for all alarms including tests
        if (!settings.playAdhanAudio) {
            Log.d("PrayerAlarmReceiver", "ADHAN SUPPRESSED: playAdhanAudio is disabled.")
            settingsManager.clearMutedPrayer()
            notificationHelper.cancelWarningNotification()
            return
        }

        // strictly honor the muted/skipped state for all alarms including tests
        if (settings.mutedPrayerName.equals(prayerName, ignoreCase = true) && settings.mutedPrayerDate == prayerDate) {
            Log.d("PrayerAlarmReceiver", "ADHAN SKIPPED: Logic recognized skip for $prayerName.")
            settingsManager.clearMutedPrayer()
            notificationHelper.cancelWarningNotification()
            return
        }

        settingsManager.clearMutedPrayer()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vaktiva:AdhanWakeLock")
        wakeLock.acquire(5 * 60 * 1000L)

        try {
            notificationHelper.cancelWarningNotification()

            val prayerType = PrayerType.fromString(prayerName)
            val audioPath = if (settings.useSpecificAdhanForEachPrayer && prayerType != null) {
                settings.prayerSpecificAdhanPaths[prayerType] ?: getDefaultAdhanForPrayer(context, prayerType)
            } else {
                settings.selectedAdhanPath ?: "android.resource://${context.packageName}/${R.raw.muhsinkara_muhayyerkurdi_ezan}"
            }

            val serviceIntent = Intent(context, AdhanService::class.java).apply {
                putExtra(NotificationHelper.EXTRA_PRAYER_NAME, prayerName)
                putExtra("AUDIO_PATH", audioPath)
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

    private fun getDefaultAdhanForPrayer(context: Context, prayerType: PrayerType): String {
        val resId = when (prayerType) {
            PrayerType.FAJR -> R.raw.muhsinkara_fajr
            PrayerType.DHUHR -> R.raw.muhsinkara_muhayyerkurdi_ezan
            PrayerType.ASR -> R.raw.muhsinkara_asr
            PrayerType.MAGHRIB -> R.raw.muhsinkara_maghrib
            PrayerType.ISHA -> R.raw.muhsinkara_isha
            else -> R.raw.muhsinkara_muhayyerkurdi_ezan
        }
        return "android.resource://${context.packageName}/$resId"
    }
}
