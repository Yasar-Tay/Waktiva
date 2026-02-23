package com.ybugmobile.vaktiva.ui.home.composables

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
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
    modifier: Modifier = Modifier,
    statusIcon: (@Composable (Modifier) -> Unit)? = null,
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
                        statusIcon = statusIcon
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
                    statusIcon = statusIcon
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
fun LocationSection(
    locationName: String,
    contentColor: Color,
    isNetworkAvailable: Boolean,
    isLocationEnabled: Boolean,
    isLocationPermissionGranted: Boolean,
    modifier: Modifier = Modifier,
    statusIcon: (@Composable (Modifier) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isArabic = configuration.locales[0].language == "ar"
    
    val unknownStr = stringResource(R.string.home_unknown_location)
    val isFallbackState = !isLocationEnabled || !isNetworkAvailable || !isLocationPermissionGranted
    
    // Determine the city name, replacing sentinel "Unknown" with localized string
    val rawCity = locationName.substringBefore(",")
    val city = if (rawCity == "Unknown" || rawCity.isEmpty()) unknownStr else rawCity
    
    // Show statusIcon only if there is a health issue, otherwise show standard location pin
    val showStatusIcon = isFallbackState && statusIcon != null

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showStatusIcon) {
                    statusIcon?.invoke(Modifier.fillMaxSize())
                } else {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))

            val displayTitle = when {
                !isLocationEnabled -> stringResource(R.string.home_gps_disabled_location)
                !isLocationPermissionGranted -> stringResource(R.string.home_location_permission_denied)
                !isNetworkAvailable -> stringResource(R.string.home_offline_status)
                else -> city
            }

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                letterSpacing = (-0.5).sp
            )
        }
        
        val subTitle = if (isFallbackState) {
            if (locationName.isNotEmpty() && locationName != "Unknown" && locationName != unknownStr) {
                stringResource(R.string.home_last_known_location, city)
            } else ""
        } else {
            locationName.substringAfter(", ").ifEmpty { "" }
        }

        if (subTitle.isNotEmpty()) {
            Text(
                text = subTitle.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isArabic) 14.sp else 11.sp
                ),
                color = contentColor.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                // Aligned padding: Box width (28.dp) + Spacer width (8.dp) = 36.dp
                modifier = Modifier.padding(start = 36.dp)
            )
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
            contentColor = Color.White
        )
    }
}
