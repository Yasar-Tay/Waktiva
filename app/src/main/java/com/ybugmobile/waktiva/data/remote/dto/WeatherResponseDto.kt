package com.ybugmobile.waktiva.data.remote.dto

import com.google.gson.annotations.SerializedName

data class WeatherResponseDto(
    @SerializedName("current")
    val current: CurrentWeatherDto
)

data class CurrentWeatherDto(
    @SerializedName("temp_c")
    val temperatureCelsius: Double,
    @SerializedName("is_day")
    val isDay: Int,
    @SerializedName("condition")
    val condition: WeatherConditionDto
)

data class WeatherConditionDto(
    @SerializedName("code")
    val code: Int
)
