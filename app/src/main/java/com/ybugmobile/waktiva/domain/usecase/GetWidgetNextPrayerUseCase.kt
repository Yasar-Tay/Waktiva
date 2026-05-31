package com.ybugmobile.waktiva.domain.usecase

import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Widget-specific resolver for the next countdown target.
 *
 * Kept separate from the home-screen logic so widget transition policy can evolve
 * without accidentally changing in-app countdown behavior.
 */
class GetWidgetNextPrayerUseCase @Inject constructor() {

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

        val tomorrowFajr = tomorrow?.timings?.get(PrayerType.FAJR) ?: return null
        return NextPrayer(
            type = PrayerType.FAJR,
            time = tomorrowFajr,
            date = tomorrow.date,
            remainingDuration = Duration.between(now, tomorrow.date.atTime(tomorrowFajr))
        )
    }
}
