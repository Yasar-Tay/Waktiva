package com.ybugmobile.vaktiva.ui.qibla

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.cos
import kotlin.math.sin

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

    // Smoothed Azimuth for the compass dial
    val animatedAzimuth by animateFloatAsState(
        targetValue = compassData.azimuth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "azimuth"
    )

    // Calculate rotation for alignment logic (Qibla relative to phone)
    var relativeQiblaAngle = (qiblaDirection.toFloat() - compassData.azimuth)
    while (relativeQiblaAngle <= -180) relativeQiblaAngle += 360
    while (relativeQiblaAngle > 180) relativeQiblaAngle -= 360

    val isAligned = abs(relativeQiblaAngle) < 3f
    val alignmentColor by animateColorAsState(
        targetValue = if (isAligned) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
        label = "alignmentColor"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (locationPermissionState.status.isGranted) {
            
            // 1. Main Content Layer (Map or Compass)
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
                                    style.addImage("user-arrow", createArrowBitmap("#2196F3"))
                                    style.addImage("custom-arrow", createArrowBitmap("#F44336"))
                                    symbolManager = SymbolManager(this@apply, map, style).apply {
                                        iconAllowOverlap = true
                                        iconIgnorePlacement = true
                                    }
                                    lineManager = LineManager(this@apply, map, style)
                                    userLocation?.let {
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
                        mapInstance?.let { map ->
                            val currentStyle = map.style
                            val needsStyleChange = if (isSatelliteView) {
                                currentStyle == null || currentStyle.uri.isNotEmpty()
                            } else {
                                currentStyle == null || currentStyle.uri != STREET_STYLE
                            }
                            if (needsStyleChange) {
                                symbolManager?.deleteAll()
                                lineManager?.deleteAll()
                                symbolManager = null
                                lineManager = null
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

                // Annotations update
                LaunchedEffect(userLocation, compassData.azimuth, customPoint, symbolManager, lineManager) {
                    val sm = symbolManager ?: return@LaunchedEffect
                    val lm = lineManager ?: return@LaunchedEffect
                    sm.deleteAll()
                    lm.deleteAll()
                    userLocation?.let { loc ->
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        sm.create(SymbolOptions().withLatLng(userLatLng).withIconImage("user-arrow").withIconRotate(compassData.azimuth).withIconSize(1.5f))
                        lm.create(LineOptions().withLatLngs(listOf(userLatLng, kaabaLatLng)).withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#FFD700"))).withLineWidth(4f))
                    }
                    customPoint?.let { cp ->
                        sm.create(SymbolOptions().withLatLng(cp).withIconImage("custom-arrow").withIconRotate(compassData.azimuth).withIconSize(1.5f))
                        lm.create(LineOptions().withLatLngs(listOf(cp, kaabaLatLng)).withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#F44336"))).withLineWidth(3f))
                    }
                }
            } else {
                // Compass positioned slightly higher to avoid info box
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = (-60).dp), // Offset up to avoid bottom card
                    contentAlignment = Alignment.Center
                ) {
                    ProfessionalCompass(
                        azimuth = animatedAzimuth,
                        qiblaAngle = qiblaDirection.toFloat(),
                        alignmentColor = alignmentColor,
                        isAligned = isAligned
                    )
                }
            }

            // 2. UI Overlays (Top and Bottom)
            
            // TOP: Switcher
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(32.dp),
                    tonalElevation = 8.dp
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
            }

            // BOTTOM: Enhanced Info Card (More compact)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Title and Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isAligned) "Mecca Aligned" else "Rotate Phone",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = alignmentColor
                                )
                                Text(
                                    text = userLocation?.let { "Qibla: ${qiblaDirection.toInt()}°" } ?: "Locating...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Minimal status indicator
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(alignmentColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAligned) Icons.Default.CheckCircle else Icons.Default.NearMe,
                                    contentDescription = null,
                                    tint = alignmentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Metric Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CompactMetric(label = "Qibla", value = "${qiblaDirection.toInt()}°", icon = Icons.Default.Place)
                            VerticalDivider(modifier = Modifier.height(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            CompactMetric(label = "Heading", value = "${(compassData.azimuth.toInt() + 360) % 360}°", icon = Icons.Default.Explore)
                            VerticalDivider(modifier = Modifier.height(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            CompactMetric(
                                label = "Signal",
                                value = getAccuracyLabel(compassData.accuracy),
                                icon = Icons.Default.Wifi,
                                color = if (compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Map Specific Controls (Optional visibility)
                        if (isMapView) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { isSatelliteView = !isSatelliteView },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                ) {
                                    Icon(if (isSatelliteView) Icons.Default.Map else Icons.Default.Satellite, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isSatelliteView) "Standard" else "Satellite", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        userLocation?.let { loc ->
                                            mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 18.0))
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Recenter", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Permission Request UI
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Location Access Required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(text = "Required for accurate Qibla calculation.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { locationPermissionState.launchPermissionRequest() }, shape = RoundedCornerShape(16.dp)) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        if (showCalibrationDialog) {
            CalibrationDialog(onDismiss = { showCalibrationDialog = false })
        }
    }
}

@Composable
fun CompactMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ProfessionalCompass(
    azimuth: Float,
    qiblaAngle: Float,
    alignmentColor: Color,
    isAligned: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2
            val innerRadius = radius * 0.9f

            // 1. Draw Outer Ring
            drawCircle(
                color = onSurface.copy(alpha = 0.05f),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            drawCircle(
                color = onSurface.copy(alpha = 0.15f),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            // 2. Rotate everything relative to North (The Compass Dial)
            rotate(-azimuth) {
                // Degree Ticks
                for (i in 0 until 360 step 5) {
                    val angleInRad = Math.toRadians(i.toDouble() - 90)
                    val tickLength = if (i % 30 == 0) 15.dp.toPx() else 8.dp.toPx()
                    val startX = center.x + (radius - 5.dp.toPx()) * cos(angleInRad).toFloat()
                    val startY = center.y + (radius - 5.dp.toPx()) * sin(angleInRad).toFloat()
                    val endX = center.x + (radius - 5.dp.toPx() - tickLength) * cos(angleInRad).toFloat()
                    val endY = center.y + (radius - 5.dp.toPx() - tickLength) * sin(angleInRad).toFloat()

                    drawLine(
                        color = if (i % 30 == 0) onSurface.copy(alpha = 0.4f) else onSurface.copy(alpha = 0.15f),
                        start = androidx.compose.ui.geometry.Offset(startX, startY),
                        end = androidx.compose.ui.geometry.Offset(endX, endY),
                        strokeWidth = if (i % 30 == 0) 2.dp.toPx() else 1.dp.toPx()
                    )
                }

                // Direction Labels (N, E, S, W)
                val directions = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                directions.forEach { (label, angle) ->
                    val angleInRad = Math.toRadians(angle.toDouble() - 90)
                    val x = center.x + (innerRadius - 20.dp.toPx()) * cos(angleInRad).toFloat()
                    val y = center.y + (innerRadius - 20.dp.toPx()) * sin(angleInRad).toFloat()

                    rotate(azimuth, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                        val textLayout = textMeasurer.measure(
                            text = label,
                            style = TextStyle(
                                color = if (label == "N") Color.Red else onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                x - textLayout.size.width / 2,
                                y - textLayout.size.height / 2
                            )
                        )
                    }
                }

                // Kaaba Marker on the outer circle (Moves with the dial)
                val qiblaInRad = Math.toRadians(qiblaAngle.toDouble() - 90)
                val kX = center.x + radius * cos(qiblaInRad).toFloat()
                val kY = center.y + radius * sin(qiblaInRad).toFloat()
                
                drawCircle(
                    color = Color(0xFFFFD700), // Gold
                    radius = 8.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(kX, kY)
                )
                drawCircle(
                    color = onSurface,
                    radius = 8.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(kX, kY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }

            // 3. Central Needle - Fixed towards phone heading
            val needlePath = Path().apply {
                moveTo(center.x, center.y - radius * 0.75f)
                lineTo(center.x + 12.dp.toPx(), center.y)
                lineTo(center.x, center.y + 20.dp.toPx())
                lineTo(center.x - 12.dp.toPx(), center.y)
                close()
            }
            drawPath(path = needlePath, color = alignmentColor)
            
            val shadowPath = Path().apply {
                moveTo(center.x, center.y - radius * 0.75f)
                lineTo(center.x + 12.dp.toPx(), center.y)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(path = shadowPath, color = Color.Black.copy(alpha = 0.1f))

            // Center Point
            drawCircle(color = onSurface, radius = 4.dp.toPx())
            drawCircle(color = Color.White, radius = 2.dp.toPx())
        }

        // Aligned indicator (Small and clean)
        AnimatedVisibility(visible = isAligned, modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)) {
            Surface(
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "ALIGNED",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

fun getAccuracyLabel(accuracy: Int): String {
    return when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Med"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
        else -> "Poor"
    }
}

@Composable
fun CalibrationDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Calibration Needed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Move your phone in a figure-8 pattern to improve accuracy.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Got it")
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
