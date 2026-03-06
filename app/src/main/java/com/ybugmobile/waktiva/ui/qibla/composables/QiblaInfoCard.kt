package com.ybugmobile.waktiva.ui.qibla.composables

import android.content.res.Configuration
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.sensor.CompassData

@Composable
fun QiblaInfoCard(
    isAligned: Boolean,
    alignmentColor: Color,
    qiblaDirection: Double,
    compassData: CompassData,
    isAccuracyLow: Boolean,
    isAccuracyUnreliable: Boolean,
    onCalibrationClick: () -> Unit,
    containerColor: Color,
    contentColor: Color
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = contentColor.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(if (isLandscape) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Row: Status Pill & Calibration Warning
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    isAligned = isAligned,
                    alignmentColor = alignmentColor,
                    contentColor = contentColor
                )

                if (isAccuracyLow || isAccuracyUnreliable) {
                    CalibrationWarningPill(
                        onClick = onCalibrationClick,
                        contentColor = contentColor
                    )
                }
            }

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricItem(
                    label = stringResource(R.string.qibla_label),
                    value = "${qiblaDirection.toInt()}°",
                    icon = Icons.Rounded.MyLocation,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider(contentColor.copy(alpha = 0.1f))

                MetricItem(
                    label = stringResource(R.string.qibla_heading),
                    value = "${(compassData.azimuth.toInt() + 360) % 360}°",
                    icon = Icons.Rounded.Explore,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider(contentColor.copy(alpha = 0.1f))

                AccuracyItem(
                    accuracy = compassData.accuracy,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    isAligned: Boolean,
    alignmentColor: Color,
    contentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        color = contentColor.copy(alpha = 0.05f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(alignmentColor.copy(alpha = if (isAligned) dotAlpha else 1f))
            )
            Text(
                text = if (isAligned) stringResource(R.string.qibla_mecca_aligned)
                       else stringResource(R.string.qibla_rotate_phone),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                letterSpacing = 0.2.sp
            )
        }
    }
}

@Composable
private fun CalibrationWarningPill(
    onClick: () -> Unit,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = Color(0xFFF87171).copy(alpha = 0.1f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Rounded.CompassCalibration,
                null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFF87171)
            )
            Text(
                text = stringResource(R.string.qibla_calibrate).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color(0xFFF87171),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    icon: ImageVector,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = contentColor
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.3f),
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun AccuracyItem(
    accuracy: Int,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val barColor = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color(0xFF4CAF50)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color(0xFFFFD700)
        else -> Color(0xFFF87171)
    }

    val bars = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 3
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 2
        else -> 1
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.height(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (i in 1..3) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height((4 * i + 4).dp)
                        .clip(CircleShape)
                        .background(if (i <= bars) barColor else contentColor.copy(alpha = 0.1f))
                )
            }
        }
        Text(
            text = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> stringResource(R.string.accuracy_high)
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> stringResource(R.string.accuracy_med)
                else -> stringResource(R.string.accuracy_low)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
        Text(
            text = stringResource(R.string.qibla_signal).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.3f),
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun VerticalDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(color)
    )
}
