package com.ybugmobile.waktiva.ui.home.composables

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A procedural picture of the Moon's near side, [size] pixels across, which [light] shades for
 * any phase.
 *
 * The surface is a brightness (albedo) map: grainy highlands; the dark maria where they really
 * are, with soft, irregular shores; scattered small craters; and the bright ray craters Tycho,
 * Copernicus, Kepler and Aristarchus. It is built once per size; lighting a phase only reshades it.
 *
 * Positions are on the disc as seen from the northern hemisphere, x to the right and y down,
 * both from -1 to 1.
 */
internal class MoonTexture(val size: Int) {
    private val albedo = FloatArray(size * size)
    private val roughness = FloatArray(size * size)

    init {
        val r = size / 2f
        for (py in 0 until size) {
            for (px in 0 until size) {
                val x = (px + 0.5f - r) / r
                val y = (py + 0.5f - r) / r
                if (x * x + y * y > 1.05f) continue
                albedo[py * size + px] = albedoAt(x, y)
                roughness[py * size + px] = (fbm(x * 30f, y * 30f, 17) - 0.5f) * 0.05f
            }
        }
    }

    /**
     * The Moon at [phaseProgress] (0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter) as
     * non-premultiplied ARGB pixels, lit from the right while waxing as seen from the north.
     *
     * Light falls off with the Lommel-Seeliger law, so the full Moon looks evenly bright, as the
     * real one does, rather than like a shaded ball, and the terminator is soft and slightly
     * ragged where relief catches the low sun. The night side is faint earthshine and mostly lets
     * the sky show through.
     *
     * The day side is exposed brighter through a soft shoulder, so the highlands shine without
     * clipping and the maria keep their contrast, and a faint sheen rises where the sun is
     * highest, so the Moon looks luminous rather than a flat grey.
     */
    fun light(phaseProgress: Double): IntArray {
        val elongation = phaseProgress * 2 * PI
        val sunX = sin(elongation).toFloat()
        val sunZ = -cos(elongation).toFloat()
        val r = size / 2f
        val pixels = IntArray(size * size)
        for (py in 0 until size) {
            for (px in 0 until size) {
                val x = (px + 0.5f - r) / r
                val y = (py + 0.5f - r) / r
                val rr = x * x + y * y
                if (rr > 1.02f) continue
                val i = py * size + px
                val z = sqrt(max(0f, 1f - rr))
                val incidence = x * sunX + z * sunZ + roughness[i]
                var lit = if (incidence > 0f) 0.8f * (2f * incidence / (incidence + z + 1e-4f)) + 0.2f * incidence else 0f
                lit *= ((incidence + 0.02f) / 0.08f).coerceIn(0f, 1f)
                val shine = min(1f, lit * 6f)
                val a = albedo[i]
                val day = if (shine > 0f) expose(a * lit / shine) else 0f
                val sheen = if (shine > 0f) SheenStrength * max(0f, incidence).pow(4) else 0f
                val night = (1f - shine) * a
                val red = channel(255f * (day + sheen) + 150f * night)
                val green = channel(251f * (day + sheen) + 160f * night)
                val blue = channel(240f * (day + sheen) + 185f * night)
                val edge = ((1f - sqrt(rr)) * r + 0.5f).coerceIn(0f, 1f)
                val alpha = channel(255f * edge * (0.14f + 0.86f * shine))
                pixels[i] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return pixels
    }

    private fun channel(value: Float) = value.roundToInt().coerceIn(0, 255)

    /** Brightens a surface brightness (0..1) through a soft shoulder that still maps 1 to 1. */
    private fun expose(brightness: Float) = (1f - exp(-Exposure * brightness)) / ExposureNorm

    private fun albedoAt(x: Float, y: Float): Float {
        var a = 0.8f +
            (fbm(x * 5f, y * 5f, 3) - 0.5f) * 0.18f +
            (fbm(x * 18f, y * 18f, 9) - 0.5f) * 0.14f +
            (fbm(x * 48f, y * 48f, 13) - 0.5f) * 0.08f
        for (mare in Maria) {
            val reach = hypot((x - mare.x) / mare.rx, (y - mare.y) / mare.ry)
            if (reach > 1.6f) continue
            val shore = reach + (fbm(x * 4f + mare.x * 9f, y * 4f + mare.y * 9f, 21) - 0.5f) * 0.9f
            val inside = 1f - ((shore - 0.55f) / 0.5f).coerceIn(0f, 1f)
            a *= 1f - mare.darkness * inside * (0.85f + 0.3f * fbm(x * 14f, y * 14f, 5))
        }
        for (crater in Craters) {
            val d = hypot(x - crater.x, y - crater.y)
            if (d > crater.r * 2f) continue
            val rim = exp(-((d - crater.r) / (0.3f * crater.r)).pow(2))
            val floor = if (d < crater.r) 1f - d / crater.r else 0f
            a += crater.strength * (0.07f * rim - 0.04f * floor)
        }
        for (crater in RayCraters) {
            val dx = x - crater.x
            val dy = y - crater.y
            val d = hypot(dx, dy)
            a += crater.gain * exp(-(d / crater.r).pow(2))
            if (crater.rays > 0) {
                val angle = atan2(dy, dx)
                val ray = max(0f, cos(crater.rays * angle + 1.3f)).pow(8) * 0.6f +
                    max(0f, cos((crater.rays - 3) * angle + 0.4f)).pow(10) * 0.4f
                a += 0.06f * ray * exp(-d / 0.35f) * min(1f, d / (crater.r * 2f))
            }
        }
        return a.coerceIn(0f, 1f)
    }

    private class Mare(val x: Float, val y: Float, val rx: Float, val ry: Float, val darkness: Float)
    private class Crater(val x: Float, val y: Float, val r: Float, val strength: Float)
    private class RayCrater(val x: Float, val y: Float, val r: Float, val gain: Float, val rays: Int)

    private companion object {
        /** How strongly the day side is brightened; higher lifts the mid-tones more. */
        const val Exposure = 1.6f
        val ExposureNorm = 1f - exp(-Exposure)

        /** Brightness of the sheen where the sun stands overhead. */
        const val SheenStrength = 0.1f

        val Maria = listOf(
            Mare(-0.30f, -0.40f, 0.30f, 0.24f, 0.32f), // Imbrium
            Mare(0.18f, -0.36f, 0.17f, 0.15f, 0.30f), // Serenitatis
            Mare(0.35f, -0.06f, 0.21f, 0.18f, 0.30f), // Tranquillitatis
            Mare(0.68f, -0.30f, 0.10f, 0.09f, 0.34f), // Crisium
            Mare(0.55f, 0.16f, 0.13f, 0.18f, 0.26f), // Fecunditatis
            Mare(0.36f, 0.30f, 0.08f, 0.08f, 0.24f), // Nectaris
            Mare(-0.62f, -0.02f, 0.28f, 0.46f, 0.28f), // Oceanus Procellarum
            Mare(-0.18f, 0.36f, 0.17f, 0.12f, 0.24f), // Nubium
            Mare(-0.52f, 0.34f, 0.09f, 0.09f, 0.26f), // Humorum
            Mare(0.02f, -0.70f, 0.38f, 0.06f, 0.16f), // Frigoris
            Mare(0.02f, -0.14f, 0.09f, 0.07f, 0.20f) // Vaporum and Sinus Medii
        )

        val RayCraters = listOf(
            RayCrater(-0.12f, 0.72f, 0.028f, 0.28f, 12), // Tycho
            RayCrater(-0.32f, -0.12f, 0.026f, 0.16f, 7), // Copernicus
            RayCrater(-0.55f, -0.10f, 0.018f, 0.12f, 5), // Kepler
            RayCrater(-0.72f, -0.38f, 0.016f, 0.24f, 0) // Aristarchus
        )

        val Craters: List<Crater> = run {
            val random = Random(7)
            val list = mutableListOf<Crater>()
            while (list.size < 110) {
                val x = random.nextFloat() * 2f - 1f
                val y = random.nextFloat() * 2f - 1f
                if (x * x + y * y < 0.92f) {
                    list += Crater(x, y, 0.008f + 0.04f * random.nextFloat().pow(2.2f), 0.3f + 0.7f * random.nextFloat())
                }
            }
            list
        }

        fun hash(x: Int, y: Int, seed: Int): Float {
            var h = x * 374761393 + y * 668265263 + seed * 982451653
            h = (h xor (h ushr 13)) * 1274126177
            return ((h xor (h ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967295f
        }

        fun valueNoise(x: Float, y: Float, seed: Int): Float {
            val xi = floor(x).toInt()
            val yi = floor(y).toInt()
            val u = smooth(x - xi)
            val v = smooth(y - yi)
            val a = hash(xi, yi, seed)
            val b = hash(xi + 1, yi, seed)
            val c = hash(xi, yi + 1, seed)
            val d = hash(xi + 1, yi + 1, seed)
            return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
        }

        fun smooth(t: Float) = t * t * (3 - 2 * t)

        fun fbm(x: Float, y: Float, seed: Int): Float {
            var total = 0f
            var amplitude = 0.5f
            var frequency = 1f
            for (octave in 0 until 5) {
                total += amplitude * valueNoise(x * frequency, y * frequency, seed + octave)
                frequency *= 2.03f
                amplitude *= 0.5f
            }
            return total
        }
    }
}
