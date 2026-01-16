package com.ybugmobile.vaktiva.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer_days ORDER BY date ASC")
    fun getAllPrayerDays(): Flow<List<PrayerDayEntity>>

    @Query("SELECT * FROM prayer_days WHERE date = :date LIMIT 1")
    suspend fun getPrayerDayByDate(date: String): PrayerDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrayerDays(prayerDays: List<PrayerDayEntity>)

    @Query("DELETE FROM prayer_days WHERE date < :currentDate")
    suspend fun deletePastDays(currentDate: String)

    @Query("SELECT COUNT(*) FROM prayer_days WHERE date >= :currentDate")
    suspend fun getFutureDaysCount(currentDate: String): Int
}
