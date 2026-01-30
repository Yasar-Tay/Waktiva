package com.ybugmobile.vaktiva.di

import android.content.Context
import androidx.room.Room
import com.ybugmobile.vaktiva.data.local.VaktivaDatabase
import com.ybugmobile.vaktiva.data.local.dao.PrayerDao
import com.ybugmobile.vaktiva.data.local.dao.PrayerStatusDao
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
    fun provideVaktivaDatabase(
        @ApplicationContext context: Context
    ): VaktivaDatabase {
        return Room.databaseBuilder(
            context,
            VaktivaDatabase::class.java,
            VaktivaDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun providePrayerDao(database: VaktivaDatabase): PrayerDao {
        return database.prayerDao()
    }

    @Provides
    @Singleton
    fun providePrayerStatusDao(database: VaktivaDatabase): PrayerStatusDao {
        return database.prayerStatusDao()
    }
}
