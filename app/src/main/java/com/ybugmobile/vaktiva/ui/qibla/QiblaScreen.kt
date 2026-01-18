package com.ybugmobile.vaktiva.ui.qibla

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.data.sensor.CompassData
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

// Custom Satellite source to ensure it works
private val SATELLITE_SOURCE = XYTileSource(
    "USGS-Imagery", 0, 18, 256, "",
    arrayOf("https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/"),
    "USGS"
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(
    viewModel: QiblaViewModel = hiltViewModel()
) {
    val userLocation by viewModel.userLocation.collectAsState(initial = null)
    val qiblaDirection by viewModel.qiblaDirection.collectAsState(initial = 0.0)
    val compassData by viewModel.compassData.collectAsState(initial = CompassData(0f, SensorManager.SENSOR_STATUS_ACCURACY_LOW))
    
    var isMapView by remember { mutableStateOf(false) }
    var isSatelliteView by remember { mutableStateOf(false) }
    val kaabaGeoPoint = GeoPoint(21.4225, 39.8262)

    var customPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var showCalibrationDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    
    // Cache package name and icons
    val pkgName = remember { context.packageName }
    val userArrowIcon = remember { createArrowIcon(context, "#2196F3") }
    val customArrowIcon = remember { createArrowIcon(context, "#F44336") }

    LaunchedEffect(pkgName) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = pkgName
    }

    LaunchedEffect(compassData.accuracy) {
        showCalibrationDialog = compassData.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (locationPermissionState.status.isGranted) {
            if (isMapView) {
                var mapViewRef by remember { mutableStateOf<MapView?>(null) }
                
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setMultiTouchControls(true)
                            setBuiltInZoomControls(false)
                            minZoomLevel = 3.0
                            maxZoomLevel = 20.0
                            
                            // Initialize map center if location is already known
                            userLocation?.let {
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(it.latitude, it.longitude))
                            }

                            // Add persistent overlays once
                            val qLine = Polyline().apply {
                                color = AndroidColor.parseColor("#FFD700")
                                width = 8f
                                title = "qibla_line"
                            }
                            val cLine = Polyline().apply {
                                color = AndroidColor.parseColor("#F44336")
                                width = 6f
                                title = "custom_line"
                            }
                            val uMarker = Marker(this).apply {
                                icon = userArrowIcon
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "user_marker"
                            }
                            val cMarker = Marker(this).apply {
                                icon = customArrowIcon
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "custom_marker"
                            }
                            
                            overlays.add(qLine)
                            overlays.add(cLine)
                            overlays.add(uMarker)
                            overlays.add(cMarker)

                            val eventsReceiver = object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                                override fun longPressHelper(p: GeoPoint?): Boolean {
                                    p?.let { customPoint = it }
                                    return true
                                }
                            }
                            overlays.add(MapEventsOverlay(eventsReceiver))
                            
                            mapViewRef = this
                        }
                    },
                    update = { map ->
                        // 1. Tile Source Update (only on change)
                        val targetSource = if (isSatelliteView) SATELLITE_SOURCE else TileSourceFactory.MAPNIK
                        if (map.tileProvider.tileSource.name() != targetSource.name()) {
                            map.setTileSource(targetSource)
                        }

                        // 2. Find and update existing overlays (efficiently)
                        var userMarker: Marker? = null
                        var customMarker: Marker? = null
                        var qiblaLine: Polyline? = null
                        var customLine: Polyline? = null
                        
                        for (overlay in map.overlays) {
                            when (overlay) {
                                is Marker -> {
                                    if (overlay.title == "user_marker") userMarker = overlay
                                    else if (overlay.title == "custom_marker") customMarker = overlay
                                }
                                is Polyline -> {
                                    if (overlay.title == "qibla_line") qiblaLine = overlay
                                    else if (overlay.title == "custom_line") customLine = overlay
                                }
                            }
                        }

                        // 3. Apply changes from State
                        userLocation?.let { loc ->
                            val userPoint = GeoPoint(loc.latitude, loc.longitude)
                            
                            // Center if never centered
                            if (map.zoomLevelDouble < 4.0) {
                                map.controller.setZoom(15.0)
                                map.controller.setCenter(userPoint)
                            }

                            userMarker?.apply {
                                position = userPoint
                                rotation = -compassData.azimuth
                            }
                            qiblaLine?.apply {
                                setPoints(listOf(userPoint, kaabaGeoPoint))
                            }
                        }

                        customPoint?.let { cp ->
                            customMarker?.apply {
                                position = cp
                                rotation = -compassData.azimuth
                                isEnabled = true // Marker doesn't have isVisible in some versions, use isEnabled or Alpha
                                alpha = 1.0f
                            }
                            customLine?.apply {
                                setPoints(listOf(cp, kaabaGeoPoint))
                                isEnabled = true
                            }
                        } ?: run {
                            customMarker?.alpha = 0f
                            customLine?.isEnabled = false
                        }
                        
                        // 4. Manual invalidate to ensure custom point and rotation appear immediately
                        map.postInvalidate()
                    },
                    onRelease = { map ->
                        map.onDetach()
                    }
                )

                // Map Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 180.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { isSatelliteView = !isSatelliteView },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(if (isSatelliteView) Icons.Default.Terrain else Icons.Default.Satellite, contentDescription = "Toggle Layer")
                    }
                    
                    FloatingActionButton(
                        onClick = {
                            userLocation?.let { loc ->
                                mapViewRef?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                mapViewRef?.controller?.setZoom(18.0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                    }
                }
            }

            // Compass Needle Rotation Logic
            var needleRotation = (qiblaDirection.toFloat() - compassData.azimuth)
            while (needleRotation <= -180) needleRotation += 360
            while (needleRotation > 180) needleRotation -= 360

            val isAligned = abs(needleRotation) < 3f
            val alignmentColor by animateColorAsState(
                targetValue = if (isAligned) Color(0xFF4CAF50) else Color(0xFFFFD700),
                label = "alignmentColor"
            )

            if (!isMapView) {
                Box(
                    modifier = Modifier.align(Alignment.Center).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val compassSize = 320f
                    Box(modifier = Modifier.size(compassSize.dp), contentAlignment = Alignment.Center) {
                        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                            rotate(-compassData.azimuth) {
                                drawCircle(
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    radius = size.minDimension / 2,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                )
                                drawLine(
                                    color = Color.Red.copy(alpha = 0.6f),
                                    start = center.copy(y = center.y - size.minDimension / 2 + 10),
                                    end = center.copy(y = center.y - size.minDimension / 2 + 30),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        ComposeCanvas(modifier = Modifier.size((compassSize * 0.8f).dp)) {
                            rotate(needleRotation) {
                                val path = Path().apply {
                                    moveTo(center.x, center.y - size.minDimension / 2)
                                    lineTo(center.x + 15.dp.toPx(), center.y)
                                    lineTo(center.x, center.y - 10.dp.toPx())
                                    lineTo(center.x - 15.dp.toPx(), center.y)
                                    close()
                                }
                                drawPath(path, color = alignmentColor)
                                
                                val tailPath = Path().apply {
                                    moveTo(center.x, center.y + 10.dp.toPx())
                                    lineTo(center.x + 8.dp.toPx(), center.y)
                                    lineTo(center.x, center.y - 10.dp.toPx())
                                    lineTo(center.x - 8.dp.toPx(), center.y)
                                    close()
                                }
                                drawPath(tailPath, color = alignmentColor.copy(alpha = 0.5f))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isAligned,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 140.dp)
                    ) {
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                text = "QIBLA ALIGNED",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Top Header and Toggle
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(32.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        FilterChip(
                            selected = !isMapView,
                            onClick = { isMapView = false },
                            label = { Text("Compass") },
                            leadingIcon = { Icon(Icons.Default.Explore, null) },
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = isMapView,
                            onClick = { isMapView = true },
                            label = { Text("Map") },
                            leadingIcon = { Icon(Icons.Default.Map, null) },
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isMapView) "Map View" else "Compass View",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAligned) "Aligned with Kaaba" else "Rotate phone to find Qibla",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAligned) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "Location Permission Required", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { locationPermissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
        
        // Info Box at Bottom
        if (locationPermissionState.status.isGranted) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
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
                    
                    if (compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Low accuracy. Calibrate compass.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (showCalibrationDialog) {
            Dialog(onDismissRequest = { showCalibrationDialog = false }) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Compass Calibration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Move your phone in a figure-8 pattern (∞) to calibrate.", textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { showCalibrationDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Got it") }
                    }
                }
            }
        }
    }
}

private fun createArrowIcon(context: android.content.Context, colorHex: String): BitmapDrawable {
    val arrowBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(arrowBitmap)
    val paint = Paint().apply {
        color = AndroidColor.parseColor(colorHex)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val path = android.graphics.Path().apply {
        moveTo(50f, 10f)
        lineTo(90f, 90f)
        lineTo(50f, 70f)
        lineTo(10f, 90f)
        close()
    }
    canvas.drawPath(path, paint)
    return BitmapDrawable(context.resources, arrowBitmap)
}
