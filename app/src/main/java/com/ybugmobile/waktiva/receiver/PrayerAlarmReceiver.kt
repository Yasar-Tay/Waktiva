package com.ybugmobile.waktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.alarm.AlarmScheduler
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.data.worker.AdhanWorker
import com.ybugmobile.waktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.ui.widget.WaktivaWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * A [BroadcastReceiver] responsible for handling all prayer-related alarm events.
 */
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
            val pendingResult = goAsync()
            scope.launch {
                try {
                    settingsManager.muteNextPrayer(prayerName, prayerDate)
                    notificationHelper.cancelWarningNotification()
                    WaktivaWidget().updateAll(context)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_PRE_ADHAN_NOTIFICATION -> {
                        val settings = settingsManager.settingsFlow.first()
                        if (!settings.playAdhanAudio || !settings.enablePreAdhanWarning) return@launch

                        val isMuted = settings.mutedPrayerName.equals(prayerName, ignoreCase = true) && 
                                     settings.mutedPrayerDate == prayerDate
                        
                        if (isMuted) return@launch
                        
                        notificationHelper.showPreAdhanWarning(
                            prayerName = prayerName,
                            prayerDate = prayerDate,
                            minutes = settings.preAdhanWarningMinutes
                        )
                    }
                    AlarmScheduler.ACTION_PRAYER_ALARM -> {
                        WaktivaWidget().updateAll(context)
                        handleAdhanTrigger(context, prayerName, prayerDate)
                        rescheduleNextPrayer()
                    }
                    AlarmScheduler.ACTION_WIDGET_REFRESH -> {
                        // This action wakes the app to ensure transitions like Sunrise -> Dhuhr
                        // happen exactly on time in the widget without a negative countdown.
                        Log.d("PrayerAlarmReceiver", "Executing WIDGET REFRESH milestone transition")
                        WaktivaWidget().updateAll(context)
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
        
        // Don't trigger adhan for Sunrise (it's handled by ACTION_WIDGET_REFRESH)
        if (prayerName.equals(com.ybugmobile.waktiva.domain.model.PrayerType.SUNRISE.name, ignoreCase = true)) {
            return
        }

        if (!settings.playAdhanAudio) {
            settingsManager.clearMutedPrayer()
            notificationHelper.cancelWarningNotification()
            return
        }

        if (settings.mutedPrayerName.equals(prayerName, ignoreCase = true) && settings.mutedPrayerDate == prayerDate) {
            settingsManager.clearMutedPrayer()
            notificationHelper.cancelWarningNotification()
            return
        }

        settingsManager.clearMutedPrayer()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Waktiva:AdhanWakeLock")
        wakeLock.acquire(5 * 60 * 1000L)

        try {
            notificationHelper.cancelWarningNotification()
            val prayerType = com.ybugmobile.waktiva.domain.model.PrayerType.fromString(prayerName)
            val audioPath = if (settings.useSpecificAdhanForEachPrayer && prayerType != null) {
                settings.prayerSpecificAdhanPaths[prayerType] ?: getDefaultAdhanForPrayer(context, prayerType)
            } else {
                settings.selectedAdhanPath ?: "android.resource://${context.packageName}/${R.raw.muhsinkara_muhayyerkurdi_ezan}"
            }

            // WorkManager.enqueueUniqueWork() is exempt from BOOT_COMPLETED FGS restrictions.
            // AdhanWorker calls setForeground() internally — not startForegroundService().
            val inputData = Data.Builder()
                .putString(AdhanWorker.KEY_PRAYER_NAME, prayerName)
                .putString(AdhanWorker.KEY_AUDIO_PATH, audioPath)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AdhanWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                AdhanWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun getDefaultAdhanForPrayer(context: Context, prayerType: com.ybugmobile.waktiva.domain.model.PrayerType): String {
        val resId = when (prayerType) {
            com.ybugmobile.waktiva.domain.model.PrayerType.FAJR -> R.raw.muhsinkara_fajr
            com.ybugmobile.waktiva.domain.model.PrayerType.DHUHR -> R.raw.muhsinkara_muhayyerkurdi_ezan
            com.ybugmobile.waktiva.domain.model.PrayerType.ASR -> R.raw.muhsinkara_asr
            com.ybugmobile.waktiva.domain.model.PrayerType.MAGHRIB -> R.raw.muhsinkara_maghrib
            com.ybugmobile.waktiva.domain.model.PrayerType.ISHA -> R.raw.muhsinkara_isha
            else -> R.raw.muhsinkara_muhayyerkurdi_ezan
        }
        return "android.resource://${context.packageName}/$resId"
    }
}
