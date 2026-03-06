package com.ybugmobile.waktiva.data.mapper

import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.waktiva.domain.model.HijriData
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
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
        if (parts.size >= 4) {
            HijriData(
                day = parts[0].toInt(),
                monthNumber = parts[1].toInt(),
                monthEn = parts[2],
                year = parts[3].toInt()
            )
        } else if (parts.size == 3) {
            HijriData(
                day = parts[0].toInt(),
                monthNumber = 1,
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
        ),
        moonPhase = moonPhase,
        moonIllumination = moonIllumination,
        moonrise = moonrise,
        moonset = moonset
    )
}

fun PrayerDay.toEntity(): PrayerDayEntity {
    val timings = this.timings
    val hijri = this.hijriDate
    return PrayerDayEntity(
        date = this.date.toString(),
        hijriDate = if (hijri != null) "${hijri.day} ${hijri.monthNumber} ${hijri.monthEn} ${hijri.year}" else "",
        fajr = timings[PrayerType.FAJR].toString(),
        sunrise = timings[PrayerType.SUNRISE].toString(),
        dhuhr = timings[PrayerType.DHUHR].toString(),
        asr = timings[PrayerType.ASR].toString(),
        maghrib = timings[PrayerType.MAGHRIB].toString(),
        isha = timings[PrayerType.ISHA].toString(),
        moonPhase = this.moonPhase,
        moonIllumination = this.moonIllumination,
        moonrise = this.moonrise,
        moonset = this.moonset
    )
}
