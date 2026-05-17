package com.ybugmobile.waktiva.data.remote

import com.ybugmobile.waktiva.data.remote.dto.OverpassResponseDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST

interface OverpassApiService {

    @Headers(
        "Accept: */*",
        "User-Agent: Waktiva/1.0 (Android)"
    )
    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun query(@Field("data") query: String): OverpassResponseDto

    companion object {
        const val BASE_URL = "https://overpass-api.de/"
    }
}
