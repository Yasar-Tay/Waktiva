package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.provider.ReligiousDaysProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ModernCalendarStrip(
    selectedDate: LocalDate,
    availableDays: List<PrayerDay>,
    onDateSelected: (LocalDate) -> Unit,
    contentColor: Color = Color.White
) {
    val today = LocalDate.now()
    val monthFormatter = DateTimeFormatter.ofPattern("MMM")
    val dayNameFormatter = DateTimeFormatter.ofPattern("EEE")
    
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Slower Auto-scroll to selected date
    LaunchedEffect(selectedDate) {
        val index = availableDays.indexOfFirst { it.date == selectedDate }
        if (index != -1) {
            val itemWidthWithSpacingPx = with(density) { 72.dp.toPx() }
            val targetScrollPx = index * itemWidthWithSpacingPx
            val currentScrollPx = listState.firstVisibleItemIndex * itemWidthWithSpacingPx + 
                                 listState.firstVisibleItemScrollOffset
            
            val scrollDelta = targetScrollPx - currentScrollPx
            
            listState.animateScrollBy(
                value = scrollDelta,
                animationSpec = tween(
                    durationMillis = 1000, 
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(availableDays) { index, prayerDay ->
                val date = prayerDay.date
                val hijri = prayerDay.hijriDate
                val isSelected = date == selectedDate
                val isToday = date == today

                val religiousDay = ReligiousDaysProvider.getReligiousDay(date)
                val isReligiousDay = religiousDay != null
                
                val isRamadan = hijri?.monthNumber == 9
                val isEidFitr = hijri?.monthNumber == 10 && hijri.day in 1..3
                val isEidAdha = hijri?.monthNumber == 12 && hijri.day in 10..13
                val isEid = isEidFitr || isEidAdha

                // Muted, sophisticated colors for special days
                val accentColor = when {
                    isEid -> Color(0xFFFBC02D) // Soft Gold
                    isRamadan -> Color(0xFF81C784) // Sage Green
                    isReligiousDay -> Color(0xFF90CAF9) // Soft Blue
                    else -> contentColor.copy(alpha = 0.15f)
                }

                val cardBgColor by animateColorAsState(
                    targetValue = if (isSelected) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                    label = "cardBg"
                )

                Surface(
                    onClick = { onDateSelected(date) },
                    color = cardBgColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .width(62.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, contentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            } else if (isReligiousDay || isRamadan || isEid) {
                                Modifier.border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            } else Modifier
                        )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Section: Month Label
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isReligiousDay || isRamadan || isEid -> accentColor.copy(alpha = 0.35f)
                                        isSelected -> contentColor.copy(alpha = 0.2f)
                                        else -> contentColor.copy(alpha = 0.1f)
                                    }
                                )
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.format(monthFormatter).uppercase(),
                                color = if (isReligiousDay || isRamadan || isEid) contentColor else contentColor.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Middle Section: Day Number
                        Text(
                            text = date.dayOfMonth.toString(),
                            color = contentColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 22.sp
                        )

                        // Bottom Section: Day Name
                        Text(
                            text = date.format(dayNameFormatter).uppercase(),
                            color = contentColor.copy(alpha = if (isSelected) 1f else 0.4f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // Visual indicator for today or special days
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            if (isToday) {
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .background(if (isSelected) contentColor else accentColor, CircleShape)
                                )
                            } else if (isReligiousDay || isRamadan || isEid) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = accentColor.copy(alpha = 0.8f),
                                    modifier = Modifier.size(8.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
