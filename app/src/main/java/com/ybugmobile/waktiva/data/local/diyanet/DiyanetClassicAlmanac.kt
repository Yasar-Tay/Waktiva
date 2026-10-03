package com.ybugmobile.waktiva.data.local.diyanet

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The prayer times the way Diyanet's own tables compute them: the classic hour-angle formula with
 * the sun's declination and equation of time from the low-precision almanac, taken once for the
 * date at 0h UT and not refined to each event's moment, then Diyanet's temkin added.
 *
 * Against the official 2026 and 2027 tables this reproduces Sunrise, Dhuhr, Asr and Maghrib to
 * within a minute wherever the sun rises and sets, and Fajr and Isha wherever the sun reaches
 * their angles: Istanbul, Toronto and Sydney within a tenth of a minute on average, Asr in
 * Stockholm 0.1 against the precise ephemeris' 1.8. Taking the sun at 0h UT rather than at each
 * event is what Diyanet does, and it matters most where a time is sensitive to the declination:
 * around the dates an angle stops being reached, the published time lags the precise one by up
 * to a day's change. Nothing here is fitted: the angles and temkin are Diyanet's.
 *
 * Sunrise and Maghrib are the tables' prayer day ([prayerAxis]): never longer than 19 hours or
 * shorter than 5, so they exist on every day, polar ones included. Fajr, Asr and Isha are null
 * where the sun doesn't reach the altitude they need.
 */
object DiyanetClassicAlmanac {

    fun calculate(date: LocalDate, location: PrayerLocation): DiyanetClassicDay {
        val latitude = location.latitude
        val sun = DiyanetClassicSun(date, latitude, location.longitude)
        val noon = sun.noonMinutes

        fun at(minutesUt: Double?, temkinMinutes: Long): ZonedDateTime? = minutesUt?.let {
            atMinutes(date, it, location.zoneId).plusMinutes(temkinMinutes)
        }

        // Shafi Asr: an object's shadow is its noon shadow plus its own length. When the sun stays
        // below the horizon at noon there is no shadow to lengthen, and the tables give Dhuhr's
        // time (Tromso from 25 November to 18 January); they give the formula's time on every
        // other day, polar summer included. With the sun barely up at noon the formula's hour angle
        // is near zero and Asr's smaller temkin would put it before Dhuhr: the tables give Dhuhr
        // then too (Rovaniemi around the winter solstice).
        val dhuhr = requireNotNull(at(noon, DHUHR_TEMKIN))
        val noonAltitude = 90.0 - abs(latitude - sun.declination)
        val asr = if (noonAltitude <= 0.0) {
            dhuhr
        } else {
            val asrDepression = -deg(atan(1.0 / (1.0 + tan(rad(abs(latitude - sun.declination))))))
            at(sun.hourAngleMinutes(asrDepression)?.let { noon + it }, ASR_TEMKIN)?.let { maxOf(it, dhuhr) }
        }
        val (sunrise, maghrib) = prayerAxis(sun, date, latitude, location.longitude)
        return DiyanetClassicDay(
            fajr = at(sun.hourAngleMinutes(FAJR_ANGLE)?.let { noon - it }, FAJR_TEMKIN),
            sunrise = atMinutes(date, sunrise, location.zoneId),
            dhuhr = dhuhr,
            asr = asr,
            maghrib = atMinutes(date, maghrib, location.zoneId),
            isha = at(sun.hourAngleMinutes(ishaAngle(latitude))?.let { noon + it }, ISHA_TEMKIN)
        )
    }

    /**
     * Isha's angle: 17° up to 43°N and anywhere south (Istanbul, Sydney, Dunedin), 16° north of
     * 43°N (Toronto and the north).
     */
    fun ishaAngle(latitude: Double): Double = if (latitude > 43.0) 16.0 else 17.0

    /**
     * The tables' Sunrise and Maghrib, temkin included, in minutes UT after 0h UT of [date].
     *
     * The prayer day is the sun's, but never more than 9.5 hours either side of Dhuhr (its temkin
     * included), nor of the summer solstice's Dhuhr, and never less than 2.5 hours either side of
     * Dhuhr. So above ~59°N around midsummer Sunrise stays at its solstice clock time until the
     * solstice and then follows Dhuhr, and Maghrib the other way round (Umea 2026: Sunrise 01:16 UT
     * from 18 May to 24 June); on polar days and nights the bounds alone give the day. Against the
     * tables north of 58°N: 98 % of these days within a minute, where V14's axis was 1.2 to 1.7
     * minutes off on average. The nights between them are what Fajr and Isha's shares are taken of
     * ([DiyanetHighLatitudeTwilight]): 5 hours where both bounds hold.
     */
    internal fun prayerAxis(sun: DiyanetClassicSun, date: LocalDate, latitude: Double, longitude: Double): Pair<Double, Double> {
        val dhuhr = sun.noonMinutes + DHUHR_TEMKIN
        val solstice = if (latitude >= 0) {
            LocalDate.of(date.year, 6, 21)
        } else {
            LocalDate.of(if (date.monthValue <= 6) date.year - 1 else date.year, 12, 21)
        }
        val solsticeDhuhr = DiyanetClassicSun(solstice, latitude, longitude).noonMinutes + DHUHR_TEMKIN
        val horizon = sun.hourAngleMinutes(HORIZON_DEPRESSION)
        // Without a sunrise the sun is either up all day or down all day.
        val up = 90.0 - abs(latitude - sun.declination) > 0.0
        var sunrise = horizon?.let { sun.noonMinutes - it + SUNRISE_TEMKIN }
            ?: if (up) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        var maghrib = horizon?.let { sun.noonMinutes + it + MAGHRIB_TEMKIN }
            ?: if (up) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        sunrise = minOf(maxOf(sunrise, dhuhr - LONG_DAY_HALF, solsticeDhuhr - LONG_DAY_HALF), dhuhr - SHORT_DAY_HALF)
        maghrib = maxOf(minOf(maghrib, dhuhr + LONG_DAY_HALF, solsticeDhuhr + LONG_DAY_HALF), dhuhr + SHORT_DAY_HALF)
        return sunrise to maghrib
    }

