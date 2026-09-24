package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ybugmobile.waktiva.domain.model.CurrentPrayer
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.ui.home.composables.gear.GearDayCircle
import com.ybugmobile.waktiva.ui.home.composables.gear.GearDayCircleStyle
import java.time.LocalTime

/** The home screen's day circle, drawn in the style the user picked in settings. */
@Composable
fun DayCircle(
    style: DayCircleStyle,
    day: PrayerDay,
    currentTime: LocalTime,
    nextPrayer: NextPrayer?,
    currentPrayer: CurrentPrayer?,
    isSelectedDayToday: Boolean,
    isHijriVisible: Boolean = false,
    onToggleHijri: () -> Unit = {},
    contentColor: Color = Color.White,
    isMuted: Boolean = false,
    playAdhanAudio: Boolean = false,
    onSkipAudio: (String) -> Unit = {}
) {
    val gearStyle = when (style) {
        DayCircleStyle.CLASSIC -> null
        DayCircleStyle.BRASS -> GearDayCircleStyle.BRASS
        DayCircleStyle.STEEL -> GearDayCircleStyle.STEEL
        DayCircleStyle.SKELETON -> GearDayCircleStyle.SKELETON
    }
    if (gearStyle == null) {
        PrayerCircleVisualization(
            day, currentTime, nextPrayer, currentPrayer, isSelectedDayToday,
            isHijriVisible, onToggleHijri, contentColor, isMuted, playAdhanAudio, onSkipAudio
        )
    } else {
        GearDayCircle(
            gearStyle, day, currentTime, nextPrayer, currentPrayer, isSelectedDayToday,
            isHijriVisible, onToggleHijri, contentColor, isMuted, playAdhanAudio, onSkipAudio
        )
    }
}
