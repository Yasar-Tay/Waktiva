package com.ybugmobile.waktiva.data.remote

import com.ybugmobile.waktiva.BuildConfig
import com.ybugmobile.waktiva.data.remote.dto.WeatherResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    /** The current weather and [days] days of hourly forecast, today first, in one request. */
    @GET("forecast.json")
    suspend fun getForecast(
        @Query("q") query: String,
        @Query("days") days: Int = FORECAST_DAYS,
        @Query("key") apiKey: String = BuildConfig.WEATHER_API_KEY,
        @Query("aqi") airQuality: String = "no",
        @Query("alerts") alerts: String = "no"
    ): WeatherResponseDto

    companion object {
        const val BASE_URL = "https://api.weatherapi.com/v1/"

        /** Today and the next two days, as far as the free plan forecasts. */
        const val FORECAST_DAYS = 3
    }
}
