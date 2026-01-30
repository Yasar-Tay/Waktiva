package com.ybugmobile.vaktiva.ui.qibla.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ProfessionalCompass(
    azimuth: Float,
    qiblaAngle: Float,
    alignmentColor: Color,
    isAligned: Boolean,
    contentColor: Color = Color.White
) {
    val textMeasurer = rememberTextMeasurer()
    val indicatorColor by animateColorAsState(
        targetValue = if (isAligned) Color(0xFF4CAF50) else Color(0xFFFFD700),
        animationSpec = tween(500),
        label = "indicator"
    )

    Box(modifier = Modifier.size(340.dp), contentAlignment = Alignment.Center) {
        // Outer glow layer
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            indicatorColor.copy(alpha = if (isAligned) 0.15f else 0.05f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2 - 20.dp.toPx()
            val innerRadius = radius * 0.85f

            // 1. Static Outer Ring (Glass Effect)
            drawCircle(
                color = contentColor.copy(alpha = 0.05f),
                radius = radius + 10.dp.toPx(),
                style = Fill
            )
            drawCircle(
                brush = Brush.sweepGradient(
                    0.0f to contentColor.copy(alpha = 0f),
                    0.5f to contentColor.copy(alpha = 0.2f),
                    1.0f to contentColor.copy(alpha = 0f)
                ),
                radius = radius + 10.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. Rotating Scale
            rotate(-azimuth) {
                for (i in 0 until 360 step 2) {
                    val angleInRad = Math.toRadians(i.toDouble() - 90)
                    val isMajor = i % 30 == 0
                    val isMedium = i % 10 == 0
                    
                    val tickLength = when {
                        isMajor -> 16.dp.toPx()
                        isMedium -> 10.dp.toPx()
                        else -> 5.dp.toPx()
                    }
                    
                    val startX = center.x + radius * cos(angleInRad).toFloat()
                    val startY = center.y + radius * sin(angleInRad).toFloat()
                    val endX = center.x + (radius - tickLength) * cos(angleInRad).toFloat()
                    val endY = center.y + (radius - tickLength) * sin(angleInRad).toFloat()

                    drawLine(
                        color = when {
                            isMajor -> contentColor.copy(alpha = 0.9f)
                            isMedium -> contentColor.copy(alpha = 0.5f)
                            else -> contentColor.copy(alpha = 0.2f)
                        },
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Cardinal Directions
                val directions = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                directions.forEach { (label, angle) ->
                    val angleInRad = Math.toRadians(angle.toDouble() - 90)
                    val x = center.x + (radius - 32.dp.toPx()) * cos(angleInRad).toFloat()
                    val y = center.y + (radius - 32.dp.toPx()) * sin(angleInRad).toFloat()
                    
                    rotate(azimuth, pivot = Offset(x, y)) {
                        val textLayout = textMeasurer.measure(
                            text = label,
                            style = TextStyle(
                                color = if (label == "N") Color(0xFFF87171) else contentColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(
                                x - textLayout.size.width / 2,
                                y - textLayout.size.height / 2
                            )
                        )
                    }
                }

                // 3. Qibla Marker (Kaaba)
                val qiblaInRad = Math.toRadians(qiblaAngle.toDouble() - 90)
                val kX = center.x + radius * cos(qiblaInRad).toFloat()
                val kY = center.y + radius * sin(qiblaInRad).toFloat()
                
                // Glow behind marker
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(indicatorColor.copy(alpha = 0.6f), Color.Transparent)
                    ),
                    radius = 20.dp.toPx(),
                    center = Offset(kX, kY)
                )
                
                // The Marker
                drawCircle(
                    color = indicatorColor,
                    radius = 6.dp.toPx(),
                    center = Offset(kX, kY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(kX, kY),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 4. Modern Central Needle (Static)
            val needleWidth = 6.dp.toPx()
            val needleHeight = radius * 0.7f
            
            // Top half (Main Pointer)
            val topNeedlePath = Path().apply {
                moveTo(center.x, center.y - needleHeight)
                lineTo(center.x + needleWidth, center.y)
                lineTo(center.x - needleWidth, center.y)
                close()
            }
            
            // Bottom half (Tail)
            val bottomNeedlePath = Path().apply {
                moveTo(center.x, center.y + 12.dp.toPx())
                lineTo(center.x + needleWidth, center.y)
                lineTo(center.x - needleWidth, center.y)
                close()
            }

            drawPath(
                path = topNeedlePath,
                brush = Brush.verticalGradient(
                    listOf(alignmentColor, alignmentColor.copy(alpha = 0.7f))
                )
            )
            drawPath(
                path = bottomNeedlePath,
                color = contentColor.copy(alpha = 0.2f)
            )
            
            // Center Pivot
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = center
            )
            drawCircle(
                color = alignmentColor,
                radius = 5.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Aligned Badge
        AnimatedVisibility(
            visible = isAligned,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.9f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
            ) {
                Text(
                    text = stringResource(R.string.qibla_aligned_badge),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
