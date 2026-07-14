package com.ybugmobile.waktiva.data.local.diyanet

import org.shredzone.commons.suncalc.SunPosition
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.LinkedHashMap
import kotlin.math.roundToLong

/**
 * Prayer-night based astronomy kernel for the V12 reconstruction.
 *
 * A night is owned by its evening date and spans that day's prayer Maghrib to the
 * following prayer Sunrise.  Twilight roots are searched on the physical night axis,
 * not in fixed civil half-days.  This prevents 23:xx/00:xx roots from being silently
 * assigned to the wrong prayer date.
 *
 * This class only reports astronomical events.  It deliberately contains no Diyanet
 * high-latitude regime or interpolation decisions.
 */
class DiyanetNightAstronomyKernel {

    private val nightCache = BoundedCache<NightKey, DiyanetNightEvents>(2048)

    fun events(
        eveningDate: LocalDate,
        prayerMaghrib: ZonedDateTime,
        nextPrayerSunrise: ZonedDateTime,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetNightEvents {
        require(nextPrayerSunrise.toInstant().isAfter(prayerMaghrib.toInstant())) {
            "Prayer sunrise must be after prayer maghrib: $prayerMaghrib -> $nextPrayerSunrise"
        }

        val key = NightKey(
            candidateVersion = KERNEL_VERSION,
            eveningDate = eveningDate,
            maghribEpochSecond = prayerMaghrib.toEpochSecond(),
            maghribNano = prayerMaghrib.nano,
            sunriseEpochSecond = nextPrayerSunrise.toEpochSecond(),
            sunriseNano = nextPrayerSunrise.nano,
            latitudeE6 = (location.latitude * 1_000_000.0).roundToLong(),
            longitudeE6 = (location.longitude * 1_000_000.0).roundToLong(),
            zoneId = location.zoneId.id,
            elevationDecimeters = (location.calculationElevationMeters * 10.0).roundToLong(),
            fajrAngleTenths = (profile.fajrAngle * 10.0).roundToLong().toInt(),
            ishaAngleTenths = (profile.ishaAngle * 10.0).roundToLong().toInt()
        )

        return nightCache.getOrPut(key) {
            computeEvents(
                eveningDate = eveningDate,
                prayerMaghrib = prayerMaghrib,
                nextPrayerSunrise = nextPrayerSunrise,
                location = location,
                profile = profile
            )
        }
    }

    private fun computeEvents(
        eveningDate: LocalDate,
        prayerMaghrib: ZonedDateTime,
        nextPrayerSunrise: ZonedDateTime,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetNightEvents {
        val physicalStart = prayerMaghrib.toInstant()
        val physicalEnd = nextPrayerSunrise.toInstant()
        val scanStart = physicalStart.minus(ROOT_WINDOW_PADDING)
        val scanEnd = physicalEnd.plus(ROOT_WINDOW_PADDING)

        val ishaRoots = findTwilightRoots(
            start = scanStart,
            end = scanEnd,
            location = location,
            angle = profile.ishaAngle,
            direction = RootDirection.DESCENDING
        ).filter { it >= physicalStart && it <= physicalEnd }

        val fajrRoots = findTwilightRoots(
            start = scanStart,
            end = scanEnd,
            location = location,
            angle = profile.fajrAngle,
            direction = RootDirection.RISING
        ).filter { it >= physicalStart && it <= physicalEnd }

        val minimum = minimumSolarAltitude(
            start = physicalStart,
            end = physicalEnd,
            location = location
        )

        return DiyanetNightEvents(
            eveningDate = eveningDate,
            prayerMaghrib = prayerMaghrib,
            nextPrayerSunrise = nextPrayerSunrise,
            descendingIshaRoots = ishaRoots.map { ZonedDateTime.ofInstant(it, location.zoneId) },
            risingFajrRoots = fajrRoots.map { ZonedDateTime.ofInstant(it, location.zoneId) },
            selectedIsha = ishaRoots.firstOrNull()?.let { ZonedDateTime.ofInstant(it, location.zoneId) },
            selectedNextFajr = fajrRoots.lastOrNull()?.let { ZonedDateTime.ofInstant(it, location.zoneId) },
            minimumSolarAltitudeDegrees = minimum.altitudeDegrees,
            minimumSolarAltitudeAt = ZonedDateTime.ofInstant(minimum.instant, location.zoneId)
        )
    }

    private fun findTwilightRoots(
        start: Instant,
        end: Instant,
        location: PrayerLocation,
        angle: Double,
        direction: RootDirection
    ): List<Instant> {
        if (!end.isAfter(start)) return emptyList()

        val roots = ArrayList<Instant>(2)
        var previousInstant = start
        var previousValue = twilightValue(previousInstant, location, angle)
        var currentInstant = minInstant(start.plus(ROOT_SCAN_STEP), end)

        while (currentInstant.isAfter(previousInstant)) {
            val currentValue = twilightValue(currentInstant, location, angle)
            if (crossed(previousValue, currentValue, direction)) {
                val root = refineTwilightRoot(
                    lowerBound = previousInstant,
                    upperBound = currentInstant,
                    location = location,
                    angle = angle,
                    direction = direction
                )
                if (roots.lastOrNull()?.let { Duration.between(it, root).abs() > ROOT_DEDUPLICATION } != false) {
                    roots += root
                }
            }

            if (currentInstant == end) break
            previousInstant = currentInstant
            previousValue = currentValue
            currentInstant = minInstant(currentInstant.plus(ROOT_SCAN_STEP), end)
        }

        return roots
    }

    private fun refineTwilightRoot(
        lowerBound: Instant,
        upperBound: Instant,
        location: PrayerLocation,
        angle: Double,
        direction: RootDirection
    ): Instant {
        var lower = lowerBound
        var upper = upperBound

        repeat(ROOT_BINARY_SEARCH_ITERATIONS) {
            val midpoint = midpoint(lower, upper)
            val middleValue = twilightValue(midpoint, location, angle)
            when (direction) {
                RootDirection.RISING -> {
                    if (middleValue > 0.0) upper = midpoint else lower = midpoint
                }
                RootDirection.DESCENDING -> {
                    if (middleValue <= 0.0) upper = midpoint else lower = midpoint
                }
            }
        }
        return upper
    }

    private fun minimumSolarAltitude(
        start: Instant,
        end: Instant,
        location: PrayerLocation
    ): SolarMinimum {
        var bestInstant = start
        var bestAltitude = trueSolarAltitude(start, location)
        var cursor = minInstant(start.plus(MINIMUM_SCAN_STEP), end)

        while (true) {
            val altitude = trueSolarAltitude(cursor, location)
            if (altitude < bestAltitude) {
                bestAltitude = altitude
                bestInstant = cursor
            }
            if (cursor == end) break
            cursor = minInstant(cursor.plus(MINIMUM_SCAN_STEP), end)
        }

        var left = maxInstant(start, bestInstant.minus(MINIMUM_SCAN_STEP))
        var right = minInstant(end, bestInstant.plus(MINIMUM_SCAN_STEP))
        repeat(MINIMUM_REFINEMENT_ITERATIONS) {
            val span = Duration.between(left, right)
            val oneThird = span.dividedBy(3)
            val first = left.plus(oneThird)
            val second = right.minus(oneThird)
            if (trueSolarAltitude(first, location) <= trueSolarAltitude(second, location)) {
                right = second
            } else {
                left = first
            }
        }

        val refined = midpoint(left, right)
        return SolarMinimum(
            instant = refined,
            altitudeDegrees = trueSolarAltitude(refined, location)
        )
    }

    private fun twilightValue(
        instant: Instant,
        location: PrayerLocation,
        angle: Double
    ): Double = trueSolarAltitude(instant, location) + angle

    private fun trueSolarAltitude(
        instant: Instant,
        location: PrayerLocation
    ): Double = SunPosition.compute()
        .timezone(location.zoneId)
        .on(instant)
        .at(location.latitude, location.longitude)
        .elevation(location.calculationElevationMeters)
        .execute()
        .trueAltitude

    private fun crossed(
        previousValue: Double,
        currentValue: Double,
        direction: RootDirection
    ): Boolean = when (direction) {
        RootDirection.RISING -> previousValue <= 0.0 && currentValue > 0.0
        RootDirection.DESCENDING -> previousValue > 0.0 && currentValue <= 0.0
    }

    private fun midpoint(left: Instant, right: Instant): Instant =
        left.plus(Duration.between(left, right).dividedBy(2))

    private fun minInstant(left: Instant, right: Instant): Instant = if (left <= right) left else right
    private fun maxInstant(left: Instant, right: Instant): Instant = if (left >= right) left else right

    private enum class RootDirection { RISING, DESCENDING }

    private data class SolarMinimum(
        val instant: Instant,
        val altitudeDegrees: Double
    )

    private data class NightKey(
        val candidateVersion: String,
        val eveningDate: LocalDate,
        val maghribEpochSecond: Long,
        val maghribNano: Int,
        val sunriseEpochSecond: Long,
        val sunriseNano: Int,
        val latitudeE6: Long,
        val longitudeE6: Long,
        val zoneId: String,
        val elevationDecimeters: Long,
        val fajrAngleTenths: Int,
        val ishaAngleTenths: Int
    )

    private class BoundedCache<K, V>(private val maxSize: Int) {
        private val values = object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > maxSize
        }

        @Synchronized
        fun getOrPut(key: K, producer: () -> V): V {
            values[key]?.let { return it }
            return producer().also { values[key] = it }
        }
    }

    companion object {
        const val KERNEL_VERSION = "diyanet_prayer_night_kernel_v12"
        private val ROOT_SCAN_STEP: Duration = Duration.ofMinutes(5)
        private val ROOT_WINDOW_PADDING: Duration = Duration.ofHours(2)
        private val ROOT_DEDUPLICATION: Duration = Duration.ofSeconds(30)
        private const val ROOT_BINARY_SEARCH_ITERATIONS = 28
        private val MINIMUM_SCAN_STEP: Duration = Duration.ofMinutes(10)
        private const val MINIMUM_REFINEMENT_ITERATIONS = 24
    }
}

data class DiyanetNightEvents(
    val eveningDate: LocalDate,
    val prayerMaghrib: ZonedDateTime,
    val nextPrayerSunrise: ZonedDateTime,
    val descendingIshaRoots: List<ZonedDateTime>,
    val risingFajrRoots: List<ZonedDateTime>,
    val selectedIsha: ZonedDateTime?,
    val selectedNextFajr: ZonedDateTime?,
    val minimumSolarAltitudeDegrees: Double,
    val minimumSolarAltitudeAt: ZonedDateTime
) {
    val prayerDate: LocalDate
        get() = eveningDate.plusDays(1)

    fun fajrRootBelongsToPreviousCivilDate(): Boolean =
        selectedNextFajr?.toLocalDate()?.isBefore(prayerDate) == true
}

