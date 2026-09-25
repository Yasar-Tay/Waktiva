package com.ybugmobile.waktiva.ui.theme.clouds

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Gaussian-like blur of ARGB pixels (as returned by Bitmap.getPixels, not premultiplied),
 * done as three box passes per axis on premultiplied channels so edges don't fringe.
 * Pixels outside the image count as transparent. [radius] is roughly the standard deviation.
 */
internal fun blurArgb(src: IntArray, width: Int, height: Int, radius: Float): IntArray {
    val box = radius.roundToInt()
    if (box < 1 || width == 0 || height == 0) return src.copyOf()
    val n = width * height
    val channels = Array(4) { FloatArray(n) }
    for (i in 0 until n) {
        val c = src[i]
        val a = (c ushr 24) and 0xFF
        val k = a / 255f
        channels[0][i] = a.toFloat()
        channels[1][i] = ((c shr 16) and 0xFF) * k
        channels[2][i] = ((c shr 8) and 0xFF) * k
        channels[3][i] = (c and 0xFF) * k
    }
    val scratch = FloatArray(n)
    for (channel in channels) {
        repeat(3) {
            boxHorizontal(channel, scratch, width, height, box)
            boxVertical(scratch, channel, width, height, box)
        }
    }
    return IntArray(n) { i ->
        val a = channels[0][i]
        if (a < 0.5f) {
            0
        } else {
            val k = 255f / a
            val r = (channels[1][i] * k).roundToInt().coerceIn(0, 255)
            val g = (channels[2][i] * k).roundToInt().coerceIn(0, 255)
            val b = (channels[3][i] * k).roundToInt().coerceIn(0, 255)
            (a.roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

private fun boxHorizontal(src: FloatArray, dst: FloatArray, width: Int, height: Int, r: Int) {
    val norm = 1f / (2 * r + 1)
    for (y in 0 until height) {
        val row = y * width
        var sum = 0f
        for (x in 0..minOf(r, width - 1)) sum += src[row + x]
        for (x in 0 until width) {
            dst[row + x] = sum * norm
            val leaving = x - r
            val entering = x + r + 1
            if (leaving >= 0) sum -= src[row + leaving]
            if (entering < width) sum += src[row + entering]
        }
    }
}

private fun boxVertical(src: FloatArray, dst: FloatArray, width: Int, height: Int, r: Int) {
    val norm = 1f / (2 * r + 1)
    for (x in 0 until width) {
        var sum = 0f
        for (y in 0..minOf(r, height - 1)) sum += src[y * width + x]
        for (y in 0 until height) {
            dst[y * width + x] = sum * norm
            val leaving = y - r
            val entering = y + r + 1
            if (leaving >= 0) sum -= src[leaving * width + x]
            if (entering < height) sum += src[entering * width + x]
        }
    }
}

/** A blurred, mutable copy of this bitmap. */
internal fun Bitmap.blurred(radius: Float): Bitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    out.setPixels(blurArgb(pixels, width, height, radius), 0, width, 0, 0, width, height)
    return out
}
