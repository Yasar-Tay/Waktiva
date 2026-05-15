package com.ybugmobile.waktiva.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
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

    @Query("SELECT COUNT(*) FROM prayer_days WHERE date LIKE :yearMonth || '%'")
    suspend fun getCountForYearMonth(yearMonth: String): Int

    /** Deletes all prayer days for a given year-month (e.g. "2026-05"). */
    @Query("DELETE FROM prayer_days WHERE date LIKE :yearMonth || '%'")
    suspend fun deletePrayerDaysForYearMonth(yearMonth: String)

    /** One-shot (non-Flow) snapshot of all stored prayer days. */
    @Query("SELECT * FROM prayer_days ORDER BY date ASC")
    suspend fun getAllPrayerDaysOnce(): List<PrayerDayEntity>

    /** Updates only the six time columns; leaves date, hijriDate and all other fields untouched. */
    @Query(
        "UPDATE prayer_days SET fajr=:fajr, sunrise=:sunrise, dhuhr=:dhuhr, " +
        "asr=:asr, maghrib=:maghrib, isha=:isha WHERE date=:date"
    )
    suspend fun updateTimings(
        date: String,
        fajr: String,
        sunrise: String,
        dhuhr: String,
        asr: String,
        maghrib: String,
        isha: String
    )
}
