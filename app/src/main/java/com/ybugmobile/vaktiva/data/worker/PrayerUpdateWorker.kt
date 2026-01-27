package com.ybugmobile.vaktiva.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
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
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()
            val currentDate = sdf.format(calendar.time)
            
            val remainingDays = repository.getRemainingDaysCount(currentDate)
            Log.d("PrayerUpdateWorker", "Remaining days in cache: $remainingDays")
            
            // If less than 15 days of data remaining, proactively fetch to maintain a 30-day buffer
            if (remainingDays < 15) {
                Log.d("PrayerUpdateWorker", "Proactively fetching prayer times for current and next month")
                val settings = settingsManager.settingsFlow.first()
                
                // Fetch current month
                val resultCurrent = repository.refreshPrayerTimes(
                    year = calendar.get(Calendar.YEAR),
                    month = calendar.get(Calendar.MONTH) + 1,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    method = settings.calculationMethod
                )
                
                // Fetch next month
                val nextMonthCal = calendar.clone() as Calendar
                nextMonthCal.add(Calendar.MONTH, 1)
                val resultNext = repository.refreshPrayerTimes(
                    year = nextMonthCal.get(Calendar.YEAR),
                    month = nextMonthCal.get(Calendar.MONTH) + 1,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    method = settings.calculationMethod
                )
                
                if (resultCurrent.isSuccess && resultNext.isSuccess) {
                    scheduleAlarms()
                    Result.success()
                } else {
                    // If one fails, we retry later
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
