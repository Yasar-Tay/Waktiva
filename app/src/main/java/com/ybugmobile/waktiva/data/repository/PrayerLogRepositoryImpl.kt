package com.ybugmobile.waktiva.data.repository

import com.ybugmobile.waktiva.data.local.dao.PrayerStatusDao
import com.ybugmobile.waktiva.data.local.entity.PrayerStatusEntity
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.domain.model.PrayerLogBackup
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class PrayerLogRepositoryImpl @Inject constructor(
    private val dao: PrayerStatusDao,
    private val settingsManager: SettingsManager
) : PrayerLogRepository {

    override fun getPrayedPrayers(): Flow<Map<LocalDate, Set<PrayerType>>> =
        dao.getDoneStatuses()
            .map { statuses ->
                statuses.groupBy({ LocalDate.parse(it.date) }, { it.prayerType }).mapValues { it.value.toSet() }
            }
            .distinctUntilChanged()

    override fun getPrayedPrayers(date: LocalDate): Flow<Set<PrayerType>> =
        dao.getDoneStatusesForDate(date.toString())
            .map { statuses -> statuses.mapTo(mutableSetOf()) { it.prayerType } }
            .distinctUntilChanged()

    override fun getStartDate(): Flow<LocalDate?> = settingsManager.prayerLogStartFlow

    override suspend fun setPrayed(date: LocalDate, type: PrayerType, prayed: Boolean) {
        // The log begins on the day it's first used, whichever day's prayer is marked: marking a
        // prayer from last week doesn't turn every other prayer since then into a missed one.
        if (prayed) settingsManager.markPrayerLogStarted(LocalDate.now())
        dao.updateStatus(PrayerStatusEntity(date = date.toString(), prayerType = type, isDone = prayed))
    }

    override suspend fun backup(): PrayerLogBackup = PrayerLogBackup(
        startDate = settingsManager.prayerLogStartFlow.first(),
        prayed = dao.getDoneStatusesOnce()
            .groupBy({ LocalDate.parse(it.date) }, { it.prayerType })
            .mapValues { it.value.toSet() }
    )

    override suspend fun restore(backup: PrayerLogBackup): Int {
        val existing = dao.getDoneStatusesOnce().mapTo(mutableSetOf()) { it.date to it.prayerType }
        val added = backup.prayed.flatMap { (date, prayers) -> prayers.map { date.toString() to it } }
            .filter { it !in existing }
        dao.insertStatuses(added.map { (date, type) -> PrayerStatusEntity(date = date, prayerType = type, isDone = true) })
        // A copy that doesn't say when its log began starts it on its first marked day.
        (backup.startDate ?: backup.prayed.keys.minOrNull())?.let { settingsManager.markPrayerLogStarted(it) }
        return added.size
    }

    override suspend fun clear() {
        dao.deleteAll()
        settingsManager.clearPrayerLogStart()
    }
}
