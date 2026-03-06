package com.ybugmobile.waktiva.data.local

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
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
        val params = getCalculationParameters(methodId)
        val madhab = if (madhabId == 1) Madhab.HANAFI else Madhab.SHAFI
        params.madhab = madhab
        
        if (methodId == 13) {
            // Turkey (Diyanet) adjustments
            // Base is MWL (18°/17°) which matches Diyanet angles
            params.adjustments.fajr = 0
            params.adjustments.sunrise = -7
            params.adjustments.dhuhr = 5
            params.adjustments.asr = 4
            params.adjustments.maghrib = 7
            params.adjustments.isha = 2
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

    private fun getCalculationParameters(id: Int): CalculationParameters {
        return when (id) {
            1 -> CalculationMethod.KARACHI.parameters
            2 -> CalculationMethod.NORTH_AMERICA.parameters
            3 -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            4 -> CalculationMethod.UMM_AL_QURA.parameters
            5 -> CalculationMethod.EGYPTIAN.parameters
            7 -> CalculationParameters(17.7, 14.0) // Tehran (Institute of Geophysics)
            8 -> CalculationMethod.DUBAI.parameters
            9 -> CalculationMethod.KUWAIT.parameters
            10 -> CalculationMethod.QATAR.parameters
            11 -> CalculationMethod.SINGAPORE.parameters
            13 -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters // Turkey (Diyanet)
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        }
    }
}
