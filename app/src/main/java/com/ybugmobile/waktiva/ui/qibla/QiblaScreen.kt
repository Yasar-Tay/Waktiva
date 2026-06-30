package com.ybugmobile.waktiva.ui.qibla

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.ui.home.composables.LocationSection
import com.ybugmobile.waktiva.ui.qibla.composables.*
import com.ybugmobile.waktiva.ui.settings.composables.SystemHealthEmptyState
import com.ybugmobile.waktiva.ui.settings.composables.SystemHealthOverlay
import com.ybugmobile.waktiva.ui.theme.GlassTheme
import com.ybugmobile.waktiva.ui.theme.getGlassTheme
import com.ybugmobile.waktiva.ui.theme.getGradientForTime
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(
    viewModel: QiblaViewModel = hiltViewModel()
) {
    // Using collectAsStateWithLifecycle to ensure sensors and flows are managed correctly with the lifecycle
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    // Guard against the double-fetch race: when the screen first opens, isLoading is true and
    // currentPrayerDay is null while Room is still returning cached data.  Without the isLoading
    // guard this effect would fire immediately and call refresh() in parallel with the ViewModel
    // init fetch, resulting in two simultaneous mosque network calls.
    LaunchedEffect(state.isNetworkAvailable, state.hasSystemIssues, state.currentPrayerDay, state.isLoading) {
        if (state.currentPrayerDay == null && !state.isLoading && state.isNetworkAvailable && !state.hasSystemIssues) {
            viewModel.refresh()
        }
    }

    val isAccuracyLow = state.compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    val isAccuracyUnreliable = state.compassData.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE

    var showCalibrationDialog by remember { mutableStateOf(false) }
    var showHealthOverlay by remember { mutableStateOf(false) }

    val localTime = state.currentTime.toLocalTime()
    val backgroundGradient = getGradientForTime(localTime, state.currentPrayerDay)
    val glassTheme = getGlassTheme(localTime, state.currentPrayerDay)

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
        targetValue = if (isAligned) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
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
                onStatusClick = { showHealthOverlay = true },
                onDismissMapHint = { viewModel.dismissMapHint() }
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
    onStatusClick: () -> Unit,
    onDismissMapHint: () -> Unit
) {
    var isMapView by rememberSaveable { mutableStateOf(false) }
    var isSatelliteView by rememberSaveable { mutableStateOf(true) }
    var showMapHintSession by rememberSaveable { mutableStateOf(true) }
    val kaabaLatLng = LatLng(21.4225, 39.8262)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val lightGlassTheme = GlassTheme(
        containerColor = Color.White.copy(alpha = 0.9f),
        contentColor = Color.Black,
        borderColor = Color.White.copy(alpha = 0.3f),
        secondaryContentColor = Color.Black.copy(alpha = 0.6f),
        isLightMode = true
    )

    val currentTheme = if (isMapView) lightGlassTheme else glassTheme
    val shouldShowMapHint = state.settings?.showQiblaMapHint ?: true

    Box(modifier = Modifier.fillMaxSize()) {
        // LAYER 1: Fullscreen Map — always kept in the composition tree so the MapLibre
        // GLSurfaceView (and its EGL/GPU context) is never destroyed on compass↔map toggles.
        // Visibility is controlled via Modifier.alpha; a pointerInput consumer on PointerEventPass
        // .Initial blocks all touches reaching the hidden AndroidView when not in map view.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isMapView) 1f else 0f)
                .pointerInput(isMapView) {
                    if (!isMapView) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    }
                }
        ) {
            QiblaMap(
                settings = state.settings,
                compassData = state.compassData,
                isSatelliteView = isSatelliteView,
                isAligned = isAligned,
                kaabaLatLng = kaabaLatLng,
                mosques = state.mosques,
                mosqueFetchFailed = state.mosqueFetchFailed,
                onMapReady = { },
                onMapLongClick = { showMapHintSession = false },
                onToggleSatellite = { isSatelliteView = !isSatelliteView },
                fabAlignment = if (isLandscape) Alignment.BottomCenter else Alignment.CenterEnd,
                fabPadding = if (isLandscape) PaddingValues(start = 80.dp, end = 320.dp, bottom = 32.dp) else PaddingValues(16.dp),
                isHorizontalFabs = isLandscape
            )

            if (!state.isNetworkAvailable) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(32.dp), tonalElevation = 8.dp) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
                    .padding(start = 60.dp) // Offset for navigation rail
                    .systemBarsPadding()
                    .displayCutoutPadding()
            ) {
                // LEFT HALF: Professional Compass
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(360.dp), contentAlignment = Alignment.Center) {
                        if (!isMapView) {
                            ProfessionalCompass(
                                azimuth = currentAzimuth,
                                qiblaAngle = state.qiblaDirection.toFloat(),
                                alignmentColor = alignmentColor,
                                isAligned = isAligned,
                                contentColor = currentTheme.contentColor
                            )
                            
                            QiblaAlignmentEffect(isAligned = isAligned, alignmentColor = alignmentColor)
                        }
                    }
                }

                // RIGHT HALF: Header, Info Card, and Switcher
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LocationHeader(
                            state = state,
                            currentTheme = currentTheme,
                            isMapView = isMapView,
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

                        QiblaViewSwitcher(
                            isMapView = isMapView,
                            onViewChange = { isMapView = it },
                            contentColor = currentTheme.contentColor,
                            containerColor = currentTheme.containerColor,
                            borderColor = currentTheme.borderColor
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


        AnimatedVisibility(
            visible = isMapView && shouldShowMapHint && showMapHintSession,
            enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
                .padding(top = if (isLandscape) 24.dp else 88.dp)
                .systemBarsPadding()
                .displayCutoutPadding()
        ) {
            MapInteractionHintCard(
                currentTheme = currentTheme,
                onDismiss = {
                    showMapHintSession = false
                    onDismissMapHint()
                }
            )
        }
    }
}

@Composable
private fun MapInteractionHintCard(
    currentTheme: GlassTheme,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        color = currentTheme.containerColor.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.borderColor),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.qibla_map_hint_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.contentColor
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.qibla_map_hint_body),
                style = MaterialTheme.typography.bodySmall,
                color = currentTheme.secondaryContentColor
            )
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = currentTheme.contentColor)
            ) {
                Text(
                    text = stringResource(R.string.qibla_got_it),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LocationHeader(
    state: QiblaViewState,
    currentTheme: GlassTheme,
    isMapView: Boolean,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isMapView) {
        Surface(
            color = currentTheme.containerColor,
            shape = RoundedCornerShape(22.dp),
            modifier = modifier.fillMaxWidth().height(44.dp),
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
            modifier = modifier.fillMaxWidth()
        )
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
        Box(modifier = Modifier.weight(1f)) {
            LocationHeader(state, currentTheme, isMapView, onStatusClick)
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
        ProfessionalCompass(
            azimuth = currentAzimuth,
            qiblaAngle = state.qiblaDirection.toFloat(),
            alignmentColor = alignmentColor,
            isAligned = isAligned,
            contentColor = currentTheme.contentColor
        )
        
        QiblaAlignmentEffect(isAligned = isAligned, alignmentColor = alignmentColor)
    }
}

