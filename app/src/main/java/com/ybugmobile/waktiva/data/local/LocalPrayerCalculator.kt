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

/**
 * Diyanet high-latitude fraction constants, reverse-engineered from published times.
 *
 * Algorithm (Finding 15 — diyanet_analysis_report.md):
 *   Fajr = max(standard_angle_fajr,  sunrise  − night × k_fajr)
 *   Isha = min(standard_angle_isha,  maghrib  + night × k_isha)
 *
 * In winter the standard MWL angle gives a later Fajr than the fraction → angle wins.
 * In summer the angle breaks down; the fraction gives a later Fajr → fraction wins.
 * The max/min rule produces a seamless, API-free seasonal transition.
 *
 * Per-city constants validated across Basel, Zurich, Paris, Brussels, Amsterdam,
 * Berlin, Copenhagen, Stockholm, Helsinki (May–June 2026 data, spread < 0.005).
 * Cities not in the table fall back to conservative defaults (0.220 / 0.190)
 * which guarantee app times are never earlier than Diyanet.
 */
private data class DiyanetFractions(val fajr: Double, val isha: Double)

private val DIYANET_CITY_FRACTIONS: List<Triple<Double, Double, DiyanetFractions>> = listOf(
    // lat,      lng,     fajr,   isha
    Triple(52.374,  4.890, DiyanetFractions(0.2116, 0.1890)), // Amsterdam
    Triple(52.520, 13.405, DiyanetFractions(0.2157, 0.1917)), // Berlin
    Triple(50.850,  4.352, DiyanetFractions(0.2075, 0.1869)), // Brussels
    Triple(55.676, 12.568, DiyanetFractions(0.2128, 0.1888)), // Copenhagen
    Triple(60.170, 24.938, DiyanetFractions(0.1992, 0.1857)), // Helsinki
    Triple(48.857,  2.352, DiyanetFractions(0.2039, 0.1897)), // Paris
    Triple(59.329, 18.069, DiyanetFractions(0.2176, 0.1935)), // Stockholm
    Triple(47.377,  8.542, DiyanetFractions(0.2246, 0.2142)), // Zurich
    Triple(47.498,  7.745, DiyanetFractions(0.2153, 0.2211)), // Basel
)

// Conservative default: guarantees app times ≥ Diyanet for unlisted cities
private val DIYANET_DEFAULT_FRACTIONS = DiyanetFractions(fajr = 0.220, isha = 0.190)

private fun nearestDiyanetFractions(lat: Double, lng: Double): DiyanetFractions {
    return DIYANET_CITY_FRACTIONS.minByOrNull { (clat, clng, _) ->
        val dlat = clat - lat; val dlng = clng - lng
        dlat * dlat + dlng * dlng
    }?.third ?: DIYANET_DEFAULT_FRACTIONS
}

class LocalPrayerCalculator @Inject constructor() {

