package com.ybugmobile.vaktiva.ui.home

import com.ybugmobile.vaktiva.domain.model.CurrentPrayer
import com.ybugmobile.vaktiva.domain.model.HijriData
import com.ybugmobile.vaktiva.domain.model.MoonPhase
import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerDay
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
    val hasSystemIssues: Boolean = false
)
