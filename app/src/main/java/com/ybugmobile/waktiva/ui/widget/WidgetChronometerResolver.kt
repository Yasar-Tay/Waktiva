package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.domain.model.NextPrayer
import java.time.ZoneId

internal data class WidgetChronometerState(
    val cacheKey: String,
    val baseTime: Long,
    val isRunning: Boolean
)

internal object WidgetChronometerResolver {

    fun resolve(
        nextPrayer: NextPrayer?,
        nowEpochMillis: Long,
        elapsedRealtime: Long,
        cachedPrayerKey: String,
        cachedBaseTime: Long
    ): WidgetChronometerState? {
        if (nextPrayer == null) return null

        val cacheKey = "${nextPrayer.type}@${nextPrayer.date}@${nextPrayer.time}"
        val targetEpochMillis = nextPrayer.date.atTime(nextPrayer.time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val remainingMillis = targetEpochMillis - nowEpochMillis

        return if (remainingMillis <= 0L) {
            WidgetChronometerState(
                cacheKey = cacheKey,
                baseTime = elapsedRealtime,
                isRunning = false
            )
        } else {
            val baseTime = if (cacheKey == cachedPrayerKey && cachedBaseTime > elapsedRealtime) {
                cachedBaseTime
            } else {
                elapsedRealtime + remainingMillis
            }
            WidgetChronometerState(
                cacheKey = cacheKey,
                baseTime = baseTime,
                isRunning = true
            )
        }
    }
}
