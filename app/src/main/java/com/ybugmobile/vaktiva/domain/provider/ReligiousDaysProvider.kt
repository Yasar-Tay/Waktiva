package com.ybugmobile.vaktiva.domain.provider

import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.ReligiousDay
import java.time.LocalDate

object ReligiousDaysProvider {
    private val days2025 = listOf(
        ReligiousDay(LocalDate.of(2025, 1, 1), R.string.rel_day_regaip),
        ReligiousDay(LocalDate.of(2025, 1, 26), R.string.rel_day_mirac),
        ReligiousDay(LocalDate.of(2025, 2, 13), R.string.rel_day_berat),
        ReligiousDay(LocalDate.of(2025, 3, 1), R.string.rel_day_ramadan_start),
        ReligiousDay(LocalDate.of(2025, 3, 26), R.string.rel_day_kadir),
        ReligiousDay(LocalDate.of(2025, 3, 29), R.string.rel_day_arefe),
        ReligiousDay(LocalDate.of(2025, 3, 30), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2025, 3, 31), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2025, 4, 1), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2025, 6, 5), R.string.rel_day_arefe),
        ReligiousDay(LocalDate.of(2025, 6, 6), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2025, 6, 7), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2025, 6, 8), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2025, 6, 9), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2025, 6, 26), R.string.rel_day_hijri_new_year),
        ReligiousDay(LocalDate.of(2025, 7, 5), R.string.rel_day_ashura),
        ReligiousDay(LocalDate.of(2025, 9, 3), R.string.rel_day_mawlid)
    )

    private val days2026 = listOf(
        ReligiousDay(LocalDate.of(2026, 1, 19), R.string.rel_day_3_months),
        ReligiousDay(LocalDate.of(2026, 1, 22), R.string.rel_day_regaip),
        ReligiousDay(LocalDate.of(2026, 2, 13), R.string.rel_day_mirac),
        ReligiousDay(LocalDate.of(2026, 3, 3), R.string.rel_day_berat),
        ReligiousDay(LocalDate.of(2026, 3, 18), R.string.rel_day_ramadan_start),
        ReligiousDay(LocalDate.of(2026, 4, 13), R.string.rel_day_kadir),
        ReligiousDay(LocalDate.of(2026, 4, 19), R.string.rel_day_arefe),
        ReligiousDay(LocalDate.of(2026, 4, 20), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2026, 4, 21), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2026, 4, 22), R.string.rel_day_ramadan_eid),
        ReligiousDay(LocalDate.of(2026, 5, 26), R.string.rel_day_arefe),
        ReligiousDay(LocalDate.of(2026, 5, 27), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2026, 5, 28), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2026, 5, 29), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2026, 5, 30), R.string.rel_day_sacrifice_eid),
        ReligiousDay(LocalDate.of(2026, 6, 16), R.string.rel_day_hijri_new_year),
        ReligiousDay(LocalDate.of(2026, 6, 25), R.string.rel_day_ashura),
        ReligiousDay(LocalDate.of(2026, 8, 25), R.string.rel_day_mawlid)
    )

    fun getReligiousDay(date: LocalDate): ReligiousDay? {
        val allDays = days2025 + days2026
        return allDays.find { it.date == date }
    }
}
