package com.ybugmobile.waktiva.domain.provider

import com.ybugmobile.waktiva.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReligiousDaysProviderTest {

    @Test
    fun eachDateHasOneName() {
        for (year in listOf(2026, 2027)) {
            val dates = ReligiousDaysProvider.getReligiousDays(year).map { it.date }
            assertEquals("year $year", dates.size, dates.toSet().size)
        }
    }

    @Test
    fun theYearsAreInDateOrder() {
        val days = ReligiousDaysProvider.getReligiousDays(2027)
        assertTrue(days.zipWithNext().all { (a, b) -> a.date < b.date })
    }

    @Test
    fun twentyTwentySevenFollowsDiyanet() {
        fun name(m: Int, d: Int) = ReligiousDaysProvider.getReligiousDay(LocalDate.of(2027, m, d))?.nameResId
        assertEquals(R.string.rel_day_ramadan_start, name(2, 8))
        assertEquals(R.string.rel_day_first_tarawih, name(2, 7))
        assertEquals(R.string.rel_day_ramadan_eid, name(3, 9))
        assertEquals(R.string.rel_day_sacrifice_eid, name(5, 19))
        assertEquals(R.string.rel_day_mawlid, name(8, 13))
        // Rajab 1449 begins before the year is out: its first Thursday night and its Mi'raj.
        assertEquals(R.string.rel_day_regaip, name(12, 2))
        assertEquals(R.string.rel_day_mirac, name(12, 24))
        assertNull(name(5, 20))
    }

    @Test
    fun aYearWithoutDataIsEmpty() {
        assertTrue(ReligiousDaysProvider.getReligiousDays(2028).isEmpty())
    }
}