    /** The instant [minutesUt] minutes after 0h UT of [date], in [zoneId]. */
    internal fun atMinutes(date: LocalDate, minutesUt: Double, zoneId: ZoneId): ZonedDateTime =
        date.atStartOfDay(ZoneOffset.UTC)
            .plusNanos((minutesUt * 60_000_000_000.0).toLong())
            .withZoneSameInstant(zoneId)

    /** Declination in degrees and equation of time in hours at Julian day [julianDay]. */
    internal fun sunAt(julianDay: Double): Pair<Double, Double> {
        val d = julianDay - J2000
        val meanAnomaly = 357.529 + 0.98560028 * d
        val meanLongitude = 280.459 + 0.98564736 * d
        val longitude = meanLongitude + 1.915 * sin(rad(meanAnomaly)) + 0.020 * sin(rad(2 * meanAnomaly))
        val obliquity = 23.439 - 0.00000036 * d
        val rightAscension = deg(atan2(cos(rad(obliquity)) * sin(rad(longitude)), cos(rad(longitude)))) / 15.0
        val declination = deg(asin(sin(rad(obliquity)) * sin(rad(longitude))))
        val equationOfTime = wrapHours(meanLongitude / 15.0 - wrap24(rightAscension))
        return declination to equationOfTime
    }

    private fun wrap24(hours: Double) = ((hours % 24.0) + 24.0) % 24.0
    private fun wrapHours(hours: Double) = wrap24(hours + 12.0) - 12.0
    internal fun rad(degrees: Double) = Math.toRadians(degrees)
    internal fun deg(radians: Double) = Math.toDegrees(radians)

    const val VERSION = "diyanet_classic_almanac_0h_ut_v2_prayer_day_bounds"
    const val FAJR_ANGLE = 18.0
    const val HORIZON_DEPRESSION = 0.833
    private const val J2000 = 2451545.0
    internal const val UNIX_EPOCH_JULIAN_DAY = 2440587.5

    private const val LONG_DAY_HALF = 570.0
    private const val SHORT_DAY_HALF = 150.0

    // Diyanet's temkin, in minutes.
    private const val FAJR_TEMKIN = 0L
    const val SUNRISE_TEMKIN = -7L
    const val DHUHR_TEMKIN = 5L
    private const val ASR_TEMKIN = 4L
    const val MAGHRIB_TEMKIN = 7L
    private const val ISHA_TEMKIN = 0L
}

/** The sun on [date] as [DiyanetClassicAlmanac] takes it: once, at 0h UT. Times are minutes UT after 0h UT of the date. */
internal class DiyanetClassicSun(date: LocalDate, private val latitude: Double, longitude: Double) {
    val declination: Double
    val noonMinutes: Double

    init {
        val (declination, equationOfTime) = DiyanetClassicAlmanac.sunAt(
            date.toEpochDay() + DiyanetClassicAlmanac.UNIX_EPOCH_JULIAN_DAY
        )
        this.declination = declination
        noonMinutes = (12.0 - equationOfTime - longitude / 15.0) * 60.0
    }

    /** Minutes from noon to the sun's crossing of [depressionDegrees] below the horizon, or null if it doesn't get there. */
    fun hourAngleMinutes(depressionDegrees: Double): Double? {
        val rad = DiyanetClassicAlmanac::rad
        val x = (-sin(rad(depressionDegrees)) - sin(rad(latitude)) * sin(rad(declination))) /
            (cos(rad(latitude)) * cos(rad(declination)))
        return if (x < -1.0 || x > 1.0) null else DiyanetClassicAlmanac.deg(acos(x)) * 4.0
    }

    /** The sun's altitude at lower culmination, in degrees, north of the equator. */
    val minimumAltitude: Double get() = latitude + declination - 90.0
}

data class DiyanetClassicDay(
    val fajr: ZonedDateTime?,
    val sunrise: ZonedDateTime?,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime?,
    val maghrib: ZonedDateTime?,
    val isha: ZonedDateTime?
)
