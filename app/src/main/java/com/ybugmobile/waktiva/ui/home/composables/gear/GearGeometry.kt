package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val TAU = (2 * PI).toFloat()

/** The day wheel has one tooth per 15 minutes of the day. */
internal const val MAIN_TEETH = 96

/** Prayer gears turn 8 times for every turn of the day wheel. */
internal const val PLANET_TEETH = 12

internal fun Float.toDegrees(): Float = this * 180f / PI.toFloat()

internal fun pointOn(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))

internal fun rotateAround(point: Offset, pivot: Offset, angle: Float): Offset {
    val dx = point.x - pivot.x
    val dy = point.y - pivot.y
    return Offset(pivot.x + dx * cos(angle) - dy * sin(angle), pivot.y + dx * sin(angle) + dy * cos(angle))
}

/**
 * Angle of [minutes] on the dial, matching PrayerCircleVisualization:
 * midnight at the bottom, noon at the top, clockwise (counter-clockwise in RTL).
 */
internal fun dayAngle(minutes: Float, rtl: Boolean): Float =
    (if (rtl) -1f else 1f) * minutes / 1440f * TAU + TAU / 4f

/** Circular pitch; meshing gears must share it. */
private fun circularPitch(pitchRadius: Float, teeth: Int) = TAU * pitchRadius / teeth

internal fun gearAddendum(pitchRadius: Float, teeth: Int) = 0.30f * circularPitch(pitchRadius, teeth)

internal fun gearDedendum(pitchRadius: Float, teeth: Int) = 0.36f * circularPitch(pitchRadius, teeth)

/**
 * Appends a toothed outline around [center] to [path]. Tooth k is centred on
 * `rotation + k * 2π / teeth`. [internal] points the teeth inward, as on a ring gear.
 */
internal fun addGearOutline(
    path: Path,
    center: Offset,
    pitchRadius: Float,
    teeth: Int,
    rotation: Float = 0f,
    internal: Boolean = false
) {
    val step = TAU / teeth
    val add = gearAddendum(pitchRadius, teeth)
    val ded = gearDedendum(pitchRadius, teeth)
    val tipRadius = if (internal) pitchRadius - add else pitchRadius + add
    val rootRadius = if (internal) pitchRadius + ded else pitchRadius - ded
    val baseHalf = 0.27f * step
    val tipHalf = 0.13f * step
    val rootRect = Rect(center, rootRadius)
    val tipRect = Rect(center, tipRadius)

    for (k in 0 until teeth) {
        val a = rotation + k * step
        if (k == 0) pointOn(center, rootRadius, a - step + baseHalf).let { path.moveTo(it.x, it.y) }
        path.arcTo(rootRect, (a - step + baseHalf).toDegrees(), (step - 2 * baseHalf).toDegrees(), false)
        pointOn(center, tipRadius, a - tipHalf).let { path.lineTo(it.x, it.y) }
        path.arcTo(tipRect, (a - tipHalf).toDegrees(), (2 * tipHalf).toDegrees(), false)
        pointOn(center, rootRadius, a + baseHalf).let { path.lineTo(it.x, it.y) }
    }
    path.close()
}

/**
 * Rotation for a gear meshing on the outside of a driver so its teeth sit in the
 * driver's gaps. [theta] is the direction from the driver's centre to the driven gear.
 * The driven gear turns the opposite way at `driverTeeth / drivenTeeth` speed.
 */
internal fun meshExternal(driverRotation: Float, driverTeeth: Int, theta: Float, drivenTeeth: Int): Float {
    val u = (theta - driverRotation) / (TAU / driverTeeth)
    return theta + TAU / 2f - (0.5f - u) * (TAU / drivenTeeth)
}

/**
 * Rotation for a planet gear meshing inside a ring gear. The planet turns the same
 * way as the ring at `ringTeeth / planetTeeth` speed.
 */
internal fun meshInternal(ringRotation: Float, ringTeeth: Int, theta: Float, planetTeeth: Int): Float {
    val u = (theta - ringRotation) / (TAU / ringTeeth)
    return theta - (u - 0.5f) * (TAU / planetTeeth)
}
