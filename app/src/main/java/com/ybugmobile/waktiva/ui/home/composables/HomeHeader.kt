package com.ybugmobile.waktiva.ui.home.composables

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.domain.model.HijriData
import java.time.LocalDate

/**
 * Header component for the Home screen.
 * Displays the current location information and provides access to system health status.
 *
 * @param locationName The name of the current location.
 * @param date The selected Gregorian date.
 * @param hijriDate The corresponding Hijri date data.
 * @param contentColor The preferred color for text and icons.
 * @param onStatusClick Callback when the location/status area is tapped.
 * @param modifier Modifier for the root container.
 * @param isNetworkAvailable Connectivity status.
 * @param isLocationEnabled System-level location toggle status.
 * @param isLocationPermissionGranted App-level location permission status.
 */
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)

    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                LocationSection(
                    locationName = locationName, 
                    contentColor = contentColor, 
                    isNetworkAvailable = isNetworkAvailable, 
                    isLocationEnabled = isLocationEnabled,
                    isLocationPermissionGranted = isLocationPermissionGranted,
                    onStatusClick = onStatusClick
                )
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
