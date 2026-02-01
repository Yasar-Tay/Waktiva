package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.layout.*
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
    val tomorrowReligiousDay = if (religiousDay == null) {
        ReligiousDaysProvider.getReligiousDay(date.plusDays(1))
    } else null

    val holidayToShow = religiousDay ?: tomorrowReligiousDay ?: return

    val isToday = religiousDay != null

    val label = if (isToday) {
        stringResource(holidayToShow.nameResId)
    } else {
        stringResource(
            R.string.home_tomorrow_is,
            stringResource(holidayToShow.nameResId)
        )
    }

    val holidayDate = if (isToday) date else date.plusDays(1)
    val holidayHijriMonth = if (isToday) hijriDate?.monthNumber else null
    val holidayHijriDay = if (isToday) hijriDate?.day else null
    
    val accentColor = getCalendarAccentColor(holidayDate, holidayHijriMonth, holidayHijriDay, contentColor)

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = accentColor.copy(alpha = 0.15f),
        modifier = modifier
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
    
    val isEid = isEid(hijriMonth, hijriDay, religiousDay)
    val isRamadan = isRamadan(hijriMonth, religiousDay)

    return when {
        isEid -> Color(0xFFFFD54F) // Soft Gold
        isRamadan -> Color(0xFF81C784) // Sage Green
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

fun isEid(hijriMonth: Int?, hijriDay: Int?, religiousDay: ReligiousDay?): Boolean {
    return (hijriMonth == 10 && hijriDay in 1..3) || 
           (hijriMonth == 12 && hijriDay in 10..13) ||
           religiousDay?.nameResId == R.string.rel_day_ramadan_eid ||
           religiousDay?.nameResId == R.string.rel_day_sacrifice_eid ||
           religiousDay?.nameResId == R.string.rel_day_arefe
}

// Overload for simpler calls
fun getCalendarAccentColor(date: LocalDate, hijriData: HijriData?, contentColor: Color): Color {
    return getCalendarAccentColor(date, hijriData?.monthNumber, hijriData?.day, contentColor)
}
