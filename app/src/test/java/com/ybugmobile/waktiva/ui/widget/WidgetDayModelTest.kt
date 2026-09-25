package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WidgetDayModelTest {

    private val today = LocalDate.of(2026, 9, 25)
    private val tomorrow = today.plusDays(1)
    private val days = listOf(day(today), day(tomorrow))

    @Test
    fun marksPassedNextAndUpcomingForToday() {
        val now = today.atTime(14, 0)
        val next = nextPrayer(PrayerType.ASR, today, LocalTime.of(16, 30), now)

        val rows = WidgetDayModel.rows(days, next, now)

        assertEquals(PrayerType.entries, rows.map { it.type })
        assertEquals(
            listOf(
                WidgetRowStatus.PASSED, WidgetRowStatus.PASSED, WidgetRowStatus.PASSED,
                WidgetRowStatus.NEXT, WidgetRowStatus.UPCOMING, WidgetRowStatus.UPCOMING
            ),
            rows.map { it.status }
        )
    }

    @Test
    fun switchesToTomorrowAfterIsha() {
        val now = today.atTime(22, 0)
        val next = nextPrayer(PrayerType.FAJR, tomorrow, LocalTime.of(5, 10), now)

        val rows = WidgetDayModel.rows(days, next, now)

        assertEquals(WidgetRowStatus.NEXT, rows.first().status)
        assertTrue(rows.drop(1).all { it.status == WidgetRowStatus.UPCOMING })
    }

    @Test
    fun rowAtExactlyNowCountsAsPassedWhenNotNext() {
        val now = today.atTime(13, 5)
        val next = nextPrayer(PrayerType.ASR, today, LocalTime.of(16, 30), now)

        val dhuhr = WidgetDayModel.rows(days, next, now).first { it.type == PrayerType.DHUHR }

        assertEquals(WidgetRowStatus.PASSED, dhuhr.status)
    }

    @Test
    fun returnsEmptyWhenDayIsNotCached() {
        val now = today.plusDays(5).atTime(9, 0)

        assertTrue(WidgetDayModel.rows(days, null, now).isEmpty())
    }

    @Test
    fun skipsMissingTimings() {
        val partial = PrayerDay(
            date = today,
            hijriDate = null,
            timings = mapOf(PrayerType.FAJR to LocalTime.of(5, 0), PrayerType.ISHA to LocalTime.of(20, 0))
        )

        val rows = WidgetDayModel.rows(listOf(partial), null, today.atTime(12, 0))

        assertEquals(listOf(PrayerType.FAJR, PrayerType.ISHA), rows.map { it.type })
    }

    @Test
    fun picksFamilyFromCellSize() {
        assertEquals(WidgetSize.SMALL, WidgetSize.from(0, 0))
        assertEquals(WidgetSize.SMALL, WidgetSize.from(160, 400))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.from(320, 160))
        assertEquals(WidgetSize.LARGE, WidgetSize.from(320, 320))
    }

    @Test
    fun shortCellsUseCompactBar() {
        // Widgets placed with the previous 4×1 design keep a readable single-row layout.
        assertEquals(WidgetSize.COMPACT, WidgetSize.from(320, 70))
        assertEquals(WidgetSize.COMPACT, WidgetSize.from(160, 60))
        assertEquals(WidgetSize.SMALL, WidgetSize.from(160, 110))
    }

    private fun day(date: LocalDate) = PrayerDay(
        date = date,
        hijriDate = null,
        timings = mapOf(
            PrayerType.FAJR to LocalTime.of(5, 10),
            PrayerType.SUNRISE to LocalTime.of(6, 40),
            PrayerType.DHUHR to LocalTime.of(13, 5),
            PrayerType.ASR to LocalTime.of(16, 30),
            PrayerType.MAGHRIB to LocalTime.of(19, 10),
            PrayerType.ISHA to LocalTime.of(20, 35)
        )
    )

    private fun nextPrayer(type: PrayerType, date: LocalDate, time: LocalTime, now: LocalDateTime) =
        NextPrayer(
            type = type,
            time = time,
            date = date,
            remainingDuration = Duration.between(now, date.atTime(time))
        )
}
