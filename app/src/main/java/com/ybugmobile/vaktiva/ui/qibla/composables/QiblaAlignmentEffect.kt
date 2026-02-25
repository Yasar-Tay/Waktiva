package com.ybugmobile.vaktiva.ui.qibla.composables

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
    modifier: Modifier = Modifier
) {
    // 1. MASTER TIMER: Only ONE state change per frame instead of 35+
    val infiniteTransition = rememberInfiniteTransition(label = "performanceEffect")
    val masterProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "masterTimer"
    )

    val rayAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rayAlpha"
    )

    // Permanent particle metadata (Non-animated constants)
    val particles = remember {
        List(35) {
            ParticleMeta(
                startXOffset = Random.nextFloat() * 120f - 60f,
                speedMultiplier = Random.nextFloat() * 0.5f + 0.8f, // Varied falling speeds
                delayOffset = Random.nextFloat(), // Individual start times
                size = Random.nextFloat() * 1.2f + 0.4f
            )
        }
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
            
            // --- 1. GOD RAY (Volumetric focus) ---
            val rayHeight = radius * 3.2f
            val rayTopWidth = 2.dp.toPx()
            val rayBaseWidth = radius * 0.9f
            
            val rayPath = Path().apply {
                moveTo(center.x - rayTopWidth / 2, center.y - rayHeight)
                lineTo(center.x + rayTopWidth / 2, center.y - rayHeight)
                lineTo(center.x + rayBaseWidth / 2, center.y + 60.dp.toPx())
                lineTo(center.x - rayBaseWidth / 2, center.y + 60.dp.toPx())
                close()
            }

            // Atmospheric white beam
            drawPath(
                path = rayPath,
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = rayAlpha * 0.45f),
                        0.6f to Color.White.copy(alpha = rayAlpha * 0.15f),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(center.x, center.y - rayHeight * 0.5f),
                    radius = rayHeight * 0.7f
                ),
                blendMode = BlendMode.Screen
            )

            // Static Green Energy Pillar
            val pillarHalfWidth = 42.dp.toPx()
            drawPath(
                path = rayPath,
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.35f to Color(0xFF4CAF50).copy(alpha = 0.1f),
                        0.5f to Color(0xFF4CAF50).copy(alpha = 0.55f),
                        0.65f to Color(0xFF4CAF50).copy(alpha = 0.1f),
                        1.0f to Color.Transparent
                    ),
                    startX = center.x - pillarHalfWidth,
                    endX = center.x + pillarHalfWidth
                ),
                blendMode = BlendMode.Plus
            )

            // VERTICAL MASK (Corrected DstIn logic)
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

            // --- 2. OPTIMIZED RAINING BULBS ---
            particles.forEach { meta ->
                // Calculate individual progress based on Master Timer + individual delay
                val p = (masterProgress * meta.speedMultiplier + meta.delayOffset) % 1f
                
                val particleY = (center.y - radius * 1.6f) + (radius * 1.6f * p)
                val particleX = center.x + (meta.startXOffset.dp.toPx() * (1f - p) * 0.5f)
                
                val alpha = when {
                    p < 0.15f -> p / 0.15f 
                    p > 0.6f -> (1f - p) / 0.4f
                    else -> 1f
                }

                if (alpha > 0f) {
                    // Optimized bulb core
                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.8f),
                        radius = meta.size.dp.toPx(),
                        center = Offset(particleX, particleY),
                        blendMode = BlendMode.Plus
                    )
                    // High-performance light glow (Single pass)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = alpha * 0.2f), Color.Transparent),
                            radius = meta.size.dp.toPx() * 4f,
                            center = Offset(particleX, particleY)
                        ),
                        radius = meta.size.dp.toPx() * 4f,
                        center = Offset(particleX, particleY),
                        blendMode = BlendMode.Screen
                    )
                }
            }
            
            // 3. SOURCE FLARE
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = rayAlpha * 0.5f), Color.Transparent),
                    center = Offset(center.x, center.y - rayHeight),
                    radius = 45.dp.toPx()
                ),
                center = Offset(center.x, center.y - rayHeight),
                radius = 45.dp.toPx(),
                blendMode = BlendMode.Plus
            )
        }
    }
}

private data class ParticleMeta(
    val startXOffset: Float,
    val speedMultiplier: Float,
    val delayOffset: Float,
    val size: Float
)
