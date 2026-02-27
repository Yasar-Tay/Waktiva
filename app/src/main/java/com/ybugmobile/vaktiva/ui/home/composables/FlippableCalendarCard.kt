package com.ybugmobile.vaktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
import com.ybugmobile.vaktiva.ui.theme.LocalGlassTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Compact modern sizing
    val cardWidth = if (isLandscape) 64.dp else 80.dp
    val cardHeight = cardWidth * 1.2f

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }

    Box(
        modifier = modifier
            .width(cardWidth + 32.dp)
            .height(cardHeight + 32.dp)
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
        // Soft Glow Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.2f), Color.Transparent),
                            center = center,
                            radius = (cardWidth.toPx() / 2) * 2.0f
                        ),
                        radius = (cardWidth.toPx() / 2) * 2.0f
                    )
                }
        )

        // Today Active Border
        if (isSelectedDayToday) {
            Box(
                modifier = Modifier
                    .size(cardWidth + 10.dp, cardHeight + 10.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.2f * pulseScale),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
            )
        }

        // THE CALENDAR CARD
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
    val dayFontSize = if (isLandscape) 28.sp else 34.sp
    val monthFontSize = if (isLandscape) 10.sp else 12.sp

    // Enhanced glassmorphism: even lighter body, solid header
    val containerColor = if (glassTheme.isLightMode) Color.White.copy(0.06f) else Color.Black.copy(0.15f)
    val borderColor = if (glassTheme.isLightMode) Color.White.copy(0.4f) else Color.White.copy(0.12f)
    val dayNumberColor = Color.White.copy(alpha = 0.95f) // Light color for dayNumber

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { if (isBack) rotationY = 180f },
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section: Solid Accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .background(accentColor.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                val headerTextColor = if (accentColor.luminance() > 0.5f) Color.Black.copy(0.7f) else Color.White
                Text(
                    text = bottomText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = monthFontSize
                    ),
                    color = headerTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Date Section: Lighter background area (inherits surface transparency)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = topText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = dayFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = IBMPlexArabic,
                        letterSpacing = (-0.5).sp
                    ),
                    color = dayNumberColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
