package com.ybugmobile.waktiva.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrayerLogProgressTest {

    private val day = LocalDate.of(2026, 9, 1)
    private val all = LoggedPrayers.toSet()

    @Test
    fun anEmptyLogStartsAtLevelOne() {
        val progress = PrayerLogProgress.of(emptyMap())
        assertEquals(0, progress.xp)
        assertEquals(1, progress.level)
        assertTrue(progress.earned.isEmpty())
        assertEquals(PrayerLogBadge.FIRST_PRAYER, progress.nextBadge?.badge)
    }

    @Test
    fun aPrayerIsTenXpAndAFullDayAddsItsBonus() {
        val progress = PrayerLogProgress.of(mapOf(day to all, day.plusDays(1) to setOf(PrayerType.FAJR)))
        assertEquals(6, progress.totalPrayed)
        assertEquals(1, progress.fullDays)
        assertEquals(6 * 10 + 25, progress.xp)
        assertEquals(setOf(PrayerLogBadge.FIRST_PRAYER, PrayerLogBadge.FIRST_FULL_DAY), progress.earned)
    }

    @Test
    fun eachLevelTakesALittleMore() {
        // Two full days: 2 × 75 = 150 XP, past level 1's 100, 50 into level 2's 125.
        val progress = PrayerLogProgress.of(mapOf(day to all, day.plusDays(1) to all))
        assertEquals(2, progress.level)
        assertEquals(50, progress.levelXp)
        assertEquals(125, progress.levelSpan)
    }

    @Test
    fun theBestStreakOutlivesABreak() {
        val prayed = (0L until 4).associate { day.plusDays(it) to all } +
            (6L until 8).associate { day.plusDays(it) to all }
        val progress = PrayerLogProgress.of(prayed)
        assertEquals(4, progress.bestStreak)
        assertTrue(PrayerLogBadge.STREAK_3 in progress.earned)
        assertFalse(PrayerLogBadge.STREAK_7 in progress.earned)
        assertEquals(4, progress.badges.first { it.badge == PrayerLogBadge.STREAK_7 }.current)
    }

    @Test
    fun aWeekOfFajrEarnsTheMorningStar() {
        val prayed = (0L until 7).associate { day.plusDays(it) to setOf(PrayerType.FAJR) }
        val progress = PrayerLogProgress.of(prayed)
        assertTrue(PrayerLogBadge.FAJR_7 in progress.earned)
        assertEquals(0, progress.bestStreak)
    }

    @Test
    fun badgeProgressStopsAtItsTarget() {
        val prayed = (0L until 30).associate { day.plusDays(it) to all }
        val progress = PrayerLogProgress.of(prayed)
        val hundred = progress.badges.first { it.badge == PrayerLogBadge.PRAYERS_100 }
        assertEquals(100, hundred.current)
        assertTrue(hundred.isEarned)
        assertEquals(PrayerLogBadge.STREAK_40, progress.nextBadge?.badge)
    }

    @Test
    fun withEveryBadgeEarnedThereIsNoNext() {
        val prayed = (0L until 100).associate { day.plusDays(it) to all }
        assertNull(PrayerLogProgress.of(prayed).nextBadge)
    }

    @Test
    fun markingTheLastPrayerCelebratesTheFullDayAndItsBadge() {
        val before = mapOf(day to all - PrayerType.ISHA)
        val after = mapOf(day to all)
        val celebration = PrayerLogProgress.celebration(before, after, day, day)
        assertTrue(celebration!!.fullDay)
        assertEquals(1, celebration.streak)
        assertEquals(listOf(PrayerLogBadge.FIRST_FULL_DAY), celebration.badges)
    }

    @Test
    fun aLevelReachedIsCelebrated() {
        // A full day and two prayers are 95 XP; a third makes 105, past level 1's 100.
        val before = mapOf(day to all, day.plusDays(1) to setOf(PrayerType.FAJR, PrayerType.DHUHR))
        val after = mapOf(day to all, day.plusDays(1) to setOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR))
        val celebration = PrayerLogProgress.celebration(before, after, day.plusDays(1), day.plusDays(1))
        assertEquals(2, celebration?.level)
        assertFalse(celebration!!.fullDay)
    }

    @Test
    fun anOrdinaryMarkOrAnUnmarkCelebratesNothing() {
        val some = mapOf(day to setOf(PrayerType.FAJR, PrayerType.DHUHR))
        val more = mapOf(day to setOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR))
        assertNull(PrayerLogProgress.celebration(some, more, day, day))
        assertNull(PrayerLogProgress.celebration(mapOf(day to all), more, day, day))
    }
}
