package com.ybugmobile.vaktiva.domain.provider

import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.ReligiousDay
import java.time.LocalDate

object ReligiousDaysProvider {
    private val days2026 = listOf(
        ReligiousDay(LocalDate.of(2026, 1, 15), R.string.rel_day_mirac),

        ReligiousDay(LocalDate.of(2026, 2, 2), R.string.rel_day_berat),
        ReligiousDay(LocalDate.of(2026, 2, 18), R.string.rel_day_first_tarawih),
        ReligiousDay(LocalDate.of(2026, 2, 19), R.string.rel_day_ramadan_start),


        ReligiousDay(LocalDate.of(2026, 3, 16), R.string.rel_day_kadir),
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

        ReligiousDay(LocalDate.of(2026, 12, 10), R.string.rel_day_3_months),
        ReligiousDay(LocalDate.of(2026, 12, 10), R.string.rel_day_berat)
    )

    fun getReligiousDay(date: LocalDate): ReligiousDay? {
        return days2026.find { it.date == date }
    }
}
