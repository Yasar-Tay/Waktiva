package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.ui.graphics.Color
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.DayForecast
import com.ybugmobile.waktiva.domain.model.HourForecast
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.home.composables.gear.WeatherTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DaySkyTest {

    private val date = LocalDate.of(2026, 9, 30)
    private val day = PrayerDay(
        date = date,
        hijriDate = null,
        timings = mapOf(
            PrayerType.FAJR to LocalTime.of(5, 32),
            PrayerType.SUNRISE to LocalTime.of(6, 57),
            PrayerType.DHUHR to LocalTime.of(13, 5),
            PrayerType.ASR to LocalTime.of(16, 25),
            PrayerType.MAGHRIB to LocalTime.of(19, 4),
            PrayerType.ISHA to LocalTime.of(20, 24)
        )
    )

    /** Clear until noon, rain from 15:00 to 18:59, overcast after. */
    private val forecast = DayForecast(date, 15.0, 26.0, (0 until 24).map { h ->
        val (condition, effect) = when {
            h < 12 -> WeatherCondition.CLEAR to WeatherCondition.CLEAR
            h < 15 -> WeatherCondition.PARTLY_CLOUDY to WeatherCondition.PARTLY_CLOUDY
            h < 19 -> WeatherCondition.RAINY to WeatherCondition.RAINY
            else -> WeatherCondition.OVERCAST to WeatherCondition.OVERCAST
        }
        HourForecast(h, condition, effect, temperature = 15.0 + h / 2.0, isDay = h in 7..18)
    })

    private fun weather(effectsOn: Boolean = true, now: WeatherCondition = WeatherCondition.PARTLY_CLOUDY) =
        DayCircleWeather(forecast, nowCondition = now, nowEffect = now, effectsOn = effectsOn)

    // --- The sky's colours ---

    @Test
    fun theSkyFollowsTheHomeScreensColours() {
        assertEquals(Color(0xFF1E1B4B), skyLight(5 * 60 + 32f, day))
        assertEquals(Color(0xFF3E86CF), skyLight(13 * 60 + 5f, day))
        assertEquals(Color(0xFF121A2B), skyLight(0f, day))
        assertEquals(Color(0xFF121A2B), skyLight(1440f, day))
    }

    @Test
    fun theSweepPutsNoonAtTheTopAndGoesRoundOnce() {
        val sweep = skySweep(day, { null }, rtl = false)
        assertEquals(97, sweep.size)
        assertEquals(sweep.first().second, sweep.last().second)
        assertTrue(sweep.toList().zipWithNext().all { (a, b) -> b.first > a.first })
        // A sweep starts at 3 o'clock and runs clockwise: three quarters round is the top.
        assertEquals(skyLight(720f, day), sweep[72].second)
        assertEquals(skyLight(360f, day), sweep[48].second)
    }

    @Test
    fun rightToLeftMirrorsTheMorningAndTheEvening() {
        val sweep = skySweep(day, { null }, rtl = true)
        assertEquals(skyLight(720f, day), sweep[72].second)
        assertEquals(skyLight(1080f, day), sweep[48].second)
    }

    @Test
    fun eachHourTakesTheToneOfItsWeather() {
        val rainy = skySweep(day, { h -> if (h == 16) WeatherCondition.RAINY else null }, rtl = false)
        // 16:00 is at fraction (16 / 24 + 0.25) mod 1 = 11 / 12: stop 88.
        assertEquals(WeatherTone.forScenery(WeatherCondition.RAINY)(skyLight(960f, day)), rainy[88].second)
        assertEquals(skyLight(720f, day), rainy[72].second)
    }

    @Test
    fun withEffectsOffTheSkyOnlyHasTheDaysLight() {
        val sky = daySky(day, weather(effectsOn = false), now = LocalTime.of(14, 10), rtl = false)
        assertEquals(skySweep(day, { null }, rtl = false).map { it.second }, sky.sweep.map { it.second })
        assertTrue(sky.starryHours.isEmpty())
    }

    @Test
    fun theCurrentHourFollowsTheSkyOnScreen() {
        val sky = daySky(day, weather(now = WeatherCondition.THUNDERSTORM), now = LocalTime.of(10, 30), rtl = false)
        // 10:00 is at fraction (10 / 24 + 0.25) mod 1 = 2 / 3: stop 64, the forecast saying clear.
        assertEquals(WeatherTone.forScenery(WeatherCondition.THUNDERSTORM)(skyLight(600f, day)), sky.sweep[64].second)
    }

    @Test
    fun starsOnlyInClearNightHours() {
        val sky = daySky(day, weather(), now = null, rtl = false)
        assertEquals((0..6).toList(), sky.starryHours)
    }

    // --- The prayers' weather ---

    @Test
    fun eachPrayerHasTheWeatherOfItsHour() {
        val weather = prayerWeather(day, weather(), now = null, currentPrayer = null)
        assertEquals(WeatherCondition.RAINY, weather.getValue(PrayerType.ASR).condition)
        assertEquals(WeatherCondition.OVERCAST, weather.getValue(PrayerType.ISHA).condition)
        assertEquals(15.0 + 16 / 2.0, weather.getValue(PrayerType.ASR).temperature!!, 0.0)
        assertTrue(weather.getValue(PrayerType.DHUHR).isDay)
        assertFalse(weather.getValue(PrayerType.ISHA).isDay)
        assertFalse(weather.getValue(PrayerType.FAJR).isDay)
    }

    @Test
    fun todaysCurrentHourTakesTheReportedWeather() {
        val weather = prayerWeather(day, weather(now = WeatherCondition.HEAVY_RAIN), LocalTime.of(13, 40), PrayerType.DHUHR)
        assertEquals(WeatherCondition.HEAVY_RAIN, weather.getValue(PrayerType.DHUHR).condition)
        assertEquals(WeatherCondition.RAINY, weather.getValue(PrayerType.ASR).condition)
    }

    @Test
    fun prayersGoneByAreMarkedPast() {
        val weather = prayerWeather(day, weather(), LocalTime.of(17, 0), PrayerType.ASR)
        assertTrue(weather.getValue(PrayerType.FAJR).isPast)
        assertTrue(weather.getValue(PrayerType.DHUHR).isPast)
        assertFalse(weather.getValue(PrayerType.ASR).isPast)
        assertFalse(weather.getValue(PrayerType.ISHA).isPast)
    }

    @Test
    fun noForecastNoWeather() {
        assertTrue(prayerWeather(day, weather().copy(forecast = null), null, null).isEmpty())
        assertTrue(prayerWeather(day, null, null, null).isEmpty())
    }

    @Test
    fun theRangeIsInWholeDegrees() {
        assertEquals("15° – 26°", forecast.copy(minTemp = 14.6, maxTemp = 26.4).temperatureRange())
    }

    @Test
    fun iconsMatchTheTopOfTheScreen() {
        assertEquals(R.drawable.clear_night, weatherIconRes(WeatherCondition.CLEAR, isDay = false))
        assertEquals(R.drawable.partly_cloudy_day, weatherIconRes(WeatherCondition.MAINLY_CLEAR, isDay = true))
        assertEquals(R.drawable.rain_day_night, weatherIconRes(WeatherCondition.RAIN_SHOWERS, isDay = false))
    }

    // --- Where the icons go ---

    private val minutes = day.timings.values.map { it.hour * 60f + it.minute }

    private fun offset(index: Int, rtl: Boolean = false, list: List<Float> = minutes) =
        weatherIconOffset(list, index, labelRadius = 300f, labelHalfWidth = 40f, labelHalfHeight = 12f, iconSize = 44f, gap = 8f, rtl = rtl)

    @Test
    fun anIconSitsBesideItsLabelOnTheRoomierSide() {
        // Dhuhr at 13:05 (just right of the top, x = 300 cos 286° ≈ 83) has six hours back to Sunrise
        // and three on to Asr: its icon goes before it, back towards the top.
        val dhuhr = offset(2)
        assertEquals(300f, dhuhr.getDistance(), 0.5f)
        assertTrue(dhuhr.y < 0f && dhuhr.x < 60f)
        // Isha has the night after it: it goes after, further round towards midnight.
        val isha = offset(5)
        assertTrue(isha.y > 0f)
    }

    @Test
    fun rightToLeftMirrorsTheIcons() {
        val ltr = offset(2)
        val rtl = offset(2, rtl = true)
        assertEquals(-ltr.x, rtl.x, 0.01f)
        assertEquals(ltr.y, rtl.y, 0.01f)
    }

    @Test
    fun withoutRoomBesideItAnIconSitsInsideItsLabel() {
        val crowded = listOf(600f, 610f, 620f, 630f, 640f, 650f)
        val icon = offset(2, list = crowded)
        assertEquals(300f - 12f - 8f - 22f, icon.getDistance(), 0.5f)
    }

    @Test
    fun theIconsOfOneDayNeverOverlapEachOther() {
        val all = minutes.indices.map { offset(it) }
        all.forEachIndexed { i, a -> all.drop(i + 1).forEach { b -> assertTrue((a - b).getDistance() > 44f) } }
    }
}
