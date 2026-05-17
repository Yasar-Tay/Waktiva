package com.ybugmobile.waktiva.data.repository

import com.ybugmobile.waktiva.data.local.dao.MosqueDao
import com.ybugmobile.waktiva.data.local.entity.MosqueEntity
import com.ybugmobile.waktiva.data.remote.OverpassApiService
import com.ybugmobile.waktiva.domain.model.MosqueLocation
import com.ybugmobile.waktiva.domain.repository.MosqueRepository
import android.util.Log
import javax.inject.Inject
import kotlin.math.roundToInt

private const val TAG = "MosqueRepo"

private const val TTL_MS = 24 * 60 * 60 * 1000L

class MosqueRepositoryImpl @Inject constructor(
    private val mosqueDao: MosqueDao,
    private val overpassApiService: OverpassApiService
) : MosqueRepository {

    override suspend fun getNearbyMosques(lat: Double, lng: Double): List<MosqueLocation> {
        val anchorLat = (lat * 10).roundToInt() / 10.0
        val anchorLng = (lng * 10).roundToInt() / 10.0

        Log.d(TAG, "getNearbyMosques: lat=$lat lng=$lng anchor=($anchorLat,$anchorLng)")

        val cached = mosqueDao.getMosques(anchorLat, anchorLng)
        val now = System.currentTimeMillis()
        val freshCache = cached.firstOrNull()?.let { (now - it.fetchedAt) < TTL_MS } == true
        if (cached.isNotEmpty() && freshCache) {
            Log.d(TAG, "Returning ${cached.size} cached mosques")
            return cached.map { it.toDomain() }
        }

        Log.d(TAG, "Cache miss — fetching from Overpass API")
        val query = """
            [out:json][timeout:15];
            (
              node["amenity"="place_of_worship"]["religion"="muslim"](around:10000,$lat,$lng);
              way["amenity"="place_of_worship"]["religion"="muslim"](around:10000,$lat,$lng);
              node["building"="mosque"](around:10000,$lat,$lng);
              way["building"="mosque"](around:10000,$lat,$lng);
            );
            out center 50;
        """.trimIndent()

        val response = overpassApiService.query(query)
        Log.d(TAG, "Overpass returned ${response.elements.size} elements")

        val entities = response.elements.mapNotNull { el ->
            val elLat = el.lat ?: el.center?.lat ?: return@mapNotNull null
            val elLng = el.lon ?: el.center?.lon ?: return@mapNotNull null
            MosqueEntity(
                id = el.id,
                name = el.tags?.get("name"),
                lat = elLat,
                lng = elLng,
                anchorLat = anchorLat,
                anchorLng = anchorLng,
                fetchedAt = now
            )
        }

        Log.d(TAG, "Mapped to ${entities.size} entities (${response.elements.size - entities.size} skipped — missing coords)")
        mosqueDao.deleteForAnchor(anchorLat, anchorLng)
        mosqueDao.insertAll(entities)

        return entities.map { it.toDomain() }
    }

    private fun MosqueEntity.toDomain() = MosqueLocation(id, name, lat, lng)
}
