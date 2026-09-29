package com.ybugmobile.waktiva.ui.prayerlog

import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PrayerLogStateTest {

    private val today = LocalDate.of(2026, 9, 29)
    private val now = LocalDateTime.of(today, LocalTime.of(17, 0))
    private val all = LoggedPrayers.toSet()

    @Test
    fun theTimelineStartsTodayAndShowsTheAskedDays() {
        val state = buildPrayerLogState(emptyMap(), null, listOf(prayerDay(today)), now, shownDays = 14)
        assertEquals(14, state.days.size)
        assertEquals(today, state.today?.date)
        assertEquals(today.minusDays(13), state.days.last().date)
    }

    @Test
    fun todaysPrayersFollowTheClock() {
        val state = buildPrayerLogState(
            mapOf(today to setOf(PrayerType.FAJR)), today, listOf(prayerDay(today)), now, shownDays = 14
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
        val state = buildPrayerLogState(prayed, start, listOf(prayerDay(today)), now, shownDays = 14)

        // Yesterday missed three; today none yet (Asr is still on).
        assertEquals(3, state.missedSinceStart)
        assertEquals(9, state.week.prayed)
        assertEquals(3, state.week.missed)
        assertEquals(0.75f, state.week.rate!!, 0.001f)
        assertEquals(0, state.streak)
        // Earlier days aren't held to the log.
        assertEquals(PrayerLogStatus.UNTRACKED, state.days[5].entries.first().status)
    }

    @Test
    fun noRateBeforeAnyPrayerHasGone() {
        val early = LocalDateTime.of(today, LocalTime.of(4, 0))
        val state = buildPrayerLogState(emptyMap(), null, listOf(prayerDay(today)), early, shownDays = 14)
        assertNull(state.week.rate)
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
