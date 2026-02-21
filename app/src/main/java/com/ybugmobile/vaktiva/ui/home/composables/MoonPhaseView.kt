package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.MoonPhase
import java.util.Locale

@Composable
fun MoonPhaseView(
    moonPhase: MoonPhase?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    if (moonPhase == null) return

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

    val surfacePainter = rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_moon_surface))

    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2.5f

                // Use the parallactic angle from the accurate SunCalc calculation
                // to rotate the moon exactly as it appears in the sky
                val rotationAngle = moonPhase.parallacticAngle.toFloat()

                withTransform({
                    rotate(degrees = rotationAngle, pivot = center)
                }) {
                    // 1. Atmospheric Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(contentColor.copy(alpha = glowAlpha * 0.4f), Color.Transparent),
                            center = center,
                            radius = radius * 2f
                        ),
                        radius = radius * 2f,
                        center = center
                    )

                    val moonClipPath = Path().apply {
                        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                    }

                    clipPath(moonClipPath) {
                        // 2. Dark Background (The unlit part of the moon)
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.4f), 
                            radius = radius,
                            center = center,
                            style = Fill
                        )

                        // 3. The Illuminated Part (Mask)
                        val illumination = moonPhase.illumination.toFloat()
                        val isWaning = moonPhase.phaseProgress >= 0.5

                        withTransform({
                            if (isWaning) {
                                scale(scaleX = -1f, scaleY = 1f, pivot = center)
                            }
                        }) {
                            if (illumination > 0) {
                                if (illumination >= 0.98f) {
                                    drawCircle(contentColor.copy(alpha = 0.85f), radius, center)
                                } else {
                                    val maskPath = Path()
                                    maskPath.addArc(
                                        oval = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius),
                                        startAngleDegrees = -90f,
                                        sweepAngleDegrees = 180f
                                    )
                                    
                                    val curveXControl = radius * (1f - 2f * illumination)
                                    maskPath.cubicTo(
                                        center.x + curveXControl, center.y + radius,
                                        center.x + curveXControl, center.y - radius,
                                        center.x, center.y - radius
                                    )
                                    
                                    drawPath(maskPath, color = contentColor.copy(alpha = 0.85f), style = Fill)
                                }
                            }
                        }

                        // 4. Moon Surface Texture
                        withTransform({
                            translate(center.x - radius, center.y - radius)
                        }) {
                            with(surfacePainter) {
                                draw(
                                    size = Size(radius * 2, radius * 2),
                                    alpha = 0.5f 
                                )
                            }
                        }
                    }

                    // 5. Moon Outline
                    drawCircle(
                        color = contentColor.copy(alpha = 0.3f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
        
        Text(
            text = String.format(Locale.US, "%.1f%%", moonPhase.illumination * 100),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = contentColor.copy(alpha = 0.8f)
        )
    }
}
