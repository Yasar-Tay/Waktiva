package com.ybugmobile.waktiva.ui.home.composables

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.domain.model.MoonPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Moon as it looks tonight: a realistic near side ([MoonTexture]) lit for the current phase,
 * turned by its parallactic angle so the crescent tilts as it does in the sky, in a soft bloom
 * of moonlight that breathes slowly and grows with the illumination.
 *
 * The surface is rendered off the main thread, once per size; each new phase only reshades it.
 *
 * @param moonPhase Data object containing illumination percentage, phase progress, and angle.
 * @param contentColor Colour of the label.
 * @param modifier Layout modifier.
 */
@Composable
fun MoonPhaseView(
    moonPhase: MoonPhase?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    if (moonPhase == null) return

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Subtle atmospheric glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "moonGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val diameterPx = with(LocalDensity.current) { (MoonBox * DiscShare).roundToPx() }
    val texture by produceState<MoonTexture?>(null, diameterPx) {
        value = withContext(Dispatchers.Default) { MoonTexture(diameterPx) }
    }
    // Reshade only when the phase has moved visibly (the view updates hourly).
    val phaseStep = (moonPhase.phaseProgress * 1000).roundToInt()
    val moonImage by produceState<ImageBitmap?>(null, texture, phaseStep) {
        val surface = texture ?: return@produceState
        value = withContext(Dispatchers.Default) {
            Bitmap.createBitmap(surface.light(moonPhase.phaseProgress), surface.size, surface.size, Bitmap.Config.ARGB_8888)
                .asImageBitmap()
        }
    }

    val moonContent = @Composable {
        Box(
            modifier = Modifier.size(MoonBox),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension * DiscShare / 2f
                // Moonlight bloom: bright at the limb, fading out over a disc's width; stronger the
                // fuller the moon, and breathing slowly. A fixed moonlight colour, so it glows the
                // same whatever colour the text is.
                val bloom = (0.3f + 0.7f * moonPhase.illumination.toFloat()) * (0.75f + 0.5f * glowAlpha)
                val reach = radius * 2.2f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to MoonLight.copy(alpha = 0.5f * bloom),
                        radius / reach to MoonLight.copy(alpha = 0.42f * bloom),
                        radius * 1.3f / reach to MoonLight.copy(alpha = 0.16f * bloom),
                        1f to MoonLight.copy(alpha = 0f),
                        center = center,
                        radius = reach
                    ),
                    radius = reach,
                    center = center
                )
                moonImage?.let { image ->
                    rotate(moonPhase.parallacticAngle.toFloat(), center) {
                        drawImage(
                            image,
                            dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
                            dstSize = IntSize((radius * 2).roundToInt(), (radius * 2).roundToInt())
                        )
                    }
                }
            }
        }
    }

    val labelContent = @Composable {
        Text(
            text = String.format(Locale.US, "%.1f%%", moonPhase.illumination * 100),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = contentColor.copy(alpha = 0.8f)
        )
    }

    if (isLandscape) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            moonContent()
            labelContent()
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            moonContent()
            labelContent()
        }
    }
}

private val MoonBox = 96.dp

/** Share of the box the lunar disc spans; the rest is room for its glow. */
private const val DiscShare = 0.8f

/** Warm white of the moonlight bloom. */
private val MoonLight = Color(0xFFFFF4DC)
