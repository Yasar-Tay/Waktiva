package com.ybugmobile.waktiva.ui.prayerlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.waktiva.domain.manager.TimeManager
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerLogRepository
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/** One prayer of a day in the log. [time] is when its time begins, if that day's times are kept. */
data class PrayerLogEntry(val type: PrayerType, val status: PrayerLogStatus, val time: LocalTime?)

/** A day in the log with its five prayers. */
data class PrayerLogDay(val date: LocalDate, val entries: List<PrayerLogEntry>) {
    val prayed: Int get() = entries.count { it.status == PrayerLogStatus.PRAYED }
    val missed: Int get() = entries.count { it.status == PrayerLogStatus.MISSED }
    val isComplete: Boolean get() = prayed == entries.size
    val isTracked: Boolean get() = entries.any { it.status != PrayerLogStatus.UNTRACKED }
}

/** How many prayers were prayed and missed over a stretch of days. */
data class PrayerLogTally(val prayed: Int = 0, val missed: Int = 0) {
    /** The share of prayers whose time has gone that were prayed, or null while none has gone. */
    val rate: Float? get() = (prayed + missed).takeIf { it > 0 }?.let { prayed.toFloat() / it }
}

data class PrayerLogViewState(
    val today: PrayerLogDay? = null,
    /** Today's prayer times, which set the dial's sky and its hours, and the time now, by the minute. */
    val todayTimes: PrayerDay? = null,
    val now: LocalTime = LocalTime.MIDNIGHT,
    /** Days in a row with all five prayers prayed. */
    val streak: Int = 0,
    /** XP, level and badges: the log as a game. */
    val progress: PrayerLogProgress = PrayerLogProgress(),
    val last7Days: PrayerLogTally = PrayerLogTally(),
    val last30Days: PrayerLogTally = PrayerLogTally(),
    /** The day the log began, or null before the first prayer is marked. */
    val startDate: LocalDate? = null,
    /** The month on the calendar. */
    val month: YearMonth? = null,
    /** The calendar's days, the 1st first; the days still to come null. */
    val calendar: List<PrayerLogDay?> = emptyList(),
    /** The day weeks start on, for the calendar's columns. */
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** How many times each prayer was missed in the calendar's month. */
    val monthMissed: Map<PrayerType, Int> = emptyMap(),
    /** The day picked on the calendar, whose prayers can be marked under it. */
    val selected: PrayerLogDay? = null,
    val canShowEarlier: Boolean = false,
    val canShowLater: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * The prayer log screen (çetele): today's prayers on the dial of the day, the level and the
 * streak, and a month's calendar to see which days and which prayers were missed, and mark them.
 */
@HiltViewModel
class PrayerLogViewModel @Inject constructor(
    private val prayerLogRepository: PrayerLogRepository,
    prayerRepository: PrayerRepository,
    private val timeManager: TimeManager
) : ViewModel() {

    // How many months back from this one the calendar shows.
    private val monthsBack = MutableStateFlow(0)

    // Null picks the month's default day (see [defaultSelection]).
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val firstDayOfWeek: DayOfWeek get() = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    // Prayers come and go by the minute; the seconds would only redo the same work.
    private val minute = timeManager.currentTime
        .map { it.truncatedTo(ChronoUnit.MINUTES) }
        .distinctUntilChanged()

    val state: StateFlow<PrayerLogViewState> = combine(
        prayerLogRepository.getPrayedPrayers(),
        prayerLogRepository.getStartDate(),
        prayerRepository.getPrayerDays(),
        minute,
        combine(monthsBack, selectedDate, ::Pair)
    ) { prayed, start, prayerDays, now, (back, selected) ->
        buildPrayerLogState(prayed, start, prayerDays, now, back, selected, firstDayOfWeek)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrayerLogViewState())

    /** Moves the calendar [by] months earlier (negative) or later, within the last year. */
    fun showMonth(by: Int) {
        monthsBack.update { (it - by).coerceIn(0, MONTHS_BACK) }
        selectedDate.value = null
    }

    /** Picks [date], up to today and within the last year, and shows its month. */
    fun select(date: LocalDate) {
        val today = timeManager.now().toLocalDate()
        val back = monthsBetween(date, today)
        if (date.isAfter(today) || back > MONTHS_BACK) return
        monthsBack.value = back
        selectedDate.value = date
    }

    fun setPrayed(date: LocalDate, type: PrayerType, prayed: Boolean) {
        viewModelScope.launch { prayerLogRepository.setPrayed(date, type, prayed) }
    }

    companion object {
        /** How many months back the calendar goes: a year. */
        const val MONTHS_BACK = 12
    }
}

/** How many months back from [today]'s the one holding [date] is. */
internal fun monthsBetween(date: LocalDate, today: LocalDate): Int =
    ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(today)).toInt().coerceAtLeast(0)

