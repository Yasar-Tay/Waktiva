package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class PrayerDay(
    val date: LocalDate,
    val hijriDate: String,
    val timings: Map<PrayerType, LocalTime>
)
