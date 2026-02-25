package com.ybugmobile.vaktiva.ui.qibla

import android.content.res.Configuration
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.home.composables.LocationSection
import com.ybugmobile.vaktiva.ui.qibla.composables.*
import com.ybugmobile.vaktiva.ui.settings.composables.SystemHealthEmptyState
import com.ybugmobile.vaktiva.ui.settings.composables.SystemHealthOverlay
import com.ybugmobile.vaktiva.ui.theme.GlassTheme
import com.ybugmobile.vaktiva.ui.theme.getGlassTheme
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(
    viewModel: QiblaViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    LaunchedEffect(state.isNetworkAvailable, state.hasSystemIssues, state.currentPrayerDay) {
        if (state.currentPrayerDay == null && state.isNetworkAvailable && !state.hasSystemIssues) {
            viewModel.refresh()
        }
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
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isAligned by remember {
        derivedStateOf {
            var relativeQiblaAngle = (state.qiblaDirection.toFloat() - state.compassData.azimuth)
            while (relativeQiblaAngle <= -180) relativeQiblaAngle += 360
            while (relativeQiblaAngle > 180) relativeQiblaAngle -= 360
            abs(relativeQiblaAngle) < 3f
        }
    }

    val alignmentColor by animateColorAsState(
        targetValue = if (isAligned) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
        label = "alignmentColor"
    )

    val contentColor = glassTheme.contentColor

    Box(modifier = Modifier.fillMaxSize().background(brush = backgroundGradient)) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = contentColor)
            }
        } else if (state.currentPrayerDay == null && (!state.isNetworkAvailable || state.hasSystemIssues)) {
            SystemHealthEmptyState(
                isRefreshing = isRefreshing,
                hasPrayerData = false,
                contentColor = contentColor,
                glassTheme = glassTheme,
                onStatusClick = { showHealthOverlay = true }
            )
        } else {
            QiblaContent(
                state = state,
                isAccuracyLow = isAccuracyLow,
                isAccuracyUnreliable = isAccuracyUnreliable,
                pulseAlpha = pulseAlpha,
                currentAzimuth = state.compassData.azimuth,
                isAligned = isAligned,
                alignmentColor = alignmentColor,
                glassTheme = glassTheme,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                onCalibrationClick = { showCalibrationDialog = true },
                onStatusClick = { showHealthOverlay = true }
            )
        }

        if (showCalibrationDialog) {
            CalibrationDialog(onDismiss = { showCalibrationDialog = false })
        }

        if (showHealthOverlay) {
            SystemHealthOverlay(
                onDismiss = { 
                    showHealthOverlay = false 
                    viewModel.refresh()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QiblaContent(
    state: QiblaViewState,
    isAccuracyLow: Boolean,
    isAccuracyUnreliable: Boolean,
    pulseAlpha: Float,
    currentAzimuth: Float,
    isAligned: Boolean,
    alignmentColor: Color,
    glassTheme: GlassTheme,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCalibrationClick: () -> Unit,
    onStatusClick: () -> Unit
) {
    var isMapView by rememberSaveable { mutableStateOf(false) }
    var isSatelliteView by rememberSaveable { mutableStateOf(false) }
    val kaabaLatLng = LatLng(21.4225, 39.8262)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val lightGlassTheme = GlassTheme(
        containerColor = Color.White.copy(alpha = 0.7f),
        contentColor = Color.Black,
        borderColor = Color.White.copy(alpha = 0.3f),
        secondaryContentColor = Color.Black.copy(alpha = 0.6f),
        isLightMode = true
    )

    val currentTheme = if (isMapView) lightGlassTheme else glassTheme

    Box(modifier = Modifier.fillMaxSize()) {
        // LAYER 1: Fullscreen Map (Common for both Portrait & Landscape)
        if (isMapView) {
            QiblaMap(
                settings = state.settings,
                compassData = state.compassData,
                isSatelliteView = isSatelliteView,
                isAligned = isAligned,
                kaabaLatLng = kaabaLatLng,
                onMapReady = { },
                onMapLongClick = { },
                onToggleSatellite = { isSatelliteView = !isSatelliteView },
                fabAlignment = Alignment.CenterEnd,
                fabPadding = if (isLandscape) PaddingValues(end = 16.dp) else PaddingValues(16.dp)
            )
            
            if (!state.isNetworkAvailable) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
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
                            Icon(Icons.Rounded.WifiOff, null, tint = Color(0xFFFACC15), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.health_no_internet), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onStatusClick, shape = RoundedCornerShape(12.dp)) {
                                Text(stringResource(R.string.health_title))
                            }
                        }
                    }
                }
            }
        }

        // LAYER 2: UI Overlays
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 80.dp)
                    .systemBarsPadding()
                    .displayCutoutPadding()
            ) {
                Box(
                    modifier = Modifier.weight(1.3f).fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (!isMapView) {
                        ProfessionalCompass(
                            azimuth = currentAzimuth,
                            qiblaAngle = state.qiblaDirection.toFloat(),
                            alignmentColor = alignmentColor,
                            isAligned = isAligned,
                            contentColor = currentTheme.contentColor
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TopHeaderRow(
                            state = state,
                            currentTheme = currentTheme,
                            isMapView = isMapView,
                            onViewChange = { isMapView = it },
                            onStatusClick = onStatusClick
                        )

                        QiblaInfoCard(
                            isAligned = isAligned,
                            alignmentColor = alignmentColor,
                            qiblaDirection = state.qiblaDirection,
                            compassData = state.compassData,
                            isAccuracyLow = isAccuracyLow,
                            isAccuracyUnreliable = isAccuracyUnreliable,
                            onCalibrationClick = onCalibrationClick,
                            containerColor = currentTheme.containerColor,
                            contentColor = currentTheme.contentColor
                        )
                    }
                }
            }
        } else {
            // Portrait UI Layer
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxWidth().systemBarsPadding()) {
                    Spacer(Modifier.height(24.dp))
                    TopHeaderRow(
                        state = state,
                        currentTheme = currentTheme,
                        isMapView = isMapView,
                        onViewChange = { isMapView = it },
                        onStatusClick = onStatusClick,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    if (!isMapView) {
                        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(32.dp))
                                CompassContainer(
                                    isAccuracyLow = isAccuracyLow,
                                    isAligned = isAligned,
                                    pulseAlpha = pulseAlpha,
                                    currentAzimuth = currentAzimuth,
                                    state = state,
                                    alignmentColor = alignmentColor,
                                    currentTheme = currentTheme
                                )
                                Spacer(Modifier.height(240.dp))
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 96.dp)
                        .navigationBarsPadding()
                ) {
                    QiblaInfoCard(
                        isAligned = isAligned,
                        alignmentColor = alignmentColor,
                        qiblaDirection = state.qiblaDirection,
                        compassData = state.compassData,
                        isAccuracyLow = isAccuracyLow,
                        isAccuracyUnreliable = isAccuracyUnreliable,
                        onCalibrationClick = onCalibrationClick,
                        containerColor = currentTheme.containerColor,
                        contentColor = currentTheme.contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHeaderRow(
    state: QiblaViewState,
    currentTheme: GlassTheme,
    isMapView: Boolean,
    onViewChange: (Boolean) -> Unit,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (isMapView) {
            Surface(
                color = currentTheme.containerColor,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f).height(44.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.borderColor)
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    LocationSection(
                        locationName = state.settings?.locationName ?: "...",
                        contentColor = currentTheme.contentColor,
                        onStatusClick = onStatusClick,
                        isNetworkAvailable = state.isNetworkAvailable,
                        isLocationEnabled = state.isLocationEnabled,
                        isLocationPermissionGranted = state.isLocationPermissionGranted
                    )
                }
            }
        } else {
            LocationSection(
                locationName = state.settings?.locationName ?: "...",
                contentColor = currentTheme.contentColor,
                onStatusClick = onStatusClick,
                isNetworkAvailable = state.isNetworkAvailable,
                isLocationEnabled = state.isLocationEnabled,
                isLocationPermissionGranted = state.isLocationPermissionGranted,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(Modifier.width(12.dp))

        QiblaViewSwitcher(
            isMapView = isMapView,
            onViewChange = onViewChange,
            contentColor = currentTheme.contentColor,
            containerColor = currentTheme.containerColor,
            borderColor = currentTheme.borderColor
        )
    }
}

@Composable
private fun CompassContainer(
    isAccuracyLow: Boolean,
    isAligned: Boolean,
    pulseAlpha: Float,
    currentAzimuth: Float,
    state: QiblaViewState,
    alignmentColor: Color,
    currentTheme: GlassTheme
) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        if (isAccuracyLow && !isAligned) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = pulseAlpha),
                            Color.Transparent
                        )
                    )
                )
            )
        }
        ProfessionalCompass(
            azimuth = currentAzimuth,
            qiblaAngle = state.qiblaDirection.toFloat(),
            alignmentColor = alignmentColor,
            isAligned = isAligned,
            contentColor = currentTheme.contentColor
        )
    }
}
