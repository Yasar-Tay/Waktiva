package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate

/**
 * Represents a significant religious day anchored to a Gregorian date.
 */
data class ReligiousDay(
    val date: LocalDate,
    val nameResId: Int
)
