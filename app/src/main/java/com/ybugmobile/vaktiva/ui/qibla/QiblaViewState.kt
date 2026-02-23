package com.ybugmobile.vaktiva.ui.qibla

import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.data.sensor.CompassData
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import java.time.LocalDateTime

data class QiblaViewState(
    val settings: UserSettings? = null,
    val qiblaDirection: Double = 0.0,
    val compassData: CompassData = CompassData(0f, 0),
    val currentPrayerDay: PrayerDay? = null,
    val currentTime: LocalDateTime = LocalDateTime.now(),
    val isLoading: Boolean = true,
    val isNetworkAvailable: Boolean = true,
    val isLocationEnabled: Boolean = true,
    val isLocationPermissionGranted: Boolean = true,
    val hasSystemIssues: Boolean = false
)
