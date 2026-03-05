package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.service.AdhanService
import com.ybugmobile.vaktiva.ui.widget.VaktivaWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
        
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            scope.launch {
                rescheduleNextPrayer()
                VaktivaWidget().updateAll(context)
                updatePersistentNotification()
                pendingResult.finish()
            }
            return
        }

        val prayerName = intent.getStringExtra(NotificationHelper.EXTRA_PRAYER_NAME) ?: return
        val prayerDate = intent.getStringExtra(NotificationHelper.EXTRA_PRAYER_DATE) ?: LocalDate.now().toString()

        Log.d("PrayerAlarmReceiver", "onReceive: action=$action, prayer=$prayerName, date=$prayerDate")
        
        if (action == NotificationHelper.ACTION_SKIP_ADHAN) {
            runBlocking {
                settingsManager.muteNextPrayer(prayerName, prayerDate)
                notificationHelper.cancelWarningNotification()
                VaktivaWidget().updateAll(context)
                updatePersistentNotification()
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
                        VaktivaWidget().updateAll(context)
                        handleAdhanTrigger(context, prayerName, prayerDate)
                        rescheduleNextPrayer()
                        updatePersistentNotification()
                    }
                }
            } catch (e: Exception) {
                Log.e("PrayerAlarmReceiver", "Error in onReceive", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updatePersistentNotification() {
        val settings = settingsManager.settingsFlow.first()
        if (settings.showPersistentNotification) {
            val prayerDays = prayerRepository.getPrayerDays().first()
            val now = LocalDateTime.now()
            val today = prayerDays.find { it.date == LocalDate.now() }
            
            val nextPrayer = today?.let { day ->
                val nowTime = now.toLocalTime()
                val nextReal = day.timings.entries
                    .filter { it.value.isAfter(nowTime) }
                    .minByOrNull { it.value }
                
                nextReal?.let {
                    NextPrayer(it.key, it.value, day.date, Duration.between(now, day.date.atTime(it.value)))
                }
            }
            
            if (nextPrayer != null) {
                notificationHelper.showPersistentNotification(nextPrayer)
            } else {
                notificationHelper.hidePersistentNotification()
            }
        } else {
            notificationHelper.hidePersistentNotification()
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
