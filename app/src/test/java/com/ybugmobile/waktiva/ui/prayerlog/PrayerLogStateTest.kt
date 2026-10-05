package com.ybugmobile.waktiva.ui.prayerlog

import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class PrayerLogStateTest {

    // A Tuesday.
    private val today = LocalDate.of(2026, 9, 29)
    private val now = LocalDateTime.of(today, LocalTime.of(17, 0))
    private val all = LoggedPrayers.toSet()
    private val monday = DayOfWeek.MONDAY

    private fun state(
        prayed: Map<LocalDate, Set<PrayerType>> = emptyMap(),
        start: LocalDate? = null,
        days: List<PrayerDay> = listOf(prayerDay(today)),
        at: LocalDateTime = now,
        back: Int = 0,
        selected: LocalDate? = null
    ) = buildPrayerLogState(prayed, start, days, at, back, selected, monday)

    @Test
    fun theCalendarIsThisMonthAndPicksYesterday() {
        val state = state()
        assertEquals(YearMonth.of(2026, 9), state.month)
        assertEquals(30, state.calendar.size)
        // Up to today, the 29th; the 30th is still to come.
        assertEquals(today, state.calendar[28]?.date)
        assertNull(state.calendar[29])
        assertEquals(today.minusDays(1), state.selected?.date)
        assertTrue(state.canShowEarlier)
        assertFalse(state.canShowLater)
    }

    @Test
    fun anEarlierMonthPicksItsLastDay() {
        val state = state(back = 1)
        assertEquals(YearMonth.of(2026, 8), state.month)
        assertEquals(LocalDate.of(2026, 8, 31), state.selected?.date)
        assertTrue(state.calendar.all { it != null })
        assertTrue(state.canShowLater)
    }

    @Test
    fun aMonthPicksItsLastDayWithAPrayerMissed() {
        val start = today.minusDays(10)
        val gap = today.minusDays(4)
        val prayed = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .associateWith {
                when (it) {
                    gap -> all - PrayerType.FAJR
                    // Today's two whose time has gone are prayed; Asr is still on.
                    today -> setOf(PrayerType.FAJR, PrayerType.DHUHR)
                    else -> all
                }
            }
        val state = state(prayed = prayed, start = start)
        assertEquals(gap, state.selected?.date)
        assertEquals(1, state.monthMissed[PrayerType.FAJR])
        assertEquals(0, state.monthMissed[PrayerType.ISHA])
    }

    @Test
    fun theFirstDayOfAMonthPicksItself() {
        val first = LocalDate.of(2026, 10, 1)
        assertEquals(first, defaultSelection(YearMonth.of(2026, 10), first, emptyMap()))
    }

    @Test
    fun daysFindTheirMonth() {
        assertEquals(0, monthsBetween(today, today))
        assertEquals(0, monthsBetween(LocalDate.of(2026, 9, 1), today))
        assertEquals(1, monthsBetween(LocalDate.of(2026, 8, 31), today))
        assertEquals(12, monthsBetween(LocalDate.of(2025, 9, 1), today))
    }

    @Test
    fun aDayAfterTodayCannotBePicked() {
        val state = state(days = emptyList(), selected = today.plusDays(1))
        assertEquals(today.minusDays(1), state.selected?.date)
    }

    @Test
    fun todaysPrayersFollowTheClock() {
        val state = state(prayed = mapOf(today to setOf(PrayerType.FAJR)), start = today)
        val statuses = state.today!!.entries.map { it.status }
        assertEquals(
            listOf(
                PrayerLogStatus.PRAYED,   // Fajr, marked
                PrayerLogStatus.MISSED,   // Dhuhr, over at 16:30
                PrayerLogStatus.ACTIVE,   // Asr, until 19:10
                PrayerLogStatus.UPCOMING, // Maghrib
                PrayerLogStatus.UPCOMING  // Isha
            ),
            statuses
        )
    }

    @Test
    fun tallyAndMissedCountOnlyTheLoggedDays() {
        val start = today.minusDays(2)
        val prayed = mapOf(
            today.minusDays(2) to all,
            today.minusDays(1) to setOf(PrayerType.FAJR, PrayerType.DHUHR),
            today to setOf(PrayerType.FAJR, PrayerType.DHUHR)
        )
        val state = state(prayed = prayed, start = start)

        // Yesterday missed three; today none yet (Asr is still on).
        assertEquals(1, state.monthMissed[PrayerType.ASR])
        assertEquals(3, state.monthMissed.values.sum())
        assertEquals(9, state.last7Days.prayed)
        assertEquals(3, state.last7Days.missed)
        assertEquals(0.75f, state.last7Days.rate!!, 0.001f)
        assertEquals(0, state.streak)
        // Earlier days aren't held to the log.
        val earlier = state.calendar.first { it?.date == today.minusDays(5) }!!
        assertEquals(PrayerLogStatus.UNTRACKED, earlier.entries.first().status)
    }

    @Test
    fun noRateBeforeAnyPrayerHasGone() {
        val early = LocalDateTime.of(today, LocalTime.of(4, 0))
        val state = state(at = early)
        assertNull(state.last7Days.rate)
        assertEquals(0, state.monthMissed.values.sum())
    }

    private fun prayerDay(date: LocalDate) = PrayerDay(
        date = date,
        hijriDate = null,
        timings = mapOf(
            PrayerType.FAJR to LocalTime.of(5, 30),
            PrayerType.SUNRISE to LocalTime.of(7, 0),
            PrayerType.DHUHR to LocalTime.of(13, 5),
            PrayerType.ASR to LocalTime.of(16, 30),
            PrayerType.MAGHRIB to LocalTime.of(19, 10),
            PrayerType.ISHA to LocalTime.of(20, 35)
        )
    )
}
