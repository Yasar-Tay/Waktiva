package com.ybugmobile.vaktiva.ui.home.composables

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.HijriData
import com.ybugmobile.vaktiva.domain.model.HijriUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeHeader(
    locationName: String,
    date: LocalDate,
    hijriDate: HijriData?,
    contentColor: Color,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNetworkAvailable: Boolean = true,
    isLocationEnabled: Boolean = true,
    isLocationPermissionGranted: Boolean = true
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val effectiveHijri = remember(hijriDate, date) {
        hijriDate ?: HijriUtils.calculateFallbackHijri(date)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp)
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocationSection(
                        locationName = locationName, 
                        contentColor = contentColor, 
                        isNetworkAvailable = isNetworkAvailable, 
                        isLocationEnabled = isLocationEnabled,
                        isLocationPermissionGranted = isLocationPermissionGranted,
                        onStatusClick = onStatusClick
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Top
                ) {
                    DatesSection(date, effectiveHijri, contentColor, context, isOffline = hijriDate == null)
                    Spacer(modifier = Modifier.height(12.dp))
                    ReligiousBadge(date = date, contentColor = contentColor, hijriDate = effectiveHijri)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.Start) {
                LocationSection(
                    locationName = locationName, 
                    contentColor = contentColor, 
                    isNetworkAvailable = isNetworkAvailable, 
                    isLocationEnabled = isLocationEnabled,
                    isLocationPermissionGranted = isLocationPermissionGranted,
                    onStatusClick = onStatusClick
                )
                Spacer(modifier = Modifier.height(12.dp))
                DatesSection(date, effectiveHijri, contentColor, context, isOffline = hijriDate == null)
                Spacer(modifier = Modifier.height(12.dp))
                ReligiousBadge(date = date, contentColor = contentColor, hijriDate = effectiveHijri)
            }
        }
    }
}

@Composable
private fun DatesSection(
    date: LocalDate,
    hijriDate: HijriData?,
    contentColor: Color,
    context: Context,
    isOffline: Boolean = false
) {
    val currentLocale = Locale.getDefault()
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM", currentLocale)
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE", currentLocale)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = date.format(dayFormatter).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
            Text(
                text = date.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor.copy(alpha = 0.8f)
            )
        }

        if (hijriDate != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(1.dp)
                    .height(32.dp)
                    .background(contentColor.copy(alpha = 0.1f))
            )

            val hijriMonthResId = context.resources.getIdentifier(
                "hijri_month_${hijriDate.monthNumber}",
                "string",
                context.packageName
            )
            val translatedMonth = if (hijriMonthResId != 0) stringResource(hijriMonthResId) else hijriDate.monthEn

            Column(horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(currentLocale, "%d", hijriDate.day),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = if (currentLocale.language in listOf("ar", "fa", "ur")) 
                                MaterialTheme.typography.headlineSmall.fontFamily 
                            else MaterialTheme.typography.titleMedium.fontFamily
                        ),
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = translatedMonth.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isOffline) contentColor.copy(alpha = 0.7f) else contentColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = String.format(currentLocale, "%d %s", hijriDate.year, stringResource(R.string.hijri_suffix)) + 
                           if (isOffline) stringResource(R.string.home_hijri_offline_indicator) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeHeaderPreview() {
    MaterialTheme {
        HomeHeader(
            locationName = "Istanbul, Turkey",
            date = LocalDate.of(2026, 3, 17),
            hijriDate = HijriData(30, 8, "Sha'ban", 1446),
            contentColor = Color.White,
            onStatusClick = {}
        )
    }
}
