package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.graphics.Color
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherToneTest {

    private val brass = Color(0xFFD4AB5A)

    /** Spread between the strongest and weakest channel: 0 for grey. */
    private fun chroma(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)

    @Test
    fun clearWeatherLeavesColoursUntouched() {
        assertEquals(brass, WeatherTone.forScenery(WeatherCondition.CLEAR)(brass))
        assertEquals(brass, WeatherTone.forScenery(WeatherCondition.UNKNOWN)(brass))
    }

    @Test
    fun cloudsMuteAndDarken() {
        val toned = WeatherTone.forScenery(WeatherCondition.OVERCAST)(brass)
        assertTrue(chroma(toned) < chroma(brass))
        assertTrue(toned.red < brass.red)
    }

    @Test
    fun stormsMuteMoreThanClouds() {
        val cloudy = WeatherTone.forScenery(WeatherCondition.OVERCAST)(brass)
        val stormy = WeatherTone.forScenery(WeatherCondition.THUNDERSTORM)(brass)
        assertTrue(chroma(stormy) < chroma(cloudy))
        assertTrue(stormy.red < cloudy.red)
    }

    @Test
    fun heavyRainCountsAsStormLikeTheBackground() {
        val heavy = WeatherTone.forScenery(WeatherCondition.HEAVY_RAIN)(brass)
        val storm = WeatherTone.forScenery(WeatherCondition.THUNDERSTORM)(brass)
        assertEquals(storm, heavy)
    }

    @Test
    fun paletteTonesItsMetals() {
        val clear = GearPalette(WeatherTone.None)
        val rainy = GearPalette(WeatherTone.forScenery(WeatherCondition.RAINY))
        assertTrue(chroma(rainy.brass[1].second) < chroma(clear.brass[1].second))
        assertTrue(chroma(rainy.gold) < chroma(clear.gold))
    }
}
