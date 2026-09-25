package com.ybugmobile.waktiva.ui.theme.clouds

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftBlurTest {

    private fun alpha(c: Int) = (c ushr 24) and 0xFF
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    @Test
    fun zeroRadiusReturnsAnUnchangedCopy() {
        val src = IntArray(9) { 0xFF336699.toInt() }
        assertArrayEquals(src, blurArgb(src, 3, 3, 0.2f))
    }

    @Test
    fun keepsTheInteriorOfAFlatAreaUnchanged() {
        val size = 41
        val src = IntArray(size * size) { 0xFFCC8844.toInt() }
        val centre = blurArgb(src, size, size, 3f)[20 * size + 20]
        assertEquals(255, alpha(centre))
        assertEquals(0xCC, red(centre))
    }

    @Test
    fun spreadsAPointWithoutChangingItsColour() {
        val size = 21
        val src = IntArray(size * size)
        src[10 * size + 10] = 0xFFFF0000.toInt()
        val out = blurArgb(src, size, size, 2f)
        val centre = out[10 * size + 10]
        val neighbour = out[10 * size + 12]
        assertTrue(alpha(centre) in 1..254)
        assertTrue(alpha(neighbour) > 0)
        // premultiplied blurring keeps a lone red pixel red, with no dark fringe
        assertEquals(255, red(neighbour))
        assertEquals(0, blue(neighbour))
    }
}
