package com.ybugmobile.vaktiva.ui.qibla.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    isAligned: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2
            val innerRadius = radius * 0.9f

            drawCircle(
                color = onSurface.copy(alpha = 0.05f),
                radius = radius,
                style = Fill
            )
            drawCircle(
                color = onSurface.copy(alpha = 0.15f),
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )

            rotate(-azimuth) {
                for (i in 0 until 360 step 5) {
                    val angleInRad = Math.toRadians(i.toDouble() - 90)
                    val tickLength = if (i % 30 == 0) 15.dp.toPx() else 8.dp.toPx()
                    val startX = center.x + (radius - 5.dp.toPx()) * cos(angleInRad).toFloat()
                    val startY = center.y + (radius - 5.dp.toPx()) * sin(angleInRad).toFloat()
                    val endX =
                        center.x + (radius - 5.dp.toPx() - tickLength) * cos(angleInRad).toFloat()
                    val endY =
                        center.y + (radius - 5.dp.toPx() - tickLength) * sin(angleInRad).toFloat()

                    drawLine(
                        color = if (i % 30 == 0) onSurface.copy(alpha = 0.4f) else onSurface.copy(
                            alpha = 0.15f
                        ),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (i % 30 == 0) 2.dp.toPx() else 1.dp.toPx()
                    )
                }

                val directions = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                directions.forEach { (label, angle) ->
                    val angleInRad = Math.toRadians(angle.toDouble() - 90)
                    val x = center.x + (innerRadius - 20.dp.toPx()) * cos(angleInRad).toFloat()
                    val y = center.y + (innerRadius - 20.dp.toPx()) * sin(angleInRad).toFloat()
                    rotate(azimuth, pivot = Offset(x, y)) {
                        val textLayout = textMeasurer.measure(
                            text = label,
                            style = TextStyle(
                                color = if (label == "N") Color.Red else onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
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

                val qiblaInRad = Math.toRadians(qiblaAngle.toDouble() - 90)
                val kX = center.x + radius * cos(qiblaInRad).toFloat()
                val kY = center.y + radius * sin(qiblaInRad).toFloat()
                drawCircle(
                    color = Color(0xFFFFD700),
                    radius = 8.dp.toPx(),
                    center = Offset(kX, kY)
                )
                drawCircle(
                    color = onSurface,
                    radius = 8.dp.toPx(),
                    center = Offset(kX, kY),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            val needlePath = Path().apply {
                moveTo(center.x, center.y - radius * 0.75f)
                lineTo(center.x + 12.dp.toPx(), center.y)
                lineTo(center.x, center.y + 20.dp.toPx())
                lineTo(center.x - 12.dp.toPx(), center.y)
                close()
            }
            drawPath(path = needlePath, color = alignmentColor)
            val shadowPath = Path().apply {
                moveTo(center.x, center.y - radius * 0.75f)
                lineTo(center.x + 12.dp.toPx(), center.y)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(path = shadowPath, color = Color.Black.copy(alpha = 0.1f))
            drawCircle(color = onSurface, radius = 4.dp.toPx())
            drawCircle(color = Color.White, radius = 2.dp.toPx())
        }

        AnimatedVisibility(
            visible = isAligned,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        ) {
            Surface(
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = stringResource(R.string.qibla_aligned_badge),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}