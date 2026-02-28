package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
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
    // Suppress unused warning while keeping signature compatibility
    SideEffect {
        val _ignore = currentTime 
    }

    val rotation by animateFloatAsState(
        targetValue = if (isHijriVisible) 180f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "cardFlip"
    )

    // Subtle breathing animation for "Today"
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") } // Abbreviated (e.g., OCT)

    Box(
        modifier = modifier
            .size(100.dp) 
            .graphicsLayer {
                scaleX = if (isSelectedDayToday) pulseScale else 1f
                scaleY = if (isSelectedDayToday) pulseScale else 1f
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFlip() },
        contentAlignment = Alignment.Center
    ) {
        // Today's Minimal Halo (Apple Style)
        if (isSelectedDayToday) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = breathAlpha), Color.Transparent),
                                center = center,
                                radius = size.minDimension / 1.1f
                            )
                        )
                    }
            )
        }

        // The Card Core
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
        ) {
            if (rotation <= 90f) {
                CalendarSide(
                    topText = day.date.format(dayFormatter),
                    bottomText = day.date.format(monthFormatter).uppercase(Locale.getDefault()),
                    isBack = false,
                    accentColor = accentColor
                )
            } else {
                val hijri = day.hijriDate
                val monthResId = hijri?.let {
                    context.resources.getIdentifier("hijri_month_${it.monthNumber}", "string", context.packageName)
                } ?: 0
                val monthName = if (monthResId != 0) stringResource(monthResId) else hijri?.monthEn ?: ""
                
                // For Hijri abbreviation, take first 3 letters as a standard short form
                val displayMonth = if (monthName.length > 3) monthName.take(3) else monthName

                CalendarSide(
                    topText = hijri?.day?.toString() ?: "",
                    bottomText = displayMonth.uppercase(Locale.getDefault()),
                    isBack = true,
                    accentColor = accentColor
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
    accentColor: Color
) {
    val glassTheme = LocalGlassTheme.current
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { if (isBack) rotationY = 180f },
        color = Color.White.copy(alpha = 0.08f), 
        shape = RoundedCornerShape(28.dp), 
        border = BorderStroke(
            width = 0.5.dp, 
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))
            )
        ),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Month Label (Abbreviated)
            Text(
                text = bottomText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    fontSize = 11.sp 
                ),
                color = accentColor.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Day Number
            Text(
                text = topText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 34.sp, 
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IBMPlexArabic,
                    letterSpacing = (-1).sp,
                    lineHeight = 34.sp 
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-2).dp) 
            )
            
            // Subtle Dot Indicator
            Box(
                modifier = Modifier
                    .padding(top = 0.dp)
                    .size(3.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    }
}
