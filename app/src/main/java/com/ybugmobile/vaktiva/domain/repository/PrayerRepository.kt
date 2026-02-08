package com.ybugmobile.vaktiva.domain.repository

import com.ybugmobile.vaktiva.domain.model.PrayerDay
import kotlinx.coroutines.flow.Flow

interface PrayerRepository {
    fun getPrayerDays(): Flow<List<PrayerDay>>
    
    suspend fun refreshPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double?,
        longitude: Double?,
        method: Int
    ): Result<Unit>
    
    suspend fun getRemainingDaysCount(currentDate: String): Int

    suspend fun deletePastData(currentDate: String)
}
