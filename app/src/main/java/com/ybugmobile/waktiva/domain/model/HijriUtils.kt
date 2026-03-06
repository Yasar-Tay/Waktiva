package com.ybugmobile.waktiva.domain.model

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField

object HijriUtils {
    /**
     * Calculates the effective Hijri date for a given target date.
     * The Hijri day is considered to start at midnight.
     *
     * @param targetDate The Gregorian date we want the Hijri date for.
     * @param allPrayerDays The list of available prayer timings and Hijri data.
     * @return The HijriData for the target date.
     */
    fun getEffectiveHijriDate(
        targetDate: LocalDate,
        allPrayerDays: List<PrayerDay>
    ): HijriData? {
        return allPrayerDays.find { it.date == targetDate }?.hijriDate
            ?: calculateFallbackHijri(targetDate)
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
