package com.ybugmobile.waktiva.domain.model

/** How the home screen draws the day's prayer circle. Persisted by [name]. */
enum class DayCircleStyle {
    CLASSIC,
    BRASS,
    STEEL,
    SKELETON;

    companion object {
        /** Shown until the user picks a style. */
        val DEFAULT = BRASS

        fun fromName(name: String?): DayCircleStyle =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
