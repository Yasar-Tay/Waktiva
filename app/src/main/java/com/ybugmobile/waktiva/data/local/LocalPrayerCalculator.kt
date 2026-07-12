package com.ybugmobile.waktiva.data.local

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.ybugmobile.waktiva.data.local.diyanet.AdaptiveDiyanetCalculator
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetProfiles
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
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
        val diyanetProfile = if (methodId == 13) DiyanetProfiles.resolve(latitude) else null

        return (1..YearMonth.of(year, month).lengthOfMonth()).map { day ->
            val date = LocalDate.of(year, month, day)
            val adhanTimes = PrayerTimes(coordinates, DateComponents(year, month, day), params)

            val adaptiveResult = if (diyanetProfile != null) {
                adaptiveDiyanetCalculator.calculate(date, location, diyanetProfile)
            } else {
                null
            }
            val diyanetAxis = if (diyanetProfile != null) {
                adaptiveDiyanetCalculator.prayerAxis(date, location, diyanetProfile)
            } else {
                null
            }

            val fajr = adaptiveResult?.fajr?.toAdaptiveTimeString()
                ?: adhanTimes.fajr.toTimeString(zoneId)
            val isha = adaptiveResult?.isha?.toAdaptiveTimeString()
                ?: adhanTimes.isha.toTimeString(zoneId)

            val sunrise = diyanetAxis?.prayerSunrise?.toAdaptiveTimeString()
                ?: adhanTimes.sunrise.toTimeString(zoneId)
            val dhuhr = diyanetAxis?.prayerNoon?.toAdaptiveTimeString()
                ?: adhanTimes.dhuhr.toTimeString(zoneId)
            val maghrib = diyanetAxis?.prayerMaghrib?.toAdaptiveTimeString()
                ?: adhanTimes.maghrib.toTimeString(zoneId)
            val asr = if (diyanetAxis != null) {
                val candidate = validatedAsrTime(
                    candidate = adhanTimes.asr,
                    date = date,
                    zoneId = zoneId
                ) ?: calculatePolarAsrFallback(
                    date = date,
                    latitude = latitude,
                    longitude = longitude,
                    params = params,
                    zoneId = zoneId
                )
                candidate?.takeIf { isClockBetween(it, dhuhr, maghrib) }
                    ?: midpointClock(dhuhr, maghrib)
            } else {
                adhanTimes.asr.toTimeString(zoneId)
            }

            PrayerDayEntity(
                date = date.toString(),
                hijriDate = "",
                fajr = fajr,
                sunrise = sunrise,
                dhuhr = dhuhr,
                asr = asr,
                maghrib = maghrib,
                isha = isha
            )
        }
    }

    private fun Date.toTimeString(zoneId: ZoneId): String {
        return toInstant().atZone(zoneId).format(timeFormatter)
    }

    private fun ZonedDateTime.toAdaptiveTimeString(): String {
        return plusSeconds(30).withSecond(0).withNano(0).format(timeFormatter)
    }

    private fun calculatePolarAsrFallback(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        params: CalculationParameters,
        zoneId: ZoneId
    ): String? {
        val boundedLatitude = latitude.coerceIn(-DIYANET_POLAR_REFERENCE_LATITUDE, DIYANET_POLAR_REFERENCE_LATITUDE)
        if (boundedLatitude == latitude) return null

        val fallbackTimes = PrayerTimes(
            Coordinates(boundedLatitude, longitude),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            params
        )
        return validatedAsrTime(
            candidate = fallbackTimes.asr,
            date = date,
            zoneId = zoneId
        )
    }

    private fun validatedAsrTime(
        candidate: Date?,
        date: LocalDate,
        zoneId: ZoneId
    ): String? {
        val asr = candidate?.toInstant()?.atZone(zoneId) ?: return null
        if (asr.toLocalDate() != date) return null
        return asr.format(timeFormatter)
    }

    private fun isClockBetween(candidate: String, start: String, end: String): Boolean {
        val startMinutes = clockMinutes(start)
        var endMinutes = clockMinutes(end)
        var candidateMinutes = clockMinutes(candidate)
        while (endMinutes <= startMinutes) endMinutes += MINUTES_PER_DAY
        while (candidateMinutes <= startMinutes) candidateMinutes += MINUTES_PER_DAY
        return candidateMinutes < endMinutes
    }

    private fun midpointClock(start: String, end: String): String {
        val startMinutes = clockMinutes(start)
        var endMinutes = clockMinutes(end)
        while (endMinutes <= startMinutes) endMinutes += MINUTES_PER_DAY
        val midpoint = startMinutes + (endMinutes - startMinutes) / 2
        val wrappedMidpoint = midpoint % MINUTES_PER_DAY
        return "%02d:%02d".format(wrappedMidpoint / 60, wrappedMidpoint % 60)
    }

    private fun clockMinutes(value: String): Int {
        val (hour, minute) = value.split(':').map(String::toInt)
        return hour * 60 + minute
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

    private companion object {
        const val DIYANET_POLAR_REFERENCE_LATITUDE = 62.0
        const val MINUTES_PER_DAY = 24 * 60
    }
}
