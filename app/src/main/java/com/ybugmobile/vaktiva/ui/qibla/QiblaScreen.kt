package com.ybugmobile.vaktiva.ui.qibla

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.home.composables.LocationSection
import com.ybugmobile.vaktiva.ui.qibla.composables.*
import com.ybugmobile.vaktiva.ui.settings.composables.SystemHealthOverlay
import com.ybugmobile.vaktiva.ui.theme.getGlassTheme
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
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

    val isAccuracyLow = state.compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    val isAccuracyUnreliable = state.compassData.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE

    var showCalibrationDialog by remember { mutableStateOf(false) }
    var showHealthOverlay by remember { mutableStateOf(false) }

    val backgroundGradient = getGradientForTime(state.currentTime.toLocalTime(), state.currentPrayerDay)
    val glassTheme = getGlassTheme(state.currentTime.toLocalTime(), state.currentPrayerDay)

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

    val contentColor = glassTheme.contentColor
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.3f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    )

    Box(modifier = Modifier.fillMaxSize().background(brush = backgroundGradient)) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = contentColor)
            }
        } else {
            QiblaContent(
                state = state,
                isAccuracyLow = isAccuracyLow,
                isAccuracyUnreliable = isAccuracyUnreliable,
                pulseAlpha = pulseAlpha,
                animatedAzimuth = animatedAzimuth,
                isAligned = isAligned,
                alignmentColor = alignmentColor,
                glassTheme = glassTheme,
                onCalibrationClick = { showCalibrationDialog = true },
                onStatusClick = { showHealthOverlay = true }
            )
        }

        if (showCalibrationDialog) {
            CalibrationDialog(onDismiss = { showCalibrationDialog = false })
        }

        if (showHealthOverlay) {
            SystemHealthOverlay(
                onDismiss = { showHealthOverlay = false }
            )
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
    glassTheme: com.ybugmobile.vaktiva.ui.theme.GlassTheme,
    onCalibrationClick: () -> Unit,
    onStatusClick: () -> Unit
) {
    var isMapView by rememberSaveable { mutableStateOf(false) }
    var isSatelliteView by rememberSaveable { mutableStateOf(false) }
    val kaabaLatLng = LatLng(21.4225, 39.8262)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val theme = MaterialTheme.colorScheme

    val effectiveContentColor = when {
        isMapView -> theme.onSurface
        else -> glassTheme.contentColor
    }

    val statusIcon = if (!state.isNetworkAvailable || state.hasSystemIssues) {
        @Composable {
            val color = if (!state.isNetworkAvailable) Color(0xFFFACC15) else Color(0xFFFF5252)
            Surface(
                onClick = onStatusClick,
                shape = CircleShape,
                color = if (!isMapView) color.copy(alpha = 0.15f) else theme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = if (isMapView) 0.5f else 0.4f)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (!state.isNetworkAvailable) Icons.Rounded.WifiOff else Icons.Rounded.PriorityHigh,
                        contentDescription = "Status",
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    } else null

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMapView && !isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                QiblaMap(
                    settings = state.settings,
                    compassData = state.compassData,
                    isSatelliteView = isSatelliteView,
                    isAligned = isAligned,
                    kaabaLatLng = kaabaLatLng,
                    onMapReady = { },
                    onMapLongClick = { },
                    onToggleSatellite = { isSatelliteView = !isSatelliteView }
                )
                
                if (!state.isNetworkAvailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.padding(32.dp),
                            tonalElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Rounded.WifiOff,
                                    null,
                                    tint = Color(0xFFFACC15),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.health_no_internet),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = onStatusClick,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.health_title))
                                }
                            }
                        }
                    }
                }
            }
        } else if (isAccuracyLow && !isLandscape) {
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

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding()) {
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (isMapView) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            QiblaMap(
                                settings = state.settings,
                                compassData = state.compassData,
                                isSatelliteView = isSatelliteView,
                                isAligned = isAligned,
                                kaabaLatLng = kaabaLatLng,
                                onMapReady = { },
                                onMapLongClick = { },
                                onToggleSatellite = { isSatelliteView = !isSatelliteView }
                            )
                            
                            if (!state.isNetworkAvailable) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.padding(32.dp),
                                        tonalElevation = 8.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Rounded.WifiOff, null, tint = Color(0xFFFACC15), modifier = Modifier.size(40.dp))
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                text = stringResource(R.string.health_no_internet),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            if (isAccuracyLow) {
                                Box(
                                    modifier = Modifier
                                        .size(300.dp)
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
                            
                            Box(
                                modifier = Modifier
                                    .size(280.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                glassTheme.containerColor.copy(alpha = 0.4f),
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
                                contentColor = glassTheme.contentColor
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isMapView) theme.surface else Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.settings?.let { s ->
                            Surface(
                                color = if (isMapView) theme.surface else Color.Transparent,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.padding(end = 8.dp),
                                border = if (isMapView) androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    effectiveContentColor.copy(alpha = 0.1f)
                                ) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = if (isMapView) 12.dp else 0.dp,
                                        vertical = if (isMapView) 4.dp else 0.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LocationSection(
                                        locationName = s.locationName,
                                        contentColor = effectiveContentColor,
                                        isNetworkAvailable = state.isNetworkAvailable
                                    )
                                    if (statusIcon != null) {
                                        Spacer(Modifier.width(12.dp))
                                        statusIcon.invoke()
                                    }
                                }
                            }
                        }

                        QiblaViewSwitcher(
                            isMapView = isMapView,
                            onViewChange = { isMapView = it },
                            contentColor = effectiveContentColor,
                            containerColor = if (isMapView) theme.surface else glassTheme.containerColor,
                            borderColor = if (isMapView) effectiveContentColor.copy(alpha = 0.1f) else glassTheme.borderColor
                        )
                    }

                    QiblaInfoCard(
                        isAligned = isAligned,
                        alignmentColor = alignmentColor,
                        qiblaDirection = state.qiblaDirection,
                        compassData = state.compassData,
                        isAccuracyLow = isAccuracyLow,
                        isAccuracyUnreliable = isAccuracyUnreliable,
                        onCalibrationClick = onCalibrationClick,
                        isMapView = isMapView,
                        isSatelliteView = isSatelliteView,
                        containerColor = if (isMapView) theme.surface else glassTheme.containerColor,
                        contentColor = effectiveContentColor
                    )
                    
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        } else {
            // Portrait Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.settings?.let { s ->
                    Surface(
                        color = if (isMapView) theme.surface else Color.Transparent,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.padding(end = 12.dp),
                        shadowElevation = if (isMapView) 4.dp else 0.dp,
                        tonalElevation = if (isMapView) 2.dp else 0.dp,
                        border = if (isMapView) androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            effectiveContentColor.copy(alpha = 0.1f)
                        ) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (isMapView) 6.dp else 6.dp,
                                vertical = if (isMapView) 8.dp else 16.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LocationSection(
                                locationName = s.locationName,
                                contentColor = effectiveContentColor,
                                isNetworkAvailable = state.isNetworkAvailable
                            )
                            if (statusIcon != null) {
                                Spacer(Modifier.width(12.dp))
                                statusIcon.invoke()
                            }
                        }
                    }
                }

                QiblaViewSwitcher(
                    isMapView = isMapView,
                    onViewChange = { isMapView = it },
                    contentColor = effectiveContentColor,
                    containerColor = if (isMapView) theme.surface else glassTheme.containerColor,
                    borderColor = if (isMapView) effectiveContentColor.copy(alpha = 0.1f) else glassTheme.borderColor
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))

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
                                                glassTheme.containerColor.copy(alpha = 0.4f),
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
                                contentColor = glassTheme.contentColor
                            )
                        }
                    }
                }

                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    QiblaInfoCard(
                        isAligned = isAligned,
                        alignmentColor = alignmentColor,
                        qiblaDirection = state.qiblaDirection,
                        compassData = state.compassData,
                        isAccuracyLow = isAccuracyLow,
                        isAccuracyUnreliable = isAccuracyUnreliable,
                        onCalibrationClick = onCalibrationClick,
                        isMapView = isMapView,
                        isSatelliteView = isSatelliteView,
                        containerColor = if (isMapView) theme.surface else glassTheme.containerColor,
                        contentColor = effectiveContentColor
                    )
                }
                
                Spacer(modifier = Modifier.height(150.dp))
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocationRequiredFallback(permissionState: PermissionState) {
    val context = LocalContext.current
    var denialCount by rememberSaveable { mutableIntStateOf(0) }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.qibla_location_required),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.qibla_location_required_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (denialCount >= 2 || (permissionState.status as? PermissionStatus.Denied)?.shouldShowRationale == true) {
                        openAppSettings()
                    } else {
                        permissionState.launchPermissionRequest()
                        denialCount++
                    }
                },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.qibla_grant_permission))
            }
        }
    }
}
