package com.ybugmobile.waktiva.ui.qibla.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    // 1. Adaptive Glow Color
    val indicatorColor by animateColorAsState(
        targetValue = if (isAligned) alignmentColor else Color(0xFFFFD700),
        animationSpec = tween(600),
        label = "indicator"
    )

    // 2. Shortest-Path Azimuth Animation
    var lastTargetAzimuth by remember { mutableStateOf(azimuth) }
    var azimuthOffset by remember { mutableStateOf(0f) }
    
    SideEffect {
        val diff = azimuth - lastTargetAzimuth
        if (diff > 180f) azimuthOffset -= 360f
        else if (diff < -180f) azimuthOffset += 360f
        lastTargetAzimuth = azimuth
    }

    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuth + azimuthOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "magneticAzimuth"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "compassEffects")
    
    // WHITE GLOW TAPPER ANIMATION
    val taperPulse by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "taperPulse"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(modifier = Modifier.size(340.dp), contentAlignment = Alignment.Center) {
        // Outer Ambient Glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer {
                    val scale = if (isAligned) glowScale else 1f
                    scaleX = scale
                    scaleY = scale
                    alpha = if (isAligned) 0.8f else 0.4f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            indicatorColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        // The Compass Dial
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = -animatedAzimuth }
                .drawWithCache {
                    onDrawWithContent {
                        val center = this.center
                        val radius = size.minDimension / 2 - 20.dp.toPx()

                        // A. DYNAMIC DIAL BACKGROUND (White Taper Glow when aligned)
                        if (isAligned) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White.copy(alpha = taperPulse),
                                        0.6f to Color.White.copy(alpha = taperPulse * 0.3f),
                                        1.0f to Color.Transparent
                                    ),
                                    center = center,
                                    radius = radius + 10.dp.toPx()
                                ),
                                radius = radius + 10.dp.toPx(),
                                style = Fill
                            )
                        } else {
                            drawCircle(
                                color = contentColor.copy(alpha = 0.03f),
                                radius = radius + 10.dp.toPx(),
                                style = Fill
                            )
                        }

                        // Ticks & Graduations
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
                                    isMajor -> contentColor.copy(alpha = 0.8f)
                                    isMedium -> contentColor.copy(alpha = 0.4f)
                                    else -> contentColor.copy(alpha = 0.15f)
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
                            
                            rotate(animatedAzimuth, pivot = Offset(x, y)) {
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
                                    topLeft = Offset(x - textLayout.size.width / 2, y - textLayout.size.height / 2)
                                )
                            }
                        }

                        // Kaaba Marker
                        val qiblaInRad = Math.toRadians(qiblaAngle.toDouble() - 90)
                        val kX = center.x + radius * cos(qiblaInRad).toFloat()
                        val kY = center.y + radius * sin(qiblaInRad).toFloat()
                        
                        drawCircle(
                            brush = Brush.radialGradient(listOf(indicatorColor.copy(alpha = 0.4f), Color.Transparent)),
                            radius = 24.dp.toPx() * (if (isAligned) glowScale else 1f),
                            center = Offset(kX, kY)
                        )
                        
                        drawCircle(color = Color.White, radius = 15.dp.toPx(), center = Offset(kX, kY))
                        drawCircle(color = Color(0xFFFFD700), radius = 13.dp.toPx(), center = Offset(kX, kY))
                        
                        val kaabaSize = 12.dp.toPx()
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(kX - kaabaSize / 2, kY - kaabaSize / 2),
                            size = androidx.compose.ui.geometry.Size(kaabaSize, kaabaSize)
                        )
                        drawRect(
                            color = Color(0xFFFFD700),
                            topLeft = Offset(kX - kaabaSize / 2, kY - kaabaSize / 4),
                            size = androidx.compose.ui.geometry.Size(kaabaSize, kaabaSize / 8)
                        )
                    }
                }
        )

        // Static Center Needle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2 - 20.dp.toPx()
            
            val needleWidth = 4.dp.toPx()
            val needleHeight = radius * 0.85f
            val tailHeight = 20.dp.toPx()

            val mainNeedlePath = Path().apply {
                moveTo(center.x, center.y - needleHeight)
                lineTo(center.x + needleWidth, center.y)
                lineTo(center.x, center.y + tailHeight)
                lineTo(center.x - needleWidth, center.y)
                close()
            }

            // Shadow
            translate(1f, 1f) {
                drawPath(mainNeedlePath, Color.Black.copy(alpha = 0.1f))
            }

            // Body
            drawPath(
                path = mainNeedlePath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFF5F5F5), Color(0xFFDDDDDD)),
                    startY = center.y - needleHeight,
                    endY = center.y + tailHeight
                )
            )

            // North Tip Accent
            val northTipPath = Path().apply {
                moveTo(center.x, center.y - needleHeight)
                lineTo(center.x + needleWidth, center.y - needleHeight * 0.7f)
                lineTo(center.x - needleWidth, center.y - needleHeight * 0.7f)
                close()
            }
            drawPath(path = northTipPath, color = Color(0xFFF87171).copy(alpha = 0.2f))

            // Pivot
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = center)
            drawCircle(color = indicatorColor, radius = 2.5.dp.toPx(), center = center)
        }
    }
}
