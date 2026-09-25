package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class MetalLightTest {

    private fun degrees(radians: Float) = ((Math.toDegrees(radians.toDouble()) % 360 + 360) % 360).toFloat()

    @Test
    fun usesTheDefaultLightWhenTheSunIsDown() {
        assertEquals(GearLight.DEFAULT_ANGLE, sunLightAngle(120f, -5f, 0f), 0f)
        assertEquals(GearLight.DEFAULT_ANGLE, sunLightAngle(120f, 0f, 0f), 0f)
    }

    @Test
    fun aHighSunInFrontLightsFromAbove() {
        assertEquals(270f, degrees(sunLightAngle(180f, 60f, 180f)), 0.5f)
    }

    @Test
    fun aSunToTheRightLightsFromTheRight() {
        // 90° clockwise of the heading, 30° up: from the right and a little above
        assertEquals(330f, degrees(sunLightAngle(270f, 30f, 180f)), 0.5f)
        // and mirrored to the left
        assertEquals(210f, degrees(sunLightAngle(90f, 30f, 180f)), 0.5f)
    }

    @Test
    fun aSunStraightBehindThePhoneFallsBackToTheDefault() {
        // low and dead ahead: its projection on the screen has no clear direction
        assertEquals(degrees(GearLight.DEFAULT_ANGLE), degrees(sunLightAngle(90f, 5f, 90f)), 0.5f)
    }

    @Test
    fun blendsAnglesTheShortWayRound() {
        val from = Math.toRadians(350.0).toFloat()
        val to = Math.toRadians(10.0).toFloat()
        assertEquals(360f, Math.toDegrees(blendAngle(from, to, 0.5f).toDouble()).toFloat(), 0.01f)
        assertEquals(10f, degrees(blendAngle(from, to, 1f)), 0.01f)
    }

    @Test
    fun sheenTurnsItsBrightestReflectionTowardsTheLight() {
        val sheen = MetalSheen(
            listOf(0f to Color.DarkGray, 0.5f to Color.Gray, 0.625f to Color.White, 0.75f to Color.Gray, 1f to Color.DarkGray)
        )
        for (lightDegrees in listOf(0f, 90f, 225f, 300f)) {
            val stops = sheen.stops(GearLight(Math.toRadians(lightDegrees.toDouble()).toFloat()))
            val brightest = stops.maxBy { it.second.luminance() }.first
            assertEquals(lightDegrees / 360f, brightest % 1f, 1f / 48)
        }
    }

    @Test
    fun defaultLightIsUpAndToTheLeft() {
        assertEquals((PI * 1.25).toFloat(), GearLight.DEFAULT_ANGLE, 1e-5f)
        assertEquals(-0.7071f, GearLight.Default.towards.x, 1e-3f)
        assertEquals(-0.7071f, GearLight.Default.towards.y, 1e-3f)
    }
}
