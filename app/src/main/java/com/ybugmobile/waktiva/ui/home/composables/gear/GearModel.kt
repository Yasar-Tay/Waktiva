package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.PrayerType

/** One prayer as the gear dials draw it. */
internal class GearPrayer(
    val type: PrayerType,
    val minutes: Float,
    val color: Color,
    val painter: VectorPainter,
    val icon: ImageVector,
    /** Formatted prayer time, e.g. "13:26". */
    val label: String
)

/** Everything a dial needs for one frame. */
internal class GearFrame(
    val prayers: List<GearPrayer>,
    val current: GearPrayer,
    val nowMinutes: Float,
    val showNow: Boolean,
    /** Ambient rotation in radians, looping over [0, 2π). */
    val phase: Float,
    val rtl: Boolean,
    val labelStyle: TextStyle,
    val textMeasurer: TextMeasurer,
    /** Special-day bridge, drawn by the dial under its hand; null on ordinary days. */
    val bridge: SpecialDayBridge?,
    /** Material colours, toned for the current weather. */
    val palette: GearPalette
) {
    val direction get() = if (rtl) -1f else 1f
    val dayTurn get() = nowMinutes / 1440f * TAU
}

/** Where a dial has room for the special-day bridge below its centre. */
internal class BridgeSpec(
    /** Inner radius of the bridge. */
    val innerRadius: Float,
    /** Radius of the open area the bridge must stay inside. */
    val freeRadius: Float,
    /** Widest arc the bridge may span, in radians. */
    val maxSpan: Float
)

/**
 * A gear-styled day dial. Implementations cache their paths for one canvas size,
 * so build a new one whenever the size changes.
 */
internal interface GearDial {
    /** Distance from the centre to each prayer marker. */
    val markerDistance: Float

    /** Radius of a prayer marker, used for its tap target. */
    val markerRadius: Float

    /** Radius of the central circle the date card fills as a disc, or null to show the standard card. */
    val hubRadius: Float?

    /** Radius of the outermost ring around the hub, which the prayer name must clear; null if none. */
    val hubOuterRadius: Float?

    val bridge: BridgeSpec

    fun draw(scope: DrawScope, frame: GearFrame)
}

internal fun createGearDial(style: DayCircleStyle, sizePx: Float, pxPerDp: Float): GearDial = when (style) {
    DayCircleStyle.BRASS -> BrassGearDial(sizePx, pxPerDp)
    DayCircleStyle.STEEL -> SteelGearDial(sizePx, pxPerDp)
    DayCircleStyle.SKELETON -> SkeletonGearDial(sizePx, pxPerDp)
    DayCircleStyle.CLASSIC -> throw IllegalArgumentException("CLASSIC is drawn by PrayerCircleVisualization")
}
