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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.ui.theme.IBMPlexArabic
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
    val cardSize = if (isLandscape) 80.dp else 94.dp

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val flarePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flarePulse"
    )

    Box(
        modifier = modifier
            .size(cardSize + 48.dp) 
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFlip()
            },
        contentAlignment = Alignment.Center
    ) {
        // ATMOSPHERIC HALO
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val radius = (cardSize.toPx() / 2)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.3f * flarePulse),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 1.8f
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
                    currentTime = currentTime,
                    timings = day.timings,
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
                    currentTime = currentTime,
                    timings = day.timings,
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
    currentTime: LocalTime,
    timings: Map<PrayerType, LocalTime>,
    isLandscape: Boolean
) {
    val dayFontSize = if (isLandscape) 30.sp else 34.sp
    val monthFontSize = if (isLandscape) 11.sp else 13.sp

    val fajrTime = timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val sunriseTime = timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val maghribTime = timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 0)
    val ishaTime = timings[PrayerType.ISHA] ?: LocalTime.of(19, 30)

    val markerFajr = Color(0xFF81D4FA)
    val markerSunrise = Color(0xFFFFE082)
    val markerDhuhr = Color(0xFFFFF59D)
    val markerAsr = Color(0xFFFFCC80)
    val markerMaghrib = Color(0xFFCE93D8)
    val markerIsha = Color(0xFF9FA8DA)

    val config = remember(currentTime, timings) {
        when {
            // Fajr period
            currentTime.isAfter(fajrTime) && currentTime.isBefore(sunriseTime) -> {
                SkyColors(markerFajr.copy(alpha = 0.8f), markerFajr.copy(alpha = 0.4f), markerFajr.copy(alpha = 0.1f))
            }
            // Sunrise period
            currentTime.isAfter(sunriseTime.minusMinutes(15)) && currentTime.isBefore(sunriseTime.plusMinutes(45)) -> {
                SkyColors(markerSunrise.copy(alpha = 0.8f), markerSunrise.copy(alpha = 0.4f), Color.Transparent)
            }
            // Daytime (Dhuhr/Asr)
            currentTime.isAfter(sunriseTime) && currentTime.isBefore(maghribTime.minusMinutes(15)) -> {
                SkyColors(markerDhuhr.copy(alpha = 0.7f), markerAsr.copy(alpha = 0.3f), Color.Transparent)
            }
            // Sunset period (Maghrib)
            currentTime.isAfter(maghribTime.minusMinutes(15)) && currentTime.isBefore(ishaTime) -> {
                SkyColors(markerMaghrib.copy(alpha = 0.8f), markerMaghrib.copy(alpha = 0.4f), Color.Transparent)
            }
            // Night period (Isha)
            else -> {
                SkyColors(markerIsha.copy(alpha = 0.8f), markerIsha.copy(alpha = 0.4f), Color.Transparent)
            }
        }
    }

    val animTop by animateColorAsState(config.top, tween(1500))
    val animMid by animateColorAsState(config.mid, tween(1500))
    val animBottom by animateColorAsState(config.bottom, tween(1500))

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { if (isBack) rotationY = 180f },
        color = Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.5f), Color.Transparent, accentColor.copy(alpha = 0.2f))
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to animTop,
                        0.45f to animMid,
                        0.65f to animBottom,
                        1.0f to Color.Transparent
                    )
                )
                .drawBehind {
                    val radius = size.width / 2
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(center.x, center.y - radius * 0.4f),
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

private data class SkyColors(val top: Color, val mid: Color, val bottom: Color)
