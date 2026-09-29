package com.ybugmobile.waktiva.data.local.entity

import androidx.room.Entity
import com.ybugmobile.waktiva.domain.model.PrayerType

/**
 * A prayer the user marked in their prayer log (çetele). It stands on its own, with no link to
 * [PrayerDayEntity]: prayer days are replaced month by month and pruned as they pass, but the log
 * is the user's own record and has to outlive them.
 */
@Entity(
    tableName = "prayer_status",
    primaryKeys = ["date", "prayerType"]
)
data class PrayerStatusEntity(
    val date: String, // Format: YYYY-MM-DD
    val prayerType: PrayerType,
    val isDone: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
