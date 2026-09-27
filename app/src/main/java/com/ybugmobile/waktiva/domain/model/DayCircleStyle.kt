package com.ybugmobile.waktiva.domain.model

/** How the home screen draws the day's prayer circle. Persisted by [name]. */
enum class DayCircleStyle {
    CLASSIC,
    BRASS,
    STEEL,
    SKELETON;

    companion object {
        /** Shown until the user picks a style. */
        val DEFAULT = CLASSIC

        /**
         * The style saved as [name]. Skeleton is no longer offered: its look became the classic
         * circle's, so a saved skeleton opens as classic (settings would find no option for it).
         */
        fun fromName(name: String?): DayCircleStyle {
            if (name.equals(SKELETON.name, ignoreCase = true)) return CLASSIC
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
        }
    }
}
