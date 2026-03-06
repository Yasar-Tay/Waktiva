package com.ybugmobile.waktiva.ui.qibla.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun QiblaAlignmentEffect(
    isAligned: Boolean,
    alignmentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cinematicAlignmentEffect")
    
    // Master Timer for optimized particle rain
    val masterProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "masterTimer"
    )

    val particleStates = (0 until 35).map { index ->
        val duration = remember { Random.nextInt(5000, 8000) }
        val delay = remember { Random.nextInt(0, 4000) }
        val startXOffset = remember { Random.nextFloat() * 120f - 60f }
        val size = remember { Random.nextFloat() * 1.2f + 0.4f } 
        val driftDirection = remember { if (Random.nextBoolean()) 1f else -1f }
        
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "p$index"
        )
        
        ParticleState(progress, startXOffset, size, driftDirection)
    }

    AnimatedVisibility(
        visible = isAligned,
        enter = fadeIn(tween(1500)),
        exit = fadeOut(tween(1000)),
        modifier = modifier.fillMaxSize()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val center = this.center
            val radius = size.minDimension / 2 - 20.dp.toPx()
            
            // 1. PILLAR GEOMETRY
            val rayHeight = radius * 3.2f
            val rayTopWidth = 8.dp.toPx() * 2 
            val rayBaseWidth = radius * 0.9f
            
            val rayPath = Path().apply {
                moveTo(center.x - rayTopWidth / 2, center.y - rayHeight)
                lineTo(center.x + rayTopWidth / 2, center.y - rayHeight)
                lineTo(center.x + rayBaseWidth / 2, center.y + 60.dp.toPx())
                lineTo(center.x - rayBaseWidth / 2, center.y + 60.dp.toPx())
                close()
            }

            // A. Static Energy Pillar - Dynamic color based on time of day
            val pillarHalfWidth = 42.dp.toPx()
            drawPath(
                path = rayPath,
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.35f to alignmentColor.copy(alpha = 0.15f),
                        0.5f to alignmentColor.copy(alpha = 0.55f),
                        0.65f to alignmentColor.copy(alpha = 0.15f),
                        1.0f to Color.Transparent
                    ),
                    startX = center.x - pillarHalfWidth,
                    endX = center.x + pillarHalfWidth
                ),
                blendMode = BlendMode.Plus
            )

            // B. CINEMATIC VERTICAL FADE
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White,
                        0.5f to Color.White, 
                        0.8f to Color.White.copy(alpha = 0.3f),
                        0.95f to Color.Transparent 
                    ),
                    startY = center.y - rayHeight,
                    endY = center.y + 60.dp.toPx()
                ),
                blendMode = BlendMode.DstIn
            )

            // 2. RAINING LIGHT BULBS - Updated with "scatter through center" logic
            particleStates.forEach { state ->
                val p = state.progress
                // Increase fall distance so they pass the center
                val particleY = (center.y - radius * 1.6f) + (radius * 2.2f * p)
                
                // Converge then scatter logic
                // They reach center horizontal alignment around p=0.7
                val horizontalFactor = if (p < 0.7f) {
                    (1f - (p / 0.7f)) // Converging
                } else {
                    ((p - 0.7f) / 0.3f) * 0.3f // Scattering slightly back out after passing center
                }
                
                val particleX = center.x + (state.startXOffset.dp.toPx() * horizontalFactor) + 
                                (state.driftDirection * 10.dp.toPx() * p) // Added a bit of random drift
                
                // Fading out logic: they disappear after passing the center
                val alpha = when {
                    p < 0.15f -> p / 0.15f // Fade in at top
                    p > 0.85f -> (1f - p) / 0.15f // Fade out late, after scattering through center
                    else -> 1f
                }

                if (alpha > 0f) {
                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.8f),
                        radius = state.size.dp.toPx(),
                        center = Offset(particleX, particleY),
                        blendMode = BlendMode.Plus
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = alpha * 0.2f), Color.Transparent),
                            radius = state.size.dp.toPx() * 4f,
                            center = Offset(particleX, particleY)
                        ),
                        radius = state.size.dp.toPx() * 4f,
                        center = Offset(particleX, particleY),
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }
    }
}

private data class ParticleState(
    val progress: Float,
    val startXOffset: Float,
    val size: Float,
    val driftDirection: Float
)
