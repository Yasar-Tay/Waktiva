package com.ybugmobile.vaktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.ui.theme.IBMPlexArabic
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FlippableCalendarCard(
    day: PrayerDay,
    contentColor: Color,
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Scale factors for landscape
    val outerSize = if (isLandscape) 90.dp else 115.dp
    val innerSize = if (isLandscape) 70.dp else 88.dp
    val centerTextSize = if (isLandscape) 28.sp else 36.sp
    val topTextSize = if (isLandscape) 8.sp else 10.sp
    val bottomTextSize = if (isLandscape) 7.sp else 9.sp

    val infiniteTransition = rememberInfiniteTransition(label = "stellar")
    
    // Rotating orbits
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotation"
    )
    
    // Breathing pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }

    Box(
        modifier = modifier
            .size(outerSize) 
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isFlipped = !isFlipped
            },
        contentAlignment = Alignment.Center
    ) {
        // Celestial Orbits
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(orbitRotation)
                .drawBehind {
                    val accent = if (rotation <= 90f) Color(0xFFEF4444) else Color(0xFF10B981)
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.3f), accent.copy(alpha = 0f))
                        ),
                        style = Stroke(
                            width = 1.dp.toPx(), 
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                        ),
                        radius = size.width / 2 - 4.dp.toPx()
                    )
                }
        )

        // Core Flippable Body
        Box(
            modifier = Modifier
                .size(innerSize)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 15f * density
                },
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                StellarSpherePage(
                    topText = day.date.format(monthFormatter).uppercase(Locale.getDefault()),
                    centerText = day.date.format(dayFormatter),
                    bottomText = day.date.year.toString(),
                    isBack = false,
                    accentColor = Color(0xFFEF4444),
                    centerTextSize = centerTextSize,
                    topTextSize = topTextSize,
                    bottomTextSize = bottomTextSize
                )
            } else {
                val hijri = day.hijriDate
                val monthResId = hijri?.let {
                    context.resources.getIdentifier("hijri_month_${it.monthNumber}", "string", context.packageName)
                } ?: 0
                val monthName = if (monthResId != 0) stringResource(monthResId) else hijri?.monthEn ?: ""

                StellarSpherePage(
                    topText = monthName.take(3).uppercase(Locale.getDefault()),
                    centerText = hijri?.day?.toString() ?: "",
                    bottomText = hijri?.year?.toString() ?: "",
                    isBack = true,
                    accentColor = Color(0xFF10B981),
                    centerTextSize = centerTextSize,
                    topTextSize = topTextSize,
                    bottomTextSize = bottomTextSize
                )
            }
        }
    }
}

@Composable
private fun StellarSpherePage(
    topText: String,
    centerText: String,
    bottomText: String,
    isBack: Boolean,
    accentColor: Color,
    centerTextSize: androidx.compose.ui.unit.TextUnit,
    topTextSize: androidx.compose.ui.unit.TextUnit,
    bottomTextSize: androidx.compose.ui.unit.TextUnit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { if (isBack) rotationY = 180f },
        color = Color.Black.copy(alpha = 0.35f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.7f), Color.White.copy(alpha = 0.2f), accentColor.copy(alpha = 0.5f))
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Internal Nebula Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(size.width * 0.3f, size.height * 0.3f),
                            radius = size.width * 0.8f
                        )
                    )
                    
                    // Atmospheric Rim Light
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, accentColor.copy(alpha = 0.12f)),
                            radius = size.width * 0.5f
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = topText,
                    color = accentColor.copy(alpha = 0.9f),
                    style = TextStyle(
                        fontSize = topTextSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                )

                Text(
                    text = centerText,
                    color = Color.White,
                    style = TextStyle(
                        fontSize = centerTextSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = IBMPlexArabic,
                        letterSpacing = (-1).sp,
                        shadow = Shadow(accentColor.copy(alpha = 0.7f), blurRadius = 18f)
                    )
                )

                Text(
                    text = bottomText,
                    color = Color.White.copy(alpha = 0.5f),
                    style = TextStyle(
                        fontSize = bottomTextSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}
