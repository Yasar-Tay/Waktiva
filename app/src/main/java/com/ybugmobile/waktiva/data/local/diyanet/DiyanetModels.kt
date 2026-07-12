package com.ybugmobile.waktiva.data.local.diyanet

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

const val ADAPTIVE_DIYANET_CANDIDATE_VERSION = "adaptive_full_year_waktiva_v4"
const val DIYANET_ASTRONOMY_KERNEL_VERSION = "waktiva_suncalc_true_altitude_v1"
const val PRAYER_SUNRISE_OFFSET_MINUTES = 7L
const val PRAYER_DHUHR_OFFSET_MINUTES = 5L
const val PRAYER_MAGHRIB_OFFSET_MINUTES = 7L

data class PrayerLocation(
    val latitude: Double,
    val longitude: Double,
    val zoneId: ZoneId,
    val calculationElevationMeters: Double = 0.0
)

data class DiyanetCriteriaProfile(
    val profileId: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val highLatitude: Boolean
)

enum class DiyanetRegime {
    DIRECT_ANGLES,
    SOLSTICE_ONE_THIRD_GRADUAL,
    ROBUST_MISSING_FAJR_FULL_YEAR
}

enum class DiyanetConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNSUPPORTED
}

data class DiyanetDateRange(
    val start: LocalDate,
    val end: LocalDate
) {
    val lengthDays: Int
        get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    operator fun contains(date: LocalDate): Boolean {
        return !date.isBefore(start) && !date.isAfter(end)
    }
}

data class DiyanetRawEvents(
    val date: LocalDate,
    val astronomicalSunrise: ZonedDateTime?,
    val astronomicalSunset: ZonedDateTime?,
    val astronomicalNoon: ZonedDateTime?,
    val prayerSunrise: ZonedDateTime?,
    val prayerMaghrib: ZonedDateTime?,
    val prayerNoon: ZonedDateTime?,
    val directFajr: ZonedDateTime?,
    val directIsha: ZonedDateTime?
)

data class DiyanetPrayerAxis(
    val prayerSunrise: ZonedDateTime?,
    val prayerMaghrib: ZonedDateTime?,
    val phase: String? = null
)

data class DiyanetEstimatedTimes(
    val fajr: ZonedDateTime?,
    val isha: ZonedDateTime?,
    val previousUrfiRawMinutes: Double? = null,
    val currentUrfiRawMinutes: Double? = null,
    val previousUrfiEffectiveMinutes: Double? = null,
    val currentUrfiEffectiveMinutes: Double? = null,
    val previousBoundPhase: String? = null,
    val currentBoundPhase: String? = null,
    val nextBoundPhase: String? = null,
    val durationClock: String? = null
)

data class DiyanetCalculationResult(
    val fajr: ZonedDateTime?,
    val isha: ZonedDateTime?,
    val regime: DiyanetRegime,
    val confidence: DiyanetConfidence,
    val diagnostics: DiyanetDiagnostics
)

data class DiyanetDiagnostics(
    val candidateVersion: String = ADAPTIVE_DIYANET_CANDIDATE_VERSION,
    val profileId: String,
    val zoneId: String,
    val zoneSource: String,
    val calculationElevationM: Double,
    val anchor: LocalDate? = null,
    val fajrAngle: Double? = null,
    val ishaAngle: Double? = null,
    val anchorPrayerSunrise: ZonedDateTime? = null,
    val anchorPrayerMaghrib: ZonedDateTime? = null,
    val anchorNextTrueFajr: ZonedDateTime? = null,
    val anchorShariNightMinutes: Double? = null,
    val anchorOneThirdMinutes: Double? = null,
    val anchorEstimatedFajr: ZonedDateTime? = null,
    val anchorEstimatedIsha: ZonedDateTime? = null,
    val regime: DiyanetRegime,
    val adaptiveShoulderRegime: String? = null,
    val usesFiveHourBounds: Boolean? = null,
    val firstMissing: LocalDate? = null,
    val lastMissing: LocalDate? = null,
    val ishaFirstMissing: LocalDate? = null,
    val ishaLastMissing: LocalDate? = null,
    val delayedIshaAutumnTransition: Boolean? = null,
    val springReferenceDay: LocalDate? = null,
    val summerRatio: Double? = null,
    val ratioSource: String? = null,
    val minimumNightMinutes: Int? = null,
    val previousUrfiRawMinutes: Double? = null,
    val currentUrfiRawMinutes: Double? = null,
    val previousUrfiEffectiveMinutes: Double? = null,
    val currentUrfiEffectiveMinutes: Double? = null,
    val springFajrMarginMinutes: Double? = null,
    val springIshaMarginMinutes: Double? = null,
    val autumnFajrMarginMinutes: Double? = null,
    val autumnIshaMarginMinutes: Double? = null,
    val fajrTransitionStart: LocalDate? = null,
    val fajrTransitionEnd: LocalDate? = null,
    val ishaTransitionStart: LocalDate? = null,
    val ishaTransitionEnd: LocalDate? = null,
    val phase: String? = null,
    val transitionCurve: String? = null,
    val directFajr: ZonedDateTime? = null,
    val directIsha: ZonedDateTime? = null,
    val estimatedFajr: ZonedDateTime? = null,
    val estimatedIsha: ZonedDateTime? = null,
    val previousBoundPhase: String? = null,
    val currentBoundPhase: String? = null,
    val nextBoundPhase: String? = null,
    val durationClock: String? = null,
    val fallbackReason: String? = null
)

