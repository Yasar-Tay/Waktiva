package com.ybugmobile.waktiva.data.repository

import android.location.Location
import android.util.Log
import com.ybugmobile.waktiva.data.local.dao.MosqueDao
import com.ybugmobile.waktiva.data.local.entity.MosqueEntity
import com.ybugmobile.waktiva.data.remote.OverpassApiService
import com.ybugmobile.waktiva.data.remote.dto.OverpassResponseDto
import com.ybugmobile.waktiva.domain.model.MosqueLocation
import com.ybugmobile.waktiva.domain.repository.MosqueRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private const val TAG = "MosqueRepo"

// Mosques rarely change; a long TTL also spares the overloaded public Overpass servers.
private const val TTL_MS = 7 * 24 * 60 * 60 * 1000L

// The next mirror starts after this long even if the previous one is still waiting,
// or immediately when an earlier one has failed.
private const val HEDGE_DELAY_MS = 4_000L

// Overloaded mirrors often answer 502/504 instantly; such a fast failure is retried
// once after a short pause, as the next attempt frequently goes through.
private const val FAST_FAILURE_MS = 5_000L
private const val RETRY_BACKOFF_MS = 3_000L

// Upper bound for the whole lookup so the map never waits indefinitely.
private const val OVERALL_TIMEOUT_MS = 60_000L

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
        // timeout:25 bounds server-side execution; queueing on busy mirrors comes on top.
        // Limit raised to 200 — 50 was too low for dense cities like Istanbul.
        val query = """
            [out:json][timeout:25];
            (
              nwr["amenity"="place_of_worship"]["religion"="muslim"](around:10000,$lat,$lng);
              nwr["building"="mosque"](around:10000,$lat,$lng);
            );
            out center 200;
        """.trimIndent()

        val response = try {
            queryAnyMirror(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Every mirror failed — a stale cache beats an error banner.
            if (cached.isNotEmpty()) {
                Log.w(TAG, "All Overpass mirrors failed; returning ${cached.size} stale cached mosques", e)
                return cached.map { it.toDomain() }
            }
            throw e
        }
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

    /**
     * Queries the public Overpass mirrors with staggered (hedged) requests and returns
     * the first good answer. Run one after another, a single stalled mirror used up the
     * whole wait (overpass-api.de and maps.mail.ru each hit the 25 s cap before the
     * last mirror failed with 500), so the map ended up showing an error every time.
     */
    private suspend fun queryAnyMirror(query: String): OverpassResponseDto {
        val urls = OverpassApiService.INTERPRETER_URLS
        var lastError: Throwable? = null

        val response = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            coroutineScope {
                val outcomes = Channel<Result<OverpassResponseDto>>(urls.size)
                val failures = MutableStateFlow(0)
                val workers = urls.mapIndexed { i, url ->
                    launch {
                        withTimeoutOrNull(i * HEDGE_DELAY_MS) { failures.first { it >= i } }
                        val outcome = queryMirror(url, query)
                        if (outcome.isFailure) failures.value++
                        outcomes.send(outcome)
                    }
                }
                try {
                    repeat(urls.size) {
                        outcomes.receive()
                            .onSuccess { return@coroutineScope it }
                            .onFailure { lastError = it }
                    }
                    null
                } finally {
                    workers.forEach { it.cancel() }
                }
            }
        }
        return response
            ?: throw (lastError as? Exception ?: IOException("No Overpass mirror answered in time"))
    }

    private suspend fun queryMirror(url: String, query: String): Result<OverpassResponseDto> {
        var attempt = 0
        while (true) {
            val startedAt = System.currentTimeMillis()
            try {
                val response = overpassApiService.query(url, query)
                if (response.remark?.contains("error", ignoreCase = true) == true) {
                    throw IllegalStateException("Overpass remark: ${response.remark}")
                }
                Log.d(TAG, "Overpass mirror answered: $url")
                return Result.success(response)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startedAt
                Log.w(TAG, "Overpass mirror failed: $url after ${elapsed} ms (${e.message})")
                if (attempt++ > 0 || elapsed > FAST_FAILURE_MS) return Result.failure(e)
                delay(RETRY_BACKOFF_MS)
            }
        }
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
