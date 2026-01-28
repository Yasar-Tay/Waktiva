package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalTime

fun getGradientForTime(currentTime: LocalTime, day: PrayerDay?): Brush {
    if (day == null) return Brush.verticalGradient(listOf(Color(0xFF1e3c72), Color(0xFF2a5298)))

    val fajr = day.timings[PrayerType.FAJR] ?: LocalTime.of(5, 0)
    val fajrKarahat = day.timings[PrayerType.FAJR]?.minusMinutes(45) ?: LocalTime.of(5, 0)
    val sunrise = day.timings[PrayerType.SUNRISE] ?: LocalTime.of(6, 30)
    val sunriseKarahat = day.timings[PrayerType.SUNRISE]?.plusMinutes(45) ?: LocalTime.of(6, 30)
    val dhuhr = day.timings[PrayerType.DHUHR] ?: LocalTime.of(12, 30)
    val asr = day.timings[PrayerType.ASR] ?: LocalTime.of(15, 30)
    val asrKarahat = day.timings[PrayerType.ASR]?.minusMinutes(45) ?: LocalTime.of(15, 30)
    val maghrib = day.timings[PrayerType.MAGHRIB] ?: LocalTime.of(18, 30)
    val isha = day.timings[PrayerType.ISHA] ?: LocalTime.of(20, 0)

    return when {
        currentTime.isBefore(fajr) -> Brush.verticalGradient(
            listOf(
                Color(0xFF0F2027),
                Color(0xFF203A43)
            )
        ) // Deep Night
        currentTime.isAfter(fajrKarahat) && currentTime.isBefore(sunriseKarahat) -> Brush.verticalGradient(
            listOf(
                Color(0xFFFD746C),
                Color(0xFF02868A)
            )
        ) // Dawn
        currentTime.isBefore(dhuhr) -> Brush.verticalGradient(
            listOf(
                Color(0xFF2863A9),
                Color(0xFF4C79AF)
            )
        ) // Midday
        currentTime.isBefore(asr) -> Brush.verticalGradient(
            listOf(
                Color(0xFF2863A9),
                Color(0xFF4C79AF)
            )
        ) // Morning
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(
            listOf(
                Color(0xFF2863A9),
                Color(0xFF4C79AF)
            )
        ) // Golden Hour
        currentTime.isAfter(asrKarahat) && currentTime.isBefore(maghrib)  -> Brush.verticalGradient(
            listOf(
                Color(0xFFE94057),
                Color(0xFFFF5722),
                Color(0xFF381B08)
            )
        ) // Sunset
        currentTime.isAfter(maghrib) && currentTime.isBefore(isha) -> Brush.verticalGradient(
            listOf(
                Color(0xFF601A23),
                Color(0xFF021F3F)
            )
        ) // through Isha
        else -> Brush.verticalGradient(
            listOf(
                Color(0xFF0F2027),
                Color(0xFF2C5364)
            )
        ) // Night After Isha
    }
}
