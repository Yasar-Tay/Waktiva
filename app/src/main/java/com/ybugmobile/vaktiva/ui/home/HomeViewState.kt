package com.ybugmobile.vaktiva.ui.home

import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeViewState(
    val selectedDate: LocalDate = LocalDate.now(),
    val currentTime: LocalDateTime = LocalDateTime.now(),
    val currentPrayerDay: PrayerDay? = null,
    val nextPrayer: NextPrayer? = null,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true, // Initial state is loading
    val locationName: String = "",
    val isAdhanPlaying: Boolean = false,
    val playingPrayerName: String? = null,
    val error: String? = null
)
