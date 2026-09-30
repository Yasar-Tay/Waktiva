package com.ybugmobile.waktiva.ui.donation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.ui.home.composables.MoonTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The Moon at [phase] (0 new, 0.25 first quarter, 0.5 full) as a small icon: the same surface as
 * the home screen's moon, lit for the phase, in a faint glow that grows with the light. The night
 * side shows as dim earthshine, so a thin crescent still reads as a whole moon.
 *
 * Rendered off the main thread; the glow shows on its own until the surface is ready.
 */
@Composable
internal fun MoonIcon(phase: Double, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val diameterPx = with(LocalDensity.current) { (size * DiscShare).roundToPx() }
    val image by produceState<ImageBitmap?>(null, diameterPx, phase) {
        value = withContext(Dispatchers.Default) {
            val surface = MoonTexture(diameterPx)
            Bitmap.createBitmap(surface.light(phase), diameterPx, diameterPx, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    val illumination = ((1 - cos(2 * PI * phase)) / 2).toFloat()

    Canvas(modifier.size(size)) {
        val radius = diameterPx / 2f
        val reach = this.size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                0f to MoonLight.copy(alpha = 0.1f + 0.3f * illumination),
                radius / reach to MoonLight.copy(alpha = 0.06f + 0.22f * illumination),
                1f to MoonLight.copy(alpha = 0f),
                center = center,
                radius = reach
            ),
            radius = reach
        )
        image?.let {
            drawImage(
                it,
                dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
                dstSize = IntSize(diameterPx, diameterPx)
            )
        }
    }
}

/** Share of the icon the disc spans; the rest is room for its glow. */
private const val DiscShare = 0.78f

/** Warm white of the moonlight, as around the home screen's moon. */
private val MoonLight = Color(0xFFFFF4DC)
