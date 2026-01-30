package com.ybugmobile.vaktiva.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.ybugmobile.vaktiva.domain.model.PrayerType

@Entity(
    tableName = "prayer_status",
    primaryKeys = ["date", "prayerType"],
    foreignKeys = [
        ForeignKey(
            entity = PrayerDayEntity::class,
            parentColumns = ["date"],
            childColumns = ["date"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["date"])]
)
data class PrayerStatusEntity(
    val date: String, // Format: YYYY-MM-DD
    val prayerType: PrayerType,
    val isDone: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
