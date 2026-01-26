package com.ybugmobile.vaktiva.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class NextPrayer(
    val type: PrayerType,
    val time: LocalTime,
    val date: LocalDate,
    val remainingDuration: Duration
)
