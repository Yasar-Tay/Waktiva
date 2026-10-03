package com.ybugmobile.waktiva.domain.model

import java.time.LocalDate

/** What a badge counts towards its target. */
enum class BadgeMeasure {
    /** Prayers marked, all told. */
    PRAYERS,

    /** Days with all five prayers marked, all told. */
    FULL_DAYS,

    /** The longest run of full days in a row. */
    STREAK,

    /** The longest run of days in a row with Fajr marked. */
    FAJR_RUN
}

/** A badge in the prayer log, earned once its [measure] reaches [target]. In the order they show. */
enum class PrayerLogBadge(val measure: BadgeMeasure, val target: Int) {
    FIRST_PRAYER(BadgeMeasure.PRAYERS, 1),
    FIRST_FULL_DAY(BadgeMeasure.FULL_DAYS, 1),
    STREAK_3(BadgeMeasure.STREAK, 3),
    STREAK_7(BadgeMeasure.STREAK, 7),
    FAJR_7(BadgeMeasure.FAJR_RUN, 7),
    PRAYERS_100(BadgeMeasure.PRAYERS, 100),
    STREAK_40(BadgeMeasure.STREAK, 40),
    PRAYERS_500(BadgeMeasure.PRAYERS, 500)
}

/** How far a badge is: [current] of its target, held at the target once earned. */
data class BadgeProgress(val badge: PrayerLogBadge, val current: Int) {
    val target: Int get() = badge.target
    val isEarned: Boolean get() = current >= target
    val fraction: Float get() = current.toFloat() / target
}

/** What a mark reached, to cheer for: see [PrayerLogProgress.celebration]. */
data class PrayerLogCelebration(
    val fullDay: Boolean,
    /** Full days in a row as of today, after the mark. */
    val streak: Int,
    /** The new level, if one was reached. */
    val level: Int?,
    val badges: List<PrayerLogBadge>
)

/**
 * The prayer log as a game: points (XP) for every prayer marked and a bonus for every full day,
 * levels that take a little more XP each, and badges.
 */
data class PrayerLogProgress(
    val totalPrayed: Int = 0,
    val fullDays: Int = 0,
    /** The longest run of full days in a row, ever. */
    val bestStreak: Int = 0,
    val xp: Int = 0,
    val level: Int = 1,
    /** XP gathered since this level began. */
    val levelXp: Int = 0,
    /** XP this level takes, all told, to reach the next. */
    val levelSpan: Int = levelSpan(1),
    val badges: List<BadgeProgress> = PrayerLogBadge.entries.map { BadgeProgress(it, 0) }
) {
    val earned: Set<PrayerLogBadge> get() = badges.filter { it.isEarned }.mapTo(mutableSetOf()) { it.badge }

    /** The badge not yet earned that is nearest to being earned, or null once all are. */
    val nextBadge: BadgeProgress? get() = badges.filterNot { it.isEarned }.maxByOrNull { it.fraction }

    companion object {
        const val XP_PER_PRAYER = 10
        const val FULL_DAY_BONUS = 25

        /** XP level [level] takes to reach the next: 100, then 25 more each level. */
        fun levelSpan(level: Int): Int = 100 + 25 * (level - 1)

        /** The progress made by the prayers in [prayed]. */
        fun of(prayed: Map<LocalDate, Set<PrayerType>>): PrayerLogProgress {
            val totalPrayed = prayed.values.sumOf { day -> day.count { it.isLogged } }
            val fullDays = prayed.filterValues { it.containsAll(LoggedPrayers) }.keys
            val fajrDays = prayed.filterValues { PrayerType.FAJR in it }.keys
            val bestStreak = longestRun(fullDays)
            val bestFajrRun = longestRun(fajrDays)

            val xp = totalPrayed * XP_PER_PRAYER + fullDays.size * FULL_DAY_BONUS
            var level = 1
            var levelXp = xp
            while (levelXp >= levelSpan(level)) {
                levelXp -= levelSpan(level)
                level++
            }

            val badges = PrayerLogBadge.entries.map { badge ->
                val reached = when (badge.measure) {
                    BadgeMeasure.PRAYERS -> totalPrayed
                    BadgeMeasure.FULL_DAYS -> fullDays.size
                    BadgeMeasure.STREAK -> bestStreak
                    BadgeMeasure.FAJR_RUN -> bestFajrRun
                }
                BadgeProgress(badge, reached.coerceAtMost(badge.target))
            }

            return PrayerLogProgress(
                totalPrayed = totalPrayed,
                fullDays = fullDays.size,
                bestStreak = bestStreak,
                xp = xp,
                level = level,
                levelXp = levelXp,
                levelSpan = levelSpan(level),
                badges = badges
            )
        }

        /**
         * What marking prayers on [date] reached, going from [before] to [after]: the day made
         * full, a new level, new badges, with the streak as of [today]. Null when it reached none.
         */
        fun celebration(
            before: Map<LocalDate, Set<PrayerType>>,
            after: Map<LocalDate, Set<PrayerType>>,
            date: LocalDate,
            today: LocalDate
        ): PrayerLogCelebration? {
            val was = of(before)
            val now = of(after)
            fun isFull(prayed: Map<LocalDate, Set<PrayerType>>) = prayed[date]?.containsAll(LoggedPrayers) == true
            val celebration = PrayerLogCelebration(
                fullDay = isFull(after) && !isFull(before),
                streak = PrayerLog.streak(after, today),
                level = now.level.takeIf { it > was.level },
                badges = PrayerLogBadge.entries.filter { it in now.earned && it !in was.earned }
            )
            return celebration.takeIf { it.fullDay || it.level != null || it.badges.isNotEmpty() }
        }

        /** The most days in a row among [dates]. */
        private fun longestRun(dates: Collection<LocalDate>): Int {
            var best = 0
            var run = 0
            var last: LocalDate? = null
            for (date in dates.sortedBy { it.toEpochDay() }) {
                run = if (last != null && date == last.plusDays(1)) run + 1 else 1
                best = maxOf(best, run)
                last = date
            }
            return best
        }
    }
}
