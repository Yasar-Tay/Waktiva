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

    // --- The day's weather in spells ---

    private fun forecastOf(condition: (Int) -> WeatherCondition) = DayForecast(date, 5.0, 12.0, (0 until 24).map { h ->
        val c = condition(h)
        HourForecast(h, c, c, temperature = 8.0, isDay = h in 7..18)
    })

    private fun spellsOf(now: LocalTime? = null, condition: (Int) -> WeatherCondition): List<WeatherSpell> =
        weatherSpells(day, DayCircleWeather(forecastOf(condition), WeatherCondition.UNKNOWN, WeatherCondition.UNKNOWN, true), now)

    @Test
    fun aClearDayHasASunAndAMoon() {
        // Sunrise 06:57 and Maghrib 19:04: the day is the hours from 07:00 to 18:59.
        val spells = spellsOf { WeatherCondition.CLEAR }
        assertEquals(2, spells.size)
        val sun = spells.single { it.weather.isDay }
        assertEquals(7 * 60f, sun.startMinute)
        assertEquals(19 * 60f, sun.endMinute)
        // The night runs on over midnight, from 19:00 to 06:59.
        val moon = spells.single { !it.weather.isDay }
        assertEquals(19 * 60f, moon.startMinute)
        assertEquals((24 + 7) * 60f, moon.endMinute)
    }

    @Test
    fun anOvercastDayIsOneIcon() {
        val spells = spellsOf { WeatherCondition.OVERCAST }
        assertEquals(1, spells.size)
        assertEquals(0f, spells[0].startMinute)
        assertEquals(1440f, spells[0].endMinute)
    }

    @Test
    fun aRainyNightIsOneSpellAcrossMidnight() {
        val spells = spellsOf { h -> if (h >= 22 || h < 4) WeatherCondition.RAINY else WeatherCondition.OVERCAST }
        assertEquals(2, spells.size)
        val rain = spells.single { it.weather.condition == WeatherCondition.RAINY }
        assertEquals(22 * 60f, rain.startMinute)
        assertEquals((24 + 4) * 60f, rain.endMinute)
    }

    @Test
    fun theStrengthOfTheRainDoesNotSplitASpell() {
        // Drizzle, rain and heavy rain are all one icon's rain; it shows the most frequent of them.
        val spells = spellsOf { h ->
            when (h) {
                10 -> WeatherCondition.DRIZZLE
                11, 12, 13 -> WeatherCondition.RAINY
                14 -> WeatherCondition.HEAVY_RAIN
                else -> WeatherCondition.OVERCAST
            }
        }
        val rain = spells.single { it.weather.condition == WeatherCondition.RAINY }
        assertEquals(10 * 60f, rain.startMinute)
        assertEquals(15 * 60f, rain.endMinute)
    }

    @Test
    fun aPassingCloudJoinsItsNeighboursButAShowerKeepsItsIcon() {
        val spells = spellsOf { h ->
            when (h) {
                9 -> WeatherCondition.PARTLY_CLOUDY
                15 -> WeatherCondition.RAIN_SHOWERS
                else -> WeatherCondition.OVERCAST
            }
        }
        assertTrue(spells.none { it.weather.condition == WeatherCondition.PARTLY_CLOUDY })
        val shower = spells.single { it.weather.condition == WeatherCondition.RAIN_SHOWERS }
        assertEquals(15 * 60f, shower.startMinute)
        assertEquals(16 * 60f, shower.endMinute)
        // The cloud's hour is taken into the overcast around it: one overcast spell, round to the shower.
        assertEquals(2, spells.size)
    }

    @Test
    fun aSpellThatHasEndedIsGoneBy() {
        val spells = spellsOf(now = LocalTime.of(14, 0)) { h -> if (h in 10..11) WeatherCondition.SNOWY else WeatherCondition.OVERCAST }
        assertTrue(spells.single { it.weather.condition == WeatherCondition.SNOWY }.weather.isPast)
        assertFalse(spells.single { it.weather.condition == WeatherCondition.OVERCAST }.weather.isPast)
    }

    @Test
    fun noForecastNoSpells() {
        assertTrue(weatherSpells(day, null, null).isEmpty())
    }

    private fun place(spells: List<WeatherSpell>, keepOut: List<androidx.compose.ui.geometry.Rect> = emptyList(), innerLimit: Float = 0f) =
        spellIconOffsets(spells, labelRadius = 300f, labelHalfWidth = 40f, labelHalfHeight = 12f, iconSize = 44f, gap = 8f, keepOut = keepOut, innerLimit = innerLimit, rtl = false)

    @Test
    fun spellIconsSitInsideTheTimesAndApart() {
        val icons = place(spellsOf { h -> if (h in 10..11 || h == 15) WeatherCondition.RAINY else WeatherCondition.CLEAR })
        assertTrue(icons.size >= 3)
        icons.forEach { (_, o) ->
            // Inside the labels: at most the label radius less a label's half height, gap and half an icon.
            assertTrue(o.getDistance() <= 300f - 12f - 8f - 22f + 0.5f)
            // A label reaches furthest in towards the centre diagonally: the hypotenuse of its halves.
            assertTrue(o.getDistance() >= 300f - kotlin.math.hypot(40f, 12f) - 8f - 22f - 0.5f)
        }
        icons.forEachIndexed { i, (_, a) -> icons.drop(i + 1).forEach { (_, b) -> assertTrue((a - b).getDistance() >= 44f + 8f) } }
    }

    @Test
    fun aSpellsIconSitsAtItsMiddleWhenThereIsRoom() {
        val spell = spellsOf { h -> if (h in 2..3) WeatherCondition.RAINY else WeatherCondition.OVERCAST }
            .single { it.weather.condition == WeatherCondition.RAINY }
        val (_, icon) = place(listOf(spell)).single()
        // 03:00 is a little left of the bottom.
        val expected = com.ybugmobile.waktiva.ui.home.composables.gear.pointOn(
            androidx.compose.ui.geometry.Offset.Zero, 1f,
            com.ybugmobile.waktiva.ui.home.composables.gear.dayAngle(180f, false)
        )
        assertEquals(expected.x, icon.x / icon.getDistance(), 0.001f)
        assertEquals(expected.y, icon.y / icon.getDistance(), 0.001f)
    }

    @Test
    fun anIconKeepsClearOfThePrayersName() {
        // Overcast all day: one spell, whose middle is noon, right under the name over the hub.
        val name = androidx.compose.ui.geometry.Rect(-120f, -290f, 120f, -240f)
        val (_, icon) = place(spellsOf { WeatherCondition.OVERCAST }, keepOut = listOf(name)).single()
        assertFalse(name.overlaps(androidx.compose.ui.geometry.Rect(icon.x - 22f, icon.y - 22f, icon.x + 22f, icon.y + 22f)))
    }

    @Test
    fun anIconNeverReachesOverTheHub() {
        // A hub so large that only the bottom and top, where a label reaches in least, leave room.
        val hub = 300f - 12f - 8f - 44f - 1f
        // Rain round midnight (the bottom) and at 08:00 to 09:59 (up on the left, where labels reach in further).
        val spells = spellsOf { h -> if (h == 23 || h == 0 || h in 8..9) WeatherCondition.RAINY else WeatherCondition.OVERCAST }
        val icons = place(spells, innerLimit = hub)
        icons.forEach { (_, o) -> assertTrue(o.getDistance() - 22f >= hub - 0.5f) }
        val rain = icons.filter { it.first.condition == WeatherCondition.RAINY }
        assertEquals(1, rain.size)
        assertTrue(rain.single().second.y > 250f)
    }
}
