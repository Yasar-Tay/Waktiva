package com.ybugmobile.waktiva.ui.home

import com.ybugmobile.waktiva.domain.model.CurrentPrayer
import com.ybugmobile.waktiva.domain.model.HijriData
import com.ybugmobile.waktiva.domain.model.MoonPhase
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeViewState(
    val selectedDate: LocalDate = LocalDate.now(),
    val currentTime: LocalDateTime = LocalDateTime.now(),
    val currentPrayerDay: PrayerDay? = null,
    val currentPrayer: CurrentPrayer? = null,
    val nextPrayer: NextPrayer? = null,
    val moonPhase: MoonPhase? = null,
    val effectiveHijriDate: HijriData? = null,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true, // Initial state is loading
    val locationName: String = "",
    val isAdhanPlaying: Boolean = false,
    val playingPrayerName: String? = null,
    val isMuted: Boolean = false,
    val isHijriSelected: Boolean = false,
    val error: String? = null,
    val isNetworkAvailable: Boolean = true,
    val isLocationEnabled: Boolean = true,
    val isLocationPermissionGranted: Boolean = true,
    val hasSystemIssues: Boolean = false,
    
    // Weather state
    val weatherCondition: WeatherCondition = WeatherCondition.UNKNOWN,
    val temperature: Double? = null,
    
    // Solar and Compass data for atmospheric effects
    val sunAzimuth: Float = 0f,
    val sunAltitude: Float = 0f,
    val compassAzimuth: Float = 0f
)
