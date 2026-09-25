package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import java.time.LocalDateTime
import java.time.LocalTime

/** iOS-style widget families. The launcher cell size decides which one is rendered. */
internal enum class IosWidgetSize {
    SMALL,
    MEDIUM,
    LARGE;

    companion object {
        /** Minimum dp dimensions at which each family starts, mirroring the iOS 2×2 / 4×2 / 4×4 grid. */
        const val SMALL_MIN_DP = 110f
        const val WIDE_MIN_WIDTH_DP = 250f
        const val LARGE_MIN_HEIGHT_DP = 280f

        fun from(widthDp: Int, heightDp: Int): IosWidgetSize = when {
            widthDp < WIDE_MIN_WIDTH_DP -> SMALL
            heightDp < LARGE_MIN_HEIGHT_DP -> MEDIUM
            else -> LARGE
        }
    }
}

internal enum class IosRowStatus { PASSED, NEXT, UPCOMING }

internal data class IosPrayerRow(
    val type: PrayerType,
    val time: LocalTime,
    val status: IosRowStatus
)

internal object IosWidgetModel {

    /**
     * Rows for the day that contains [nextPrayer]; after Isha this is tomorrow, so the
     * list never shows a fully "passed" day. Missing timings are skipped.
     */
    fun rows(
        days: List<PrayerDay>,
        nextPrayer: NextPrayer?,
        now: LocalDateTime
    ): List<IosPrayerRow> {
        val displayDate = nextPrayer?.date ?: now.toLocalDate()
        val day = days.find { it.date == displayDate } ?: return emptyList()

        return PrayerType.entries.mapNotNull { type ->
            val time = day.timings[type] ?: return@mapNotNull null
            val status = when {
                nextPrayer != null && type == nextPrayer.type && displayDate == nextPrayer.date ->
                    IosRowStatus.NEXT
                !displayDate.atTime(time).isAfter(now) -> IosRowStatus.PASSED
                else -> IosRowStatus.UPCOMING
            }
            IosPrayerRow(type, time, status)
        }
    }
}
