package com.ybugmobile.waktiva.data.local

import android.content.Context
import com.google.gson.Gson
import com.ybugmobile.waktiva.domain.model.DayForecast
import com.ybugmobile.waktiva.domain.model.HourForecast
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.WeatherInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The last weather fetched, with when it was fetched; see [WeatherCache]. */
data class CachedWeather(val info: WeatherInfo, val fetchedAtMillis: Long)

/**
 * The last weather the app fetched, kept on disk so the home-screen widgets can show the day's
 * weather without asking the network each time they're drawn. The repository writes it on every
 * successful fetch.
 */
@Singleton
class WeatherCache @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(info: WeatherInfo, fetchedAtMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putString(KEY, gson.toJson(info.toDto(fetchedAtMillis))).apply()
    }

    fun load(): CachedWeather? = runCatching {
        prefs.getString(KEY, null)?.let { gson.fromJson(it, WeatherCacheDto::class.java)?.toCached() }
    }.getOrNull()

    private companion object {
        const val KEY = "last_weather"
    }
}

// The file format: plain strings for dates and weather, so a renamed enum only drops the cache.

private data class WeatherCacheDto(
    val fetchedAt: Long,
    val temperature: Double,
    val condition: String,
    val effectCondition: String,
    val isDay: Boolean,
    val precipitation: Double,
    val cloudCover: Int,
    val days: List<WeatherCacheDayDto>?
)

private data class WeatherCacheDayDto(val date: String, val min: Double, val max: Double, val hours: List<WeatherCacheHourDto>?)

private data class WeatherCacheHourDto(val hour: Int, val condition: String, val effect: String, val temperature: Double, val isDay: Boolean)

private fun WeatherInfo.toDto(fetchedAt: Long) = WeatherCacheDto(
    fetchedAt = fetchedAt,
    temperature = temperature,
    condition = condition.name,
    effectCondition = effectCondition.name,
    isDay = isDay,
    precipitation = precipitationMillimeters,
    cloudCover = cloudCoverPercent,
    days = forecast.map { day ->
        WeatherCacheDayDto(
            date = day.date.toString(),
            min = day.minTemp,
            max = day.maxTemp,
            hours = day.hours.map { WeatherCacheHourDto(it.hour, it.condition.name, it.effectCondition.name, it.temperature, it.isDay) }
        )
    }
)

private fun WeatherCacheDto.toCached(): CachedWeather {
    fun weather(name: String?) = WeatherCondition.entries.firstOrNull { it.name == name } ?: WeatherCondition.UNKNOWN
    val info = WeatherInfo(
        temperature = temperature,
        condition = weather(condition),
        isDay = isDay,
        precipitationMillimeters = precipitation,
        cloudCoverPercent = cloudCover,
        effectCondition = weather(effectCondition),
        forecast = days.orEmpty().map { day ->
            DayForecast(
                date = LocalDate.parse(day.date),
                minTemp = day.min,
                maxTemp = day.max,
                hours = day.hours.orEmpty().map {
                    HourForecast(it.hour, weather(it.condition), weather(it.effect), it.temperature, it.isDay)
                }
            )
        }
    )
    return CachedWeather(info, fetchedAt)
}
