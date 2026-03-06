package com.ybugmobile.waktiva.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AstroResponseDto(
    @SerializedName("code") val code: Int,
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: AstroDataDto
)

data class AstroDataDto(
    @SerializedName("sunrise") val sunrise: String?,
    @SerializedName("sunset") val sunset: String?,
    @SerializedName("moonrise") val moonrise: String?,
    @SerializedName("moonset") val moonset: String?,
    @SerializedName("moon_phase") val moonPhase: Double,
    @SerializedName("moon_illumination") val moonIllumination: Double
)
