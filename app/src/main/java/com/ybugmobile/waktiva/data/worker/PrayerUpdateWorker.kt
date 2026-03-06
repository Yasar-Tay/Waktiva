package com.ybugmobile.waktiva.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ybugmobile.waktiva.data.alarm.AlarmScheduler
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@HiltWorker
class PrayerUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PrayerRepository,
    private val settingsManager: SettingsManager,
    private val alarmScheduler: AlarmScheduler
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val settings = settingsManager.settingsFlow.first()
            if (settings.latitude == null || settings.longitude == null) {
                Log.d("PrayerUpdateWorker", "No location stored, skipping update")
                return Result.success()
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()
            val currentDate = sdf.format(calendar.time)
            
            val remainingDays = repository.getRemainingDaysCount(currentDate)
            Log.d("PrayerUpdateWorker", "Remaining days in cache: $remainingDays")
            
            // If less than 15 days of data remaining, proactively fetch to maintain a buffer
            if (remainingDays < 15) {
                Log.d("PrayerUpdateWorker", "Proactively fetching prayer times for next 3 months")
                
                var allSuccess = true
                
                // Fetch current and next 2 months (Total 3 months)
                for (i in 0..2) {
                    val fetchCal = calendar.clone() as Calendar
                    fetchCal.add(Calendar.MONTH, i)
                    val result = repository.refreshPrayerTimes(
                        year = fetchCal.get(Calendar.YEAR),
                        month = fetchCal.get(Calendar.MONTH) + 1,
                        latitude = settings.latitude,
                        longitude = settings.longitude,
                        method = settings.calculationMethod
                    )
                    if (!result.isSuccess) allSuccess = false
                }
                
                if (allSuccess) {
                    scheduleAlarms()
                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                scheduleAlarms()
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("PrayerUpdateWorker", "Error updating prayer times", e)
            Result.failure()
        }
    }

    private suspend fun scheduleAlarms() {
        val settings = settingsManager.settingsFlow.first()
        val prayerDays = repository.getPrayerDays().first()
        alarmScheduler.scheduleNextAlarm(
            prayerDays = prayerDays,
            enablePreAdhan = settings.enablePreAdhanWarning,
            preAdhanMinutes = settings.preAdhanWarningMinutes
        )
    }
}
