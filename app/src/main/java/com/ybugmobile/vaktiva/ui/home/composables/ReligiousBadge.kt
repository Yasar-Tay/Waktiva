package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.HijriData
import com.ybugmobile.vaktiva.domain.model.ReligiousDay
import com.ybugmobile.vaktiva.domain.provider.ReligiousDaysProvider
import java.time.LocalDate

@Composable
fun ReligiousBadge(
    date: LocalDate,
    contentColor: Color,
    modifier: Modifier = Modifier,
    hijriDate: HijriData? = null
) {
    val religiousDay = ReligiousDaysProvider.getReligiousDay(date) ?: return
    
    val label = stringResource(religiousDay.nameResId)
    val accentColor = getCalendarAccentColor(date, hijriDate?.monthNumber, hijriDate?.day, contentColor)

    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.12f),
        modifier = modifier.graphicsLayer {
            shadowElevation = 2f
            shape = CircleShape
            clip = true
        },
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(accentColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(10.dp)
                )
            }

            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    fontSize = 10.sp
                ),
                color = contentColor.copy(alpha = 0.9f)
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
