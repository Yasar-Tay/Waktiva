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

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    val allPrayerDays: Flow<List<PrayerDayEntity>> = prayerRepository.getPrayerDays()

    val currentPrayerDay: Flow<PrayerDayEntity?> = combine(allPrayerDays, selectedDate) { days, date ->
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        days.find { it.date == dateStr }
    }

    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val calculationMethods = listOf(
        "Muslim World League" to 3,
        "Islamic Society of North America (ISNA)" to 2,
        "Egyptian General Authority of Survey" to 5,
        "Umm al-Qura University, Makkah" to 4,
        "University of Islamic Sciences, Karachi" to 1,
        "Institute of Geophysics, University of Tehran" to 7,
        "Gulf Region" to 8,
        "Kuwait" to 9,
        "Qatar" to 10,
        "Majlis Ugama Islam Singapura, Singapore" to 11,
        "Turkey" to 13
    )

    init {
        // Update current time every second
        tickerFlow(1000).onEach {
            _currentTime.value = LocalDateTime.now()
        }.launchIn(viewModelScope)

        // Automatically fetch location and data on launch
        onPermissionsGranted()
    }

    private fun tickerFlow(periodMillis: Long) = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }

    val todayPrayerDay: Flow<PrayerDayEntity?> = allPrayerDays.map { days ->
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        days.find { it.date == today }
    }

    private val todayPrayerTimes = todayPrayerDay.map { day ->
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

    val nextPrayerInfo: Flow<NextPrayerInfo?> = combine(todayPrayerTimes, currentTime) { prayers, now ->
        if (prayers == null) return@combine null
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val currentTime = now.toLocalTime()
        val next = prayers.firstOrNull { it.second.isAfter(currentTime) }
            ?: prayers.first().let { it.first to it.second }

        val remainingMillis = if (next.second.isAfter(currentTime)) {
            ChronoUnit.MILLIS.between(currentTime, next.second)
        } else {
            ChronoUnit.MILLIS.between(currentTime, LocalTime.MAX) + ChronoUnit.MILLIS.between(LocalTime.MIN, next.second)
        }

        NextPrayerInfo(
            name = next.first,
            time = next.second.format(formatter),
            remainingMillis = remainingMillis
        )
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            onPermissionsGranted()
            delay(500)
            _isRefreshing.value = false
        }
    }

    fun updateCalculationMethod(methodId: Int) {
        viewModelScope.launch {
            settingsManager.updateCalculationMethod(methodId)
            refresh()
        }
    }

    fun onPermissionsGranted() {
        viewModelScope.launch {
            val location = locationWrapper.getCurrentLocation()
            val currentSettings = settings.first()
            val now = LocalDate.now()
            
            if (location != null) {
                val addressName = locationWrapper.getAddressFromLocation(location.latitude, location.longitude)
                    ?: "Current Location"

                settingsManager.saveLocation(
                    lat = location.latitude,
                    lng = location.longitude,
                    name = addressName
                )
                
                // Fetch current and next month to ensure 14+ days of data
                prayerRepository.refreshPrayerTimes(
                    year = now.year,
                    month = now.monthValue,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    method = currentSettings.calculationMethod
                )
                
                // If late in the month, fetch next month too
                if (now.dayOfMonth > 20) {
                    val nextMonth = now.plusMonths(1)
                    prayerRepository.refreshPrayerTimes(
                        year = nextMonth.year,
                        month = nextMonth.monthValue,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        method = currentSettings.calculationMethod
                    )
                }
            } else {
                // Fallback using stored settings if GPS is unavailable
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
