package com.ybugmobile.waktiva.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents all prayer-related data for a single Gregorian calendar day.
 * This is a core domain model, encapsulating the prayer timings and corresponding Hijri date.
 */
data class PrayerDay(
    val date: LocalDate,
    val hijriDate: HijriData?,
    val timings: Map<PrayerType, LocalTime>,
    
    // Astronomical data (Optional, cached from accurate source)
    val moonPhase: Double? = null,
    val moonIllumination: Double? = null,
    val moonrise: String? = null,
    val moonset: String? = null
)

/**
 * Represents a date in the Hijri (Islamic) calendar.
 */
data class HijriData(
    val day: Int,
    val monthNumber: Int,
    val monthEn: String,
    val year: Int
)
