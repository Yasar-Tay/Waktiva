package com.ybugmobile.vaktiva.ui.qibla.composables

import android.content.res.Configuration
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.sensor.CompassData

@Composable
fun QiblaInfoCard(
    isAligned: Boolean,
    alignmentColor: Color,
    qiblaDirection: Double,
    compassData: CompassData,
    isAccuracyLow: Boolean,
    isAccuracyUnreliable: Boolean,
    onCalibrationClick: () -> Unit,
    isMapView: Boolean = false,
    isSatelliteView: Boolean = false
) {
    val theme = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Dynamic Colors based on View Mode and Orientation
    val containerColor by animateColorAsState(
        targetValue = when {
            isMapView -> theme.surface // Solid surface color
            else -> Color.White.copy(alpha = 0.1f)
        },
        label = "containerColor"
    )
    
    val contentColor = when {
        isMapView && !isLandscape -> theme.onSurface
        else -> Color.White
    }

    val dividerColor = contentColor.copy(alpha = 0.1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = contentColor.copy(alpha = 0.12f)
        ),
        shadowElevation = if (isMapView && !isLandscape) 6.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Section: Status & Calibration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isAligned) Icons.Rounded.Verified else Icons.Rounded.RotateRight,
                        contentDescription = null,
                        tint = if (isAligned) alignmentColor else contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isAligned) stringResource(R.string.qibla_mecca_aligned)
                               else stringResource(R.string.qibla_rotate_phone),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        letterSpacing = 0.2.sp
                    )
                }

                if (isAccuracyLow || isAccuracyUnreliable) {
                    TextButton(
                        onClick = onCalibrationClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (isMapView && !isLandscape) theme.error else Color(0xFFF87171)
                        )
                    ) {
                        Icon(Icons.Rounded.CompassCalibration, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.qibla_calibrate),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = dividerColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Section
            if (isLandscape) {
                // Correctly use LandscapeMetricRow for vertical stacking in landscape
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LandscapeMetricRow(
                        label = stringResource(R.string.qibla_label),
                        value = "${qiblaDirection.toInt()}°",
                        icon = Icons.Rounded.MyLocation,
                        contentColor = contentColor
                    )
                    LandscapeMetricRow(
                        label = stringResource(R.string.qibla_heading),
                        value = "${(compassData.azimuth.toInt() + 360) % 360}°",
                        icon = Icons.Rounded.Explore,
                        contentColor = contentColor
                    )
                    LandscapeMetricRow(
                        label = stringResource(R.string.qibla_signal),
                        value = getAccuracyLabel(compassData.accuracy),
                        icon = Icons.Rounded.WifiTethering,
                        color = if (isAccuracyLow) Color(0xFFF87171) else Color(0xFF4CAF50),
                        contentColor = contentColor
                    )
                }
            } else {
                // Horizontal layout for Portrait with Intrinsic height for dividers
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactMetricItem(
                        label = stringResource(R.string.qibla_label),
                        value = "${qiblaDirection.toInt()}°",
                        icon = Icons.Rounded.MyLocation,
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    
                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = dividerColor
                    )
                    
                    CompactMetricItem(
                        label = stringResource(R.string.qibla_heading),
                        value = "${(compassData.azimuth.toInt() + 360) % 360}°",
                        icon = Icons.Rounded.Explore,
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )

                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = dividerColor
                    )

                    CompactMetricItem(
                        label = stringResource(R.string.qibla_signal),
                        value = getAccuracyLabel(compassData.accuracy),
                        icon = Icons.Rounded.WifiTethering,
                        color = if (isAccuracyLow) Color(0xFFF87171) else Color(0xFF4CAF50),
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeMetricRow(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color? = null,
    contentColor: Color
) {
    val effectiveColor = color ?: contentColor
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveColor.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = effectiveColor
        )
    }
}

@Composable
private fun CompactMetricItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color? = null,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val effectiveColor = color ?: contentColor
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveColor.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = effectiveColor
            )
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun getAccuracyLabel(accuracy: Int): String {
    return when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> stringResource(R.string.accuracy_high)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> stringResource(R.string.accuracy_med)
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> stringResource(R.string.accuracy_low)
        else -> stringResource(R.string.accuracy_poor)
    }
}
