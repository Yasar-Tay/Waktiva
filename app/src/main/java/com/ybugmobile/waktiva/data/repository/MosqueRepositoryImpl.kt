package com.ybugmobile.waktiva.data.repository

import android.location.Location
import android.util.Log
import com.ybugmobile.waktiva.data.local.dao.MosqueDao
import com.ybugmobile.waktiva.data.local.entity.MosqueEntity
import com.ybugmobile.waktiva.data.remote.OverpassApiService
import com.ybugmobile.waktiva.domain.model.MosqueLocation
import com.ybugmobile.waktiva.domain.repository.MosqueRepository
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
        // nwr = node/way/relation — catches large mosque complexes (e.g. Blue Mosque)
        // that are mapped as relations in OSM, which the old node+way query missed.
        // timeout:25 matches the OkHttp read timeout (30 s) with headroom.
        // Limit raised to 200 — 50 was too low for dense cities like Istanbul.
        val query = """
            [out:json][timeout:25];
            (
              nwr["amenity"="place_of_worship"]["religion"="muslim"](around:10000,$lat,$lng);
              nwr["building"="mosque"](around:10000,$lat,$lng);
            );
            out center 200;
        """.trimIndent()

        val response = overpassApiService.query(query)
        Log.d(TAG, "Overpass returned ${response.elements.size} elements")

        val entities = response.elements.mapNotNull { el ->
            val elLat = el.lat ?: el.center?.lat ?: return@mapNotNull null
            val elLng = el.lon ?: el.center?.lon ?: return@mapNotNull null
            MosqueEntity(
                id = el.id,
                // Prefer English name for international users; fall back to local OSM name
                name = el.tags?.let { it["name:en"] ?: it["name"] },
                lat = elLat,
                lng = elLng,
                anchorLat = anchorLat,
                anchorLng = anchorLng,
                fetchedAt = now,
                address = buildAddress(el.tags)
            )
        }

        Log.d(TAG, "Mapped to ${entities.size} entities (${response.elements.size - entities.size} skipped — missing coords)")

        // Deduplicate: the same physical mosque can appear as both a node and a way
        // in OSM, giving two entries with different IDs but nearly identical coordinates.
        // Keep only the first entry within a 50 m radius of any already-kept entry.
        val deduped = dedup(entities)
        Log.d(TAG, "After dedup: ${deduped.size} unique mosques (${entities.size - deduped.size} duplicates removed)")

        mosqueDao.replaceForAnchor(anchorLat, anchorLng, deduped)

        return deduped.map { it.toDomain() }
    }

    private fun MosqueEntity.toDomain() = MosqueLocation(id, name, lat, lng, address)
}

/**
 * Removes entries whose coordinates are within [thresholdMeters] of an already-kept
 * entry. Preserves the first occurrence (generally the node, which has a precise
 * point location, rather than a way's bounding-box centre).
 */
private fun dedup(
    entities: List<MosqueEntity>,
    thresholdMeters: Float = 50f
): List<MosqueEntity> {
    val kept = mutableListOf<MosqueEntity>()
    val dist = FloatArray(1)
    for (e in entities) {
        val isDuplicate = kept.any { k ->
            Location.distanceBetween(k.lat, k.lng, e.lat, e.lng, dist)
            dist[0] < thresholdMeters
        }
        if (!isDuplicate) kept += e
    }
    return kept
}

/**
 * Builds a human-readable address string from Overpass OSM tags.
 * Prefers `addr:full` when present; otherwise assembles from individual parts.
 * Both city and postcode are included when available (previously postcode was
 * dropped whenever city was present).
 */
private fun buildAddress(tags: Map<String, String>?): String? {
    if (tags == null) return null
    tags["addr:full"]?.takeIf { it.isNotBlank() }?.let { return it }
    val parts = mutableListOf<String>()
    val housenumber = tags["addr:housenumber"]
    val street = tags["addr:street"]
    if (housenumber != null || street != null) {
        parts += listOfNotNull(housenumber, street).joinToString(" ")
    }
    tags["addr:city"]?.let { parts += it }
    tags["addr:postcode"]?.let { parts += it }
    return parts.joinToString(", ").ifEmpty { null }
}
