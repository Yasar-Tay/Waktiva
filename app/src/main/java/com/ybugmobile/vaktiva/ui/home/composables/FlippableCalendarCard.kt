package com.ybugmobile.vaktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.ui.theme.IBMPlexArabic
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FlippableCalendarCard(
    day: PrayerDay,
    contentColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var isFlipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "cardFlip"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "celestial_vibes")
    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auraRotation"
    )
    
    val flarePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flarePulse"
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardSize = if (isLandscape) 80.dp else 94.dp

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }

    Box(
        modifier = modifier
            .size(cardSize + 54.dp) // Ample space for atmospheric "Sun" rays
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isFlipped = !isFlipped
            },
        contentAlignment = Alignment.Center
    ) {
        // ATMOSPHERIC SUN / STAR BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val radius = (cardSize.toPx() / 2)
                    
                    // 1. Core Radiant Bloom (The Sun's Body)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.4f * flarePulse),
                                accentColor.copy(alpha = 0.1f * flarePulse),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 1.7f
                        )
                    )

                    // 2. Rotating Corona Rays
                    rotate(auraRotation) {
                        val rayCount = 12
                        val innerRayRadius = radius * 1.05f
                        val outerRayRadius = radius * 1.55f
                        
                        for (i in 0 until rayCount) {
                            val angle = i * (360f / rayCount)
                            val angleRad = Math.toRadians(angle.toDouble())
                            
                            val startX = center.x + innerRayRadius * cos(angleRad).toFloat()
                            val startY = center.y + innerRayRadius * sin(angleRad).toFloat()
                            val endX = center.x + outerRayRadius * cos(angleRad).toFloat()
                            val endY = center.y + outerRayRadius * sin(angleRad).toFloat()

                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.5f * flarePulse), Color.Transparent),
                                    start = Offset(startX, startY),
                                    end = Offset(endX, endY)
                                ),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // 3. Faint Orbital Shimmer
                    drawCircle(
                        color = accentColor.copy(alpha = 0.2f * flarePulse),
                        radius = radius * 1.2f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    
                    drawCircle(
                        color = accentColor.copy(alpha = 0.1f),
                        radius = radius * 1.4f,
                        style = Stroke(
                            width = 0.8.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 16f), 0f)
                        )
                    )
                }
        )

        // THE CALENDAR DISC (The Moon/Planet surface feel)
        Box(
            modifier = Modifier
                .size(cardSize)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 15f * density
                }
        ) {
            if (rotation <= 90f) {
                CalendarSide(
                    topText = day.date.format(dayFormatter),
                    bottomText = day.date.format(monthFormatter).uppercase(Locale.getDefault()),
                    contentColor = contentColor,
                    isBack = false,
                    accentColor = accentColor,
                    isLandscape = isLandscape
                )
            } else {
                val hijri = day.hijriDate
                val monthResId = hijri?.let {
                    context.resources.getIdentifier("hijri_month_${it.monthNumber}", "string", context.packageName)
                } ?: 0
                val monthName = if (monthResId != 0) stringResource(monthResId) else hijri?.monthEn ?: ""
                val abbreviatedHijriMonth = monthName.take(3).uppercase(Locale.getDefault())

                CalendarSide(
                    topText = hijri?.day?.toString() ?: "",
                    bottomText = abbreviatedHijriMonth,
                    contentColor = contentColor,
                    isBack = true,
                    accentColor = accentColor,
                    isLandscape = isLandscape
                )
            }
        }
    }
}

@Composable
private fun CalendarSide(
    topText: String,
    bottomText: String,
    contentColor: Color,
    isBack: Boolean,
    accentColor: Color,
    isLandscape: Boolean
) {
    val dayFontSize = if (isLandscape) 30.sp else 34.sp
    val monthFontSize = if (isLandscape) 8.sp else 9.sp

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { if (isBack) rotationY = 180f },
        color = Color.White.copy(alpha = 0.12f), // Semi-transparent glass
        shape = CircleShape,
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.6f), Color.Transparent, accentColor.copy(alpha = 0.2f))
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(if (isLandscape) 4.dp else 8.dp)
            ) {
                Text(
                    text = topText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = dayFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = IBMPlexArabic,
                        letterSpacing = (-1).sp
                    ),
                    color = contentColor,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = bottomText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontSize = monthFontSize
                    ),
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
