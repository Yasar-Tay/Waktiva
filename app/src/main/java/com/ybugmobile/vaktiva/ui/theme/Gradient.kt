package com.ybugmobile.vaktiva.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun getGradientForTime(currentTime: LocalTime, day: PrayerDayEntity?): Brush {
    if (day == null) return Brush.verticalGradient(listOf(Color(0xFF1e3c72), Color(0xFF2a5298)))
    
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    fun parse(s: String) = LocalTime.parse(s.split(" ")[0], formatter)
    
    val fajr = parse(day.fajr)
    val sunrise = parse(day.sunrise)
    val dhuhr = parse(day.dhuhr)
    val asr = parse(day.asr)
    val maghrib = parse(day.maghrib)
    val isha = parse(day.isha)

    return when {
        currentTime.isBefore(fajr) -> Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43))) // Deep Night
        currentTime.isBefore(sunrise) -> Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color(0xFFFD746C))) // Dawn
        currentTime.isBefore(dhuhr) -> Brush.verticalGradient(listOf(Color(0xFFF3904F), Color(0xFF3B4371))) // Morning
        currentTime.isBefore(asr) -> Brush.verticalGradient(listOf(Color(0xFF4CA1AF), Color(0xFFC4E0E5))) // Midday
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(listOf(Color(0xFFF16529), Color(0xFFE44D26))) // Golden Hour
        currentTime.isBefore(isha) -> Brush.verticalGradient(listOf(Color(0xFFE94057), Color(0xFFF27121))) // Sunset
        else -> Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364))) // Night After Isha
    }
}
