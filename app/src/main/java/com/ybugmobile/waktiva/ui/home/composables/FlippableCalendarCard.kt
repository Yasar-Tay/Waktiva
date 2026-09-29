package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.domain.model.HijriUtils
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.ui.theme.GlassSurface
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * An interactive round liquid glass calendar component that flips between Gregorian and Hijri dates.
 * Features liquid glass material with bright emphasis glow, 3D-like rotation animations,
 * temporal "sheen" effects, and contextual active prayer color matching.
 *
 * @param day The prayer day data containing both Gregorian and Hijri information.
 * @param isHijriVisible Current flip state (true for Hijri side).
 * @param onFlip Callback triggered when the card is tapped.
 * @param contentColor Base color for text.
 * @param accentColor Primary color used for the card's header (derived from active prayer).
 * @param currentTime Current system time used for subtle temporal animations.
 * @param isSelectedDayToday Flag to apply pulsing effects and brightness glow if focused on today.
 * @param pulseScale Current animated scale value for the container.
 * @param temperatureRange The day's range of temperatures, shown under the day in place of the
 * status dot; null to keep the dot.
 * @param modifier Root layout modifier.
 */
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
    temperatureRange: String? = null,
    modifier: Modifier = Modifier
) {
    // 3D Flip animation state
    val rotation by animateFloatAsState(
        targetValue = if (isHijriVisible) 180f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "cardFlip"
    )

    // "Chronos" Animation: A slow, eternal rotation of a light sweep across the card's surface
    val infiniteTransition = rememberInfiniteTransition(label = "chronos")
    val timeAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "timeSweep"
    )

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }

    val cardModifier = if (modifier == Modifier) Modifier.size(100.dp) else modifier

    Box(
        modifier = cardModifier
            .graphicsLayer {
                // Apply external pulsing scale if today
                scaleX = if (isSelectedDayToday) pulseScale else 1f
                scaleY = if (isSelectedDayToday) pulseScale else 1f
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFlip() },
        contentAlignment = Alignment.Center
    ) {
        // Rotatable Card Core
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density // Enhance 3D effect depth
                }
        ) {
            if (rotation <= 90f) {
                // Front Side: Gregorian
                CalendarSide(
                    topText = day.date.format(dayFormatter),
                    bottomText = day.date.format(monthFormatter).uppercase(Locale.getDefault()),
                    isBack = false,
                    accentColor = accentColor,
                    timeAngle = timeAngle,
                    isSelectedDayToday = isSelectedDayToday,
                    note = temperatureRange
                )
            } else {
                // Back Side: Hijri
                val hijri = remember(day) { day.hijriDate ?: HijriUtils.calculateFallbackHijri(day.date) }
                val monthResId = hijri?.let {
                    context.resources.getIdentifier("hijri_month_${it.monthNumber}", "string", context.packageName)
                } ?: 0
                val monthName = if (monthResId != 0) stringResource(monthResId) else hijri?.monthEn ?: ""
                
                // Abbreviate long Hijri month names for the small header
                val displayMonth = if (monthName.length > 3) monthName.take(3) else monthName

                CalendarSide(
                    topText = hijri?.day?.toString() ?: "",
                    bottomText = displayMonth.uppercase(Locale.getDefault()),
                    isBack = true,
                    accentColor = accentColor,
                    timeAngle = timeAngle,
                    isSelectedDayToday = isSelectedDayToday,
                    note = temperatureRange
                )
            }
        }
    }
}

/**
 * Represents a single face of the round [FlippableCalendarCard] with liquid glass theme styling
 * and ModernCalendar selected day brightness glow.
 */
@Composable
private fun CalendarSide(
    topText: String,
    bottomText: String,
    isBack: Boolean,
    accentColor: Color,
    timeAngle: Float,
    isSelectedDayToday: Boolean,
    note: String? = null
) {
    val glassTheme = LocalGlassTheme.current
    val cardShape = CircleShape

    // Glass emphasis animation matching ModernCalendar's selected day card brightness
    val glassEmphasis by animateFloatAsState(
        targetValue = if (isSelectedDayToday) 1.0f else 0.6f,
        label = "glassEmphasis"
    )

    // Dynamic contrast adjustment for the header text
    val headerTextColor = remember(accentColor) {
        if (accentColor.luminance() > 0.5f) Color(0xFF1C1C1E) else Color.White
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { if (isBack) rotationY = 180f },
        shape = cardShape,
        glass = glassTheme,
        emphasis = glassEmphasis,
        accent = accentColor.copy(alpha = if (isSelectedDayToday) 0.65f else 0.35f)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawBehind {
                        // "Temporal Sheen": A subtle reflection that passes across the card surface
                        rotate(timeAngle + 45f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent)
                                ),
                                size = size * 2.5f
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val innerInset = minOf(this.maxWidth, this.maxHeight) * 0.15f

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Month Header (Pill-shaped accent badge fitting in the upper curve)
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(accentColor.copy(alpha = 0.95f), accentColor.copy(alpha = 0.82f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bottomText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            fontSize = 9.5.sp
                        ),
                        color = headerTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Day Number - Central large text
                Text(
                    text = topText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IBMPlexArabic,
                        letterSpacing = (-1).sp,
                        lineHeight = 28.sp
                    ),
                    color = glassTheme.contentColor,
                    textAlign = TextAlign.Center
                )

                if (note != null) {
                    // The day's temperatures, where the status dot would be.
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = IBMPlexArabic,
                            letterSpacing = 0.sp
                        ),
                        color = glassTheme.contentColor.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                } else {
                    // Active status dot
                    val dotAlpha by animateFloatAsState(
                        targetValue = if (topText.isNotEmpty()) 0.5f else 0.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dotPulse"
                    )

                    Box(
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(glassTheme.contentColor.copy(alpha = dotAlpha))
                    )
                }
            }
        }
    }
}
