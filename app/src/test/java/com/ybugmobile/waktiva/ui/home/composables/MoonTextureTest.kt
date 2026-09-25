package com.ybugmobile.waktiva.ui.home.composables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class MoonTextureTest {

    private val size = 64
    private val texture = MoonTexture(size)

    private fun alpha(pixel: Int) = pixel ushr 24

    /** Share of the disc drawn (nearly) opaque, i.e. in daylight. */
    private fun litShare(pixels: IntArray): Float {
        var disc = 0
        var lit = 0
        val r = size / 2f
        for (py in 0 until size) for (px in 0 until size) {
            val x = (px + 0.5f - r) / r
            val y = (py + 0.5f - r) / r
            if (x * x + y * y > 0.98f) continue
            disc++
            if (alpha(pixels[py * size + px]) > 200) lit++
        }
        return lit.toFloat() / disc
    }

    private fun meanAlpha(pixels: IntArray, fromX: Int, toX: Int): Float {
        var sum = 0L
        var count = 0
        for (py in 0 until size) for (px in fromX until toX) {
            sum += alpha(pixels[py * size + px])
            count++
        }
        return sum.toFloat() / count
    }

    @Test
    fun leavesTheCornersTransparent() {
        val pixels = texture.light(0.5)
        assertEquals(0, alpha(pixels[0]))
        assertEquals(0, alpha(pixels[size * size - 1]))
    }

    @Test
    fun litShareFollowsThePhase() {
        for (phase in listOf(0.15, 0.25, 0.4, 0.5, 0.6, 0.75, 0.85)) {
            val expected = ((1 - cos(2 * PI * phase)) / 2).toFloat()
            assertEquals("phase $phase", expected, litShare(texture.light(phase)), 0.07f)
        }
    }

    @Test
    fun waxingIsLitOnTheRightAndWaningOnTheLeft() {
        val waxing = texture.light(0.25)
        assertTrue(meanAlpha(waxing, size / 2, size) > meanAlpha(waxing, 0, size / 2) * 2)
        val waning = texture.light(0.75)
        assertTrue(meanAlpha(waning, 0, size / 2) > meanAlpha(waning, size / 2, size) * 2)
    }

    @Test
    fun theNewMoonIsOnlyFaintEarthshine() {
        val centre = texture.light(0.0)[(size / 2) * size + size / 2]
        assertTrue(alpha(centre) in 1..60)
    }

    @Test
    fun theSurfaceHasDarkMariaAndBrightHighlands() {
        val full = texture.light(0.5)
        val r = size / 2f
        fun brightness(x: Float, y: Float): Int {
            val px = (x * r + r).toInt()
            val py = (y * r + r).toInt()
            return (full[py * size + px] shr 8) and 0xFF
        }
        // Mare Crisium against the southern highlands near Tycho's surroundings
        assertTrue(brightness(0.68f, -0.30f) < brightness(0.25f, 0.62f))
    }
}
