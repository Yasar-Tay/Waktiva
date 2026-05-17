package com.ybugmobile.waktiva.domain.repository

import com.ybugmobile.waktiva.domain.model.MosqueLocation

interface MosqueRepository {
    suspend fun getNearbyMosques(lat: Double, lng: Double): List<MosqueLocation>
}
