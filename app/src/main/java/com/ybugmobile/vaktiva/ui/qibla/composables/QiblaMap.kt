package com.ybugmobile.vaktiva.ui.qibla.composables

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Qibla
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.data.sensor.CompassData
import com.ybugmobile.vaktiva.ui.qibla.MapConstants
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.utils.ColorUtils
import kotlin.math.abs

@Composable
fun QiblaMap(
    settings: UserSettings?,
    compassData: CompassData,
    isSatelliteView: Boolean,
    isAligned: Boolean,
    kaabaLatLng: LatLng,
    onMapReady: (MapLibreMap) -> Unit,
    onMapLongClick: (LatLng) -> Unit,
    onToggleSatellite: () -> Unit,
    showFabs: Boolean = true,
    fabAlignment: Alignment = Alignment.CenterEnd,
    fabPadding: PaddingValues = PaddingValues(16.dp),
    isHorizontalFabs: Boolean = false
) {
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var lineManager by remember { mutableStateOf<LineManager?>(null) }
    var customPoint by remember { mutableStateOf<LatLng?>(null) }
    
    var isMapOriented by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "linePulse")
    val linePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "linePulseAlpha"
    )

    LaunchedEffect(isAligned) {
        if (isAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            mapInstance?.let { map ->
                if (map.cameraPosition.tilt < 40.0) {
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(map.cameraPosition)
                                .tilt(50.0)
                                .build()
                        ), 1000
                    )
                }
            }
        } else {
            mapInstance?.let { map ->
                if (map.cameraPosition.tilt > 10.0) {
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(map.cameraPosition)
                                .tilt(0.0)
                                .build()
                        ), 1000
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    getMapAsync { map ->
                        mapInstance = map
                        onMapReady(map)
                        map.uiSettings.isCompassEnabled = false
                        
                        map.addOnCameraMoveListener {
                            val cameraPosition = map.cameraPosition
                            isMapOriented = abs(cameraPosition.bearing) > 1.0 || abs(cameraPosition.tilt) > 1.0
                        }

                        val initialStyle = if (isSatelliteView) MapConstants.SATELLITE_STYLE_JSON else MapConstants.STREET_STYLE
                        map.setStyle(Style.Builder().run {
                            if (isSatelliteView) fromJson(initialStyle) else fromUri(initialStyle)
                        }) { style ->
                            style.addImage(MapConstants.USER_ARROW_ID, createAppleStyleMarker("#007AFF"))
                            style.addImage(MapConstants.CUSTOM_ARROW_ID, createAppleStyleMarker("#AF52DE"))
                            style.addImage("green_arrow", createAppleStyleMarker("#4CD964"))
                            style.addImage("kaaba_marker", createKaabaMarker("#FFD700"))

                            lineManager = LineManager(this@apply, map, style)
                            symbolManager = SymbolManager(this@apply, map, style).apply {
                                iconAllowOverlap = true
                                iconIgnorePlacement = true
                            }
                            
                            settings?.let {
                                val lat = it.latitude
                                val lng = it.longitude
                                if (lat != null && lng != null) {
                                    map.moveCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(lat, lng),
                                            MapConstants.DEFAULT_ZOOM
                                        )
                                    )
                                }
                            }
                        }
                        map.addOnMapLongClickListener { point ->
                            customPoint = point
                            onMapLongClick(point)
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
                        currentStyle == null || currentStyle.uri != MapConstants.STREET_STYLE
                    }
                    if (needsStyleChange) {
                        symbolManager?.deleteAll()
                        lineManager?.deleteAll()
                        symbolManager = null
                        lineManager = null
                        map.setStyle(Style.Builder().run {
                            if (isSatelliteView) fromJson(MapConstants.SATELLITE_STYLE_JSON) else fromUri(MapConstants.STREET_STYLE)
                        }) { style ->
                            style.addImage(MapConstants.USER_ARROW_ID, createAppleStyleMarker("#007AFF"))
                            style.addImage(MapConstants.CUSTOM_ARROW_ID, createAppleStyleMarker("#AF52DE"))
                            style.addImage("green_arrow", createAppleStyleMarker("#4CD964"))
                            style.addImage("kaaba_marker", createKaabaMarker("#FFD700"))
                            
                            lineManager = LineManager(view, map, style)
                            symbolManager = SymbolManager(view, map, style).apply {
                                iconAllowOverlap = true
                                iconIgnorePlacement = true
                            }
                        }
                    }
                }
            }
        )

        if (showFabs) {
            val fabContent = @Composable {
                AnimatedVisibility(
                    visible = isMapOriented,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            mapInstance?.let { map ->
                                val currentPos = map.cameraPosition
                                val newPos = CameraPosition.Builder(currentPos)
                                    .bearing(0.0)
                                    .tilt(0.0)
                                    .build()
                                map.animateCamera(CameraUpdateFactory.newCameraPosition(newPos))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Reset Orientation",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                FloatingActionButton(
                    onClick = {
                        settings?.let { loc ->
                            val lat = loc.latitude
                            val lng = loc.longitude
                            if (lat != null && lng != null) {
                                mapInstance?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(lat, lng),
                                        MapConstants.DEFAULT_ZOOM
                                    )
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Locate Me",
                        modifier = Modifier.size(24.dp)
                    )
                }

                FloatingActionButton(
                    onClick = onToggleSatellite,
                    containerColor = if (isSatelliteView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSatelliteView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isSatelliteView) Icons.Default.Map else Icons.Default.Satellite,
                        contentDescription = "Toggle Satellite",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (isHorizontalFabs) {
                Row(
                    modifier = Modifier
                        .align(fabAlignment)
                        .padding(fabPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fabContent()
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(fabAlignment)
                        .padding(fabPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    fabContent()
                }
            }
        }
    }

    LaunchedEffect(settings, compassData.azimuth, customPoint, symbolManager, lineManager, linePulseAlpha) {
        val sm = symbolManager ?: return@LaunchedEffect
        val lm = lineManager ?: return@LaunchedEffect
        sm.deleteAll()
        lm.deleteAll()
        
        val appleBlue = "#007AFF"
        val appleGreen = "#4CD964"
        val customPurple = "#AF52DE"
        
        sm.create(
            SymbolOptions().withLatLng(kaabaLatLng)
                .withIconImage("kaaba_marker")
                .withIconSize(1.3f)
        )
        
        settings?.let { loc ->
            val lat = loc.latitude
            val lng = loc.longitude
            if (lat != null && lng != null) {
                val userLatLng = LatLng(lat, lng)
                val qiblaDir = Qibla(Coordinates(lat, lng)).direction
                val isUserAligned = abs(compassData.azimuth - qiblaDir) < 2.0
                
                val activeColor = if (isUserAligned) appleGreen else appleBlue
                val activeIcon = if (isUserAligned) "green_arrow" else MapConstants.USER_ARROW_ID
                
                lm.create(
                    LineOptions().withLatLngs(listOf(userLatLng, kaabaLatLng))
                        .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(activeColor)))
                        .withLineWidth(18f)
                        .withLineOpacity(linePulseAlpha * 0.4f)
                        .withLineBlur(5f)
                )

                lm.create(
                    LineOptions().withLatLngs(listOf(userLatLng, kaabaLatLng))
                        .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                        .withLineWidth(10f)
                        .withLineJoin("round")
                )
                lm.create(
                    LineOptions().withLatLngs(listOf(userLatLng, kaabaLatLng))
                        .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(activeColor)))
                        .withLineWidth(6f)
                        .withLineJoin("round")
                )
                
                sm.create(
                    SymbolOptions().withLatLng(userLatLng).withIconImage(activeIcon)
                        .withIconRotate(compassData.azimuth).withIconSize(1.2f)
                )
            }
        }
        
        customPoint?.let { cp ->
            val qiblaDir = Qibla(Coordinates(cp.latitude, cp.longitude)).direction
            val isCustomAligned = abs(compassData.azimuth - qiblaDir) < 2.0
            
            val activeColor = if (isCustomAligned) appleGreen else customPurple
            val activeIcon = if (isCustomAligned) "green_arrow" else MapConstants.CUSTOM_ARROW_ID
            
            lm.create(
                LineOptions().withLatLngs(listOf(cp, kaabaLatLng))
                    .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(activeColor)))
                    .withLineWidth(18f)
                    .withLineOpacity(linePulseAlpha * 0.4f)
                    .withLineBlur(5f)
            )

            lm.create(
                LineOptions().withLatLngs(listOf(cp, kaabaLatLng))
                    .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                    .withLineWidth(10f)
                    .withLineJoin("round")
            )
            lm.create(
                LineOptions().withLatLngs(listOf(cp, kaabaLatLng))
                    .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(activeColor)))
                    .withLineWidth(6f)
                    .withLineJoin("round")
            )
            sm.create(
                SymbolOptions().withLatLng(cp).withIconImage(activeIcon)
                    .withIconRotate(compassData.azimuth).withIconSize(1.2f)
            )
        }
    }
}

