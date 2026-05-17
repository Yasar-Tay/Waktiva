package com.ybugmobile.waktiva.data.remote.dto

import com.google.gson.annotations.SerializedName

data class OverpassResponseDto(
    val elements: List<OverpassElement>
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
