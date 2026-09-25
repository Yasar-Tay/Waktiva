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

    private val cloudyConditions = WeatherCondition.entries.filter { cloudRecipe(it) != null }

    @Test
    fun portraitCloudsStayInTheSkyBand() {
        cloudyConditions.filter { it != WeatherCondition.FOGGY }.forEach { condition ->
            layoutClouds(cloudRecipe(condition)!!, width, height).forEach { c ->
                assertTrue(condition.name, c.top + c.height <= height * PortraitSky + 0.5f)
            }
        }
    }

    @Test
    fun landscapeCloudsStayInTheTopBand() {
        cloudyConditions.filter { it != WeatherCondition.FOGGY }.forEach { condition ->
            layoutClouds(cloudRecipe(condition)!!, height, width).forEach { c ->
                assertTrue(condition.name, c.top + c.height <= width * LandscapeSky + 0.5f)
            }
        }
    }

    @Test
    fun cloudsFadeOutTowardsTheBandBottomButFogDoesNot() {
        val overcast = cloudRecipe(WeatherCondition.OVERCAST)!!
        assertEquals(height * PortraitSky, skyFadeBottom(overcast, width, height)!!, 0.5f)
        assertEquals(width * LandscapeSky, skyFadeBottom(overcast, height, width)!!, 0.5f)
        assertNull(skyFadeBottom(cloudRecipe(WeatherCondition.FOGGY)!!, width, height))

        assertEquals(1f, SkyFade.first().second, 0f)
        assertEquals(0f, SkyFade.last().second, 0f)
        SkyFade.toList().zipWithNext().forEach { (upper, lower) ->
            assertTrue(upper.first < lower.first && upper.second >= lower.second)
        }
    }

    @Test
    fun fogStaysLowOnTheScreen() {
        layoutClouds(cloudRecipe(WeatherCondition.FOGGY)!!, width, height).forEach { c ->
            assertTrue(c.top + c.height >= height * 0.5f - 0.5f)
        }
    }

    @Test
    fun placesTheRecipesCloudsWithTheirShapes() {
        val recipe = cloudRecipe(WeatherCondition.PARTLY_CLOUDY)!!
        val clouds = layoutClouds(recipe, width, height)
        assertEquals(recipe.layers.sumOf { it.count }, clouds.size)
        clouds.forEach { c ->
            val spec = recipe.layers[c.layer]
            assertEquals(spec.kind, c.kind)
            assertTrue(c.width / c.height in spec.aspect.start - 0.01f..spec.aspect.endInclusive + 0.01f)
            assertTrue(c.width in spec.width.start * width - 0.5f..spec.width.endInclusive * width + 0.5f)
        }
    }

    @Test
    fun landscapeKeepsPortraitSizesAndAddsCloudsAcross() {
        val recipe = cloudRecipe(WeatherCondition.OVERCAST)!!
        val portrait = layoutClouds(recipe, width, height)
        val landscape = layoutClouds(recipe, height, width)
        // same short side, so the same cloud sizes rather than clouds twice as wide
        landscape.forEach { c ->
            val spec = recipe.layers[c.layer]
            assertTrue(c.width <= spec.width.endInclusive * width + 0.5f)
        }
        assertTrue(landscape.size >= portrait.size * 2)
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
