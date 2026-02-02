package com.ybugmobile.vaktiva.di

import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.manager.BillingManagerImpl
import com.ybugmobile.vaktiva.domain.manager.BillingManager
import com.ybugmobile.vaktiva.domain.manager.SettingsManagerInterface
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
