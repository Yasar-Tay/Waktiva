package com.ybugmobile.waktiva.ui.theme.clouds

import com.ybugmobile.waktiva.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLayoutTest {

    private val width = 1080f
    private val height = 2340f

    @Test
    fun clearSkiesHaveNoClouds() {
        assertNull(cloudRecipe(WeatherCondition.CLEAR))
        assertNull(cloudRecipe(WeatherCondition.UNKNOWN))
    }

    @Test
    fun everyCloudyConditionHasARecipe() {
        WeatherCondition.entries
            .filter { it != WeatherCondition.CLEAR && it != WeatherCondition.UNKNOWN }
            .forEach { assertTrue(it.name, cloudRecipe(it) != null) }
    }

    @Test
    fun placesTheRecipesCloudsInTheirBands() {
        val recipe = cloudRecipe(WeatherCondition.PARTLY_CLOUDY)!!
        val clouds = layoutClouds(recipe, width, height)
        assertEquals(recipe.layers.sumOf { it.count }, clouds.size)
        clouds.forEach { c ->
            val band = recipe.layers[c.layer].top
            assertTrue(c.top >= band.start * height - 0.5f && c.top <= band.endInclusive * height + 0.5f)
            assertTrue(c.width / c.height in recipe.layers[c.layer].aspect.start..recipe.layers[c.layer].aspect.endInclusive + 0.01f)
        }
    }

    @Test
    fun cloudsInALayerDriftAtTheirOwnSpeeds() {
        val clouds = layoutClouds(cloudRecipe(WeatherCondition.OVERCAST)!!, width, height)
        clouds.groupBy { it.layer }.values.forEach { layer ->
            assertTrue(layer.map { it.speed }.distinct().size == layer.size)
        }
        // nearer layers drift faster on average
        val average = clouds.groupBy { it.layer }.mapValues { (_, l) -> l.map { it.speed }.average() }
        assertTrue(average.getValue(0) < average.getValue(1) && average.getValue(1) < average.getValue(2))
    }

    @Test
    fun wrapsFromTheRightEdgeBackToTheLeft() {
        val cloud = layoutClouds(cloudRecipe(WeatherCondition.RAINY)!!, width, height).first()
        val margin = cloud.height
        for (seconds in listOf(0f, 13f, 250f, 4000f)) {
            val left = cloud.leftAt(seconds, width)
            assertTrue(left >= -cloud.width - margin - 0.5f && left <= width + margin + 0.5f)
        }
        // one full loop brings the cloud back to where it started
        val loop = (width + cloud.width + 2 * margin) / cloud.speed
        assertEquals(cloud.leftAt(5f, width), cloud.leftAt(5f + loop, width), 1f)
    }

    @Test
    fun layoutIsStable() {
        val recipe = cloudRecipe(WeatherCondition.THUNDERSTORM)!!
        val a = layoutClouds(recipe, width, height)
        val b = layoutClouds(recipe, width, height)
        assertEquals(a.map { it.startX to it.top }, b.map { it.startX to it.top })
    }
}
