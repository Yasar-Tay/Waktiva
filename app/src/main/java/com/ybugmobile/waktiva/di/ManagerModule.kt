package com.ybugmobile.waktiva.di

import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.data.manager.BillingManagerImpl
import com.ybugmobile.waktiva.domain.manager.BillingManager
import com.ybugmobile.waktiva.domain.manager.SettingsManagerInterface
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManagerModule {

    @Binds
    @Singleton
    abstract fun bindSettingsManager(
        settingsManager: SettingsManager
    ): SettingsManagerInterface

    @Binds
    @Singleton
    abstract fun bindBillingManager(
        billingManager: BillingManagerImpl
    ): BillingManager
}
