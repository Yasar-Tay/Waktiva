package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.HijriUtils
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.ReligiousDay
import com.ybugmobile.waktiva.domain.provider.ReligiousDaysProvider
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.liquidGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A modern, horizontal calendar strip that allows users to select different days.
 * Supports both Gregorian and Hijri calendar systems with a smooth transition.
 * Includes visual cues for current day, selected day, and religious events (Ramadan, Eid).
 *
 * @param selectedDate The date currently selected and focused.
 * @param availableDays The list of days available for selection.
 * @param isHijriSelected Whether the Hijri calendar system is currently active.
 * @param onToggleCalendarType Callback to switch between Gregorian and Hijri views.
 * @param onDateSelected Callback when a date is picked by the user.
 * @param contentColor Base color for text and unselected elements.
 */
@Composable
fun ModernCalendarStrip(
    selectedDate: LocalDate,
    availableDays: List<PrayerDay>,
    isHijriSelected: Boolean,
    onToggleCalendarType: (Boolean) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    contentColor: Color = Color.White
) {
    val today = LocalDate.now()
    val monthFormatter = DateTimeFormatter.ofPattern("MMM")
    val dayNameFormatter = DateTimeFormatter.ofPattern("EEE")
    
    val listState = rememberLazyListState()
    val glassTheme = LocalGlassTheme.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val currentLocale = Locale.getDefault()
    val isNonLatin = currentLocale.language in listOf("ar", "fa", "ur", "bn")

    // Automatically scroll to the selected date whenever it changes
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
        // Header Row: Calendar toggle and religious indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gregorian/Hijri Toggle
            Surface(
                color = contentColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    CalendarToggleOption(
                        icon = Icons.Default.WbSunny,
                        isSelected = !isHijriSelected,
                        onClick = { onToggleCalendarType(false) },
                        contentColor = contentColor
                    )
                    CalendarToggleOption(
                        icon = Icons.Default.NightsStay,
                        isSelected = isHijriSelected,
                        onClick = { onToggleCalendarType(true) },
                        contentColor = contentColor
                    )
                }
            }

            // High-level religious information for the selected day
            val effectiveHijri = remember(selectedDate, availableDays) {
                HijriUtils.getEffectiveHijriDate(
                    targetDate = selectedDate,
                    allPrayerDays = availableDays
                )
            }

            ReligiousBadge(gregorianDate = selectedDate, contentColor = contentColor, hijriDate = effectiveHijri)
        }

        // Horizontal List of individual day cards
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(availableDays) { index, prayerDay ->
                val date = prayerDay.date
                
                val hijri = remember(date, availableDays) {
                    HijriUtils.getEffectiveHijriDate(
                        targetDate = date,
                        allPrayerDays = availableDays
                    )
                }

                val isSelected = date == selectedDate
                val isToday = date == today

                val religiousDay = ReligiousDaysProvider.getReligiousDay(date)
                val hijriMonth = hijri?.monthNumber
                val hijriDayNum = hijri?.day
                
                // Color coding for special religious periods
                val accentColor = getCalendarAccentColor(date, hijriMonth, hijriDayNum, contentColor)
                val isRamadan = isRamadan(hijriMonth, religiousDay)
                val isEid = isEid(religiousDay)

                val isSpecial = religiousDay != null || isRamadan || isEid
                // Liquid glass: the selected card is the densest and brightest, today's next.
                val glassEmphasis by animateFloatAsState(
                    targetValue = when {
                        isSelected -> 1f
                        isToday -> 0.6f
                        isSpecial -> 0.3f
                        else -> 0f
                    },
                    label = "glassEmphasis"
                )

                Surface(
                    onClick = { onDateSelected(date) },
                    color = Color.Transparent,
                    shape = DayCardShape,
                    modifier = Modifier
                        .width(62.dp)
                        .liquidGlass(
                            shape = DayCardShape,
                            glass = glassTheme,
                            tint = if (isToday || isSpecial) accentColor else null,
                            emphasis = glassEmphasis,
                            accent = when {
                                isToday -> accentColor.copy(alpha = 0.7f)
                                isSpecial -> accentColor.copy(alpha = 0.45f)
                                else -> null
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Month Indicator (Top Bar)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    // A tinted band of glass, fading down into the card.
                                    Brush.verticalGradient(
                                        when {
                                            isToday -> listOf(accentColor.copy(alpha = 0.5f), accentColor.copy(alpha = 0f))
                                            isSpecial -> listOf(accentColor.copy(alpha = 0.45f), accentColor.copy(alpha = 0f))
                                            isSelected -> listOf(contentColor.copy(alpha = 0.26f), contentColor.copy(alpha = 0f))
                                            else -> listOf(contentColor.copy(alpha = 0.14f), contentColor.copy(alpha = 0f))
                                        }
                                    )
                                )
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val monthText = if (isHijriSelected && hijri != null) {
                                // Extract and translate Hijri month name
                                val resId = context.resources.getIdentifier(
                                    "hijri_month_${hijri.monthNumber}",
                                    "string",
                                    context.packageName
                                )
                                val translated = if (resId != 0) stringResource(resId) else hijri.monthEn
                                translated.take(3).uppercase()
                            } else {
                                date.format(monthFormatter).uppercase()
                            }
                            Text(
                                text = monthText,
                                color = if (isToday || isSpecial) contentColor else contentColor.copy(alpha = 0.6f),
                                fontSize = if (isNonLatin) 11.sp else 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Day Number (Center)
                        val dayNumber = if (isHijriSelected && hijri != null) hijri.day.toString() else date.dayOfMonth.toString()
                        Text(
                            text = dayNumber,
                            color = contentColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 22.sp
                        )

                        // Short Day Name (Bottom)
                        Text(
                            text = date.format(dayNameFormatter).uppercase(),
                            color = contentColor.copy(alpha = if (isSelected || isToday) 1f else 0.4f),
                            fontSize = if (isNonLatin) 12.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Day cards use the large, continuous-looking corners of liquid glass. */
private val DayCardShape = RoundedCornerShape(16.dp)

/** Individual toggle button for switching calendar systems. */
@Composable
private fun CalendarToggleOption(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentColor: Color
) {
    Surface(
        color = if (isSelected) contentColor.copy(alpha = 0.2f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).size(20.dp),
            tint = if (isSelected) contentColor else contentColor.copy(alpha = 0.5f)
        )
    }
}

/** Determines the thematic accent color based on religious significance. */
private fun getCalendarAccentColor(
    date: LocalDate,
    hijriMonth: Int?,
    hijriDay: Int?,
    contentColor: Color
): Color {
    return when {
        hijriMonth == 9 -> Color(0xFF4ADE80) // Ramadan Green
        isEid(ReligiousDaysProvider.getReligiousDay(date)) -> Color(0xFFFBBF24) // Eid Gold
        else -> contentColor
    }
}

/** Returns true if the given data corresponds to the month of Ramadan. */
private fun isRamadan(hijriMonth: Int?, religiousDay: ReligiousDay?): Boolean {
    return hijriMonth == 9 || religiousDay?.nameResId == R.string.rel_day_ramadan_start
}

/** Returns true if the given data corresponds to an Eid holiday. */
private fun isEid(religiousDay: ReligiousDay?): Boolean {
    return religiousDay?.nameResId in listOf(R.string.rel_day_ramadan_eid, R.string.rel_day_sacrifice_eid)
}
