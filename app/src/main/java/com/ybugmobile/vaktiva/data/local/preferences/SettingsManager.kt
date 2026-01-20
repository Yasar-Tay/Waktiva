package com.ybugmobile.vaktiva.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val selectedAdhanPath: String?
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
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            madhab = preferences[MADHAB] ?: 0, // 0: Shafi, 1: Hanafi
            calculationMethod = preferences[CALCULATION_METHOD] ?: 2, // Default: ISNA
            latitude = preferences[LAST_KNOWN_LAT] ?: 0.0,
            longitude = preferences[LAST_KNOWN_LNG] ?: 0.0,
            locationName = preferences[LAST_LOCATION_NAME] ?: "Unknown",
            language = preferences[LANGUAGE] ?: "system",
            selectedAdhanPath = preferences[SELECTED_ADHAN_PATH]
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

    suspend fun setCalculationMethod(method: Int) = updateCalculationMethod(method)

    suspend fun saveLocation(lat: Double, lng: Double, name: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_KNOWN_LAT] = lat
            preferences[LAST_KNOWN_LNG] = lng
            preferences[LAST_LOCATION_NAME] = name
        }
    }
}
