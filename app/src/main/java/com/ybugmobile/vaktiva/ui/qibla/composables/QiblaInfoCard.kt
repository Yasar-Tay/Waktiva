package com.ybugmobile.vaktiva.ui.qibla.composables

import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Top Section: Alignment Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAligned) stringResource(R.string.qibla_mecca_aligned).uppercase()
                               else stringResource(R.string.qibla_rotate_phone).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (isAligned) Color(0xFF4CAF50) else Color.White,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAligned) stringResource(R.string.qibla_kaaba_aligned) 
                               else stringResource(R.string.qibla_find_marker),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                if (isAccuracyLow) {
                    FilledTonalButton(
                        onClick = onCalibrationClick,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Rounded.CompassCalibration, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.qibla_calibrate).uppercase(), 
                            style = MaterialTheme.typography.labelLarge, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = stringResource(R.string.qibla_label).uppercase(),
                    value = "${qiblaDirection.toInt()}°",
                    icon = Icons.Rounded.MyLocation,
                    modifier = Modifier.weight(1f)
                )
                
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.1f)).align(Alignment.CenterVertically))
                
                MetricItem(
                    label = stringResource(R.string.qibla_heading).uppercase(),
                    value = "${(compassData.azimuth.toInt() + 360) % 360}°",
                    icon = Icons.Rounded.Explore,
                    modifier = Modifier.weight(1f)
                )

                Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.1f)).align(Alignment.CenterVertically))

                MetricItem(
                    label = stringResource(R.string.qibla_signal).uppercase(),
                    value = getAccuracyLabel(compassData.accuracy).uppercase(),
                    icon = Icons.Rounded.WifiTethering,
                    color = if (isAccuracyLow) Color(0xFFF87171) else Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
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
