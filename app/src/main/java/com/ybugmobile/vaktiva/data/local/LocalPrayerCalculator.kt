package com.ybugmobile.vaktiva.data.local

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class LocalPrayerCalculator @Inject constructor() {

    fun calculateMonthlyPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        methodId: Int,
        madhabId: Int = 0 // 0 for Shafi, 1 for Hanafi
    ): List<PrayerDayEntity> {
        val coordinates = Coordinates(latitude, longitude)
        val method = getCalculationMethod(methodId)
        val madhab = if (madhabId == 1) Madhab.HANAFI else Madhab.SHAFI
        val params = method.parameters.apply {
            this.madhab = madhab
        }

        val prayerDays = mutableListOf<PrayerDayEntity>()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (day in 1..daysInMonth) {
            val dateComponents = DateComponents.from(calendar.time)
            val adhanTimes = PrayerTimes(coordinates, dateComponents, params)

            prayerDays.add(
                PrayerDayEntity(
                    date = dateFormat.format(calendar.time),
                    hijriDate = "", // Local calculation doesn't easily provide Hijri, will be empty or handled separately
                    fajr = timeFormat.format(adhanTimes.fajr),
                    sunrise = timeFormat.format(adhanTimes.sunrise),
                    dhuhr = timeFormat.format(adhanTimes.dhuhr),
                    asr = timeFormat.format(adhanTimes.asr),
                    maghrib = timeFormat.format(adhanTimes.maghrib),
                    isha = timeFormat.format(adhanTimes.isha)
                )
            )
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return prayerDays
    }

    private fun getCalculationMethod(id: Int): CalculationMethod {
        return when (id) {
            0 -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            1 -> CalculationMethod.EGYPTIAN
            2 -> CalculationMethod.KARACHI
            3 -> CalculationMethod.UMM_AL_QURA
            4 -> CalculationMethod.GULF
            5 -> CalculationMethod.MOON_SIGHTING_COMMITTEE
            6 -> CalculationMethod.NORTH_AMERICA
            7 -> CalculationMethod.KUWAIT
            8 -> CalculationMethod.QATAR
            9 -> CalculationMethod.SINGAPORE
            10 -> CalculationMethod.TURKEY
            else -> CalculationMethod.NORTH_AMERICA // Default to ISNA/NA
        }
    }
}
