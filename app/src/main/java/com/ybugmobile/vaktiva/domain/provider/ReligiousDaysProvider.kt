package com.ybugmobile.vaktiva.domain.provider

import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.HijriData
import com.ybugmobile.vaktiva.domain.model.ReligiousDay

object ReligiousDaysProvider {
    /**
     * List of religious days with their fixed Hijri dates.
     */
    private val religiousDays = listOf(
        ReligiousDay(7, 1, R.string.rel_day_3_months),
        ReligiousDay(7, 1, R.string.rel_day_regaip), // Note: Regaip is first Friday of Rajab, but usually shown at start
        ReligiousDay(7, 27, R.string.rel_day_mirac),
        ReligiousDay(8, 15, R.string.rel_day_berat),
        ReligiousDay(8, 30, R.string.rel_day_first_tarawih),
        ReligiousDay(9, 1, R.string.rel_day_ramadan_start),
        ReligiousDay(9, 27, R.string.rel_day_kadir),
        ReligiousDay(9, 30, R.string.rel_day_eid_eve),
        ReligiousDay(10, 1, R.string.rel_day_ramadan_eid),
        ReligiousDay(10, 2, R.string.rel_day_ramadan_eid),
        ReligiousDay(10, 3, R.string.rel_day_ramadan_eid),
        ReligiousDay(12, 9, R.string.rel_day_eid_eve),
        ReligiousDay(12, 10, R.string.rel_day_sacrifice_eid),
        ReligiousDay(12, 11, R.string.rel_day_sacrifice_eid),
        ReligiousDay(12, 12, R.string.rel_day_sacrifice_eid),
        ReligiousDay(12, 13, R.string.rel_day_sacrifice_eid),
        ReligiousDay(1, 1, R.string.rel_day_hijri_new_year),
        ReligiousDay(1, 10, R.string.rel_day_ashura),
        ReligiousDay(3, 12, R.string.rel_day_mawlid)
    )

    /**
     * Returns the religious day for a given Hijri date if one exists.
     */
    fun getReligiousDay(hijriData: HijriData?): ReligiousDay? {
        if (hijriData == null) return null
        return religiousDays.find { 
            it.month == hijriData.monthNumber && it.day == hijriData.day 
        }
    }
}
