package com.ybugmobile.waktiva.data.local.dao

import androidx.room.*
import com.ybugmobile.waktiva.data.local.entity.PrayerStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerStatusDao {
    /** Every prayer marked as prayed, oldest first. */
    @Query("SELECT * FROM prayer_status WHERE isDone = 1 ORDER BY date ASC")
    fun getDoneStatuses(): Flow<List<PrayerStatusEntity>>

    @Query("SELECT * FROM prayer_status WHERE date = :date AND isDone = 1")
    fun getDoneStatusesForDate(date: String): Flow<List<PrayerStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateStatus(status: PrayerStatusEntity)
}
