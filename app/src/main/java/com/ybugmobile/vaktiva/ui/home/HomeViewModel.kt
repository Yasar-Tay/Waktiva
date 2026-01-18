package com.ybugmobile.vaktiva.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.location.LocationWrapper
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prayerRepository: PrayerRepository,
    private val settingsManager: SettingsManager,
    private val locationWrapper: LocationWrapper
) : ViewModel() {

    val settings = settingsManager.settingsFlow

    val currentPrayerDay: Flow<PrayerDayEntity?> = prayerRepository.getPrayerDays()
        .map { days ->
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            days.find { it.date == today }
        }

    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        // Update current time every second
        tickerFlow(1000).onEach {
            _currentTime.value = LocalDateTime.now()
        }.launchIn(viewModelScope)
    }

    private fun tickerFlow(periodMillis: Long) = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }

    // Parse times only when the day changes, not every second
    private val prayerTimes = currentPrayerDay.map { day ->
        if (day == null) return@map null
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        
        fun parseTime(timeStr: String): LocalTime {
            val cleaned = timeStr.split(" ")[0]
            return LocalTime.parse(cleaned, formatter)
        }

        listOf(
            "Fajr" to parseTime(day.fajr),
            "Sunrise" to parseTime(day.sunrise),
            "Dhuhr" to parseTime(day.dhuhr),
            "Asr" to parseTime(day.asr),
            "Maghrib" to parseTime(day.maghrib),
            "Isha" to parseTime(day.isha)
        )
    }

    val nextPrayerInfo: Flow<NextPrayerInfo?> = combine(prayerTimes, currentTime) { prayers, now ->
        if (prayers == null) return@combine null
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val currentTime = now.toLocalTime()
        val next = prayers.firstOrNull { it.second.isAfter(currentTime) }
            ?: prayers.first().let { it.first to it.second } // Fallback to Fajr (next day, but simplified for now)

        val remainingMillis = if (next.second.isAfter(currentTime)) {
            ChronoUnit.MILLIS.between(currentTime, next.second)
        } else {
            // It's after Isha, so next is Fajr tomorrow
            ChronoUnit.MILLIS.between(currentTime, LocalTime.MAX) + ChronoUnit.MILLIS.between(LocalTime.MIN, next.second)
        }

        NextPrayerInfo(
            name = next.first,
            time = next.second.format(formatter),
            remainingMillis = remainingMillis
        )
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            onPermissionsGranted()
            // Add a small delay for better UI feedback if it's too fast
            delay(500)
            _isRefreshing.value = false
        }
    }

    fun onPermissionsGranted() {
        viewModelScope.launch {
            val location = locationWrapper.getCurrentLocation()
            val currentSettings = settings.first()
            val now = LocalDate.now()
            
            if (location != null) {
                // Reverse geocode to get city, country
                val addressName = locationWrapper.getAddressFromLocation(location.latitude, location.longitude)
                    ?: "Current Location"

                // Save the new location
                settingsManager.saveLocation(
                    lat = location.latitude,
                    lng = location.longitude,
                    name = addressName
                )
                
                prayerRepository.refreshPrayerTimes(
                    year = now.year,
                    month = now.monthValue,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    method = currentSettings.calculationMethod
                )
            } else {
                // If location is still null, we might want to use cached settings or show error
                prayerRepository.refreshPrayerTimes(
                    year = now.year,
                    month = now.monthValue,
                    latitude = currentSettings.latitude,
                    longitude = currentSettings.longitude,
                    method = currentSettings.calculationMethod
                )
            }
        }
    }
}

data class NextPrayerInfo(
    val name: String,
    val time: String,
    val remainingMillis: Long
)
