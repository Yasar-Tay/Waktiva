package com.ybugmobile.waktiva.domain.model

/** How the home screen draws the day's prayer circle. Persisted by [name]. */
enum class DayCircleStyle {
    CLASSIC,
    BRASS,
    STEEL;

    companion object {
        /** Shown until the user picks a style. */
        val DEFAULT = CLASSIC

        /** The removed skeleton style, whose look became the classic circle's. */
        private const val LEGACY_SKELETON = "SKELETON"

        /** The style saved as [name]; a saved skeleton opens as classic. */
        fun fromName(name: String?): DayCircleStyle {
            if (name.equals(LEGACY_SKELETON, ignoreCase = true)) return CLASSIC
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
        }
    }
}
