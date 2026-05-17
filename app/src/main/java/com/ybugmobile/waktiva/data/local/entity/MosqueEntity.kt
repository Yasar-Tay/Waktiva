package com.ybugmobile.waktiva.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mosques")
data class MosqueEntity(
    @PrimaryKey val id: Long,
    val name: String?,
    val lat: Double,
    val lng: Double,
    val anchorLat: Double,
    val anchorLng: Double,
    val fetchedAt: Long
)
