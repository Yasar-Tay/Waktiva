package com.ybugmobile.vaktiva.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UmmahResponseDto(
    @SerializedName("data") val data: List<UmmahPrayerDayDto>?
)

data class UmmahPrayerDayDto(
    @SerializedName("date") val date: String, // YYYY-MM-DD
    @SerializedName("timings") val timings: UmmahTimingsDto
)

data class UmmahTimingsDto(
    @SerializedName("fajr") val fajr: String,
    @SerializedName("sunrise") val sunrise: String,
    @SerializedName("dhuhr") val dhuhr: String,
    @SerializedName("asr") val asr: String,
    @SerializedName("maghrib") val maghrib: String,
    @SerializedName("isha") val isha: String
)
