package com.ybugmobile.vaktiva.ui.qibla

import androidx.lifecycle.ViewModel
import com.batoulapps.adhan.Qibla
import com.batoulapps.adhan.Coordinates
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.sensor.CompassData
import com.ybugmobile.vaktiva.data.sensor.CompassManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class QiblaViewModel @Inject constructor(
    settingsManager: SettingsManager,
    compassManager: CompassManager
) : ViewModel() {

    val qiblaDirection: Flow<Double> = settingsManager.settingsFlow.map { settings ->
        val coordinates = Coordinates(settings.latitude, settings.longitude)
        Qibla(coordinates).direction
    }

    val userLocation = settingsManager.settingsFlow.map { settings ->
        Coordinates(settings.latitude, settings.longitude)
    }

    val compassData: Flow<CompassData> = compassManager.compassFlow
}
