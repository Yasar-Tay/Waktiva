package com.ybugmobile.waktiva.data.local.diyanet

import org.shredzone.commons.suncalc.SunPosition
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.roundToLong

class DiyanetAstronomyKernel {

    private val rawEventCache = BoundedCache<RawEventCacheKey, DiyanetRawEvents>(768)
    private val fiveHourBoundsCache = BoundedCache<FiveHourBoundsCacheKey, Boolean>(96)
    private val solsticeBoundsCache = BoundedCache<SolsticeBoundsCacheKey, DiyanetSolsticeBounds>(96)

    fun rawEvents(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetRawEvents {
        val key = RawEventCacheKey(
            candidateVersion = ADAPTIVE_DIYANET_CANDIDATE_VERSION,
            latitudeKey = normalizeCoordinate(location.latitude),
            longitudeKey = normalizeCoordinate(location.longitude),
            zoneId = location.zoneId.id,
            date = date,
            calculationElevationKey = normalizeCoordinate(location.calculationElevationMeters),
            fajrAngleTenthDegrees = (profile.fajrAngle * 10.0).roundToLong().toInt(),
            ishaAngleTenthDegrees = (profile.ishaAngle * 10.0).roundToLong().toInt(),
            rootStepMinutes = ROOT_SCAN_STEP.toMinutes().toInt(),
            rootIterations = ROOT_BINARY_SEARCH_ITERATIONS,
            astronomyKernelVersion = DIYANET_ASTRONOMY_KERNEL_VERSION
        )
        return rawEventCache.getOrPut(key) {
            computeRawEvents(date, location, profile)
        }
    }

    fun usesFiveHourBounds(
        year: Int,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): Boolean {
        val key = FiveHourBoundsCacheKey(
            candidateVersion = ADAPTIVE_DIYANET_CANDIDATE_VERSION,
            latitudeKey = normalizeCoordinate(location.latitude),
            longitudeKey = normalizeCoordinate(location.longitude),
            zoneId = location.zoneId.id,
            calculationElevationKey = normalizeCoordinate(location.calculationElevationMeters),
            profileId = profile.profileId,
            year = year
        )
        return fiveHourBoundsCache.getOrPut(key) {
            computeUsesFiveHourBounds(year, location, profile)
        }
    }

    fun prayerAxis(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        useFiveHourBounds: Boolean
    ): DiyanetPrayerAxis {
        val rawEvents = rawEvents(date, location, profile)
        if (!useFiveHourBounds) {
            return DiyanetPrayerAxis(
                prayerSunrise = rawEvents.prayerSunrise,
                prayerMaghrib = rawEvents.prayerMaghrib
            )
        }

        val bounds = runCatching { solsticeBounds(date.year, location) }
            .getOrElse {
                return DiyanetPrayerAxis(
                    prayerSunrise = rawEvents.prayerSunrise,
                    prayerMaghrib = rawEvents.prayerMaghrib,
                    phase = "solstice_bounds_unavailable"
                )
            }

        val zoneId = location.zoneId
        val useSummerBound = closerToSummer(date, bounds)
        val phases = mutableListOf<String>()

        val prayerSunrise = if (rawEvents.prayerSunrise == null) {
            phases += "sunrise_missing"
            zonedDateTimeFromSeconds(
                day = date,
                seconds = if (useSummerBound) bounds.summerPrayerSunriseSeconds else bounds.winterPrayerSunriseSeconds,
                zoneId = zoneId
            )
        } else {
            val seconds = civilSeconds(rawEvents.prayerSunrise)
            val bounded = seconds.coerceIn(bounds.summerPrayerSunriseSeconds, bounds.winterPrayerSunriseSeconds)
            if (abs(bounded - seconds) > 1.0) {
                phases += "sunrise_bounded"
                zonedDateTimeFromSeconds(day = date, seconds = bounded, zoneId = zoneId)
            } else {
                rawEvents.prayerSunrise
            }
        }

        val prayerMaghrib = if (rawEvents.prayerMaghrib == null) {
            phases += "maghrib_missing"
            zonedDateTimeFromSeconds(
                day = date,
                seconds = if (useSummerBound) bounds.summerPrayerMaghribSeconds else bounds.winterPrayerMaghribSeconds,
                zoneId = zoneId
            )
        } else {
            var seconds = civilSeconds(rawEvents.prayerMaghrib, unwrapEvening = true)
            val lower = bounds.winterPrayerMaghribSeconds
            var upper = bounds.summerPrayerMaghribSeconds
            while (upper < lower) {
                upper += SECONDS_PER_DAY
            }
            while (seconds < lower - HALF_DAY_SECONDS) {
                seconds += SECONDS_PER_DAY
            }
            while (seconds > upper + HALF_DAY_SECONDS) {
                seconds -= SECONDS_PER_DAY
            }
            val bounded = seconds.coerceIn(lower, upper)
            if (abs(bounded - seconds) > 1.0) {
                phases += "maghrib_bounded"
                zonedDateTimeFromSeconds(day = date, seconds = bounded, zoneId = zoneId)
            } else {
                rawEvents.prayerMaghrib
            }
        }

        return DiyanetPrayerAxis(
            prayerSunrise = prayerSunrise,
            prayerMaghrib = prayerMaghrib,
            phase = phases.takeIf { it.isNotEmpty() }?.joinToString("+")
        )
    }

    private fun computeRawEvents(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetRawEvents {
        val zoneId = location.zoneId
        val sunTimes = computeSunTimes(date, location)

        val astronomicalSunrise = sunTimes.rise
        val astronomicalSunset = sunTimes.set
        val astronomicalNoon = sunTimes.noon

        val prayerSunrise = astronomicalSunrise?.minusMinutes(PRAYER_SUNRISE_OFFSET_MINUTES)
        val prayerMaghrib = astronomicalSunset?.plusMinutes(PRAYER_MAGHRIB_OFFSET_MINUTES)
        val prayerNoon = astronomicalNoon?.plusMinutes(PRAYER_DHUHR_OFFSET_MINUTES)

        val morningStart = date.atStartOfDay(zoneId)
        val morningEnd = date.atTime(12, 0).atZone(zoneId)
        val eveningStart = morningEnd
        val eveningEnd = date.plusDays(1).atStartOfDay(zoneId)

        val directFajr = findTwilightRoot(
            start = morningStart,
            end = morningEnd,
            location = location,
            angle = profile.fajrAngle,
            rising = true
        )
        val directIsha = findTwilightRoot(
            start = eveningStart,
            end = eveningEnd,
            location = location,
            angle = profile.ishaAngle,
            rising = false
        )

        return DiyanetRawEvents(
            date = date,
            astronomicalSunrise = astronomicalSunrise,
            astronomicalSunset = astronomicalSunset,
            astronomicalNoon = astronomicalNoon,
            prayerSunrise = prayerSunrise,
            prayerMaghrib = prayerMaghrib,
            prayerNoon = prayerNoon,
            directFajr = directFajr,
            directIsha = directIsha
        )
    }

    private fun computeUsesFiveHourBounds(
        year: Int,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): Boolean {
        val (summerSolstice, winterSolstice) = solsticeDates(year, location.latitude)
        val dayLengths = mutableListOf<Double>()

        for (date in listOf(summerSolstice, winterSolstice)) {
            val rawEvents = rawEvents(date, location, profile)
            val prayerSunrise = rawEvents.prayerSunrise ?: return true
            val prayerMaghrib = rawEvents.prayerMaghrib ?: return true
            var normalizedMaghrib = prayerMaghrib
            while (!normalizedMaghrib.isAfter(prayerSunrise)) {
                normalizedMaghrib = normalizedMaghrib.plusDays(1)
            }
            dayLengths += Duration.between(prayerSunrise.toInstant(), normalizedMaghrib.toInstant()).toMinutesDouble()
        }

        val shortestDay = dayLengths.minOrNull() ?: return false
        val shortestNight = MINUTES_PER_DAY - (dayLengths.maxOrNull() ?: return false)
        return shortestDay < FIVE_HOUR_TRIGGER_MINUTES || shortestNight < FIVE_HOUR_TRIGGER_MINUTES
    }

    private fun solsticeBounds(
        year: Int,
        location: PrayerLocation
    ): DiyanetSolsticeBounds {
        val key = SolsticeBoundsCacheKey(
            candidateVersion = ADAPTIVE_DIYANET_CANDIDATE_VERSION,
            latitudeKey = normalizeCoordinate(location.latitude),
            longitudeKey = normalizeCoordinate(location.longitude),
            zoneId = location.zoneId.id,
            calculationElevationKey = normalizeCoordinate(location.calculationElevationMeters),
            year = year
        )
        return solsticeBoundsCache.getOrPut(key) {
            computeSolsticeBounds(year, location)
        }
    }

    private fun computeSolsticeBounds(
        year: Int,
        location: PrayerLocation
    ): DiyanetSolsticeBounds {
        val (summerAnchor, winterAnchor) = solsticeDates(year, location.latitude)
        val summerPrayerNoon = computeSunTimes(summerAnchor, location).noon
            ?.plusMinutes(PRAYER_DHUHR_OFFSET_MINUTES)
            ?: error("Missing solar noon for $summerAnchor")
        val winterPrayerNoon = computeSunTimes(winterAnchor, location).noon
            ?.plusMinutes(PRAYER_DHUHR_OFFSET_MINUTES)
            ?: error("Missing solar noon for $winterAnchor")

        return DiyanetSolsticeBounds(
            summerAnchor = summerAnchor,
            winterAnchor = winterAnchor,
            summerPrayerSunriseSeconds = civilSeconds(summerPrayerNoon) - SUMMER_BOUND_OFFSET_SECONDS,
            summerPrayerMaghribSeconds = civilSeconds(summerPrayerNoon, unwrapEvening = true) + SUMMER_BOUND_OFFSET_SECONDS,
            winterPrayerSunriseSeconds = civilSeconds(winterPrayerNoon) - WINTER_BOUND_OFFSET_SECONDS,
            winterPrayerMaghribSeconds = civilSeconds(winterPrayerNoon, unwrapEvening = true) + WINTER_BOUND_OFFSET_SECONDS
        )
    }

    private fun computeSunTimes(
        date: LocalDate,
        location: PrayerLocation
    ): SunTimes {
        return SunTimes.compute()
            .timezone(location.zoneId)
            .on(date)
            .at(location.latitude, location.longitude)
            .elevation(location.calculationElevationMeters)
            .oneDay()
            .execute()
    }

    private fun solsticeDates(year: Int, latitude: Double): Pair<LocalDate, LocalDate> {
        return if (latitude >= 0.0) {
            LocalDate.of(year, 6, 21) to LocalDate.of(year, 12, 21)
        } else {
            LocalDate.of(year, 12, 21) to LocalDate.of(year, 6, 21)
        }
    }

    private fun closerToSummer(
        target: LocalDate,
        bounds: DiyanetSolsticeBounds
    ): Boolean {
        val cycleDays = target.lengthOfYear().toLong()
        val summerDistance = cyclicDistanceDays(target, bounds.summerAnchor, cycleDays)
        val winterDistance = cyclicDistanceDays(target, bounds.winterAnchor, cycleDays)
        return summerDistance <= winterDistance
    }

    private fun cyclicDistanceDays(
        left: LocalDate,
        right: LocalDate,
        cycleDays: Long
    ): Long {
        val distance = abs(ChronoUnit.DAYS.between(left, right))
        return minOf(distance, cycleDays - minOf(distance, cycleDays))
    }

    private fun findTwilightRoot(
        start: ZonedDateTime,
        end: ZonedDateTime,
        location: PrayerLocation,
        angle: Double,
        rising: Boolean
    ): ZonedDateTime? {
        val endInstant = end.toInstant()
        var previousInstant = start.toInstant()
        var previousValue = twilightValue(previousInstant, location, angle)
        var currentInstant = previousInstant.plus(ROOT_SCAN_STEP)

        while (!currentInstant.isAfter(endInstant)) {
            val currentValue = twilightValue(currentInstant, location, angle)
            if (crossed(previousValue, currentValue, rising)) {
                return refineTwilightRoot(previousInstant, currentInstant, location, angle, rising)
            }
            previousInstant = currentInstant
            previousValue = currentValue
            currentInstant = currentInstant.plus(ROOT_SCAN_STEP)
        }

        if (previousInstant != endInstant) {
            val endValue = twilightValue(endInstant, location, angle)
            if (crossed(previousValue, endValue, rising)) {
                return refineTwilightRoot(previousInstant, endInstant, location, angle, rising)
            }
        }

        return null
    }

    private fun refineTwilightRoot(
        lowerBound: Instant,
        upperBound: Instant,
        location: PrayerLocation,
        angle: Double,
        rising: Boolean
    ): ZonedDateTime {
        var lower = lowerBound
        var upper = upperBound

        repeat(ROOT_BINARY_SEARCH_ITERATIONS) {
            val midpoint = lower.plus(Duration.between(lower, upper).dividedBy(2))
            val middleValue = twilightValue(midpoint, location, angle)
            if (rising) {
                if (middleValue > 0.0) {
                    upper = midpoint
                } else {
                    lower = midpoint
                }
            } else {
                if (middleValue <= 0.0) {
                    upper = midpoint
                } else {
                    lower = midpoint
                }
            }
        }

        return ZonedDateTime.ofInstant(upper, location.zoneId)
    }

    private fun twilightValue(
        instant: Instant,
        location: PrayerLocation,
        angle: Double
    ): Double {
        return trueSolarElevation(instant, location) + angle
    }

    private fun trueSolarElevation(
        instant: Instant,
        location: PrayerLocation
    ): Double {
        return SunPosition.compute()
            .timezone(location.zoneId)
            .on(instant)
            .at(location.latitude, location.longitude)
            .elevation(location.calculationElevationMeters)
            .execute()
            .trueAltitude
    }

    private fun crossed(previousValue: Double, currentValue: Double, rising: Boolean): Boolean {
        return if (rising) {
            previousValue <= 0.0 && currentValue > 0.0
        } else {
            previousValue > 0.0 && currentValue <= 0.0
        }
    }

    private fun normalizeCoordinate(value: Double): Long {
        return (value * 1_000_000.0).roundToLong()
    }

    private data class RawEventCacheKey(
        val candidateVersion: String,
        val latitudeKey: Long,
        val longitudeKey: Long,
        val zoneId: String,
        val date: LocalDate,
        val calculationElevationKey: Long,
        val fajrAngleTenthDegrees: Int,
        val ishaAngleTenthDegrees: Int,
        val rootStepMinutes: Int,
        val rootIterations: Int,
        val astronomyKernelVersion: String
    )

    private data class FiveHourBoundsCacheKey(
        val candidateVersion: String,
        val latitudeKey: Long,
        val longitudeKey: Long,
        val zoneId: String,
        val calculationElevationKey: Long,
        val profileId: String,
        val year: Int
    )

    private data class SolsticeBoundsCacheKey(
        val candidateVersion: String,
        val latitudeKey: Long,
        val longitudeKey: Long,
        val zoneId: String,
        val calculationElevationKey: Long,
        val year: Int
    )

    private data class DiyanetSolsticeBounds(
        val summerAnchor: LocalDate,
        val winterAnchor: LocalDate,
        val summerPrayerSunriseSeconds: Double,
        val summerPrayerMaghribSeconds: Double,
        val winterPrayerSunriseSeconds: Double,
        val winterPrayerMaghribSeconds: Double
    )

    private class BoundedCache<K, V>(
        private val maxSize: Int
    ) {
        private val delegate = object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }

        @Synchronized
        fun getOrPut(key: K, producer: () -> V): V {
            val existing = delegate[key]
            if (existing != null) {
                return existing
            }

            val computed = producer()
            delegate[key] = computed
            return computed
        }
    }

    private companion object {
        val ROOT_SCAN_STEP: Duration = Duration.ofMinutes(5)
        const val ROOT_BINARY_SEARCH_ITERATIONS: Int = 28
        const val FIVE_HOUR_TRIGGER_MINUTES: Double = 295.0
        const val MINUTES_PER_DAY: Double = 1440.0
        const val SECONDS_PER_DAY: Double = 24.0 * 3600.0
        const val HALF_DAY_SECONDS: Double = 12.0 * 3600.0
        const val SUMMER_BOUND_OFFSET_SECONDS: Double = 570.0 * 60.0
        const val WINTER_BOUND_OFFSET_SECONDS: Double = 150.0 * 60.0
    }
}
