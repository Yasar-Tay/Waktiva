package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.HijriData
import com.ybugmobile.vaktiva.ui.theme.Inter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeHeader(
    locationName: String,
    date: LocalDate,
    hijriDate: HijriData?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Location Section (Top Left)
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = locationName.substringBefore(",")
                        .ifEmpty { stringResource(R.string.home_unknown_location) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    letterSpacing = (-0.5).sp
                )
            }
            Text(
                text = locationName.substringAfter(", ").ifEmpty { "" }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.3f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 20.dp)
            )
        }

        // 2. Dates Section (Right Aligned)
        if (hijriDate != null) {
            val hijriMonthResId = context.resources.getIdentifier(
                "hijri_month_${hijriDate.monthNumber}", 
                "string", 
                context.packageName
            )
            val translatedMonth = if (hijriMonthResId != 0) stringResource(hijriMonthResId) else hijriDate.monthEn

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Gregorian Date Group
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = date.format(dayFormatter).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = contentColor.copy(alpha = 0.3f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = date.format(dateFormatter),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }

                // Elegant Vertical Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .height(24.dp)
                        .background(contentColor.copy(alpha = 0.15f))
                )

                // Hijri Date Group
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = hijriDate.day.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = Inter,
                                fontWeight = FontWeight.Light
                            ),
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = translatedMonth.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = contentColor,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Text(
                        text = "${hijriDate.year} AH",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
