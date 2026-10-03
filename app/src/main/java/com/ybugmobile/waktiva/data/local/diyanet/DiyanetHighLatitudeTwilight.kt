package com.ybugmobile.waktiva.data.local.diyanet

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * Fajr and Isha from 45°N up to where the sun still rises and sets every day, the way Diyanet's
 * tables give them through the summer, on the sun of [DiyanetClassicAlmanac].
 *
 * The tables follow the angles (18° and 16°) until a summer target comes within reach, move to it
 * along a straight line in clock time, hold a constant share of the night through the summer and
 * come back the same way:
 *
 * - **Reference night.** The shortest night on which the sun still gets 18° below the horizon:
 *   the eve of the first night it doesn't, or the solstice where it always does (below about
 *   48.5°N). How far that night's 18° Fajr falls after midnight, Δ, sets the summer.
 * - **Summer share.** Fajr is a share of the night before sunrise, Isha a share after Maghrib
 *   (both measured from the temkin'd times): Fajr 0.1866 + 0.379·Δ/N, Isha (1 + 2Δ/N)/6, N the
 *   reference night. It holds from the first night without 18° to the last, or is the single
 *   point of the solstice.
 * - **Short nights.** When a night is shorter than about five and a half hours the shares are
 *   taken of a five-hour night centred on Dhuhr + 12 h, so Fajr and Isha stop following sunrise
 *   and sunset (north of about 59.5°N around the solstice).
 * - **Transitions.** Fajr leaves the 18° time on the last day it is still at least 20.5 minutes
 *   later than the summer target and moves to it in a straight line; Isha mirrors it with 16°, and
 *   both come back the same way in the autumn, which the tables compare in clock time.
 *
 * The 18° night is lost within a day of when this almanac loses it: the tables sometimes take the
 * night before as the reference, the more often the barely darker the last night (about half the
 * time below 0.01°), likely because their coordinates for a place differ from ours by a few km.
 * Around that edge the two candidate shares are blended by how likely each is.
 *
 * Found on the official 2026 tables (2,900 places from 45°N to 65.5°N) and checked on 2027, which
 * was not used: Fajr's mean error 0.78 → 0.26 minutes against V14, Isha's 0.68 → 0.22.
 */
object DiyanetHighLatitudeTwilight {

    fun day(date: LocalDate, location: PrayerLocation): DiyanetHighLatitudeDay? {
        if (location.latitude < MIN_LATITUDE) return null
        val year = profile(date.year, location) ?: return null
        val index = date.dayOfYear - 1
        return DiyanetHighLatitudeDay(
            fajr = DiyanetClassicAlmanac.atMinutes(date, year.fajr[index], location.zoneId),
            isha = DiyanetClassicAlmanac.atMinutes(date, year.isha[index], location.zoneId),
            regime = year.regime[index]
        )
    }

    private data class Key(val year: Int, val latitudeE6: Long, val longitudeE6: Long, val zoneId: String)

    private class YearProfile(val fajr: DoubleArray, val isha: DoubleArray, val regime: Array<DiyanetHighLatitudeRegime>)

    private val cache = object : LinkedHashMap<Key, YearProfile?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, YearProfile?>?) = size > CACHE_SIZE
    }

    private fun profile(year: Int, location: PrayerLocation): YearProfile? {
        val key = Key(year, (location.latitude * 1e6).roundToLong(), (location.longitude * 1e6).roundToLong(), location.zoneId.id)
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val computed = buildProfile(year, location.latitude, location.longitude, location.zoneId)
        synchronized(cache) { cache[key] = computed }
        return computed
    }

    /** Minutes UT after 0h UT of each date of [year]; null where a day has no sunrise or sunset. */
    private fun buildProfile(year: Int, latitude: Double, longitude: Double, zoneId: ZoneId): YearProfile? {
        val first = LocalDate.of(year, 1, 1)
        val days = first.lengthOfYear()
        // Index i + 1 is day i of the year; one day of padding at each end.
        val suns = Array(days + 2) { DiyanetClassicSun(first.plusDays(it - 1L), latitude, longitude) }
        fun sun(i: Int) = suns[i + 1]
        fun noon(i: Int) = sun(i).noonMinutes
        fun horizon(i: Int) = sun(i).hourAngleMinutes(DiyanetClassicAlmanac.HORIZON_DEPRESSION)
        if ((-1..days).any { horizon(it) == null }) return null

        fun sunrise(i: Int) = noon(i) - horizon(i)!!
        fun sunset(i: Int) = noon(i) + horizon(i)!!
        fun directFajr(i: Int) = sun(i).hourAngleMinutes(DiyanetClassicAlmanac.FAJR_ANGLE)?.let { noon(i) - it }
        fun directIsha(i: Int) = sun(i).hourAngleMinutes(ISHA_ANGLE)?.let { noon(i) + it }
        // The night between the temkin'd Maghrib and Sunrise, ending on day i and starting on day i.
        fun nightBefore(i: Int) = (sunrise(i) - TEMKIN) - (sunset(i - 1) - MINUTES_PER_DAY + TEMKIN)
        fun nightAfter(i: Int) = (sunrise(i + 1) + MINUTES_PER_DAY - TEMKIN) - (sunset(i) + TEMKIN)

        val fajr = DoubleArray(days) { directFajr(it) ?: Double.NaN }
        val isha = DoubleArray(days) { directIsha(it) ?: Double.NaN }
        val regime = Array(days) { DiyanetHighLatitudeRegime.DIRECT }

        val solstice = LocalDate.of(year, 6, 21).dayOfYear - 1
        val firstLost = (0..solstice).firstOrNull { directFajr(it) == null }
        val summerStart: Int
        val summerEnd: Int
        val references: List<Pair<Int, Double>>
        if (firstLost != null) {
            summerStart = firstLost
            summerEnd = (solstice until days).firstOrNull { directFajr(it) != null }?.minus(1) ?: return null
            val reference = firstLost - 1
            val lastDepth = -FAJR_DEPRESSION - sun(reference).minimumAltitude
            val dayFraction = lastDepth / (sun(firstLost).minimumAltitude - sun(reference).minimumAltitude)
            val earlierWeight = 1.0 / (1.0 + exp(-(EDGE_CENTRE - dayFraction) / EDGE_WIDTH))
            references = listOf(reference to 1.0 - earlierWeight, reference - 1 to earlierWeight)
        } else {
            summerStart = solstice
            summerEnd = solstice
            references = listOf(solstice to 1.0)
        }

        fun share(base: Double, slope: Double) = references.sumOf { (day, weight) ->
            val afterMidnight = directFajr(day)!! - (noon(day) - MINUTES_PER_DAY / 2)
            weight * (base + slope * afterMidnight / nightBefore(day))
        }
        val fajrShare = share(FAJR_SHARE_BASE, FAJR_SHARE_SLOPE)
        val ishaShare = share(ISHA_SHARE_BASE, ISHA_SHARE_SLOPE)

        // Diyanet's midnight is Dhuhr + 12 h, Dhuhr's temkin included.
        fun summerFajr(i: Int) = maxOf(
            sunrise(i) - TEMKIN - fajrShare * nightBefore(i),
            noon(i) - MINUTES_PER_DAY / 2 + DiyanetClassicAlmanac.DHUHR_TEMKIN + SHORT_NIGHT / 2 - fajrShare * SHORT_NIGHT
        )
        fun summerIsha(i: Int) = minOf(
            sunset(i) + TEMKIN + ishaShare * nightAfter(i),
            noon(i) + MINUTES_PER_DAY / 2 + DiyanetClassicAlmanac.DHUHR_TEMKIN - SHORT_NIGHT / 2 + ishaShare * SHORT_NIGHT
        )

        for (i in summerStart..summerEnd) {
            fajr[i] = summerFajr(i)
            isha[i] = summerIsha(i)
            regime[i] = DiyanetHighLatitudeRegime.SUMMER
        }

        val fajrStart = summerFajr(summerStart)
        (summerStart - 1 downTo 0).firstOrNull { directFajr(it)!! >= fajrStart + TRANSITION_MINUTES }?.let { from ->
            for (i in from + 1 until summerStart) {
                fajr[i] = fajrStart + TRANSITION_MINUTES * (summerStart - i) / (summerStart - from)
                regime[i] = DiyanetHighLatitudeRegime.SPRING_TRANSITION
            }
        }
        // The tables compare the autumn times as clock times of day: where the sun's midnight comes
        // before the clock's (Kazan, Oskemen, Aihui), the first 18° Fajr after the summer falls
        // before midnight, reads as the later time and is taken at once, with no transition.
        fun clock(i: Int, minutesUt: Double): Double {
            val local = DiyanetClassicAlmanac.atMinutes(first.plusDays(i.toLong()), minutesUt, zoneId)
            return local.toLocalTime().toNanoOfDay() / 60_000_000_000.0
        }
        val fajrEnd = summerFajr(summerEnd)
        val autumnTarget = clock(summerEnd, fajrEnd) + TRANSITION_MINUTES
        (summerEnd + 1 until days).firstOrNull { i -> directFajr(i)?.let { clock(i, it) >= autumnTarget } == true }?.let { to ->
            for (i in summerEnd + 1 until to) {
                fajr[i] = fajrEnd + TRANSITION_MINUTES * (i - summerEnd) / (to - summerEnd)
                regime[i] = DiyanetHighLatitudeRegime.AUTUMN_TRANSITION
            }
        }
        val ishaStart = summerIsha(summerStart)
        (summerStart - 1 downTo 0).firstOrNull { directIsha(it)?.let { x -> x <= ishaStart - TRANSITION_MINUTES } == true }?.let { from ->
            for (i in from + 1 until summerStart) {
                isha[i] = ishaStart - TRANSITION_MINUTES * (summerStart - i) / (summerStart - from)
            }
        }
        val ishaEnd = summerIsha(summerEnd)
        (summerEnd + 1 until days).firstOrNull { directIsha(it)?.let { x -> x <= ishaEnd - TRANSITION_MINUTES } == true }?.let { to ->
            for (i in summerEnd + 1 until to) {
                isha[i] = ishaEnd - TRANSITION_MINUTES * (i - summerEnd) / (to - summerEnd)
            }
        }
        // A day the angle isn't reached but no rule above covers: the summer share.
        for (i in 0 until days) {
            if (fajr[i].isNaN()) fajr[i] = summerFajr(i)
            if (isha[i].isNaN()) isha[i] = summerIsha(i)
        }
        return YearProfile(fajr, isha, regime)
    }

    const val VERSION = "diyanet_high_latitude_reference_night_v1"
    const val MIN_LATITUDE = 45.0

    private const val FAJR_DEPRESSION = DiyanetClassicAlmanac.FAJR_ANGLE
    private const val ISHA_ANGLE = 16.0
    private const val TEMKIN = DiyanetClassicAlmanac.MAGHRIB_TEMKIN.toDouble()
    private const val MINUTES_PER_DAY = 1440.0

    // Fitted on 2026: Fajr's share of the night, against how far the reference night's 18° Fajr falls after midnight.
    private const val FAJR_SHARE_BASE = 0.18661
    private const val FAJR_SHARE_SLOPE = 0.37909
    // Isha's: (1 + 2Δ/N) / 6.
    private const val ISHA_SHARE_BASE = 1.0 / 6.0
    private const val ISHA_SHARE_SLOPE = 1.0 / 3.0
    private const val TRANSITION_MINUTES = 20.5
    private const val SHORT_NIGHT = 300.0
    // How likely the tables take the night before as the reference, by where in the day the 18°
    // night is lost: about half at 0.035 of a day after the reference night's 0h UT.
    private const val EDGE_CENTRE = 0.035
    private const val EDGE_WIDTH = 0.02
    private const val CACHE_SIZE = 16
}

enum class DiyanetHighLatitudeRegime { DIRECT, SPRING_TRANSITION, SUMMER, AUTUMN_TRANSITION }

data class DiyanetHighLatitudeDay(
    val fajr: ZonedDateTime,
    val isha: ZonedDateTime,
    val regime: DiyanetHighLatitudeRegime
)
