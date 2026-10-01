package com.ybugmobile.waktiva.ui.widget

import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import java.time.LocalDateTime
import java.time.LocalTime

/** The 2×2 widget's families. The launcher cell size decides which one is rendered. */
internal enum class WidgetSize {
    /** Short cells (e.g. 4×1), kept readable for widgets placed with an earlier design: the countdown bar. */
    COMPACT,
    /** iOS 2×2 square. */
    SMALL;

    companion object {
        /** Minimum dp dimensions at which each family starts. */
        const val MIN_WIDTH_DP = 110f
        const val COMPACT_MIN_HEIGHT_DP = 40f
        const val SQUARE_MIN_HEIGHT_DP = 110f

        fun from(widthDp: Int, heightDp: Int): WidgetSize =
            if (heightDp in 1 until SQUARE_MIN_HEIGHT_DP.toInt()) COMPACT else SMALL
    }
}

internal enum class WidgetRowStatus { PASSED, NEXT, UPCOMING }

internal data class WidgetPrayerRow(
    val type: PrayerType,
    val time: LocalTime,
    val status: WidgetRowStatus
)

internal object WidgetDayModel {

    /**
     * Rows for the day that contains [nextPrayer]; after Isha this is tomorrow, so the
     * list never shows a fully "passed" day. Missing timings are skipped.
     */
    fun rows(
        days: List<PrayerDay>,
        nextPrayer: NextPrayer?,
        now: LocalDateTime
    ): List<WidgetPrayerRow> {
        val displayDate = nextPrayer?.date ?: now.toLocalDate()
        val day = days.find { it.date == displayDate } ?: return emptyList()

        return PrayerType.entries.mapNotNull { type ->
            val time = day.timings[type] ?: return@mapNotNull null
            val status = when {
                nextPrayer != null && type == nextPrayer.type && displayDate == nextPrayer.date ->
                    WidgetRowStatus.NEXT
                !displayDate.atTime(time).isAfter(now) -> WidgetRowStatus.PASSED
                else -> WidgetRowStatus.UPCOMING
            }
            WidgetPrayerRow(type, time, status)
        }
    }
}
