package com.ybugmobile.vaktiva.domain.repository

import com.ybugmobile.vaktiva.domain.model.MoonPhase
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Repository interface defining the contract for managing prayer data, lunar information,
 * and data lifecycle within the application.
 */
interface PrayerRepository {
    
    /**
     * Observes a stream of [PrayerDay] objects from the local database.
     * @return A [Flow] emitting a list of prayer data for stored days.
     */
    fun getPrayerDays(): Flow<List<PrayerDay>>

    /**
     * Retrieves the [MoonPhase] for a specific date and time.
     * Implementation typically involves astronomical calculations or API lookups.
     *
     * @param dateTime The [LocalDateTime] for which to calculate the moon phase.
     * @return A [MoonPhase] object containing illumination and phase details.
     */
    suspend fun getMoonPhase(dateTime: LocalDateTime): MoonPhase
    
    /**
     * Triggers a refresh of prayer times from remote or local sources.
     *
     * @param year The Gregorian year for the times.
     * @param month The Gregorian month (1-12).
     * @param latitude User's current latitude.
     * @param longitude User's current longitude.
     * @param method The numerical ID of the calculation method (e.g., Diyanet, MWL).
     * @return A [Result] indicating success or containing an error.
     */
    suspend fun refreshPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double?,
        longitude: Double?,
        method: Int
    ): Result<Unit>
    
    /**
     * Counts how many days of cached prayer data remain starting from the provided date.
     * Used by [WorkManager] tasks to determine if a proactive refresh is needed.
     *
     * @param currentDate ISO formatted date string (e.g., "2023-10-27").
     * @return The number of future days available in the cache.
     */
    suspend fun getRemainingDaysCount(currentDate: String): Int

    /**
     * Cleans up the local database by removing data older than the current date.
     *
     * @param currentDate ISO formatted date string representing the boundary for deletion.
     */
    suspend fun deletePastData(currentDate: String)
}
