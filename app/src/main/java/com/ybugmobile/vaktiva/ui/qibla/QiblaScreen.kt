package com.ybugmobile.vaktiva.ui.qibla

import android.Manifest
import android.content.Intent
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.data.sensor.CompassData
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QiblaScreen(
    viewModel: QiblaViewModel = hiltViewModel()
) {
    val userLocation by viewModel.userLocation.collectAsState(initial = null)
    val qiblaDirection by viewModel.qiblaDirection.collectAsState(initial = 0.0)
    val compassData by viewModel.compassData.collectAsState(initial = CompassData(0f, SensorManager.SENSOR_STATUS_ACCURACY_LOW))
    
    val kaabaGeoPoint = GeoPoint(21.4225, 39.8262)

    var showCalibrationDialog by remember { mutableStateOf(false) }

    // Update calibration dialog visibility based on sensor accuracy
    LaunchedEffect(compassData.accuracy) {
        showCalibrationDialog = compassData.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    // Manage MapView Lifecycle
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (locationPermissionState.status.isGranted) {
            // Map Background
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView },
                update = { map ->
                    userLocation?.let { loc ->
                        val userPoint = GeoPoint(loc.latitude, loc.longitude)

                        // Zoom to detail (Street level)
                        map.controller.setZoom(18.0)
                        map.controller.setCenter(userPoint)

                        map.overlays.clear()

                        // User Marker
                        val userMarker = Marker(map)
                        userMarker.position = userPoint
                        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        userMarker.title = "You"
                        map.overlays.add(userMarker)

                        // Polyline to Kaaba
                        val line = Polyline()
                        line.addPoint(userPoint)
                        line.addPoint(kaabaGeoPoint)
                        line.color = android.graphics.Color.parseColor("#FFD700")
                        line.width = 5f
                        map.overlays.add(line)

                        map.invalidate()
                    }
                }
            )

            // Professional Compass Overlay
            val needleRotation = (qiblaDirection.toFloat() - compassData.azimuth)

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                // Compass Rose Background
                Canvas(modifier = Modifier.fillMaxSize()) {
                    rotate(-compassData.azimuth) {
                        // Draw North/South/East/West markings
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.2f),
                            radius = size.minDimension / 2,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                        
                        // North Marker
                        drawLine(
                            color = Color.Red,
                            start = center,
                            end = center.copy(y = center.y - size.minDimension / 2 + 20),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Qibla Pointer (The "Needle")
                Canvas(modifier = Modifier.size(220.dp)) {
                    rotate(needleRotation) {
                        // Gold Qibla Pointer
                        val path = Path().apply {
                            moveTo(center.x, center.y - size.minDimension / 2) // Tip
                            lineTo(center.x + 20, center.y) // Right base
                            lineTo(center.x, center.y - 20) // Inner notch
                            lineTo(center.x - 20, center.y) // Left base
                            close()
                        }
                        drawPath(path, color = Color(0xFFFFD700)) // Gold
                        
                        // Shadow/Tail
                        val tailPath = Path().apply {
                            moveTo(center.x, center.y + 20)
                            lineTo(center.x + 10, center.y)
                            lineTo(center.x, center.y - 20)
                            lineTo(center.x - 10, center.y)
                            close()
                        }
                        drawPath(tailPath, color = Color(0x80FFD700))
                    }
                }
                
                // Center Pin
                Surface(
                    modifier = Modifier.size(16.dp),
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(2.dp, Color.Gray)
                ) {}
            }

            // Top Bar Overlay
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Qibla Finder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Align the gold arrow with the top",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Permission missing UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Location Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "The Qibla feature needs your location to determine the correct direction to the Kaaba.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    if (locationPermissionState.status.shouldShowRationale) {
                        locationPermissionState.launchPermissionRequest()
                    } else {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Text("Grant Permission")
                }
            }
        }
        
        // Info Box
        if (locationPermissionState.status.isGranted) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${qiblaDirection.toInt()}°",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Distance to Kaaba: 1,234 km", // Placeholder for real calc
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Low accuracy. Calibrate compass.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // Calibration Dialog
        if (showCalibrationDialog) {
            Dialog(onDismissRequest = { showCalibrationDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Compass Calibration",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Your compass accuracy is unreliable. Please move your phone in a figure-8 pattern (∞) to calibrate the sensors.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showCalibrationDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Got it")
                        }
                    }
                }
            }
        }
    }
}
