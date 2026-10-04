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
    fun theGalaxyIsFiveWholeWeeksEndingThisWeekAndPicksYesterday() {
        val state = state()
        assertEquals(35, state.galaxy.size)
        // Four weeks before this Monday, the 28th.
        assertEquals(LocalDate.of(2026, 8, 31), state.galaxyStart)
        // Up to today; the rest of this week is still to come.
        assertEquals(today, state.galaxy[29]?.date)
        assertNull(state.galaxy[30])
        assertEquals(today.minusDays(1), state.selected?.date)
        assertTrue(state.canShowEarlier)
        assertFalse(state.canShowLater)
    }

    @Test
    fun anEarlierGalaxyPicksItsLastDay() {
        val state = state(back = 1)
        assertEquals(LocalDate.of(2026, 7, 27), state.galaxyStart)
        assertEquals(LocalDate.of(2026, 8, 30), state.selected?.date)
        assertTrue(state.galaxy.all { it != null })
        assertTrue(state.canShowLater)
    }

    @Test
    fun theFirstDayOfAGalaxyPicksItself() {
        val first = LocalDate.of(2026, 8, 31)
        assertEquals(first, defaultSelection(first, 0, first))
    }

    @Test
    fun daysFindTheirGalaxy() {
        assertEquals(0, galaxiesBetween(today, today, monday))
        assertEquals(0, galaxiesBetween(LocalDate.of(2026, 8, 31), today, monday))
        assertEquals(1, galaxiesBetween(LocalDate.of(2026, 8, 30), today, monday))
    }

    @Test
    fun aDayAfterTodayCannotBePicked() {
        val state = state(days = emptyList(), selected = today.plusDays(1))
        assertEquals(today, state.selected?.date)
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
        assertEquals(3, state.galaxyMissed)
        assertEquals(9, state.last7Days.prayed)
        assertEquals(3, state.last7Days.missed)
        assertEquals(0.75f, state.last7Days.rate!!, 0.001f)
        assertEquals(0, state.streak)
        // Earlier days aren't held to the log.
        val earlier = state.galaxy.first { it?.date == today.minusDays(5) }!!
        assertEquals(PrayerLogStatus.UNTRACKED, earlier.entries.first().status)
    }

    @Test
    fun noRateBeforeAnyPrayerHasGone() {
        val early = LocalDateTime.of(today, LocalTime.of(4, 0))
        val state = state(at = early)
        assertNull(state.last7Days.rate)
        assertEquals(0, state.galaxyMissed)
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
