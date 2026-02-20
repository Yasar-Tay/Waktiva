package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate

/**
 * Encapsulates astronomical and calendar data related to the moon's state for a given date.
 * This is used for both visual rendering of the moon and religious date alignment.
 *
 * @property illumination The percentage of the moon's visible surface that is illuminated (0.0 to 1.0).
 * @property phaseProgress The current progress through the lunar cycle (0.0 to 1.0), 
 * where 0.0 is New Moon, 0.25 is First Quarter, 0.5 is Full Moon, and 0.75 is Last Quarter.
 * @property phaseName The descriptive name of the moon phase (e.g., "Waxing Crescent").
 * @property hijriDate A formatted string representation of the corresponding Hijri date.
 * @property moonrise The time of moonrise as a formatted string, or null if not available/calculated.
 * @property moonset The time of moonset as a formatted string, or null if not available/calculated.
 * @property date The [LocalDate] to which this moon phase data applies.
 */
data class MoonPhase(
    val illumination: Double,
    val phaseProgress: Double,
    val phaseName: String,
    val hijriDate: String,
    val moonrise: String?,
    val moonset: String?,
    val date: LocalDate
)
