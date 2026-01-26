package com.ybugmobile.vaktiva.domain.usecase

import com.ybugmobile.vaktiva.domain.model.NextPrayer
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

class GetNextPrayerUseCase @Inject constructor() {
    operator fun invoke(today: PrayerDay?, tomorrow: PrayerDay?, now: LocalDateTime): NextPrayer? {
        if (today == null) return null
        
        val currentTime = now.toLocalTime()
        
        // Find the first prayer today that is after now
        val nextToday = PrayerType.values()
            .map { it to today.timings[it]!! }
            .firstOrNull { it.second.isAfter(currentTime) }

        return if (nextToday != null) {
            NextPrayer(
                type = nextToday.first,
                time = nextToday.second,
                date = today.date,
                remainingDuration = Duration.between(currentTime, nextToday.second)
            )
        } else if (tomorrow != null) {
            // If no more prayers today, it's Fajr tomorrow
            val firstTomorrow = tomorrow.timings[PrayerType.FAJR]!!
            val durationToMidnight = Duration.between(currentTime, LocalTime.MAX)
            val durationFromMidnight = Duration.between(LocalTime.MIN, firstTomorrow)
            
            NextPrayer(
                type = PrayerType.FAJR,
                time = firstTomorrow,
                date = tomorrow.date,
                remainingDuration = durationToMidnight.plus(durationFromMidnight).plusSeconds(1)
            )
        } else {
            null
        }
    }
}
