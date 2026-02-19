package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate

data class MoonPhase(
    val illumination: Double,
    val phaseProgress: Double, // 0.0 to 1.0 (New Moon to New Moon)
    val phaseName: String,
    val hijriDate: String,
    val moonrise: String?,
    val moonset: String?,
    val date: LocalDate
)
