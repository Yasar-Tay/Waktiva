package com.ybugmobile.vaktiva.data.local.dao

import androidx.room.*
import com.ybugmobile.vaktiva.data.local.entity.PrayerStatusEntity
import com.ybugmobile.vaktiva.domain.model.PrayerType
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerStatusDao {
    @Query("SELECT * FROM prayer_status WHERE date = :date")
    fun getStatusesForDate(date: String): Flow<List<PrayerStatusEntity>>

    @Query("SELECT * FROM prayer_status WHERE date = :date AND prayerType = :prayerType LIMIT 1")
    suspend fun getStatus(date: String, prayerType: PrayerType): PrayerStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateStatus(status: PrayerStatusEntity)

    @Query("SELECT * FROM prayer_status ORDER BY date DESC")
    fun getAllStatuses(): Flow<List<PrayerStatusEntity>>

    @Query("DELETE FROM prayer_status WHERE date < :currentDate")
    suspend fun deletePastStatuses(currentDate: String)
}
