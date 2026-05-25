package com.ybugmobile.waktiva

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Converts "HH:mm" to total minutes since midnight.
 */
private fun String.toMinutes(): Int {
    val (h, m) = split(":").map { it.toInt() }
    return h * 60 + m
}

class LocalPrayerCalculatorTest {

    private val calculator = LocalPrayerCalculator()

    @Test
    fun `calculateMonthlyPrayerTimes returns correct number of days`() {
        // February 2024 (Leap year)
        val prayerDays = calculator.calculateMonthlyPrayerTimes(
            year = 2024,
            month = 2,
            latitude = 41.0082, // Istanbul
            longitude = 28.9784,
            methodId = 0,
            madhabId = 0
        )
        
        assertEquals(29, prayerDays.size)
    }

    @Test
    fun `prayer times are in correct format HH mm`() {
        val prayerDays = calculator.calculateMonthlyPrayerTimes(
            year = 2024,
            month = 1,
            latitude = 51.5074, // London
            longitude = -0.1278,
            methodId = 2,
            madhabId = 0
        )
        
        val firstDay = prayerDays.first()
        val timeRegex = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")
        
        assertTrue("Fajr format incorrect: ${firstDay.fajr}", firstDay.fajr.matches(timeRegex))
        assertTrue("Sunrise format incorrect: ${firstDay.sunrise}", firstDay.sunrise.matches(timeRegex))
        assertTrue("Isha format incorrect: ${firstDay.isha}", firstDay.isha.matches(timeRegex))
    }

    // -------------------------------------------------------------------------
    // Diyanet fraction-based algorithm tests
    //
    // Reference values from Diyanet's official published times (ezanvakti API).
    // Algorithm: Fajr = max(standard_angle, sunrise − night × k_fajr)
    //            Isha = min(standard_angle, maghrib + night × k_isha)
    // Tolerance: ±10 min (fraction constants validated across May–June 2026)
    // -------------------------------------------------------------------------

    /**
     * Amsterdam (52.4°N) – May 15 2026
     * Diyanet: Fajr 04:00, Isha 23:03
     * Fraction constants: k_fajr=0.2116, k_isha=0.1890 → exact match expected
     */
    @Test
    fun `diyanet fraction algorithm Amsterdam May 15`() {
        val lat = 52.374; val lng = 4.890
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Amsterdam May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 04:00  Isha 23:03")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 04:00",
            kotlin.math.abs(day.fajr.toMinutes() - (4*60)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 23:03",
            kotlin.math.abs(day.isha.toMinutes() - (23*60+3)) <= 10)
    }

    /**
     * Berlin (52.5°N) – May 15 2026
     * Diyanet: Fajr 03:24, Isha 22:31
     */
    @Test
    fun `diyanet fraction algorithm Berlin May 15`() {
        val lat = 52.520; val lng = 13.405
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Berlin May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 03:24  Isha 22:31")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 03:24",
            kotlin.math.abs(day.fajr.toMinutes() - (3*60+24)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 22:31",
            kotlin.math.abs(day.isha.toMinutes() - (22*60+31)) <= 10)
    }

    /**
     * Paris (48.9°N) – May 15 2026
     * Diyanet: Fajr 04:21, Isha 23:08
     */
    @Test
    fun `diyanet fraction algorithm Paris May 15`() {
        val lat = 48.857; val lng = 2.352
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Paris May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 04:21  Isha 23:08")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 04:21",
            kotlin.math.abs(day.fajr.toMinutes() - (4*60+21)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 23:08",
            kotlin.math.abs(day.isha.toMinutes() - (23*60+8)) <= 10)
    }

    /**
     * Basel (47.5°N) – May 15 2026
     * Diyanet: Fajr 03:53, Isha 22:55
     */
    @Test
    fun `diyanet fraction algorithm Basel May 15`() {
        val lat = 47.498; val lng = 7.745
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Basel May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 03:53  Isha 22:55")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 03:53",
            kotlin.math.abs(day.fajr.toMinutes() - (3*60+53)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 22:55",
            kotlin.math.abs(day.isha.toMinutes() - (22*60+55)) <= 10)
    }

