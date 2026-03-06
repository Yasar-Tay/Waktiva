package com.ybugmobile.waktiva.di

import android.content.Context
import androidx.room.Room
import com.ybugmobile.waktiva.data.local.WaktivaDatabase
import com.ybugmobile.waktiva.data.local.dao.PrayerDao
import com.ybugmobile.waktiva.data.local.dao.PrayerStatusDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWaktivaDatabase(
        @ApplicationContext context: Context
    ): WaktivaDatabase {
        return Room.databaseBuilder(
            context,
            WaktivaDatabase::class.java,
            WaktivaDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun providePrayerDao(database: WaktivaDatabase): PrayerDao {
        return database.prayerDao()
    }

    @Provides
    @Singleton
    fun providePrayerStatusDao(database: WaktivaDatabase): PrayerStatusDao {
        return database.prayerStatusDao()
    }
}
