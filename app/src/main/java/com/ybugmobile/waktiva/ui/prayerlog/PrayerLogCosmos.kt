package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The deep sky the prayer log is drawn on: a band of nebula across the dark, violet with blue and
 * green gas in it and lanes of dust, and stars all over it (a still painted from fractal noise,
 * R.drawable.cetele_cosmos), with a few more stars twinkling over it.
 *
 * The still covers the screen and a little more below it: as the page scrolls ([scroll], in
 * pixels) it drifts up more slowly than the page does, deep behind it.
 */
@Composable
internal fun CosmicSky(modifier: Modifier = Modifier, scroll: () -> Int = { 0 }) {
    val image = ImageBitmap.imageResource(R.drawable.cetele_cosmos)
    val twinkle = rememberInfiniteTransition(label = "cosmosTwinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "cosmosTwinkle"
    )
    val stars = remember {
        val random = Random(17)
        List(60) {
            CosmicStar(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = if (random.nextFloat() < 0.8f) 0.7f else 1.2f,
                alpha = 0.35f + random.nextFloat() * 0.55f,
                phase = random.nextFloat(),
                warm = random.nextFloat() < 0.3f
            )
        }
    }
    Spacer(
        modifier.drawWithCache {
            val drift = 120.dp.toPx()
            // Cover the screen and the drift below it, cropping the sides if the screen is wider.
            val scale = max(size.width / image.width, (size.height + drift) / image.height)
            val dst = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt())
            val left = ((size.width - dst.width) / 2f).roundToInt()

            onDrawBehind {
                val shift = (scroll() * 0.08f).coerceIn(0f, drift)
                drawRect(Color(0xFF03030A))
                drawImage(image, dstOffset = IntOffset(left, -shift.roundToInt()), dstSize = dst, filterQuality = FilterQuality.Medium)

                val t = twinkle.value
                stars.forEach { star ->
                    val shimmer = 0.5f + 0.5f * sin(2f * PI.toFloat() * (t + star.phase))
                    drawCircle(
                        (if (star.warm) Color(0xFFFFE2C4) else Color.White).copy(alpha = star.alpha * shimmer),
                        radius = star.radius.dp.toPx(),
                        center = Offset(star.x * size.width, star.y * size.height)
                    )
                }
            }
        }
    )
}

private class CosmicStar(val x: Float, val y: Float, val radius: Float, val alpha: Float, val phase: Float, val warm: Boolean)
