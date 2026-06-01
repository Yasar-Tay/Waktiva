package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WidgetChronometerResolverTest {

    @Test
    fun reusesCachedBaseForSameFutureTarget() {
        val nextPrayer = nextPrayer(
            type = PrayerType.DHUHR,
            date = LocalDate.of(2026, 6, 1),
            time = LocalTime.of(13, 0)
        )
        val nowEpochMillis = epochMillis(LocalDate.of(2026, 6, 1), LocalTime.of(12, 0))
        val elapsedRealtime = 10_000L
        val cachedBaseTime = 3_610_000L
        val cachedKey = "DHUHR@2026-06-01@13:00"

        val state = WidgetChronometerResolver.resolve(
            nextPrayer = nextPrayer,
            nowEpochMillis = nowEpochMillis,
            elapsedRealtime = elapsedRealtime,
            cachedPrayerKey = cachedKey,
            cachedBaseTime = cachedBaseTime
        )

        assertEquals(cachedBaseTime, state?.baseTime)
        assertTrue(state?.isRunning == true)
    }

    @Test
    fun doesNotReuseExpiredBaseWhenTargetPassed() {
        val nextPrayer = nextPrayer(
            type = PrayerType.SUNRISE,
            date = LocalDate.of(2026, 6, 1),
            time = LocalTime.of(5, 30)
        )
        val nowEpochMillis = epochMillis(LocalDate.of(2026, 6, 1), LocalTime.of(5, 31))
        val elapsedRealtime = 20_000L

        val state = WidgetChronometerResolver.resolve(
            nextPrayer = nextPrayer,
            nowEpochMillis = nowEpochMillis,
            elapsedRealtime = elapsedRealtime,
            cachedPrayerKey = "SUNRISE@2026-06-01@05:30",
            cachedBaseTime = 99_999L
        )

        assertEquals(elapsedRealtime, state?.baseTime)
        assertFalse(state?.isRunning == true)
    }

    @Test
    fun computesFreshBaseForNewTarget() {
        val nextPrayer = nextPrayer(
            type = PrayerType.DHUHR,
            date = LocalDate.of(2026, 6, 1),
            time = LocalTime.of(13, 0)
        )
        val nowEpochMillis = epochMillis(LocalDate.of(2026, 6, 1), LocalTime.of(12, 45))
        val elapsedRealtime = 50_000L

        val state = WidgetChronometerResolver.resolve(
            nextPrayer = nextPrayer,
            nowEpochMillis = nowEpochMillis,
            elapsedRealtime = elapsedRealtime,
            cachedPrayerKey = "SUNRISE@2026-06-01@05:30",
            cachedBaseTime = 99_999L
        )

        assertEquals(elapsedRealtime + (15 * 60 * 1000L), state?.baseTime)
        assertTrue(state?.isRunning == true)
    }

    private fun nextPrayer(type: PrayerType, date: LocalDate, time: LocalTime): NextPrayer = NextPrayer(
        type = type,
        time = time,
        date = date,
        remainingDuration = java.time.Duration.ZERO
    )

    private fun epochMillis(date: LocalDate, time: LocalTime): Long = date.atTime(time)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
