package com.ybugmobile.waktiva.data.local

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.ybugmobile.waktiva.data.local.diyanet.AdaptiveDiyanetCalculator
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
import com.ybugmobile.waktiva.data.local.diyanet.resolveDiyanetProfile
import com.ybugmobile.waktiva.data.local.diyanet.roundForDisplay
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject.Inject

class LocalPrayerCalculator @Inject constructor() {

    private val adaptiveDiyanetCalculator = AdaptiveDiyanetCalculator()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun calculateMonthlyPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        methodId: Int,
        madhabId: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<PrayerDayEntity> {
        val coordinates = Coordinates(latitude, longitude)
        val params = getCalculationParameters(methodId).apply {
            madhab = if (madhabId == 1) Madhab.HANAFI else Madhab.SHAFI
        }

        if (methodId == 13) {
            params.adjustments.fajr = 0
            params.adjustments.sunrise = -7
            params.adjustments.dhuhr = 5
            params.adjustments.asr = 4
            params.adjustments.maghrib = 7
        }

        val location = PrayerLocation(
            latitude = latitude,
            longitude = longitude,
            zoneId = zoneId
        )
        val diyanetProfile = if (methodId == 13) resolveDiyanetProfile(latitude) else null

        return (1..YearMonth.of(year, month).lengthOfMonth()).map { day ->
            val date = LocalDate.of(year, month, day)
            val adhanTimes = PrayerTimes(coordinates, DateComponents(year, month, day), params)

            val adaptiveResult = if (diyanetProfile != null) {
                adaptiveDiyanetCalculator.calculate(date, location, diyanetProfile)
            } else {
                null
            }

            val fajr = adaptiveResult?.fajr?.toAdaptiveTimeString()
                ?: adhanTimes.fajr.toTimeString(zoneId)
            val isha = adaptiveResult?.isha?.toAdaptiveTimeString()
                ?: adhanTimes.isha.toTimeString(zoneId)

            PrayerDayEntity(
                date = date.toString(),
                hijriDate = "",
                fajr = fajr,
                sunrise = adhanTimes.sunrise.toTimeString(zoneId),
                dhuhr = adhanTimes.dhuhr.toTimeString(zoneId),
                asr = adhanTimes.asr.toTimeString(zoneId),
                maghrib = adhanTimes.maghrib.toTimeString(zoneId),
                isha = isha
            )
        }
    }

    private fun Date.toTimeString(zoneId: ZoneId): String {
        return toInstant().atZone(zoneId).format(timeFormatter)
    }

    private fun ZonedDateTime.toAdaptiveTimeString(): String {
        return roundForDisplay(this).format(timeFormatter)
    }

    private fun getCalculationParameters(id: Int): CalculationParameters {
        return when (id) {
            1 -> CalculationMethod.KARACHI.parameters
            2 -> CalculationMethod.NORTH_AMERICA.parameters
            3 -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            4 -> CalculationMethod.UMM_AL_QURA.parameters
            5 -> CalculationMethod.EGYPTIAN.parameters
            7 -> CalculationParameters(17.7, 14.0)
            8 -> CalculationMethod.DUBAI.parameters
            9 -> CalculationMethod.KUWAIT.parameters
            10 -> CalculationMethod.QATAR.parameters
            11 -> CalculationMethod.SINGAPORE.parameters
            13 -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        }
    }
}
