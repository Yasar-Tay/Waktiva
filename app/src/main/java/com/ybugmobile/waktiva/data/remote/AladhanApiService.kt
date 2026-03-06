package com.ybugmobile.waktiva.data.remote

import com.ybugmobile.waktiva.data.remote.dto.AladhanResponseDto
import com.ybugmobile.waktiva.data.remote.dto.AstroResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AladhanApiService {

    @GET("v1/calendar/{year}/{month}")
    suspend fun getPrayerTimesCalendar(
        @Path("year") year: Int,
        @Path("month") month: Int,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int = 2,
        @Query("shafaq") shafaq: String? = null,
        @Query("school") school: Int? = null,
        @Query("midnightMode") midnightMode: Int? = null,
        @Query("timezonemode") timezonemode: Int? = null,
        @Query("latitudeAdjustmentMethod") latitudeAdjustmentMethod: Int? = null,
        @Query("adjustment") adjustment: Int? = null
    ): AladhanResponseDto

    @GET("v1/astro")
    suspend fun getAstroData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("date") date: String // Format: DD-MM-YYYY
    ): AstroResponseDto

    companion object {
        const val BASE_URL = "https://api.aladhan.com/"
    }
}
