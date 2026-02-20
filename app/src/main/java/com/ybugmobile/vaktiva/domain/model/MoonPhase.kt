package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate

/**
 * Encapsulates astronomical and calendar data related to the moon's state for a given date.
 * This is used for both visual rendering of the moon and religious date alignment.
 *
 * @property illumination The percentage of the moon's visible surface that is illuminated (0.0 to 1.0).
 * @property phaseProgress The current progress through the lunar cycle (0.0 to 1.0).
 * @property phaseName The descriptive name of the moon phase.
 * @property hijriDate Structured Hijri date data.
 * @property moonrise The time of moonrise as a formatted string.
 * @property moonset The time of moonset as a formatted string.
 * @property date The [LocalDate] to which this moon phase data applies.
 * @property parallacticAngle The angle of the moon's crescent relative to the zenith (in degrees).
 */
data class MoonPhase(
    val illumination: Double,
    val phaseProgress: Double,
    val phaseName: String,
    val hijriDate: HijriData?,
    val moonrise: String?,
    val moonset: String?,
    val date: LocalDate,
    val parallacticAngle: Double = 0.0
)
