package com.ybugmobile.vaktiva.ui.home.composables

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R

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

    val displayTitle = when {
        !isLocationEnabled -> stringResource(R.string.home_gps_disabled_location)
        !isLocationPermissionGranted -> stringResource(R.string.home_location_permission_denied)
        !isNetworkAvailable -> stringResource(R.string.home_offline_status)
        else -> city
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showStatusIcon && statusIcon != null) {
                    statusIcon(Modifier.fillMaxSize())
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

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                letterSpacing = (-0.5).sp,
                maxLines = 1
            )
        }
        
        val subTitle = if (isFallbackState) {
            if (city != unknownStr && city.isNotEmpty()) {
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
                modifier = Modifier.padding(start = 36.dp),
                maxLines = 1
            )
        }
    }
}