data class DiyanetAnnualProfile(
    val profileId: String,
    val anchor: LocalDate,
    val regime: DiyanetRegime,
    val usesFiveHourBounds: Boolean = false,
    val dominantMissingRun: DiyanetDateRange? = null,
    val dominantMissingIshaRun: DiyanetDateRange? = null,
    val delayedIshaAutumnTransition: Boolean = false,
    val adaptiveShoulderRegime: String? = null,
    val springReferenceDay: LocalDate? = null,
    val summerRatio: Double? = null,
    val ratioSource: String? = null,
    val minimumNightMinutes: Int = 0,
    val springFajrMarginMinutes: Double? = null,
    val springIshaMarginMinutes: Double? = null,
    val autumnFajrMarginMinutes: Double? = null,
    val autumnIshaMarginMinutes: Double? = null,
    val fajrTransitionStart: LocalDate? = null,
    val fajrTransitionEnd: LocalDate? = null,
    val ishaTransitionStart: LocalDate? = null,
    val ishaTransitionEnd: LocalDate? = null,
    val solsticeOneThird: Duration? = null,
    val solsticeFajrDuration: Duration? = null,
    val solsticeIshaDuration: Duration? = null,
    val anchorPrayerSunrise: ZonedDateTime? = null,
    val anchorPrayerMaghrib: ZonedDateTime? = null,
    val anchorNextTrueFajr: ZonedDateTime? = null,
    val anchorEstimatedFajr: ZonedDateTime? = null,
    val anchorEstimatedIsha: ZonedDateTime? = null
)

fun resolveDiyanetProfile(latitude: Double): DiyanetCriteriaProfile {
    return if (abs(latitude) <= 43.0) {
        DiyanetCriteriaProfile(
            profileId = "waktiva_diyanet_direct_18_17_v1",
            fajrAngle = 18.0,
            ishaAngle = 17.0,
            highLatitude = false
        )
    } else {
        DiyanetCriteriaProfile(
            profileId = "waktiva_diyanet_adaptive_18_16_v1",
            fajrAngle = 18.0,
            ishaAngle = 16.0,
            highLatitude = true
        )
    }
}

fun roundForDisplay(dateTime: ZonedDateTime): ZonedDateTime {
    return dateTime.plusSeconds(30).withSecond(0).withNano(0)
}

fun civilSeconds(value: ZonedDateTime, unwrapEvening: Boolean = false): Double {
    var seconds = (
        value.hour * 3600.0 +
            value.minute * 60.0 +
            value.second +
            value.nano / 1_000_000_000.0
        )
    if (unwrapEvening && value.hour < 12) {
        seconds += 24.0 * 3600.0
    }
    return seconds
}

fun zonedDateTimeFromSeconds(day: LocalDate, seconds: Double, zoneId: ZoneId): ZonedDateTime {
    val secondsPerDay = 24.0 * 3600.0
    val dayOffset = kotlin.math.floor(seconds / secondsPerDay).toLong()
    val remainder = seconds - dayOffset * secondsPerDay
    return day
        .plusDays(dayOffset)
        .atStartOfDay(zoneId)
        .plusNanos((remainder * 1_000_000_000.0).roundToLong())
}

fun Duration.toMinutesDouble(): Double {
    return seconds / 60.0 + nano / 60_000_000_000.0
}

fun minutesAsDuration(minutes: Double): Duration {
    return Duration.ofNanos((minutes * 60.0 * 1_000_000_000.0).roundToLong())
}
