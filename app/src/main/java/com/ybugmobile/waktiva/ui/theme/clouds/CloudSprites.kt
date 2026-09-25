package com.ybugmobile.waktiva.ui.theme.clouds

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

internal enum class CloudKind { CUMULUS, STRATUS, NIMBUS, TOWER, FOG }

/** Lit top, body, shaded base and highlight, as ARGB ints. */
internal class CloudPalette(val top: Int, val body: Int, val shade: Int, val highlight: Int)

/** Interior colours of a storm cloud lit by lightning. */
private val LightningPalette = CloudPalette(0xFFF4F7FF.toInt(), 0xFFC9D4FF.toInt(), 0xFF8E9BD0.toInt(), 0xFFFFFFFF.toInt())

/**
 * A pre-rendered cloud. [image] covers the cloud plus [pad] on every side, at a reduced
 * resolution; draw it at [width] x [height] scene pixels.
 */
internal class CloudSprite(val image: ImageBitmap, val pad: Float, val width: Float, val height: Float)

private class Lobe(val x: Float, val y: Float, val r: Float, val squash: Float = 1f, val minor: Boolean = false)

private class Silhouette(val lobes: List<Lobe>, val base: RectF, val baseRadius: Float)

private fun Random.between(from: Float, to: Float) = from + (to - from) * nextFloat()

/** Lobes and base of a cloud of [w] x [h] placed [pad] in from the sprite's edges. */
private fun silhouette(kind: CloudKind, rnd: Random, w: Float, h: Float, pad: Float): Silhouette {
    val lobes = mutableListOf<Lobe>()
    var base = RectF(pad + w * 0.04f, pad + h * 0.55f, pad + w * 0.96f, pad + h)
    var baseRadius = h * 0.22f
    when (kind) {
        CloudKind.CUMULUS -> {
            val n = 5 + rnd.nextInt(3)
            for (i in 0 until n) {
                val t = i / (n - 1f)
                val bulge = sin(PI * t).toFloat().pow(0.8f)
                val r = h * (0.26f + 0.42f * bulge) * rnd.between(0.85f, 1.15f)
                lobes += Lobe(pad + w * (0.1f + 0.8f * t) + (rnd.nextFloat() - 0.5f) * w * 0.05f, pad + h - r * 0.95f - h * 0.1f * bulge * rnd.nextFloat(), r)
            }
            // crowning puffs on the two biggest lobes
            lobes.sortedByDescending { it.r }.take(2).forEach { l ->
                lobes += Lobe(l.x + (rnd.nextFloat() - 0.5f) * l.r * 0.8f, l.y - l.r * 0.55f, l.r * rnd.between(0.45f, 0.65f), minor = true)
            }
            // small irregular puffs along the top edge break up the scalloped outline
            repeat(12) {
                val t = rnd.between(0.08f, 0.92f)
                val top = pad + h * (0.62f - 0.5f * sin(PI * t).toFloat().pow(0.8f))
                lobes += Lobe(pad + w * t, top + (rnd.nextFloat() - 0.3f) * h * 0.14f, h * rnd.between(0.08f, 0.24f), minor = true)
            }
        }
        CloudKind.STRATUS, CloudKind.NIMBUS, CloudKind.FOG -> {
            val n = if (kind == CloudKind.NIMBUS) 11 else 13
            val (thick, spread) = when (kind) {
                CloudKind.NIMBUS -> 0.34f to 0.26f
                CloudKind.FOG -> 0.3f to 0.18f
                else -> 0.24f to 0.2f
            }
            val squash = if (kind == CloudKind.NIMBUS) 0.72f else 0.55f
            for (i in 0 until n) {
                val t = i / (n - 1f)
                val r = h * (thick + spread * rnd.nextFloat()) * (0.75f + 0.25f * sin(PI * t).toFloat())
                lobes += Lobe(pad + w * (0.04f + 0.92f * t), pad + h * (0.52f + (rnd.nextFloat() - 0.5f) * 0.18f), r, squash)
            }
            // uneven wisps so the layer doesn't read as a row of equal bumps
            repeat(10) {
                lobes += Lobe(pad + w * rnd.between(0.05f, 0.95f), pad + h * rnd.between(0.4f, 0.65f), h * rnd.between(0.12f, 0.32f), 0.5f, minor = true)
            }
            base = RectF(pad + w * 0.04f, pad + h * 0.45f, pad + w * 0.96f, pad + h * 0.85f)
            baseRadius = h * 0.2f
        }
        CloudKind.TOWER -> {
            // cumulonimbus: broad base, rising column, flattened anvil top
            for (i in 0 until 9) {
                val r = h * rnd.between(0.18f, 0.32f)
                lobes += Lobe(pad + w * (0.08f + 0.84f * i / 8f), pad + h * 0.78f - r * 0.6f, r)
            }
            for (k in 0 until 7) {
                val t = k / 6f
                lobes += Lobe(pad + w * (0.46f + (rnd.nextFloat() - 0.5f) * 0.18f), pad + h * (0.72f - 0.55f * t), h * rnd.between(0.16f, 0.24f) * (1 - t * 0.25f))
            }
            for (k in 0 until 7) {
                lobes += Lobe(pad + w * (0.2f + 0.62f * k / 6f), pad + h * 0.12f + (rnd.nextFloat() - 0.5f) * h * 0.05f, h * rnd.between(0.08f, 0.13f))
            }
            base = RectF(pad + w * 0.04f, pad + h * 0.7f, pad + w * 0.96f, pad + h)
            baseRadius = h * 0.12f
        }
    }
    return Silhouette(lobes, base, baseRadius)
}

