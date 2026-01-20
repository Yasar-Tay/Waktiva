package com.ybugmobile.vaktiva.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ybugmobile.vaktiva.domain.model.PrayerType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UserSettings(
    val madhab: Int,
    val calculationMethod: Int,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val language: String,
    val selectedAdhanPath: String?,
    val prayerSpecificAdhanPaths: Map<PrayerType, String?>,
    val useSpecificAdhanForEachPrayer: Boolean,
    val playAdhanAudio: Boolean,
    val isSetupComplete: Boolean,
    val enablePreAdhanWarning: Boolean,
    val preAdhanWarningMinutes: Int
)

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val MADHAB = intPreferencesKey("madhab")
        val CALCULATION_METHOD = intPreferencesKey("calculation_method")
        val LAST_KNOWN_LAT = doublePreferencesKey("last_lat")
        val LAST_KNOWN_LNG = doublePreferencesKey("last_lng")
        val LAST_LOCATION_NAME = stringPreferencesKey("last_location_name")
        val LANGUAGE = stringPreferencesKey("language")
        val SELECTED_ADHAN_PATH = stringPreferencesKey("selected_adhan_path")
        val USE_SPECIFIC_ADHAN = booleanPreferencesKey("use_specific_adhan")
        val PLAY_ADHAN_AUDIO = booleanPreferencesKey("play_adhan_audio")
        val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        val ENABLE_PRE_ADHAN_WARNING = booleanPreferencesKey("enable_pre_adhan_warning")
        val PRE_ADHAN_WARNING_MINUTES = intPreferencesKey("pre_adhan_warning_minutes")

        private fun prayerPathKey(type: PrayerType) = stringPreferencesKey("adhan_path_${type.name}")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val prayerSpecificPaths = PrayerType.entries.associateWith { type ->
            preferences[prayerPathKey(type)]
        }

        UserSettings(
            madhab = preferences[MADHAB] ?: 0, // 0: Shafi, 1: Hanafi
            calculationMethod = preferences[CALCULATION_METHOD] ?: 2, // Default: ISNA
            latitude = preferences[LAST_KNOWN_LAT] ?: 0.0,
            longitude = preferences[LAST_KNOWN_LNG] ?: 0.0,
            locationName = preferences[LAST_LOCATION_NAME] ?: "Unknown",
            language = preferences[LANGUAGE] ?: "system",
            selectedAdhanPath = preferences[SELECTED_ADHAN_PATH],
            prayerSpecificAdhanPaths = prayerSpecificPaths,
            useSpecificAdhanForEachPrayer = preferences[USE_SPECIFIC_ADHAN] ?: false,
            playAdhanAudio = preferences[PLAY_ADHAN_AUDIO] ?: true,
            isSetupComplete = preferences[IS_SETUP_COMPLETE] ?: false,
            enablePreAdhanWarning = preferences[ENABLE_PRE_ADHAN_WARNING] ?: true,
            preAdhanWarningMinutes = preferences[PRE_ADHAN_WARNING_MINUTES] ?: 5
        )
    }

    suspend fun updateMadhab(madhab: Int) {
        context.dataStore.edit { preferences ->
            preferences[MADHAB] = madhab
        }
    }

    suspend fun updateCalculationMethod(method: Int) {
        context.dataStore.edit { preferences ->
            preferences[CALCULATION_METHOD] = method
        }
    }

    suspend fun updateLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE] = language
        }
    }

    suspend fun updateSelectedAdhanPath(path: String?) {
        context.dataStore.edit { preferences ->
            if (path == null) {
                preferences.remove(SELECTED_ADHAN_PATH)
            } else {
                preferences[SELECTED_ADHAN_PATH] = path
            }
        }
    }

    suspend fun updatePrayerSpecificAdhanPath(type: PrayerType, path: String?) {
        context.dataStore.edit { preferences ->
            val key = prayerPathKey(type)
            if (path == null) {
                preferences.remove(key)
            } else {
                preferences[key] = path
            }
        }
    }

    suspend fun updateUseSpecificAdhan(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_SPECIFIC_ADHAN] = enabled
        }
    }

    suspend fun updatePlayAdhanAudio(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PLAY_ADHAN_AUDIO] = enabled
        }
    }

    suspend fun updatePreAdhanWarning(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_PRE_ADHAN_WARNING] = enabled
        }
    }

    suspend fun updatePreAdhanWarningMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PRE_ADHAN_WARNING_MINUTES] = minutes
        }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_SETUP_COMPLETE] = complete
        }
    }

    suspend fun setCalculationMethod(method: Int) = updateCalculationMethod(method)

    suspend fun saveLocation(lat: Double, lng: Double, name: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_KNOWN_LAT] = lat
            preferences[LAST_KNOWN_LNG] = lng
            preferences[LAST_LOCATION_NAME] = name
        }
    }
}
