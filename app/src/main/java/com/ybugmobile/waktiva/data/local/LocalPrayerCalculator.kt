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
import kotlin.math.abs

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
            // Turkey (Diyanet) base adjustments (MWL 18°/17° + minute offsets)
            params.adjustments.fajr = 0
            params.adjustments.sunrise = -7
            params.adjustments.dhuhr = 5
            params.adjustments.asr = 4
            params.adjustments.maghrib = 7

            val absLat = abs(latitude)
            if (absLat in 45.0..55.0) {
                // Mid-high-latitude correction (45–55°N): Diyanet progressively reduces
                // the Fajr and Isha angles above 45°N instead of using standard 18°/17°.
                // Formulas reverse-engineered from Diyanet's published times for Basel,
                // Zurich, London and Berlin vs. pure angle-based calculation:
                //   Fajr : max(11°, 18° − 0.67 × (|lat| − 45°))
                //   Isha : max(11°, 17° − 0.80 × (|lat| − 45°))
                params.fajrAngle = maxOf(11.0, 18.0 - 0.67 * (absLat - 45.0))
                params.ishaAngle = maxOf(11.0, 17.0 - 0.80 * (absLat - 45.0))
                // No flat minute adjustments — the angles already encode the correction.
            } else if (absLat > 55.0) {
                // Above 55°N the Aladhan API is called with latitudeAdjustmentMethod=2
                // (1/7 of night), so the local calculator is not used for post-processing.
                // Keep standard MWL angles; this path is only reached in the offline
                // fallback where we do our best with what we have.
                params.fajrAngle = maxOf(11.0, 18.0 - 0.67 * (absLat - 45.0))
                params.ishaAngle = maxOf(11.0, 17.0 - 0.80 * (absLat - 45.0))
            } else {
                params.adjustments.isha = 2
            }
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
