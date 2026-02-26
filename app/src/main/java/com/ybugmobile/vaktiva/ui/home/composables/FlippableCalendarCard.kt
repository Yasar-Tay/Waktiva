package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
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
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FlippableCalendarCard(
    day: PrayerDay,
    contentColor: Color,
    accentColor: Color, // Now passed dynamically
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

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM") }

    Box(
        modifier = modifier
            .size(100.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isFlipped = !isFlipped
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
        ) {
            if (rotation <= 90f) {
                ModernCalendarCircle(
                    topText = day.date.format(dayFormatter),
                    bottomText = day.date.format(monthFormatter).uppercase(Locale.getDefault()),
                    contentColor = contentColor,
                    isBack = false,
                    accentColor = accentColor
                )
            } else {
                val hijri = day.hijriDate
                val monthResId = hijri?.let {
                    context.resources.getIdentifier("hijri_month_${it.monthNumber}", "string", context.packageName)
                } ?: 0
                val monthName = if (monthResId != 0) stringResource(monthResId) else hijri?.monthEn ?: ""

                ModernCalendarCircle(
                    topText = hijri?.day?.toString() ?: "",
                    bottomText = monthName.uppercase(Locale.getDefault()),
                    contentColor = contentColor,
                    isBack = true,
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
private fun ModernCalendarCircle(
    topText: String,
    bottomText: String,
    contentColor: Color,
    isBack: Boolean,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .size(100.dp)
            .graphicsLayer { if (isBack) rotationY = 180f },
        color = Color.White.copy(alpha = 0.08f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(contentColor.copy(alpha = 0.25f), Color.Transparent, contentColor.copy(alpha = 0.1f))
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Central glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.15f), Color.Transparent),
                            radius = size.width * 0.6f
                        )
                    )
                    
                    // Subtle top accent
                    drawArc(
                        color = accentColor.copy(alpha = 0.4f),
                        startAngle = 225f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                // Large Day Number
                Text(
                    text = topText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = IBMPlexArabic,
                        letterSpacing = (-1).sp
                    ),
                    color = contentColor,
                    textAlign = TextAlign.Center
                )

                // Month Text
                Text(
                    text = bottomText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontSize = 10.sp
                    ),
                    color = contentColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
