package com.ybugmobile.vaktiva.ui.qibla

import android.content.Context
import android.hardware.GeomagneticField
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoulapps.adhan.Qibla
import com.batoulapps.adhan.Coordinates
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.sensor.CompassData
import com.ybugmobile.vaktiva.data.sensor.CompassManager
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.domain.manager.TimeManager
import com.ybugmobile.vaktiva.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class QiblaViewModel @Inject constructor(
    settingsManager: SettingsManager,
    private val compassManager: CompassManager,
    private val prayerRepository: PrayerRepository,
    timeManager: TimeManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val settings = settingsManager.settingsFlow

    val qiblaDirection: Flow<Double> = settings.map { s ->
        if (s.latitude != null && s.longitude != null) {
            val coordinates = Coordinates(s.latitude, s.longitude)
            Qibla(coordinates).direction
        } else 0.0
    }

    val compassData: Flow<CompassData> = compassManager.compassFlow

    val currentTime = timeManager.currentTime

    val currentPrayerDay: Flow<PrayerDay?> = prayerRepository.getPrayerDays().map { days ->
        val today = LocalDate.now()
        days.find { it.date == today }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isNetworkAvailable = MutableStateFlow(PermissionUtils.isNetworkAvailable(context))
    private val _hasSystemIssues = MutableStateFlow(checkSystemIssues())
    private val _hasSettled = MutableStateFlow(false)

    // Grouping status flows to stay within the 5-parameter limit of combine
    private val statusFlow = combine(
        _hasSettled,
        _isNetworkAvailable,
        _hasSystemIssues
    ) { settled, network, issues ->
        Triple(settled, network, issues)
    }

    val state: StateFlow<QiblaViewState> = combine(
        settings,
        compassData,
        currentPrayerDay,
        currentTime,
        statusFlow
    ) { s, c, d, t, status ->
        val (settled, network, issues) = status
        val qiblaDir = if (s.latitude != null && s.longitude != null) {
            Qibla(Coordinates(s.latitude, s.longitude)).direction
        } else 0.0

        QiblaViewState(
            settings = s,
            qiblaDirection = qiblaDir,
            compassData = c,
            currentPrayerDay = d,
            currentTime = t,
            isLoading = !settled,
            isNetworkAvailable = network,
            hasSystemIssues = issues
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QiblaViewState(isLoading = true))

    init {
        // Update health status periodically
        currentTime.onEach { now ->
            if (now.second % 30 == 0) {
                _isNetworkAvailable.value = PermissionUtils.isNetworkAvailable(context)
                _hasSystemIssues.value = checkSystemIssues()
            }
        }.launchIn(viewModelScope)

        // Update magnetic declination whenever location changes
        viewModelScope.launch {
            settings.collectLatest { s ->
                if (s.latitude != null && s.longitude != null) {
                    val geomagneticField = GeomagneticField(
                        s.latitude.toFloat(),
                        s.longitude.toFloat(),
                        0f, // Altitude - approximate as 0 for declination
                        System.currentTimeMillis()
                    )
                    compassManager.setDeclination(geomagneticField.declination)
                }
            }
        }

        // Mark as settled after a small delay to allow flows to emit their first values
        viewModelScope.launch {
            delay(300) // Small buffer to let flows settle
            _hasSettled.value = true
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _isNetworkAvailable.value = PermissionUtils.isNetworkAvailable(context)
            _hasSystemIssues.value = checkSystemIssues()
            
            val s = settings.first()
            val now = LocalDate.now()
            if (s.latitude != null && s.longitude != null) {
                prayerRepository.refreshPrayerTimes(
                    year = now.year,
                    month = now.monthValue,
                    latitude = s.latitude,
                    longitude = s.longitude,
                    method = s.calculationMethod
                )
            }

            delay(500)
            _isRefreshing.value = false
        }
    }

    private fun checkSystemIssues(): Boolean {
        return !PermissionUtils.isLocationEnabled(context) || 
               PermissionUtils.isDoNotDisturbActive(context) || 
               PermissionUtils.areNotificationChannelsMuted(context) ||
               !PermissionUtils.isLocationPermissionGranted(context) ||
               !PermissionUtils.isNotificationPermissionGranted(context)
    }
}
