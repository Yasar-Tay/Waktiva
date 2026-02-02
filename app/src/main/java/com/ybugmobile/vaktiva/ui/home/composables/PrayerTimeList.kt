package com.ybugmobile.vaktiva.ui.home.composables

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
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.format.DateTimeFormatter

@Composable
fun PrayerTimeList(
    day: PrayerDay,
    currentPrayerType: PrayerType?,
    contentColor: Color = Color.White,
    highlightColor: Color = Color.Black.copy(alpha = 0.2f)
) {
    data class PrayerItem(val type: PrayerType, val resId: Int, val time: String, val icon: ImageVector)

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    val prayers = listOf(
        PrayerItem(PrayerType.FAJR, R.string.prayer_fajr, day.timings[PrayerType.FAJR]?.format(timeFormatter) ?: "", ImageVector.vectorResource(R.drawable.water_lux_rotated)),
        PrayerItem(PrayerType.SUNRISE, R.string.prayer_sunrise, day.timings[PrayerType.SUNRISE]?.format(timeFormatter) ?: "", Icons.Default.WbTwilight),
        PrayerItem(PrayerType.DHUHR, R.string.prayer_dhuhr, day.timings[PrayerType.DHUHR]?.format(timeFormatter) ?: "", Icons.Default.WbSunny),
        PrayerItem(PrayerType.ASR, R.string.prayer_asr, day.timings[PrayerType.ASR]?.format(timeFormatter) ?: "", Icons.Default.WbSunny),
        PrayerItem(PrayerType.MAGHRIB, R.string.prayer_maghrib, day.timings[PrayerType.MAGHRIB]?.format(timeFormatter) ?: "", Icons.Default.WbTwilight),
        PrayerItem(PrayerType.ISHA, R.string.prayer_isha, day.timings[PrayerType.ISHA]?.format(timeFormatter) ?: "", Icons.Default.NightsStay)
    )

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
            
            // Highlight container for current prayer
            val itemContainerColor = if (isCurrent) highlightColor else Color.Transparent
            val itemContentColor = if (isCurrent) contentColor else contentColor.copy(alpha = 0.7f)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = itemContentColor,
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
