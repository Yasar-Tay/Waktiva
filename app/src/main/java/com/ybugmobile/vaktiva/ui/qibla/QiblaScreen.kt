package com.ybugmobile.vaktiva.ui.qibla

import android.Manifest
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.qibla.composables.CalibrationDialog
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.ui.qibla.composables.ProfessionalCompass
import com.ybugmobile.vaktiva.ui.qibla.composables.QiblaInfoCard
import com.ybugmobile.vaktiva.ui.qibla.composables.QiblaMap
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(
    viewModel: QiblaViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    val context = LocalContext.current
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    // Logic for accuracy warning
    val isAccuracyLow = state.compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    val isAccuracyUnreliable = state.compassData.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE

    var showCalibrationDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.compassData.accuracy) {
        if (isAccuracyUnreliable) {
            showCalibrationDialog = true
        }
    }

    // Background logic
    val backgroundGradient = getGradientForTime(state.currentTime.toLocalTime(), state.currentPrayerDay)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val animatedAzimuth by animateFloatAsState(
        targetValue = state.compassData.azimuth,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "azimuth"
    )

    var relativeQiblaAngle = (state.qiblaDirection.toFloat() - state.compassData.azimuth)
    while (relativeQiblaAngle <= -180) relativeQiblaAngle += 360
    while (relativeQiblaAngle > 180) relativeQiblaAngle -= 360

    val isAligned = abs(relativeQiblaAngle) < 3f
    val alignmentColor by animateColorAsState(
        targetValue = if (isAligned) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
        label = "alignmentColor"
    )

    // Dynamic Color Logic
    val sunrise = state.currentPrayerDay?.timings?.get(PrayerType.SUNRISE)
    val sunset = state.currentPrayerDay?.timings?.get(PrayerType.MAGHRIB)
    val isLightBackground = if (sunrise != null && sunset != null) {
        val t = state.currentTime.toLocalTime()
        t.isAfter(sunrise) && t.isBefore(sunset)
    } else false

    val contentColor = if (isLightBackground) Color.Black else Color.White
    val textShadow = if (!isLightBackground) Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    ) else null

    Box(modifier = Modifier.fillMaxSize().background(brush = backgroundGradient)) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = contentColor)
            }
        } else {
            if (locationPermissionState.status.isGranted) {
                QiblaContent(
                    state = state,
                    isAccuracyLow = isAccuracyLow,
                    pulseAlpha = pulseAlpha,
                    animatedAzimuth = animatedAzimuth,
                    isAligned = isAligned,
                    alignmentColor = alignmentColor,
                    contentColor = contentColor,
                    textShadow = textShadow,
                    isLightBackground = isLightBackground
                )
            } else {
                LocationRequiredFallback(onGrantClick = { locationPermissionState.launchPermissionRequest() })
            }
        }

        if (showCalibrationDialog) {
            CalibrationDialog(onDismiss = { showCalibrationDialog = false })
        }
    }
}

@Composable
private fun QiblaContent(
    state: QiblaViewState,
    isAccuracyLow: Boolean,
    pulseAlpha: Float,
    animatedAzimuth: Float,
    isAligned: Boolean,
    alignmentColor: Color,
    contentColor: Color,
    textShadow: Shadow?,
    isLightBackground: Boolean
) {
    var isMapView by remember { mutableStateOf(false) }
    var isSatelliteView by remember { mutableStateOf(false) }
    val kaabaLatLng = LatLng(21.4225, 39.8262)

    Box(modifier = Modifier.fillMaxSize()) {
        if (isAccuracyLow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        if (isMapView) {
            QiblaMap(
                settings = state.settings,
                compassData = state.compassData,
                isSatelliteView = isSatelliteView,
                kaabaLatLng = kaabaLatLng,
                onMapReady = { },
                onMapLongClick = { },
                onToggleSatellite = { isSatelliteView = !isSatelliteView }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().offset(y = (-60).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        if (isLightBackground) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    ProfessionalCompass(
                        azimuth = animatedAzimuth,
                        qiblaAngle = state.qiblaDirection.toFloat(),
                        alignmentColor = alignmentColor,
                        isAligned = isAligned,
                        contentColor = contentColor
                    )
                }
            }
        }

        // TOP: Location Info
        state.settings?.let { s ->
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = s.locationName,
                            color = contentColor,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.4f, %.4f", s.latitude, s.longitude),
                        color = contentColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }
            }
        }

        // Switcher
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                    .padding(4.dp)
            ) {
                FilterChip(
                    selected = !isMapView,
                    onClick = { isMapView = false },
                    label = { Text(stringResource(R.string.qibla_compass)) },
                    leadingIcon = { Icon(Icons.Default.Explore, null) },
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                FilterChip(
                    selected = isMapView,
                    onClick = { isMapView = true },
                    label = { Text(stringResource(R.string.qibla_map)) },
                    leadingIcon = { Icon(Icons.Default.Map, null) },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }

        // BOTTOM: Info Card
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            QiblaInfoCard(
                isAligned = isAligned,
                alignmentColor = alignmentColor,
                settings = state.settings,
                qiblaDirection = state.qiblaDirection,
                compassData = state.compassData,
                isAccuracyLow = isAccuracyLow,
            )
        }
    }
}

@Composable
private fun LocationRequiredFallback(onGrantClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = stringResource(R.string.qibla_location_required), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(text = stringResource(R.string.qibla_location_required_desc), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onGrantClick, shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.qibla_grant_permission))
            }
        }
    }
}
