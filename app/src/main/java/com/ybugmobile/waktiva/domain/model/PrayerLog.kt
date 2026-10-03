package com.ybugmobile.waktiva.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/** The five daily prayers the prayer log (çetele) keeps; sunrise is a time, not a prayer. */
val LoggedPrayers: List<PrayerType> = PrayerType.entries.filter { it != PrayerType.SUNRISE }

val PrayerType.isLogged: Boolean get() = this != PrayerType.SUNRISE

/** Where a prayer stands in the log. */
enum class PrayerLogStatus {
    /** Marked as prayed. */
    PRAYED,

    /** Its time came and went without being marked. */
    MISSED,

    /** Its time has come and not yet gone. */
    ACTIVE,

    /** Its time hasn't come yet. */
    UPCOMING,

    /** Its time went before the user began the log, so there's nothing to hold it to. */
    UNTRACKED
}

/** A nudge [at] a while before [type]'s time on [date] ends, [endsAt], to mark it if prayed. */
data class PrayerLogNudge(val date: LocalDate, val type: PrayerType, val at: LocalDateTime, val endsAt: LocalDateTime)

object PrayerLog {

    /**
     * Where [type] on [date] stands at [now]. [start] and [end] bound the prayer's time (see
     * [windowEnd]); they're null for days without prayer times, such as past days no longer kept,
     * whose prayers count as gone. Days before [trackedSince] aren't held to the log.
     */
    fun status(
        date: LocalDate,
        type: PrayerType,
        prayed: Boolean,
        now: LocalDateTime,
        start: LocalDateTime?,
        end: LocalDateTime?,
        trackedSince: LocalDate
    ): PrayerLogStatus {
        if (prayed) return PrayerLogStatus.PRAYED
        val today = now.toLocalDate()
        if (date.isAfter(today)) return PrayerLogStatus.UPCOMING
        if (start != null && now.isBefore(start)) return PrayerLogStatus.UPCOMING
        val isOpen = if (end != null) now.isBefore(end) else date == today
        if (isOpen) return PrayerLogStatus.ACTIVE
        return if (date.isBefore(trackedSince)) PrayerLogStatus.UNTRACKED else PrayerLogStatus.MISSED
    }

    /**
     * When [type]'s time on [day] ends: at the next time of the day, and for Isha at the next
     * dawn. Without [nextDay], that dawn is taken from [day]'s own Fajr, a minute or two off.
     */
    fun windowEnd(type: PrayerType, day: PrayerDay, nextDay: PrayerDay?): LocalDateTime? {
        val endsAt = when (type) {
            PrayerType.FAJR -> PrayerType.SUNRISE
            PrayerType.SUNRISE -> PrayerType.DHUHR
            PrayerType.DHUHR -> PrayerType.ASR
            PrayerType.ASR -> PrayerType.MAGHRIB
            PrayerType.MAGHRIB -> PrayerType.ISHA
            PrayerType.ISHA -> return (nextDay?.timings?.get(PrayerType.FAJR) ?: day.timings[PrayerType.FAJR])
                ?.atDate(day.date.plusDays(1))
        }
        return day.timings[endsAt]?.atDate(day.date)
    }

    /**
     * The next reminder to mark a day's prayers after [now]: [minutesAfterIsha] after that day's
     * Isha begins, with the day it's for. Null when no kept day has a reminder still ahead.
     */
    fun nextReminder(
        prayerDays: List<PrayerDay>,
        now: LocalDateTime,
        minutesAfterIsha: Int
    ): Pair<LocalDate, LocalDateTime>? = prayerDays
        .mapNotNull { day ->
            day.timings[PrayerType.ISHA]?.let { isha -> day.date to isha.atDate(day.date).plusMinutes(minutesAfterIsha.toLong()) }
        }
        .filter { (_, at) -> at.isAfter(now) }
        .minByOrNull { (_, at) -> at }

    /**
     * The prayers nudged before their time ends. Isha's ends at dawn, deep in the night; the
     * reminder after Isha speaks for it.
     */
    val NudgedPrayers: List<PrayerType> = listOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB)

    /**
     * The next nudge after [now]: [minutesBefore] before a [NudgedPrayers] prayer's time ends, on
     * a kept day. A time shorter than that gets none. Null when no kept day has one still ahead.
     */
    fun nextNudge(prayerDays: List<PrayerDay>, now: LocalDateTime, minutesBefore: Long): PrayerLogNudge? {
        val byDate = prayerDays.associateBy { it.date }
        return prayerDays
            .flatMap { day ->
                NudgedPrayers.mapNotNull { type ->
                    val start = day.timings[type]?.atDate(day.date) ?: return@mapNotNull null
                    val end = windowEnd(type, day, byDate[day.date.plusDays(1)]) ?: return@mapNotNull null
                    val at = end.minusMinutes(minutesBefore).takeIf { it.isAfter(start) } ?: return@mapNotNull null
                    PrayerLogNudge(day.date, type, at, end)
                }
            }
            .filter { it.at.isAfter(now) }
            .minByOrNull { it.at }
    }

    /** The first day the log holds prayers to: the day it began, or [today] before it has. */
    fun trackedSince(start: LocalDate?, today: LocalDate): LocalDate =
        start?.takeIf { it.isBefore(today) } ?: today

    /**
     * How many days in a row, up to [today], have all five prayers marked. Today counts once it's
     * complete; until then the run up to yesterday still stands.
     */
    fun streak(prayed: Map<LocalDate, Set<PrayerType>>, today: LocalDate): Int {
        fun isComplete(date: LocalDate) = prayed[date]?.containsAll(LoggedPrayers) == true
        var date = if (isComplete(today)) today else today.minusDays(1)
        var days = 0
        while (isComplete(date)) {
            days++
            date = date.minusDays(1)
        }
        return days
    }
}