    /**
     * Winter sanity check: algorithm should NOT over-correct in winter.
     * Standard MWL angle works in December — fraction method must not fire.
     * Istanbul (41°N, below 43° threshold) — fraction not applied at all.
     */
    @Test
    fun `diyanet fraction does not over-correct in winter`() {
        val lat = 52.374; val lng = 4.890  // Amsterdam
        val days = calculator.calculateMonthlyPrayerTimes(2025, 12, lat, lng, methodId = 13)
        val day21 = days[20] // Dec 21
        println("Amsterdam Dec 21 — Fajr: ${day21.fajr}  Isha: ${day21.isha}")
        // In winter, standard MWL angle gives Fajr ~06:40, Isha ~18:20
        // Fraction would give Fajr ~05:21, Isha ~19:34
        // max/min rule: standard wins → Fajr should be ~06:30–07:00, Isha ~17:30–18:30
        assertTrue("Winter Fajr ${day21.fajr} should be after 06:00 (standard angle wins)",
            day21.fajr.toMinutes() >= 6*60)
        assertTrue("Winter Isha ${day21.isha} should be before 19:00 (standard angle wins)",
            day21.isha.toMinutes() <= 19*60)
    }

    /**
     * Calibration test — derives exact fraction constants for each reference city using
     * the Adhan Kotlin library's own sunrise/maghrib values (no minute adjustments).
     *
     * The current constants were computed against api.aladhan.com which uses slightly
     * different astronomical calculations than the Adhan library. This test back-calculates
     * the correct constants so the production formula is perfectly calibrated.
     *
     * Run this test, copy the printed constants into DIYANET_CITY_FRACTIONS.
     */
    @Test
    fun `calibrate Diyanet fraction constants against Adhan library`() {
        data class Ref(val year: Int, val month: Int, val day: Int, val fajrMin: Int, val ishaMin: Int)
        // tz: IANA timezone ID — must match the timezone Diyanet uses for each city's published times
        data class City(val name: String, val lat: Double, val lng: Double, val tz: String, val refs: List<Ref>)

        val cities = listOf(
            City("Amsterdam", 52.374,  4.890, "Europe/Amsterdam", listOf(
                Ref(2026, 5, 15, 4*60,      23*60+3),
                Ref(2026, 5, 21, 3*60+55,   23*60+8),
                Ref(2026, 6,  5, 3*60+47,   23*60+21),
                Ref(2026, 6, 10, 3*60+47,   23*60+21)
            )),
            City("Berlin",    52.520, 13.405, "Europe/Berlin", listOf(
                Ref(2026, 5, 15, 3*60+24,   22*60+31),
                Ref(2026, 5, 21, 3*60+19,   22*60+36),
                Ref(2026, 6,  5, 3*60+11,   22*60+49)
            )),
            City("Brussels",  50.850,  4.352, "Europe/Brussels", listOf(
                Ref(2026, 5, 15, 4*60+21,   23*60+8),
                Ref(2026, 5, 21, 4*60+19,   23*60+11),
                Ref(2026, 6,  5, 4*60+12,   23*60+21)
            )),
            City("Copenhagen",55.676, 12.568, "Europe/Copenhagen", listOf(
                Ref(2026, 5, 15, 3*60+24,   23*60+3),
                Ref(2026, 5, 21, 3*60+19,   23*60+8),
                Ref(2026, 6,  5, 3*60+11,   23*60+21),
                Ref(2026, 6, 10, 3*60+9,    23*60+21)
            )),
            City("Helsinki",  60.170, 24.938, "Europe/Helsinki", listOf(
                Ref(2026, 5, 15, 3*60+17,   23*60+7),
                Ref(2026, 5, 21, 3*60+9,    23*60+17),
                Ref(2026, 6,  5, 2*60+58,   23*60+37),
                Ref(2026, 6, 10, 2*60+58,   23*60+42)
            )),
            City("Paris",     48.857,  2.352, "Europe/Paris", listOf(
                Ref(2026, 5, 15, 4*60+21,   23*60+8),
                Ref(2026, 5, 21, 4*60+19,   23*60+11),
                Ref(2026, 6,  5, 4*60+12,   23*60+18),
                Ref(2026, 6, 10, 4*60+10,   23*60+21)
            )),
            City("Stockholm", 59.329, 18.069, "Europe/Stockholm", listOf(
                Ref(2026, 5, 15, 3*60+17,   23*60+7),
                Ref(2026, 5, 21, 3*60+9,    23*60+17),
                Ref(2026, 6,  5, 2*60+58,   23*60+37),
                Ref(2026, 6, 10, 2*60+58,   23*60+42)
            )),
            City("Zurich",    47.377,  8.542, "Europe/Zurich", listOf(
                Ref(2026, 5, 15, 3*60+49,   22*60+51),
                Ref(2026, 5, 21, 3*60+40,   22*60+58),
                Ref(2026, 6,  5, 3*60+40,   23*60+2)
            )),
            City("Basel",     47.498,  7.745, "Europe/Zurich", listOf(
                Ref(2026, 5, 15, 3*60+53,   22*60+55),
                Ref(2026, 5, 21, 3*60+51,   22*60+58),
                Ref(2026, 6,  5, 3*60+44,   23*60+5),
                Ref(2026, 6, 10, 3*60+42,   23*60+8)
            )),
            // --- Non-European cities: sourced from namazvakitleri.diyanet.gov.tr ---
            City("Toronto",  43.653, -79.383, "America/Toronto", listOf(
                Ref(2026, 5, 15, 3*60+50,   22*60+21),
                Ref(2026, 5, 21, 3*60+40,   22*60+30),
                Ref(2026, 6,  5, 3*60+19,   22*60+52),
                Ref(2026, 6, 10, 3*60+15,   22*60+57)
            )),
            City("Montreal", 45.502, -73.567, "America/Toronto", listOf(
                Ref(2026, 5, 15, 3*60+13,   22*60+9),
                Ref(2026, 5, 21, 3*60+10,   22*60+20),
                Ref(2026, 6,  5, 3*60+2,    22*60+33),
                Ref(2026, 6, 10, 2*60+59,   22*60+37)
            )),
            City("Moscow",   55.756,  37.617, "Europe/Moscow", listOf(
                Ref(2026, 5, 15, 2*60+43,   21*60+59),
                Ref(2026, 5, 21, 2*60+37,   22*60+6),
                Ref(2026, 6,  5, 2*60+26,   22*60+21),
                Ref(2026, 6, 10, 2*60+24,   22*60+25)
            )),
            City("London",   51.507,  -0.128, "Europe/London", listOf(
                Ref(2026, 5, 15, 3*60+21,   22*60+22),
                Ref(2026, 5, 21, 3*60+17,   22*60+26),
                Ref(2026, 6,  5, 3*60+10,   22*60+38),
                Ref(2026, 6, 10, 3*60+9,    22*60+41)
            ))
        )

        // MWL params with all adjustments zeroed — gives raw astronomical sunrise/maghrib
        fun astroParams() = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters.also {
            it.adjustments.fajr = 0; it.adjustments.sunrise = 0
            it.adjustments.dhuhr = 0; it.adjustments.asr = 0
            it.adjustments.maghrib = 0; it.adjustments.isha = 0
        }

        println("\n=== Calibrated Diyanet fraction constants ===")
        for (city in cities) {
            val coords = Coordinates(city.lat, city.lng)
            // Format Adhan times in the city's own timezone so local-time arithmetic is correct.
            // Using the JVM default timezone gives wrong night lengths for non-European cities.
            val cityFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone(city.tz)
            }
            val kFajrList = mutableListOf<Double>()
            val kIshaList = mutableListOf<Double>()

            for (ref in city.refs) {
                val times = PrayerTimes(coords, DateComponents(ref.year, ref.month, ref.day), astroParams())
                val sunriseMin = cityFmt.format(times.sunrise).let { t ->
                    t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
                }
                val maghribMin = cityFmt.format(times.maghrib).let { t ->
                    t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
                }
                val night = (sunriseMin + 1440) - maghribMin
                kFajrList.add((sunriseMin - ref.fajrMin).toDouble() / night)
                kIshaList.add((ref.ishaMin - maghribMin).toDouble() / night)
            }

            val kFajr = kFajrList.average()
            val kIsha = kIshaList.average()
            println("%-12s  k_fajr=%.4f  k_isha=%.4f  (spread_f=%.4f  spread_i=%.4f)".format(
                city.name, kFajr, kIsha,
                kFajrList.max() - kFajrList.min(),
                kIshaList.max() - kIshaList.min()
            ))
        }
        println("=============================================\n")
    }

    /**
     * Toronto (43.7°N) – May 15 2026
     * Diyanet ref: Fajr 03:50, Isha 22:21
     */
    // Tests for non-European cities temporarily set the JVM timezone to the city's
    // local timezone. The production code uses SimpleDateFormat(Locale.getDefault())
    // which formats in the device timezone — on a real device this is always correct.
    // Unit tests run in the machine's timezone (CEST on this box), so we override it
    // per-test to make the formatted-string assertions portable.
    private fun <T> withTimezone(tzId: String, block: () -> T): T {
        val saved = TimeZone.getDefault()
        return try {
            TimeZone.setDefault(TimeZone.getTimeZone(tzId))
            block()
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun `diyanet fraction algorithm Toronto May 15`() = withTimezone("America/Toronto") {
        val lat = 43.653; val lng = -79.383
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Toronto May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 03:50  Isha 22:21")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 03:50",
            kotlin.math.abs(day.fajr.toMinutes() - (3*60+50)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 22:21",
            kotlin.math.abs(day.isha.toMinutes() - (22*60+21)) <= 10)
    }

    /**
     * Montreal (45.5°N) – May 15 2026
     * Diyanet ref: Fajr 03:13, Isha 22:09
     */
    @Test
    fun `diyanet fraction algorithm Montreal May 15`() = withTimezone("America/Toronto") {
        val lat = 45.502; val lng = -73.567
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Montreal May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 03:13  Isha 22:09")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 03:13",
            kotlin.math.abs(day.fajr.toMinutes() - (3*60+13)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 22:09",
            kotlin.math.abs(day.isha.toMinutes() - (22*60+9)) <= 10)
    }

    /**
     * Moscow (55.8°N) – May 15 2026
     * Diyanet ref: Fajr 02:43, Isha 21:59
     */
    @Test
    fun `diyanet fraction algorithm Moscow May 15`() = withTimezone("Europe/Moscow") {
        val lat = 55.756; val lng = 37.617
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("Moscow May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 02:43  Isha 21:59")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 02:43",
            kotlin.math.abs(day.fajr.toMinutes() - (2*60+43)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 21:59",
            kotlin.math.abs(day.isha.toMinutes() - (21*60+59)) <= 10)
    }

    /**
     * London (51.5°N) – May 15 2026
     * Diyanet ref: Fajr 03:21, Isha 22:22
     */
    @Test
    fun `diyanet fraction algorithm London May 15`() = withTimezone("Europe/London") {
        val lat = 51.507; val lng = -0.128
        val days = calculator.calculateMonthlyPrayerTimes(2026, 5, lat, lng, methodId = 13)
        val day = days[14]
        println("London May 15 — Fajr: ${day.fajr}  Isha: ${day.isha}")
        println("Diyanet ref: Fajr 03:21  Isha 22:22")
        assertTrue("Fajr ${day.fajr} should be within 10 min of 03:21",
            kotlin.math.abs(day.fajr.toMinutes() - (3*60+21)) <= 10)
        assertTrue("Isha ${day.isha} should be within 10 min of 22:22",
            kotlin.math.abs(day.isha.toMinutes() - (22*60+22)) <= 10)
    }

    /**
     * Winter sanity check for North America.
     * Toronto Dec 21 2026: Diyanet Fajr 06:03, Isha 18:16 — standard angle wins.
     */
    @Test
    fun `diyanet fraction does not over-correct in winter Toronto`() = withTimezone("America/Toronto") {
        val lat = 43.653; val lng = -79.383
        val days = calculator.calculateMonthlyPrayerTimes(2026, 12, lat, lng, methodId = 13)
        val day21 = days[20]
        println("Toronto Dec 21 — Fajr: ${day21.fajr}  Isha: ${day21.isha}")
        println("Diyanet ref: Fajr 06:03  Isha 18:16")
        assertTrue("Winter Fajr ${day21.fajr} should be within 15 min of 06:03 (standard angle wins)",
            kotlin.math.abs(day21.fajr.toMinutes() - (6*60+3)) <= 15)
        assertTrue("Winter Isha ${day21.isha} should be within 15 min of 18:16 (standard angle wins)",
            kotlin.math.abs(day21.isha.toMinutes() - (18*60+16)) <= 15)
    }

    @Test
    fun `hanafi madhab changes asr time`() {
        // Istanbul coordinates
        val lat = 41.0082
        val lng = 28.9784
        
        val shafiDays = calculator.calculateMonthlyPrayerTimes(2024, 5, lat, lng, 0, 0)
        val hanafiDays = calculator.calculateMonthlyPrayerTimes(2024, 5, lat, lng, 0, 1)
        
        val shafiAsr = shafiDays.first().asr
        val hanafiAsr = hanafiDays.first().asr
        
        // In most cases, Hanafi Asr is later than Shafi Asr
        assertTrue("Hanafi Asr ($hanafiAsr) should be different/later than Shafi Asr ($shafiAsr)", hanafiAsr != shafiAsr)
    }
}
