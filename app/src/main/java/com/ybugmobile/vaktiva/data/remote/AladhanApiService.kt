package com.ybugmobile.vaktiva.data.remote

import com.ybugmobile.vaktiva.data.remote.dto.AladhanResponseDto
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
        @Query("method") method: Int = 2, // Default to ISNA/MWL or similar, will be dynamic later
        @Query("shafaq") shafaq: String? = null,
        @Query("school") school: Int? = null, // 0 for Shafi, 1 for Hanafi
        @Query("midnightMode") midnightMode: Int? = null,
        @Query("timezonemode") timezonemode: Int? = null,
        @Query("latitudeAdjustmentMethod") latitudeAdjustmentMethod: Int? = null,
        @Query("adjustment") adjustment: Int? = null
    ): AladhanResponseDto

    companion object {
        const val BASE_URL = "https://api.aladhan.com/"
    }
}
