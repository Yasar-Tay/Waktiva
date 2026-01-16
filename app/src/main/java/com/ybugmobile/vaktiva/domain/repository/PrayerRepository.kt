package com.ybugmobile.vaktiva.domain.repository

import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import kotlinx.coroutines.flow.Flow

interface PrayerRepository {
    fun getPrayerDays(): Flow<List<PrayerDayEntity>>
    
    suspend fun refreshPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        method: Int
    ): Result<Unit>
    
    suspend fun getRemainingDaysCount(currentDate: String): Int
}
