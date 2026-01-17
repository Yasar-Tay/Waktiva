package com.ybugmobile.vaktiva.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prayerRepository: PrayerRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val settings = settingsManager.settingsFlow

    val currentPrayerDay: Flow<PrayerDayEntity?> = prayerRepository.getPrayerDays()
        .map { days ->
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            days.find { it.date == today }
        }

    private val _currentTime = MutableStateFlow(LocalDateTime.now())
    val currentTime = _currentTime.asStateFlow()

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

    val nextPrayerInfo: Flow<NextPrayerInfo?> = combine(currentPrayerDay, currentTime) { day, now ->
        if (day == null) return@combine null

        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val prayers = listOf(
            "Fajr" to LocalTime.parse(day.fajr, formatter),
            "Sunrise" to LocalTime.parse(day.sunrise, formatter),
            "Dhuhr" to LocalTime.parse(day.dhuhr, formatter),
            "Asr" to LocalTime.parse(day.asr, formatter),
            "Maghrib" to LocalTime.parse(day.maghrib, formatter),
            "Isha" to LocalTime.parse(day.isha, formatter)
        )

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
}

data class NextPrayerInfo(
    val name: String,
    val time: String,
    val remainingMillis: Long
)
