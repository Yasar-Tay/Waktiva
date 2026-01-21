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
import kotlinx.coroutines.launch
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

    val compassData: Flow<CompassData> = compassManager.compassFlow

    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

    val currentPrayerDay: Flow<PrayerDay?> = prayerRepository.getPrayerDays().map { days ->
        val today = LocalDate.now()
        days.find { it.date == today }
    }

    private val _hasSettled = MutableStateFlow(false)

    val state: StateFlow<QiblaViewState> = combine(
        settings,
        compassData,
        currentPrayerDay,
        currentTime,
        _hasSettled
    ) { s, c, d, t, settled ->
        val qiblaDir = Qibla(Coordinates(s.latitude, s.longitude)).direction
        QiblaViewState(
            settings = s,
            qiblaDirection = qiblaDir,
            compassData = c,
            currentPrayerDay = d,
            currentTime = t,
            isLoading = !settled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QiblaViewState(isLoading = true))

    init {
        // Ticker for current time
        flow {
            while (true) {
                emit(LocalDateTime.now())
                delay(1000)
            }
        }.onEach { _currentTime.value = it }
            .launchIn(viewModelScope)

        // Mark as settled after a small delay to allow flows to emit their first values
        viewModelScope.launch {
            delay(300) // Small buffer to let flows settle
            _hasSettled.value = true
        }
    }
}
