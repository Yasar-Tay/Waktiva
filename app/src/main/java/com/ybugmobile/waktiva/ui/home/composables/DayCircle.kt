package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ybugmobile.waktiva.domain.model.CurrentPrayer
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.ui.home.composables.gear.GearDayCircle
import java.time.LocalTime

/**
 * The home screen's day circle, drawn in the style the user picked in settings.
 * [sunLight] is the screen angle (radians) the sunlight falls from, which the gear styles' metal
 * reflects, or null for the default light; the classic circle ignores it.
 */
@Composable
fun DayCircle(
    style: DayCircleStyle,
    day: PrayerDay,
    currentTime: LocalTime,
    currentPrayer: CurrentPrayer?,
    isSelectedDayToday: Boolean,
    isHijriVisible: Boolean = false,
    onToggleHijri: () -> Unit = {},
    contentColor: Color = Color.White,
    sunLight: Float? = null
) {
    if (style == DayCircleStyle.CLASSIC) {
        PrayerCircleVisualization(
            day = day,
            currentTime = currentTime,
            nextPrayer = null,
            currentPrayer = currentPrayer,
            isSelectedDayToday = isSelectedDayToday,
            isHijriVisible = isHijriVisible,
            onToggleHijri = onToggleHijri,
            contentColor = contentColor
        )
    } else {
        GearDayCircle(
            style = style,
            day = day,
            currentTime = currentTime,
            currentPrayer = currentPrayer,
            isSelectedDayToday = isSelectedDayToday,
            isHijriVisible = isHijriVisible,
            onToggleHijri = onToggleHijri,
            contentColor = contentColor,
            sunLight = sunLight
        )
    }
}
