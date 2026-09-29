package com.ybugmobile.waktiva.ui.prayerlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.waktiva.domain.manager.TimeManager
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLog
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
    /** The days shown on the timeline, today first. */
    val days: List<PrayerLogDay> = emptyList(),
    /** Days in a row with all five prayers prayed. */
    val streak: Int = 0,
    val week: PrayerLogTally = PrayerLogTally(),
    val month: PrayerLogTally = PrayerLogTally(),
    /** Prayers missed since the log began. */
    val missedSinceStart: Int = 0,
    /** The day the log began, or null before the first prayer is marked. */
    val startDate: LocalDate? = null,
    val canShowEarlier: Boolean = false,
    val isLoading: Boolean = true
) {
    val today: PrayerLogDay? get() = days.firstOrNull()
}

/**
 * The prayer log screen (çetele): today's prayers, how the last week and month went, and a
 * timeline of days to look back on and mark prayers in.
 */
@HiltViewModel
class PrayerLogViewModel @Inject constructor(
    private val prayerLogRepository: PrayerLogRepository,
    prayerRepository: PrayerRepository,
    timeManager: TimeManager
) : ViewModel() {

    private val shownDays = MutableStateFlow(PAGE_DAYS)

    // Prayers come and go by the minute; the seconds would only redo the same work.
    private val minute = timeManager.currentTime
        .map { it.truncatedTo(ChronoUnit.MINUTES) }
        .distinctUntilChanged()

    val state: StateFlow<PrayerLogViewState> = combine(
        prayerLogRepository.getPrayedPrayers(),
        prayerLogRepository.getStartDate(),
        prayerRepository.getPrayerDays(),
        minute,
        shownDays
    ) { prayed, start, prayerDays, now, shown ->
        buildPrayerLogState(prayed, start, prayerDays, now, shown)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrayerLogViewState())

    fun showEarlierDays() {
        shownDays.update { (it + PAGE_DAYS).coerceAtMost(MAX_DAYS) }
    }

    fun setPrayed(date: LocalDate, type: PrayerType, prayed: Boolean) {
        viewModelScope.launch { prayerLogRepository.setPrayed(date, type, prayed) }
    }

    companion object {
        /** Days the timeline shows at first, and adds each time earlier days are asked for. */
        const val PAGE_DAYS = 14

        /** How far back the timeline goes. */
        const val MAX_DAYS = 366
    }
}

/**
 * The log as of [now]: [shownDays] days of timeline back from today, and the tallies. Days
 * without kept prayer times (past days are pruned) still get their prayers, as gone.
 */
internal fun buildPrayerLogState(
    prayed: Map<LocalDate, Set<PrayerType>>,
    start: LocalDate?,
    prayerDays: List<PrayerDay>,
    now: LocalDateTime,
    shownDays: Int
): PrayerLogViewState {
    val today = now.toLocalDate()
    val trackedSince = PrayerLog.trackedSince(start, today)
    val byDate = prayerDays.associateBy { it.date }

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

    val tallyDays = maxOf(shownDays, 30, ChronoUnit.DAYS.between(trackedSince, today).toInt() + 1)
    val days = (0 until tallyDays).map { dayOf(today.minusDays(it.toLong())) }

    fun tally(count: Int) = days.take(count).fold(PrayerLogTally()) { sum, day ->
        PrayerLogTally(sum.prayed + day.prayed, sum.missed + day.missed)
    }

    return PrayerLogViewState(
        days = days.take(shownDays),
        streak = PrayerLog.streak(prayed, today),
        week = tally(7),
        month = tally(30),
        missedSinceStart = days.sumOf { it.missed },
        startDate = start,
        canShowEarlier = shownDays < PrayerLogViewModel.MAX_DAYS,
        isLoading = false
    )
}
