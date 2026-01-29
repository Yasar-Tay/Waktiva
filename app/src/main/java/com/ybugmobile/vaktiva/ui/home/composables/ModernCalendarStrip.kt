package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ModernCalendarStrip(
    selectedDate: LocalDate,
    availableDays: List<PrayerDay>,
    onDateSelected: (LocalDate) -> Unit,
    contentColor: Color = Color.White
) {
    val today = LocalDate.now()
    val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val dayNameFormatter = DateTimeFormatter.ofPattern("EEE")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedDate.format(monthYearFormatter),
            color = contentColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            letterSpacing = 1.sp
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(availableDays) { prayerDay ->
                val date = prayerDay.date
                val hijri = prayerDay.hijriDate
                val isSelected = date == selectedDate
                val isToday = date == today

                // Logic for Special Days
                val isRamadan = hijri?.monthNumber == 9
                val isEidFitr = hijri?.monthNumber == 10 && hijri.day in 1..3
                val isEidAdha = hijri?.monthNumber == 12 && hijri.day in 10..13
                val isEid = isEidFitr || isEidAdha

                val specialColor = when {
                    isEid -> Color(0xFFFFD700) // Gold for Eid
                    isRamadan -> Color(0xFF81C784) // Soft Green for Ramadan
                    else -> null
                }

                val bgColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> contentColor.copy(alpha = 0.2f)
                        isEid -> specialColor?.copy(alpha = 0.25f) ?: Color.Transparent
                        isRamadan -> specialColor?.copy(alpha = 0.15f) ?: Color.Transparent
                        else -> Color.Transparent
                    },
                    label = "bgColor"
                )
                
                Surface(
                    onClick = { onDateSelected(date) },
                    color = bgColor,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(58.dp)
                        .then(
                            if ((isEid || isRamadan) && !isSelected) {
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = specialColor?.copy(alpha = 0.4f) ?: Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                            } else Modifier
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.format(dayNameFormatter).uppercase(),
                            color = contentColor.copy(alpha = if (isSelected) 1f else 0.4f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        
                        Text(
                            text = date.dayOfMonth.toString(),
                            color = if (isEid && !isSelected) specialColor!! else contentColor,
                            fontSize = 18.sp,
                            fontWeight = if (isSelected || isEid || isRamadan) FontWeight.ExtraBold else FontWeight.Medium
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.height(10.dp)
                        ) {
                            if (isToday) {
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .background(contentColor, CircleShape)
                                )
                            }
                            
                            if (isEid || isRamadan) {
                                if (isToday) Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = specialColor ?: contentColor,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
