package com.ybugmobile.vaktiva.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ybugmobile.vaktiva.data.local.dao.PrayerDao
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity

@Database(
    entities = [PrayerDayEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaktivaDatabase : RoomDatabase() {
    abstract fun prayerDao(): PrayerDao

    companion object {
        const val DATABASE_NAME = "vaktiva_db"
    }
}
