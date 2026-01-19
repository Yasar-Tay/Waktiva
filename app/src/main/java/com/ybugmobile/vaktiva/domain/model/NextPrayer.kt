package com.ybugmobile.vaktiva.domain.model

import java.time.Duration
import java.time.LocalTime

data class NextPrayer(
    val type: PrayerType,
    val time: LocalTime,
    val remainingDuration: Duration
)
