package com.ybugmobile.waktiva.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PrayerLogTest {

    private val today = LocalDate.of(2026, 9, 29)
    private val day = prayerDay(today)
    private val tomorrow = prayerDay(today.plusDays(1), fajr = LocalTime.of(5, 31))

    private fun at(hour: Int, minute: Int, date: LocalDate = today) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    private fun status(
        type: PrayerType,
        now: LocalDateTime,
        prayed: Boolean = false,
        date: LocalDate = today,
        trackedSince: LocalDate = today,
        prayerDay: PrayerDay? = day
    ) = PrayerLog.status(
        date = date,
        type = type,
        prayed = prayed,
        now = now,
        start = prayerDay?.timings?.get(type)?.atDate(date),
        end = prayerDay?.let { PrayerLog.windowEnd(type, it, tomorrow) },
        trackedSince = trackedSince
    )

    @Test
    fun aPrayerIsUpcomingBeforeItsTime() {
        assertEquals(PrayerLogStatus.UPCOMING, status(PrayerType.ASR, at(13, 0)))
    }

    @Test
    fun aPrayerIsActiveFromItsTimeUntilTheNext() {
        assertEquals(PrayerLogStatus.ACTIVE, status(PrayerType.DHUHR, at(13, 5)))
        assertEquals(PrayerLogStatus.ACTIVE, status(PrayerType.DHUHR, at(16, 29)))
    }

    @Test
    fun aPrayerNotMarkedIsMissedOnceItsTimeEnds() {
        assertEquals(PrayerLogStatus.MISSED, status(PrayerType.DHUHR, at(16, 30)))
        assertEquals(PrayerLogStatus.MISSED, status(PrayerType.FAJR, at(7, 10)))
    }

    @Test
    fun aMarkedPrayerIsPrayedWhateverTheTime() {
        assertEquals(PrayerLogStatus.PRAYED, status(PrayerType.DHUHR, at(23, 0), prayed = true))
    }

    @Test
    fun ishaStaysOpenPastMidnightUntilTheNextDawn() {
        assertEquals(PrayerLogStatus.ACTIVE, status(PrayerType.ISHA, at(2, 0, today.plusDays(1))))
        assertEquals(PrayerLogStatus.MISSED, status(PrayerType.ISHA, at(5, 31, today.plusDays(1))))
    }

    @Test
    fun pastDaysWithoutPrayerTimesCountAsGone() {
        val yesterday = today.minusDays(1)
        assertEquals(
            PrayerLogStatus.MISSED,
            status(PrayerType.ASR, at(9, 0), date = yesterday, trackedSince = yesterday, prayerDay = null)
        )
    }

    @Test
    fun daysBeforeTheLogBeganAreNotHeldToIt() {
        val lastWeek = today.minusDays(7)
        assertEquals(
            PrayerLogStatus.UNTRACKED,
            status(PrayerType.ASR, at(9, 0), date = lastWeek, trackedSince = today, prayerDay = null)
        )
        assertEquals(
            PrayerLogStatus.PRAYED,
            status(PrayerType.ASR, at(9, 0), prayed = true, date = lastWeek, trackedSince = today, prayerDay = null)
        )
    }

    @Test
    fun futureDaysAreUpcoming() {
        assertEquals(PrayerLogStatus.UPCOMING, status(PrayerType.FAJR, at(23, 0), date = today.plusDays(1)))
    }

    @Test
    fun ishaEndsAtTheNextDaysFajrOrTodaysWithoutIt() {
        assertEquals(at(5, 31, today.plusDays(1)), PrayerLog.windowEnd(PrayerType.ISHA, day, tomorrow))
        assertEquals(at(5, 30, today.plusDays(1)), PrayerLog.windowEnd(PrayerType.ISHA, day, null))
    }

    @Test
    fun theLogIsTrackedFromItsStartOrToday() {
        assertEquals(today, PrayerLog.trackedSince(null, today))
        assertEquals(today.minusDays(3), PrayerLog.trackedSince(today.minusDays(3), today))
        assertEquals(today, PrayerLog.trackedSince(today.plusDays(2), today))
    }

    @Test
    fun theStreakCountsCompleteDaysUpToToday() {
        val all = LoggedPrayers.toSet()
        val prayed = mapOf(
            today to all,
            today.minusDays(1) to all,
            today.minusDays(2) to all - PrayerType.ASR,
            today.minusDays(3) to all
        )
        assertEquals(2, PrayerLog.streak(prayed, today))
    }

    @Test
    fun anUnfinishedTodayKeepsYesterdaysStreak() {
        val all = LoggedPrayers.toSet()
        val prayed = mapOf(
            today to setOf(PrayerType.FAJR),
            today.minusDays(1) to all,
            today.minusDays(2) to all
        )
        assertEquals(2, PrayerLog.streak(prayed, today))
        assertEquals(0, PrayerLog.streak(emptyMap(), today))
    }

    @Test
    fun sunriseIsNotLogged() {
        assertEquals(5, LoggedPrayers.size)
        assertNull(LoggedPrayers.find { it == PrayerType.SUNRISE })
    }

    private fun prayerDay(date: LocalDate, fajr: LocalTime = LocalTime.of(5, 30)) = PrayerDay(
        date = date,
        hijriDate = null,
        timings = mapOf(
            PrayerType.FAJR to fajr,
            PrayerType.SUNRISE to LocalTime.of(7, 0),
            PrayerType.DHUHR to LocalTime.of(13, 5),
            PrayerType.ASR to LocalTime.of(16, 30),
            PrayerType.MAGHRIB to LocalTime.of(19, 10),
            PrayerType.ISHA to LocalTime.of(20, 35)
        )
    )
}
