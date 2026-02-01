package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.ybugmobile.vaktiva.domain.model.ReligiousDay
import com.ybugmobile.vaktiva.domain.provider.ReligiousDaysProvider
import java.time.LocalDate

@Composable
fun ReligiousBadge(
    date: LocalDate,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val religiousDay = ReligiousDaysProvider.getReligiousDay(date)
    val tomorrowReligiousDay = if (religiousDay == null) {
        ReligiousDaysProvider.getReligiousDay(date.plusDays(1))
    } else null

    val holidayToShow = religiousDay ?: tomorrowReligiousDay ?: return
    
    val holidayLabel = if (religiousDay != null) {
        stringResource(holidayToShow.nameResId).uppercase()
    } else {
        stringResource(R.string.home_tomorrow_is, stringResource(holidayToShow.nameResId))
    }

    // Get the color for the holiday (either today's or tomorrow's)
    val holidayDate = if (religiousDay != null) date else date.plusDays(1)
    val accentColor = getCalendarAccentColor(holidayDate, null, contentColor)
    
    Surface(
        color = accentColor.copy(alpha = 0.25f),
        shape = CircleShape,
        modifier = modifier.border(1.5.dp, accentColor.copy(alpha = 0.5f), CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = holidayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Shared logic for calendar accent colors.
 * Note: hijriData is optional here because ReligiousDaysProvider only uses the LocalDate.
 * But we can use it for Ramadan/Eid checks if available.
 */
fun getCalendarAccentColor(
    date: LocalDate,
    hijriMonth: Int?,
    hijriDay: Int?,
    contentColor: Color
): Color {
    val isToday = date == LocalDate.now()
    val isReligiousDay = ReligiousDaysProvider.getReligiousDay(date) != null
    
    val isRamadan = hijriMonth == 9
    val isEidFitr = hijriMonth == 10 && hijriDay in 1..3
    val isEidAdha = hijriMonth == 12 && hijriDay in 10..13
    val isEid = isEidFitr || isEidAdha

    return when {
        isToday -> Color(0xFF42A5F5) // Remarkable Vibrant Blue
        isEid -> Color(0xFFFFD54F) // Soft Gold
        isRamadan -> Color(0xFF81C784) // Sage Green
        isReligiousDay -> Color(0xFFBA68C8) // Soft Purple
        else -> contentColor.copy(alpha = 0.15f)
    }
}

// Overload for simpler calls
fun getCalendarAccentColor(date: LocalDate, hijriData: com.ybugmobile.vaktiva.domain.model.HijriData?, contentColor: Color): Color {
    return getCalendarAccentColor(date, hijriData?.monthNumber, hijriData?.day, contentColor)
}
