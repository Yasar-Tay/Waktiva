package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate

data class MoonPhase(
    val illumination: Double,
    val phaseName: String,
    val hijriDate: String,
    val moonrise: String?,
    val moonset: String?,
    val date: LocalDate
)
