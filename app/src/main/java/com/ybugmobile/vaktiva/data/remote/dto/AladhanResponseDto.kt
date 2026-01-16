package com.ybugmobile.vaktiva.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AladhanResponseDto(
    @SerializedName("code") val code: Int,
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<PrayerDayDto>
)

data class PrayerDayDto(
    @SerializedName("timings") val timings: TimingsDto,
    @SerializedName("date") val date: DateDto,
    @SerializedName("meta") val meta: MetaDto
)

data class TimingsDto(
    @SerializedName("Fajr") val fajr: String,
    @SerializedName("Sunrise") val sunrise: String,
    @SerializedName("Dhuhr") val dhuhr: String,
    @SerializedName("Asr") val asr: String,
    @SerializedName("Sunset") val sunset: String,
    @SerializedName("Maghrib") val maghrib: String,
    @SerializedName("Isha") val isha: String,
    @SerializedName("Imsak") val imsak: String,
    @SerializedName("Midnight") val midnight: String,
    @SerializedName("Firstthird") val firstThird: String,
    @SerializedName("Lastthird") val lastThird: String
)

data class DateDto(
    @SerializedName("readable") val readable: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("gregorian") val gregorian: GregorianDateDto,
    @SerializedName("hijri") val hijri: HijriDateDto
)

data class GregorianDateDto(
    @SerializedName("date") val date: String,
    @SerializedName("format") val format: String,
    @SerializedName("day") val day: String,
    @SerializedName("weekday") val weekday: WeekdayDto,
    @SerializedName("month") val month: MonthDto,
    @SerializedName("year") val year: String
)

data class HijriDateDto(
    @SerializedName("date") val date: String,
    @SerializedName("format") val format: String,
    @SerializedName("day") val day: String,
    @SerializedName("weekday") val weekday: WeekdayDto,
    @SerializedName("month") val month: HijriMonthDto,
    @SerializedName("year") val year: String
)

data class WeekdayDto(
    @SerializedName("en") val en: String,
    @SerializedName("ar") val ar: String? = null
)

data class MonthDto(
    @SerializedName("number") val number: Int,
    @SerializedName("en") val en: String
)

data class HijriMonthDto(
    @SerializedName("number") val number: Int,
    @SerializedName("en") val en: String,
    @SerializedName("ar") val ar: String
)

data class MetaDto(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("timezone") val timezone: String,
    @SerializedName("method") val method: CalculationMethodDto
)

data class CalculationMethodDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)
