package com.ybugmobile.waktiva.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents the immediate upcoming prayer event.
 * Used for countdowns and scheduling the next alarm.
 *
 * @property type The [PrayerType] of the next prayer (e.g., FAJR).
 * @property time The [LocalTime] when the prayer starts.
 * @property date The [LocalDate] of the prayer (could be tomorrow if today's prayers are finished).
 * @property remainingDuration The calculated [Duration] between "now" and the prayer time.
 * @property isTest A flag indicating if this object was created for UI testing or simulation purposes.
 */
data class NextPrayer(
    val type: PrayerType,
    val time: LocalTime,
    val date: LocalDate,
    val remainingDuration: Duration,
    val isTest: Boolean = false
)
