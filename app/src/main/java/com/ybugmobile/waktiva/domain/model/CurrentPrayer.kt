package com.ybugmobile.waktiva.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class CurrentPrayer(
    val type: PrayerType,
    val time: LocalTime,
    val date: LocalDate,
    val elapsedDuration: Duration
)
