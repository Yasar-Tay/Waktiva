package com.ybugmobile.waktiva.domain.repository

import com.ybugmobile.waktiva.domain.model.PrayerType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The user's prayer log (çetele): which prayers they have marked as prayed. */
interface PrayerLogRepository {

    /** The prayers marked as prayed, by day. */
    fun getPrayedPrayers(): Flow<Map<LocalDate, Set<PrayerType>>>

    /** The prayers marked as prayed on [date]. */
    fun getPrayedPrayers(date: LocalDate): Flow<Set<PrayerType>>

    /**
     * The day the user began the log: before it, prayers not marked weren't missed, only not
     * logged. Null until the first prayer is marked.
     */
    fun getStartDate(): Flow<LocalDate?>

    /** Marks [type] on [date] as prayed, or takes the mark back. */
    suspend fun setPrayed(date: LocalDate, type: PrayerType, prayed: Boolean)
}
