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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class PrayerLogStateTest {

    private val today = LocalDate.of(2026, 9, 29)
    private val now = LocalDateTime.of(today, LocalTime.of(17, 0))
    private val all = LoggedPrayers.toSet()

    private val thisMonth = YearMonth.from(today)

    @Test
    fun theCalendarShowsTheMonthUpToTodayAndPicksYesterday() {
        val state = buildPrayerLogState(emptyMap(), null, listOf(prayerDay(today)), now, thisMonth, null)
        assertEquals(29, state.calendar.size)
        assertEquals(today, state.today?.date)
        assertEquals(today.minusDays(1), state.selected?.date)
        assertTrue(state.canShowPreviousMonth)
        assertFalse(state.canShowNextMonth)
    }

    @Test
    fun theLastWeekRunsUpToTodayAcrossTheMonthsStart() {
        val first = LocalDate.of(2026, 10, 3)
        val state = buildPrayerLogState(
            emptyMap(), null, emptyList(), LocalDateTime.of(first, LocalTime.NOON), YearMonth.from(first), null
        )
        assertEquals((6L downTo 0L).map { first.minusDays(it) }, state.lastWeek.map { it.date })
    }

    @Test
    fun anEarlierMonthPicksItsLastDay() {
        val august = thisMonth.minusMonths(1)
        val state = buildPrayerLogState(emptyMap(), null, listOf(prayerDay(today)), now, august, null)
        assertEquals(31, state.calendar.size)
        assertEquals(august.atEndOfMonth(), state.selected?.date)
        assertTrue(state.canShowNextMonth)
    }

    @Test
    fun onTheFirstOfTheMonthTodayIsPicked() {
        val first = LocalDate.of(2026, 10, 1)
        assertEquals(first, defaultSelection(YearMonth.from(first), first))
    }

    @Test
    fun aDayAfterTodayCannotBePicked() {
        val state = buildPrayerLogState(emptyMap(), null, emptyList(), now, thisMonth, today.plusDays(1))
        assertEquals(today, state.selected?.date)
    }

    @Test
    fun todaysPrayersFollowTheClock() {
        val state = buildPrayerLogState(
            mapOf(today to setOf(PrayerType.FAJR)), today, listOf(prayerDay(today)), now, thisMonth, null
        )
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
        val state = buildPrayerLogState(prayed, start, listOf(prayerDay(today)), now, thisMonth, null)

        // Yesterday missed three; today none yet (Asr is still on).
        assertEquals(3, state.missedSinceStart)
        assertEquals(9, state.last7Days.prayed)
        assertEquals(3, state.last7Days.missed)
        assertEquals(0.75f, state.last7Days.rate!!, 0.001f)
        assertEquals(0, state.streak)
        // Earlier days aren't held to the log.
        assertEquals(PrayerLogStatus.UNTRACKED, state.calendar.getValue(today.minusDays(5)).entries.first().status)
    }

    @Test
    fun noRateBeforeAnyPrayerHasGone() {
        val early = LocalDateTime.of(today, LocalTime.of(4, 0))
        val state = buildPrayerLogState(emptyMap(), null, listOf(prayerDay(today)), early, thisMonth, null)
        assertNull(state.last7Days.rate)
        assertEquals(0, state.missedSinceStart)
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
