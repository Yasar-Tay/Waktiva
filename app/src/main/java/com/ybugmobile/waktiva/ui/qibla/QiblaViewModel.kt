package com.ybugmobile.waktiva.ui.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batoulapps.adhan.Qibla
import com.batoulapps.adhan.Coordinates
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.data.sensor.CompassData
import com.ybugmobile.waktiva.data.sensor.CompassManager
import com.ybugmobile.waktiva.domain.model.MosqueLocation
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.repository.MosqueRepository
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.manager.TimeManager
import com.ybugmobile.waktiva.utils.PermissionUtils
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
    private val mosqueRepository: MosqueRepository,
    timeManager: TimeManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val settings = settingsManager.settingsFlow

    val compassData: Flow<CompassData> = compassManager.compassFlow

    val currentTime = timeManager.currentTime

    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPrayerDay: Flow<PrayerDay?> = allPrayerDays.map { days ->
        val today = LocalDate.now()
        days.find { it.date == today }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _mosques = MutableStateFlow<List<MosqueLocation>>(emptyList())

    private val _isNetworkAvailable = MutableStateFlow(PermissionUtils.isNetworkAvailable(context))
    private val _isLocationEnabled = MutableStateFlow(PermissionUtils.isLocationEnabled(context))
    private val _isLocationPermissionGranted = MutableStateFlow(PermissionUtils.isLocationPermissionGranted(context))
    private val _hasSystemIssues = MutableStateFlow(checkSystemIssues())
    private val _hasSettled = MutableStateFlow(false)

    // Grouping status flows
    private val statusFlow = combine(
        _hasSettled,
        _isNetworkAvailable,
        _isLocationEnabled,
        _isLocationPermissionGranted,
        _hasSystemIssues
    ) { settled, network, locEnabled, locPerm, issues ->
        StateQuint(settled, network, locEnabled, locPerm, issues)
    }

    private val coreStateFlow = combine(
        settings,
        compassData,
        currentPrayerDay,
        currentTime,
        statusFlow
    ) { s, c, d, t, status ->
        val (settled, network, locEnabled, locPerm, issues) = status
        val qiblaDir = if (s.latitude != null && s.longitude != null) {
            Qibla(Coordinates(s.latitude, s.longitude)).direction
        } else 0.0

        QiblaViewState(
            settings = s,
            qiblaDirection = qiblaDir,
            compassData = c,
            currentPrayerDay = d,
            currentTime = t,
            isLoading = !settled && d == null,
            isNetworkAvailable = network,
            isLocationEnabled = locEnabled,
            isLocationPermissionGranted = locPerm,
            hasSystemIssues = issues
        )
    }

    val state: StateFlow<QiblaViewState> = combine(coreStateFlow, _mosques) { core, mosques ->
        core.copy(mosques = mosques)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QiblaViewState(isLoading = true))

    init {
        // Update health status periodically
        currentTime.onEach { now ->
            if (now.second % 30 == 0) {
                updateHealthStatus()
            }
        }.launchIn(viewModelScope)

        // Update magnetic declination whenever location changes
        viewModelScope.launch {
            settings.collectLatest { s ->
                if (s.latitude != null && s.longitude != null) {
                    val geomagneticField = GeomagneticField(
                        s.latitude.toFloat(),
                        s.longitude.toFloat(),
                        s.altitude?.toFloat() ?: 0f,
                        System.currentTimeMillis()
                    )
                    compassManager.setDeclination(geomagneticField.declination)
                }
            }
        }

        // Logic to mark as settled:
        // Instead of a fixed delay, we wait for the first emissions of critical data
        viewModelScope.launch {
            combine(settings, currentPrayerDay) { s, d -> 
                s.latitude != null && d != null 
            }.filter { it }
             .first()
            
            _hasSettled.value = true
        }
        
        // Fallback for settled state if no data is found after a longer period (e.g. 2 seconds)
        viewModelScope.launch {
            delay(2000)
            _hasSettled.value = true
        }

        // Fetch nearby mosques whenever location changes
        viewModelScope.launch {
            settings.collectLatest { s ->
                val lat = s.latitude ?: return@collectLatest
                val lng = s.longitude ?: return@collectLatest
                runCatching { mosqueRepository.getNearbyMosques(lat, lng) }
                    .onSuccess {
                        Log.d("QiblaVM", "Mosques loaded: ${it.size}")
                        _mosques.value = it
                    }
                    .onFailure { Log.e("QiblaVM", "Mosque fetch failed", it) }
            }
        }
    }

    private fun updateHealthStatus() {
        _isNetworkAvailable.value = PermissionUtils.isNetworkAvailable(context)
        _isLocationEnabled.value = PermissionUtils.isLocationEnabled(context)
        _isLocationPermissionGranted.value = PermissionUtils.isLocationPermissionGranted(context)
        _hasSystemIssues.value = checkSystemIssues()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            updateHealthStatus()

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
                runCatching { mosqueRepository.getNearbyMosques(s.latitude, s.longitude) }
                    .onSuccess {
                        Log.d("QiblaVM", "Mosques refreshed: ${it.size}")
                        _mosques.value = it
                    }
                    .onFailure { Log.e("QiblaVM", "Mosque refresh failed", it) }
            }

            delay(500)
            _isRefreshing.value = false
            _hasSettled.value = true
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

data class StateQuint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
