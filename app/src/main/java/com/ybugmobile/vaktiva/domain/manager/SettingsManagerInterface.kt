package com.ybugmobile.vaktiva.domain.manager

import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.domain.model.PrayerType
import kotlinx.coroutines.flow.Flow

interface SettingsManagerInterface {
    val settingsFlow: Flow<UserSettings>
    suspend fun muteNextPrayer(prayerName: String, date: String)
    suspend fun clearMutedPrayer()
    suspend fun updateMadhab(madhab: Int)
    suspend fun updateCalculationMethod(method: Int)
    suspend fun updateLanguage(language: String)
    suspend fun updateSelectedAdhanPath(path: String?)
    suspend fun updatePrayerSpecificAdhanPath(type: PrayerType, path: String?)
    suspend fun updateUseSpecificAdhan(enabled: Boolean)
    suspend fun updatePlayAdhanAudio(enabled: Boolean)
    suspend fun updatePreAdhanWarning(enabled: Boolean)
    suspend fun updatePreAdhanWarningMinutes(minutes: Int)
    suspend fun setSetupComplete(complete: Boolean)
    suspend fun setCalculationMethod(method: Int)
    suspend fun saveLocation(lat: Double, lng: Double, name: String)
    suspend fun saveLocation(lat: Double, lng: Double, alt: Double, name: String)
    suspend fun setTestAlarmEndTime(timeMillis: Long?)
    suspend fun updateCalendarType(isHijri: Boolean)
    suspend fun updateShowWeatherEffects(enabled: Boolean)
    suspend fun updateShowPersistentNotification(enabled: Boolean)
}
