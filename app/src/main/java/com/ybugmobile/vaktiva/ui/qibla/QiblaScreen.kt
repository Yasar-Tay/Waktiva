package com.ybugmobile.vaktiva.ui.qibla

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.utils.ColorUtils
import kotlin.math.abs

// Free Style URLs
private const val STREET_STYLE = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
private const val SATELLITE_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "raster-tiles": {
      "type": "raster",
      "tiles": ["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256,
      "attribution": "Esri"
    }
  },
  "layers": [{"id": "simple-tiles", "type": "raster", "source": "raster-tiles", "minzoom": 0, "maxzoom": 20}]
}
"""

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
    val kaabaLatLng = LatLng(21.4225, 39.8262)

    var customPoint by remember { mutableStateOf<LatLng?>(null) }
    var showCalibrationDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Map State References
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var lineManager by remember { mutableStateOf<LineManager?>(null) }

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    LaunchedEffect(compassData.accuracy) {
        showCalibrationDialog = compassData.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (locationPermissionState.status.isGranted) {
            if (isMapView) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            getMapAsync { map ->
                                mapInstance = map
                                val initialStyle = if (isSatelliteView) SATELLITE_STYLE_JSON else STREET_STYLE
                                map.setStyle(Style.Builder().run {
                                    if (isSatelliteView) fromJson(initialStyle) else fromUri(initialStyle)
                                }) { style ->
                                    // Add images to style
                                    style.addImage("user-arrow", createArrowBitmap("#2196F3"))
                                    style.addImage("custom-arrow", createArrowBitmap("#F44336"))
                                    
                                    symbolManager = SymbolManager(this@apply, map, style).apply {
                                        iconAllowOverlap = true
                                        iconIgnorePlacement = true
                                    }
                                    lineManager = LineManager(this@apply, map, style)
                                    
                                    userLocation?.let {
                                        // Move camera to user location with maximum zoom (20.0)
                                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 18.0))
                                    }
                                }
                                
                                map.addOnMapLongClickListener { point ->
                                    customPoint = point
                                    true
                                }
                            }
                        }
                    },
                    update = { view ->
                        // Re-apply style if it changed
                        mapInstance?.let { map ->
                            val currentStyle = map.style
                            val targetStyleUri = if (isSatelliteView) "" else STREET_STYLE
                            
                            if (currentStyle == null || (isSatelliteView && currentStyle.uri.isNotEmpty()) || (!isSatelliteView && currentStyle.uri != STREET_STYLE)) {
                                map.setStyle(Style.Builder().run {
                                    if (isSatelliteView) fromJson(SATELLITE_STYLE_JSON) else fromUri(STREET_STYLE)
                                }) { style ->
                                    style.addImage("user-arrow", createArrowBitmap("#2196F3"))
                                    style.addImage("custom-arrow", createArrowBitmap("#F44336"))
                                    symbolManager = SymbolManager(view, map, style).apply {
                                        iconAllowOverlap = true
                                        iconIgnorePlacement = true
                                    }
                                    lineManager = LineManager(view, map, style)
                                }
                            }
                        }
                    }
                )

                // Update Annotations (Markers and Lines)
                LaunchedEffect(userLocation, compassData, customPoint, symbolManager, lineManager) {
                    val sm = symbolManager ?: return@LaunchedEffect
                    val lm = lineManager ?: return@LaunchedEffect
                    
                    sm.deleteAll()
                    lm.deleteAll()

                    // User Location Arrow
                    userLocation?.let { loc ->
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        sm.create(SymbolOptions()
                            .withLatLng(userLatLng)
                            .withIconImage("user-arrow")
                            .withIconRotate(-compassData.azimuth) // Points to device direction
                            .withIconSize(1.5f))
                        
                        // Line to Qibla from User
                        lm.create(LineOptions()
                            .withLatLngs(listOf(userLatLng, kaabaLatLng))
                            .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#FFD700")))
                            .withLineWidth(4f))
                    }

                    // Custom Long-Press Arrow
                    customPoint?.let { cp ->
                        sm.create(SymbolOptions()
                            .withLatLng(cp)
                            .withIconImage("custom-arrow")
                            .withIconRotate(-compassData.azimuth) // Also follows phone rotation
                            .withIconSize(1.5f))
                        
                        // Line to Qibla from Custom Point
                        lm.create(LineOptions()
                            .withLatLngs(listOf(cp, kaabaLatLng))
                            .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#F44336")))
                            .withLineWidth(3f))
                    }
                }

                // Map Controls
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 180.dp, end = 16.dp),
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
                                // Animate camera to user location with maximum zoom (20.0)
                                mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 18.0))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                    }
                }
            }

            // Compass View Logic
            var needleRotation = (qiblaDirection.toFloat() - compassData.azimuth)
            while (needleRotation <= -180) needleRotation += 360
            while (needleRotation > 180) needleRotation -= 360

            val isAligned = abs(needleRotation) < 3f
            val alignmentColor by animateColorAsState(
                targetValue = if (isAligned) Color(0xFF4CAF50) else Color(0xFFFFD700),
                label = "alignmentColor"
            )

            if (!isMapView) {
                Box(modifier = Modifier.align(Alignment.Center).fillMaxSize(), contentAlignment = Alignment.Center) {
                    val compassSize = 320f
                    Box(modifier = Modifier.size(compassSize.dp), contentAlignment = Alignment.Center) {
                        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                            rotate(-compassData.azimuth) {
                                drawCircle(color = Color.Gray.copy(alpha = 0.3f), radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                                drawLine(color = Color.Red.copy(alpha = 0.6f), start = center.copy(y = center.y - size.minDimension / 2 + 10), end = center.copy(y = center.y - size.minDimension / 2 + 30), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
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
                    AnimatedVisibility(visible = isAligned, modifier = Modifier.align(Alignment.TopCenter).padding(top = 140.dp)) {
                        Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
                            Text(text = "QIBLA ALIGNED", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Top Header and Info Box
            Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = RoundedCornerShape(32.dp), tonalElevation = 4.dp) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        FilterChip(selected = !isMapView, onClick = { isMapView = false }, label = { Text("Compass") }, leadingIcon = { Icon(Icons.Default.Explore, null) }, shape = RoundedCornerShape(24.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(selected = isMapView, onClick = { isMapView = true }, label = { Text("Map") }, leadingIcon = { Icon(Icons.Default.Map, null) }, shape = RoundedCornerShape(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = if (isMapView) "Map View" else "Compass View", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = if (isAligned) "Aligned with Kaaba" else "Rotate phone to find Qibla", style = MaterialTheme.typography.bodySmall, color = if (isAligned) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = "Location Permission Required", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { locationPermissionState.launchPermissionRequest() }) { Text("Grant Permission") }
            }
        }
        
        // Info Box at Bottom
        if (locationPermissionState.status.isGranted) {
            Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = RoundedCornerShape(24.dp), shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${qiblaDirection.toInt()}°", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
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

private fun createArrowBitmap(colorHex: String): Bitmap {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { 
        color = AndroidColor.parseColor(colorHex)
        style = Paint.Style.FILL
        isAntiAlias = true 
    }
    val path = android.graphics.Path().apply { 
        moveTo(32f, 5f)
        lineTo(55f, 55f)
        lineTo(32f, 45f)
        lineTo(9f, 55f)
        close() 
    }
    canvas.drawPath(path, paint)
    return bitmap
}
