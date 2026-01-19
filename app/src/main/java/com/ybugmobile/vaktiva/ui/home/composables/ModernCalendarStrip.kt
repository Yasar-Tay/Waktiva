package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ModernCalendarStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val calendarDays = remember {
        (0..13).map { today.plusDays(it.toLong()) }
    }

    val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val dayNameFormatter = DateTimeFormatter.ofPattern("EEE")

    // Language-aware "Today" label
    val todayLabel = if (Locale.getDefault().language == "tr") "BUGÜN" else "TODAY"

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedDate.format(monthYearFormatter),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(calendarDays) { date ->
                val isSelected = date == selectedDate
                val isToday = date == today

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f),
                    label = "bgColor"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else Color.White,
                    label = "contentColor"
                )
                val borderColor =
                    if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.15f)

                Surface(
                    color = backgroundColor,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .width(60.dp)
                        .height(84.dp)
                        .clickable { onDateSelected(date) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header section for Day Name with distinct background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.38f)
                                .background(
                                    if (isSelected) Color.Transparent else Color.Black.copy(
                                        alpha = 0.2f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.format(dayNameFormatter).uppercase(),
                                color = contentColor.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Subtle Divider
                        HorizontalDivider(
                            color = if (isSelected) Color.Black.copy(alpha = 0.1f) else Color.White.copy(
                                alpha = 0.1f
                            ),
                            thickness = 0.5.dp
                        )

                        // Body section for Date
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.62f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                color = contentColor,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )

                            if (isToday) {
                                Text(
                                    text = todayLabel,
                                    color = if (isSelected) Color.Blue else Color.Cyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}