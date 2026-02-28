package com.ybugmobile.vaktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.HijriData
import com.ybugmobile.vaktiva.domain.model.ReligiousDay
import com.ybugmobile.vaktiva.domain.model.WeatherCondition
import com.ybugmobile.vaktiva.domain.provider.ReligiousDaysProvider
import com.ybugmobile.vaktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.vaktiva.ui.theme.desaturate
import com.ybugmobile.vaktiva.ui.theme.darken
import java.time.LocalDate
import java.util.Locale

@Composable
fun ReligiousBadge(
    date: LocalDate,
    contentColor: Color,
    modifier: Modifier = Modifier,
    hijriDate: HijriData? = null
) {
    val religiousDay = ReligiousDaysProvider.getReligiousDay(date) ?: return
    val glassTheme = LocalGlassTheme.current
    val weatherCondition = glassTheme.weatherCondition
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val label = stringResource(religiousDay.nameResId)
    val baseAccentColor = getCalendarAccentColor(date, hijriDate?.monthNumber, hijriDate?.day, contentColor)
    
    // Apply weather adjustments to the religious accent color
    val accentColor = remember(baseAccentColor, weatherCondition) {
        val isCloudy = weatherCondition != WeatherCondition.CLEAR && weatherCondition != WeatherCondition.UNKNOWN
        val isSevere = weatherCondition == WeatherCondition.RAINY || 
                      weatherCondition == WeatherCondition.THUNDERSTORM || 
                      weatherCondition == WeatherCondition.SNOWY

        if (isCloudy) {
            val desaturateAmount = if (isSevere) 0.3f else 0.15f
            val darkenAmount = if (isSevere) 0.15f else 0.05f
            baseAccentColor.desaturate(desaturateAmount).darken(darkenAmount)
        } else {
            baseAccentColor
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconAlpha"
    )

    // Apple Style: Squircle Pill
    Surface(
        shape = RoundedCornerShape(14.dp), // Tighter squircle for small badge
        color = Color.White.copy(alpha = 0.08f), // Matching card glass
        modifier = modifier.height(if (isLandscape) 28.dp else 32.dp),
        border = BorderStroke(
            width = 0.5.dp, 
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))
            )
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Accent Color Header Style (Mini version of card header)
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 14.dp else 16.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = iconAlpha),
                    modifier = Modifier.size(if (isLandscape) 10.dp else 12.dp)
                )
            }

            Text(
                text = label.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    fontSize = if (isLandscape) 9.sp else 10.sp
                ),
                color = contentColor.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun getCalendarAccentColor(
    date: LocalDate,
    hijriMonth: Int?,
    hijriDay: Int?,
    contentColor: Color
): Color {
    val religiousDay = ReligiousDaysProvider.getReligiousDay(date)
    val isToday = date == LocalDate.now()
    
    val isEid = isEid(religiousDay)
    val isRamadan = isRamadan(hijriMonth, religiousDay)
    val isEve = isEve(religiousDay)

    return when {
        isEid -> Color(0xFFFFD54F) // Soft Gold
        isRamadan -> Color(0xFF81C784) // Sage Green
        isEve -> Color(0xFFBA68C8) // Soft Purple
        religiousDay != null -> Color(0xFFBA68C8) // Soft Purple
        isToday -> Color(0xFF42A5F5) // Vibrant Blue
        else -> contentColor.copy(alpha = 0.15f)
    }
}

fun isRamadan(hijriMonth: Int?, religiousDay: ReligiousDay?): Boolean {
    return hijriMonth == 9 || 
           religiousDay?.nameResId == R.string.rel_day_ramadan_start ||
           religiousDay?.nameResId == R.string.rel_day_first_tarawih ||
           religiousDay?.nameResId == R.string.rel_day_kadir
}

fun isEid(religiousDay: ReligiousDay?): Boolean {
    return religiousDay?.nameResId == R.string.rel_day_ramadan_eid ||
           religiousDay?.nameResId == R.string.rel_day_sacrifice_eid
}

fun isEve(religiousDay: ReligiousDay?): Boolean {
    return religiousDay?.nameResId == R.string.rel_day_eid_eve
}

fun getCalendarAccentColor(date: LocalDate, hijriData: HijriData?, contentColor: Color): Color {
    return getCalendarAccentColor(date, hijriData?.monthNumber, hijriData?.day, contentColor)
}
