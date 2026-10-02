package com.ybugmobile.waktiva.domain.provider

import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.ReligiousDay
import java.time.LocalDate

/**
 * The religious days as the Diyanet calendar dates them, one name per date. A night (kandil) is
 * listed on the day whose evening it falls in; the first tarawih on the eve of Ramadan.
 */
object ReligiousDaysProvider {
    private val days2026 = listOf(
        ReligiousDay(LocalDate.of(2026, 1, 15), R.string.rel_day_mirac),
        ReligiousDay(LocalDate.of(2026, 2, 2), R.string.rel_day_berat),
        ReligiousDay(LocalDate.of(2026, 2, 18), R.string.rel_day_first_tarawih),
        ReligiousDay(LocalDate.of(2026, 2, 19), R.string.rel_day_ramadan_start),
        ReligiousDay(LocalDate.of(2026, 3, 16), R.string.rel_day_kadir),
        ReligiousDay(LocalDate.of(2026, 3, 19), R.string.rel_day_eid_eve),
        ReligiousDay(LocalDate.of(2026, 3, 20), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2026, 3, 21), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2026, 3, 22), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2026, 5, 26), R.string.rel_day_eid_eve),
        ReligiousDay(LocalDate.of(2026, 5, 27), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2026, 5, 28), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2026, 5, 29), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2026, 5, 30), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2026, 6, 16), R.string.rel_day_hijri_new_year),
        ReligiousDay(LocalDate.of(2026, 6, 25), R.string.rel_day_ashura),
        ReligiousDay(LocalDate.of(2026, 8, 24), R.string.rel_day_mawlid),
        ReligiousDay(LocalDate.of(2026, 12, 10), R.string.rel_day_3_months)
    )

    // From Diyanet's list for 2027 (vakithesaplama.diyanet.gov.tr), which Al-Adhan's Diyanet Hijri
    // calendar agrees with day for day. The first tarawih is the last day of Sha'ban 1448.
    private val days2027 = listOf(
        ReligiousDay(LocalDate.of(2027, 1, 4), R.string.rel_day_mirac),
        ReligiousDay(LocalDate.of(2027, 1, 22), R.string.rel_day_berat),
        ReligiousDay(LocalDate.of(2027, 2, 7), R.string.rel_day_first_tarawih),
        ReligiousDay(LocalDate.of(2027, 2, 8), R.string.rel_day_ramadan_start),
        ReligiousDay(LocalDate.of(2027, 3, 5), R.string.rel_day_kadir),
        ReligiousDay(LocalDate.of(2027, 3, 8), R.string.rel_day_eid_eve),
        ReligiousDay(LocalDate.of(2027, 3, 9), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2027, 3, 10), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2027, 3, 11), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2027, 5, 15), R.string.rel_day_eid_eve),
        ReligiousDay(LocalDate.of(2027, 5, 16), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2027, 5, 17), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2027, 5, 18), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2027, 5, 19), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2027, 6, 6), R.string.rel_day_hijri_new_year),
        ReligiousDay(LocalDate.of(2027, 6, 15), R.string.rel_day_ashura),
        ReligiousDay(LocalDate.of(2027, 8, 13), R.string.rel_day_mawlid),
        ReligiousDay(LocalDate.of(2027, 11, 29), R.string.rel_day_3_months),
        ReligiousDay(LocalDate.of(2027, 12, 2), R.string.rel_day_regaip),
        ReligiousDay(LocalDate.of(2027, 12, 24), R.string.rel_day_mirac)
    )

    private val days = days2026 + days2027

    fun getReligiousDay(date: LocalDate): ReligiousDay? {
        return days.find { it.date == date }
    }

    /** The religious days of [year] in date order; empty for a year with no data. */
    fun getReligiousDays(year: Int): List<ReligiousDay> {
        return days.filter { it.date.year == year }.sortedBy { it.date }
    }
}
