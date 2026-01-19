package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
    nextPrayerType: PrayerType?,
    currentMethodId: Int,
    onMethodSelected: (Int) -> Unit
) {
    data class PrayerItem(val type: PrayerType, val resId: Int, val time: String, val icon: ImageVector)

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    val prayers = listOf(
        PrayerItem(PrayerType.FAJR, R.string.prayer_fajr, day.timings[PrayerType.FAJR]?.format(timeFormatter) ?: "", Icons.Default.WbTwilight),
        PrayerItem(PrayerType.SUNRISE, R.string.prayer_sunrise, day.timings[PrayerType.SUNRISE]?.format(timeFormatter) ?: "", Icons.Default.WbSunny),
        PrayerItem(PrayerType.DHUHR, R.string.prayer_dhuhr, day.timings[PrayerType.DHUHR]?.format(timeFormatter) ?: "", Icons.Default.LightMode),
        PrayerItem(PrayerType.ASR, R.string.prayer_asr, day.timings[PrayerType.ASR]?.format(timeFormatter) ?: "", Icons.Default.WbSunny),
        PrayerItem(PrayerType.MAGHRIB, R.string.prayer_maghrib, day.timings[PrayerType.MAGHRIB]?.format(timeFormatter) ?: "", Icons.Default.WbTwilight),
        PrayerItem(PrayerType.ISHA, R.string.prayer_isha, day.timings[PrayerType.ISHA]?.format(timeFormatter) ?: "", Icons.Default.NightsStay)
    )

    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.home_prayer_provider),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                CalculationMethodSelector(
                    currentMethodId = currentMethodId,
                    onMethodSelected = onMethodSelected,
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            prayers.forEach { item ->
                val isNext = item.type == nextPrayerType
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isNext) Color.Yellow else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(item.resId),
                            color = if (isNext) Color.Yellow else Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Text(
                        text = item.time,
                        color = if (isNext) Color.Yellow else Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun CalculationMethodSelector(
    currentMethodId: Int,
    onMethodSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val methodNames = mapOf(
        3 to stringResource(R.string.method_mwl),
        2 to stringResource(R.string.method_isna),
        5 to stringResource(R.string.method_egypt),
        4 to stringResource(R.string.method_makkah),
        1 to stringResource(R.string.method_karachi),
        7 to stringResource(R.string.method_tehran),
        8 to stringResource(R.string.method_gulf),
        9 to stringResource(R.string.method_kuwait),
        10 to stringResource(R.string.method_qatar),
        11 to stringResource(R.string.method_singapore),
        13 to stringResource(R.string.method_turkey)
    )

    val currentMethodName = methodNames[currentMethodId] ?: "Unknown Method"

    Box {
        Surface(
            onClick = { expanded = true },
            color = Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentMethodName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            methodNames.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onMethodSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}
