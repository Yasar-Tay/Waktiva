package com.ybugmobile.vaktiva.ui.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoulapps.adhan.Qibla
import com.batoulapps.adhan.Coordinates
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.sensor.CompassData
import com.ybugmobile.vaktiva.data.sensor.CompassManager
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class QiblaViewModel @Inject constructor(
    settingsManager: SettingsManager,
    compassManager: CompassManager,
    prayerRepository: PrayerRepository
) : ViewModel() {

    val settings = settingsManager.settingsFlow

    val qiblaDirection: Flow<Double> = settings.map { settings ->
        val coordinates = Coordinates(settings.latitude, settings.longitude)
        Qibla(coordinates).direction
    }

    val userLocation = settings.map { settings ->
        Coordinates(settings.latitude, settings.longitude)
    }

    val compassData: Flow<CompassData> = compassManager.compassFlow

    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

    val currentPrayerDay: Flow<PrayerDay?> = prayerRepository.getPrayerDays().map { days ->
        val today = LocalDate.now()
        days.find { it.date == today }
    }

    init {
        flow {
            while (true) {
                emit(LocalDateTime.now())
                delay(1000)
            }
        }.onEach { _currentTime.value = it }
            .launchIn(viewModelScope)
    }
}
