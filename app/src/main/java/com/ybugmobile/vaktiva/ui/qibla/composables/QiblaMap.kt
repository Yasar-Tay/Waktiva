package com.ybugmobile.vaktiva.ui.qibla.composables

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.data.sensor.CompassData
import com.ybugmobile.vaktiva.ui.qibla.MapConstants
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

@Composable
fun QiblaMap(
    settings: UserSettings?,
    compassData: CompassData,
    isSatelliteView: Boolean,
    kaabaLatLng: LatLng,
    onMapReady: (MapLibreMap) -> Unit,
    onMapLongClick: (LatLng) -> Unit
) {
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var lineManager by remember { mutableStateOf<LineManager?>(null) }
    var customPoint by remember { mutableStateOf<LatLng?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                getMapAsync { map ->
                    mapInstance = map
                    onMapReady(map)
                    val initialStyle = if (isSatelliteView) MapConstants.SATELLITE_STYLE_JSON else MapConstants.STREET_STYLE
                    map.setStyle(Style.Builder().run {
                        if (isSatelliteView) fromJson(initialStyle) else fromUri(initialStyle)
                    }) { style ->
                        style.addImage(MapConstants.USER_ARROW_ID, createArrowBitmap("#2196F3"))
                        style.addImage(MapConstants.CUSTOM_ARROW_ID, createArrowBitmap("#F44336"))

                        symbolManager = SymbolManager(this@apply, map, style).apply {
                            iconAllowOverlap = true
                            iconIgnorePlacement = true
                        }
                        lineManager = LineManager(this@apply, map, style)
                        settings?.let {
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(it.latitude, it.longitude),
                                    MapConstants.DEFAULT_ZOOM
                                )
                            )
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
                        style.addImage(MapConstants.USER_ARROW_ID, createArrowBitmap("#2196F3"))
                        style.addImage(MapConstants.CUSTOM_ARROW_ID, createArrowBitmap("#F44336"))
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
    LaunchedEffect(settings, compassData.azimuth, customPoint, symbolManager, lineManager) {
        val sm = symbolManager ?: return@LaunchedEffect
        val lm = lineManager ?: return@LaunchedEffect
        sm.deleteAll()
        lm.deleteAll()
        settings?.let { loc ->
            val userLatLng = LatLng(loc.latitude, loc.longitude)
            sm.create(
                SymbolOptions().withLatLng(userLatLng).withIconImage(MapConstants.USER_ARROW_ID)
                    .withIconRotate(compassData.azimuth).withIconSize(1.5f)
            )
            lm.create(
                LineOptions().withLatLngs(listOf(userLatLng, kaabaLatLng))
                    .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#FFD700")))
                    .withLineWidth(4f)
            )
        }
        customPoint?.let { cp ->
            sm.create(
                SymbolOptions().withLatLng(cp).withIconImage(MapConstants.CUSTOM_ARROW_ID)
                    .withIconRotate(compassData.azimuth).withIconSize(1.5f)
            )
            lm.create(
                LineOptions().withLatLngs(listOf(cp, kaabaLatLng))
                    .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#F44336")))
                    .withLineWidth(3f)
            )
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