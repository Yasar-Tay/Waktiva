package com.ybugmobile.waktiva.ui.home.composables.gear

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class GearGeometryTest {

    private val rotations = listOf(0f, 0.3f, 2.1f, 5.9f)
    private val contacts = listOf(0.2f, 1.7f, 4.4f)

    /** Fractional part in [0, 1). */
    private fun frac(x: Float) = ((x % 1f) + 1f) % 1f

    @Test
    fun externalMeshSeatsDriverToothInPlanetGap() {
        for (driver in rotations) for (theta in contacts) {
            val planet = meshExternal(driver, MAIN_TEETH, theta, PLANET_TEETH)
            val driverPhase = (theta - driver) / (TAU / MAIN_TEETH)
            val planetPhase = (theta + TAU / 2 - planet) / (TAU / PLANET_TEETH)
            assertEquals(0.5f, frac(driverPhase + planetPhase), 1e-3f)
        }
    }

    @Test
    fun internalMeshSeatsRingToothInPlanetGap() {
        for (ring in rotations) for (theta in contacts) {
            val planet = meshInternal(ring, MAIN_TEETH, theta, PLANET_TEETH)
            val ringPhase = (theta - ring) / (TAU / MAIN_TEETH)
            val planetPhase = (theta - planet) / (TAU / PLANET_TEETH)
            assertEquals(0.5f, frac(ringPhase - planetPhase), 1e-3f)
        }
    }

    @Test
    fun planetsTurnEightTimesPerWheelTurn() {
        val step = 0.01f
        val external = meshExternal(1f + step, MAIN_TEETH, 0.5f, PLANET_TEETH) - meshExternal(1f, MAIN_TEETH, 0.5f, PLANET_TEETH)
        val internal = meshInternal(1f + step, MAIN_TEETH, 0.5f, PLANET_TEETH) - meshInternal(1f, MAIN_TEETH, 0.5f, PLANET_TEETH)
        assertEquals(-8f * step, external, 1e-4f) // outside mesh: opposite direction
        assertEquals(8f * step, internal, 1e-4f)  // inside a ring: same direction
    }

    @Test
    fun dayAngleMatchesPrayerCircleLayout() {
        val halfPi = (PI / 2).toFloat()
        assertEquals(halfPi, dayAngle(0f, rtl = false), 1e-5f)            // midnight at the bottom
        assertEquals(3 * halfPi, dayAngle(720f, rtl = false), 1e-5f)      // noon at the top
        assertEquals(2 * halfPi, dayAngle(360f, rtl = false), 1e-5f)      // 06:00 on the left
        assertEquals(0f, dayAngle(360f, rtl = true), 1e-5f)               // mirrored in RTL
    }
}
