package com.ybugmobile.waktiva.data.remote.dto

import com.google.gson.annotations.SerializedName

data class WeatherResponseDto(
    @SerializedName("location")
    val location: WeatherLocationDto,
    @SerializedName("current")
    val current: CurrentWeatherDto,
    /** Only in forecast.json responses. */
    @SerializedName("forecast")
    val forecast: ForecastDto? = null
)

data class WeatherLocationDto(
    @SerializedName("lat")
    val latitude: Double,
    @SerializedName("lon")
    val longitude: Double
)

data class CurrentWeatherDto(
    @SerializedName("temp_c")
    val temperatureCelsius: Double,
    @SerializedName("is_day")
    val isDay: Int,
    @SerializedName("precip_mm")
    val precipitationMillimeters: Double,
    @SerializedName("cloud")
    val cloudCoverPercent: Int,
    @SerializedName("condition")
    val condition: WeatherConditionDto
)

data class WeatherConditionDto(
    @SerializedName("code")
    val code: Int
)

data class ForecastDto(
    @SerializedName("forecastday")
    val days: List<ForecastDayDto>
)

data class ForecastDayDto(
    /** yyyy-MM-dd, in the location's time zone. */
    @SerializedName("date")
    val date: String,
    @SerializedName("day")
    val day: ForecastDaySummaryDto,
    @SerializedName("hour")
    val hours: List<ForecastHourDto>
)

data class ForecastDaySummaryDto(
    @SerializedName("maxtemp_c")
    val maxTemperatureCelsius: Double,
    @SerializedName("mintemp_c")
    val minTemperatureCelsius: Double
)

data class ForecastHourDto(
    /** yyyy-MM-dd HH:mm, in the location's time zone. */
    @SerializedName("time")
    val time: String,
    @SerializedName("temp_c")
    val temperatureCelsius: Double,
    @SerializedName("is_day")
    val isDay: Int,
    @SerializedName("precip_mm")
    val precipitationMillimeters: Double,
    @SerializedName("cloud")
    val cloudCoverPercent: Int,
    @SerializedName("condition")
    val condition: WeatherConditionDto
)
