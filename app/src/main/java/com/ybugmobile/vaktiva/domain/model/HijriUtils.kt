package com.ybugmobile.vaktiva.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField

object HijriUtils {
    /**
     * Calculates the effective Hijri date for a given target date, considering the Islamic
     * practice of starting the new day at Maghrib (sunset) or the standard midnight.
     *
     * @param targetDate The Gregorian date we want the Hijri date for.
     * @param allPrayerDays The list of available prayer timings and Hijri data.
     * @param currentTime The current system time.
     * @param todayDate The current Gregorian date.
     * @param startsAtMaghrib Whether the Hijri day starts at Maghrib (true) or midnight (false).
     * @return The HijriData for the target date.
     */
    fun getEffectiveHijriDate(
        targetDate: LocalDate,
        allPrayerDays: List<PrayerDay>,
        currentTime: LocalTime,
        todayDate: LocalDate = LocalDate.now(),
        startsAtMaghrib: Boolean = true
    ): HijriData? {
        // Rollover logic should only apply when we are viewing "Today" relative to system time.
        // For any other date, we show the standard Hijri date for that Gregorian day.
        val isTargetToday = targetDate == todayDate
        
        val isPastRollover = if (startsAtMaghrib && isTargetToday) {
            val todayRecord = allPrayerDays.find { it.date == todayDate }
            val maghribTime = todayRecord?.timings?.get(PrayerType.MAGHRIB) ?: LocalTime.of(18, 0)
            currentTime.isAfter(maghribTime) || currentTime == maghribTime
        } else {
            false
        }

        val sourceDate = if (isPastRollover) targetDate.plusDays(1) else targetDate
        
        return allPrayerDays.find { it.date == sourceDate }?.hijriDate 
            ?: calculateFallbackHijri(sourceDate)
    }

    /**
     * Provides a local fallback calculation of the Hijri date using Java's HijrahChronology.
     */
    fun calculateFallbackHijri(date: LocalDate): HijriData? {
        return try {
            val hDate = HijrahChronology.INSTANCE.date(date)
            HijriData(
                day = hDate.get(ChronoField.DAY_OF_MONTH),
                monthNumber = hDate.get(ChronoField.MONTH_OF_YEAR),
                monthEn = "",
                year = hDate.get(ChronoField.YEAR)
            )
        } catch (e: Exception) {
            null
        }
    }
}
