package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.ybugmobile.waktiva.domain.model.CurrentPrayer
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.isLogged
import com.ybugmobile.waktiva.ui.home.composables.gear.GearDayCircle
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * The home screen's day circle, drawn in the style the user picked in settings.
 * [sunLight] is the screen angle (radians) the sunlight falls from, which the metal of every style
 * reflects, or null for the default light.
 *
 * Today's prayers marked in the prayer log ([prayedPrayers], null until the log has loaded) glow on
 * the circle. Tapping one of today's prayers whose time has come calls [onLogPrayer] to log it;
 * other badges show their time.
 *
 * [weather] fills the circle with the day's sky, hour by hour, and puts each prayer's weather
 * beside its time; see [DayCircleWeather].
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
    sunLight: Float? = null,
    prayedPrayers: Set<PrayerType>? = null,
    onLogPrayer: ((PrayerType) -> Unit)? = null,
    weather: DayCircleWeather? = null
) {
    // The dial moves by the minute and only shows which prayer is current. So it gets the time to
    // the minute and a prayer that changes with the prayer, not with its elapsed time every second.
    // Being the same instances for the whole minute, they let the dial skip the seconds.
    val minuteTime = remember(currentTime.hour, currentTime.minute) { currentTime.truncatedTo(ChronoUnit.MINUTES) }
    val prayer = remember(currentPrayer?.type, currentPrayer?.date) { currentPrayer }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val now = if (isSelectedDayToday) minuteTime else null
    // The sky changes by the hour; the prayers' weather also fades the prayers gone by.
    val sky = remember(day, weather, now?.hour, rtl) { daySky(day, weather, now, rtl) }
    val prayersWeather = remember(day, weather, now, prayer?.type) { prayerWeather(day, weather, now, prayer?.type) }
    // The day's weather as the dial shows it: an icon for each spell of one kind of weather.
    val spells = remember(day, weather, now?.hour) { weatherSpells(day, weather, now) }
    // With weather effects on, each badge is toned for its own hour, as the sky is.
    val badgeWeather = remember(prayersWeather, weather?.effectsOn) {
        if (weather?.effectsOn == true) prayersWeather.mapValues { it.value.effectCondition } else emptyMap()
    }
    val temperatureRange = remember(weather?.forecast) { weather?.forecast?.temperatureRange() }

    // Only today's circle shows the log; another day's prayers are all still to come.
    val prayed = if (isSelectedDayToday) prayedPrayers else emptySet()
    val onPrayerTap: (PrayerType) -> Boolean = { type ->
        val time = day.timings[type]
        val canLog = onLogPrayer != null && isSelectedDayToday && type.isLogged &&
            time != null && !time.isAfter(minuteTime)
        if (canLog) onLogPrayer?.invoke(type)
        canLog
    }

    if (style == DayCircleStyle.CLASSIC) {
        PrayerCircleVisualization(
            day = day,
            currentTime = minuteTime,
            nextPrayer = null,
            currentPrayer = prayer,
            isSelectedDayToday = isSelectedDayToday,
            isHijriVisible = isHijriVisible,
            onToggleHijri = onToggleHijri,
            contentColor = contentColor,
            sunLight = sunLight,
            prayedPrayers = prayed,
            onPrayerTap = onPrayerTap,
            sky = sky,
            prayerWeather = prayersWeather,
            badgeWeather = badgeWeather,
            weatherSpells = spells,
            temperatureRange = temperatureRange
        )
    } else {
        GearDayCircle(
            style = style,
            day = day,
            currentTime = minuteTime,
            currentPrayer = prayer,
            isSelectedDayToday = isSelectedDayToday,
            isHijriVisible = isHijriVisible,
            onToggleHijri = onToggleHijri,
            contentColor = contentColor,
            sunLight = sunLight,
            prayedPrayers = prayed,
            onPrayerTap = onPrayerTap,
            sky = sky,
            prayerWeather = prayersWeather,
            badgeWeather = badgeWeather,
            weatherSpells = spells,
            temperatureRange = temperatureRange
        )
    }
}
