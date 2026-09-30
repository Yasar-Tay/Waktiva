package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.data.local.CachedWeather
import com.ybugmobile.waktiva.domain.model.DayForecast
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.isLogged
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One time of the day in the day circle widgets' strip. [status] is where the prayer stands in
 * the prayer log, or null for a time the log doesn't keep (sunrise, or every time while the log
 * is off).
 */
internal data class DayCell(
    val type: PrayerType,
    val time: LocalTime,
    val status: PrayerLogStatus?,
    val isCurrent: Boolean,
    val isPassed: Boolean
) {
    /** Whether a tap marks it, or takes the mark back: once its time has come. */
    val canMark: Boolean get() = status != null && status != PrayerLogStatus.UPCOMING
}

/** The weather the widgets show for now, and the day's forecast when there is one. */
internal data class WidgetWeather(
    val condition: WeatherCondition,
    val effectCondition: WeatherCondition,
    val isDay: Boolean,
    val temperature: Double,
    val forecast: DayForecast?
)

internal object DayWidgetModel {

    /** How long the weather fetched stays "now"; after that the forecast for the hour stands in. */
    const val CURRENT_WEATHER_FRESH_MILLIS = 2 * 60 * 60 * 1000L

    /** The prayer whose time it is at [now]: the last to have begun, or the night's Isha before Fajr. */
    fun currentPrayer(day: PrayerDay, now: LocalTime): PrayerType =
        day.timings.entries
            .filter { !it.value.isAfter(now) }
            .maxByOrNull { it.value }
            ?.key ?: PrayerType.ISHA

    /**
     * [day]'s [types] at [now], each with its place in the prayer log from the prayers marked on
     * it ([prayed]) when [logEnabled]. [nextDay] closes Isha's time at the next dawn; [logStart]
     * is the day the log began.
     */
    fun cells(
        day: PrayerDay,
        nextDay: PrayerDay?,
        types: List<PrayerType>,
        prayed: Set<PrayerType>,
        now: LocalDateTime,
        logStart: LocalDate?,
        logEnabled: Boolean
    ): List<DayCell> {
        val current = currentPrayer(day, now.toLocalTime()).takeIf { day.date == now.toLocalDate() }
        val trackedSince = PrayerLog.trackedSince(logStart, now.toLocalDate())
        return types.mapNotNull { type ->
            val time = day.timings[type] ?: return@mapNotNull null
            val start = time.atDate(day.date)
            val status = if (logEnabled && type.isLogged) {
                PrayerLog.status(
                    date = day.date,
                    type = type,
                    prayed = type in prayed,
                    now = now,
                    start = start,
                    end = PrayerLog.windowEnd(type, day, nextDay),
                    trackedSince = trackedSince
                )
            } else {
                null
            }
            DayCell(
                type = type,
                time = time,
                status = status,
                isCurrent = type == current,
                isPassed = !start.isAfter(now) && type != current
            )
        }
    }

    /**
     * The weather for [now] from the [cache]: what was reported while it's fresh, the forecast
     * for the hour after that, and nothing once the cache no longer holds [now]'s day.
     */
    fun weatherNow(cache: CachedWeather?, now: LocalDateTime, nowMillis: Long): WidgetWeather? {
        cache ?: return null
        val forecast = cache.info.forecast.firstOrNull { it.date == now.toLocalDate() }
        val age = nowMillis - cache.fetchedAtMillis
        if (age in 0 until CURRENT_WEATHER_FRESH_MILLIS) {
            val info = cache.info
            return WidgetWeather(info.condition, info.effectCondition, info.isDay, info.temperature, forecast)
        }
        val hour = forecast?.at(now.toLocalTime()) ?: return null
        return WidgetWeather(hour.condition, hour.effectCondition, hour.isDay, hour.temperature, forecast)
    }
}
