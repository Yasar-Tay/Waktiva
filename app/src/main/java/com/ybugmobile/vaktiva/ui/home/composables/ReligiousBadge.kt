package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val religiousDay = ReligiousDaysProvider.getReligiousDay(date)
    
    val holidayToShow = religiousDay ?: return

    val label = stringResource(holidayToShow.nameResId)
    
    val accentColor = getCalendarAccentColor(date, hijriDate?.monthNumber, hijriDate?.day, contentColor)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.15f),
        modifier = modifier.border(1.5.dp, contentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // Optional subtle icon
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        }
    }
}


/**
 * Shared logic for calendar accent colors.
 * Prioritizes religious events over the current day color.
 */
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
        isEve -> Color(0xFFBA68C8) // Soft Purple (Distinct from Gold)
        religiousDay != null -> Color(0xFFBA68C8) // Soft Purple
        isToday -> Color(0xFF42A5F5) // Remarkable Vibrant Blue
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

// Overload for simpler calls
fun getCalendarAccentColor(date: LocalDate, hijriData: HijriData?, contentColor: Color): Color {
    return getCalendarAccentColor(date, hijriData?.monthNumber, hijriData?.day, contentColor)
}
