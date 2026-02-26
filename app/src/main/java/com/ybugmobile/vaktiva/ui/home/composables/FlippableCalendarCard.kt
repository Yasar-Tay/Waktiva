package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

    val context = LocalContext.current
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd") }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }

    Box(
        modifier = modifier
            .size(90.dp) // Slightly more compact
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 15f * density
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isFlipped = !isFlipped
            },
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            // Front side: Gregorian
            ModernCalendarPage(
                topText = day.date.format(monthFormatter).uppercase(Locale.getDefault()),
                centerText = day.date.format(dayFormatter),
                bottomText = day.date.year.toString(),
                contentColor = contentColor,
                isBack = false,
                accentColor = Color(0xFFEF4444) // Modern red
            )
        } else {
            // Back side: Hijri
            val hijri = day.hijriDate
            val monthResId = hijri?.let {
                context.resources.getIdentifier(
                    "hijri_month_${it.monthNumber}",
                    "string",
                    context.packageName
                )
            } ?: 0
            val monthName = if (monthResId != 0) stringResource(monthResId) else hijri?.monthEn ?: ""

            ModernCalendarPage(
                topText = monthName.take(3).uppercase(Locale.getDefault()),
                centerText = hijri?.day?.toString() ?: "",
                bottomText = hijri?.year?.toString() ?: "",
                contentColor = contentColor,
                isBack = true,
                accentColor = Color(0xFF10B981) // Modern emerald for Hijri
            )
        }
    }
}

@Composable
private fun ModernCalendarPage(
    topText: String,
    centerText: String,
    bottomText: String,
    contentColor: Color,
    isBack: Boolean,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (isBack) rotationY = 180f
            },
        color = Color.White.copy(alpha = 0.12f), // More transparent glass
        shape = RoundedCornerShape(20.dp), // More rounded
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Subtle glass highlight at the top
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.4f
                        )
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor.copy(alpha = 0.9f), accentColor.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = topText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }

            // Divider line (Subtle glass effect)
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )

            // Middle & Bottom
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = centerText,
                    color = contentColor,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = IBMPlexArabic,
                    letterSpacing = (-1).sp
                )
                
                Spacer(Modifier.height(0.dp))

                Box(
                    modifier = Modifier
                        .background(contentColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = bottomText,
                        color = contentColor.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
