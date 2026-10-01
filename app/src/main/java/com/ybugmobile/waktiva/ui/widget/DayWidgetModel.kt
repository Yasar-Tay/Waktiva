package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.data.local.CachedWeather
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.isLogged
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Where the day stands for the day circle widgets, which show only what matters at this moment
 * rather than the whole day at once.
 */
internal enum class MomentState {
    /** A prayer's time is on and it isn't marked: the time left for it, and "I prayed". */
    OPEN,

    /** As [OPEN], in the last [DayWidgetModel.ENDING_MINUTES] of its time. */
    ENDING,

    /** The prayer whose time it is is marked: a calm countdown to the next one. */
    PRAYED,

    /** No prayer's time is on (after sunrise): the countdown to the next one. */
    WAITING
}

/**
 * The moment: the [prayer] whose time it is (sunrise after sunrise), the day it's kept under in
 * the prayer log ([logDate], yesterday for the night's Isha before dawn), what comes [next] and
 * when the [prayer]'s time ends, which is when [next] begins.
 */
internal data class Moment(
    val state: MomentState,
    val prayer: PrayerType,
    val logDate: LocalDate,
    val next: NextPrayer,
    /** When the [prayer]'s time began; null when that day's times aren't kept. */
    val startsAt: LocalDateTime?,
    val endsAt: LocalDateTime,
    /** Whether "I prayed" is offered: the log is on and the prayer is one it keeps, not yet marked. */
    val canMark: Boolean
) {
    /** How much of the [prayer]'s time is left at [now], from 1 as it begins to 0 as it ends. */
    fun remaining(now: LocalDateTime): Float {
        val start = startsAt ?: return 1f
        val whole = Duration.between(start, endsAt).toMillis()
        if (whole <= 0L) return 0f
        return (Duration.between(now, endsAt).toMillis().toFloat() / whole).coerceIn(0f, 1f)
    }
}

/** One prayer in the 4×4 widget's line of times. */
internal data class DayTime(
    val type: PrayerType,
    val time: LocalTime,
    val isCurrent: Boolean,
    val isPassed: Boolean,
    val isPrayed: Boolean
)

/** The weather the widgets show for now. */
internal data class WidgetWeather(
    val condition: WeatherCondition,
    val isDay: Boolean,
    val temperature: Double
)

internal object DayWidgetModel {

    /** The last stretch of a prayer's time, when the widgets warn that it's ending. */
    const val ENDING_MINUTES = 30L

    /** How long the weather fetched stays "now"; after that the forecast for the hour stands in. */
    const val CURRENT_WEATHER_FRESH_MILLIS = 2 * 60 * 60 * 1000L

    /** The prayer whose time it is at [now]: the last to have begun, or the night's Isha before Fajr. */
    fun currentPrayer(day: PrayerDay, now: LocalTime): PrayerType =
        day.timings.entries
            .filter { !it.value.isAfter(now) }
            .maxByOrNull { it.value }
            ?.key ?: PrayerType.ISHA

    /**
     * The moment at [now] on [today], with [next] the prayer the app counts down to. [prayed] are
     * the prayers marked in the log, by day; with [logEnabled] off nothing is offered to mark.
     */
    fun moment(
        today: PrayerDay,
        now: LocalDateTime,
        next: NextPrayer,
        prayed: Map<LocalDate, Set<PrayerType>>,
        logEnabled: Boolean
    ): Moment {
        val fajr = today.timings[PrayerType.FAJR]
        val beforeDawn = fajr != null && now.toLocalTime().isBefore(fajr)
        val prayer = if (beforeDawn) PrayerType.ISHA else currentPrayer(today, now.toLocalTime())
        val logDate = if (beforeDawn) today.date.minusDays(1) else today.date
        val endsAt = next.date.atTime(next.time)
        // Before dawn the night's Isha began yesterday, a minute or two off today's time.
        val startsAt = today.timings[prayer]?.atDate(logDate)

        val isPrayed = prayer in prayed[logDate].orEmpty()
        val keeps = logEnabled && prayer.isLogged
        val state = when {
            prayer == PrayerType.SUNRISE -> MomentState.WAITING
            keeps && isPrayed -> MomentState.PRAYED
            Duration.between(now, endsAt) <= Duration.ofMinutes(ENDING_MINUTES) -> MomentState.ENDING
            else -> MomentState.OPEN
        }
        return Moment(
            state = state,
            prayer = prayer,
            logDate = logDate,
            next = next,
            startsAt = startsAt,
            endsAt = endsAt,
            canMark = keeps && !isPrayed && state != MomentState.WAITING
        )
    }

    /** [day]'s five prayers at [now], with those marked in [prayed]. */
    fun times(day: PrayerDay, now: LocalDateTime, current: PrayerType, prayed: Set<PrayerType>): List<DayTime> =
        PrayerType.entries.filter { it.isLogged }.mapNotNull { type ->
            val time = day.timings[type] ?: return@mapNotNull null
            val isCurrent = type == current && !day.date.atTime(time).isAfter(now)
            DayTime(
                type = type,
                time = time,
                isCurrent = isCurrent,
                isPassed = !isCurrent && !day.date.atTime(time).isAfter(now),
                isPrayed = type in prayed
            )
        }

    /**
     * The weather for [now] from the [cache]: what was reported while it's fresh, the forecast
     * for the hour after that, and nothing once the cache no longer holds [now]'s day.
     */
    fun weatherNow(cache: CachedWeather?, now: LocalDateTime, nowMillis: Long): WidgetWeather? {
        cache ?: return null
        val age = nowMillis - cache.fetchedAtMillis
        if (age in 0 until CURRENT_WEATHER_FRESH_MILLIS) {
            val info = cache.info
            return WidgetWeather(info.condition, info.isDay, info.temperature)
        }
        val hour = cache.info.forecast.firstOrNull { it.date == now.toLocalDate() }?.at(now.toLocalTime()) ?: return null
        return WidgetWeather(hour.condition, hour.isDay, hour.temperature)
    }
}
