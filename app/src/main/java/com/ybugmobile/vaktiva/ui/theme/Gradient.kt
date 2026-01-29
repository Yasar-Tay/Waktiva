package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalTime

fun getGradientForTime(currentTime: LocalTime, day: PrayerDay?): Brush {
    if (day == null) return Brush.verticalGradient(listOf(Color(0xFF1e3c72), Color(0xFF2a5298)))

    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)

    // Transitions
    val dawnStart = fajr.minusMinutes(45)
    val dayStart = sunrise.plusMinutes(45)
    val sunsetStart = maghrib.minusMinutes(45)

    return when {
        // Deep Night (Midnight to 45m before Fajr)
        currentTime.isBefore(dawnStart) -> Brush.verticalGradient(
            listOf(Color(0xFF0F2027), Color(0xFF203A43))
        )

        // Dawn (45m before Fajr to 45m after Sunrise)
        currentTime.isBefore(dayStart) -> Brush.verticalGradient(
            listOf(Color(0xFFE96443), Color(0xFF904E95)) // Darker, richer dawn for contrast
        )

        // Midday (Morning/Afternoon until 45m before Maghrib)
        currentTime.isBefore(sunsetStart) -> Brush.verticalGradient(
            listOf(Color(0xFF0F4888), Color(0xFF437AA6)) // Deeper blue at top for white text contrast
        )

        // Sunset (45m before Maghrib until Maghrib)
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(
            listOf(Color(0xFFE94057), Color(0xFFFF5722), Color(0xFF381B08))
        )

        // Evening / Dusk (Maghrib until Isha)
        currentTime.isBefore(isha) -> Brush.verticalGradient(
            listOf(Color(0xFF601A23), Color(0xFF021F3F))
        )

        // Late Night (After Isha)
        else -> Brush.verticalGradient(
            listOf(Color(0xFF0F2027), Color(0xFF2C5364))
        )
    }
}
