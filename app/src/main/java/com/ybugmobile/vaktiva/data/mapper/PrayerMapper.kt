package com.ybugmobile.vaktiva.data.mapper

import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.domain.model.HijriData
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun PrayerDayEntity.toDomain(): PrayerDay {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    
    fun parseTime(timeStr: String): LocalTime {
        val cleaned = timeStr.split(" ")[0]
        return LocalTime.parse(cleaned, formatter)
    }

    val hijri = try {
        val parts = hijriDate.split(" ")
        if (parts.size >= 3) {
            HijriData(
                day = parts[0].toInt(),
                monthEn = parts[1],
                year = parts[2].toInt()
            )
        } else null
    } catch (e: Exception) {
        null
    }

    return PrayerDay(
        date = LocalDate.parse(date),
        hijriDate = hijri,
        timings = mapOf(
            PrayerType.FAJR to parseTime(fajr),
            PrayerType.SUNRISE to parseTime(sunrise),
            PrayerType.DHUHR to parseTime(dhuhr),
            PrayerType.ASR to parseTime(asr),
            PrayerType.MAGHRIB to parseTime(maghrib),
            PrayerType.ISHA to parseTime(isha)
        )
    )
}
