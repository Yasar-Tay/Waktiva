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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
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
    /** Days in a row with all five prayers prayed. */
    val streak: Int = 0,
    /** XP, level and badges: the log as a game. */
    val progress: PrayerLogProgress = PrayerLogProgress(),
    val last7Days: PrayerLogTally = PrayerLogTally(),
    val last30Days: PrayerLogTally = PrayerLogTally(),
    /** Prayers missed since the log began. */
    val missedSinceStart: Int = 0,
    /** The day the log began, or null before the first prayer is marked. */
    val startDate: LocalDate? = null,
    /** The month the calendar shows. */
    val shownMonth: YearMonth = YearMonth.now(),
    /** The shown month's days up to today; later days aren't in it. */
    val calendar: Map<LocalDate, PrayerLogDay> = emptyMap(),
    /** The day picked on the calendar, whose prayers can be marked under it. */
    val selected: PrayerLogDay? = null,
    val canShowPreviousMonth: Boolean = false,
    val canShowNextMonth: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * The prayer log screen (çetele): today's prayers with how the last week and month went, and a
 * month calendar to look back on and mark any day's prayers.
 */
@HiltViewModel
class PrayerLogViewModel @Inject constructor(
    private val prayerLogRepository: PrayerLogRepository,
    prayerRepository: PrayerRepository,
    private val timeManager: TimeManager
) : ViewModel() {

    private val shownMonth = MutableStateFlow(YearMonth.from(timeManager.now()))

    // Null picks the month's default day (see [defaultSelection]).
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    // Prayers come and go by the minute; the seconds would only redo the same work.
    private val minute = timeManager.currentTime
        .map { it.truncatedTo(ChronoUnit.MINUTES) }
        .distinctUntilChanged()

    val state: StateFlow<PrayerLogViewState> = combine(
        prayerLogRepository.getPrayedPrayers(),
        prayerLogRepository.getStartDate(),
        prayerRepository.getPrayerDays(),
        minute,
        combine(shownMonth, selectedDate, ::Pair)
    ) { prayed, start, prayerDays, now, (month, selected) ->
        buildPrayerLogState(prayed, start, prayerDays, now, month, selected)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrayerLogViewState())

    /** Moves the calendar [months] back or forth, within the last year, and picks its default day. */
    fun showMonth(months: Long) {
        val current = YearMonth.from(timeManager.now())
        shownMonth.update { month ->
            month.plusMonths(months).coerceIn(current.minusMonths(MONTHS_BACK), current)
        }
        selectedDate.value = null
    }

    fun select(date: LocalDate) {
        if (!date.isAfter(timeManager.now().toLocalDate())) selectedDate.value = date
    }

    fun setPrayed(date: LocalDate, type: PrayerType, prayed: Boolean) {
        viewModelScope.launch { prayerLogRepository.setPrayed(date, type, prayed) }
    }

    companion object {
        /** How many months back the calendar goes. */
        const val MONTHS_BACK = 12L
    }
}

/**
 * The day picked when [month] comes up: yesterday in this month (today being on the card above,
 * yesterday is the day most likely left to fill in), else the month's last day.
 */
internal fun defaultSelection(month: YearMonth, today: LocalDate): LocalDate = when {
    month != YearMonth.from(today) -> month.atEndOfMonth()
    today.dayOfMonth > 1 -> today.minusDays(1)
    else -> today
}

/**
 * The log as of [now], with [month] on the calendar and [selected] (or the month's default) picked
 * on it. Days without kept prayer times (past days are pruned) still get their prayers, as gone.
 */
internal fun buildPrayerLogState(
    prayed: Map<LocalDate, Set<PrayerType>>,
    start: LocalDate?,
    prayerDays: List<PrayerDay>,
    now: LocalDateTime,
    month: YearMonth,
    selected: LocalDate?
): PrayerLogViewState {
    val today = now.toLocalDate()
    val trackedSince = PrayerLog.trackedSince(start, today)
    val byDate = prayerDays.associateBy { it.date }
    val selectedDate = (selected ?: defaultSelection(month, today)).takeUnless { it.isAfter(today) } ?: today

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
    val first = listOf(trackedSince, today.minusDays(29), month.atDay(1), selectedDate).minBy { it.toEpochDay() }
    val days = generateSequence(today) { it.minusDays(1) }
        .takeWhile { !it.isBefore(first) }
        .associateWith(::dayOf)

    fun tally(count: Long) = (0 until count).mapNotNull { days[today.minusDays(it)] }
        .fold(PrayerLogTally()) { sum, day -> PrayerLogTally(sum.prayed + day.prayed, sum.missed + day.missed) }

    val thisMonth = YearMonth.from(today)
    return PrayerLogViewState(
        today = days[today],
        streak = PrayerLog.streak(prayed, today),
        progress = PrayerLogProgress.of(prayed),
        last7Days = tally(7),
        last30Days = tally(30),
        missedSinceStart = days.values.sumOf { it.missed },
        startDate = start,
        shownMonth = month,
        calendar = days.filterKeys { YearMonth.from(it) == month },
        selected = days[selectedDate],
        canShowPreviousMonth = month.isAfter(thisMonth.minusMonths(PrayerLogViewModel.MONTHS_BACK)),
        canShowNextMonth = month.isBefore(thisMonth),
        isLoading = false
    )
}
