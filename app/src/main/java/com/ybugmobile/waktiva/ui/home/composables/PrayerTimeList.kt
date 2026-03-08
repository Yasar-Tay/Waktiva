package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import java.time.format.DateTimeFormatter

/**
 * A structured list component that displays all prayer times for a given day.
 * Highlights the current active prayer and uses thematic icons and colors.
 *
 * @param day The prayer data for the selected day.
 * @param currentPrayerType The type of prayer currently active (used for highlighting).
 * @param contentColor Base color for text and unselected icons.
 * @param highlightColor Background color for the current prayer's list item.
 */
@Composable
fun PrayerTimeList(
    day: PrayerDay,
    currentPrayerType: PrayerType?,
    contentColor: Color = Color.White,
    highlightColor: Color = Color.Black.copy(alpha = 0.2f)
) {
    /** Internal model for prayer list entries. */
    data class PrayerItem(
        val type: PrayerType,
        val resId: Int,
        val time: String,
        val icon: ImageVector,
        val color: Color
    )

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // Define the sequence of prayers with their respective icons and colors
    val prayers = listOf(
        PrayerItem(PrayerType.FAJR, R.string.prayer_fajr, day.timings[PrayerType.FAJR]?.format(timeFormatter) ?: "", ImageVector.vectorResource(R.drawable.haze_day_rotated), Color(0xFF81D4FA)),
        PrayerItem(PrayerType.SUNRISE, R.string.prayer_sunrise, day.timings[PrayerType.SUNRISE]?.format(timeFormatter) ?: "", ImageVector.vectorResource(R.drawable.sunrise), Color(0xFFFFE082)),
        PrayerItem(PrayerType.DHUHR, R.string.prayer_dhuhr, day.timings[PrayerType.DHUHR]?.format(timeFormatter) ?: "", ImageVector.vectorResource(R.drawable.clear_day), Color(0xFFFFF59D)),
        PrayerItem(PrayerType.ASR, R.string.prayer_asr, day.timings[PrayerType.ASR]?.format(timeFormatter) ?: "", ImageVector.vectorResource(R.drawable.clear_day), Color(0xFFFFCC80)),
        PrayerItem(PrayerType.MAGHRIB, R.string.prayer_maghrib, day.timings[PrayerType.MAGHRIB]?.format(timeFormatter) ?: "", ImageVector.vectorResource(R.drawable.sunset), Color(0xFFCE93D8)),
        PrayerItem(PrayerType.ISHA, R.string.prayer_isha, day.timings[PrayerType.ISHA]?.format(timeFormatter) ?: "", ImageVector.vectorResource(R.drawable.clear_night), Color(0xFF9FA8DA))
    )

    // Optional text shadow for better readability on light/busy backgrounds
    val commonShadow = if (contentColor.red > 0.5f) Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    ) else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        prayers.forEach { item ->
            val isCurrent = item.type == currentPrayerType
            
            // Visual state determination based on active prayer status
            val itemContainerColor = if (isCurrent) highlightColor else Color.Transparent
            val itemContentColor = if (isCurrent) contentColor else contentColor.copy(alpha = 0.7f)
            val iconTint = if (isCurrent) item.color else itemContentColor
            val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(itemContainerColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Icon and Prayer Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(item.resId),
                        color = itemContentColor,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            shadow = commonShadow
                        ),
                        fontWeight = fontWeight,
                        fontSize = 16.sp
                    )
                }
                
                // Right side: Formatted Time
                Text(
                    text = item.time,
                    color = itemContentColor,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        shadow = commonShadow
                    ),
                    fontWeight = fontWeight,
                    fontSize = 16.sp
                )
            }
        }
    }
}
