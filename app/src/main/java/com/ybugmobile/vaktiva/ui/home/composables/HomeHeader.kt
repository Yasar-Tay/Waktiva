package com.ybugmobile.vaktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ybugmobile.vaktiva.domain.model.HijriData
import com.ybugmobile.vaktiva.domain.model.HijriUtils
import java.time.LocalDate

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
            .padding(top = 10.dp)
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
                    DatesSection(
                        date = date,
                        hijriDate = effectiveHijri,
                        contentColor = contentColor,
                        context = context,
                        isOffline = hijriDate == null
                    )
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
                DatesSection(
                    date = date,
                    hijriDate = effectiveHijri,
                    contentColor = contentColor,
                    context = context,
                    isOffline = hijriDate == null
                )
                Spacer(modifier = Modifier.height(12.dp))
                ReligiousBadge(date = date, contentColor = contentColor, hijriDate = effectiveHijri)
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
