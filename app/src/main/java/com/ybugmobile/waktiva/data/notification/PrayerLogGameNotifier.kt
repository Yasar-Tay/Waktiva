package com.ybugmobile.waktiva.data.notification

import com.ybugmobile.waktiva.domain.manager.SettingsManagerInterface
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerLogRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The prayer log's game in notifications, while the log and its game notifications are on: the
 * nudge before a prayer's time ends, and cheers for what a mark made outside the app reaches
 * (inside it, the prayer log screen cheers for itself).
 */
@Singleton
class PrayerLogGameNotifier @Inject constructor(
    private val settingsManager: SettingsManagerInterface,
    private val prayerLogRepository: PrayerLogRepository,
    private val notificationHelper: NotificationHelper
) {

    suspend fun isOn(): Boolean = settingsManager.settingsFlow.first()
        .let { it.prayerLogEnabled && it.prayerLogGameNotifications }

    /**
     * Marks [type] on [date] as prayed, or takes the mark back, and cheers in a notification for
     * what the mark reached: the day made full, a level, a badge. [today] is the day the streak
     * is counted to.
     */
    suspend fun setPrayed(date: LocalDate, type: PrayerType, prayed: Boolean, today: LocalDate) {
        markAll(date, listOf(type), prayed, today)
    }

    /** As [setPrayed], for several of [date]'s prayers at once. */
    suspend fun markAll(date: LocalDate, types: List<PrayerType>, prayed: Boolean, today: LocalDate) {
        if (!prayed || !isOn()) {
            types.forEach { prayerLogRepository.setPrayed(date, it, prayed) }
            return
        }
        val before = prayerLogRepository.getPrayedPrayers().first()
        types.forEach { prayerLogRepository.setPrayed(date, it, true) }
        val after = prayerLogRepository.getPrayedPrayers().first()
        PrayerLogProgress.celebration(before, after, date, today)
            ?.let(notificationHelper::showPrayerLogCelebration)
    }

    /**
     * The nudge for [type] on [date], as long as it's still unmarked; [today] is the day the
     * streak is counted to.
     */
    suspend fun nudge(date: LocalDate, type: PrayerType, today: LocalDate) {
        if (!isOn()) return
        val prayed = prayerLogRepository.getPrayedPrayers().first()
        if (type in prayed[date].orEmpty()) return
        notificationHelper.showPrayerLogNudge(
            date = date,
            type = type,
            streak = PrayerLog.streak(prayed, today),
            level = PrayerLogProgress.of(prayed).level
        )
    }

    /** The streak the reminder after Isha speaks of for [date], or null with the game off. */
    suspend fun reminderStreak(date: LocalDate): Int? {
        if (!isOn()) return null
        return PrayerLog.streak(prayerLogRepository.getPrayedPrayers().first(), date)
    }
}
