package com.ybugmobile.vaktiva.data.worker

import android.content.Context
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
            val currentDate = sdf.format(Calendar.getInstance().time)
            
            val remainingDays = repository.getRemainingDaysCount(currentDate)
            
            if (remainingDays < 3) {
                val settings = settingsManager.settingsFlow.first()
                val calendar = Calendar.getInstance()
                
                // Fetch current month and next month to ensure we have enough data
                val resultCurrent = repository.refreshPrayerTimes(
                    year = calendar.get(Calendar.YEAR),
                    month = calendar.get(Calendar.MONTH) + 1,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    method = settings.calculationMethod
                )
                
                // Also fetch next month just in case we are at the end of the month
                calendar.add(Calendar.MONTH, 1)
                val resultNext = repository.refreshPrayerTimes(
                    year = calendar.get(Calendar.YEAR),
                    month = calendar.get(Calendar.MONTH) + 1,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    method = settings.calculationMethod
                )
                
                if (resultCurrent.isSuccess && resultNext.isSuccess) {
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
            Result.failure()
        }
    }

    private suspend fun scheduleAlarms() {
        val prayerDays = repository.getPrayerDays().first()
        alarmScheduler.scheduleUpcomingAlarms(prayerDays)
    }
}
