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
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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
    /** Today's prayer times, which colour the sky over today's stars, and the time now, by the minute. */
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
    /**
     * The galaxy's days, oldest first: [GALAXY_WEEKS] whole weeks from [galaxyStart], the days
     * still to come null.
     */
    val galaxy: List<PrayerLogDay?> = emptyList(),
    val galaxyStart: LocalDate? = null,
    /** Prayers missed in the galaxy's weeks. */
    val galaxyMissed: Int = 0,
    /** The day picked in the galaxy, whose prayers can be marked under it. */
    val selected: PrayerLogDay? = null,
    val canShowEarlier: Boolean = false,
    val canShowLater: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * The prayer log screen (çetele): today's prayers as the stars of the day, how the last week and
 * month went, and the galaxy of the last five weeks to look back on and mark any day's prayers.
 */
@HiltViewModel
class PrayerLogViewModel @Inject constructor(
    private val prayerLogRepository: PrayerLogRepository,
    prayerRepository: PrayerRepository,
    private val timeManager: TimeManager
) : ViewModel() {

    // How many galaxies back from the one ending this week; see [galaxyStart].
    private val galaxiesBack = MutableStateFlow(0)

    // Null picks the galaxy's default day (see [defaultSelection]).
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
        combine(galaxiesBack, selectedDate, ::Pair)
    ) { prayed, start, prayerDays, now, (back, selected) ->
        buildPrayerLogState(prayed, start, prayerDays, now, back, selected, firstDayOfWeek)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrayerLogViewState())

    /** Moves the galaxy [by] five weeks earlier (negative) or later, within the last year. */
    fun showGalaxy(by: Int) {
        galaxiesBack.update { (it - by).coerceIn(0, GALAXIES_BACK) }
        selectedDate.value = null
    }

    /** Picks [date], up to today and within the last year, and shows the galaxy it's in. */
    fun select(date: LocalDate) {
        val today = timeManager.now().toLocalDate()
        val back = galaxiesBetween(date, today, firstDayOfWeek)
        if (date.isAfter(today) || back > GALAXIES_BACK) return
        galaxiesBack.value = back
        selectedDate.value = date
    }

    fun setPrayed(date: LocalDate, type: PrayerType, prayed: Boolean) {
        viewModelScope.launch { prayerLogRepository.setPrayed(date, type, prayed) }
    }

    companion object {
        /** How many galaxies back the log goes: about a year. */
        const val GALAXIES_BACK = 10
    }
}

/** How many weeks a galaxy spans. */
const val GALAXY_WEEKS = 5

/** The first day of [date]'s week. */
private fun weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(firstDay))

/** The first day of the galaxy [back] galaxies before the one ending in [today]'s week. */
internal fun galaxyStart(today: LocalDate, back: Int, firstDay: DayOfWeek): LocalDate =
    weekStart(today, firstDay).minusWeeks(GALAXY_WEEKS.toLong() * back + GALAXY_WEEKS - 1)

/** How many galaxies back from [today]'s the one holding [date] is. */
internal fun galaxiesBetween(date: LocalDate, today: LocalDate, firstDay: DayOfWeek): Int {
    val weeks = ChronoUnit.WEEKS.between(weekStart(date, firstDay), weekStart(today, firstDay)).toInt()
    return if (weeks < 0) 0 else weeks / GALAXY_WEEKS
}

/**
 * The day picked when a galaxy comes up: yesterday in this one (today being in the sky above,
 * yesterday is the day most likely left to fill in), else the galaxy's last day.
 */
internal fun defaultSelection(start: LocalDate, back: Int, today: LocalDate): LocalDate =
    if (back == 0) today.minusDays(1).takeUnless { it.isBefore(start) } ?: today
    else start.plusWeeks(GALAXY_WEEKS.toLong()).minusDays(1)

/**
 * The log as of [now], with the galaxy [back] galaxies before this week's and [selected] (or the
 * galaxy's default) picked in it, weeks starting on [firstDay]. Days without kept prayer times
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
    val galaxyStart = galaxyStart(today, back, firstDay)
    val selectedDate = (selected ?: defaultSelection(galaxyStart, back, today)).takeUnless { it.isAfter(today) } ?: today

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
    val first = listOf(trackedSince, today.minusDays(29), galaxyStart, selectedDate).minBy { it.toEpochDay() }
    val days = generateSequence(today) { it.minusDays(1) }
        .takeWhile { !it.isBefore(first) }
        .associateWith(::dayOf)

    fun tally(count: Long) = (0 until count).mapNotNull { days[today.minusDays(it)] }
        .fold(PrayerLogTally()) { sum, day -> PrayerLogTally(sum.prayed + day.prayed, sum.missed + day.missed) }

    val galaxy = (0 until GALAXY_WEEKS * 7).map { days[galaxyStart.plusDays(it.toLong())] }
    return PrayerLogViewState(
        today = days[today],
        todayTimes = byDate[today],
        now = now.toLocalTime(),
        streak = PrayerLog.streak(prayed, today),
        progress = PrayerLogProgress.of(prayed),
        last7Days = tally(7),
        last30Days = tally(30),
        startDate = start,
        galaxy = galaxy,
        galaxyStart = galaxyStart,
        galaxyMissed = galaxy.sumOf { it?.missed ?: 0 },
        selected = days[selectedDate],
        canShowEarlier = back < PrayerLogViewModel.GALAXIES_BACK,
        canShowLater = back > 0,
        isLoading = false
    )
}
