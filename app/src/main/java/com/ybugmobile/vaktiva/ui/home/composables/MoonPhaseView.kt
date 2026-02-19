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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.domain.model.MoonPhase

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

    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp), // Doubled from 48.dp
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2.5f

                // Atmospheric Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(contentColor.copy(alpha = glowAlpha * 0.4f), Color.Transparent),
                        center = center,
                        radius = radius * 2f
                    ),
                    radius = radius * 2f,
                    center = center
                )

                // Background (Dark part of the moon)
                drawCircle(
                    color = contentColor.copy(alpha = 0.15f),
                    radius = radius,
                    center = center,
                    style = Fill
                )

                // The Illuminated Part (Calculated curve)
                val illumination = moonPhase.illumination.toFloat() // 0.0 to 1.0
                val path = Path()
                
                if (illumination > 0) {
                    if (illumination == 1f) {
                        drawCircle(contentColor, radius, center)
                    } else {
                        path.addArc(
                            oval = androidx.compose.ui.geometry.Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 180f
                        )
                        
                        val curveXControl = radius * (1f - 2f * illumination)
                        path.cubicTo(
                            center.x + curveXControl, center.y + radius,
                            center.x + curveXControl, center.y - radius,
                            center.x, center.y - radius
                        )
                        
                        drawPath(path, color = contentColor, style = Fill)
                    }
                }

                // Moon Outline
                drawCircle(
                    color = contentColor.copy(alpha = 0.3f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()) // Slightly thicker stroke for larger moon
                )
            }
        }
        
        Text(
            text = "${(moonPhase.illumination * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp // Increased from 10.sp for better visibility
            ),
            color = contentColor.copy(alpha = 0.8f)
        )
    }
}
