package com.ybugmobile.waktiva.ui.qibla.composables

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import android.location.Location
import com.google.gson.JsonObject
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer as MapSymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point as GeoPoint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Qibla
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.data.sensor.CompassData
import com.ybugmobile.waktiva.domain.model.MosqueLocation
import com.ybugmobile.waktiva.ui.qibla.MapConstants
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.utils.ColorUtils
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Path as ComposePath
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MOSQUE_SOURCE_ID = "mosque-source"
private const val MOSQUE_LAYER_ID = "mosque-layer"

private data class QiblaAnnotations(
    val userSymbol: Symbol? = null,
    val userRing: Symbol? = null,
    val customSymbol: Symbol? = null,
    val customRing: Symbol? = null
)

@Composable
fun QiblaMap(
    settings: UserSettings?,
    compassData: CompassData,
    isSatelliteView: Boolean,
    isAligned: Boolean,
    kaabaLatLng: LatLng,
    mosques: List<MosqueLocation> = emptyList(),
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
    var annotations by remember { mutableStateOf(QiblaAnnotations()) }
    val selectedMosqueState = remember { mutableStateOf<MosqueLocation?>(null) }
    var selectedMosque by selectedMosqueState
    val selectedMosqueScreenPos = remember { mutableStateOf<android.graphics.PointF?>(null) }
    var lastShownMosque by remember { mutableStateOf<MosqueLocation?>(null) }
    var lastScreenPos by remember { mutableStateOf(android.graphics.PointF(0f, 0f)) }
    val haptic = LocalHapticFeedback.current
    val ringScale = remember { Animatable(0f) }
    val density = LocalDensity.current.density

    LaunchedEffect(mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        map.addOnMapClickListener { tapLatLng ->
            val pt = map.projection.toScreenLocation(tapLatLng)
            val r = 28f * density
            val rect = RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r)
            val features = map.queryRenderedFeatures(rect, MOSQUE_LAYER_ID)
            if (features.isNotEmpty()) {
                val props = features[0].properties()
                if (props != null) {
                    val mosque = MosqueLocation(
                        id = props.get("mosqueId")?.asLong ?: 0L,
                        name = props.get("name")?.asString?.takeIf { it.isNotEmpty() },
                        lat = props.get("lat")?.asDouble ?: 0.0,
                        lng = props.get("lng")?.asDouble ?: 0.0
                    )
                    selectedMosqueState.value = mosque
                    selectedMosqueScreenPos.value = map.projection.toScreenLocation(LatLng(mosque.lat, mosque.lng))
                    return@addOnMapClickListener true
                }
            }
            selectedMosqueState.value = null
            selectedMosqueScreenPos.value = null
            false
        }
    }

    LaunchedEffect(isAligned) {
        if (isAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            ringScale.snapTo(0.5f)
            ringScale.animateTo(1.0f, tween(350, easing = FastOutSlowInEasing))
        } else {
            ringScale.animateTo(0f, tween(180))
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
                            selectedMosqueState.value?.let { m ->
                                selectedMosqueScreenPos.value = map.projection.toScreenLocation(LatLng(m.lat, m.lng))
                            }
                        }

                        val initialStyle = if (isSatelliteView) MapConstants.SATELLITE_STYLE_JSON else MapConstants.STREET_STYLE
                        map.setStyle(Style.Builder().run {
                            if (isSatelliteView) fromJson(initialStyle) else fromUri(initialStyle)
                        }) { style ->
                            style.addImage(MapConstants.USER_ARROW_ID, createDirectionMarker("#007AFF"))
                            style.addImage(MapConstants.CUSTOM_ARROW_ID, createDirectionMarker("#5856D6"))
                            style.addImage("green_arrow", createDirectionMarker("#34C759"))
                            style.addImage("kaaba_marker", createKaabaMarker("#FFD700"))
                            style.addImage("alignment_ring", createAlignmentRing("#34C759"))
                            style.addImage("mosque_marker", createMosqueMarker())

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
                            style.addImage(MapConstants.USER_ARROW_ID, createDirectionMarker("#007AFF"))
                            style.addImage(MapConstants.CUSTOM_ARROW_ID, createDirectionMarker("#5856D6"))
                            style.addImage("green_arrow", createDirectionMarker("#34C759"))
                            style.addImage("kaaba_marker", createKaabaMarker("#FFD700"))
                            style.addImage("alignment_ring", createAlignmentRing("#34C759"))
                            style.addImage("mosque_marker", createMosqueMarker())
                            
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

        // Speech bubble anchored to the tapped mosque icon
        val currentMosque = selectedMosqueState.value
        val currentPos = selectedMosqueScreenPos.value
        if (currentMosque != null) lastShownMosque = currentMosque
        if (currentPos != null) lastScreenPos = currentPos

        AnimatedVisibility(
            visible = currentMosque != null,
            enter = scaleIn(initialScale = 0.8f) + fadeIn(tween(180)),
            exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(130)),
        ) {
            val mosque = lastShownMosque ?: return@AnimatedVisibility
            MosqueSpeechBubble(
                mosque = mosque,
                screenX = lastScreenPos.x,
                screenY = lastScreenPos.y,
                settings = settings,
                onDismiss = {
                    selectedMosqueState.value = null
                    selectedMosqueScreenPos.value = null
                }
            )
        }

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

    // Geometry effect: runs only when structure changes (location, alignment, custom point)
    // NOT on every compass update — that would delete/recreate symbols 20x/second, breaking clicks
    LaunchedEffect(settings, isAligned, customPoint, symbolManager, lineManager) {
        val sm = symbolManager ?: return@LaunchedEffect
        val lm = lineManager ?: return@LaunchedEffect
        sm.deleteAll()
        lm.deleteAll()

        val blue = "#007AFF"
        val green = "#34C759"
        val purple = "#5856D6"

        sm.create(SymbolOptions().withLatLng(kaabaLatLng).withIconImage("kaaba_marker").withIconSize(1.3f))

        var newAnnotations = QiblaAnnotations()

        settings?.let { loc ->
            val lat = loc.latitude
            val lng = loc.longitude
            if (lat != null && lng != null) {
                val userLatLng = LatLng(lat, lng)
                val activeColor = if (isAligned) green else blue
                val activeIcon  = if (isAligned) "green_arrow" else MapConstants.USER_ARROW_ID

                lm.create(
                    LineOptions().withLatLngs(listOf(userLatLng, kaabaLatLng))
                        .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(activeColor)))
                        .withLineWidth(8f).withLineOpacity(0.06f).withLineBlur(4f)
                )
                lm.create(
                    LineOptions().withLatLngs(listOf(userLatLng, kaabaLatLng))
                        .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                        .withLineWidth(if (isAligned) 6f else 5f)
                        .withLineJoin("round")
                )
                lm.create(
                    LineOptions().withLatLngs(listOf(userLatLng, kaabaLatLng))
                        .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(activeColor)))
                        .withLineWidth(if (isAligned) 3f else 2.5f)
                        .withLineJoin("round")
                )

                val ringSymbol = if (isAligned) sm.create(
                    SymbolOptions().withLatLng(userLatLng)
                        .withIconImage("alignment_ring")
                        .withIconOpacity(1.0f)
                        .withIconSize(0.01f)
                ) else null

                val userSymbol = sm.create(
                    SymbolOptions().withLatLng(userLatLng)
                        .withIconImage(activeIcon)
                        .withIconRotate(compassData.azimuth)
                        .withIconSize(1.2f)
                )

                newAnnotations = newAnnotations.copy(userSymbol = userSymbol, userRing = ringSymbol)
            }
        }

        customPoint?.let { cp ->
            lm.create(
                LineOptions().withLatLngs(listOf(cp, kaabaLatLng))
                    .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(purple)))
                    .withLineWidth(8f).withLineOpacity(0.06f).withLineBlur(4f)
            )
            lm.create(
                LineOptions().withLatLngs(listOf(cp, kaabaLatLng))
                    .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                    .withLineWidth(5f).withLineJoin("round")
            )
            lm.create(
                LineOptions().withLatLngs(listOf(cp, kaabaLatLng))
                    .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(purple)))
                    .withLineWidth(2.5f).withLineJoin("round")
            )

            val customSymbol = sm.create(
                SymbolOptions().withLatLng(cp)
                    .withIconImage(MapConstants.CUSTOM_ARROW_ID)
                    .withIconRotate(compassData.azimuth)
                    .withIconSize(1.2f)
            )

            newAnnotations = newAnnotations.copy(customSymbol = customSymbol)
        }

        annotations = newAnnotations
    }

    // Rotation-only effect: updates arrow rotation on every compass tick without recreating symbols
    LaunchedEffect(compassData.azimuth, annotations) {
        val sm = symbolManager ?: return@LaunchedEffect
        annotations.userSymbol?.let { it.iconRotate = compassData.azimuth; sm.update(it) }
        annotations.customSymbol?.let { it.iconRotate = compassData.azimuth; sm.update(it) }
    }

    // Ring scale effect: only active during the ~350ms spring animation
    LaunchedEffect(ringScale.value, annotations) {
        val sm = symbolManager ?: return@LaunchedEffect
        annotations.userRing?.let { it.iconSize = ringScale.value; sm.update(it) }
    }

    LaunchedEffect(symbolManager, mosques) {
        symbolManager ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect

        val featureList = mosques.map { mosque ->
            Feature.fromGeometry(
                GeoPoint.fromLngLat(mosque.lng, mosque.lat),
                JsonObject().apply {
                    addProperty("mosqueId", mosque.id)
                    addProperty("name", mosque.name ?: "")
                    addProperty("lat", mosque.lat)
                    addProperty("lng", mosque.lng)
                }
            )
        }
        val collection = FeatureCollection.fromFeatures(featureList)

        val existing = style.getSourceAs<GeoJsonSource>(MOSQUE_SOURCE_ID)
        if (existing != null) {
            existing.setGeoJson(collection)
        } else {
            style.addSource(GeoJsonSource(MOSQUE_SOURCE_ID, collection))
            style.addLayer(
                MapSymbolLayer(MOSQUE_LAYER_ID, MOSQUE_SOURCE_ID).withProperties(
                    PropertyFactory.iconImage("mosque_marker"),
                    PropertyFactory.iconSize(1.5f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            )
        }
    }
}

@Composable
private fun MosqueSpeechBubble(
    mosque: MosqueLocation,
    screenX: Float,
    screenY: Float,
    settings: UserSettings?,
    onDismiss: () -> Unit
) {
    val localDensity = LocalDensity.current
    val bubbleWidth = 220.dp
    val tailHeight = 10.dp
    val aboveIcon = 32.dp  // gap between tail tip and icon center

    val bubbleWidthPx = with(localDensity) { bubbleWidth.toPx() }
    val tailHeightPx = with(localDensity) { tailHeight.toPx() }
    val aboveIconPx = with(localDensity) { aboveIcon.toPx() }
    val marginPx = with(localDensity) { 16.dp.toPx() }

    // Start with a reasonable height estimate so the bubble doesn't flash at y=0
    var cardHeightPx by remember { mutableStateOf(with(localDensity) { 64.dp.toPx() }) }

    // Center bubble horizontally on the icon; clamp to left margin
    val xPx = (screenX - bubbleWidthPx / 2f).coerceAtLeast(marginPx)
    // Place bubble so its tail tip sits aboveIcon px above the icon center
    val yPx = (screenY - aboveIconPx - tailHeightPx - cardHeightPx).coerceAtLeast(marginPx)
    // Tail tip x relative to the bubble's left edge; keep it within the bubble
    val tailTipX = (screenX - xPx).coerceIn(tailHeightPx * 1.5f, bubbleWidthPx - tailHeightPx * 1.5f)

    // Snap instantly so the bubble tracks map panning without lag
    val xAnim by animateFloatAsState(xPx, animationSpec = snap(), label = "bubbleX")
    val yAnim by animateFloatAsState(yPx, animationSpec = snap(), label = "bubbleY")
    val tailXAnim by animateFloatAsState(tailTipX, animationSpec = snap(), label = "tailX")

    val distanceText = remember(mosque.id, settings?.latitude, settings?.longitude) {
        settings?.latitude?.let { userLat ->
            settings.longitude?.let { userLng ->
                val result = FloatArray(1)
                Location.distanceBetween(userLat, userLng, mosque.lat, mosque.lng, result)
                val m = result[0].toInt()
                if (m < 1000) "$m m" else "${"%.1f".format(m / 1000f)} km"
            }
        }
    }

    Column(
        modifier = Modifier
            .offset { IntOffset(xAnim.roundToInt(), yAnim.roundToInt()) }
            .width(bubbleWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.97f),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { cardHeightPx = it.height.toFloat() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(Color(0xFF34C759), CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        mosque.name ?: "Mosque",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.Black
                    )
                    if (distanceText != null) {
                        Text(
                            distanceText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.45f)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Downward-pointing tail triangle
        Spacer(
            Modifier
                .width(bubbleWidth)
                .height(tailHeight)
                .drawBehind {
                    drawPath(
                        ComposePath().apply {
                            moveTo(tailXAnim - tailHeightPx, 0f)
                            lineTo(tailXAnim + tailHeightPx, 0f)
                            lineTo(tailXAnim, size.height)
                            close()
                        },
                        color = Color.White.copy(alpha = 0.97f)
                    )
                }
        )
    }
}

private fun createDirectionMarker(colorHex: String): Bitmap {
    val size = 180
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val color = AndroidColor.parseColor(colorHex)

    // Directional drop shadow — offset downward, not symmetric
    canvas.drawCircle(cx + 1f, cy + 5f, 40f, Paint().apply {
        isAntiAlias = true
        this.color = AndroidColor.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    })

    // White border
    canvas.drawCircle(cx, cy, 42f, Paint().apply {
        isAntiAlias = true
        this.color = AndroidColor.WHITE
        style = Paint.Style.FILL
    })

    // Colored fill
    canvas.drawCircle(cx, cy, 37f, Paint().apply {
        isAntiAlias = true
        this.color = color
        style = Paint.Style.FILL
    })

    // Navigation chevron
    val path = Path().apply {
        moveTo(cx, cy - 20f)
        lineTo(cx + 13f, cy + 15f)
        lineTo(cx, cy + 7f)
        lineTo(cx - 13f, cy + 15f)
        close()
    }
    canvas.drawPath(path, Paint().apply {
        isAntiAlias = true
        this.color = AndroidColor.WHITE
        style = Paint.Style.FILL
    })

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

private fun createMosqueMarker(): Bitmap {
    val size = 140
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val green = AndroidColor.parseColor("#34C759")

    // Shadow
    canvas.drawCircle(cx + 1f, cy + 4f, 32f, Paint().apply {
        isAntiAlias = true
        color = AndroidColor.argb(50, 0, 0, 0)
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    })

    // White border
    canvas.drawCircle(cx, cy, 34f, Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    })

    // Green fill
    canvas.drawCircle(cx, cy, 29f, Paint().apply {
        isAntiAlias = true
        color = green
        style = Paint.Style.FILL
    })

    // White crescent
    val crescentPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx - 2f, cy, 13f, crescentPaint)
    canvas.drawCircle(cx + 3f, cy - 1f, 10f, Paint().apply {
        isAntiAlias = true
        color = green
        style = Paint.Style.FILL
    })

    return bitmap
}

private fun createAlignmentRing(colorHex: String): Bitmap {
    val size = 260
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val color = AndroidColor.parseColor(colorHex)

    // Single hairline ring — one signal, nothing more
    canvas.drawCircle(cx, cy, 96f, Paint().apply {
        isAntiAlias = true
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        alpha = 220
    })

    return bitmap
}
