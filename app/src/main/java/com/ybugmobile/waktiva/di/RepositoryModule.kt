package com.ybugmobile.waktiva.di

import com.ybugmobile.waktiva.data.repository.PrayerRepositoryImpl
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPrayerRepository(
        prayerRepositoryImpl: PrayerRepositoryImpl
    ): PrayerRepository
}
