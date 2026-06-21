package com.ybugmobile.waktiva.data.remote.dto

import com.google.gson.annotations.SerializedName

data class WeatherResponseDto(
    @SerializedName("location")
    val location: WeatherLocationDto,
    @SerializedName("current")
    val current: CurrentWeatherDto
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
