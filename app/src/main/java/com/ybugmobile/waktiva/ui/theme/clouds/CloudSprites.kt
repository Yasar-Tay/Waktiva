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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal enum class CloudKind { STRATUS, NIMBUS, FOG }

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

/**
 * Lobes and base of a cloud of [w] x [h] placed [pad] in from the sprite's edges: a flat,
 * elongated row of squashed lobes, so every kind reads as a layer rather than a heap.
 */
private fun silhouette(kind: CloudKind, rnd: Random, w: Float, h: Float, pad: Float): Silhouette {
    val lobes = mutableListOf<Lobe>()
    val n = if (kind == CloudKind.NIMBUS) 11 else 13
    val (thick, spread) = when (kind) {
        CloudKind.NIMBUS -> 0.34f to 0.26f
        CloudKind.FOG -> 0.3f to 0.18f
        CloudKind.STRATUS -> 0.24f to 0.2f
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
    return Silhouette(lobes, RectF(pad + w * 0.04f, pad + h * 0.45f, pad + w * 0.96f, pad + h * 0.85f), h * 0.2f)
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
    val pad = h // room for the soft edge and the wisps
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

    val blurFactor = if (kind == CloudKind.FOG) 0.22f else 0.1f
    val soft = body.blurred(max(1f, h * blurFactor) * resolution)
    val canvas = Canvas(soft)

    if (kind != CloudKind.FOG) {
        // A defined edge under the blur.
        canvas.drawBitmap(body, 0f, 0f, Paint().apply { alpha = 89 })

        // Density map: a dense core with thinner, see-through patches.
        val floor = if (kind == CloudKind.NIMBUS) 0.5f else 0.28f
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
            repeat(if (kind == CloudKind.NIMBUS) 4 else 5) {
                val rx = min(pad * 0.9f, h * rnd.between(0.5f, 1.1f)) * 1.6f
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

/**
 * Fades this sprite, drawn with its top edge at [top] in the scene, out down the sky band that
 * ends at [bottom] (see [SkyFade]). Clouds only drift sideways, so the fade is baked in once here
 * instead of fading the whole sky through an offscreen layer every frame.
 */
internal fun CloudSprite.fadeDownSky(top: Float, bottom: Float) {
    val bitmap = image.asAndroidBitmap()
    val resolution = bitmap.width / width
    val mask = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        shader = LinearGradient(
            0f, 0f, 0f, bottom,
            SkyFade.map { (_, alpha) -> withAlpha(Color.BLACK, alpha) }.toIntArray(),
            SkyFade.map { (at, _) -> at }.toFloatArray(),
            Shader.TileMode.CLAMP
        )
    }
    val spriteWidth = width
    val spriteHeight = height
    Canvas(bitmap).apply {
        scale(resolution, resolution)
        translate(0f, -top)
        drawRect(0f, top, spriteWidth, top + spriteHeight, mask)
    }
}
