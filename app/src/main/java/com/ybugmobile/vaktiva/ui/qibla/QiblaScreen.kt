package com.ybugmobile.vaktiva.ui.qibla

import android.Manifest
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var hasShownCalibrationDialog by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(state.compassData.accuracy) {
        if (isAccuracyUnreliable && !hasShownCalibrationDialog) {
            showCalibrationDialog = true
            hasShownCalibrationDialog = true
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
                    isAccuracyUnreliable = isAccuracyUnreliable,
                    pulseAlpha = pulseAlpha,
                    animatedAzimuth = animatedAzimuth,
                    isAligned = isAligned,
                    alignmentColor = alignmentColor,
                    contentColor = contentColor,
                    textShadow = textShadow,
                    isLightBackground = isLightBackground,
                    onCalibrationClick = { showCalibrationDialog = true }
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
    isAccuracyUnreliable: Boolean,
    pulseAlpha: Float,
    animatedAzimuth: Float,
    isAligned: Boolean,
    alignmentColor: Color,
    contentColor: Color,
    textShadow: Shadow?,
    isLightBackground: Boolean,
    onCalibrationClick: () -> Unit
) {
    var isMapView by remember { mutableStateOf(false) }
    var isSatelliteView by remember { mutableStateOf(false) }
    val kaabaLatLng = LatLng(21.4225, 39.8262)

    val effectiveContentColor = if (isMapView) {
        if (isSatelliteView) Color.White else Color.Black
    } else contentColor

    val effectiveShadow = if (isMapView && isSatelliteView) textShadow else if (isMapView) null else textShadow

    Box(modifier = Modifier.fillMaxSize()) {
        // Background layer
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
        } else if (isAccuracyLow) {
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

        // TOP HEADER: Location (Left) and View Switcher (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location Info (Improved Design & Contrast)
            state.settings?.let { s ->
                Surface(
                    color = if (isMapView) MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            else Color.Transparent,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    shadowElevation = if (isMapView) 4.dp else 0.dp,
                    tonalElevation = if (isMapView) 2.dp else 0.dp
                ) {
                    val locationTextColor = if (isMapView) MaterialTheme.colorScheme.onSurface else effectiveContentColor
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = locationTextColor, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = s.locationName.substringBefore(","),
                                color = locationTextColor,
                                style = MaterialTheme.typography.titleMedium.copy(shadow = if (isMapView) null else effectiveShadow),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.4f, %.4f", s.latitude, s.longitude),
                            color = locationTextColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall.copy(shadow = if (isMapView) null else effectiveShadow),
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }
                }
            }

            // Segmented Switcher (Improved Design & Contrast)
            Surface(
                color = if (isMapView) MaterialTheme.colorScheme.surface.copy(alpha = 0.95f) 
                        else if (isLightBackground) Color.Black.copy(0.1f) 
                        else Color.White.copy(0.15f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.height(44.dp),
                shadowElevation = if (isMapView) 4.dp else 0.dp,
                tonalElevation = if (isMapView) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwitcherButton(
                        isSelected = !isMapView,
                        icon = Icons.Default.Explore,
                        contentColor = effectiveContentColor,
                        isMapView = isMapView,
                        onClick = { isMapView = false }
                    )
                    SwitcherButton(
                        isSelected = isMapView,
                        icon = Icons.Default.Map,
                        contentColor = effectiveContentColor,
                        isMapView = isMapView,
                        onClick = { isMapView = true }
                    )
                }
            }
        }

        // MAIN CONTENT
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Spacer for Header
            Spacer(modifier = Modifier.height(100.dp))

            // MIDDLE: Compass Area
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!isMapView) {
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

            // BOTTOM: Info Card and Calibration FAB
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Box {
                    QiblaInfoCard(
                        isAligned = isAligned,
                        alignmentColor = alignmentColor,
                        settings = state.settings,
                        qiblaDirection = state.qiblaDirection,
                        compassData = state.compassData,
                        isAccuracyLow = isAccuracyLow,
                    )
                    
                    if (isAccuracyUnreliable) {
                        SmallFloatingActionButton(
                            onClick = onCalibrationClick,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 12.dp, y = (-20).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Calibration Needed",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SwitcherButton(
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentColor: Color,
    isMapView: Boolean,
    onClick: () -> Unit
) {
    val theme = MaterialTheme.colorScheme
    
    val bgColor by animateColorAsState(
        if (isSelected) {
            if (isMapView) theme.primary else Color.White.copy(alpha = 0.9f)
        } else Color.Transparent,
        label = "bg"
    )
    val iconColor by animateColorAsState(
        if (isSelected) {
            if (isMapView) theme.onPrimary else Color.Black
        } else {
            if (isMapView) theme.onSurface.copy(alpha = 0.6f) else contentColor.copy(alpha = 0.7f)
        },
        label = "icon"
    )

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
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
