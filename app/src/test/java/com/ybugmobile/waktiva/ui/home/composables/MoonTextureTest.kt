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

    /** Green channel: the night side's earthshine stays well under [DaylightLevel]. */
    private fun brightness(pixel: Int) = (pixel shr 8) and 0xFF

    /**
     * Share of the disc in daylight. Opacity runs from the night side's 90% to full with the
     * sunlight, so more than halfway (alpha above 242) is the lit side of the terminator.
     */
    private fun litShare(pixels: IntArray): Float {
        var disc = 0
        var lit = 0
        val r = size / 2f
        for (py in 0 until size) for (px in 0 until size) {
            val x = (px + 0.5f - r) / r
            val y = (py + 0.5f - r) / r
            if (x * x + y * y > 0.96f) continue
            disc++
            if (alpha(pixels[py * size + px]) > 242) lit++
        }
        return lit.toFloat() / disc
    }

    private fun meanBrightness(pixels: IntArray, fromX: Int, toX: Int): Float {
        var sum = 0L
        var count = 0
        for (py in 0 until size) for (px in fromX until toX) {
            sum += brightness(pixels[py * size + px])
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
        assertTrue(meanBrightness(waxing, size / 2, size) > meanBrightness(waxing, 0, size / 2) * 2)
        val waning = texture.light(0.75)
        assertTrue(meanBrightness(waning, 0, size / 2) > meanBrightness(waning, size / 2, size) * 2)
    }

    @Test
    fun theNewMoonIsOnlyFaintEarthshine() {
        val centre = texture.light(0.0)[(size / 2) * size + size / 2]
        // Dim, but nearly opaque, so the disc keeps its shape on any sky.
        assertTrue(brightness(centre) < DaylightLevel)
        assertTrue(alpha(centre) > 200)
    }

    @Test
    fun theTerminatorHasNoBrightLine() {
        // At first quarter the terminator runs down the middle. Going from the night side into the
        // day, no pixel near it may stand out above both of its neighbours a few pixels either side.
        val quarter = texture.light(0.25)
        for (py in size / 4 until size * 3 / 4) {
            for (px in size / 2 - 6..size / 2 + 6) {
                val here = brightness(quarter[py * size + px])
                val sides = maxOf(brightness(quarter[py * size + px - 3]), brightness(quarter[py * size + px + 3]))
                assertTrue("row $py, column $px: $here against $sides", here <= sides + 15)
            }
        }
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

    private companion object {
        /** Above the brightest earthshine, below the dimmest daylight worth calling lit. */
        const val DaylightLevel = 80
    }
}
