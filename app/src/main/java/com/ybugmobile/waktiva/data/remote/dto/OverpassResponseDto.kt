package com.ybugmobile.waktiva.data.remote.dto

import com.google.gson.annotations.SerializedName

data class OverpassResponseDto(
    val elements: List<OverpassElement>,
    // Set when the server aborted the query (e.g. "runtime error: Query timed out");
    // elements are then partial or empty and must not be cached.
    val remark: String? = null
)

data class OverpassElement(
    val id: Long,
    val type: String,
    val lat: Double?,
    val lon: Double?,
    val center: OverpassCenter?,
    val tags: Map<String, String>?
)

data class OverpassCenter(
    val lat: Double,
    val lon: Double
)
