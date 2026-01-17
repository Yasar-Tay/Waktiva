package com.ybugmobile.vaktiva.ui.qibla

import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.vaktiva.data.sensor.CompassData
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun QiblaScreen(
    viewModel: QiblaViewModel = hiltViewModel()
) {
    val userLocation by viewModel.userLocation.collectAsState(initial = null)
    val qiblaDirection by viewModel.qiblaDirection.collectAsState(initial = 0.0)
    val compassData by viewModel.compassData.collectAsState(initial = CompassData(0f, SensorManager.SENSOR_STATUS_ACCURACY_LOW))
    
    val kaabaLatLng = LatLng(21.4225, 39.8262)

    var showCalibrationDialog by remember { mutableStateOf(false) }

    // Update calibration dialog visibility based on sensor accuracy
    LaunchedEffect(compassData.accuracy) {
        showCalibrationDialog = compassData.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map Background
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapLibre.getInstance(context)
                MapView(context).apply {
                    getMapAsync { map ->
                        // Use a valid MapLibre style URI instead of "OpenStreetMap"
                        map.setStyle("https://demotiles.maplibre.org/style.json") { style ->
                            userLocation?.let { loc ->
                                val userLatLng = LatLng(loc.latitude, loc.longitude)
                                map.animateCamera(CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(userLatLng)
                                        .zoom(2.0)
                                        .build()
                                ))
                                map.addPolyline(
                                    PolylineOptions()
                                        .add(userLatLng, kaabaLatLng)
                                        .color(android.graphics.Color.parseColor("#FFD700"))
                                        .width(3f)
                                )
                            }
                        }
                    }
                }
            }
        )

        // Overlay Compass Needle
        val needleRotation = (qiblaDirection.toFloat() - compassData.azimuth)

        Canvas(modifier = Modifier
            .size(200.dp)
            .align(Alignment.Center)
        ) {
            rotate(needleRotation) {
                // Needle: Points towards Qibla
                drawLine(
                    color = Color.Red,
                    start = center,
                    end = center.copy(y = center.y - 100.dp.toPx()),
                    strokeWidth = 6.dp.toPx()
                )
                drawLine(
                    color = Color.White,
                    start = center,
                    end = center.copy(y = center.y + 100.dp.toPx()),
                    strokeWidth = 6.dp.toPx()
                )
            }
        }
        
        // Info Box
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Qibla Angle: ${qiblaDirection.toInt()}°",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                if (compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Low accuracy. Please move phone in a ∞ pattern.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
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
