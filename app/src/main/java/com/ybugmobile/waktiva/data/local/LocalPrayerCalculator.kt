package com.ybugmobile.waktiva.data.local

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.ybugmobile.waktiva.data.local.diyanet.AdaptiveDiyanetCalculator
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetEngineVersions
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetProfiles
import com.ybugmobile.waktiva.data.local.diyanet.DiyanetReconstructionV14
import com.ybugmobile.waktiva.data.local.diyanet.PrayerLocation
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject.Inject

enum class DiyanetRoutingDiagnosticCode {
    SOUTHERN_HEMISPHERE_V14_DISABLED,
    NORMAL_DAY_ASR_FALLBACK
}

data class DiyanetRoutingDiagnostic(
    val code: DiyanetRoutingDiagnosticCode,
    val latitude: Double,
    val longitude: Double,
    val date: LocalDate? = null,
    val message: String
)

enum class DiyanetEngineRouting {
    V14,
    V9,
    ADHAN
}

enum class DiyanetAsrSource {
    DIRECT_ADHAN,
    REFERENCE_LATITUDE_62,
    DHUHR_MAGHRIB_MIDPOINT,
    POLAR_NIGHT_EQUALS_DHUHR,
    INVALID_OR_OTHER
}

/** Optional calculation trace for regression/export tooling; it does not affect output selection. */
data class DiyanetCalculationTrace(
    val date: LocalDate,
    val engineVersion: String,
    val routing: DiyanetEngineRouting,
    val regime: String,
    val diagnostic: String?,
    val asrSource: DiyanetAsrSource,
    val directFajrAvailable: Boolean?,
    val directIshaAvailable: Boolean?,
    val fajrState: String?,
    val ishaState: String?,
    val axisMode: String?,
    val polarNight: Boolean,
    val polarDay: Boolean,
    val utcOffsetSeconds: Int
)

class LocalPrayerCalculator @Inject constructor() {

