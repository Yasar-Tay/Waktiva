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
import kotlin.math.PI

@Composable
fun FlippableCalendarCard(
    day: PrayerDay,
    isHijriVisible: Boolean,
    onFlip: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    sunAzimuth: Float,
    sunAltitude: Float,
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

    val infiniteTransition = rememberInfiniteTransition(label = "celestial_vibes")
    val flarePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flarePulse"
    )

    // Performance: Pass these as lambdas to defer state reading to Draw phase
    val animSunAzimuth = animateFloatAsState(sunAzimuth, spring(stiffness = Spring.StiffnessLow), label = "az")
    val animSunAltitude = animateFloatAsState(sunAltitude, spring(stiffness = Spring.StiffnessLow), label = "alt")

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardSize = if (isLandscape) 80.dp else 94.dp

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }

    Box(
        modifier = modifier
            .size(cardSize + 54.dp) 
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFlip()
            },
        contentAlignment = Alignment.Center
    ) {
        // ATMOSPHERIC BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val radius = (cardSize.toPx() / 2)
                    
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

        // THE CALENDAR DISC
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
                    sunAzimuthProvider = { animSunAzimuth.value },
                    sunAltitudeProvider = { animSunAltitude.value },
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
                    sunAzimuthProvider = { animSunAzimuth.value },
                    sunAltitudeProvider = { animSunAltitude.value },
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
    sunAzimuthProvider: () -> Float,
    sunAltitudeProvider: () -> Float,
    isLandscape: Boolean
) {
    val dayFontSize = if (isLandscape) 30.sp else 34.sp
    val monthFontSize = if (isLandscape) 8.sp else 9.sp

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { if (isBack) rotationY = 180f },
        color = Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.6f), Color.Transparent, accentColor.copy(alpha = 0.2f))
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val azimuth = sunAzimuthProvider()
                    val altitude = sunAltitudeProvider()
                    
                    val lightAngleRad = ((azimuth - 90f) * PI / 180f).toFloat()
                    val terminatorShift = (sin(altitude * PI / 180f) * 0.4f).toFloat()
                    
                    val brightStop = (0.35f - terminatorShift).coerceIn(0f, 1f)
                    val mStop = (0.55f - terminatorShift).coerceIn(0f, 1f)
                    val transparentStop = (0.85f - terminatorShift).coerceIn(0f, 1f)

                    val radius = size.width / 2
                    val dirX = cos(lightAngleRad)
                    val dirY = sin(lightAngleRad)
                    
                    val gradientStart = Offset(center.x + dirX * radius, center.y + dirY * radius)
                    val gradientEnd = Offset(center.x - dirX * radius, center.y - dirY * radius)

                    // 1. The Main Planet Gradient
                    drawCircle(
                        brush = Brush.linearGradient(
                            0.0f to accentColor.copy(alpha = 0.5f),
                            brightStop to accentColor.copy(alpha = 0.3f),
                            mStop to accentColor.copy(alpha = 0.1f),
                            transparentStop to Color.Transparent,
                            1.0f to Color.Transparent,
                            start = gradientStart,
                            end = gradientEnd,
                            tileMode = TileMode.Clamp
                        )
                    )

                    // 2. Curvature Highlight
                    val highlightOffset = Offset(
                        center.x + dirX * radius * 0.45f,
                        center.y + dirY * radius * 0.45f
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                            center = highlightOffset,
                            radius = radius * 1.3f
                        )
                    )
                },
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
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = bottomText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontSize = monthFontSize
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
