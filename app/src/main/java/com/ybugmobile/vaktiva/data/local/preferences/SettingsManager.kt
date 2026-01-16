package com.ybugmobile.vaktiva.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.int64PreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val MADHAB = stringPreferencesKey("madhab")
        val CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        val LAST_KNOWN_LAT = stringPreferencesKey("last_lat")
        val LAST_KNOWN_LNG = stringPreferencesKey("last_lng")
        val LAST_LOCATION_NAME = stringPreferencesKey("last_location_name")
    }

    val madhab: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MADHAB] ?: "SHAFI" // Default
    }

    val calculationMethod: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CALCULATION_METHOD] ?: "MUSLIM_WORLD_LEAGUE" // Default
    }

    suspend fun updateMadhab(madhab: String) {
        context.dataStore.edit { preferences ->
            preferences[MADHAB] = madhab
        }
    }

    suspend fun updateCalculationMethod(method: String) {
        context.dataStore.edit { preferences ->
            preferences[CALCULATION_METHOD] = method
        }
    }

    suspend fun saveLocation(lat: Double, lng: Double, name: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_KNOWN_LAT] = lat.toString()
            preferences[LAST_KNOWN_LNG] = lng.toString()
            preferences[LAST_LOCATION_NAME] = name
        }
    }
}
