package com.ybugmobile.vaktiva.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.location.LocationWrapper
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

@HiltWorker
class LocationUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationWrapper: LocationWrapper,
    private val repository: PrayerRepository,
    private val settingsManager: SettingsManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val currentLocation = locationWrapper.getCurrentLocation() ?: return Result.failure()
            val settings = settingsManager.settingsFlow.first()

            val savedLat = settings.latitude
            val savedLng = settings.longitude

            val shouldUpdate = if (savedLat == null || savedLng == null) {
                true
            } else {
                val distance = locationWrapper.calculateDistance(
                    savedLat,
                    savedLng,
                    currentLocation.latitude,
                    currentLocation.longitude
                )
                distance > 50.0
            }

            // If user moved more than 50km or no previous location, refresh data
            if (shouldUpdate) {
                val calendar = Calendar.getInstance()
                
                val result = repository.refreshPrayerTimes(
                    year = calendar.get(Calendar.YEAR),
                    month = calendar.get(Calendar.MONTH) + 1,
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    method = settings.calculationMethod
                )

                if (result.isSuccess) {
                    settingsManager.saveLocation(
                        lat = currentLocation.latitude,
                        lng = currentLocation.longitude,
                        alt = currentLocation.altitude,
                        name = "Updated Location" // We'll add Geocoding later
                    )
                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
