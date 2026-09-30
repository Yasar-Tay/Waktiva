package com.ybugmobile.waktiva.domain.model

import java.time.LocalDate

/**
 * A copy of the prayer log (çetele), to keep or to move to another phone: the day the log began
 * and the prayers marked as prayed, by day.
 */
data class PrayerLogBackup(
    val startDate: LocalDate?,
    val prayed: Map<LocalDate, Set<PrayerType>>
) {
    /** How many prayers the copy holds. */
    val size: Int get() = prayed.values.sumOf { it.size }
}
