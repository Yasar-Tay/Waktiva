package com.ybugmobile.waktiva.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ybugmobile.waktiva.data.local.entity.MosqueEntity

@Dao
interface MosqueDao {

    @Query("SELECT * FROM mosques WHERE anchorLat = :lat AND anchorLng = :lng")
    suspend fun getMosques(lat: Double, lng: Double): List<MosqueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mosques: List<MosqueEntity>)

    @Query("DELETE FROM mosques WHERE anchorLat = :lat AND anchorLng = :lng")
    suspend fun deleteForAnchor(lat: Double, lng: Double)
}
