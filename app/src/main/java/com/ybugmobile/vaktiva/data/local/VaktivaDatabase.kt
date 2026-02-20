package com.ybugmobile.vaktiva.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ybugmobile.vaktiva.data.local.converters.PrayerTypeConverter
import com.ybugmobile.vaktiva.data.local.dao.PrayerDao
import com.ybugmobile.vaktiva.data.local.dao.PrayerStatusDao
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.data.local.entity.PrayerStatusEntity

@Database(
    entities = [
        PrayerDayEntity::class,
        PrayerStatusEntity::class
    ],
    version = 3, // Incremented version for astro data fields
    exportSchema = false
)
@TypeConverters(PrayerTypeConverter::class)
abstract class VaktivaDatabase : RoomDatabase() {
    abstract fun prayerDao(): PrayerDao
    abstract fun prayerStatusDao(): PrayerStatusDao

    companion object {
        const val DATABASE_NAME = "vaktiva_db"
    }
}
