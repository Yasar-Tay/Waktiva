package com.ybugmobile.vaktiva.ui.qibla.composables

import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.data.sensor.CompassData

@Composable
fun QiblaInfoCard(
    isAligned: Boolean,
    alignmentColor: Color,
    settings: UserSettings?,
    qiblaDirection: Double,
    compassData: CompassData,
    isAccuracyLow: Boolean,
    isAccuracyUnreliable: Boolean,
    onCalibrationClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = 0.95f
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAligned) stringResource(R.string.qibla_mecca_aligned) else stringResource(
                            R.string.qibla_rotate_phone
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = alignmentColor
                    )
                    Text(
                        text = settings?.let {
                            stringResource(
                                R.string.qibla_direction,
                                qiblaDirection.toInt()
                            )
                        } ?: stringResource(R.string.qibla_locating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAccuracyLow) {
                        FilledIconButton(
                            onClick = onCalibrationClick,
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Calibration Needed",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(alignmentColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAligned) Icons.Default.CheckCircle else Icons.Default.NearMe,
                            contentDescription = null,
                            tint = alignmentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompactMetric(
                    label = stringResource(R.string.qibla_label),
                    value = "${qiblaDirection.toInt()}°",
                    icon = Icons.Default.Place
                )
                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                CompactMetric(
                    label = stringResource(R.string.qibla_heading),
                    value = "${(compassData.azimuth.toInt() + 360) % 360}°",
                    icon = Icons.Default.Explore
                )
                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                CompactMetric(
                    label = stringResource(R.string.qibla_signal),
                    value = getAccuracyLabel(compassData.accuracy),
                    icon = Icons.Default.Wifi,
                    color = if (isAccuracyLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
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