private fun withAlpha(color: Int, alpha: Float) = (((alpha.coerceIn(0f, 1f) * 255).toInt()) shl 24) or (color and 0x00FFFFFF)

private fun Canvas.drawLobe(l: Lobe, paint: Paint) =
    drawOval(RectF(l.x - l.r, l.y - l.r * l.squash, l.x + l.r, l.y + l.r * l.squash), paint)

/**
 * Renders one cloud: a silhouette of lobes on a flat base, shaded from a lit top to a shaded
 * base with light and shade on its main lobes, then softened, thinned by a patchy density map
 * so the sky shows through, and trailed by faint wisps. Runs off the main thread.
 *
 * @param resolution Bitmap pixels per scene pixel; clouds are soft, so half resolution is plenty.
 */
internal fun buildCloudSprite(
    kind: CloudKind,
    seed: Int,
    w: Float,
    h: Float,
    palette: CloudPalette,
    lit: Boolean,
    resolution: Float
): CloudSprite {
    val rnd = Random(seed)
    val pad = h // room for puffs rising above the body and for the soft edge
    val cw = w + pad * 2
    val ch = h + pad * 2
    val bw = max(1, ceil(cw * resolution).toInt())
    val bh = max(1, ceil(ch * resolution).toInt())
    val colors = if (lit) LightningPalette else palette
    val shape = silhouette(kind, rnd, w, h, pad)

    fun layer(draw: Canvas.() -> Unit): Bitmap =
        Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { scale(resolution, resolution); draw() } }

    val body = layer {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        drawRoundRect(shape.base, shape.baseRadius, shape.baseRadius, fill)
        shape.lobes.forEach { drawLobe(it, fill) }

        val atop = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
        val top = shape.lobes.minOf { it.y - it.r * it.squash }
        atop.shader = LinearGradient(
            0f, top, 0f, pad + h,
            intArrayOf(colors.top, colors.body, colors.shade), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        drawRect(0f, 0f, cw, ch, atop)
        shape.lobes.filterNot { it.minor }.forEach { l ->
            val hx = l.x - l.r * 0.3f
            val hy = l.y - l.r * 0.45f
            atop.shader = RadialGradient(hx, hy, l.r * 0.95f, withAlpha(colors.highlight, 0.55f), withAlpha(colors.highlight, 0f), Shader.TileMode.CLAMP)
            drawCircle(hx, hy, l.r * 0.95f, atop)
            val sx = l.x + l.r * 0.35f
            val sy = l.y + l.r * 0.5f
            atop.shader = RadialGradient(sx, sy, l.r * 0.9f, withAlpha(colors.shade, 0.35f), withAlpha(colors.shade, 0f), Shader.TileMode.CLAMP)
            drawCircle(sx, sy, l.r * 0.9f, atop)
        }
    }

    val blurFactor = when (kind) {
        CloudKind.FOG -> 0.22f
        CloudKind.CUMULUS -> 0.06f
        else -> 0.1f
    }
    val soft = body.blurred(max(1f, h * blurFactor) * resolution)
    val canvas = Canvas(soft)

    if (kind != CloudKind.FOG) {
        // A defined edge under the blur.
        canvas.drawBitmap(body, 0f, 0f, Paint().apply { alpha = 89 })

        // Density map: a dense core with thinner, see-through patches.
        val floor = when (kind) {
            CloudKind.CUMULUS -> 0.42f
            CloudKind.STRATUS -> 0.28f
            CloudKind.NIMBUS -> 0.5f
            else -> 0.6f
        }
        val density = layer {
            drawColor(withAlpha(Color.BLACK, floor))
            val add = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
            fun blob(x: Float, y: Float, r: Float, a: Float) {
                add.shader = RadialGradient(x, y, r, withAlpha(Color.BLACK, a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                drawCircle(x, y, r, add)
            }
            shape.lobes.filterNot { it.minor }.forEach { l ->
                if (rnd.nextFloat() < 0.8f) blob(l.x + (rnd.nextFloat() - 0.5f) * l.r * 0.6f, l.y + l.r * 0.2f, l.r * rnd.between(0.9f, 1.4f), rnd.between(0.3f, 0.7f))
            }
            blob(pad + w * rnd.between(0.35f, 0.65f), pad + h * 0.7f, max(w * 0.28f, h * 0.6f), 0.35f)
        }
        canvas.drawBitmap(
            density.blurred(h * 0.18f * resolution), 0f, 0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        )

        // Wisps: thin, stretched, very soft trails off the sides and base, behind the body.
        val wisps = layer {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val count = when (kind) {
                CloudKind.CUMULUS -> 3
                CloudKind.STRATUS -> 5
                CloudKind.NIMBUS -> 4
                else -> 3
            }
            repeat(count) {
                val stretch = if (kind == CloudKind.CUMULUS) 1f else 1.6f
                val rx = min(pad * 0.9f, h * rnd.between(0.5f, 1.1f)) * stretch
                val ry = h * rnd.between(0.07f, 0.15f)
                val onSide = rnd.nextFloat() < 0.6f
                val x = when {
                    !onSide -> pad + w * rnd.between(0.2f, 0.8f)
                    rnd.nextBoolean() -> pad + w * 0.12f * rnd.nextFloat()
                    else -> pad + w - w * 0.12f * rnd.nextFloat()
                }
                val y = pad + h * if (onSide) rnd.between(0.5f, 0.8f) else rnd.between(0.85f, 0.97f)
                val reach = minOf(rx, x - 2f, cw - x - 2f)
                paint.color = withAlpha(colors.body, rnd.between(0.25f, 0.45f))
                save()
                rotate((rnd.nextFloat() - 0.5f) * 7f, x, y)
                drawOval(RectF(x - reach, y - ry, x + reach, y + ry), paint)
                restore()
            }
        }
        canvas.drawBitmap(
            wisps.blurred(h * 0.12f * resolution), 0f, 0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) }
        )
    }

    return CloudSprite(soft.asImageBitmap(), pad, cw, ch)
}
