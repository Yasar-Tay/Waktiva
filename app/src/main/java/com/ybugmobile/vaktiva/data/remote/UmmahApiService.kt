package com.ybugmobile.vaktiva.data.remote

import com.ybugmobile.vaktiva.data.remote.dto.UmmahResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface UmmahApiService {

    @GET("api/v1/calendar")
    suspend fun getPrayerTimesCalendar(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("year") year: Int,
        @Query("month") month: Int,
        @Query("method") method: Int
    ): UmmahResponseDto

    companion object {
        const val BASE_URL = "https://api.ummah.io/" // Note: Using a placeholder, assuming UmmahAPI structure
    }
}
