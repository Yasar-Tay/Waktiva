package com.ybugmobile.waktiva.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ybugmobile.waktiva.data.local.converters.PrayerTypeConverter
import com.ybugmobile.waktiva.data.local.dao.MosqueDao
import com.ybugmobile.waktiva.data.local.dao.PrayerDao
import com.ybugmobile.waktiva.data.local.dao.PrayerStatusDao
import com.ybugmobile.waktiva.data.local.entity.MosqueEntity
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.waktiva.data.local.entity.PrayerStatusEntity

@Database(
    entities = [
        PrayerDayEntity::class,
        PrayerStatusEntity::class,
        MosqueEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(PrayerTypeConverter::class)
abstract class WaktivaDatabase : RoomDatabase() {
    abstract fun prayerDao(): PrayerDao
    abstract fun prayerStatusDao(): PrayerStatusDao
    abstract fun mosqueDao(): MosqueDao

    companion object {
        const val DATABASE_NAME = "waktiva_db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS mosques (
                        id INTEGER NOT NULL PRIMARY KEY,
                        name TEXT,
                        lat REAL NOT NULL,
                        lng REAL NOT NULL,
                        anchorLat REAL NOT NULL,
                        anchorLng REAL NOT NULL,
                        fetchedAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE mosques ADD COLUMN address TEXT")
            }
        }
    }
}
