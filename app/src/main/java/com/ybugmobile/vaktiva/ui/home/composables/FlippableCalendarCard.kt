package com.ybugmobile.vaktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
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
import com.ybugmobile.vaktiva.ui.theme.LocalGlassTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

@Composable
fun FlippableCalendarCard(
    day: PrayerDay,
    isHijriVisible: Boolean,
    onFlip: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    currentTime: LocalTime,
    isSelectedDayToday: Boolean,
    pulseScale: Float,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isHijriVisible) 180f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "cardFlip"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "gravity_star")
    
    // Core Gravity Pulse
    val coreGlow by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreGlow"
    )

    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starRotation"
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val cardWidth = if (isLandscape) 82.dp else 100.dp
    val cardHeight = cardWidth

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }

    Box(
        modifier = modifier
            .width(cardWidth + 80.dp)
            .height(cardHeight + 80.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFlip()
            },
        contentAlignment = Alignment.Center
    ) {
        // GRAVITY WELL (External Atmosphere)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val baseRadius = (cardWidth.toPx() / 2)
                    
                    // Intense Solar corona
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to accentColor.copy(alpha = 0.5f * coreGlow),
                            0.4f to accentColor.copy(alpha = 0.2f),
                            0.8f to accentColor.copy(alpha = 0.05f),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = baseRadius * 3.5f * coreGlow
                        ),
                        radius = baseRadius * 3.5f * coreGlow,
                        blendMode = BlendMode.Screen
                    )

                    // Rotating Magnetic Prominences
                    rotate(starRotation) {
                        repeat(12) { i ->
                            val angle = Math.toRadians((i * 30).toDouble())
                            val flareLen = baseRadius * (1.1f + 0.3f * sin(starRotation * 0.1 + i).toFloat())
                            drawLine(
                                color = accentColor.copy(alpha = 0.3f),
                                start = Offset(
                                    center.x + baseRadius * cos(angle).toFloat(),
                                    center.y + baseRadius * sin(angle).toFloat()
                                ),
                                end = Offset(
                                    center.x + flareLen * cos(angle).toFloat(),
                                    center.y + flareLen * sin(angle).toFloat()
                                ),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
        )

        // THE STAR DISC (Core)
        Box(
            modifier = Modifier
                .size(cardWidth, cardHeight)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 15f * density
                }
        ) {
            if (rotation <= 90f) {
                CalendarSide(
                    topText = day.date.format(dayFormatter),
                    bottomText = day.date.format(monthFormatter).uppercase(Locale.getDefault()),
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
    isBack: Boolean,
    accentColor: Color,
    isLandscape: Boolean
) {
    val glassTheme = LocalGlassTheme.current
    val dayFontSize = if (isLandscape) 34.sp else 40.sp
    val monthFontSize = if (isLandscape) 11.sp else 12.sp

    val containerColor = if (glassTheme.isLightMode) Color.White.copy(0.12f) else Color.Black.copy(0.35f)
    val borderColor = accentColor.copy(alpha = 0.8f)
    val dayNumberColor = Color.White

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { if (isBack) rotationY = 180f },
        color = containerColor,
        shape = CircleShape,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Photosphere Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.8f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                val headerTextColor = if (accentColor.luminance() > 0.5f) Color.Black.copy(0.8f) else Color.White
                Text(
                    text = bottomText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        fontSize = monthFontSize
                    ),
                    color = headerTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Core Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = topText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = dayFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = IBMPlexArabic,
                        letterSpacing = (-1).sp
                    ),
                    color = dayNumberColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
