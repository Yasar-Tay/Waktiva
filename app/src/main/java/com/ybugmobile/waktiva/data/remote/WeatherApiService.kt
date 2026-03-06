package com.ybugmobile.waktiva.data.remote

import com.ybugmobile.waktiva.data.remote.dto.WeatherResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("current") current: String = "temperature_2m,weather_code,is_day",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}
