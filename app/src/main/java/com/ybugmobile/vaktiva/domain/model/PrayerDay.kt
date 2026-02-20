package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents all prayer-related data for a single Gregorian calendar day.
 * This is a core domain model, encapsulating the prayer timings and corresponding Hijri date.
 *
 * @property date The specific [LocalDate] for which the prayer times are calculated.
 * @property hijriDate The corresponding [HijriData] for the Gregorian date. Can be null if not available.
 * @property timings A map where each [PrayerType] is associated with its [LocalTime].
 */
data class PrayerDay(
    val date: LocalDate,
    val hijriDate: HijriData?,
    val timings: Map<PrayerType, LocalTime>
)

/**
 * Represents a date in the Hijri (Islamic) calendar.
 *
 * @property day The day of the month (1-30).
 * @property monthNumber The numerical representation of the Hijri month (1-12).
 * @property monthEn The English transliteration of the Hijri month name (e.g., "Muharram").
 * @property year The Hijri year.
 */
data class HijriData(
    val day: Int,
    val monthNumber: Int,
    val monthEn: String,
    val year: Int
)