private fun createAppleStyleMarker(colorHex: String): Bitmap {
    val size = 160
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    
    val markerColor = AndroidColor.parseColor(colorHex)
    
    val shadowPaint = Paint().apply {
        isAntiAlias = true
        this.color = AndroidColor.TRANSPARENT
        setShadowLayer(16f, 0f, 8f, AndroidColor.argb(90, 0, 0, 0))
    }
    canvas.drawCircle(center, center, 44f, shadowPaint)
    
    val pulsePaint = Paint().apply {
        this.color = markerColor
        alpha = 30
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(center, center, 65f, pulsePaint)
    
    val whitePaint = Paint().apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(center, center, 44f, whitePaint)
    
    val mainPaint = Paint().apply {
        this.color = markerColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(center, center, 40f, mainPaint)
    
    val arrowPaint = Paint().apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val path = Path().apply {
        moveTo(center, center - 26f)
        lineTo(center + 18f, center + 20f)
        lineTo(center, center + 12f)
        lineTo(center - 18f, center + 20f)
        close()
    }
    canvas.drawPath(path, arrowPaint)

    return bitmap
}

fun createKaabaMarker(colorHex: String): Bitmap {
    val size = 160
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    
    val markerColor = AndroidColor.parseColor(colorHex)
    
    val shadowPaint = Paint().apply {
        isAntiAlias = true
        this.color = AndroidColor.TRANSPARENT
        setShadowLayer(16f, 0f, 8f, AndroidColor.argb(90, 0, 0, 0))
    }
    canvas.drawCircle(center, center, 44f, shadowPaint)
    
    val whitePaint = Paint().apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(center, center, 44f, whitePaint)
    
    val mainPaint = Paint().apply {
        this.color = markerColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(center, center, 40f, mainPaint)
    
    val kaabaPaint = Paint().apply {
        this.color = AndroidColor.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    val kaabaSize = 34f
    canvas.drawRect(
        center - kaabaSize / 2, 
        center - kaabaSize / 2, 
        center + kaabaSize / 2, 
        center + kaabaSize / 2, 
        kaabaPaint
    )
    
    val goldPaint = Paint().apply {
        this.color = AndroidColor.parseColor("#FFD700")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawRect(
        center - kaabaSize / 2, 
        center - kaabaSize / 4, 
        center + kaabaSize / 2, 
        center - kaabaSize / 8, 
        goldPaint
    )

    return bitmap
}