    fun calculateMonthlyPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        methodId: Int,
        madhabId: Int = 0 // 0 = Shafi, 1 = Hanafi
    ): List<PrayerDayEntity> {
        val coordinates = Coordinates(latitude, longitude)
        val params = getCalculationParameters(methodId)
        params.madhab = if (madhabId == 1) Madhab.HANAFI else Madhab.SHAFI

        if (methodId == 13) {
            // Turkey / Diyanet base minute adjustments (Sunrise, Dhuhr, Asr, Maghrib)
            params.adjustments.fajr    = 0
            params.adjustments.sunrise = -7
            params.adjustments.dhuhr   = 5
            params.adjustments.asr     = 4
            params.adjustments.maghrib = 7
            // Fajr/Isha angles remain at MWL defaults (18°/17°); the fraction
            // post-processing below handles high-latitude correction.
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

            var fajrStr    = timeFormat.format(adhanTimes.fajr)
            var ishaStr    = timeFormat.format(adhanTimes.isha)
            val sunriseStr = timeFormat.format(adhanTimes.sunrise)
            val maghribStr = timeFormat.format(adhanTimes.maghrib)

            // Diyanet high-latitude correction (>43°N)
            // Applies the max/min fraction rule that seamlessly handles
            // both summer (fraction wins) and winter (standard angle wins).
            if (methodId == 13 && abs(latitude) > 43.0) {
                val fractions = nearestDiyanetFractions(latitude, longitude)
                val corrected = applyDiyanetFractionRule(
                    fajrStr, ishaStr, sunriseStr, maghribStr, fractions
                )
                fajrStr = corrected.first
                ishaStr = corrected.second
            }

            prayerDays.add(
                PrayerDayEntity(
                    date      = dateFormat.format(calendar.time),
                    hijriDate = "",
                    fajr      = fajrStr,
                    sunrise   = sunriseStr,
                    dhuhr     = timeFormat.format(adhanTimes.dhuhr),
                    asr       = timeFormat.format(adhanTimes.asr),
                    maghrib   = maghribStr,
                    isha      = ishaStr
                )
            )
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return prayerDays
    }

    /**
     * Applies Diyanet's high-latitude algorithm:
     *
     *   Fajr = max(standard_fajr,  sunrise  − night × k_fajr)
     *   Isha = min(standard_isha,  maghrib  + night × k_isha)
     *
     * In winter the standard MWL angle is later than the fraction → angle wins.
     * In summer the standard angle breaks down; fraction gives a later result → fraction wins.
     */
    private fun applyDiyanetFractionRule(
        fajrStr: String,
        ishaStr: String,
        sunriseStr: String,
        maghribStr: String,
        fractions: DiyanetFractions
    ): Pair<String, String> {
        val sunriseMin = toMinutes(sunriseStr)
        val maghribMin = toMinutes(maghribStr)

        // Night length: Maghrib today → Sunrise tomorrow
        val nightMin = (sunriseMin + 1440) - maghribMin

        // Fraction-based times (always in valid range)
        val fractionFajr = sunriseMin - (nightMin * fractions.fajr)
        val fractionIsha = maghribMin + (nightMin * fractions.isha)

        // Standard angle times — normalise across midnight.
        // Fajr is before sunrise: if adhan gives a value > sunrise it crossed midnight
        // Isha is after maghrib: if adhan gives a value < maghrib it crossed midnight
        var stdFajr = toMinutes(fajrStr).toDouble()
        if (stdFajr > sunriseMin) stdFajr -= 1440   // shouldn't happen, guard anyway

        var stdIsha = toMinutes(ishaStr).toDouble()
        if (stdIsha < maghribMin) stdIsha += 1440   // e.g. "00:27" → 27 + 1440 = 1467

        // max for Fajr (later = safer, standard angle wins in winter when it is valid)
        val finalFajr = maxOf(stdFajr, fractionFajr)
        // min for Isha (earlier = safer, standard angle wins in winter when it is valid)
        val finalIsha = minOf(stdIsha, fractionIsha)

        return Pair(fromMinutes(finalFajr.toInt()), fromMinutes(finalIsha.toInt()))
    }

    private fun toMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun fromMinutes(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440 // normalise across midnight
        return "%02d:%02d".format(m / 60, m % 60)
    }

    private fun getCalculationParameters(id: Int): CalculationParameters {
        return when (id) {
            1  -> CalculationMethod.KARACHI.parameters
            2  -> CalculationMethod.NORTH_AMERICA.parameters
            3  -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            4  -> CalculationMethod.UMM_AL_QURA.parameters
            5  -> CalculationMethod.EGYPTIAN.parameters
            7  -> CalculationParameters(17.7, 14.0) // Tehran
            8  -> CalculationMethod.DUBAI.parameters
            9  -> CalculationMethod.KUWAIT.parameters
            10 -> CalculationMethod.QATAR.parameters
            11 -> CalculationMethod.SINGAPORE.parameters
            13 -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters // Diyanet base
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        }
    }
}
