package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class PrayerDay(
    val date: LocalDate,
    val hijriDate: HijriData?,
    val timings: Map<PrayerType, LocalTime>
)

data class HijriData(
    val day: Int,
    val monthEn: String,
    val year: Int
)
