package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ModernCalendarStrip(
    selectedDate: LocalDate,
    availableDates: List<LocalDate>,
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
            items(availableDates) { date ->
                val isSelected = date == selectedDate
                val isToday = date == today

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) contentColor.copy(alpha = 0.2f) else Color.Transparent,
                    label = "bgColor"
                )
                
                Surface(
                    onClick = { onDateSelected(date) },
                    color = bgColor,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(54.dp)
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
                            color = contentColor,
                            fontSize = 18.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                        if (isToday) {
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .size(4.dp)
                                    .background(contentColor, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
