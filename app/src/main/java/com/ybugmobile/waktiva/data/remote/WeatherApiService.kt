package com.ybugmobile.waktiva.data.remote

import com.ybugmobile.waktiva.BuildConfig
import com.ybugmobile.waktiva.data.remote.dto.WeatherResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("current.json")
    suspend fun getCurrentWeather(
        @Query("q") query: String,
        @Query("key") apiKey: String = BuildConfig.WEATHER_API_KEY,
        @Query("aqi") airQuality: String = "no"
    ): WeatherResponseDto

    companion object {
        const val BASE_URL = "https://api.weatherapi.com/v1/"
    }
}
