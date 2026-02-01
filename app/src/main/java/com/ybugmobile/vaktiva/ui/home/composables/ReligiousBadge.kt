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
    
    Surface(
        color = contentColor.copy(alpha = 0.35f), // Increased opacity
        shape = CircleShape,
        modifier = modifier.border(1.5.dp, contentColor.copy(alpha = 0.5f), CircleShape) // Thicker border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), // More padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp) // Slightly larger icon
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = holidayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Black, // Extra bold
                letterSpacing = 1.2.sp, // More spacing
                fontSize = 12.sp
            )
        }
    }
}
