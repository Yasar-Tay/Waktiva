package com.ybugmobile.vaktiva.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_days")
data class PrayerDayEntity(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val hijriDate: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    
    // Astronomical data (Accurate source)
    val moonPhase: Double? = null,
    val moonIllumination: Double? = null,
    val moonrise: String? = null,
    val moonset: String? = null
)