/**
 * The day picked when a month comes up: its last day before today with a prayer missed, the day
 * most likely left to fill in. Without one, yesterday in this month, else the month's last day.
 * [days] holds the days known, by date.
 */
internal fun defaultSelection(month: YearMonth, today: LocalDate, days: Map<LocalDate, PrayerLogDay>): LocalDate {
    val first = month.atDay(1)
    val last = month.atEndOfMonth().takeUnless { it.isAfter(today) } ?: today
    val missed = generateSequence(last) { it.minusDays(1) }
        .takeWhile { !it.isBefore(first) }
        .firstOrNull { it != today && (days[it]?.missed ?: 0) > 0 }
    return missed ?: if (last == today) today.minusDays(1).takeUnless { it.isBefore(first) } ?: today else last
}

/**
 * The log as of [now], with the calendar on the month [back] months before this one and [selected]
 * (or the month's default) picked, weeks starting on [firstDay]. Days without kept prayer times
 * (past days are pruned) still get their prayers, as gone.
 */
internal fun buildPrayerLogState(
    prayed: Map<LocalDate, Set<PrayerType>>,
    start: LocalDate?,
    prayerDays: List<PrayerDay>,
    now: LocalDateTime,
    back: Int,
    selected: LocalDate?,
    firstDay: DayOfWeek
): PrayerLogViewState {
    val today = now.toLocalDate()
    val trackedSince = PrayerLog.trackedSince(start, today)
    val byDate = prayerDays.associateBy { it.date }
    val month = YearMonth.from(today).minusMonths(back.toLong())
    val picked = selected?.takeUnless { it.isAfter(today) }

    fun dayOf(date: LocalDate): PrayerLogDay {
        val prayerDay = byDate[date]
        val nextDay = byDate[date.plusDays(1)]
        val marked = prayed[date].orEmpty()
        val entries = LoggedPrayers.map { type ->
            val time = prayerDay?.timings?.get(type)
            PrayerLogEntry(
                type = type,
                status = PrayerLog.status(
                    date = date,
                    type = type,
                    prayed = type in marked,
                    now = now,
                    start = time?.atDate(date),
                    end = prayerDay?.let { PrayerLog.windowEnd(type, it, nextDay) },
                    trackedSince = trackedSince
                ),
                time = time
            )
        }
        return PrayerLogDay(date, entries)
    }

    // Every day anything here needs, from the earliest of them up to today.
    // (LocalDate compares as a ChronoLocalDate, so by epoch day to stay a LocalDate.)
    val first = listOfNotNull(trackedSince, today.minusDays(29), month.atDay(1), picked).minBy { it.toEpochDay() }
    val days = generateSequence(today) { it.minusDays(1) }
        .takeWhile { !it.isBefore(first) }
        .associateWith(::dayOf)
    val selectedDate = picked ?: defaultSelection(month, today, days)

    fun tally(count: Long) = (0 until count).mapNotNull { days[today.minusDays(it)] }
        .fold(PrayerLogTally()) { sum, day -> PrayerLogTally(sum.prayed + day.prayed, sum.missed + day.missed) }

    val calendar = (1..month.lengthOfMonth()).map { days[month.atDay(it)] }
    return PrayerLogViewState(
        today = days[today],
        todayTimes = byDate[today],
        now = now.toLocalTime(),
        streak = PrayerLog.streak(prayed, today),
        progress = PrayerLogProgress.of(prayed),
        last7Days = tally(7),
        last30Days = tally(30),
        startDate = start,
        month = month,
        calendar = calendar,
        firstDayOfWeek = firstDay,
        monthMissed = LoggedPrayers.associateWith { type ->
            calendar.count { day -> day?.entries?.any { it.type == type && it.status == PrayerLogStatus.MISSED } == true }
        },
        selected = days[selectedDate],
        canShowEarlier = back < PrayerLogViewModel.MONTHS_BACK,
        canShowLater = back > 0,
        isLoading = false
    )
}
