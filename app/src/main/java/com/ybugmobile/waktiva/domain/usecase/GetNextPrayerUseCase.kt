package com.ybugmobile.waktiva.domain.usecase

import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Resolves the next countdown target shared by both the home screen and widget.
 *
 * Rules:
 * - Before Fajr: Fajr
 * - Between Fajr and Sunrise: Sunrise
 * - At or after Sunrise: Dhuhr
 * - Then continue through the rest of the day
 * - After Isha: next day's Fajr
 */
class GetNextPrayerUseCase @Inject constructor() {
    operator fun invoke(today: PrayerDay?, tomorrow: PrayerDay?, now: LocalDateTime): NextPrayer? {
        if (today == null) return null

        val currentTime = now.toLocalTime()
        val nextToday = PrayerType.entries
            .mapNotNull { type -> today.timings[type]?.let { type to it } }
            .firstOrNull { (_, time) -> time.isAfter(currentTime) }

        if (nextToday != null) {
            val (type, time) = nextToday
            return NextPrayer(
                type = type,
                time = time,
                date = today.date,
                remainingDuration = Duration.between(now, today.date.atTime(time))
            )
        }

        val fallbackTomorrowDate = tomorrow?.date ?: today.date.plusDays(1)
        val fallbackTomorrowFajr = tomorrow?.timings?.get(PrayerType.FAJR)
            ?: today.timings[PrayerType.FAJR]
            ?: return null

        return NextPrayer(
            type = PrayerType.FAJR,
            time = fallbackTomorrowFajr,
            date = fallbackTomorrowDate,
            remainingDuration = Duration.between(now, fallbackTomorrowDate.atTime(fallbackTomorrowFajr))
        )
    }
}