    private val diyanetV14 = DiyanetReconstructionV14()
    private val adaptiveDiyanetCalculator = AdaptiveDiyanetCalculator()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun calculateMonthlyPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        methodId: Int,
        madhabId: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault(),
        diagnosticSink: (DiyanetRoutingDiagnostic) -> Unit = {},
        calculationTraceSink: (DiyanetCalculationTrace) -> Unit = {}
    ): List<PrayerDayEntity> {
        val coordinates = Coordinates(latitude, longitude)
        val params = getCalculationParameters(methodId).apply {
            madhab = if (methodId == DIYANET_METHOD_ID) {
                Madhab.SHAFI
            } else if (madhabId == 1) {
                Madhab.HANAFI
            } else {
                Madhab.SHAFI
            }
        }

        if (methodId == DIYANET_METHOD_ID) {
            // V9 remains the fallback outside V14's calibrated northern scope.
            // V14 overrides every field except İkindi in its northern scope.
            params.adjustments.fajr = 0
            params.adjustments.sunrise = -7
            params.adjustments.dhuhr = 5
            params.adjustments.asr = 4
            params.adjustments.maghrib = 7
            params.adjustments.isha = 0
        }

        val location = PrayerLocation(
            latitude = latitude,
            longitude = longitude,
            zoneId = zoneId,
            calculationElevationMeters = 0.0
        )
        val diyanetProfile = if (methodId == DIYANET_METHOD_ID) {
            DiyanetProfiles.resolve(latitude)
        } else {
            null
        }
        val useV14 = methodId == DIYANET_METHOD_ID &&
            latitude >= DiyanetReconstructionV14.MIN_ABS_LATITUDE

        if (
            methodId == DIYANET_METHOD_ID &&
            latitude <= -DiyanetReconstructionV14.MIN_ABS_LATITUDE
        ) {
            diagnosticSink(
                DiyanetRoutingDiagnostic(
                    code = DiyanetRoutingDiagnosticCode.SOUTHERN_HEMISPHERE_V14_DISABLED,
                    latitude = latitude,
                    longitude = longitude,
                    message = "V14 is disabled for southern high latitudes; using the existing V9 engine"
                )
            )
        }

        return (1..YearMonth.of(year, month).lengthOfMonth()).map { day ->
            val date = LocalDate.of(year, month, day)
            var dayDiagnostic = if (
                methodId == DIYANET_METHOD_ID &&
                latitude <= -DiyanetReconstructionV14.MIN_ABS_LATITUDE
            ) {
                DiyanetRoutingDiagnosticCode.SOUTHERN_HEMISPHERE_V14_DISABLED.name
            } else {
                null
            }
            val adhanTimes = PrayerTimes(
                coordinates,
                DateComponents(year, month, day),
                params
            )

            val reconstructed = if (useV14) {
                diyanetV14.calculate(date, location)
            } else {
                null
            }
            val adaptiveResult = if (diyanetProfile != null && !useV14) {
                adaptiveDiyanetCalculator.calculate(date, location, diyanetProfile)
            } else {
                null
            }
            val adaptiveAxis = if (diyanetProfile != null && !useV14) {
                adaptiveDiyanetCalculator.prayerAxis(date, location, diyanetProfile)
            } else {
                null
            }

            val fajr = reconstructed?.fajr?.toRoundedTimeString()
                ?: adaptiveResult?.fajr?.toRoundedTimeString()
                ?: adhanTimes.fajr.toTimeString(zoneId)
            val sunrise = reconstructed?.sunrise?.toRoundedTimeString()
                ?: adaptiveAxis?.prayerSunrise?.toRoundedTimeString()
                ?: adhanTimes.sunrise.toTimeString(zoneId)
            val dhuhr = reconstructed?.dhuhr?.toRoundedTimeString()
                ?: adaptiveAxis?.prayerNoon?.toRoundedTimeString()
                ?: adhanTimes.dhuhr.toTimeString(zoneId)
            val maghrib = reconstructed?.maghrib?.toRoundedTimeString()
                ?: adaptiveAxis?.prayerMaghrib?.toRoundedTimeString()
                ?: adhanTimes.maghrib.toTimeString(zoneId)
            val isha = reconstructed?.isha?.toRoundedTimeString()
                ?: adaptiveResult?.isha?.toRoundedTimeString()
                ?: adhanTimes.isha.toTimeString(zoneId)

            val (asr, asrSource) = when {
                reconstructed?.polarNight == true ->
                    dhuhr to DiyanetAsrSource.POLAR_NIGHT_EQUALS_DHUHR
                reconstructed != null || adaptiveAxis != null -> {
                    val directCandidate = validatedAsrTime(
                        candidate = adhanTimes.asr,
                        date = date,
                        zoneId = zoneId
                    )
                    val referenceCandidate = if (directCandidate == null) {
                        calculatePolarAsrFallback(
                            date = date,
                            latitude = latitude,
                            longitude = longitude,
                            params = params,
                            zoneId = zoneId
                        )
                    } else {
                        null
                    }
                    val candidate = directCandidate ?: referenceCandidate
                    val validCandidate = candidate?.takeIf { isClockBetween(it, dhuhr, maghrib) }
                    if (validCandidate != null) {
                        validCandidate to if (directCandidate != null) {
                            DiyanetAsrSource.DIRECT_ADHAN
                        } else {
                            DiyanetAsrSource.REFERENCE_LATITUDE_62
                        }
                    } else {
                        val midpoint = midpointClock(dhuhr, maghrib)
                        dayDiagnostic = appendDiagnostic(
                            dayDiagnostic,
                            DiyanetRoutingDiagnosticCode.NORMAL_DAY_ASR_FALLBACK.name
                        )
                        diagnosticSink(
                            DiyanetRoutingDiagnostic(
                                code = DiyanetRoutingDiagnosticCode.NORMAL_DAY_ASR_FALLBACK,
                                latitude = latitude,
                                longitude = longitude,
                                date = date,
                                message = "Invalid Asr was replaced with the Dhuhr-Maghrib midpoint"
                            )
                        )
                        midpoint to DiyanetAsrSource.DHUHR_MAGHRIB_MIDPOINT
                    }
                }
                else -> adhanTimes.asr.toTimeString(zoneId) to DiyanetAsrSource.DIRECT_ADHAN
            }

            val v14Diagnostics = reconstructed?.diagnostics
            val v9Diagnostics = adaptiveResult?.diagnostics
            val axisMode = v14Diagnostics?.axisMode ?: adaptiveAxis?.phase
            calculationTraceSink(
                DiyanetCalculationTrace(
                    date = date,
                    engineVersion = if (methodId == DIYANET_METHOD_ID) {
                        method13EngineVersion(latitude)
                    } else {
                        "adhan-java-1.1.0"
                    },
                    routing = when {
                        useV14 -> DiyanetEngineRouting.V14
                        methodId == DIYANET_METHOD_ID -> DiyanetEngineRouting.V9
                        else -> DiyanetEngineRouting.ADHAN
                    },
                    regime = v14Diagnostics?.let {
                        "${it.fajrState.name}/${it.ishaState.name}"
                    } ?: adaptiveResult?.regime?.name ?: "ADHAN",
                    diagnostic = dayDiagnostic,
                    asrSource = asrSource,
                    directFajrAvailable = v14Diagnostics?.let { it.fajrRoot != null }
                        ?: v9Diagnostics?.let { it.directFajr != null },
                    directIshaAvailable = v14Diagnostics?.let { it.ishaDirectGapMinutes != null }
                        ?: v9Diagnostics?.let { it.directIsha != null },
                    fajrState = v14Diagnostics?.fajrState?.name ?: v9Diagnostics?.phase,
                    ishaState = v14Diagnostics?.ishaState?.name ?: v9Diagnostics?.phase,
                    axisMode = axisMode,
                    polarNight = reconstructed?.polarNight == true ||
                        axisMode?.startsWith("polar_night") == true,
                    polarDay = v14Diagnostics?.polarState == "POLAR_DAY" ||
                        axisMode?.startsWith("polar_day") == true,
                    utcOffsetSeconds = reconstructed?.dhuhr?.offset?.totalSeconds
                        ?: adaptiveAxis?.prayerNoon?.offset?.totalSeconds
                        ?: date.atTime(12, 0).atZone(zoneId).offset.totalSeconds
                )
            )

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

    private fun Date.toTimeString(zoneId: ZoneId): String =
        toInstant().atZone(zoneId).format(timeFormatter)

    private fun ZonedDateTime.toRoundedTimeString(): String =
        plusSeconds(30).withSecond(0).withNano(0).format(timeFormatter)

    private fun calculatePolarAsrFallback(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        params: CalculationParameters,
        zoneId: ZoneId
    ): String? {
        val boundedLatitude = latitude.coerceIn(
            -DIYANET_POLAR_REFERENCE_LATITUDE,
            DIYANET_POLAR_REFERENCE_LATITUDE
        )
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

    private fun appendDiagnostic(existing: String?, next: String): String =
        listOfNotNull(existing, next).distinct().joinToString("|")

    private fun getCalculationParameters(id: Int): CalculationParameters = when (id) {
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
        DIYANET_METHOD_ID -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        else -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
    }

    companion object {
        fun method13EngineVersion(latitude: Double): String =
            if (latitude >= DiyanetReconstructionV14.MIN_ABS_LATITUDE) {
                DiyanetReconstructionV14.CANDIDATE_VERSION
            } else {
                DiyanetEngineVersions.ADAPTIVE_V9
            }

        const val DIYANET_METHOD_ID = 13
        const val DIYANET_POLAR_REFERENCE_LATITUDE = 62.0
        const val MINUTES_PER_DAY = 24 * 60
    }
}
