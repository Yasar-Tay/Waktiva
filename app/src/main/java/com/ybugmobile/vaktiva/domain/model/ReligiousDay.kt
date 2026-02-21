package com.ybugmobile.vaktiva.domain.model

/**
 * Represents a significant day in the Islamic calendar.
 */
data class ReligiousDay(
    val month: Int,
    val day: Int,
    val nameResId: Int
)
