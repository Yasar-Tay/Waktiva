package com.ybugmobile.waktiva.data.remote

import com.ybugmobile.waktiva.data.remote.dto.OverpassResponseDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface OverpassApiService {

    @Headers(
        "Accept: */*",
        "User-Agent: Waktiva/1.0 (Android)"
    )
    @FormUrlEncoded
    @POST
    suspend fun query(@Url url: String, @Field("data") query: String): OverpassResponseDto

    companion object {
        const val BASE_URL = "https://overpass-api.de/"

        // Public Overpass instances are frequently overloaded (instant 502/504s or
        // 40 s+ queues), so the repository walks this list until one answers.
        val INTERPRETER_URLS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
    }
}
