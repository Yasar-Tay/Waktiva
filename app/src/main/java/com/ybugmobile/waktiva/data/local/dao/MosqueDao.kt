package com.ybugmobile.waktiva.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ybugmobile.waktiva.data.local.entity.MosqueEntity

@Dao
interface MosqueDao {

    @Query("SELECT * FROM mosques WHERE anchorLat = :lat AND anchorLng = :lng")
    suspend fun getMosques(lat: Double, lng: Double): List<MosqueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mosques: List<MosqueEntity>)

    @Query("DELETE FROM mosques WHERE anchorLat = :lat AND anchorLng = :lng")
    suspend fun deleteForAnchor(lat: Double, lng: Double)

    /**
     * Atomically replaces all cached mosques for an anchor cell.
     * The delete + insert run inside a single SQLite transaction so a crash
     * between the two operations cannot leave the cache permanently empty.
     */
    @Transaction
    suspend fun replaceForAnchor(lat: Double, lng: Double, mosques: List<MosqueEntity>) {
        deleteForAnchor(lat, lng)
        insertAll(mosques)
    }
}
